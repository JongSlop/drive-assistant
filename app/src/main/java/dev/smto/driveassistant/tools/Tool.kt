package dev.smto.driveassistant.tools

import dev.smto.driveassistant.llm.FunctionSpec
import dev.smto.driveassistant.llm.ToolSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One capability the model can invoke. [run] returns a short plain-text result that
 * is fed back to the model as the tool message.
 */
interface Tool {
    val name: String
    val description: String
    /** JSON-Schema object describing the arguments. */
    val parameters: JsonObject

    suspend fun run(args: JsonObject): String

    fun spec(): ToolSpec = ToolSpec(
        function = FunctionSpec(name = name, description = description, parameters = parameters),
    )
}

/* ---- small helpers for reading arguments defensively ---- */

private val lenientJson = Json { isLenient = true; ignoreUnknownKeys = true }

fun parseArgs(raw: String): JsonObject =
    runCatching { lenientJson.parseToJsonElement(raw.ifBlank { "{}" }) as JsonObject }
        .getOrElse { JsonObject(emptyMap()) }

fun JsonObject.str(key: String): String? =
    this[key]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }?.takeIf { it.isNotBlank() }

fun JsonObject.int(key: String): Int? =
    this[key]?.let { runCatching { it.jsonPrimitive.content.trim().toInt() }.getOrNull() }

fun JsonObject.bool(key: String): Boolean? =
    this[key]?.let { runCatching { it.jsonPrimitive.content.trim().toBooleanStrict() }.getOrNull() }
