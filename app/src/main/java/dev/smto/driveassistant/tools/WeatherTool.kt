package dev.smto.driveassistant.tools

import android.content.Context
import dev.smto.driveassistant.data.SettingsRepository
import dev.smto.driveassistant.voice.LocationProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import kotlin.math.roundToInt

/**
 * Weather via Open-Meteo (no API key). Location resolution order:
 *  1. explicit `location` argument -> geocoded
 *  2. current device location
 *  3. configured home location -> geocoded
 */
class WeatherTool(
    private val context: Context,
    private val settings: SettingsRepository,
) : Tool {

    override val name = "get_weather"
    override val description =
        "Current weather and today's forecast. Optionally for a named place; otherwise " +
            "uses the driver's current location."

    override val parameters: JsonObject = Json.parseToJsonElement(
        """
        {
          "type": "object",
          "properties": {
            "location": { "type": "string", "description": "City or place name. Omit to use current location." }
          }
        }
        """.trimIndent(),
    ) as JsonObject

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun run(args: JsonObject): String {
        val explicit = args.str("location")
        val (lat, lon, label) = resolve(explicit) ?: return when {
            explicit != null -> "I couldn't find $explicit."
            else -> "I don't have a location. Enable location access or say a city name."
        }

        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&timezone=auto&forecast_days=1"

        val root = runCatching { json.parseToJsonElement(Http.getString(url)).jsonObject }
            .getOrElse { return "Weather service is unavailable right now." }

        val cur = root["current"]?.jsonObject ?: return "No current weather data for $label."
        val daily = root["daily"]?.jsonObject

        val temp = cur["temperature_2m"]?.jsonPrimitive?.double?.roundToInt()
        val feels = cur["apparent_temperature"]?.jsonPrimitive?.double?.roundToInt()
        val wind = cur["wind_speed_10m"]?.jsonPrimitive?.double?.roundToInt()
        val code = cur["weather_code"]?.jsonPrimitive?.int ?: 0
        val hi = daily?.get("temperature_2m_max")?.jsonArray?.getOrNull(0)?.jsonPrimitive?.double?.roundToInt()
        val lo = daily?.get("temperature_2m_min")?.jsonArray?.getOrNull(0)?.jsonPrimitive?.double?.roundToInt()
        val rainPct = daily?.get("precipitation_probability_max")?.jsonArray?.getOrNull(0)?.jsonPrimitive?.int

        return buildString {
            append("In $label it's ${temp ?: "?"} degrees, ${describe(code)}")
            if (feels != null && temp != null && kotlin.math.abs(feels - temp) >= 2) append(", feels like $feels")
            append(". ")
            if (hi != null && lo != null) append("High $hi, low $lo. ")
            if (rainPct != null) append("Rain chance $rainPct percent. ")
            if (wind != null && wind >= 25) append("Windy at $wind kilometers per hour.")
        }.trim()
    }

    private data class Place(val lat: Double, val lon: Double, val label: String)

    private suspend fun resolve(explicit: String?): Place? {
        if (explicit != null) return geocode(explicit)

        LocationProvider(context).current()?.let {
            return Place(round(it.latitude), round(it.longitude), "your area")
        }
        val home = settings.current().homeLocation
        return if (home.isNotBlank()) geocode(home) else null
    }

    private suspend fun geocode(query: String): Place? {
        val q = URLEncoder.encode(query, "UTF-8")
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=en&format=json"
        val root = runCatching { json.parseToJsonElement(Http.getString(url)).jsonObject }.getOrNull() ?: return null
        val first = root["results"]?.jsonArray?.getOrNull(0)?.jsonObject ?: return null
        val name = first["name"]?.jsonPrimitive?.content ?: query
        val country = first["country"]?.jsonPrimitive?.content
        return Place(
            lat = first["latitude"]!!.jsonPrimitive.double,
            lon = first["longitude"]!!.jsonPrimitive.double,
            label = if (country != null) "$name, $country" else name,
        )
    }

    private fun round(v: Double) = (v * 10000).roundToInt() / 10000.0

    private fun describe(code: Int): String = when (code) {
        0 -> "clear"
        1, 2 -> "partly cloudy"
        3 -> "overcast"
        45, 48 -> "foggy"
        51, 53, 55, 56, 57 -> "drizzle"
        61, 63, 65, 66, 67 -> "rain"
        71, 73, 75, 77 -> "snow"
        80, 81, 82 -> "rain showers"
        85, 86 -> "snow showers"
        95 -> "a thunderstorm"
        96, 99 -> "a thunderstorm with hail"
        else -> "unsettled"
    }
}
