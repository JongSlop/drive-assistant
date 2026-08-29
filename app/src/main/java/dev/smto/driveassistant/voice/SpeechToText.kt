package dev.smto.driveassistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import dev.smto.driveassistant.R
import dev.smto.driveassistant.util.forLanguage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import java.util.Locale

/**
 * System speech recognition wrapped as a cold [Flow] of [Event]s. Collect once per
 * listening turn; cancelling the collector stops the recognizer.
 *
 * Recognition providers disagree about language tags — some offline engines (e.g.
 * Dicio/Vosk) only ship one model and reject anything that isn't an exact match,
 * surfacing as ERROR_LANGUAGE_UNAVAILABLE (13). So we try a chain of candidates
 * (explicit override, bare language, device locale, English) before giving up.
 */
class SpeechToText(private val context: Context) {

    sealed interface Event {
        data object ReadyForSpeech : Event
        data object EndOfSpeech : Event
        data class Partial(val text: String) : Event
        data class Final(val text: String) : Event
        data class Failed(val reason: String, val code: Int = -1) : Event
    }

    fun available(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * @param preferredLanguage BCP-47 tag from settings; tried first and used for
     *   the spoken error messages.
     */
    fun listen(preferredLanguage: String? = null): Flow<Event> = flow {
        val strings = context.forLanguage(preferredLanguage)
        if (!available()) {
            emit(Event.Failed(strings.getString(R.string.stt_unavailable)))
            return@flow
        }

        val candidates = languageCandidates(preferredLanguage)
        for ((index, lang) in candidates.withIndex()) {
            val isLast = index == candidates.lastIndex
            var recoverable = false

            attempt(lang, strings).collect { event ->
                if (event is Event.Failed && !isLast && event.code in RECOVERABLE_CODES) {
                    Log.w(TAG, "recognizer rejected lang='$lang' (code ${event.code}); trying next")
                    recoverable = true
                } else {
                    emit(event)
                }
            }
            if (!recoverable) return@flow
        }
    }

    private fun attempt(languageTag: String?, strings: Context): Flow<Event> = callbackFlow {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            if (languageTag != null) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            }
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { trySend(Event.ReadyForSpeech) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { trySend(Event.EndOfSpeech) }

            override fun onError(error: Int) {
                trySend(Event.Failed(errorText(strings, error), error))
                close()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                firstResult(partialResults)?.let { trySend(Event.Partial(it)) }
            }

            override fun onResults(results: Bundle?) {
                val text = firstResult(results)
                if (text.isNullOrBlank()) {
                    trySend(Event.Failed(strings.getString(R.string.stt_no_match), SpeechRecognizer.ERROR_NO_MATCH))
                } else {
                    trySend(Event.Final(text))
                }
                close()
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer.startListening(intent)
        awaitClose {
            recognizer.stopListening()
            recognizer.destroy()
        }
    }

    private fun languageCandidates(preferred: String?): List<String?> {
        val locale = Locale.getDefault()
        return listOfNotNull(
            preferred?.takeIf { it.isNotBlank() },
            preferred?.takeIf { it.contains('-') }?.substringBefore('-'), // "de-DE" -> "de"
            locale.toLanguageTag(),
            locale.language,
            "en-US",
            "en",
            null, // last resort: let the service decide
        ).distinct()
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun errorText(strings: Context, code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> strings.getString(R.string.stt_no_match)
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> strings.getString(R.string.stt_no_speech)
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            strings.getString(R.string.stt_network)
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> strings.getString(R.string.stt_no_mic)
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> strings.getString(R.string.stt_busy)
        10 -> strings.getString(R.string.stt_rate_limited)         // ERROR_TOO_MANY_REQUESTS
        11 -> strings.getString(R.string.stt_server_disconnected)  // ERROR_SERVER_DISCONNECTED
        12 -> strings.getString(R.string.stt_lang_unsupported)     // ERROR_LANGUAGE_NOT_SUPPORTED
        13 -> strings.getString(R.string.stt_lang_unavailable)     // ERROR_LANGUAGE_UNAVAILABLE
        else -> strings.getString(R.string.stt_error_generic, code)
    }

    companion object {
        private const val TAG = "SpeechToText"

        /** Language-selection failures worth retrying with a different tag. */
        private val RECOVERABLE_CODES = setOf(
            12, // ERROR_LANGUAGE_NOT_SUPPORTED
            13, // ERROR_LANGUAGE_UNAVAILABLE
        )
    }
}
