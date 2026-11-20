// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import helium314.keyboard.ShadowInputMethodService
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.agent.ClipVault
import helium314.keyboard.agent.LockState
import helium314.keyboard.agent.TypingPerf
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.event.HapticEvent
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * Stage 84: the keyboard of a test that types like a finger does: key down and key up go through the real
 * `KeyboardActionListenerImpl`, `LatinIME`, `InputLogic` and the strip view, the editor is the fake of
 * [ShadowInputMethodService], the suggestion engine answers from a small word list on the real suggestion thread.
 * It records a timeline of what one key causes (calls into the editor, places of work counted by
 * [TypingPerf.work], dictionary look-ups) and counts work, not time.
 */
class TypingRig(val ime: LatinIME) {
    val switcher: KeyboardSwitcher = KeyboardSwitcher.getInstance()
    private val uiThread: Thread = Looper.getMainLooper().thread
    private lateinit var root: View

    /** Words the stub dictionary knows (exact answer) and typos it corrects (a close match, so autocorrect takes it). */
    var known: Set<String> = setOf("hello", "world", "the", "and", "that", "this", "with", "you", "for", "have", "test", "good", "day")
    var typos: Map<String, String> = mapOf("teh" to "the", "adn" to "and", "helo" to "hello", "wrold" to "world", "taht" to "that", "wiht" to "with")
    /** Look-ups of the dictionary (every suggestion computation, from any thread). */
    val lookups = AtomicInteger()

    /** One step of the timeline of a key: a call into the editor ([ui] says: from the main thread) or a counted place of work. */
    class Step(val what: String, val text: String?, val ui: Boolean, val inBatch: Boolean, val phase: String)

    private val log = ArrayList<Step>()
    private var depth = 0
    var phase = "up"

    private fun record(what: String, text: String?) {
        val ui = Thread.currentThread() === uiThread
        synchronized(log) {
            if (ui && what == "beginBatchEdit") depth++
            log.add(Step(what, text, ui, ui && depth > 0, phase))
            if (ui && what == "endBatchEdit" && depth > 0) depth--
        }
    }

    // --- the field and the engine ---

    fun setUp(withClipboardPill: Boolean = false) {
        // the emoji table loads on its own thread: a strip that sometimes has emojis is no state to compare
        helium314.keyboard.agent.EmojiSuggestions.setTableForTest(null)
        ShadowInputMethodService.reset()
        shadowOf(ime.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setIsDeviceLocked(false)
        LockState.resetForTest()
        ime.prefs().edit().clear().putBoolean(Settings.PREF_SUGGEST_CLIPBOARD_CONTENT, withClipboardPill).commit()
        ClipVault.clear()
        root = switcher.onCreateInputView(ime, true)
        ime.setInputView(root)
        startField("")
        installDictionary()
    }

    fun clip(text: String?) {
        val clipboard = ime.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("t", text ?: ""))
        // a clip the system just took has its time (the pill is for clips of the last three minutes)
        android.content.ClipDescription::class.java.getMethod("setTimestamp", Long::class.javaPrimitiveType)
            .invoke(clipboard.primaryClip!!.description, System.currentTimeMillis())
    }

    /** A new field (or the same one again, [restarting]) holding [text] with the cursor at its end. */
    fun startField(text: String, restarting: Boolean = false, inputType: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT) {
        ShadowInputMethodService.text = text
        ShadowInputMethodService.selectionStart = text.length
        ShadowInputMethodService.selectionEnd = text.length
        ShadowInputMethodService.composingStart = -1
        ShadowInputMethodService.composingEnd = -1
        ShadowInputMethodService.currentInputType = inputType
        val info = EditorInfo().apply { this.inputType = inputType; initialSelStart = text.length; initialSelEnd = text.length }
        ime.onStartInput(info, restarting)
        ime.onStartInputView(info, restarting)
        idle()
        if (!restarting) switcher.reloadMainKeyboard()
    }

