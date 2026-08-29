package dev.smto.driveassistant.voice

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import dev.smto.driveassistant.R
import dev.smto.driveassistant.util.forLanguage
import java.util.Locale

/**
 * The `ACTION_RECOGNIZE_SPEECH` *activity* path (as used by Dicio's "external popup"
 * input): the resolved recognizer app — e.g. FUTO Voice Input's `RecognizeActivity`
 * — draws its own overlay UI and returns the transcript as an activity result.
 * Unlike the `SpeechRecognizer` service API, FUTO actually implements this.
 */
object RecognizerIntents {

    data class App(val label: String, val packageName: String)

    fun build(context: Context, languageTag: String?, packageName: String? = null): Intent {
        val locale = languageTag?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()
        val prompt = context.forLanguage(languageTag).getString(R.string.stt_say_something)
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            if (!packageName.isNullOrBlank()) setPackage(packageName)
        }
    }

    /** Apps that can serve the recognizer popup, for the settings picker. */
    fun available(context: Context): List<App> {
        val pm = context.packageManager
        val base = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        val flags = PackageManager.MATCH_DEFAULT_ONLY
        return pm.queryIntentActivities(base, flags)
            .mapNotNull { info ->
                val ai = info.activityInfo ?: return@mapNotNull null
                App(ai.loadLabel(pm).toString(), ai.packageName)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun canResolve(context: Context, packageName: String? = null): Boolean =
        build(context, null, packageName).resolveActivity(context.packageManager) != null

    /** Pull the best transcript out of an activity result, or null if there is none. */
    fun extractText(data: Intent?): String? =
        data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull { it.isNotBlank() }
            ?.trim()
}
