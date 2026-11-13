// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.content.Context
import dev.agentkeyboard.core.PreferenceValue
import dev.agentkeyboard.core.ProfileGroup
import helium314.keyboard.keyboard.KeyboardActionListener.SwipeAction
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*

/** Per-gesture space bar settings: defaults equal the older settings, nothing is rewritten. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SpaceGesturesTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val prefs get() = app.prefs()
    private fun resolve(gesture: SpaceGesture) = SpaceGestures.resolve(prefs, gesture)

    @Before fun clear() {
        prefs.edit().clear().commit()
        app.getSharedPreferences(KeyboardProfileStore.STORAGE_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun defaultsEqualTheBehaviorBeforePerGestureSettings() {
        assertEquals(SpaceAction.MOVE_CURSOR, resolve(SpaceGesture.SWIPE_LEFT))
        assertEquals(SpaceAction.MOVE_CURSOR, resolve(SpaceGesture.SWIPE_RIGHT))
        assertEquals(SpaceAction.NONE, resolve(SpaceGesture.SWIPE_UP))
        assertEquals(SpaceAction.NONE, resolve(SpaceGesture.SWIPE_DOWN))
        assertEquals(SpaceAction.SWITCH_INPUT_METHOD, resolve(SpaceGesture.HOLD))
        assertEquals(SpaceAction.NONE, resolve(SpaceGesture.DOUBLE_TAP))
        assertTrue(SpaceGestures.holdShowsPicker(prefs))
        assertFalse(SpaceGestures.doubleTapAssigned(prefs))
        assertTrue(SpaceGestures.read(prefs).hasSwipe())
        assertTrue(prefs.all.isEmpty()) // reading writes nothing
    }

    @Test fun existingHeliBoardSettingsMapOntoTheGestures() {
        prefs.edit().putString(Settings.PREF_SPACE_HORIZONTAL_SWIPE, SwipeAction.SWITCH_LANGUAGE.name)
            .putString(Settings.PREF_SPACE_VERTICAL_SWIPE, SwipeAction.TOUCHPAD_MODE.name)
            .putBoolean(Settings.PREF_SPACE_TO_CHANGE_LANG, false).commit()
        assertEquals(SpaceAction.SWITCH_LANGUAGE, resolve(SpaceGesture.SWIPE_LEFT))
        assertEquals(SpaceAction.SWITCH_LANGUAGE, resolve(SpaceGesture.SWIPE_RIGHT))
        assertEquals(SpaceAction.TOUCHPAD_MODE, resolve(SpaceGesture.SWIPE_UP))
        assertEquals(SpaceAction.TOUCHPAD_MODE, resolve(SpaceGesture.SWIPE_DOWN))
        assertEquals(SpaceAction.NONE, resolve(SpaceGesture.HOLD))
        assertFalse(SpaceGestures.holdShowsPicker(prefs))

        prefs.edit().putString(Settings.PREF_SPACE_HORIZONTAL_SWIPE, SwipeAction.NONE.name)
            .putString(Settings.PREF_SPACE_VERTICAL_SWIPE, SwipeAction.NONE.name).commit()
        assertFalse(SpaceGestures.read(prefs).hasSwipe())
        // HeliBoard never ran hide or touchpad on horizontal swipes
        prefs.edit().putString(Settings.PREF_SPACE_HORIZONTAL_SWIPE, SwipeAction.HIDE_KEYBOARD.name).commit()
        assertEquals(SpaceAction.NONE, resolve(SpaceGesture.SWIPE_LEFT))
    }

    @Test fun theOptInWheelBecomesTheHoldActionAndKeepsThePickerFallback() {
        prefs.edit().putBoolean(AgentPreferences.SPACE_LONG_PRESS, true).commit()
        assertEquals(SpaceAction.ACTION_WHEEL, resolve(SpaceGesture.HOLD))
        assertTrue(SpaceGestures.holdShowsPicker(prefs)) // as before when the wheel cannot open
        prefs.edit().putBoolean(Settings.PREF_SPACE_TO_CHANGE_LANG, false).commit()
        assertEquals(SpaceAction.ACTION_WHEEL, resolve(SpaceGesture.HOLD))
        assertFalse(SpaceGestures.holdShowsPicker(prefs))
    }

    @Test fun ownValuesWinAndDefaultFollowsTheOlderSettingsAgain() {
        prefs.edit().putBoolean(AgentPreferences.SPACE_LONG_PRESS, true).commit()
        SpaceGestures.write(prefs, SpaceGesture.SWIPE_LEFT, SpaceAction.UNDO)
        SpaceGestures.write(prefs, SpaceGesture.SWIPE_UP, SpaceAction.ACTION_PANEL)
        SpaceGestures.write(prefs, SpaceGesture.HOLD, SpaceAction.EMOJI)
        SpaceGestures.write(prefs, SpaceGesture.DOUBLE_TAP, SpaceAction.COMMAND_LIST)
        val gestures = SpaceGestures.read(prefs)
        assertEquals(SpaceAction.UNDO, gestures[SpaceGesture.SWIPE_LEFT])
        assertEquals(SpaceAction.MOVE_CURSOR, gestures[SpaceGesture.SWIPE_RIGHT])
        assertEquals(SpaceAction.ACTION_PANEL, gestures[SpaceGesture.SWIPE_UP])
        assertEquals(SpaceAction.NONE, gestures[SpaceGesture.SWIPE_DOWN])
        assertEquals(SpaceAction.EMOJI, gestures[SpaceGesture.HOLD])
        assertFalse(SpaceGestures.holdShowsPicker(prefs))
        assertTrue(SpaceGestures.doubleTapAssigned(prefs))
        // the older settings stay untouched
        assertTrue(prefs.getBoolean(AgentPreferences.SPACE_LONG_PRESS, false))
        assertFalse(prefs.contains(Settings.PREF_SPACE_HORIZONTAL_SWIPE))

        SpaceGestures.write(prefs, SpaceGesture.HOLD, null)
        assertEquals(SpaceAction.ACTION_WHEEL, resolve(SpaceGesture.HOLD))
        SpaceGestures.write(prefs, SpaceGesture.HOLD, SpaceAction.SWITCH_INPUT_METHOD)
        assertTrue(SpaceGestures.holdShowsPicker(prefs))
    }

    @Test fun invalidStoredValuesFallBackAndCannotBeWritten() {
        prefs.edit().putString(SpaceGesture.HOLD.key, SpaceAction.MOVE_CURSOR.name)
            .putString(SpaceGesture.SWIPE_LEFT.key, SpaceAction.TOUCHPAD_MODE.name)
            .putString(SpaceGesture.DOUBLE_TAP.key, "EXECUTE_ARBITRARY_CODE").commit()
        assertEquals(SpaceAction.SWITCH_INPUT_METHOD, resolve(SpaceGesture.HOLD))
        assertEquals(SpaceAction.MOVE_CURSOR, resolve(SpaceGesture.SWIPE_LEFT))
        assertEquals(SpaceAction.NONE, resolve(SpaceGesture.DOUBLE_TAP))
        assertFailsWith<IllegalArgumentException> { SpaceGestures.write(prefs, SpaceGesture.DOUBLE_TAP, SpaceAction.MOVE_CURSOR) }
        assertFailsWith<IllegalArgumentException> { SpaceGestures.write(prefs, SpaceGesture.SWIPE_RIGHT, SpaceAction.TOUCHPAD_MODE) }
    }

    @Test fun everyActionIsOfferedForSomeGestureAndTapIsNever() {
        val offered = SpaceGesture.entries.flatMap { SpaceGestures.choices(it) }.toSet()
        assertEquals(SpaceAction.entries.toSet(), offered)
        assertTrue(SpaceGesture.entries.all { SpaceAction.NONE in SpaceGestures.choices(it) })
        assertEquals(setOf(SpaceAction.MOVE_CURSOR, SpaceAction.SWITCH_LANGUAGE, SpaceAction.TOUCHPAD_MODE),
            SpaceAction.entries.filter { it.isContinuous }.toSet())
    }

    @Test fun profilesSaveOwnValuesAndOlderProfilesKeepFollowingTheOlderSettings() {
        val store = KeyboardProfileStore(app)
        SpaceGestures.write(prefs, SpaceGesture.DOUBLE_TAP, SpaceAction.ACTION_WHEEL)
        store.saveCurrent(ProfileGroup.GESTURES)
        val saved = store.read(ProfileGroup.GESTURES)!!
        assertEquals(PreferenceValue.Choice(SpaceAction.ACTION_WHEEL.name), saved.values[SpaceGesture.DOUBLE_TAP.key])
        assertEquals(PreferenceValue.Choice(SpaceGestures.INHERIT), saved.values[SpaceGesture.HOLD.key])
        store.reset(ProfileGroup.GESTURES)
        assertFalse(prefs.contains(SpaceGesture.DOUBLE_TAP.key))
        store.restoreSaved(ProfileGroup.GESTURES)
        assertEquals(SpaceAction.ACTION_WHEEL, resolve(SpaceGesture.DOUBLE_TAP))
        assertFalse(prefs.contains(SpaceGesture.HOLD.key))
        assertFails { store.importSaved(store.exportSaved(ProfileGroup.GESTURES)!!.replace("\"ACTION_WHEEL\"", "\"MOVE_CURSOR\"")) }

        // a version 2 profile from before: all gestures follow its older values, own values are removed
        val older = """{"formatVersion":2,"group":"GESTURES","values":{"agent_space_long_press":false,"horizontal_space_swipe":"SWITCH_LANGUAGE","vertical_space_swipe":"NONE","delete_swipe":true}}"""
        store.importSaved(older)
        store.restoreSaved(ProfileGroup.GESTURES)
        assertFalse(prefs.contains(SpaceGesture.DOUBLE_TAP.key))
        assertEquals(SpaceAction.SWITCH_LANGUAGE, resolve(SpaceGesture.SWIPE_LEFT))
        assertEquals(SpaceAction.NONE, resolve(SpaceGesture.DOUBLE_TAP))
        // a version 1 profile still never opts into the wheel
        val legacy = """{"formatVersion":1,"group":"GESTURES","values":{"horizontal_space_swipe":"MOVE_CURSOR","vertical_space_swipe":"NONE","delete_swipe":true}}"""
        store.importSaved(legacy)
        assertEquals(PreferenceValue.Flag(false), store.read(ProfileGroup.GESTURES)!!.values[AgentPreferences.SPACE_LONG_PRESS])
        assertEquals(PreferenceValue.Choice(SpaceGestures.INHERIT), store.read(ProfileGroup.GESTURES)!!.values[SpaceGesture.HOLD.key])
    }
}
