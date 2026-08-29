package dev.smto.driveassistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Wraps the system [TextToSpeech] engine. Speech is queued; [speak] with flush=true
 * interrupts whatever is talking (used for the assistant's own replies), flush=false
 * appends (used for notification readout).
 */
class TextToSpeechManager(context: Context) {

    private val ready = CompletableDeferred<Boolean>()
    private val counter = AtomicLong(0)
    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking

    @Volatile
    private var appliedTag: String? = null

    private val engine = TextToSpeech(context.applicationContext) { status ->
        ready.complete(status == TextToSpeech.SUCCESS)
    }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { _speaking.value = true }
            override fun onDone(utteranceId: String?) { _speaking.value = false }
            @Deprecated("deprecated in API level 21")
            override fun onError(utteranceId: String?) { _speaking.value = false }
            override fun onError(utteranceId: String?, errorCode: Int) { _speaking.value = false }
        })
    }

    suspend fun awaitReady(): Boolean = ready.await()

    /** @param languageTag BCP-47 tag; falls back to the system locale if the voice is missing. */
    suspend fun speak(text: String, flush: Boolean, languageTag: String? = null) {
        if (text.isBlank()) return
        if (!awaitReady()) return
        applyLanguage(languageTag)
        val id = "u-${counter.incrementAndGet()}"
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        engine.speak(text, mode, null, id)
    }

    private fun applyLanguage(tag: String?) {
        if (tag == appliedTag) return
        val locale = tag?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()
        val result = runCatching { engine.setLanguage(locale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "TTS voice for '$tag' unavailable (code $result); using default")
            runCatching { engine.setLanguage(Locale.getDefault()) }
        }
        appliedTag = tag
    }

    fun stop() {
        engine.stop()
        _speaking.value = false
    }

    fun shutdown() {
        engine.stop()
        engine.shutdown()
    }

    private companion object {
        const val TAG = "TextToSpeechManager"
    }
}
