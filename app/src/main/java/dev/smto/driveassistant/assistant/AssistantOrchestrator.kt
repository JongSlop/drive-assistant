package dev.smto.driveassistant.assistant

import android.content.Context
import dev.smto.driveassistant.App
import dev.smto.driveassistant.R
import dev.smto.driveassistant.data.SettingsRepository
import dev.smto.driveassistant.llm.ChatMessage
import dev.smto.driveassistant.tools.NowPlaying
import dev.smto.driveassistant.tools.ToolRegistry
import dev.smto.driveassistant.util.forLanguage
import dev.smto.driveassistant.voice.SpeechToText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The conversation loop: speech in -> model (+ tool calls) -> spoken reply.
 * One instance per app; holds the running transcript so follow-ups have context.
 */
class AssistantOrchestrator(context: Context) {

    enum class Phase { IDLE, LISTENING, THINKING, SPEAKING }

    data class Line(val role: String, val text: String)

    data class UiState(
        val phase: Phase = Phase.IDLE,
        val partial: String = "",
        val transcript: List<Line> = emptyList(),
        val error: String? = null,
    )

    private val appContext = context.applicationContext
    private val app = App.from(context)
    private val stt = SpeechToText(context)
    private val registry = ToolRegistry(context, app.settings)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val history = mutableListOf<ChatMessage>()

    private fun ensureSystemPrompt(cfg: SettingsRepository.Config) {
        if (history.isNotEmpty()) return
        val languageName = Locale.forLanguageTag(cfg.language)
            .getDisplayLanguage(Locale.forLanguageTag(cfg.language))
            .replaceFirstChar { it.uppercase() }
        val hint = appContext.forLanguage(cfg.language)
            .getString(R.string.asst_reply_language_hint, languageName)
        history += ChatMessage(role = "system", content = cfg.systemPrompt + "\n\n" + hint)
    }

    fun reset() {
        history.clear()
        _state.value = UiState()
    }

    /** Capture one spoken utterance, then answer it. Suspends until the reply is queued to TTS. */
    suspend fun listenAndRespond() {
        val cfg = app.settings.current()
        _state.value = _state.value.copy(phase = Phase.LISTENING, partial = "", error = null)
        var finalText: String? = null

        stt.listen(cfg.language).collect { event ->
            when (event) {
                is SpeechToText.Event.Partial ->
                    _state.value = _state.value.copy(partial = event.text)
                is SpeechToText.Event.Final -> finalText = event.text
                is SpeechToText.Event.Failed -> {
                    _state.value = _state.value.copy(phase = Phase.IDLE, partial = "", error = event.reason)
                    app.tts.speak(event.reason, flush = true, languageTag = cfg.language)
                }
                SpeechToText.Event.EndOfSpeech,
                SpeechToText.Event.ReadyForSpeech -> Unit
            }
        }

        val said = finalText?.trim().orEmpty()
        if (said.isNotEmpty()) respondTo(said)
    }

    /** Answer a typed / already-transcribed message. */
    suspend fun respondTo(userText: String) {
        val cfg = app.settings.current()
        val strings = appContext.forLanguage(cfg.language)
        ensureSystemPrompt(cfg)
        history += ChatMessage(role = "user", content = userText)
        _state.value = _state.value.copy(
            phase = Phase.THINKING,
            partial = "",
            transcript = (_state.value.transcript + Line("user", userText)).takeLast(MAX_TRANSCRIPT_LINES),
        )

        val reply = try {
            runToolLoop(strings, turnContext(cfg, strings))
        } catch (t: Throwable) {
            val msg = t.message ?: strings.getString(R.string.asst_model_error)
            _state.value = _state.value.copy(phase = Phase.IDLE, error = msg)
            app.tts.speak(msg, flush = true, languageTag = cfg.language)
            return
        }

        history += ChatMessage(role = "assistant", content = reply)
        trimHistory()
        _state.value = _state.value.copy(
            phase = Phase.SPEAKING,
            transcript = (_state.value.transcript + Line("assistant", reply)).takeLast(MAX_TRANSCRIPT_LINES),
        )
        app.tts.speak(reply, flush = true, languageTag = cfg.language)
        app.tts.speaking.first { !it }
        _state.value = _state.value.copy(phase = Phase.IDLE)
    }

    /**
     * An ephemeral system message with volatile context (clock, media). Rebuilt every
     * turn and injected right after the main system prompt — never stored in history.
     */
    private fun turnContext(cfg: SettingsRepository.Config, strings: Context): ChatMessage {
        val locale = Locale.forLanguageTag(cfg.language)
        val now = ZonedDateTime.now()
            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.SHORT).withLocale(locale))
        val lines = mutableListOf(strings.getString(R.string.ctx_datetime, now))
        NowPlaying.describe(appContext)?.let { lines += strings.getString(R.string.ctx_now_playing, it) }
        return ChatMessage(role = "system", content = lines.joinToString(" "))
    }

    private suspend fun runToolLoop(strings: Context, turnCtx: ChatMessage): String {
        val specs = registry.specs()
        repeat(MAX_TOOL_ROUNDS) {
            val messages = listOf(history[0], turnCtx) + history.drop(1)
            val msg = app.llm.complete(messages, specs)
            history += msg

            val calls = msg.toolCalls
            if (calls.isNullOrEmpty()) {
                return msg.content?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: strings.getString(R.string.asst_done)
            }

            for (call in calls) {
                val result = registry.dispatch(call.function.name, call.function.arguments)
                history += ChatMessage(
                    role = "tool",
                    toolCallId = call.id,
                    name = call.function.name,
                    content = result,
                )
            }
        }
        return strings.getString(R.string.asst_stuck)
    }

    /**
     * Bound the running context sent to the model: keep the system message plus the
     * most recent messages, and make sure the kept window starts on a `user` message
     * so no `assistant`/`tool` call-pairing is left dangling.
     */
    private fun trimHistory() {
        val overflow = history.size - 1 - MAX_HISTORY_MESSAGES
        if (overflow > 0) repeat(overflow) { history.removeAt(1) }
        while (history.size > 1 && history[1].role != "user") history.removeAt(1)
    }

    companion object {
        private const val MAX_TOOL_ROUNDS = 4

        /** Non-system messages retained across turns (roughly 5-7 turns). */
        private const val MAX_HISTORY_MESSAGES = 30

        /** On-screen conversation lines retained. */
        private const val MAX_TRANSCRIPT_LINES = 60
    }
}
