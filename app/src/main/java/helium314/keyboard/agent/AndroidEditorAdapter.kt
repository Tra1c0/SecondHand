// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.text.InputType
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import dev.agentkeyboard.core.*
import kotlin.math.max
import kotlin.math.min

/** Main-thread adapter. Connections are fetched afresh and never stored in a snapshot. */
class AndroidEditorAdapter(
    private val connection: () -> InputConnection?,
    private val liveAccess: (EditorAccess) -> EditorAccess,
    private val commitSelection: (TextSnapshot, String) -> ApplyOutcome,
) {
    val policy = ExecutionPolicy()
    private val session = EditorSession()
    private var baseAccess = EditorAccess()
    private var selectionStart = -1
    private var selectionEnd = -1
    private var composingStart = -1
    private var composingEnd = -1
    var onInvalidated: (() -> Unit)? = null
    private var notifying = false
    private val notificationHandler = Handler(Looper.getMainLooper())
    private val notification = Runnable {
        if (!notifying) {
            notifying = true
            try { runCatching { onInvalidated?.invoke() } }
            finally { notifying = false }
        }
    }

    private fun notifyInvalidation() {
        // Tokens expire immediately. Observers are informational and run on the
        // next UI turn, so they cannot reenter an in-progress typing/tail write.
        if (notifying || onInvalidated == null) return
        notificationHandler.removeCallbacks(notification)
        notificationHandler.post(notification)
    }

    fun startEditor(inputType: Int, imeOptions: Int, start: Int, end: Int) {
        val kind = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val password = when (kind) {
            InputType.TYPE_CLASS_TEXT -> variation in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
        baseAccess = EditorAccess(isText = kind == InputType.TYPE_CLASS_TEXT, isPassword = password,
            forbidsLearning = imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0)
        selectionStart = start
        selectionEnd = end
        composingStart = -1
        composingEnd = -1
        session.start()
        notifyInvalidation()
    }

    fun selectionChanged(start: Int, end: Int, compositionStart: Int, compositionEnd: Int) {
        val changed = start != selectionStart || end != selectionEnd ||
            compositionStart != composingStart || compositionEnd != composingEnd
        selectionStart = start
        selectionEnd = end
        composingStart = compositionStart
        composingEnd = compositionEnd
        if (changed) invalidate()
    }

    fun invalidate() {
        session.invalidate()
        notifyInvalidation()
    }

    fun finishEditor() {
        session.finish()
        selectionStart = -1
        selectionEnd = -1
        notifyInvalidation()
    }

    private fun access(): EditorAccess? = runCatching {
        liveAccess(baseAccess).let { it.copy(isComposing = it.isComposing ||
            composingStart >= 0 && composingEnd > composingStart) }
    }.getOrNull()

    fun capture(): CaptureResult {
        val token = session.token ?: return CaptureResult.Denied(DenialReason.NO_EDITOR)
        val access = access() ?: return CaptureResult.Denied(DenialReason.NO_EDITOR)
        policy.editorDenial(access)?.let { return CaptureResult.Denied(it) }
        val start = min(selectionStart, selectionEnd)
        val end = max(selectionStart, selectionEnd)
        if (start < 0 || start == end) return CaptureResult.Denied(DenialReason.NO_SELECTION)
        if (end.toLong() - start > policy.maxSelectionChars) return CaptureResult.Denied(DenialReason.TOO_LARGE)
        val selected = readSelection() ?: return CaptureResult.Denied(DenialReason.NO_SELECTION)
        return policy.capture(token, access, start, end, selected)
    }

    private fun readSelection(): String? = runCatching {
        val raw = connection()?.getSelectedText(0) ?: return@runCatching null
        if (raw.length > policy.maxSelectionChars) null else raw.toString()
    }.getOrNull()

    fun isCurrent(snapshot: TextSnapshot): Boolean {
        val access = access() ?: return false
        return session.isCurrent(snapshot.token) && policy.editorDenial(access) == null &&
            snapshot.selectionStart == min(selectionStart, selectionEnd) &&
            snapshot.selectionEnd == max(selectionStart, selectionEnd)
    }

    fun apply(snapshot: TextSnapshot, result: String): ApplyOutcome {
        if (!isCurrent(snapshot) || policy.resultDenial(result) != null) return ApplyOutcome.REJECTED
        val access = access() ?: return ApplyOutcome.REJECTED
        if (policy.applyDenial(snapshot, session.token, access, selectionStart, selectionEnd, readSelection()) != null)
            return ApplyOutcome.REJECTED
        // Consume before calling into the editor: double clicks and failures cannot trigger retries.
        if (!session.consume(snapshot.token)) return ApplyOutcome.REJECTED
        return try {
            runCatching { commitSelection(snapshot, result) }.getOrDefault(ApplyOutcome.UNKNOWN)
        } finally {
            // Observers may reenter or rebuild the IME. Never call them between validation and mutation.
            notifyInvalidation()
        }
    }
}
