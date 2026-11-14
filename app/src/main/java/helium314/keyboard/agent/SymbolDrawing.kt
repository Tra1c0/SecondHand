// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import dev.agentkeyboard.core.ApplyOutcome
import dev.agentkeyboard.core.InkCloud
import dev.agentkeyboard.core.InkDots
import dev.agentkeyboard.core.InkRecognizer
import dev.agentkeyboard.core.InkStroke
import dev.agentkeyboard.core.InkTemplate
import dev.agentkeyboard.core.InkSymbols
import dev.agentkeyboard.core.OwnInkSymbols
import dev.agentkeyboard.core.PersonalInkSymbols
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsActivity
import java.lang.ref.WeakReference
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Stage 27 "Symbole malen": what happens with a finished drawing ([SymbolInk]). The best match is
 * typed through the normal text input (like a symbol from the emoji or symbol keys: it ends the
 * word being typed like punctuation). The strip then offers up to three alternatives; a tap
 * replaces the symbol in one commit and only while the text before the cursor is unchanged
 * (`LatinIME.replaceCommandText`). Nothing close enough: nothing is typed, a muted chip "Nicht
 * erkannt" opens the own symbols.
 *
 * Stage 36: while a drawing waits for its next stroke, the strip shows what would be written now
 * (a preview chip with the best guess, "✎ →", and up to two alternatives; matched off the main thread
 * once no stroke followed for [SymbolInk.PREVIEW_DELAY_MILLIS]); a tap writes that symbol at once. With the learning switch on, picking an alternative (in the preview or after writing)
 * keeps the drawing as a personal template of the picked symbol ([PersonalInkSymbols]), not in
 * incognito, while learning is paused or the device is locked. Drawings are never logged.
 */
object SymbolDrawing {
    const val PREFS_FILE = "agent_symbols"
    const val OWN_SYMBOLS = "own"
    /** Stage 36: personal templates (calibration and picked alternatives), docs/STORAGE.md. */
    const val PERSONAL_SYMBOLS = "personal"
    /** Settings page of the own symbols (SettingsDestination.AgentSymbols). */
    const val DESTINATION = "agent_symbols"
    /** Marks the preview chip (stage 36). */
    const val PREVIEW_MARK = "✎ "

    /** What the strip shows after a drawing; valid only while the cursor stays where it was. */
    private sealed class Shown(val cursor: Int)
    /**
     * A written symbol. [cloud] is its drawing, kept in memory for learning from a picked alternative;
     * [learnedOn] is non-null while the drawing is learned: the personal templates as they were before
     * (review N4), so a second pick or picking [recognized] again restores them exactly, also a drawing
     * the learning had pushed out.
     */
    private class Inserted(val text: String, val alternatives: List<String>, cursor: Int, val cloud: InkCloud?,
        val recognized: String, val learnedOn: List<OwnInkSymbols.Symbol>? = null) : Shown(cursor)
    private class NotRecognized(cursor: Int) : Shown(cursor)
    /**
     * Stage 36: a drawing that waits for its next stroke; [guess] is what would be written now for its
     * first [strokes] strokes. [stale] once a further stroke began (follow-up review F1): a tap then
     * writes the best match of all strokes and learns nothing.
     */
    private class Waiting(val guess: String?, val alternatives: List<String>, val strokes: Int, val stale: Boolean = false) : Shown(-1)

    private var shown: Shown? = null
    private var recognizer: InkRecognizer? = null
    private var dots: InkDots? = null
    private var templates: List<InkTemplate> = emptyList()
    private var recognizerSource: Pair<String?, String?>? = null

    /**
     * Stage 36 review M2: the preview is matched off the main thread (up to some 300 templates); the
     * result is shown only if nothing changed meanwhile ([previewGeneration]). Tests run it inline.
     */
    internal val workerExecutor: Executor = Executors.newSingleThreadExecutor { Thread(it, "symbol-preview").apply { isDaemon = true } }
    internal var previewExecutor: Executor = workerExecutor
    private var previewGeneration = 0
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private fun setShown(state: Shown?) {
        shown = state
        previewGeneration++
    }

