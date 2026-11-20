// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.content.SharedPreferences
import helium314.keyboard.latin.R
import helium314.keyboard.settings.screens.labelResource
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings

/**
 * Stage 87b: what the cursor trackpad (stage 87) decided on the space bar and the delete key since the process started:
 * counts and enum names only, in memory (nothing stored, nothing logged, no text, no package name). Nothing happens
 * unless a finger touches one of the two keys with the trackpad able to look at it. Read by the card "Cursor-Trackpad" of the
 * diagnose page (Diagnose.trackpad), written by KeyboardActionListenerImpl.
 */
class CursorTrackCounters {
    enum class Key { SPACE, DELETE }
    /** [ACTION]: the swipe started in a direction bound to an action of its own (stage 87c): that action runs, no trackpad; a decision, not a failure. */
    enum class Decision { ACTIVE, ACTION, OFF, REFUSED, NEVER_CLAIMED }
    /** Why a touch was left to the old swipe code ([Decision.OFF]). */
    enum class OffReason { SWITCH, BLOCKED, NO_CURSOR_DIRECTION }
    /** Why a second touch was refused: another finger owns the track, or another finger runs an action of the space bar. */
    enum class RefusedWhy { OTHER_OWNER, OTHER_ACTION }

    class PerKey {
        var touches = 0
        var active = 0
        val off = IntArray(OffReason.entries.size)
        var refused = 0
        val refusedBy = IntArray(RefusedWhy.entries.size)
        var actions = 0
        /** The start direction and action of the last swipe that ran an action instead of the trackpad (enum names). */
        var lastActionGesture: SpaceGesture? = null
        var lastActionAction: SpaceAction? = null
        var collapsed = 0
        var lastCollapsed: Boolean? = null
        var neverClaimed = 0
        var steps = 0
        var verticalArrows = 0
        var suppressedArrows = 0
        var detents = 0
        var lastDecision: Decision? = null
        var lastReason: OffReason? = null
        var lastRefusedWhy: RefusedWhy? = null
        /** The field kind of the last claimed track, flags only (null: no track claimed yet). */
        var lastClassText: Boolean? = null
        var lastMultiLine: Boolean? = null
        /** The last claimed track alone. */
        var lastSteps = 0
        var lastVerticalArrows = 0
        var lastSuppressedArrows = 0
        // the delete slide
        var lifts = 0
        var liftsDeleted = 0
        var lastSelectedChars = -1
        /** The last lift happened in a protected field (password, incognito, locked device): the selection length was not read. */
        var lastLiftProtected = false
        var lastLiftDeleted: Boolean? = null

        fun copy() = PerKey().also {
            it.touches = touches; it.active = active; off.copyInto(it.off); it.refused = refused; refusedBy.copyInto(it.refusedBy); it.actions = actions; it.lastActionGesture = lastActionGesture; it.lastActionAction = lastActionAction; it.collapsed = collapsed; it.lastCollapsed = lastCollapsed; it.neverClaimed = neverClaimed
            it.steps = steps; it.verticalArrows = verticalArrows; it.suppressedArrows = suppressedArrows; it.detents = detents
            it.lastDecision = lastDecision; it.lastReason = lastReason; it.lastRefusedWhy = lastRefusedWhy
            it.lastClassText = lastClassText; it.lastMultiLine = lastMultiLine
            it.lastSteps = lastSteps; it.lastVerticalArrows = lastVerticalArrows; it.lastSuppressedArrows = lastSuppressedArrows
            it.lifts = lifts; it.liftsDeleted = liftsDeleted; it.lastSelectedChars = lastSelectedChars; it.lastLiftProtected = lastLiftProtected; it.lastLiftDeleted = lastLiftDeleted
        }
    }

    class Snapshot(val space: PerKey, val delete: PerKey)

    private var space = PerKey()
    private var delete = PerKey()
    private fun of(key: Key) = if (key == Key.SPACE) space else delete

    fun snapshot() = Snapshot(space.copy(), delete.copy())
    fun reset() { space = PerKey(); delete = PerKey() }

    /** A touch of the key reached the trackpad code. */
    fun onTouch(key: Key) { of(key).touches++ }

    fun onOff(key: Key, reason: OffReason) {
        val k = of(key)
        k.off[reason.ordinal]++
        k.lastDecision = Decision.OFF; k.lastReason = reason
    }

    fun onRefused(key: Key, why: RefusedWhy) { val k = of(key); k.refused++; k.refusedBy[why.ordinal]++; k.lastDecision = Decision.REFUSED; k.lastReason = null; k.lastRefusedWhy = why }

