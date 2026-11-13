// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.SharedPreferences
import helium314.keyboard.keyboard.KeyboardActionListener.SwipeAction
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings

/** The gestures of the space bar that can be given an action. A single tap always types a space. */
enum class SpaceGesture(@JvmField val key: String) {
    SWIPE_LEFT("agent_space_swipe_left"),
    SWIPE_RIGHT("agent_space_swipe_right"),
    SWIPE_UP("agent_space_swipe_up"),
    SWIPE_DOWN("agent_space_swipe_down"),
    HOLD("agent_space_hold"),
    DOUBLE_TAP("agent_space_double_tap");

    val isSwipe: Boolean get() = this != HOLD && this != DOUBLE_TAP
    val isHorizontal: Boolean get() = this == SWIPE_LEFT || this == SWIPE_RIGHT
}

/** Actions a space bar gesture can run. Each one goes through the existing listener paths. */
enum class SpaceAction {
    NONE, MOVE_CURSOR, SWITCH_LANGUAGE, SWITCH_INPUT_METHOD, ACTION_WHEEL, ACTION_PANEL, DICTATION,
    CLIPBOARD, EMOJI, COMMAND_LIST, UNDO, REDO, SELECT_ALL, TOGGLE_NUMPAD, TOGGLE_SYMBOLS, TOGGLE_DPAD,
    TOUCHPAD_MODE, HIDE_KEYBOARD;

    /**
     * A continuous action receives every step of its swipe (as HeliBoard's cursor movement, language
     * slide and touchpad mode always did); every other action runs once per swipe, at the first step.
     */
    val isContinuous: Boolean get() = this == MOVE_CURSOR || this == SWITCH_LANGUAGE || this == TOUCHPAD_MODE
}

/** The resolved action of every gesture; read once per [helium314.keyboard.latin.settings.SettingsValues]. */
class SpaceGestureSettings internal constructor(private val actions: Map<SpaceGesture, SpaceAction>) {
    operator fun get(gesture: SpaceGesture): SpaceAction = actions[gesture] ?: SpaceAction.NONE

    /** Whether the space bar starts key swipes at all (PointerTracker.isSwiper). */
    fun hasSwipe(): Boolean = SpaceGesture.entries.any { it.isSwipe && get(it) != SpaceAction.NONE }
}

/**
 * Per-gesture settings of the space bar. A gesture without its own value follows the settings that
 * existed before: HeliBoard's horizontal and vertical space swipe, "long press space for the input
 * method picker" and the opt-in wheel ([AgentPreferences.SPACE_LONG_PRESS]). Nothing is migrated
 * by writing, so existing users and saved profiles keep their behavior until they choose an action.
 */
object SpaceGestures {
    /** Profile value for "no own value": the key is removed and the gesture follows the older settings. */
    const val INHERIT = "INHERIT"

    @JvmStatic val keys: Set<String> = SpaceGesture.entries.map { it.key }.toSet()

    private val horizontal = listOf(
        SpaceAction.MOVE_CURSOR, SpaceAction.SWITCH_LANGUAGE, SpaceAction.SWITCH_INPUT_METHOD, SpaceAction.ACTION_WHEEL,
        SpaceAction.ACTION_PANEL, SpaceAction.DICTATION, SpaceAction.CLIPBOARD, SpaceAction.EMOJI, SpaceAction.COMMAND_LIST,
        SpaceAction.UNDO, SpaceAction.REDO, SpaceAction.SELECT_ALL, SpaceAction.TOGGLE_NUMPAD, SpaceAction.TOGGLE_SYMBOLS,
        SpaceAction.TOGGLE_DPAD, SpaceAction.HIDE_KEYBOARD, SpaceAction.NONE,
    )

    /** The actions offered for [gesture], in display order. Touchpad mode stays vertical as in HeliBoard. */
    @JvmStatic fun choices(gesture: SpaceGesture): List<SpaceAction> = when {
        gesture.isHorizontal -> horizontal
        gesture.isSwipe -> horizontal - SpaceAction.NONE + SpaceAction.TOUCHPAD_MODE + SpaceAction.NONE
        else -> horizontal - SpaceAction.MOVE_CURSOR
    }