    /**
     * Called once per keyboard service (KeyboardActionListenerImpl): drawings go to [ime], and a tap
     * is a dot also of the own symbols with dots (stage 31).
     */
    @JvmStatic fun attach(ime: LatinIME): SymbolInk.Sink {
        val ref = WeakReference(ime)
        SymbolInk.dotCheck = object : SymbolInk.DotCheck {
            override fun isDot(strokes: List<InkStroke>, x: Float, y: Float) =
                ref.get()?.let { recognizer(it); dots?.isDot(strokes, x, y) } ?: SymbolInk.builtInDotCheck.isDot(strokes, x, y)
            // stage 43: the same templates decide whether possible dots stay dots
            override fun keeps(strokes: List<InkStroke>, possibleDots: List<Int>) =
                ref.get()?.let { recognizer(it); dots?.keeps(strokes, possibleDots) } ?: SymbolInk.builtInDotCheck.keeps(strokes, possibleDots)
        }
        return object : SymbolInk.Sink {
            override fun onDrawing(strokes: List<InkStroke>) { ref.get()?.let { onDrawing(it, strokes) } }
            override fun onWaiting(strokes: List<InkStroke>, possibleDots: List<Int>) { ref.get()?.let { onWaiting(it, strokes, possibleDots) } }
            override fun onStrokeStarted() { this@SymbolDrawing.onStrokeStarted() }
            // stage 43 review M1: the same recognizer as write; its memo answers write without matching again
            override fun recognizes(strokes: List<InkStroke>) =
                ref.get()?.let { ime -> InkCloud.of(strokes)?.let { recognizer(ime).recognize(it).best != null } ?: false } ?: true
        }.also { SymbolInk.sink = it }
    }

    /** Editor change: a waiting drawing and the chips are dropped. */
    @JvmStatic fun reset() {
        setShown(null) // first: the dropped drawing then asks for no new strip (onWaiting)
        SymbolInk.cancel()
    }

    /** Another key: finish a waiting drawing first, and the chips of the last one are done. */
    @JvmStatic fun beforeOtherInput() {
        SymbolInk.flush()
        setShown(null)
    }

    /**
     * LatinIME: a word from the strip is picked while a drawing may wait (stage 31). Text appears in
     * the order it was made: a picked word that is new (a prediction, nothing typed) comes after the
     * drawn symbol, so the symbol is written first. A pick that replaces the word typed before the
     * drawing changes that earlier word; the symbol then still follows it after the pause.
     */
    @JvmStatic fun beforeSuggestionPick(replacesTypedWord: Boolean) {
        if (!replacesTypedWord) beforeOtherInput()
    }

    internal fun onDrawing(ime: LatinIME, strokes: List<InkStroke>) = write(ime, strokes, null)

    /** Writes the drawing: [chosen] (a chip of the preview) or the best match. */
    private fun write(ime: LatinIME, strokes: List<InkStroke>, chosen: String?) {
        setShown(null)
        if (ime.agentEditorController.isBlockingInput) return
        val cloud = InkCloud.of(strokes)
        val result = cloud?.let { recognizer(ime).recognize(it) }
        val best = result?.best?.text
        val text = chosen ?: best
        if (text == null) {
            setShown(NotRecognized(ime.agentCommandCursor))
        } else {
            ime.onTextInput(text)
            val others = (listOfNotNull(best) + result!!.alternatives.map { it.text }).filter { it != text }
            val learnedOn = if (chosen != null && chosen != best && cloud != null) learn(ime, cloud, chosen, null) else null
            setShown(Inserted(text, others.take(InkRecognizer.ALTERNATIVES), ime.agentCommandCursor, cloud, best ?: text, learnedOn))
        }
        ime.setNeutralSuggestionStrip()
    }

    /**
     * Stage 36: the waiting drawing changed (a stroke more), or was dropped (empty). Stage 43: the guess
     * is for the drawing as it would be written now: without [possibleDots] that would be letters.
     */
    internal fun onWaiting(ime: LatinIME, strokes: List<InkStroke>, possibleDots: List<Int> = emptyList()) {
        if (strokes.isEmpty()) {
            previewGeneration++ // a preview still being matched is out of date
            if (shown !is Waiting) return
            setShown(null)
            // the strip gets back what it showed before the drawing (suggestions or the neutral strip)
            ime.mHandler.postUpdateSuggestionStrip(SuggestedWords.INPUT_STYLE_TYPING)
            return
        }
        if (ime.agentEditorController.isBlockingInput) {
            previewGeneration++ // follow-up review F5: an older preview still being matched is out of date
            return
        }
        val matcher = recognizer(ime) // read on the main thread (preferences), matched on the worker
        val dotRule = dots
        val generation = ++previewGeneration
        val drawing = strokes.toList()
        val taps = possibleDots.toSet()
        val ref = WeakReference(ime)
        previewExecutor.execute {
            val written = if (taps.isEmpty() || dotRule?.keeps(drawing, taps) != false) drawing
                else drawing.filterIndexed { i, _ -> i !in taps }
            val result = matcher.recognize(written)
            // postAtTime, not post: tests replace posted messages of all handlers (P51)
            mainHandler.postAtTime({
                val keyboard = ref.get() ?: return@postAtTime
                // a newer stroke, a written or dropped drawing, or a key in between: out of date
                if (generation != previewGeneration || !SymbolInk.isWaiting) return@postAtTime
                val guess = result.best?.text
                setShown(Waiting(guess, (if (guess == null) emptyList() else result.alternatives.map { it.text }).take(2), drawing.size))
                keyboard.setNeutralSuggestionStrip()
            }, SystemClock.uptimeMillis())
        }
    }

