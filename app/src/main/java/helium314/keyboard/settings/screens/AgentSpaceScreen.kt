// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import helium314.keyboard.agent.SpaceAction
import helium314.keyboard.agent.SpaceGesture
import helium314.keyboard.agent.SpaceGestures
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.NextScreenIcon
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.preferences.ListPreference
import helium314.keyboard.settings.preferences.Preference

/** Settings key of the row that opens [AgentSpaceScreen] (in "Advanced" and next to the profiles). */
const val AGENT_SPACE_ENTRY = "agent_space_gestures_entry"

/** "Space bar": one row per gesture; "Default" removes the own value, so the gesture follows the older settings. */
@Composable
fun AgentSpaceScreen(onClickBack: () -> Unit) {
    val prefs = LocalContext.current.prefs()
    val b = (LocalContext.current.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val gestures = SpaceGestures.read(prefs)
    val swipes = SpaceGesture.entries.filter { it.isSwipe }.map { gestures[it] }
    val items = SpaceGesture.entries.map { it.key } + listOfNotNull(
        if (SpaceAction.SWITCH_LANGUAGE in swipes) Settings.PREF_LANGUAGE_SWIPE_DISTANCE else null,
        if (SpaceAction.TOUCHPAD_MODE in swipes) Settings.PREF_TOUCHPAD_SENSITIVITY else null,
        if (SpaceAction.TOUCHPAD_MODE in swipes) Settings.PREF_TOUCHPAD_EDGE_SCROLL else null,
    )
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.agent_space_title),
        settings = items,
    )
}

fun createAgentSpaceSettings(context: Context) = SpaceGesture.entries.map { gesture ->
    Setting(context, gesture.key, gesture.titleResource(),
        if (gesture == SpaceGesture.DOUBLE_TAP) R.string.agent_space_double_tap_description else null) { setting ->
        val prefs = LocalContext.current.prefs()
        val items = SpaceGestures.choices(gesture).map { stringResource(it.labelResource()) to it.name }
        ListPreference(setting, items, SpaceGestures.inherited(prefs, gesture).name,
            onDefault = { SpaceGestures.write(prefs, gesture, null) })
    }
} + Setting(context, AGENT_SPACE_ENTRY, R.string.agent_space_title, R.string.agent_space_summary) {
    Preference(
        name = it.title,
        description = it.description,
        onClick = { SettingsDestination.navigateTo(SettingsDestination.AgentSpace) }
    ) { NextScreenIcon() }
}

internal fun SpaceGesture.titleResource() = when (this) {
    SpaceGesture.SWIPE_LEFT -> R.string.agent_space_swipe_left
    SpaceGesture.SWIPE_RIGHT -> R.string.agent_space_swipe_right
    SpaceGesture.SWIPE_UP -> R.string.agent_space_swipe_up
    SpaceGesture.SWIPE_DOWN -> R.string.agent_space_swipe_down
    SpaceGesture.HOLD -> R.string.agent_space_hold
    SpaceGesture.DOUBLE_TAP -> R.string.agent_space_double_tap
}

internal fun SpaceAction.labelResource() = when (this) {
    SpaceAction.NONE -> R.string.agent_space_action_none
    SpaceAction.MOVE_CURSOR -> R.string.agent_space_action_move_cursor
    SpaceAction.SWITCH_LANGUAGE -> R.string.agent_space_action_switch_language
    SpaceAction.SWITCH_INPUT_METHOD -> R.string.agent_space_action_switch_input_method
    SpaceAction.ACTION_WHEEL -> R.string.agent_space_action_wheel
    SpaceAction.ACTION_PANEL -> R.string.agent_space_action_panel
    SpaceAction.DICTATION -> R.string.agent_space_action_dictation
    SpaceAction.CLIPBOARD -> R.string.agent_space_action_clipboard
    SpaceAction.EMOJI -> R.string.agent_space_action_emoji
    SpaceAction.COMMAND_LIST -> R.string.agent_space_action_command_list
    SpaceAction.UNDO -> R.string.agent_space_action_undo
    SpaceAction.REDO -> R.string.agent_space_action_redo
    SpaceAction.SELECT_ALL -> R.string.agent_space_action_select_all
    SpaceAction.TOGGLE_NUMPAD -> R.string.agent_space_action_numpad
    SpaceAction.TOGGLE_SYMBOLS -> R.string.agent_space_action_symbols
    SpaceAction.TOGGLE_DPAD -> R.string.agent_space_action_dpad
    SpaceAction.TOUCHPAD_MODE -> R.string.agent_space_action_touchpad
    SpaceAction.HIDE_KEYBOARD -> R.string.agent_space_action_hide
}
