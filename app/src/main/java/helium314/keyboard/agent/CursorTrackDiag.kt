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
    enum class Decision { ACTIVE, OFF, REFUSED, NEVER_CLAIMED }
    /** Why a touch was left to the old swipe code ([Decision.OFF]). */
    enum class OffReason { SWITCH, BLOCKED, BINDINGS, SELECTION }

    class PerKey {
        var touches = 0
        var active = 0
        val off = IntArray(OffReason.entries.size)
        var refused = 0
        var neverClaimed = 0
        var steps = 0
        var verticalArrows = 0
        var suppressedArrows = 0
        var detents = 0
        var lastDecision: Decision? = null
        var lastReason: OffReason? = null
        /** The swipe that disqualified the space bar the last time, with its action (enum names). */
        var lastBlockerGesture: SpaceGesture? = null
        var lastBlockerAction: SpaceAction? = null
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
        var lastLiftDeleted: Boolean? = null

        fun copy() = PerKey().also {
            it.touches = touches; it.active = active; off.copyInto(it.off); it.refused = refused; it.neverClaimed = neverClaimed
            it.steps = steps; it.verticalArrows = verticalArrows; it.suppressedArrows = suppressedArrows; it.detents = detents
            it.lastDecision = lastDecision; it.lastReason = lastReason; it.lastBlockerGesture = lastBlockerGesture; it.lastBlockerAction = lastBlockerAction
            it.lastClassText = lastClassText; it.lastMultiLine = lastMultiLine
            it.lastSteps = lastSteps; it.lastVerticalArrows = lastVerticalArrows; it.lastSuppressedArrows = lastSuppressedArrows
            it.lifts = lifts; it.liftsDeleted = liftsDeleted; it.lastSelectedChars = lastSelectedChars; it.lastLiftDeleted = lastLiftDeleted
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

    fun onOff(key: Key, reason: OffReason, blocker: Pair<SpaceGesture, SpaceAction>? = null) {
        val k = of(key)
        k.off[reason.ordinal]++
        k.lastDecision = Decision.OFF; k.lastReason = reason
        if (blocker != null) { k.lastBlockerGesture = blocker.first; k.lastBlockerAction = blocker.second }
    }

    fun onRefused(key: Key) { val k = of(key); k.refused++; k.lastDecision = Decision.REFUSED; k.lastReason = null }
    fun onNeverClaimed(key: Key) { val k = of(key); k.neverClaimed++; k.lastDecision = Decision.NEVER_CLAIMED; k.lastReason = null }

    /** The finger became a swipe: the field kind is read from the input type (flags only). */
    fun onClaimed(key: Key, inputType: Int?) {
        val k = of(key)
        k.active++; k.lastDecision = Decision.ACTIVE; k.lastReason = null
        k.lastClassText = CursorTrackpad.isTextClass(inputType); k.lastMultiLine = CursorTrackpad.hasMultiLineFlag(inputType)
        k.lastSteps = 0; k.lastVerticalArrows = 0; k.lastSuppressedArrows = 0
    }

    fun onSteps(key: Key, count: Int) { val k = of(key); k.steps += count; k.lastSteps += count }
    fun onVerticalArrow(key: Key) { val k = of(key); k.verticalArrows++; k.lastVerticalArrows++ }
    fun onSuppressedArrows(key: Key, count: Int) { val k = of(key); k.suppressedArrows += count; k.lastSuppressedArrows += count }
    fun onDetent(key: Key) { of(key).detents++ }

    /** The finger of a delete slide lifted: [selected] characters were selected, [deleted] says whether they were deleted. */
    fun onDeleteLift(selected: Int, deleted: Boolean) {
        val k = delete
        k.lifts++; if (deleted) k.liftsDeleted++
        k.lastSelectedChars = selected; k.lastLiftDeleted = deleted
    }

    companion object {
        /** The one instance of the keyboard process. */
        @JvmField val shared = CursorTrackCounters()
    }
}

/**
 * Stage 87b: whether the space bar and the delete key qualify for the trackpad according to the preferences, with the reason
 * where not. The rules are those of [CursorTrackpad] (the listener's own functions); the field and the panels are only known
 * while typing, so they are not part of this verdict.
 */
object CursorTrackVerdict {
    enum class Why { SWITCH_OFF, BINDING, DELETE_SWIPE_OFF }

    class Verdict(val active: Boolean, val why: Why? = null, val gesture: SpaceGesture? = null, val action: SpaceAction? = null)

    fun trackpadOn(prefs: SharedPreferences) = prefs.getBoolean(AgentPreferences.CURSOR_TRACKPAD, true)

    fun space(prefs: SharedPreferences): Verdict {
        if (!trackpadOn(prefs)) return Verdict(false, Why.SWITCH_OFF)
        val blocker = CursorTrackpad.spaceBlocker(SpaceGestures.read(prefs)) ?: return Verdict(true)
        return Verdict(false, Why.BINDING, blocker.first, blocker.second)
    }

    fun delete(prefs: SharedPreferences): Verdict = when {
        !trackpadOn(prefs) -> Verdict(false, Why.SWITCH_OFF)
        !prefs.getBoolean(Settings.PREF_DELETE_SWIPE, Defaults.PREF_DELETE_SWIPE) -> Verdict(false, Why.DELETE_SWIPE_OFF)
        else -> Verdict(true)
    }
}

/** Stage 87b: the words of the card and of the status lines (German or English by the app's language). */
object CursorTrackText {
    private fun gestureLabel(context: Context, gesture: SpaceGesture) = context.getString(when (gesture) {
        SpaceGesture.SWIPE_LEFT -> R.string.agent_gestures_key_left
        SpaceGesture.SWIPE_RIGHT -> R.string.agent_gestures_key_right
        SpaceGesture.SWIPE_UP -> R.string.agent_gestures_key_up
        SpaceGesture.SWIPE_DOWN -> R.string.agent_gestures_key_down
        SpaceGesture.HOLD -> R.string.agent_gestures_key_hold
        SpaceGesture.DOUBLE_TAP -> R.string.agent_gestures_key_double_tap
    })

    private fun action(context: Context, action: SpaceAction) = context.getString(action.labelResource())

    /** The line under the switch "Cursor als Trackpad": whether the space bar qualifies and, if not, why and what to change. */
    fun spaceLine(context: Context, verdict: CursorTrackVerdict.Verdict): String = when {
        verdict.active -> context.getString(R.string.agent_trackdiag_line_space_active)
        verdict.why == CursorTrackVerdict.Why.SWITCH_OFF -> context.getString(R.string.agent_trackdiag_line_space_switch)
        else -> {
            val gesture = verdict.gesture ?: SpaceGesture.SWIPE_LEFT
            val bound = action(context, verdict.action ?: SpaceAction.NONE)
            if (gesture.isHorizontal) context.getString(R.string.agent_trackdiag_line_space_horizontal,
                gestureLabel(context, gesture), bound, action(context, SpaceAction.MOVE_CURSOR))
            else context.getString(R.string.agent_trackdiag_line_space_vertical,
                gestureLabel(context, gesture), bound, action(context, SpaceAction.NONE), action(context, SpaceAction.MOVE_CURSOR))
        }
    }

    /** The line for the delete key. */
    fun deleteLine(context: Context, verdict: CursorTrackVerdict.Verdict): String = context.getString(when {
        verdict.active -> R.string.agent_trackdiag_line_delete_active
        verdict.why == CursorTrackVerdict.Why.SWITCH_OFF -> R.string.agent_trackdiag_line_delete_switch
        else -> R.string.agent_trackdiag_line_delete_swipe
    })

    /** "Trackpad aktiv" or "nicht aktiv, weil ..." for the card. */
    fun now(context: Context, verdict: CursorTrackVerdict.Verdict): String {
        if (verdict.active) return context.getString(R.string.agent_trackdiag_now_active)
        val why = when (verdict.why) {
            CursorTrackVerdict.Why.SWITCH_OFF -> context.getString(R.string.agent_trackdiag_why_switch)
            CursorTrackVerdict.Why.DELETE_SWIPE_OFF -> context.getString(R.string.agent_trackdiag_why_delete_swipe)
            else -> context.getString(R.string.agent_trackdiag_why_binding, gestureLabel(context, verdict.gesture ?: SpaceGesture.SWIPE_LEFT),
                action(context, verdict.action ?: SpaceAction.NONE), action(context, SpaceAction.MOVE_CURSOR), action(context, SpaceAction.NONE))
        }
        return context.getString(R.string.agent_trackdiag_now_inactive, why)
    }

    fun pair(context: Context, prefs: SharedPreferences, first: SpaceGesture, second: SpaceGesture): String {
        val gestures = SpaceGestures.read(prefs)
        fun arrow(g: SpaceGesture) = when (g) { SpaceGesture.SWIPE_LEFT -> "←"; SpaceGesture.SWIPE_RIGHT -> "→"; SpaceGesture.SWIPE_UP -> "↑"; else -> "↓" }
        return context.getString(R.string.agent_trackdiag_pair, arrow(first), action(context, gestures[first]), arrow(second), action(context, gestures[second]))
    }

    private fun yesNo(context: Context, value: Boolean?) =
        context.getString(if (value == true) R.string.agent_diagnose_yes else R.string.agent_diagnose_no)

    fun decisions(context: Context, k: CursorTrackCounters.PerKey, withBindings: Boolean): String {
        val off = k.off
        val offTotal = off.sum()
        fun o(r: CursorTrackCounters.OffReason) = off[r.ordinal]
        return if (withBindings) context.getString(R.string.agent_trackdiag_decisions, k.active, offTotal, o(CursorTrackCounters.OffReason.SWITCH),
            o(CursorTrackCounters.OffReason.BLOCKED), o(CursorTrackCounters.OffReason.BINDINGS), o(CursorTrackCounters.OffReason.SELECTION), k.refused, k.neverClaimed)
        else context.getString(R.string.agent_trackdiag_decisions_delete, k.active, offTotal, o(CursorTrackCounters.OffReason.SWITCH),
            o(CursorTrackCounters.OffReason.BLOCKED), k.refused, k.neverClaimed)
    }

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
                } else if (k.lastLiftDeleted != null) {
                    append(context.getString(R.string.agent_trackdiag_last_lift, k.lastSelectedChars,
                        context.getString(if (k.lastLiftDeleted == true) R.string.agent_trackdiag_last_deleted else R.string.agent_trackdiag_last_kept)))
                }
            }
            CursorTrackCounters.Decision.OFF -> context.getString(R.string.agent_trackdiag_last_off, when (k.lastReason) {
                CursorTrackCounters.OffReason.BLOCKED -> context.getString(R.string.agent_trackdiag_off_blocked)
                CursorTrackCounters.OffReason.BINDINGS -> context.getString(R.string.agent_trackdiag_off_bindings,
                    context.getString(R.string.agent_trackdiag_blocker, k.lastBlockerGesture?.name ?: "?", k.lastBlockerAction?.name ?: "?"))
                CursorTrackCounters.OffReason.SELECTION -> context.getString(R.string.agent_trackdiag_off_selection)
                else -> context.getString(R.string.agent_trackdiag_off_switch)
            })
            CursorTrackCounters.Decision.REFUSED -> context.getString(R.string.agent_trackdiag_last_refused)
            CursorTrackCounters.Decision.NEVER_CLAIMED -> context.getString(R.string.agent_trackdiag_last_never)
        }
    }
}