    @JvmStatic fun read(prefs: SharedPreferences) = SpaceGestureSettings(SpaceGesture.entries.associateWith { resolve(prefs, it) })

    @JvmStatic fun resolve(prefs: SharedPreferences, gesture: SpaceGesture): SpaceAction = stored(prefs, gesture) ?: inherited(prefs, gesture)

    /** The gesture's own value, or null when it follows the older settings (also for unknown values). */
    @JvmStatic fun stored(prefs: SharedPreferences, gesture: SpaceGesture): SpaceAction? {
        val value = prefs.getString(gesture.key, null) ?: return null
        return SpaceAction.entries.firstOrNull { it.name == value }?.takeIf { it in choices(gesture) }
    }

    /** What the gesture did before it had its own setting. */
    @JvmStatic fun inherited(prefs: SharedPreferences, gesture: SpaceGesture): SpaceAction = when (gesture) {
        // HeliBoard ignored hide and touchpad mode for horizontal swipes
        SpaceGesture.SWIPE_LEFT, SpaceGesture.SWIPE_RIGHT -> fromSwipeAction(Settings.readHorizontalSpaceSwipe(prefs))
            .takeUnless { it == SpaceAction.HIDE_KEYBOARD || it == SpaceAction.TOUCHPAD_MODE } ?: SpaceAction.NONE
        SpaceGesture.SWIPE_UP, SpaceGesture.SWIPE_DOWN -> fromSwipeAction(Settings.readVerticalSpaceSwipe(prefs))
        SpaceGesture.HOLD -> when {
            prefs.getBoolean(AgentPreferences.SPACE_LONG_PRESS, false) -> SpaceAction.ACTION_WHEEL
            prefs.getBoolean(Settings.PREF_SPACE_TO_CHANGE_LANG, Defaults.PREF_SPACE_TO_CHANGE_LANG) -> SpaceAction.SWITCH_INPUT_METHOD
            else -> SpaceAction.NONE
        }
        SpaceGesture.DOUBLE_TAP -> SpaceAction.NONE
    }

    /**
     * Whether holding space falls back to the input method picker (SettingsValues.mSpaceForLangChange,
     * also the "…" hint on the space bar). With its own hold action only for [SpaceAction.SWITCH_INPUT_METHOD];
     * without, HeliBoard's setting as before, which also stays the fallback when the opt-in wheel cannot open.
     */
    @JvmStatic fun holdShowsPicker(prefs: SharedPreferences): Boolean =
        stored(prefs, SpaceGesture.HOLD)?.let { it == SpaceAction.SWITCH_INPUT_METHOD }
            ?: prefs.getBoolean(Settings.PREF_SPACE_TO_CHANGE_LANG, Defaults.PREF_SPACE_TO_CHANGE_LANG)

    /** A double tap with an action replaces "double space types a period": both never run together. */
    @JvmStatic fun doubleTapAssigned(prefs: SharedPreferences): Boolean = resolve(prefs, SpaceGesture.DOUBLE_TAP) != SpaceAction.NONE

    fun write(prefs: SharedPreferences, gesture: SpaceGesture, action: SpaceAction?) {
        require(action == null || action in choices(gesture))
        val edit = prefs.edit()
        if (action == null) edit.remove(gesture.key) else edit.putString(gesture.key, action.name)
        check(edit.commit())
    }

    private fun fromSwipeAction(action: SwipeAction): SpaceAction = when (action) {
        SwipeAction.NONE -> SpaceAction.NONE
        SwipeAction.MOVE_CURSOR -> SpaceAction.MOVE_CURSOR
        SwipeAction.SWITCH_LANGUAGE -> SpaceAction.SWITCH_LANGUAGE
        SwipeAction.TOGGLE_NUMPAD -> SpaceAction.TOGGLE_NUMPAD
        SwipeAction.TOGGLE_DPAD -> SpaceAction.TOGGLE_DPAD
        SwipeAction.HIDE_KEYBOARD -> SpaceAction.HIDE_KEYBOARD
        SwipeAction.TOUCHPAD_MODE -> SpaceAction.TOUCHPAD_MODE
    }
}
