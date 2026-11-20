// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.view.ViewGroup
import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs

/**
 * Stage 82 "Alles markieren zeigt Aktionen": while text is selected, the strip shows chips
 * "Kopieren", "Ausschneiden", "Einfügen" (only when the clipboard holds text) and "Löschen".
 * Reason: "select all" on the space bar (`performContextMenuAction(selectAll)`) selects, but an
 * editor without a selection toolbar starts none from it, so the owner saw no copy/cut/paste menu.
 *
 * The chips stand for every non-empty selection the keyboard knows (made by its own select all or
 * select word, by shift and arrows or by hand): the state is read live when the strip is built, so
 * they are gone as soon as the selection collapses, the field changes or something is typed (the
 * strip is then built for the new state). They sit behind typed command chips and the chips of a drawn
 * symbol and in front of the clipboard pill, the screenshot chip and word suggestions
 * (`LatinIME.setNeutralSuggestionStrip`, `LatinIME.setSuggestedWords`).
 *
 * A tap sends the normal key code (`CLIPBOARD_COPY`, `CLIPBOARD_CUT`, `CLIPBOARD_PASTE`, `DELETE`)
 * through [LatinIME.onEvent], so nothing is copied, cut, pasted or deleted without the tap. A tap is
 * refused when the selection is no longer the one the chip was built for. Never in password,
 * incognito or no-learning fields and while the device is locked (asked live, like the command chips: a lock
 * after the field started counts, when the chips are built and on the tap; stricter than the copy key of the
 * toolbar, which has no such check), never while input is blocked. A chip is bound to the selection and to the
 * editor session of the field it was built for. Switch: "Aktionen bei markiertem Text" (default on). Not with
 * "auto show toolbar" and never touching the toolbar ([LatinIME.tryShowSelectionChips]). The row is reused
 * while selection, field and "Einfügen" stay the same, and the clipboard service is asked once per clip. The
 * clipboard content is never read, only its type ([ClipboardManager.getPrimaryClipDescription]). Nothing is logged.
 */
object SelectionChips {
    fun isEnabled(context: Context) = context.prefs().getBoolean(AgentPreferences.SELECTION_CHIPS, true)

    /** Whether the clipboard holds text: its type only, never its content. */
    fun systemClipHasText(context: Context): Boolean =
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClipDescription?.hasMimeType("text/*") == true

    /** Replaced in tests. */
    @JvmStatic var clipHasText: (Context) -> Boolean = ::systemClipHasText

    /** Replaced in tests: whether the device is locked right now (the shared, short-lived answer). */
    @JvmStatic var deviceLocked: (Context) -> Boolean = { LockState.isLocked(it) }

    /** No chips without settings, in incognito (also no-learning and password fields) or while the device is locked, now. */
    private fun isBlocked(ime: LatinIME) =
        FormatGuardChips.isBlocked(Settings.getValues(), ime.currentInputEditorInfo) || ime.agentEditorController.isBlockingInput ||
            deviceLocked(ime)

    /** What a row was built for: the selected range and the editor session of the field. */
    private class Bound(val start: Int, val end: Int, val generation: Long) {
        fun matches(ime: LatinIME): Boolean {
            val now = ime.agentSelection ?: return false
            return now[0] == start && now[1] == end && ime.agentEditorGeneration == generation
        }
    }

    /** Whether the strip was last built with the chips, and for what. */
    @Volatile private var shown: Bound? = null

    /** Whether the clipboard had text when last asked; asked again after a clip change ([clipChanged]) or a copy or cut. */
    private var clipText: Boolean? = null

    /** How often the clipboard service was asked (tests). */
    @Volatile internal var clipChecks = 0
        private set

    internal fun resetForTest() { shown = null; clipText = null; clipChecks = 0; clipHasText = ::systemClipHasText; deviceLocked = { LockState.isLocked(it) } }

    private fun pasteAvailable(ime: LatinIME): Boolean = clipText ?: run {
        clipChecks++
        clipHasText(ime).also { clipText = it }
    }

    /** LatinIME: the chips for the selection that exists now, or null (no selection, blocked field, switched off). */
    @JvmStatic fun createChipsView(ime: LatinIME, parent: ViewGroup): View? {
        shown = null
        val selection = ime.agentSelection ?: return null
        if (!isEnabled(ime) || isBlocked(ime)) return null
        val bound = Bound(selection[0], selection[1], ime.agentEditorGeneration)
        shown = bound
        fun press(code: Int) {
            // the chip belongs to this selection and field; after any change (or a lock) it does nothing
            if (!bound.matches(ime) || isBlocked(ime)) return
            ime.onEvent(Event.createSoftwareKeypressEvent(code, 0, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false))
        }
        val chips = ArrayList<KeyboardCommands.Chip>()
        chips += KeyboardCommands.Chip(ime.getString(R.string.agent_selection_copy), ime.getString(R.string.agent_selection_copy_description)) {
            press(KeyCode.CLIPBOARD_COPY)
        }
        chips += KeyboardCommands.Chip(ime.getString(R.string.agent_selection_cut), ime.getString(R.string.agent_selection_cut_description)) {
            press(KeyCode.CLIPBOARD_CUT)
        }
        if (pasteAvailable(ime)) chips += KeyboardCommands.Chip(ime.getString(R.string.agent_selection_paste),
            ime.getString(R.string.agent_selection_paste_description)) { press(KeyCode.CLIPBOARD_PASTE) }
        chips += KeyboardCommands.Chip(ime.getString(R.string.agent_selection_delete), ime.getString(R.string.agent_selection_delete_description),
            kind = KeyboardCommands.Chip.Kind.MUTED) { press(KeyCode.DELETE) }
        // the row of the last build when nothing visible changed (a handle being dragged rebuilds the strip often)
        return KeyboardCommands.rowFor(parent, chips).apply { id = R.id.agent_selection_chips }
    }

    /**
     * LatinIME.onEvent, after a key. Select word and select all go through the editor and come back as a
     * selection update, which rebuilds the strip. What copy, cut and paste do the keyboard knows itself (an
     * expected selection update that does not rebuild the strip, a clipboard that has text now), so the
     * chips are built again here. Any other key that took the selection away (typing over it, delete)
     * shows the normal strip at once instead of after the suggestions come back.
     */
    @JvmStatic fun afterKey(ime: LatinIME, keyCode: Int) {
        val own = keyCode == KeyCode.CLIPBOARD_COPY || keyCode == KeyCode.CLIPBOARD_CUT || keyCode == KeyCode.CLIPBOARD_PASTE
        if (keyCode == KeyCode.CLIPBOARD_COPY || keyCode == KeyCode.CLIPBOARD_CUT) clipText = null
        if (own || (shown != null && ime.agentSelection == null)) ime.setNeutralSuggestionStrip()
    }

    /**
     * LatinIME.onUpdateSelection, after the input logic: chips of a selection that is gone or another one go at
     * once (the input logic returns before it rebuilds the strip on some paths, e.g. a word it does not resume).
     */
    @JvmStatic fun afterSelectionUpdate(ime: LatinIME) {
        val bound = shown ?: return
        if (!bound.matches(ime)) ime.setNeutralSuggestionStrip()
    }

    /** ClipboardHistoryManager: a new clip, so "Einfügen" is asked again; chips on show are built again. */
    @JvmStatic fun clipChanged(ime: LatinIME) {
        clipText = null
        if (shown != null) ime.setNeutralSuggestionStrip()
    }
}