    /** The start direction of a space bar swipe decided for an action of its own ([action] may be NONE: the swipe is swallowed). */
    fun onAction(gesture: SpaceGesture, action: SpaceAction) {
        val k = space
        k.actions++; k.lastDecision = Decision.ACTION; k.lastReason = null
        k.lastActionGesture = gesture; k.lastActionAction = action
    }

    /** The trackpad started with a selection and collapsed it (a flag, never the selection). */
    fun onCollapsed() { space.collapsed++; space.lastCollapsed = true }
    fun onNeverClaimed(key: Key) { val k = of(key); k.neverClaimed++; k.lastDecision = Decision.NEVER_CLAIMED; k.lastReason = null }

    /** The finger became a swipe: the field kind is read from the input type (flags only). */
    fun onClaimed(key: Key, inputType: Int?) {
        val k = of(key)
        k.active++; k.lastDecision = Decision.ACTIVE; k.lastReason = null
        k.lastClassText = CursorTrackpad.isTextClass(inputType); k.lastMultiLine = CursorTrackpad.hasMultiLineFlag(inputType)
        k.lastSteps = 0; k.lastVerticalArrows = 0; k.lastSuppressedArrows = 0; k.lastCollapsed = false
    }

    fun onSteps(key: Key, count: Int) { val k = of(key); k.steps += count; k.lastSteps += count }
    fun onVerticalArrow(key: Key) { val k = of(key); k.verticalArrows++; k.lastVerticalArrows++ }
    fun onSuppressedArrows(key: Key, count: Int) { val k = of(key); k.suppressedArrows += count; k.lastSuppressedArrows += count }
    fun onDetent(key: Key) { of(key).detents++ }

    /** The finger of a delete slide lifted: [selected] characters were selected, [deleted] says whether they were deleted. */
    fun onDeleteLift(selected: Int, deleted: Boolean, protectedField: Boolean = false) {
        val k = delete
        k.lifts++; if (deleted) k.liftsDeleted++
        k.lastSelectedChars = if (protectedField) -1 else selected; k.lastLiftProtected = protectedField; k.lastLiftDeleted = deleted
    }

    companion object {
        /** The one instance of the keyboard process. */
        @JvmField val shared = CursorTrackCounters()
    }
}

/**
 * Stage 87b (on the rule of stage 87c): whether the space bar and the delete key are a trackpad according to the preferences, with
 * the reason where not. The only rule is [CursorTrackpad.spaceReason] (the listener's own function); the field, the panels and a
 * second finger are only known while typing, so they are not part of this verdict.
 */
object CursorTrackVerdict {
    enum class Why { SWITCH_OFF, NO_CURSOR_DIRECTION, DELETE_SWIPE_OFF }

    class Verdict(val active: Boolean, val why: Why? = null, val reason: CursorTrackpad.SpaceReason? = null, val gestures: SpaceGestureSettings? = null)

    fun trackpadOn(prefs: SharedPreferences) = prefs.getBoolean(AgentPreferences.CURSOR_TRACKPAD, true)

    fun space(prefs: SharedPreferences): Verdict {
        val gestures = SpaceGestures.read(prefs)
        val reason = CursorTrackpad.spaceReason(gestures, trackpadOn(prefs))
        val why = when (reason) {
            CursorTrackpad.SpaceReason.NOT_SETTING_OFF -> Why.SWITCH_OFF
            CursorTrackpad.SpaceReason.NOT_NO_CURSOR_DIRECTION -> Why.NO_CURSOR_DIRECTION
            else -> null
        }
        return Verdict(reason.qualifies, why, reason, gestures)
    }

    fun delete(prefs: SharedPreferences): Verdict = when {
        !trackpadOn(prefs) -> Verdict(false, Why.SWITCH_OFF)
        !prefs.getBoolean(Settings.PREF_DELETE_SWIPE, Defaults.PREF_DELETE_SWIPE) -> Verdict(false, Why.DELETE_SWIPE_OFF)
        else -> Verdict(true)
    }
}

/** Stage 87b: the words of the card and of the status lines (German or English by the app's language). */
object CursorTrackText {
    private fun arrow(g: SpaceGesture) = when (g) { SpaceGesture.SWIPE_LEFT -> "←"; SpaceGesture.SWIPE_RIGHT -> "→"; SpaceGesture.SWIPE_UP -> "↑"; SpaceGesture.SWIPE_DOWN -> "↓"; else -> "?" }

    private fun action(context: Context, action: SpaceAction) = context.getString(action.labelResource())

    private val directions = listOf(SpaceGesture.SWIPE_LEFT, SpaceGesture.SWIPE_RIGHT, SpaceGesture.SWIPE_UP, SpaceGesture.SWIPE_DOWN)

    /** "← oder →" */
    private fun or(context: Context, list: List<SpaceGesture>) = list.joinToString(context.getString(R.string.agent_trackdiag_or)) { arrow(it) }