    /** LatinIME: the chips of the last drawing, or null when there is nothing (left) to show. */
    @JvmStatic fun createChipsView(ime: LatinIME, parent: ViewGroup): View? {
        val state = shown ?: return null
        if (state is Waiting) {
            if (!SymbolInk.isWaiting) {
                setShown(null)
                return null
            }
            val chips = ArrayList<KeyboardCommands.Chip>()
            chips += if (state.guess == null) KeyboardCommands.Chip(PREVIEW_MARK + "?",
                    ime.getString(R.string.agent_symbols_preview_unknown), kind = KeyboardCommands.Chip.Kind.MUTED) { takeWaiting(ime, null) }
                else KeyboardCommands.Chip(PREVIEW_MARK + state.guess, ime.getString(R.string.agent_symbols_preview, state.guess)) {
                    takeWaiting(ime, state.guess)
                }
            state.alternatives.forEach { alternative ->
                chips += KeyboardCommands.Chip(alternative, ime.getString(R.string.agent_symbols_preview_instead, alternative)) {
                    takeWaiting(ime, alternative)
                }
            }
            return KeyboardCommands.chipRow(parent, chips).apply { id = R.id.agent_symbol_chips }
        }
        val cursor = ime.agentCommandCursor
        if (cursor < 0 || cursor != state.cursor) {
            setShown(null)
            return null
        }
        val chips = when (state) {
            is NotRecognized -> listOf(KeyboardCommands.Chip(ime.getString(R.string.agent_symbols_not_recognized),
                kind = KeyboardCommands.Chip.Kind.MUTED) { openOwnSymbols(ime) })
            is Inserted -> {
                val before = ime.getAgentTextBeforeCursor(state.text.length)?.toString()
                if (before != state.text) {
                    setShown(null)
                    return null
                }
                state.alternatives.map { alternative ->
                    KeyboardCommands.Chip(alternative, ime.getString(R.string.agent_symbols_alternative, alternative)) {
                        replace(ime, state, alternative)
                    }
                }
            }
            is Waiting -> emptyList()
        }
        if (chips.isEmpty()) return null
        return KeyboardCommands.chipRow(parent, chips).apply { id = R.id.agent_symbol_chips }
    }

    /** Follow-up review F1: a further stroke begins; the preview shown is for fewer strokes now. */
    private fun onStrokeStarted() {
        previewGeneration++ // a preview still being matched is out of date
        (shown as? Waiting)?.let { shown = Waiting(it.guess, it.alternatives, it.strokes, stale = true) }
    }

    /** A preview chip: write the waiting drawing now, as [chosen] (null: the best match, i.e. none). */
    private fun takeWaiting(ime: LatinIME, chosen: String?) {
        val state = shown as? Waiting ?: return
        // review N3: while input is blocked the drawing stays where it is instead of being dropped
        if (ime.agentEditorController.isBlockingInput) return
        // a stroke still on the keys, or nothing waits any more: the chip is out of date (no write)
        // follow-up review F1: a chip for fewer strokes writes the best match of all of them, never its
        // old guess (and so learns nothing); stage 43: possible dots that are letters follow it
        SymbolInk.takeWaiting { strokes, drawn -> write(ime, strokes, if (state.stale || state.strokes != drawn) null else chosen) }
    }

    private fun replace(ime: LatinIME, state: Inserted, alternative: String) {
        if (shown !== state) return
        val outcome = ime.replaceCommandText(state.text, alternative, state.cursor)
        if (outcome == ApplyOutcome.SUBMITTED) {
            // the drawing was meant as [alternative]: learn it on top of the templates as they were
            // before this drawing was learned (a second pick moves it, the recognized one undoes it)
            var learnedOn = state.learnedOn
            val target = alternative.takeIf { it != state.recognized }
            if (state.cloud != null && (target != null || learnedOn != null)) {
                learn(ime, state.cloud, target, learnedOn)?.let { before -> learnedOn = if (target != null) before else null }
            }
            // the replaced symbol becomes an alternative itself
            setShown(Inserted(alternative, listOf(state.text) + state.alternatives.filter { it != alternative },
                ime.agentCommandCursor, state.cloud, state.recognized, learnedOn))
        } else {
            setShown(null)
            if (outcome == ApplyOutcome.REJECTED) Toast.makeText(ime, R.string.agent_command_expired, Toast.LENGTH_SHORT).show()
        }
        ime.setNeutralSuggestionStrip()
    }