    private fun installDictionary() {
        val real = ime.mInputLogic.mSuggest.javaClass.getDeclaredField("mDictionaryFacilitator").apply { isAccessible = true }
            .get(ime.mInputLogic.mSuggest) as DictionaryFacilitator
        val stub = java.lang.reflect.Proxy.newProxyInstance(DictionaryFacilitator::class.java.classLoader,
            arrayOf(DictionaryFacilitator::class.java)) { _, method, args ->
            if (method.name == "hasAtLeastOneInitializedMainDictionary") true // otherwise no autocorrect
            else if (method.name == "getCurrentLocale" || method.name == "getMainLocale") java.util.Locale.ENGLISH
            else if (method.name == "mainLocalesKnowing") (args!![0] as String).let { if (it in known) listOf(java.util.Locale.ENGLISH) else emptyList() }
            else if (method.name == "isValidSpellingWord") (args!![0] as String) in known
            else if (method.name == "getSuggestionResults") {
                lookups.incrementAndGet()
                val typed = (args!![0] as ComposedData).mTypedWord
                when {
                    typed in known -> suggestionResults(listOf(suggestion(typed, 1_500_000)))
                    typed in typos -> suggestionResults(listOf(suggestion(typos.getValue(typed), 1_200_000)))
                    else -> suggestionResults(emptyList())
                }
            } else method.invoke(real, *(args ?: emptyArray()))
        } as DictionaryFacilitator
        ime.mInputLogic.mSuggest = Suggest(stub)
        ime.mInputLogic.mSuggest.setAutoCorrectionThreshold(Settings.getValues().mAutoCorrectionThreshold)
    }

    /** Lets the delayed messages of the main thread run ([millis] of the fake clock). */
    fun idle(millis: Long = 300) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    val text: String get() = ShadowInputMethodService.text
    val composing: String get() = ShadowInputMethodService.composingText

    fun toolbarVisible(visible: Boolean) {
        val field = LatinIME::class.java.getDeclaredField("mSuggestionStripView").apply { isAccessible = true }
        val strip = field.get(ime) as helium314.keyboard.latin.suggestions.SuggestionStripView
        strip.setToolbarVisibility(visible)
    }

    // --- keys ---

    private val listener get() = ime.mKeyboardActionListener

    /** Key down of a plain key, as the touch handling sends it. */
    fun down(code: Int) = listener.onPressKey(code, 0, 1, HapticEvent.KEY_PRESS)

