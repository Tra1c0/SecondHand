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
 * keyboard shows. Every response gets a token; a newer response, a finished input or the user closing the chips
 * makes older tokens stale, and a stale callback adds nothing. A (re)start of the input or a new request does not:
 * it only re-opens a closed generation. Main thread only.
 */
class InlineAutofillGeneration {
    private var generation = 0
    private var open = false

    /**
     * An input started or the system asked for a new request: responses are accepted again. Only re-opens a closed
     * generation; while open it changes nothing, because a restart or a second request for the same field must not
     * make the chips of the response being inflated stale (device finding of stage 79: two of three chips).
     */
    fun open() {
        if (open) return
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

    /** Whether responses are accepted now (for the counters of stage 79b). */
    val isOpen: Boolean get() = open

    companion object {
        const val NONE = -1
    }
}

/**
 * Stage 79b: one inline autofill response as the keyboard sees it. It owns the row of chips ([InlineAutofillSlots]),
 * decides when the autofill container may be shown (not on the response, but when the first chip has been placed
 * for the current generation) and counts what happened to every callback. Numbers only, never a suggestion's text
 * or title. Callbacks arrive on the main thread; the fields are volatile because `dump()` reads them from a binder
 * thread and the diagnose page from the settings.
 */
class InlineAutofillResponse @JvmOverloads constructor(
    val count: Int,
    private val isCurrent: Current,
    private val onFirstChip: Runnable,
    private val clock: Clock = Clock { System.nanoTime() / 1_000_000 },
) {
    // own small interfaces: java.util.function types need API 24 (lint NewApi), the app supports 21
    fun interface Current { fun isCurrent(): Boolean }
    fun interface Clock { fun now(): Long }
    fun interface Place { fun at(position: Int) }

    private val slots = InlineAutofillSlots(count)
    private val startedAt = clock.now()
    private var firstChipFired = false

    /** Callbacks that brought a view / null / arrived after the response went stale. */
    @Volatile var withView = 0; private set
    @Volatile var nullViews = 0; private set
    @Volatile var staleDrops = 0; private set

    /** Milliseconds from the response to the first callback of any kind, -1 while none came. */
    @Volatile var firstCallbackMillis = -1L; private set

    /** Whether the first-chip action ran, that is, the container was shown for this response. */
    @Volatile var shown = false; private set

    /** Suggestions whose callback has not come yet. */
    val pending: Int get() = Math.max(0, count - withView - nullViews - staleDrops)

    /** Chips in the row now. */
    val placed: Int get() = slots.placed

    /**
     * The callback of the suggestion at [index]. A stale response (the field changed, a newer response came, the user
     * closed the chips) only counts. Otherwise a chip is put at its place by [place] (the position in the row), and when
     * that is the first chip of this response, [onFirstChip] runs exactly once, after [place]. Returns whether it ran.
     */
    fun onResult(index: Int, hasView: Boolean, place: Place): Boolean {
        if (firstCallbackMillis < 0) firstCallbackMillis = Math.max(0L, clock.now() - startedAt)
        if (!isCurrent.isCurrent()) { staleDrops++; return false }
        val position = slots.onResult(index, hasView)
        if (position < 0) {
            if (!hasView) nullViews++
            return false
        }
        withView++
        place.at(position)
        if (firstChipFired) return false
        firstChipFired = true
        onFirstChip.run()
        shown = true
        return true
    }
}

/**
 * Stage 79b: what the keyboard did with inline autofill responses since the process started: totals and the last
 * response, as numbers only. Read by `LatinIME.dump()` (`adb shell dumpsys input_method` prints one line
 * "InlineAutofill: ...") and by the diagnose page, so the owner can read it on the phone after a miss. Nothing is
 * stored or logged; nothing happens unless a response arrives.
 */
class InlineAutofillCounters {
    @Volatile var accepted = 0; private set
    @Volatile var empty = 0; private set
    @Volatile var refused = 0; private set
    @Volatile var stripWidthPx = -1; private set
    @Volatile var generationOpen = false
    @Volatile private var last: InlineAutofillResponse? = null

    class Snapshot(
        val accepted: Int, val empty: Int, val refused: Int, val stripWidthPx: Int, val generationOpen: Boolean,
        /** The last response, or null while none was accepted. */
        val last: InlineAutofillResponse?,
    )

    /** The strip width the request asked for (0 = not laid out yet). */
    fun onRequest(stripWidthPx: Int) { this.stripWidthPx = stripWidthPx }
    fun onEmpty() { empty++ }
    fun onRefused() { refused++ }
    fun onAccepted(response: InlineAutofillResponse) { accepted++; last = response }

    fun snapshot() = Snapshot(accepted, empty, refused, stripWidthPx, generationOpen, last)

    /** The one line of `dump()`. */
    fun line(): String {
        val s = snapshot()
        val sb = StringBuilder("InlineAutofill: responses accepted=").append(s.accepted)
            .append(" empty=").append(s.empty).append(" refused=").append(s.refused)
        val l = s.last
        if (l == null) sb.append(" last=none")
        else sb.append(" last: suggestions=").append(l.count).append(" withView=").append(l.withView)
            .append(" null=").append(l.nullViews).append(" stale=").append(l.staleDrops)
            .append(" pending=").append(l.pending).append(" placed=").append(l.placed)
            .append(" firstCallbackMs=").append(l.firstCallbackMillis).append(" shown=").append(l.shown)
        return sb.append("; stripWidthPx=").append(s.stripWidthPx)
            .append(" generation=").append(if (s.generationOpen) "open" else "closed").toString()
    }

    companion object {
        /** The one instance of the keyboard process. */
        @JvmField val shared = InlineAutofillCounters()
    }
}
