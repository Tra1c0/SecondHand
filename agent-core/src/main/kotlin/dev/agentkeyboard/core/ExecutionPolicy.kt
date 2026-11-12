// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import kotlin.math.max
import kotlin.math.min

data class EditorAccess(
    val isText: Boolean = false,
    val isPassword: Boolean = false,
    val isIncognito: Boolean = false,
    val forbidsLearning: Boolean = false,
    val isLocked: Boolean = false,
    val isComposing: Boolean = false,
)

enum class DenialReason {
    NO_EDITOR, LOCKED, PASSWORD, INCOGNITO, NO_LEARNING, UNSUPPORTED_EDITOR,
    COMPOSITION, NO_SELECTION, TOO_LARGE, INVALID_TEXT, STALE_CONTEXT, NO_CURSOR,
}

sealed interface CaptureResult {
    class Captured(val snapshot: TextSnapshot) : CaptureResult
    data class Denied(val reason: DenialReason) : CaptureResult
}

sealed interface AnchorResult {
    class Captured(val anchor: InsertionAnchor) : AnchorResult
    data class Denied(val reason: DenialReason) : AnchorResult
}

/** The same rules apply to local tools and to future provider requests. */
class ExecutionPolicy(
    val maxSelectionChars: Int = 8_192,
    val maxResultChars: Int = 16_384,
) {
    init {
        require(maxSelectionChars > 0 && maxResultChars >= maxSelectionChars)
    }

    fun editorDenial(access: EditorAccess): DenialReason? = when {
        access.isLocked -> DenialReason.LOCKED
        access.isPassword -> DenialReason.PASSWORD
        access.isIncognito -> DenialReason.INCOGNITO
        access.forbidsLearning -> DenialReason.NO_LEARNING
        !access.isText -> DenialReason.UNSUPPORTED_EDITOR
        access.isComposing -> DenialReason.COMPOSITION
        else -> null
    }

    fun capture(
        token: EditorToken?, access: EditorAccess,
        selectionStart: Int, selectionEnd: Int, selectedText: String?,
    ): CaptureResult {
        if (token == null) return CaptureResult.Denied(DenialReason.NO_EDITOR)
        editorDenial(access)?.let { return CaptureResult.Denied(it) }
        val start = min(selectionStart, selectionEnd)
        val end = max(selectionStart, selectionEnd)
        if (start < 0 || start == end || selectedText.isNullOrEmpty())
            return CaptureResult.Denied(DenialReason.NO_SELECTION)
        if (end.toLong() - start > maxSelectionChars || selectedText.length > maxSelectionChars)
            return CaptureResult.Denied(DenialReason.TOO_LARGE)
        if (selectedText.length != end - start || !hasValidUtf16(selectedText))
            return CaptureResult.Denied(DenialReason.INVALID_TEXT)
        return CaptureResult.Captured(TextSnapshot(token, start, end, selectedText))
    }

    fun applyDenial(
        snapshot: TextSnapshot, token: EditorToken?, access: EditorAccess,
        selectionStart: Int, selectionEnd: Int, selectedText: String?,
    ): DenialReason? {
        editorDenial(access)?.let { return it }
        if (snapshot.token != token || snapshot.selectionStart != min(selectionStart, selectionEnd)
            || snapshot.selectionEnd != max(selectionStart, selectionEnd) || snapshot.text != selectedText)
            return DenialReason.STALE_CONTEXT
        return null
    }

    /** Insertion needs a plain cursor: a selection would be replaced, which is a different approval. */
    fun captureAnchor(token: EditorToken?, access: EditorAccess, selectionStart: Int, selectionEnd: Int): AnchorResult {
        if (token == null) return AnchorResult.Denied(DenialReason.NO_EDITOR)
        editorDenial(access)?.let { return AnchorResult.Denied(it) }
        if (selectionStart < 0 || selectionStart != selectionEnd) return AnchorResult.Denied(DenialReason.NO_CURSOR)
        return AnchorResult.Captured(InsertionAnchor(token, selectionStart))
    }

    /**
     * Stage 24f `@ki`: a typed [question] as the source of one approved request. It is no text read
     * from the field but the argument of the command, taken at a plain cursor; the snapshot has an
     * empty selection there, so it can never pass [applyDenial]. The last word of the command may
     * still be composing, which is why composition is no denial here.
     */
    fun captureQuestion(token: EditorToken?, access: EditorAccess, selectionStart: Int, selectionEnd: Int,
                        question: String): CaptureResult {
        if (token == null) return CaptureResult.Denied(DenialReason.NO_EDITOR)
        editorDenial(access.copy(isComposing = false))?.let { return CaptureResult.Denied(it) }
        if (selectionStart < 0 || selectionStart != selectionEnd) return CaptureResult.Denied(DenialReason.NO_CURSOR)
        if (question.isBlank() || !hasValidUtf16(question)) return CaptureResult.Denied(DenialReason.INVALID_TEXT)
        if (question.length > maxSelectionChars) return CaptureResult.Denied(DenialReason.TOO_LARGE)
        return CaptureResult.Captured(TextSnapshot(token, selectionStart, selectionStart, question))
    }

    /**
     * Whether a [captureQuestion] snapshot still belongs to the same editor and the editor still
     * allows processing. Unlike a selection, the question does not depend on the field, so a moved
     * cursor or a changed revision does not end it; the insertion checks the field again on its own.
     */
    fun isCurrentQuestion(snapshot: TextSnapshot, token: EditorToken?, access: EditorAccess): Boolean =
        snapshot.selectionStart == snapshot.selectionEnd && token != null &&
            snapshot.token.ownerId == token.ownerId && snapshot.token.generation == token.generation &&
            editorDenial(access.copy(isComposing = false)) == null

    fun insertDenial(anchor: InsertionAnchor, token: EditorToken?, access: EditorAccess,
                     selectionStart: Int, selectionEnd: Int, text: String): DenialReason? {
        editorDenial(access)?.let { return it }
        if (anchor.token != token || selectionStart != anchor.position || selectionEnd != anchor.position)
            return DenialReason.STALE_CONTEXT
        if (text.isEmpty()) return DenialReason.INVALID_TEXT
        if (text.length > maxSelectionChars) return DenialReason.TOO_LARGE
        return resultDenial(text)
    }

    fun resultDenial(result: String): DenialReason? = when {
        result.length > maxResultChars -> DenialReason.TOO_LARGE
        !hasValidUtf16(result) -> DenialReason.INVALID_TEXT
        else -> null
    }

    private fun hasValidUtf16(text: String): Boolean {
        var index = 0
        while (index < text.length) {
            val char = text[index++]
            if (char.isHighSurrogate()) {
                if (index == text.length || !text[index++].isLowSurrogate()) return false
            } else if (char.isLowSurrogate()) return false
        }
        return true
    }
}
