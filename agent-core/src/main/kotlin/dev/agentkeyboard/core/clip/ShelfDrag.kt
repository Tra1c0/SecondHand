// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.clip

/**
 * Stage 102: the touch logic of the screenshot shelf, without any view (the clipboard view feeds it events and a clock;
 * the unit tests feed it a fake clock). One finger: a second pointer cancels a lifted or dragged item.
 *
 * ```
 * IDLE --down on a screenshot or a folder--> PRESSED
 * PRESSED --moved >= slop--> IDLE with [Event.Scroll] (the list scrolls as before)
 * PRESSED --up--> [Event.Tap] (insert the screenshot, open the folder)
 * PRESSED --held [Config.liftMs] ([tick])--> LIFTED with [Event.Lift] (haptic feedback)
 * LIFTED --up without moving--> [Event.Menu] (pin, rename: nothing changes by itself)
 * LIFTED --moved >= slop, a screenshot--> DRAGGING with [Event.Start]; a folder --> IDLE with [Event.Cancel]
 * DRAGGING --the target under the finger changes--> [Event.Hover]; held on [ShelfTarget.Out] for [Config.dwellMs] --> [Event.Dwell] once
 * DRAGGING --up--> [Event.Drop] with the target under the finger
 * LIFTED or DRAGGING --cancel, a second finger--> IDLE with [Event.Cancel]: nothing is changed
 * ```
 * Which target lies under the finger is the view's answer ([Geometry.targetAt]); what a drop does is [ScreenshotShelf.drop].
 */
class ShelfDrag(private val geometry: Geometry, private val config: Config) {
    class Config(val slopPx: Float, val liftMs: Long = 350L, val dwellMs: Long = 360L)

    fun interface Geometry {
        fun targetAt(x: Float, y: Float): ShelfTarget
    }

    /** What the finger went down on. [folder]: the folder the screenshot is shown in (its window is open), or null in the list. */
    sealed class Grab {
        data class Shot(val shot: Long, val folder: Long?) : Grab()
        data class Folder(val folder: Long) : Grab()
    }

    enum class Phase { IDLE, PRESSED, LIFTED, DRAGGING }

    sealed class Event {
        object Scroll : Event()
        data class Tap(val grab: Grab) : Event()
        data class Lift(val grab: Grab) : Event()
        data class Menu(val grab: Grab) : Event()
        data class Start(val grab: Grab.Shot) : Event()
        data class Hover(val target: ShelfTarget) : Event()
        data class Dwell(val target: ShelfTarget) : Event()
        data class Drop(val grab: Grab.Shot, val target: ShelfTarget) : Event()
        data class Cancel(val grab: Grab) : Event()
    }

    var phase = Phase.IDLE
        private set
    var grab: Grab? = null
        private set
    private var pointer = -1
    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L
    var x = 0f
        private set
    var y = 0f
        private set
    var target: ShelfTarget = ShelfTarget.None
        private set
    private var hoverSince = 0L
    private var dwelled = false

    /** Whether the view must keep the touch to itself (lifted or dragging): the list neither scrolls nor clicks then. */
    val capturing get() = phase == Phase.LIFTED || phase == Phase.DRAGGING

    /** A finger went down on [on] (null: nothing of the shelf). Returns whether it is tracked. */
    fun down(pointerId: Int, x: Float, y: Float, now: Long, on: Grab?): Boolean {
        if (phase != Phase.IDLE || on == null) return false
        pointer = pointerId
        downX = x; downY = y; this.x = x; this.y = y; downAt = now
        grab = on
        phase = Phase.PRESSED
        target = ShelfTarget.None
        return true
    }

    /** The timer: lifts a held item. Returns [Event.Lift] once, or [Event.Dwell] while dragging. */
    fun tick(now: Long): Event? {
        val grabbed = grab ?: return null
        if (phase == Phase.PRESSED && now - downAt >= config.liftMs) {
            phase = Phase.LIFTED
            return Event.Lift(grabbed)
        }
        if (phase == Phase.DRAGGING && target == ShelfTarget.Out && !dwelled && now - hoverSince >= config.dwellMs) {
            dwelled = true
            return Event.Dwell(target)
        }
        return null
    }

    fun move(pointerId: Int, x: Float, y: Float, now: Long): Event? {
        if (pointerId != pointer || phase == Phase.IDLE) return null
        this.x = x; this.y = y
        val far = (x - downX) * (x - downX) + (y - downY) * (y - downY) >= config.slopPx * config.slopPx
        when (phase) {
            Phase.PRESSED -> {
                tick(now)?.let { return it } // a late timer: the hold came first
                if (far) { reset(); return Event.Scroll }
            }
            Phase.LIFTED -> if (far) {
                val grabbed = grab
                if (grabbed !is Grab.Shot) { reset(); return grabbed?.let { Event.Cancel(it) } }
                phase = Phase.DRAGGING
                hover(geometry.targetAt(x, y), now)
                return Event.Start(grabbed)
            }
            Phase.DRAGGING -> {
                val now2 = geometry.targetAt(x, y)
                if (now2 != target) { hover(now2, now); return Event.Hover(now2) }
                return tick(now)
            }
            Phase.IDLE -> {}
        }
        return null
    }

    fun up(pointerId: Int, x: Float, y: Float, now: Long): Event? {
        if (pointerId != pointer || phase == Phase.IDLE) return null
        val grabbed = grab ?: return null
        val event = when (phase) {
            Phase.PRESSED -> if (now - downAt >= config.liftMs) Event.Menu(grabbed) else Event.Tap(grabbed)
            Phase.LIFTED -> Event.Menu(grabbed)
            Phase.DRAGGING -> Event.Drop(grabbed as Grab.Shot, geometry.targetAt(x, y))
            Phase.IDLE -> null
        }
        reset()
        return event
    }

    /** The system took the touch, the view went away, or the gesture is to end without a change. */
    fun cancel(): Event? {
        val grabbed = grab
        val wasActive = phase != Phase.IDLE
        reset()
        return if (wasActive && grabbed != null) Event.Cancel(grabbed) else null
    }

    /** A second finger went down: a lifted or dragged item ends without a change ([Event.Cancel]); a press is forgotten. */
    fun secondFinger(): Event? {
        if (capturing) return cancel()
        reset()
        return null
    }

    private fun hover(to: ShelfTarget, now: Long) {
        target = to
        hoverSince = now
        dwelled = false
    }

    private fun reset() {
        phase = Phase.IDLE
        pointer = -1
        grab = null
        target = ShelfTarget.None
        dwelled = false
    }
}
