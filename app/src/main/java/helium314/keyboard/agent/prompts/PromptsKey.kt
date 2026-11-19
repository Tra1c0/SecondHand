// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.prompts

import android.view.inputmethod.EditorInfo
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode

/**
 * Stage 74b: the prompt store icon in the popup of the comma key opens the prompt window. The layout
 * parser (`TextKeyData.getCommaPopupKeys`) asks [shown] and adds [POPUP_KEY] after the clipboard key.
 */
object PromptsKey {
    const val POPUP_KEY = "${KeyboardIconsSet.PREFIX_ICON}${KeyboardIconsSet.NAME_AGENT_PROMPTS_KEY}|!code/${KeyCode.AGENT_PROMPTS}"

    /**
     * Whether the popup gets the key: not where the window never opens (locked device, password
     * fields, fields without learning, i.e. incognito) and not in number layouts. The controller
     * checks all of this again on the tap, also the incognito setting the layout does not know.
     */
    fun shown(id: KeyboardId): Boolean =
        !id.deviceLocked && !id.isPasswordInput && !id.element.isNumberLayout &&
            id.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING == 0
}
