// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions

import helium314.keyboard.latin.utils.BarTools
import helium314.keyboard.latin.utils.ToolbarKey

/**
 * Fork, stage 88: the touch logic of customizing the bar, without any view (the views feed it events and a clock, which
 * is what lets the unit tests run it with a fake clock). It replaces the system drag and drop of stage 86 (see
 * `docs/LEISTE-KONZEPT.md`, sections 3, 4 and 6). One finger at a time: a second pointer is ignored.
 *
 * ```
 * IDLE --down on a pill or a slot--> PRESSED
 * PRESSED --held [Config.holdMs]--> DRAGGING
 * PRESSED --moved >= slop on a slot (any direction), or sideways on a pill--> DRAGGING
 * PRESSED --moved >= slop vertically on a pill--> IDLE with [Event.Scroll] (the grid scrolls; the scroll view may keep the end of the gesture to itself)
 * PRESSED --up--> a tap ([Event.Tapped]): on a slot the tool leaves the bar, on a pill it goes in or out
 * DRAGGING --finger in the bar zone--> the gap follows the finger ([Event.GapMoved]); outside --> the gap is closed
 * DRAGGING --up--> [Event.Dropped]; cancel --> [Event.Cancelled], nothing saved
 * ```
 * The model ([BarEditor]) does the list work and saves on a drop. Nothing here knows an Android class.
 */
class BarDrag(private val editor: BarEditor, private val geometry: Geometry, private val config: Config) {
    class Config(val holdMs: Long = 180L, val slopPx: Float)

    /** The two questions the logic asks of the layout. */
    interface Geometry {
        /** Whether the finger is in the bar zone: the bar plus some room below it, and everything above it. */
        fun inBarZone(x: Float, y: Float): Boolean
        /** Which of [count] evenly spread slots is nearest to the horizontal position [x] (0 until count). */
        fun slotAt(x: Float, count: Int): Int
    }

    sealed class Target {
        class Pill(val key: ToolbarKey) : Target()
        class Slot(val key: ToolbarKey) : Target()
        val tool get() = when (this) { is Pill -> key; is Slot -> key }
    }

    enum class Phase { IDLE, PRESSED, DRAGGING, SCROLLING }

    sealed class Event {
        class PickedUp(val key: ToolbarKey, val fromBar: Boolean) : Event()
        /** The gap of the dragged tool is at [slot] of the bar; null when it closed. */
        class GapMoved(val slot: Int?) : Event()
        /** A tool from the grid is over the bar and the bar is full. */
        object Full : Event()
        /** The drop: [toBar] where it went (false: the bar did not take it or the tool left it). */
        class Dropped(val key: ToolbarKey, val result: BarEditor.Result, val toBar: Boolean) : Event()
        object Cancelled : Event()
        object Scroll : Event()
        /** A short touch: on a slot the tool leaves the bar, on a pill ([onPill]) it goes in or out. */
        class Tapped(val key: ToolbarKey, val onPill: Boolean) : Event()
    }

    var phase = Phase.IDLE
        private set
    private var pointer = -1
    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L
    private var target: Target? = null
    /** The slot the gap is at while over the bar; null otherwise. */
    var gap: Int? = null
        private set
    var x = 0f
        private set
    var y = 0f
        private set
    val dragged get() = if (phase == Phase.DRAGGING) target?.tool else null
    val fromBar get() = target is Target.Slot
    private var wasFull = false
    /** Whether the finger has gone [Config.slopPx] from where it went down; a hold that is lifted on the spot puts the tool back. */
    private var travelled = false

    /** A finger went down on [on]. False when it is not tracked (another finger is active already, or nothing is under it). */
    fun down(pointerId: Int, x: Float, y: Float, now: Long, on: Target?): Boolean {
        if (phase != Phase.IDLE || on == null) return false
        pointer = pointerId
        downX = x; downY = y; this.x = x; this.y = y; downAt = now
        target = on
        phase = Phase.PRESSED
        gap = null
        wasFull = false
        travelled = false
        return true
    }

