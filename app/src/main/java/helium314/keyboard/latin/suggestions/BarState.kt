// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions

/**
 * Fork, stage 85: what the fixed bar shows, as a plain state machine without views. The bar shows either the
 * tools or content (word suggestions, command chips, selection chips, clipboard pill, autofill, symbol chips,
 * update chip); which content wins over which stays with `LatinIME`, this class only sees that there is some.
 *
 * - Nothing to offer ([onNeutral]): the tools. A new field, hiding the keyboard and an emptied field do this.
 * - Anything to offer ([onContent]): the content replaces the tools.
 * - Left button while content shows ([onLeftKey]): back to the tools; the content comes back with the next
 *   content update (the next key, a moved cursor, a new chip), not before.
 * - Left button while the tools show: opens the grid with all tools; the state does not change.
 * - [onToolsRequested] / [onContentRequested] are the old `setToolbarVisibility(true/false)` calls of `LatinIME`:
 *   forcing the tools, or the content when there is some (without content the bar stays on the tools: an empty
 *   suggestion area would leave a blank bar).
 */
class BarState {
    enum class Show { TOOLS, CONTENT }
    enum class LeftKey { GRID, SHOW_TOOLS }

    var show = Show.TOOLS
        private set
    /** Whether the last update offered content (the tools may still be showing because the user asked for them). */
    var hasContent = false
        private set
    /** The user went back to the tools while content waits; cleared by the next content update. */
    var toolsChosen = false
        private set

    /** What the left button does now. */
    val leftKey get() = if (show == Show.TOOLS) LeftKey.GRID else LeftKey.SHOW_TOOLS

    fun onNeutral() {
        hasContent = false
        toolsChosen = false
        show = Show.TOOLS
    }

    fun onContent() {
        hasContent = true
        toolsChosen = false
        show = Show.CONTENT
    }

    fun onToolsRequested() {
        show = Show.TOOLS
    }

    fun onContentRequested() {
        show = if (hasContent) Show.CONTENT else Show.TOOLS
    }

    /** The tap on the left button; returns what it did. */
    fun onLeftKey(): LeftKey {
        val did = leftKey
        if (did == LeftKey.SHOW_TOOLS) {
            show = Show.TOOLS
            toolsChosen = true
        }
        return did
    }

    /** The keyboard goes away or the strip starts again: tools, nothing waiting. */
    fun reset() = onNeutral()
}
