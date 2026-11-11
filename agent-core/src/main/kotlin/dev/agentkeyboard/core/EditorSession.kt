// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import java.util.UUID

data class EditorToken(val ownerId: String, val generation: Long, val revision: Long)

/** Main-thread owned. A token is consumed before an editor mutation is attempted. */
class EditorSession {
    private val ownerId = UUID.randomUUID().toString()
    private var generation = 0L
    private var revision = 0L
    private var active = false

    val token: EditorToken? get() = if (active) EditorToken(ownerId, generation, revision) else null

    fun start() {
        generation++
        revision = 0
        active = true
    }

    fun invalidate() {
        revision++
    }

    fun finish() {
        invalidate()
        active = false
    }

    fun isCurrent(candidate: EditorToken) = token == candidate

    fun consume(candidate: EditorToken): Boolean {
        if (!isCurrent(candidate)) return false
        invalidate()
        return true
    }
}

/** An immutable plain-text copy. Its default string form deliberately omits text. */
class TextSnapshot internal constructor(
    val token: EditorToken,
    val selectionStart: Int,
    val selectionEnd: Int,
    val text: String,
) {
    override fun toString() = "TextSnapshot(chars=${text.length}, token=$token)"
}

/** A cursor position with one-time authority to insert there. It carries no editor text. */
class InsertionAnchor internal constructor(val token: EditorToken, val position: Int) {
    override fun toString() = "InsertionAnchor(token=$token)"
}
