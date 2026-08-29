package dev.smto.driveassistant.llm

import dev.smto.driveassistant.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thin client for the OpenAI-compatible `/v1/chat/completions` endpoint.
 * Non-streaming: we need the full assistant turn (incl. tool_calls) before acting,
 * and replies are short spoken sentences so latency is dominated by TTS anyway.
 */
class LlmClient(private val settings: SettingsRepository) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    class LlmException(message: String) : IOException(message)

    suspend fun complete(messages: List<ChatMessage>, tools: List<ToolSpec>): ChatMessage =
        withContext(Dispatchers.IO) {
            val cfg = settings.current()
            if (cfg.apiKey.isBlank()) throw LlmException("No API key set. Open settings first.")

            val body = ChatRequest(
                model = cfg.model,
                messages = messages,
                tools = tools.ifEmpty { null },
                toolChoice = if (tools.isEmpty()) null else "auto",
            )
            val request = Request.Builder()
                .url(cfg.baseUrl.trimEnd('/') + "/v1/chat/completions")
                .addHeader("Authorization", "Bearer ${cfg.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(json.encodeToString(ChatRequest.serializer(), body).toRequestBody(JSON_MEDIA))
                .build()

            http.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val parsed = runCatching {
                        json.decodeFromString(ChatResponse.serializer(), raw).error?.message
                    }.getOrNull()
                    throw LlmException(parsed ?: "HTTP ${resp.code}: ${raw.take(300)}")
                }
                val decoded = json.decodeFromString(ChatResponse.serializer(), raw)
                decoded.error?.let { throw LlmException(it.message) }
                decoded.choices.firstOrNull()?.message
                    ?: throw LlmException("Empty response from model")
            }
        }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
