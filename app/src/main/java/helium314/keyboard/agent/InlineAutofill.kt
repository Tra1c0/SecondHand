// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

/**
 * Stage 79: the decisions of the inline autofill chips (password managers) that do not need the platform's
 * remote views, so they can be tested without a device. `InlineAutofillUtils` and `LatinIME` call them.
 * Nothing here reads or logs a suggestion's text.
 */
object InlineAutofillSizing {
    /** The old fixed maximum (740 px) at a density of 2.6, used only when neither the strip nor the display has a width. */
    const val FALLBACK_MAX_DP = 280

    /** A chip is never asked to be narrower than this (the old 100 px at a density of 2.6 was about 40 dp). */
    const val MIN_DP = 40

    /** What the strip keeps for itself next to the chips: the toolbar key on the left (36 dp) and the close button (30 dp). */
    const val RESERVED_DP = 66

    private fun scale(density: Float) = if (density > 0f) density else 1f

    @JvmStatic
    fun minWidthPx(density: Float): Int = Math.round(MIN_DP * scale(density))

    /**
     * The widest chip the service may build, in pixels: the strip's real width minus what the strip keeps for its
     * own keys. `stripWidthPx` is 0 while the strip is not laid out yet; then the display width stands in for it
     * (the keyboard is as wide as the display unless it floats), and without that a fixed width in dp. Never
     * smaller than [minWidthPx], so the pair of sizes stays valid.
     */
    @JvmStatic
    fun maxWidthPx(stripWidthPx: Int, displayWidthPx: Int, density: Float): Int {
        val d = scale(density)
        val min = minWidthPx(density)
        val base = if (stripWidthPx > 0) stripWidthPx else displayWidthPx
        if (base <= 0) return Math.max(min, Math.round(FALLBACK_MAX_DP * d))
        return Math.max(min, base - Math.round(RESERVED_DP * d))
    }
}

/**
 * Stage 79 (W-b): the chips are inflated one by one and each callback arrives when it is ready, in any order,
 * or never. Nothing waits for the others: a chip is put into the row at the place its service-given index
 * asks for, among the chips that have arrived so far, so the row is always in the service's order and a chip
 * that fails or never comes only leaves a gap that is not there. Main thread only, like the callbacks.
 */
class InlineAutofillSlots(private val count: Int) {
    private val answered = BooleanArray(Math.max(count, 0))
    private val present = BooleanArray(Math.max(count, 0))

    /**
     * Records the result for the suggestion at [index]. Returns the position in the row (the number of
     * already placed chips with a smaller index) at which a chip is to be inserted, or -1 when nothing is
     * to be added: the inflation failed ([hasView] false), the index is unknown, or this index answered before.
     */
    fun onResult(index: Int, hasView: Boolean): Int {
        if (index < 0 || index >= answered.size || answered[index]) return -1
        answered[index] = true
        if (!hasView) return -1
        var position = 0
        for (i in 0 until index) if (present[i]) position++
        present[index] = true
        return position
    }

    /** Whether every suggestion has answered, with a chip or without. */
    val isComplete: Boolean get() = answered.all { it }

    /** How many chips are in the row now. */
    val placed: Int get() = present.count { it }
}

/**
 * Stage 79 (W-c): which inline autofill response and which late inflation callback still belong to what the
 * keyboard shows. Every response gets a token; a new response, a new input, a finished input or the user closing
 * the chips makes older tokens stale, and a stale callback adds nothing. Main thread only.
 */
class InlineAutofillGeneration {
    private var generation = 0
    private var open = false

    /** An input started or the system asked for a new request: responses are accepted, older ones are stale. */
    fun open() {
        generation++
        open = true
    }

    /** The input finished: responses that arrive now belong to nothing, and everything in flight is stale. */
    fun close() {
        generation++
        open = false
    }

    /** Everything in flight is stale (the user closed the chips), but responses are still accepted. */
    fun invalidate() {
        generation++
    }

    /** The token for a response that just arrived, or [NONE] if no input is active; it makes all older tokens stale. */
    fun beginResponse(): Int {
        if (!open) return NONE
        generation++
        return generation
    }

    fun isCurrent(token: Int): Boolean = open && token != NONE && token == generation

    companion object {
        const val NONE = -1
    }
}