    private fun openOwnSymbols(ime: LatinIME) {
        setShown(null)
        // never open the settings over the lock screen: the chip then only goes away
        if (helium314.keyboard.latin.settings.Settings.getValues()?.mIsLocked != false) {
            ime.setNeutralSuggestionStrip()
            return
        }
        val context: Context = ime
        context.startActivity(Intent(context, SettingsActivity::class.java)
            // an existing settings task would only come to the front and ignore the destination (P39)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(AgentPreferences.OPEN_SETTINGS_DESTINATION, DESTINATION))
    }

    // --- learning (stage 36) ---

    /** Whether a picked alternative may be kept now: switch and personalized suggestions on, not paused, not incognito, not locked. */
    @JvmStatic fun mayLearn(context: Context): Boolean {
        val settings = helium314.keyboard.latin.settings.Settings.getValues() ?: return false
        // follow-up review F7: also only with personalized suggestions, whose off switch deletes the templates
        return context.prefs().getBoolean(AgentPreferences.SYMBOL_LEARNING, false) && settings.mUsePersonalizedDicts
            && !settings.mLearningPaused
            && !settings.mIncognitoModeEnabled && !settings.mIsLocked && !settings.mInputAttributes.mIsPasswordField
    }

    /**
     * Keeps [cloud] for [text] on top of [base] (the personal templates before this drawing was
     * learned; null: as stored now); with [text] null only [base] is restored. A drawing with dots is
     * kept only for a symbol with dots (review M3). Returns the templates it started from, or null when
     * nothing may be learned now.
     */
    private fun learn(ime: LatinIME, cloud: InkCloud, text: String?, base: List<OwnInkSymbols.Symbol>?): List<OwnInkSymbols.Symbol>? {
        if (!mayLearn(ime)) return null
        val before = base ?: personalSymbols(ime)
        recognizer(ime) // the templates of the symbol with or without dots
        val personal = if (text == null) before
            else PersonalInkSymbols.add(before, text, cloud, PersonalInkSymbols.hasDots(templates, text))
        savePersonalSymbols(ime, personal)
        return before
    }

    // --- templates ---

    /**
     * The recognizer of the built-in, own and personal symbols; [dots] is rebuilt with it from the same
     * templates and shares it, so a tap that is no dot does not match the written drawing twice.
     */
    private fun recognizer(context: Context): InkRecognizer {
        val prefs = prefs(context)
        val source = prefs.getString(OWN_SYMBOLS, null) to prefs.getString(PERSONAL_SYMBOLS, null)
        val cached = recognizer
        if (cached != null && source == recognizerSource) return cached
        val all = OwnInkSymbols.parse(source.first).flatMap { it.templates() } +
            PersonalInkSymbols.templates(PersonalInkSymbols.parse(source.second)) + InkSymbols.templates
        return InkRecognizer(all).also { dots = InkDots(all, it); templates = all; recognizer = it; recognizerSource = source }
    }

    /** The keyboard's recognizer as it is now, if built; for tests (stage 31 fix, one match per tap). */
    internal fun currentRecognizer(): InkRecognizer? = recognizer

    private fun prefs(context: Context) =
        AgentStorageContexts.keyboard(context).getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    /** The own symbols, in the order they were added. */
    @JvmStatic fun ownSymbols(context: Context): List<OwnInkSymbols.Symbol> =
        OwnInkSymbols.parse(prefs(context).getString(OWN_SYMBOLS, null))

    @JvmStatic fun saveOwnSymbols(context: Context, symbols: List<OwnInkSymbols.Symbol>) {
        prefs(context).edit().apply {
            if (symbols.isEmpty()) remove(OWN_SYMBOLS) else putString(OWN_SYMBOLS, OwnInkSymbols.encode(symbols))
        }.apply()
    }

    /** Stage 36: the personal templates, the symbol changed longest ago first. */
    @JvmStatic fun personalSymbols(context: Context): List<OwnInkSymbols.Symbol> =
        PersonalInkSymbols.parse(prefs(context).getString(PERSONAL_SYMBOLS, null))

    /** Review N4: everything learned is deleted together ("Gelerntes löschen", personalized suggestions off). */
    @JvmStatic fun clearPersonalSymbols(context: Context) = savePersonalSymbols(context, emptyList())

    @JvmStatic fun savePersonalSymbols(context: Context, symbols: List<OwnInkSymbols.Symbol>) {
        prefs(context).edit().apply {
            if (symbols.isEmpty()) remove(PERSONAL_SYMBOLS) else putString(PERSONAL_SYMBOLS, PersonalInkSymbols.encode(symbols))
        }.apply()
    }
}
