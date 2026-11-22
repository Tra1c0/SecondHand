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
 * - Writing session (fork, stage 89, `docs/LEISTE-RUHE-KONZEPT.md`): in a field that can show words ([calm]) the first typed key, wiped word
 *   or delete ([onTyped]) starts a session. From then on a neutral update ([onNeutral]) does not bring the tools back: the bar stays on
 *   the words area (empty when nothing is offered). The tools return only for an emptied field, a new field, a reopened keyboard, or
 *   when the user asks ([onLeftKey], [onToolTapped]: [pinned], until the next typed key). Outside such a field everything is as before.
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

    /** The rule of the writing session applies in this field (the fixed bar, a field that can show words). Set by the strip before each update. */
    var calm = false
        private set
    /** Something was typed in this session (key, wipe or delete); the neutral strip no longer means the tools. */
    var writing = false
        private set
    /** The user asked for the tools in the writing session; no update takes them away until the next typed key. */
    var pinned = false
        private set
    /** The session is on: neutral updates keep the words area. */
    val calmWriting get() = calm && writing

    fun setCalm(on: Boolean) {
        calm = on
        if (!on) { writing = false; pinned = false }
    }

    /** A character key, a wipe or a delete went to the field: the session starts (or goes on), a pinned choice of the tools ends. */
    fun onTyped() {
        if (!calm) return
        writing = true
        pinned = false
    }

    /** Stage 89b: a real pause with nothing to offer in the session: the tools instead of an empty words area; the session goes on. */
    fun onPause() {
        if (calm && writing && show == Show.CONTENT && !hasContent) show = Show.TOOLS
    }

    /** The session is over (an emptied field): the next neutral update shows the tools. */
    fun endSession() { writing = false; pinned = false }

    /** A tool of the bar was tapped while the tools show: they stay (a second undo, the redo arrow) until the next typed key. */
    fun onToolTapped() {
        if (calm && show == Show.TOOLS) pinned = true
    }

    /** What the left button does now. */
    val leftKey get() = when {
        show == Show.CONTENT -> LeftKey.SHOW_TOOLS
        stickyTools -> LeftKey.SHOW_CONTENT
        else -> LeftKey.GRID
    }

    /** [fieldEmpty]: the cursor is at the start and nothing is before it; then the session is over and the tools come back. */
    fun onNeutral(fieldEmpty: Boolean = false) {
        if (calm && writing && !fieldEmpty) {
            // the writing session: the words area stays while words stand (the strip holds them while typing goes on); tools that show
            // stay (stage 89b, owner: "die Leiste wird schwarz"): never from the tools to an empty words area
            hasContent = false
            stickyTools = false
            contentIsSelection = false
            show = if (pinned || show == Show.TOOLS) Show.TOOLS else Show.CONTENT
            return
        }
        writing = false
        pinned = false
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
        if (pinned && !selection) return // the user asked for the tools: no word, pill or chip takes them away before the next typed key
        pinned = false
        stickyTools = false
        toolsChosen = false
        show = Show.CONTENT
    }

    fun onToolsRequested() {
        show = Show.TOOLS
    }

    fun onContentRequested() {
        if (stickyTools) return // the user asked for the tools for this selection
        if (pinned) return
        show = if (hasContent || calmWriting) Show.CONTENT else Show.TOOLS
    }

    /** The tap on the left button; returns what it did. */
    fun onLeftKey(): LeftKey {
        val did = leftKey
        when (did) {
            LeftKey.SHOW_TOOLS -> {
                show = Show.TOOLS
                toolsChosen = true
                pinned = calm
                stickyTools = contentIsSelection
            }
            LeftKey.SHOW_CONTENT -> {
                show = Show.CONTENT
                toolsChosen = false
                stickyTools = false
                pinned = false
            }
            LeftKey.GRID -> Unit
        }
        return did
    }

    /** The keyboard goes away or the strip starts again: tools, nothing waiting. */
    fun reset() { writing = false; pinned = false; onNeutral() }

    companion object {
        /**
         * Fork, stage 89: how long the words of the bar stay when an update says "nothing to offer" while the user writes. A new
         * list within this time replaces them directly (no empty bar in between); after it the bar is emptied. The suggestions of a
         * key, and the resume after a delete, arrive after `mDelayInMillisecondsToUpdateSuggestions` (100 ms) plus the answer of the
         * dictionary on its own thread and one hop to the main thread; 250 ms leaves 150 ms for that. A longer time would leave outdated
         * words standing (they cannot be tapped meanwhile, see `SuggestionStripView.staleWords`).
         */
        const val CALM_DEBOUNCE_MS = 800L
    }
}