    /**
     * What a swipe does by its start direction, in one sentence: the directions that move the cursor freely (bound to "Cursor bewegen" or to
     * nothing, as [CursorTrackpad.decideStart] takes them) and the ones that run their own action.
     */
    private fun startDecides(context: Context, gestures: SpaceGestureSettings): String {
        val free = directions.filter { CursorTrackpad.isFree(gestures, it) }
        val own = directions.filterNot { CursorTrackpad.isFree(gestures, it) }.groupBy { gestures[it] }
        val parts = own.entries.mapIndexed { i, (action, list) ->
            context.getString(if (i == 0) R.string.agent_trackdiag_start_first else R.string.agent_trackdiag_start_next, or(context, list), action(context, action))
        }
        // up and down never move a cursor in a field that is not multi-line (there "nothing" even swallows the swipe, see decideStart): say so
        val vertical = free.filter { !it.isHorizontal }
        val note = if (vertical.isEmpty()) "" else context.getString(R.string.agent_trackdiag_multiline_only, or(context, vertical))
        return context.getString(R.string.agent_trackdiag_start_decides, or(context, free), note + parts.joinToString(""))
    }

    /** The part after "Leertaste: aktiv – " and "nicht aktiv – ": what the space bar does now, from the settings. */
    private fun spaceDetail(context: Context, verdict: CursorTrackVerdict.Verdict): String = when (verdict.reason) {
        CursorTrackpad.SpaceReason.QUALIFIES_ALL_AXES -> context.getString(R.string.agent_trackdiag_detail_all)
        CursorTrackpad.SpaceReason.QUALIFIES_START_DECIDES -> startDecides(context, verdict.gestures ?: SpaceGestureSettings(emptyMap()))
        CursorTrackpad.SpaceReason.NOT_NO_CURSOR_DIRECTION -> context.getString(R.string.agent_trackdiag_detail_none)
        else -> context.getString(R.string.agent_trackdiag_off_both)
    }

    /** The one statement under the switch "Cursor als Trackpad": whether the space bar qualifies and what each start direction does. */
    fun spaceLine(context: Context, verdict: CursorTrackVerdict.Verdict): String =
        if (verdict.why == CursorTrackVerdict.Why.SWITCH_OFF) context.getString(R.string.agent_trackdiag_off_both) else context.getString(if (verdict.active) R.string.agent_trackdiag_line_space_active else R.string.agent_trackdiag_line_space_inactive, spaceDetail(context, verdict))

    /** The value of the card's row for the space bar. */
    fun spaceNow(context: Context, verdict: CursorTrackVerdict.Verdict): String =
        if (verdict.why == CursorTrackVerdict.Why.SWITCH_OFF) context.getString(R.string.agent_trackdiag_now_switch_off) else context.getString(if (verdict.active) R.string.agent_trackdiag_now_active_detail else R.string.agent_trackdiag_now_inactive_detail, spaceDetail(context, verdict))

    /** The line for the delete key. */
    fun deleteLine(context: Context, verdict: CursorTrackVerdict.Verdict): String = context.getString(when {
        verdict.active -> R.string.agent_trackdiag_line_delete_active
        verdict.why == CursorTrackVerdict.Why.SWITCH_OFF -> R.string.agent_trackdiag_off_both
        else -> R.string.agent_trackdiag_line_delete_swipe
    })

    /** "Trackpad aktiv" or "nicht aktiv, weil ..." for the delete key on the card. */
    fun deleteNow(context: Context, verdict: CursorTrackVerdict.Verdict): String {
        if (verdict.active) return context.getString(R.string.agent_trackdiag_now_active)
        if (verdict.why == CursorTrackVerdict.Why.SWITCH_OFF) return context.getString(R.string.agent_trackdiag_now_switch_off)
        return context.getString(R.string.agent_trackdiag_now_inactive, context.getString(R.string.agent_trackdiag_why_delete_swipe))
    }

    fun pair(context: Context, prefs: SharedPreferences, first: SpaceGesture, second: SpaceGesture): String {
        val gestures = SpaceGestures.read(prefs)
        return context.getString(R.string.agent_trackdiag_pair, arrow(first), action(context, gestures[first]), arrow(second), action(context, gestures[second]))
    }

    private fun yesNo(context: Context, value: Boolean?) =
        context.getString(if (value == true) R.string.agent_diagnose_yes else R.string.agent_diagnose_no)