    /** Key up: the key's code reaches the keyboard, then the release. */
    fun up(code: Int) {
        listener.onCodeInput(code, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
        listener.onReleaseKey(code, false)
    }

    /** A whole tap, with the delayed messages left alone (a fast typist: the next key comes before they run). */
    fun tapFast(code: Int) { down(code); up(code) }

    /** A tap after which the delayed messages have run (a normal typist). */
    fun tap(code: Int) { tapFast(code); idle() }

    fun type(text: String) { for (c in text) tap(c.code) }

    fun backspace() = tap(KeyCode.DELETE)

    /**
     * Types [script] key by key; the character BACKSPACE_MARK is the backspace key. [fast] leaves the delayed messages
     * alone between the keys (a fast typist), otherwise every key is followed by the time the engine needs.
     */
    fun play(script: String, fast: Boolean = false) {
        for (c in script) {
            val code = if (c == BACKSPACE_MARK) KeyCode.DELETE else c.code
            if (fast) tapFast(code) else tap(code)
        }
        settle()
    }

    /** The delayed messages run until nothing is left; the real suggestion thread gets a moment between the rounds. */
    fun settle() { repeat(4) { idle(); Thread.sleep(10) } }

    fun symbolPage() { listener.onCodeInput(KeyCode.SYMBOL, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false); idle() }

    companion object { const val BACKSPACE_MARK = '\u232B' }

    // --- the timeline ---

    /** Everything [body] causes, with the editor calls, the places of work and the dictionary look-ups. */
    fun record(body: () -> Unit): Timeline {
        synchronized(log) { log.clear(); depth = 0 }
        phase = "up"
        val lookupsBefore = lookups.get()
        ShadowInputMethodService.callListener = { name, text -> record(name, text) }
        TypingPerf.workListener = { place -> record(place.name, null) }
        try { body() } finally {
            ShadowInputMethodService.callListener = null
            TypingPerf.workListener = null
        }
        return Timeline(synchronized(log) { ArrayList(log) }, lookups.get() - lookupsBefore)
    }

    /** What one key caused. */
    class Timeline(val steps: List<Step>, val lookups: Int) {
        private val ui get() = steps.filter { it.ui }
        private fun count(vararg names: String, phase: String? = null) =
            ui.count { it.what in names && (phase == null || it.phase == phase) }

        /** How often the step [name] (an editor call or a [TypingPerf.Work] name) happened on the main thread. */
        fun count(name: String) = ui.count { it.what == name }
        /** The same, only in the key up ("up"), the key down ("down") or the delayed update that follows ("later"). */
        fun count(name: String, phase: String) = ui.count { it.what == name && it.phase == phase }

        val reads get() = count("getTextBeforeCursor", "getTextAfterCursor", "getSelectedText", "getExtractedText")
        val writes get() = count("setComposingText", "commitText", "finishComposingText", "deleteSurroundingText", "sendKeyEvent",
            "setSelection", "setComposingRegion", "commitCorrection")
        val batches get() = count("beginBatchEdit")
        val suggestWaits get() = count("SUGGEST_WAIT")
        val stripNeutral get() = count("STRIP_NEUTRAL")
        val stripWords get() = count("STRIP_WORDS")
        val stripExternal get() = count("STRIP_EXTERNAL")
        val chipEvaluations get() = count("CHIPS_COMMAND", "CHIPS_SYMBOL", "CHIPS_SELECTION", "CHIPS_CLIPBOARD", "CHIPS_SCREENSHOT", "CHIPS_UPDATE")
        val clipboardService get() = count("CLIPBOARD_SERVICE")
        val clipViews get() = count("CLIP_VIEW_BUILT")

        /** The steps that cost something on the main thread, other than writing. */
        private val heavyNames = setOf("getTextBeforeCursor", "getTextAfterCursor", "getSelectedText", "getExtractedText") +
            TypingPerf.Work.entries.map { it.name }
        private fun heavy(step: Step) = step.ui && step.what in heavyNames

        private val editIndex get() = steps.indexOfFirst { it.ui && it.phase == "up" && it.what in setOf("commitText", "setComposingText", "sendKeyEvent") }

        /** Work of the key up that runs before the character reaches the editor (a read there waits for the app). */
        val beforeEdit get() = steps.take(editIndex.let { if (it < 0) steps.size else it }).count { heavy(it) && it.phase == "up" }
        /** Work of the key up between the character and the end of the batch edit: the app shows nothing new before the batch ends. */
        val insideBatchAfterEdit get() = if (editIndex < 0) 0 else steps.drop(editIndex + 1).count { heavy(it) && it.phase == "up" && it.inBatch }
        /** Reads of the editor after the character and before the batch edit ends: the app answers them, the end of the batch waits. */
        val readsInsideBatchAfterEdit get() = if (editIndex < 0) 0 else steps.drop(editIndex + 1).count {
            it.ui && it.phase == "up" && it.inBatch && it.what in setOf("getTextBeforeCursor", "getTextAfterCursor", "getSelectedText", "getExtractedText")
        }
        /** Work of the key up after the last batch edit of the key ended. */
        val afterBatch get() = steps.indexOfLast { it.ui && it.phase == "up" && it.what == "endBatchEdit" }.let { end ->
            if (end < 0) 0 else steps.drop(end + 1).count { heavy(it) && it.phase == "up" }
        }
        /** Work of the key down. */
        val onDown get() = steps.count { heavy(it) && it.phase == "down" }
        /** Work of the delayed messages that follow the key (the suggestion update after 100 ms). */
        val later get() = steps.count { heavy(it) && it.phase == "later" }

        /** The texts the keyboard committed, in order. */
        val committed get() = ui.filter { it.what == "commitText" }.map { it.text.orEmpty() }
        /** Names of the steps, for a readable failure. */
        fun names(phase: String = "up") = ui.filter { it.phase == phase }.joinToString(" ") { (if (it.inBatch) "|" else "") + it.what }
    }

    /**
     * One key of the work table: [prepare] puts the field in the state of the case, then the key is tapped and
     * the delayed messages run. [fast]: the key comes before the delayed message of the letter before it.
     */
    fun measureKey(code: Int, fast: Boolean = false): Timeline {
        if (!fast) idle()
        return record {
            phase = "down"; down(code)
            phase = "up"; up(code)
            phase = "later"; idle()
        }
    }
}
