// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import dev.agentkeyboard.core.AnchorResult
import dev.agentkeyboard.core.ApplyOutcome
import dev.agentkeyboard.core.DenialReason
import dev.agentkeyboard.core.ExecutionPolicy
import dev.agentkeyboard.core.InsertionAnchor

enum class DictationError { UNAVAILABLE, PERMISSION, LANGUAGE, NO_SPEECH, BUSY, FAILED }

/**
 * Speech recognition through a recognizer the system provides as on-device. Main thread only.
 * An implementation must never fall back to the general recognizer, which the platform
 * documents as likely to stream audio to remote servers.
 */
interface SpeechEngine {
    val available: Boolean
    fun start(languageTag: String, listener: Listener)
    /** The user is done speaking: deliver what was heard. */
    fun stop()
    /** Abort and deliver nothing. */
    fun cancel()

    interface Listener {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(error: DictationError)
    }
}

/**
 * Main-thread state machine of one dictation: listen, review, insert on an explicit click.
 * The heard text exists only in memory. Nothing is inserted automatically, a selection is never
 * replaced, and any change of the editor, cursor or privacy state discards the text.
 */
class DictationSession(
    private val engine: () -> SpeechEngine,
    private val captureAnchor: () -> AnchorResult,
    private val isCurrent: (InsertionAnchor) -> Boolean,
    private val insertAt: (InsertionAnchor, String) -> ApplyOutcome,
    private val microphoneGranted: () -> Boolean,
    private val languageTag: () -> String,
    private val ui: Ui,
) {
    interface Ui {
        fun listening(partial: String)
        /** The user said "done"; the final text has not arrived yet. */
        fun processing()
        fun review(text: String)
        fun failed(error: DictationError)
        fun denied(reason: DenialReason)
    }

    private val policy = ExecutionPolicy()
    private var epoch = 0L
    private var anchor: InsertionAnchor? = null
    private var heard: String? = null
    var isListening = false
        private set
    private var finishing = false
    val isActive: Boolean get() = anchor != null
    /** The reviewed text, for copying. Null while listening or idle. */
    val reviewText: String? get() = if (isListening) null else heard

    /** Checks the editor and the microphone before anything is recorded. */
    fun start(): Boolean {
        if (isActive) return false
        val captured = captureAnchor()
        if (captured !is AnchorResult.Captured) {
            ui.denied((captured as AnchorResult.Denied).reason)
            return false
        }
        val speech = engine()
        if (!speech.available) { ui.failed(DictationError.UNAVAILABLE); return false }
        if (!microphoneGranted()) { ui.failed(DictationError.PERMISSION); return false }
        val mine = ++epoch
        anchor = captured.anchor
        heard = null
        isListening = true
        ui.listening("")
        speech.start(languageTag(), object : SpeechEngine.Listener {
            override fun onPartial(text: String) {
                if (mine != epoch || !isListening) return
                // Lock, incognito or a moved cursor reach us only through events; re-check here
                // so the microphone does not keep running after the editor stopped being allowed.
                val target = anchor
                if (target == null || !isCurrent(target)) {
                    cancel()
                    ui.denied(DenialReason.STALE_CONTEXT)
                } else if (!finishing) ui.listening(text.take(MAX_PARTIAL_CHARS))
            }
            override fun onFinal(text: String) {
                if (mine != epoch || !isListening) return
                val target = anchor
                val clean = text.trim()
                when {
                    target == null || !isCurrent(target) -> { reset(); ui.denied(DenialReason.STALE_CONTEXT) }
                    clean.isEmpty() -> { reset(); ui.failed(DictationError.NO_SPEECH) }
                    clean.length > policy.maxSelectionChars -> { reset(); ui.denied(DenialReason.TOO_LARGE) }
                    policy.resultDenial(clean) != null -> { reset(); ui.failed(DictationError.FAILED) }
                    else -> { isListening = false; heard = clean; ui.review(clean) }
                }
            }
            override fun onError(error: DictationError) {
                if (mine != epoch || !isListening) return
                reset()
                ui.failed(error)
            }
        })
        return isActive // the engine may have failed synchronously
    }

    /** The user is done speaking. The final text arrives through the listener. */
    fun finish() {
        if (!isListening || finishing) return // a second tap does not stop twice
        finishing = true
        ui.processing()
        engine().stop()
    }

    /** Stops the microphone and forgets everything. True if there was something to discard. */
    fun cancel(): Boolean {
        val active = isActive
        val listening = isListening
        reset() // first: a recognizer that calls back from cancel() finds a newer epoch
        if (listening) runCatching { engine().cancel() }
        return active
    }

    /** The explicit click in review. One attempt: the text is forgotten before the commit. */
    fun insert(): ApplyOutcome {
        val target = anchor
        val text = heard
        if (isListening || target == null || text == null) return ApplyOutcome.REJECTED
        reset()
        return insertAt(target, text)
    }

    /** Call when the editor reports a change. True if a dictation was discarded because of it. */
    fun editorChanged(): Boolean {
        val target = anchor ?: return false
        if (isCurrent(target)) return false
        cancel()
        return true
    }

    private fun reset() {
        epoch++ // late callbacks of an older recognition are ignored
        anchor = null
        heard = null
        isListening = false
        finishing = false
    }

    private companion object { const val MAX_PARTIAL_CHARS = 2_000 }
}
