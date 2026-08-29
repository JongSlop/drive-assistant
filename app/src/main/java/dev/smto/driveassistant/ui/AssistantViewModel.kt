package dev.smto.driveassistant.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.smto.driveassistant.App
import dev.smto.driveassistant.R
import dev.smto.driveassistant.assistant.AssistantOrchestrator
import dev.smto.driveassistant.assistant.AssistantOrchestrator.Phase
import dev.smto.driveassistant.data.SettingsRepository
import dev.smto.driveassistant.data.SettingsRepository.SttMode
import dev.smto.driveassistant.util.forLanguage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AssistantViewModel(app: Application) : AndroidViewModel(app) {

    private val orchestrator = App.from(app).assistant
    val state = orchestrator.state

    private val settings = App.from(app).settings

    val sttMode = settings.config
        .map { it.sttMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SttMode.SYSTEM)

    val language = settings.config
        .map { it.language }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.DEFAULT_LANGUAGE)

    val recognizerPackage = settings.config
        .map { it.recognizerPackage }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private var running: Job? = null

    private val busy: Boolean get() = running?.isActive == true

    /**
     * Single entry point for the mic button. Tapping while idle starts listening;
     * tapping during any active phase abandons the current turn.
     */
    fun onMicTap() {
        if (busy || state.value.phase != Phase.IDLE) {
            cancel()
        } else {
            startListening()
        }
    }

    fun startListening() {
        if (busy) return
        running = viewModelScope.launch {
            try {
                orchestrator.listenAndRespond()
            } finally {
                running = null
            }
        }
    }

    /** Abort the in-flight turn: stop the coroutine, silence TTS, return to idle. */
    fun cancel() {
        running?.cancel()
        running = null
        App.get().tts.stop()
        orchestrator.abort()
    }

    fun send(text: String) {
        if (text.isBlank()) return
        running?.cancel()
        running = viewModelScope.launch {
            try {
                orchestrator.respondTo(text.trim())
            } finally {
                running = null
            }
        }
    }

    fun stopSpeaking() = App.get().tts.stop()

    /** Spoken feedback when no app can serve the recognition popup. */
    fun announceRecognizerUnavailable() {
        val ctx = getApplication<Application>().forLanguage(language.value)
        viewModelScope.launch {
            App.get().tts.speak(
                ctx.getString(R.string.stt_popup_none),
                flush = true,
                languageTag = language.value,
            )
        }
    }

    fun reset() {
        running?.cancel()
        running = null
        orchestrator.reset()
    }
}
