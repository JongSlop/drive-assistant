package dev.smto.driveassistant.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Optional: place a phone call by number or contact name. Needs CALL_PHONE (+ READ_CONTACTS for names). */
class PhoneCallTool(private val context: Context) : Tool {

    override val name = "place_call"
    override val description =
        "Start a phone call to a contact name or an explicit phone number. Driving-safe, " +
            "dials immediately."

    override val parameters: JsonObject = Json.parseToJsonElement(
        """
        {
          "type": "object",
          "properties": {
            "contact": { "type": "string", "description": "Contact name to look up." },
            "number": { "type": "string", "description": "Explicit phone number." }
          }
        }
        """.trimIndent(),
    ) as JsonObject

    override suspend fun run(args: JsonObject): String {
        val number = args.str("number") ?: args.str("contact")?.let { lookup(it) }
        ?: return "I couldn't find that contact."

        val canCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        val action = if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL
        context.startActivity(
            Intent(action, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return if (canCall) "Calling $number." else "Opening the dialer for $number."
    }

    private fun lookup(name: String): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) return null

        val uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(name))
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null,
        )?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return null
    }
}

/** Optional: start turn-by-turn navigation to a destination via the default maps app. */
class NavigationTool(private val context: Context) : Tool {

    override val name = "start_navigation"
    override val description = "Begin navigation / directions to a destination address or place name."

    override val parameters: JsonObject = Json.parseToJsonElement(
        """
        {
          "type": "object",
          "properties": { "destination": { "type": "string" } },
          "required": ["destination"]
        }
        """.trimIndent(),
    ) as JsonObject

    override suspend fun run(args: JsonObject): String {
        val dest = args.str("destination") ?: return "Where to?"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(dest)}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(dest)}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent); "Navigating to $dest." }
            .recoverCatching { context.startActivity(fallback); "Showing $dest on the map." }
            .getOrDefault("No maps app is available.")
    }
}
