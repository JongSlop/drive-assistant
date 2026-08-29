package dev.smto.driveassistant.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.smto.driveassistant.App
import dev.smto.driveassistant.data.SettingsRepository
import dev.smto.driveassistant.data.SettingsRepository.SttMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = App.from(app).settings

    val config = repo.config.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null,
    )

    fun setBaseUrl(v: String) = save { repo.setBaseUrl(v) }
    fun setApiKey(v: String) = save { repo.setApiKey(v) }
    fun setModel(v: String) = save { repo.setModel(v) }
    fun setSystemPrompt(v: String) = save { repo.setSystemPrompt(v) }
    fun setHomeLocation(v: String) = save { repo.setHomeLocation(v) }
    fun setLanguage(v: String) = save { repo.setLanguage(v) }
    fun setSttMode(v: SttMode) = save { repo.setSttMode(v) }
    fun setRecognizerPackage(v: String) = save { repo.setRecognizerPackage(v) }

    fun recognizerApps(): List<dev.smto.driveassistant.voice.RecognizerIntents.App> =
        dev.smto.driveassistant.voice.RecognizerIntents.available(getApplication())
    fun setNotificationReadout(v: Boolean) = save { repo.setNotificationReadout(v) }
    fun setNotificationReadoutOnlyInCar(v: Boolean) = save { repo.setNotificationReadoutOnlyInCar(v) }

    val languages: Map<String, String> get() = SettingsRepository.LANGUAGES

    val defaultSystemPrompt: String get() = SettingsRepository.DEFAULT_SYSTEM_PROMPT

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
