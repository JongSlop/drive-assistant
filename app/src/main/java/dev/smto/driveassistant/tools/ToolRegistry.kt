package dev.smto.driveassistant.tools

import android.content.Context
import dev.smto.driveassistant.data.SettingsRepository
import dev.smto.driveassistant.llm.ToolSpec

/** Owns the set of tools and dispatches calls from the model by name. */
class ToolRegistry(context: Context, settings: SettingsRepository) {

    private val appContext = context.applicationContext

    private val tools: Map<String, Tool> = listOf(
        MediaControlTool(appContext),
        NowPlayingTool(appContext),
        WeatherTool(appContext, settings),
        PhoneCallTool(appContext),
        NavigationTool(appContext),
        // get_recent_notifications intentionally omitted: notification content must
        // not leave the device. Readout stays local in NotificationReaderService.
    ).associateBy { it.name }

    fun specs(): List<ToolSpec> = tools.values.map { it.spec() }

    suspend fun dispatch(name: String, rawArgs: String): String {
        val tool = tools[name] ?: return "Unknown tool: $name"
        return runCatching { tool.run(parseArgs(rawArgs)) }
            .getOrElse { "Tool $name failed: ${it.message ?: it::class.simpleName}" }
    }
}
