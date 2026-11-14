// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.charart

import android.view.inputmethod.EditorInfo
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.SimplePopups

/**
 * Stage 53: the pen in the popup of the emoji key opens the character art window. The layout
 * parser (`TextKeyData`) asks here for the popup of every key labelled "emoji".
 */
object CharArtKey {
    /** The pen; a text label, so no icon set needs it. */
    const val LABEL = "✎"
    const val POPUP_KEY = "$LABEL|!code/${KeyCode.AGENT_CHAR_ART}"

    /**
     * The popup of the emoji key, or null where the window never opens: password fields, fields
     * without learning (incognito) and a locked device. The controller checks all of them again
     * when the pen is tapped, also the incognito setting, which the layout does not know.
     */
    fun popupKeys(id: KeyboardId): SimplePopups? {
        if (id.deviceLocked || id.isPasswordInput || id.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0) return null
        return SimplePopups(listOf(POPUP_KEY))
    }
}
