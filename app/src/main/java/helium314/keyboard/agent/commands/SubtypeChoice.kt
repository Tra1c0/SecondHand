// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodSubtype

/**
 * The language a language command chose for one text field (stage 24g fix, P66).
 *
 * A command chip removes the typed command before it switches the keyboard. When that leaves the
 * field empty, many apps restart the input in the same field, and HeliBoard then applies the field's
 * hint locales and the per-app subtype again ([helium314.keyboard.latin.utils.SubtypeState]); that
 * switched the keyboard straight back, e.g. to English with English popup keys. The language key
 * never empties a field, so it was not affected. While the same field restarts and the chosen
 * subtype is still in use, [holds] tells `LatinIME.onStartInputInternal` to skip that automatic
 * switch. Any other field, or any other subtype in use, ends the choice. Memory only.
 */
object SubtypeChoice {
    private data class Field(val packageName: String?, val fieldId: Int, val inputClass: Int)

    private var field: Field? = null
    private var subtype: InputMethodSubtype? = null

    private fun fieldOf(editorInfo: EditorInfo) =
        Field(editorInfo.packageName, editorInfo.fieldId, editorInfo.inputType and InputType.TYPE_MASK_CLASS)

    /** The user picked [chosen] by a command in the field described by [editorInfo]. */
    @JvmStatic
    fun remember(editorInfo: EditorInfo?, chosen: InputMethodSubtype) {
        if (editorInfo == null) { clear(); return }
        field = fieldOf(editorInfo)
        subtype = chosen
    }

    /**
     * Whether the input starting in [editorInfo] must keep [current] instead of switching by hint
     * locales or the per-app subtype. Forgets the choice once it no longer applies.
     */
    @JvmStatic
    fun holds(editorInfo: EditorInfo?, current: InputMethodSubtype?): Boolean {
        val chosen = subtype ?: return false
        if (editorInfo == null || current != chosen || fieldOf(editorInfo) != field) {
            clear()
            return false
        }
        return true
    }

    @JvmStatic
    fun clear() {
        field = null
        subtype = null
    }
}
