package dev.smto.driveassistant.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.smto.driveassistant.App
import dev.smto.driveassistant.R
import dev.smto.driveassistant.assistant.AssistantOrchestrator
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

    fun toggleListen() {
        val job = running
        if (job != null && job.isActive) {
            job.cancel()
            running = null
            return
        }
        running = viewModelScope.launch {
            orchestrator.listenAndRespond()
            running = null
        }
    }

    fun send(text: String) {
        if (text.isBlank()) return
        running?.cancel()
        running = viewModelScope.launch {
            orchestrator.respondTo(text.trim())
            running = null
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
