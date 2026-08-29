package dev.smto.driveassistant

import android.app.Application
import dev.smto.driveassistant.assistant.AssistantOrchestrator
import dev.smto.driveassistant.car.CarConnectionState
import dev.smto.driveassistant.data.SettingsRepository
import dev.smto.driveassistant.llm.LlmClient
import dev.smto.driveassistant.voice.TextToSpeechManager

/**
 * Poor-man's DI container. Everything the assistant needs is created once here and
 * pulled from [App.from]. Keeps the code dependency-free and easy to follow.
 */
class App : Application() {

    lateinit var settings: SettingsRepository
        private set
    lateinit var llm: LlmClient
        private set
    lateinit var tts: TextToSpeechManager
        private set
    lateinit var assistant: AssistantOrchestrator
        private set
    lateinit var car: CarConnectionState
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsRepository(this)
        llm = LlmClient(settings)
        tts = TextToSpeechManager(this)
        assistant = AssistantOrchestrator(this)
        car = CarConnectionState(this).apply { start() }
    }

    companion object {
        @Volatile
        private var instance: App? = null

        fun from(context: android.content.Context): App =
            context.applicationContext as App

        fun get(): App = instance ?: error("App not created yet")
    }
}
