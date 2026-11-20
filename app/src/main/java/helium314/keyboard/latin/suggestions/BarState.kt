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
 * - While the content is the selection chips, "Werkzeuge zeigen" is sticky for that selection ([stickyTools]): range changes
 *   and the app's echo of the selection rebuild the chips but keep the tools, until the selection ends (a neutral update) or
 *   words come (typing). The left key then brings the chips back ([LeftKey.SHOW_CONTENT]), so both are one tap apart.
 * - [onToolsRequested] / [onContentRequested] are the old `setToolbarVisibility(true/false)` calls of `LatinIME`:
 *   forcing the tools, or the content when there is some (without content the bar stays on the tools: an empty
 *   suggestion area would leave a blank bar).
 */
class BarState {
    enum class Show { TOOLS, CONTENT }
    enum class LeftKey { GRID, SHOW_TOOLS, SHOW_CONTENT }

    var show = Show.TOOLS
        private set
    /** Whether the last update offered content (the tools may still be showing because the user asked for them). */
    var hasContent = false
        private set
    /** The user went back to the tools while content waits; cleared by the next content update. */
    var toolsChosen = false
        private set

    /** The user chose the tools while the selection chips were the content; holds for as long as that selection lasts. */
    var stickyTools = false
        private set
    /** The content is the selection chips. */
    var contentIsSelection = false
        private set

    /** What the left button does now. */
    val leftKey get() = when {
        show == Show.CONTENT -> LeftKey.SHOW_TOOLS
        stickyTools -> LeftKey.SHOW_CONTENT
        else -> LeftKey.GRID
    }

    fun onNeutral() {
        hasContent = false
        toolsChosen = false
        stickyTools = false
        contentIsSelection = false
        show = Show.TOOLS
    }

    /** Content arrived; [selection] says it is the chips of a selected text (their rebuilds keep a sticky choice of the tools). */
    fun onContent(selection: Boolean = false) {
        hasContent = true
        contentIsSelection = selection
        if (selection && stickyTools) return // the same selection, rebuilt: the tools stay
        stickyTools = false
        toolsChosen = false
        show = Show.CONTENT
    }

    fun onToolsRequested() {
        show = Show.TOOLS
    }

    fun onContentRequested() {
        if (stickyTools) return // the user asked for the tools for this selection
        show = if (hasContent) Show.CONTENT else Show.TOOLS
    }

    /** The tap on the left button; returns what it did. */
    fun onLeftKey(): LeftKey {
        val did = leftKey
        when (did) {
            LeftKey.SHOW_TOOLS -> {
                show = Show.TOOLS
                toolsChosen = true
                stickyTools = contentIsSelection
            }
            LeftKey.SHOW_CONTENT -> {
                show = Show.CONTENT
                toolsChosen = false
                stickyTools = false
            }
            LeftKey.GRID -> Unit
        }
        return did
    }

    /** The keyboard goes away or the strip starts again: tools, nothing waiting. */
    fun reset() = onNeutral()
}
