// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import dev.agentkeyboard.core.ApplyOutcome
import dev.agentkeyboard.core.InkCloud
import dev.agentkeyboard.core.InkDots
import dev.agentkeyboard.core.InkRecognizer
import dev.agentkeyboard.core.InkStroke
import dev.agentkeyboard.core.InkSymbols
import dev.agentkeyboard.core.OwnInkSymbols
import dev.agentkeyboard.core.PersonalInkSymbols
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsActivity
import java.lang.ref.WeakReference

/**
 * Stage 27 "Symbole malen": what happens with a finished drawing ([SymbolInk]). The best match is
 * typed through the normal text input (like a symbol from the emoji or symbol keys: it ends the
 * word being typed like punctuation). The strip then offers up to three alternatives; a tap
 * replaces the symbol in one commit and only while the text before the cursor is unchanged
 * (`LatinIME.replaceCommandText`). Nothing close enough: nothing is typed, a muted chip "Nicht
 * erkannt" opens the own symbols.
 *
 * Stage 36: while a drawing waits for its next stroke, the strip shows what would be written now
 * (a preview chip with the best guess, "✎ →", and up to two alternatives); a tap writes that symbol
 * at once. With the learning switch on, picking an alternative (in the preview or after writing)
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
     * [learned] is the text it was learned for, so a second pick moves it instead of keeping two.
     */
    private class Inserted(val text: String, val alternatives: List<String>, cursor: Int, val cloud: InkCloud?,
        val recognized: String, val learned: String? = null) : Shown(cursor)
    private class NotRecognized(cursor: Int) : Shown(cursor)
    /** Stage 36: a drawing that waits for its next stroke; [guess] is what would be written now. */
    private class Waiting(val guess: String?, val alternatives: List<String>) : Shown(-1)

    private var shown: Shown? = null
    private var recognizer: InkRecognizer? = null
    private var dots: InkDots? = null
    private var recognizerSource: Pair<String?, String?>? = null

    /**
     * Called once per keyboard service (KeyboardActionListenerImpl): drawings go to [ime], and a tap
     * is a dot also of the own symbols with dots (stage 31).
     */
    @JvmStatic fun attach(ime: LatinIME): SymbolInk.Sink {
        val ref = WeakReference(ime)
        SymbolInk.dotCheck = SymbolInk.DotCheck { strokes, x, y ->
            ref.get()?.let { recognizer(it); dots?.isDot(strokes, x, y) } ?: SymbolInk.builtInDotCheck.isDot(strokes, x, y)
        }
        return object : SymbolInk.Sink {
            override fun onDrawing(strokes: List<InkStroke>) { ref.get()?.let { onDrawing(it, strokes) } }
            override fun onWaiting(strokes: List<InkStroke>) { ref.get()?.let { onWaiting(it, strokes) } }
        }.also { SymbolInk.sink = it }
    }

    /** Editor change: a waiting drawing and the chips are dropped. */
    @JvmStatic fun reset() {
        shown = null // first: the dropped drawing then asks for no new strip (onWaiting)
        SymbolInk.cancel()
    }

    /** Another key: finish a waiting drawing first, and the chips of the last one are done. */
    @JvmStatic fun beforeOtherInput() {
        SymbolInk.flush()
        shown = null
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
        shown = null
        if (ime.agentEditorController.isBlockingInput) return
        val cloud = InkCloud.of(strokes)
        val result = cloud?.let { recognizer(ime).recognize(it) }
        val best = result?.best?.text
        val text = chosen ?: best
        if (text == null) {
            shown = NotRecognized(ime.agentCommandCursor)
        } else {
            ime.onTextInput(text)
            val others = (listOfNotNull(best) + result!!.alternatives.map { it.text }).filter { it != text }
            var learned: String? = null
            if (chosen != null && chosen != best && cloud != null && learn(ime, cloud, chosen, null)) learned = chosen
            shown = Inserted(text, others.take(InkRecognizer.ALTERNATIVES), ime.agentCommandCursor, cloud, best ?: text, learned)
        }
        ime.setNeutralSuggestionStrip()
    }

    /** Stage 36: the waiting drawing changed (a stroke more), or was dropped (empty). */
    internal fun onWaiting(ime: LatinIME, strokes: List<InkStroke>) {
        if (strokes.isEmpty()) {
            if (shown !is Waiting) return
            shown = null
            // the strip gets back what it showed before the drawing (suggestions or the neutral strip)
            ime.mHandler.postUpdateSuggestionStrip(SuggestedWords.INPUT_STYLE_TYPING)
            return
        }
        if (ime.agentEditorController.isBlockingInput) return
        val result = recognizer(ime).recognize(strokes)
        val guess = result.best?.text
        shown = Waiting(guess, (if (guess == null) emptyList() else result.alternatives.map { it.text }).take(2))
        ime.setNeutralSuggestionStrip()
    }

    /** LatinIME: the chips of the last drawing, or null when there is nothing (left) to show. */
    @JvmStatic fun createChipsView(ime: LatinIME, parent: ViewGroup): View? {
        val state = shown ?: return null
        if (state is Waiting) {
            if (!SymbolInk.isWaiting) {
                shown = null
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
            shown = null
            return null
        }
        val chips = when (state) {
            is NotRecognized -> listOf(KeyboardCommands.Chip(ime.getString(R.string.agent_symbols_not_recognized),
                kind = KeyboardCommands.Chip.Kind.MUTED) { openOwnSymbols(ime) })
            is Inserted -> {
                val before = ime.getAgentTextBeforeCursor(state.text.length)?.toString()
                if (before != state.text) {
                    shown = null
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

    /** A preview chip: write the waiting drawing now, as [chosen] (null: the best match, i.e. none). */
    private fun takeWaiting(ime: LatinIME, chosen: String?) {
        if (shown !is Waiting) return
        // a stroke still on the keys, or nothing waits any more: the chip is out of date
        val strokes = SymbolInk.takeWaiting() ?: return
        write(ime, strokes, chosen)
    }

    private fun replace(ime: LatinIME, state: Inserted, alternative: String) {
        if (shown !== state) return
        val outcome = ime.replaceCommandText(state.text, alternative, state.cursor)
        if (outcome == ApplyOutcome.SUBMITTED) {
            // the drawing was meant as [alternative]: learn it (moving an earlier pick of the same drawing)
            var learned = state.learned
            if (state.cloud != null && (alternative != state.recognized || learned != null)) {
                learned = if (learn(ime, state.cloud, alternative.takeIf { it != state.recognized }, state.learned))
                    alternative.takeIf { it != state.recognized } else learned
            }
            // the replaced symbol becomes an alternative itself
            shown = Inserted(alternative, listOf(state.text) + state.alternatives.filter { it != alternative },
                ime.agentCommandCursor, state.cloud, state.recognized, learned)
        } else {
            shown = null
            if (outcome == ApplyOutcome.REJECTED) Toast.makeText(ime, R.string.agent_command_expired, Toast.LENGTH_SHORT).show()
        }
        ime.setNeutralSuggestionStrip()
    }

    private fun openOwnSymbols(ime: LatinIME) {
        shown = null
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

    /** Whether a picked alternative may be kept now: switch on, not paused, not incognito, not locked. */
    @JvmStatic fun mayLearn(context: Context): Boolean {
        val settings = helium314.keyboard.latin.settings.Settings.getValues() ?: return false
        return context.prefs().getBoolean(AgentPreferences.SYMBOL_LEARNING, false) && !settings.mLearningPaused
            && !settings.mIncognitoModeEnabled && !settings.mIsLocked && !settings.mInputAttributes.mIsPasswordField
    }

    /**
     * Keeps [cloud] for [text] (null: for nothing) and drops the newest drawing of [previous], the
     * text this drawing was learned for before. False when nothing may be learned now.
     */
    private fun learn(ime: LatinIME, cloud: InkCloud, text: String?, previous: String?): Boolean {
        if (!mayLearn(ime)) return false
        var personal = personalSymbols(ime)
        if (previous != null) personal = personal.mapNotNull { symbol ->
            if (symbol.text != previous) symbol else symbol.samples.dropLast(1).takeIf { it.isNotEmpty() }?.let { OwnInkSymbols.Symbol(symbol.text, it) }
        }
        if (text != null) personal = PersonalInkSymbols.add(personal, text, cloud)
        savePersonalSymbols(ime, personal)
        return true
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
        val templates = OwnInkSymbols.parse(source.first).flatMap { it.templates() } +
            PersonalInkSymbols.templates(PersonalInkSymbols.parse(source.second)) + InkSymbols.templates
        return InkRecognizer(templates).also { dots = InkDots(templates, it); recognizer = it; recognizerSource = source }
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

    @JvmStatic fun savePersonalSymbols(context: Context, symbols: List<OwnInkSymbols.Symbol>) {
        prefs(context).edit().apply {
            if (symbols.isEmpty()) remove(PERSONAL_SYMBOLS) else putString(PERSONAL_SYMBOLS, PersonalInkSymbols.encode(symbols))
        }.apply()
    }
}
