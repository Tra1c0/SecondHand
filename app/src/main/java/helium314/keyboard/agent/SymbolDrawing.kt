// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import dev.agentkeyboard.core.ApplyOutcome
import dev.agentkeyboard.core.InkRecognizer
import dev.agentkeyboard.core.InkStroke
import dev.agentkeyboard.core.InkSymbols
import dev.agentkeyboard.core.OwnInkSymbols
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.settings.SettingsActivity
import java.lang.ref.WeakReference

/**
 * Stage 27 "Symbole malen": what happens with a finished drawing ([SymbolInk]). The best match is
 * typed through the normal text input (like a symbol from the emoji or symbol keys: it ends the
 * word being typed like punctuation). The strip then offers up to three alternatives; a tap
 * replaces the symbol in one commit and only while the text before the cursor is unchanged
 * (`LatinIME.replaceCommandText`). Nothing close enough: nothing is typed, a muted chip "Nicht
 * erkannt" opens the own symbols. Drawings and results are neither stored nor logged.
 */
object SymbolDrawing {
    const val PREFS_FILE = "agent_symbols"
    const val OWN_SYMBOLS = "own"
    /** Settings page of the own symbols (SettingsDestination.AgentSymbols). */
    const val DESTINATION = "agent_symbols"

    /** What the strip shows after a drawing; valid only while the cursor stays where it was. */
    private sealed class Shown(val cursor: Int)
    private class Inserted(val text: String, val alternatives: List<String>, cursor: Int) : Shown(cursor)
    private class NotRecognized(cursor: Int) : Shown(cursor)

    private var shown: Shown? = null
    private var recognizer: InkRecognizer? = null
    private var recognizerSource: String? = null

    /** Called once per keyboard service (KeyboardActionListenerImpl): drawings go to [ime]. */
    @JvmStatic fun attach(ime: LatinIME): SymbolInk.Sink {
        val ref = WeakReference(ime)
        return SymbolInk.Sink { strokes -> ref.get()?.let { onDrawing(it, strokes) } }.also { SymbolInk.sink = it }
    }

    /** Editor change: a waiting drawing and the chips are dropped. */
    @JvmStatic fun reset() {
        SymbolInk.cancel()
        shown = null
    }

    /** Another key: finish a waiting drawing first, and the chips of the last one are done. */
    @JvmStatic fun beforeOtherInput() {
        SymbolInk.flush()
        shown = null
    }

    internal fun onDrawing(ime: LatinIME, strokes: List<InkStroke>) {
        shown = null
        if (ime.agentEditorController.isBlockingInput) return
        val result = recognizer(ime).recognize(strokes)
        val best = result.best
        if (best == null) {
            shown = NotRecognized(ime.agentCommandCursor)
        } else {
            ime.onTextInput(best.text)
            shown = Inserted(best.text, result.alternatives.map { it.text }, ime.agentCommandCursor)
        }
        ime.setNeutralSuggestionStrip()
    }

    /** LatinIME: the chips of the last drawing, or null when there is nothing (left) to show. */
    @JvmStatic fun createChipsView(ime: LatinIME, parent: ViewGroup): View? {
        val state = shown ?: return null
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
        }
        if (chips.isEmpty()) return null
        return KeyboardCommands.chipRow(parent, chips).apply { id = R.id.agent_symbol_chips }
    }

    private fun replace(ime: LatinIME, state: Inserted, alternative: String) {
        if (shown !== state) return
        val outcome = ime.replaceCommandText(state.text, alternative, state.cursor)
        if (outcome == ApplyOutcome.SUBMITTED) {
            // the replaced symbol becomes an alternative itself
            shown = Inserted(alternative, listOf(state.text) + state.alternatives.filter { it != alternative },
                ime.agentCommandCursor)
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

    // --- templates ---

    private fun recognizer(context: Context): InkRecognizer {
        val raw = prefs(context).getString(OWN_SYMBOLS, null)
        val cached = recognizer
        if (cached != null && raw == recognizerSource) return cached
        val own = OwnInkSymbols.parse(raw).flatMap { it.templates() }
        return InkRecognizer(own + InkSymbols.templates).also { recognizer = it; recognizerSource = raw }
    }

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
}