    fun decisions(context: Context, k: CursorTrackCounters.PerKey, forSpace: Boolean): String {
        val off = k.off
        fun o(r: CursorTrackCounters.OffReason) = off[r.ordinal]
        val offTotal = off.sum()
        return if (forSpace) context.getString(R.string.agent_trackdiag_decisions, k.active, k.actions, offTotal, o(CursorTrackCounters.OffReason.SWITCH),
            o(CursorTrackCounters.OffReason.BLOCKED), o(CursorTrackCounters.OffReason.NO_CURSOR_DIRECTION), k.refused,
            k.refusedBy[CursorTrackCounters.RefusedWhy.OTHER_OWNER.ordinal], k.refusedBy[CursorTrackCounters.RefusedWhy.OTHER_ACTION.ordinal], k.neverClaimed)
        else context.getString(R.string.agent_trackdiag_decisions_delete, k.active, offTotal, o(CursorTrackCounters.OffReason.SWITCH),
            o(CursorTrackCounters.OffReason.BLOCKED), k.refused, k.neverClaimed)
    }

    /** The action the last swipe ran instead of the trackpad, as enum names ("SWIPE_UP = UNDO"). */
    fun lastAction(context: Context, k: CursorTrackCounters.PerKey): String =
        if (k.lastActionGesture == null) context.getString(R.string.agent_trackdiag_action_none)
        else context.getString(R.string.agent_trackdiag_action_value, arrow(k.lastActionGesture!!), action(context, k.lastActionAction ?: SpaceAction.NONE),
            context.getString(R.string.agent_trackdiag_blocker, k.lastActionGesture!!.name, k.lastActionAction?.name ?: "?"))

    fun collapsed(context: Context, k: CursorTrackCounters.PerKey): String =
        if (k.collapsed == 0) context.getString(R.string.agent_trackdiag_collapsed_none)
        else if (k.collapsed == 1) context.getString(R.string.agent_trackdiag_collapsed_once, yesNo(context, k.lastCollapsed))
        else context.getString(R.string.agent_trackdiag_collapsed, k.collapsed, yesNo(context, k.lastCollapsed))

    fun field(context: Context, k: CursorTrackCounters.PerKey): String =
        if (k.lastClassText == null) context.getString(R.string.agent_trackdiag_field_none)
        else context.getString(R.string.agent_trackdiag_field, yesNo(context, k.lastClassText), yesNo(context, k.lastMultiLine))

    /** The last decision in plain words ("Trackpad, 14 Schritte, senkrecht nicht möglich, weil ..."). */
    fun last(context: Context, k: CursorTrackCounters.PerKey, space: Boolean): String {
        val decision = k.lastDecision ?: return context.getString(R.string.agent_trackdiag_last_none)
        return when (decision) {
            CursorTrackCounters.Decision.ACTIVE -> buildString {
                append(context.getString(R.string.agent_trackdiag_last_active, k.lastSteps))
                if (space) {
                    if (k.lastMultiLine != true || k.lastClassText != true) append(context.getString(R.string.agent_trackdiag_last_single_line))
                    else if (k.lastVerticalArrows > 0) append(context.getString(R.string.agent_trackdiag_last_vertical, k.lastVerticalArrows))
                    if (k.lastCollapsed == true) append(context.getString(R.string.agent_trackdiag_last_collapsed))
                } else if (k.lastLiftDeleted != null) {
                    append(context.getString(if (k.lastLiftProtected) R.string.agent_trackdiag_last_lift_protected else R.string.agent_trackdiag_last_lift, k.lastSelectedChars,
                        context.getString(if (k.lastLiftDeleted == true) R.string.agent_trackdiag_last_deleted else R.string.agent_trackdiag_last_kept)))
                }
            }
            CursorTrackCounters.Decision.ACTION -> {
                val gesture = k.lastActionGesture ?: SpaceGesture.SWIPE_LEFT
                val act = k.lastActionAction ?: SpaceAction.NONE
                val names = context.getString(R.string.agent_trackdiag_blocker, gesture.name, act.name)
                if (act == SpaceAction.NONE) context.getString(R.string.agent_trackdiag_last_nothing, arrow(gesture), names)
                else context.getString(R.string.agent_trackdiag_last_action, arrow(gesture), action(context, act), names)
            }
            CursorTrackCounters.Decision.OFF -> context.getString(R.string.agent_trackdiag_last_off, when (k.lastReason) {
                CursorTrackCounters.OffReason.BLOCKED -> context.getString(R.string.agent_trackdiag_off_blocked)
                CursorTrackCounters.OffReason.NO_CURSOR_DIRECTION -> context.getString(R.string.agent_trackdiag_off_no_direction)
                else -> context.getString(R.string.agent_trackdiag_off_switch)
            })
            CursorTrackCounters.Decision.REFUSED -> context.getString(
                if (k.lastRefusedWhy == CursorTrackCounters.RefusedWhy.OTHER_ACTION) R.string.agent_trackdiag_last_refused_action else R.string.agent_trackdiag_last_refused)
            CursorTrackCounters.Decision.NEVER_CLAIMED -> context.getString(R.string.agent_trackdiag_last_never)
        }
    }
}
