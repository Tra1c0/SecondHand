// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import java.util.Collections

/**
 * Stable identifiers of the tools built into the app. A tool is never selected, added or
 * configured by a model response or an imported file.
 */
enum class ToolId(val id: String, val external: Boolean) {
    /** Deterministic self-test: counts the approved text. No effect outside the keyboard. */
    TEST_COUNT("test.count", external = false),
    /** Hands a draft event to whichever calendar app the user picks. It does not save anything. */
    CALENDAR_INSERT("calendar.insert", external = true);

    companion object { fun fromId(id: String) = entries.firstOrNull { it.id == id } }
}

/** One labelled value of a tool request, exactly as it is previewed and handed over. */
data class ToolField(val key: String, val value: String) {
    init { require(key.matches(Regex("[a-z][a-z0-9_]{0,23}"))) }
    override fun toString() = "ToolField(key=$key, chars=${value.length})"
}

/** Immutable payload derived only from the approved selection. No URLs, code or credentials. */
class ToolRequest internal constructor(val tool: ToolId, fields: List<ToolField>) {
    val fields: List<ToolField> = Collections.unmodifiableList(fields.toList())
    fun value(key: String): String = fields.first { it.key == key }.value
    override fun toString() = "ToolRequest(tool=${tool.id}, fields=${fields.size})"
}

enum class ToolError { INVALID_INPUT }
class ToolFailure(val error: ToolError) : Exception(error.name)

/** Pure, deterministic preparation. Running a prepared request is the Android host's job. */
object Tools {
    const val MAX_TITLE_CHARS = 120
    const val MAX_DESCRIPTION_CHARS = 2_000

    fun prepare(tool: ToolId, selection: String): ToolRequest {
        val policy = ExecutionPolicy()
        if (selection.length !in 1..policy.maxSelectionChars || policy.resultDenial(selection) != null)
            throw ToolFailure(ToolError.INVALID_INPUT)
        return when (tool) {
            ToolId.TEST_COUNT -> ToolRequest(tool, listOf(ToolField("text", selection)))
            ToolId.CALENDAR_INSERT -> {
                val title = singleLine(selection.lineSequence().firstOrNull { it.isNotBlank() }
                    ?: throw ToolFailure(ToolError.INVALID_INPUT))
                ToolRequest(tool, listOf(
                    ToolField("title", truncate(title, MAX_TITLE_CHARS)),
                    ToolField("description", truncate(selection.trim(), MAX_DESCRIPTION_CHARS)),
                ))
            }
        }
    }

    /** The complete result of the test tool; identical input always gives identical output. */
    fun runTest(request: ToolRequest): String {
        require(request.tool == ToolId.TEST_COUNT)
        val text = request.value("text")
        val words = text.split(Regex("\\s+")).count { it.isNotEmpty() }
        val lines = text.count { it == '\n' } + 1
        return "chars=${text.length} words=$words lines=$lines"
    }

    private fun singleLine(text: String) = text.trim().replace(Regex("[\\p{Cntrl}\\u2028\\u2029]+"), " ")

    /** Cuts at a code point boundary so the handed-over text stays valid UTF-16. */
    private fun truncate(text: String, limit: Int): String {
        if (text.length <= limit) return text
        val end = if (text[limit - 1].isHighSurrogate()) limit - 1 else limit
        return text.substring(0, end)
    }
}
