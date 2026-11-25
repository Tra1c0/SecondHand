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
 * - Stage 99 (owner, 2026-11-25: "wenn eindeutig nicht mehr geschrieben wird"): in the session the middle shows words or nothing, never the
 *   tools because nothing is offered (left key and circle stand fixed beside it, see [FixedBar.arrangeEnd]). The tools come by
 *   themselves only after [WRITING_PAUSE_MS] without a key ([onPause], whatever the middle shows); the next key ends that rest
 *   ([onTyped]) and the next update shows the words area again.
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
    /** Stage 99: the tools came after a pause in the session (or were forced); they stay until the next key or content, see [onPause]. */
    var resting = false
        private set
    /** The session is on: neutral updates keep the words area. */
    val calmWriting get() = calm && writing

    fun setCalm(on: Boolean) {
        calm = on
        if (!on) { writing = false; pinned = false; resting = false }
    }

    /** A character key, a wipe or a delete went to the field: the session starts (or goes on), a pinned choice of the tools ends. */
    fun onTyped() {
        if (!calm) return
        writing = true
        pinned = false
        resting = false
    }

    /**
     * Stage 99: [WRITING_PAUSE_MS] without a key in the session: the middle shows the tools, whether words stand or not (stage 89b did
     * this only without words, which made it look random); the session goes on, the next key brings the words area back. Chips are
     * kept by the strip (it does not call this while one shows).
     */
    fun onPause() {
        if (!calm || !writing || pinned) return
        show = Show.TOOLS
        resting = true
        toolsChosen = false
    }

    /** The session is over (an emptied field): the next neutral update shows the tools. */
    fun endSession() { writing = false; pinned = false; resting = false }

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
            // the writing session (stage 99): words or nothing in the middle, never the tools because nothing is offered; the tools stay
            // only when the user asked for them ([pinned]) or after a pause ([resting]) until the next key
            hasContent = false
            stickyTools = false
            contentIsSelection = false
            show = if (pinned || resting) Show.TOOLS else Show.CONTENT
            return
        }
        writing = false
        pinned = false
        resting = false
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
        resting = false
        stickyTools = false
        toolsChosen = false
        show = Show.CONTENT
    }

    fun onToolsRequested() {
        show = Show.TOOLS
        if (calmWriting) resting = true // forced tools (auto show) are not taken away by the next neutral update, only by a key or content
    }

    fun onContentRequested() {
        if (stickyTools) return // the user asked for the tools for this selection
        if (pinned) return
        resting = false
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
                resting = false
            }
            LeftKey.GRID -> Unit
        }
        return did
    }

    /** The keyboard goes away or the strip starts again: tools, nothing waiting. */
    fun reset() { writing = false; pinned = false; resting = false; onNeutral() }

    companion object {
        /**
         * Fork, stage 89: how long the words of the bar stay when an update says "nothing to offer" while the user writes. A new
         * list within this time replaces them directly (no empty bar in between); after it the middle is emptied (stage 99: an empty
         * middle is allowed, the left key and the circle stand beside it). The suggestions of a key, and the resume after a delete,
         * arrive after `mDelayInMillisecondsToUpdateSuggestions` (100 ms) plus the answer of the dictionary on its own thread and one
         * hop to the main thread; 250 ms leaves 150 ms for that, and the time runs from the last key. A longer time would leave
         * outdated words standing (they cannot be tapped meanwhile, see `SuggestionStripView.staleWords`). Stage 89b had 800 ms.
         */
        const val CALM_DEBOUNCE_MS = 250L

        /**
         * Fork, stage 99: after this long without a key in the writing session the middle shows the tools ([onPause]); the next key
         * brings the words area back. 4 s: pauses between keys while typing are far below one second, and thinking pauses inside a
         * sentence of one to three seconds are common (stage 89 rejected 1.5 s for that reason); four seconds without a key are clearly
         * "no longer writing". Dictation needs no pause: the circle stands beside the words.
         */
        const val WRITING_PAUSE_MS = 4000L
    }
}
