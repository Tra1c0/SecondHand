// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions

import helium314.keyboard.latin.utils.BarTools
import helium314.keyboard.latin.utils.ToolbarKey

/**
 * Fork, stage 86: the state of the edit mode of the fixed bar, without any view. The bar is changed by dragging a
 * tool from the grid into the bar, within the bar, and out of the bar back to the grid, or by the alternative
 * without dragging (tap, TalkBack actions): [add], [remove], [shift].
 *
 * **Saved on drop**: every finished change is written at once through [write]; leaving the edit mode only closes
 * the panels. A drag that does not end in a drop on the bar or on the grid (the keyboard is closed, the finger
 * goes elsewhere) saves nothing: [dragEnd] drops the [preview].
 *
 * While a drag is over the bar, [preview] holds the bar as it would be after the drop, so that the other tools
 * visibly move over. [shown] is what the bar draws.
 */
class BarEditor(private val read: () -> List<ToolbarKey>, private val write: (List<ToolbarKey>) -> Unit) {
    enum class Result { CHANGED, FULL, UNCHANGED }

    /** The saved bar. */
    var bar: List<ToolbarKey> = read()
        private set
    /** The bar as the drop would leave it, while a drag is over the bar or has left it; null otherwise. */
    var preview: List<ToolbarKey>? = null
        private set
    var dragged: ToolbarKey? = null
        private set
    var draggedFromBar = false
        private set
    /** Whether the last change was refused because the bar holds [BarTools.MAX] tools. */
    var full = false
        private set

    /** What the bar draws. */
    val shown: List<ToolbarKey> get() = preview ?: bar

    /** Reads the saved bar again (a new edit session, the settings changed it). */
    fun reload() {
        bar = read()
        preview = null
        dragged = null
        full = false
    }

    // ---- without dragging ----

    fun add(key: ToolbarKey): Result = commit(BarTools.add(bar, key), key !in bar && bar.size >= BarTools.MAX)

    fun remove(key: ToolbarKey): Result = commit(BarTools.remove(bar, key), false)

    /** Moves [key] one place to the left or right ([delta] -1 or 1). */
    fun shift(key: ToolbarKey, delta: Int): Result = commit(BarTools.shift(bar, key, delta), false)

    /** A tap on a tool of the grid: out of the bar when it is in it, else into the first free place. */
    fun toggle(key: ToolbarKey): Result = if (key in bar) remove(key) else add(key)

    // ---- dragging ----

    fun dragStart(key: ToolbarKey, fromBar: Boolean) {
        dragged = key
        draggedFromBar = fromBar
        preview = null
        full = false
    }

    /**
     * The dragged tool is over slot [slot] of what the bar shows (a place from 0): the bar shows it there, the others
     * move over. Returns false (and shows nothing) when it does not fit, i.e. a tool from the grid with a full bar.
     */
    fun dragOverBar(slot: Int): Boolean {
        val key = dragged ?: return false
        val next = placed(key, slot)
        full = next == null
        preview = next
        return next != null
    }

    /** The dragged tool left the bar: one that came from the bar is out of it (dropping on the grid removes it). */
    fun dragLeftBar() {
        val key = dragged ?: return
        preview = if (draggedFromBar) BarTools.remove(bar, key) else null
        full = false
    }

    /** The drop on the bar at [slot]: saves the new bar. */
    fun dropOnBar(slot: Int): Result {
        val key = dragged ?: return Result.UNCHANGED
        val next = placed(key, slot)
        dragEnd()
        if (next == null) { full = true; return Result.FULL }
        return commit(next, false)
    }

    /** The drop on the grid: a tool that came from the bar leaves it; one from the grid changes nothing. */
    fun dropOnGrid(): Result {
        val key = dragged ?: return Result.UNCHANGED
        val fromBar = draggedFromBar
        dragEnd()
        return if (fromBar) commit(BarTools.remove(bar, key), false) else Result.UNCHANGED
    }

    /** The drag is over, however it ended; nothing is saved here. */
    fun dragEnd() {
        dragged = null
        preview = null
    }

    private fun placed(key: ToolbarKey, slot: Int): List<ToolbarKey>? {
        if (key in bar) return BarTools.move(bar, key, slot)
        val next = BarTools.insert(bar, key, slot)
        return if (next === bar) null else next
    }

    private fun commit(next: List<ToolbarKey>, wasFull: Boolean): Result {
        full = wasFull
        if (wasFull) return Result.FULL
        if (next == bar) return Result.UNCHANGED
        bar = next
        write(next)
        return Result.CHANGED
    }
}
