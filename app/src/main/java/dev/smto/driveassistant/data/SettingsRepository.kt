package dev.smto.driveassistant.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Holds the OpenAI-compatible endpoint config plus assistant preferences.
 *
 * NOTE: the API key is stored in a plain DataStore. On a rooted device that is not
 * a real secret boundary anyway; if you want at-rest encryption, wrap this with
 * androidx.security:security-crypto or the Keystore later.
 */
class SettingsRepository(private val context: Context) {

    /** How spoken input reaches the app. */
    enum class SttMode {
        /** Direct `SpeechRecognizer` call to the system recognition service. */
        SYSTEM,

        /**
         * `ACTION_RECOGNIZE_SPEECH` activity — the recognizer app shows its own popup
         * (e.g. FUTO Voice Input's floating overlay) and returns the transcript.
         */
        EXTERNAL_POPUP,

        /** A focused text field; the user dictates with a voice keyboard/IME (e.g. FUTO). */
        IME,
    }

    data class Config(
        val baseUrl: String,
        val apiKey: String,
        val model: String,
        val systemPrompt: String,
        val notificationReadout: Boolean,
        /** Only read notifications aloud while projecting to Android Auto. */
        val notificationReadoutOnlyInCar: Boolean,
        val allowedNotificationPackages: Set<String>,
        val homeLocation: String,
        /** BCP-47 tag driving STT hint, TTS voice, and reply language. */
        val language: String,
        val sttMode: SttMode,
        /** Package of the app to serve the EXTERNAL_POPUP recognizer; blank = system chooser. */
        val recognizerPackage: String,
    )

    val config: Flow<Config> = context.dataStore.data.map { p ->
        Config(
            baseUrl = p[KEY_BASE_URL] ?: DEFAULT_BASE_URL,
            apiKey = p[KEY_API_KEY].orEmpty(),
            model = p[KEY_MODEL] ?: DEFAULT_MODEL,
            systemPrompt = p[KEY_SYSTEM_PROMPT] ?: DEFAULT_SYSTEM_PROMPT,
            notificationReadout = (p[KEY_NOTIF_READOUT] ?: "true").toBoolean(),
            notificationReadoutOnlyInCar = (p[KEY_NOTIF_ONLY_CAR] ?: "true").toBoolean(),
            allowedNotificationPackages = p[KEY_NOTIF_PACKAGES]
                ?.split('\n')?.filter { it.isNotBlank() }?.toSet()
                ?: emptySet(),
            homeLocation = p[KEY_HOME_LOCATION].orEmpty(),
            language = p[KEY_LANGUAGE] ?: DEFAULT_LANGUAGE,
            sttMode = p[KEY_STT_MODE]?.let { runCatching { SttMode.valueOf(it) }.getOrNull() }
                ?: SttMode.SYSTEM,
            recognizerPackage = p[KEY_RECOGNIZER_PKG].orEmpty(),
        )
    }

    suspend fun current(): Config = config.first()

    suspend fun setBaseUrl(v: String) = put(KEY_BASE_URL, v.trim())
    suspend fun setApiKey(v: String) = put(KEY_API_KEY, v.trim())
    suspend fun setModel(v: String) = put(KEY_MODEL, v.trim())
    suspend fun setSystemPrompt(v: String) = put(KEY_SYSTEM_PROMPT, v)
    suspend fun setNotificationReadout(v: Boolean) = put(KEY_NOTIF_READOUT, v.toString())
    suspend fun setNotificationReadoutOnlyInCar(v: Boolean) = put(KEY_NOTIF_ONLY_CAR, v.toString())
    suspend fun setHomeLocation(v: String) = put(KEY_HOME_LOCATION, v.trim())
    suspend fun setLanguage(v: String) = put(KEY_LANGUAGE, v.trim())
    suspend fun setSttMode(v: SttMode) = put(KEY_STT_MODE, v.name)
    suspend fun setRecognizerPackage(v: String) = put(KEY_RECOGNIZER_PKG, v.trim())
    suspend fun setAllowedNotificationPackages(v: Set<String>) =
        put(KEY_NOTIF_PACKAGES, v.joinToString("\n"))

    private suspend fun put(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-chat"
        const val DEFAULT_LANGUAGE = "de-DE"

        /** label -> BCP-47 tag, shown in the settings picker. */
        val LANGUAGES = linkedMapOf(
            "Deutsch" to "de-DE",
            "English (US)" to "en-US",
            "English (UK)" to "en-GB",
            "Français" to "fr-FR",
            "Español" to "es-ES",
            "Italiano" to "it-IT",
            "Nederlands" to "nl-NL",
            "Polski" to "pl-PL",
            "Português" to "pt-PT",
            "Türkçe" to "tr-TR",
        )
        val DEFAULT_SYSTEM_PROMPT = """
            You are a hands-free voice assistant used while driving. Keep every reply short,
            spoken-style, and free of markdown, lists, or emoji. One or two sentences max.
            Prefer doing the task with a tool over describing it. If the user asks for media
            control, weather, or notifications, call the matching tool. Never ask the driver
            to look at the screen.
        """.trimIndent()

        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_MODEL = stringPreferencesKey("model")
        private val KEY_SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        private val KEY_NOTIF_READOUT = stringPreferencesKey("notif_readout")
        private val KEY_NOTIF_ONLY_CAR = stringPreferencesKey("notif_only_car")
        private val KEY_NOTIF_PACKAGES = stringPreferencesKey("notif_packages")
        private val KEY_HOME_LOCATION = stringPreferencesKey("home_location")
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        private val KEY_STT_MODE = stringPreferencesKey("stt_mode")
        private val KEY_RECOGNIZER_PKG = stringPreferencesKey("recognizer_package")
    }
}
