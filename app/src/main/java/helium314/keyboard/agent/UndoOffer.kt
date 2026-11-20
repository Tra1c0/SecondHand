// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode

/**
 * Fork, stage 88 ("Kontextwerkzeuge"): after the undo tool was used, the bar says that a redo makes sense. The keyboard cannot
 * know whether the app really has something to redo (undo and redo are the key events Ctrl+Z and Ctrl+Shift+Z, the app decides),
 * so this is a heuristic, nothing more: the offer starts with an undo and ends with anything that changes the text (a typed
 * character, a gesture, a chosen suggestion, emoji, delete, paste, a text action, a line break, ...), a new field or the keyboard going away. Using redo or undo again keeps it.
 * Pure state; the strip shows it ([helium314.keyboard.latin.suggestions.SuggestionStripView]).
 */
object UndoOffer {
    /** Codes that change nothing in the text: they leave the offer as it is. */
    private val KEEP = setOf(KeyCode.UNDO, KeyCode.REDO, KeyCode.SHIFT, KeyCode.CAPS_LOCK, KeyCode.ALPHA, KeyCode.SYMBOL,
        KeyCode.SYMBOL_ALPHA, KeyCode.NUMPAD, KeyCode.LANGUAGE_SWITCH)

    var active = false
        private set

    /** Called when the strip should look again (one strip is interested at a time). */
    @JvmStatic
    var onChange: (() -> Unit)? = null

    /** Every code the keyboard acts on, before it is handled. */
    @JvmStatic
    fun onCode(code: Int) {
        when {
            code == KeyCode.UNDO -> set(true)
            code in KEEP -> Unit
            else -> set(false)
        }
    }

    /**
     * Text came in without a key code: [helium314.keyboard.keyboard.KeyboardActionListenerImpl.onTextInput] (emoji, several characters),
     * the start of a gesture, a chosen suggestion ([helium314.keyboard.latin.suggestions.SuggestionStripView]). Ends the offer.
     */
    @JvmStatic
    fun onTextChange() = set(false)

    /** A new field, or the keyboard goes away. */
    @JvmStatic
    fun reset() = set(false)

    private fun set(value: Boolean) {
        if (active == value) return
        active = value
        onChange?.invoke()
    }
}