    /** When the hold would be over; the view posts a timer for it and calls [tick]. */
    fun holdDueAt() = if (phase == Phase.PRESSED) downAt + config.holdMs else Long.MAX_VALUE

    fun tick(now: Long): List<Event> = if (phase == Phase.PRESSED && now - downAt >= config.holdMs) pickUp() else emptyList()

    fun move(pointerId: Int, x: Float, y: Float, now: Long): List<Event> {
        if (pointerId != pointer || phase == Phase.IDLE || phase == Phase.SCROLLING) return emptyList()
        this.x = x; this.y = y
        if (Math.hypot((x - downX).toDouble(), (y - downY).toDouble()) >= config.slopPx) travelled = true
        if (phase == Phase.PRESSED) {
            val dx = x - downX
            val dy = y - downY
            val moved = Math.hypot(dx.toDouble(), dy.toDouble()) >= config.slopPx
            return when {
                now - downAt >= config.holdMs -> pickUp()
                !moved -> emptyList()
                target is Target.Slot -> pickUp()
                // the grid scrolls: the scroll view owns this gesture and may never send its end, so this logic is idle at once
                Math.abs(dy) > Math.abs(dx) -> { reset(); listOf(Event.Scroll) }
                else -> pickUp()
            }
        }
        return over()
    }

    fun up(pointerId: Int, x: Float, y: Float, now: Long): List<Event> {
        if (pointerId != pointer || phase == Phase.IDLE) return emptyList()
        this.x = x; this.y = y
        val events = when (phase) {
            Phase.PRESSED -> {
                if (now - downAt >= config.holdMs) pickUp() + finish()
                else target?.let { listOf(Event.Tapped(it.tool, it is Target.Pill)) } ?: emptyList()
            }
            Phase.DRAGGING -> finish()
            else -> emptyList()
        }
        reset()
        return events
    }

    /** The system took the touch (or the keyboard went away): back to the state before the drag, nothing saved. */
    fun cancel(): List<Event> {
        val events = if (phase == Phase.DRAGGING) { editor.dragEnd(); listOf(Event.Cancelled) } else emptyList()
        reset()
        return events
    }

    private fun reset() {
        phase = Phase.IDLE
        pointer = -1
        target = null
        gap = null
    }

    private fun pickUp(): List<Event> {
        val on = target ?: return emptyList()
        phase = Phase.DRAGGING
        editor.dragStart(on.tool, fromBar = on is Target.Slot)
        return listOf<Event>(Event.PickedUp(on.tool, on is Target.Slot)) + over()
    }

    /** The slot of the bar for the finger: the nearest among the slots the bar will have after the drop. */
    private fun slotForFinger() = editor.listIndexForSlot(geometry.slotAt(x, editor.slotCountAfterDrop()))

    private fun over(): List<Event> {
        val key = dragged ?: return emptyList()
        if (!geometry.inBarZone(x, y)) {
            editor.dragLeftBar()
            wasFull = false
            return if (gap != null) { gap = null; listOf(Event.GapMoved(null)) } else emptyList()
        }
        val slot = geometry.slotAt(x, editor.slotCountAfterDrop())
        val fits = editor.dragOverBar(editor.listIndexForSlot(slot))
        if (!fits) {
            val events = mutableListOf<Event>()
            if (gap != null) { gap = null; events += Event.GapMoved(null) }
            if (!wasFull) { wasFull = true; events += Event.Full }
            return events
        }
        wasFull = false
        if (gap == slot) return emptyList()
        gap = slot
        return listOf(Event.GapMoved(slot))
    }

    private fun finish(): List<Event> {
        val key = dragged ?: return emptyList()
        if (!travelled) { editor.dragEnd(); return listOf(Event.Cancelled) } // held and lifted on the spot: put back, nothing changes
        if (geometry.inBarZone(x, y)) {
            val slot = slotForFinger()
            val result = editor.dropOnBar(slot)
            return listOf(Event.Dropped(key, result, result != BarEditor.Result.FULL))
        }
        return listOf(Event.Dropped(key, editor.dropOnGrid(), false))
    }

    companion object {
        /** More tools than this cannot be in the bar. */
        const val MAX = BarTools.MAX
    }
}
