// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.content.Context
import dev.agentkeyboard.core.*
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.KeyboardActionListener.SwipeAction
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsSubtype
import helium314.keyboard.latin.utils.prefs
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class KeyboardProfileStoreTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val prefs get() = app.prefs()
    private val store get() = KeyboardProfileStore(app)

    @Before fun clear() {
        prefs.edit().clear().commit()
        app.getSharedPreferences(KeyboardProfileStore.STORAGE_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun designRoundTripDoesNotChangeLearningOrOtherAreas() {
        prefs.edit().putString(Settings.PREF_THEME_STYLE, KeyboardTheme.STYLE_ROUNDED)
            .putBoolean(Settings.PREF_THEME_KEY_BORDERS, true).putFloat(Settings.PREF_FONT_SCALE, 1.25f)
            .putBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, false).commit()
        store.saveCurrent(ProfileGroup.DESIGN)
        prefs.edit().putString(Settings.PREF_THEME_STYLE, KeyboardTheme.STYLE_HOLO)
            .putBoolean(Settings.PREF_SHOW_NUMBER_ROW, true).commit()
        store.restoreSaved(ProfileGroup.DESIGN)
        assertEquals(KeyboardTheme.STYLE_ROUNDED, prefs.getString(Settings.PREF_THEME_STYLE, null))
        assertEquals(1.25f, prefs.getFloat(Settings.PREF_FONT_SCALE, 0f))
        assertTrue(prefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW, false))
        assertFalse(prefs.getBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, true))
    }

    @Test fun resetKeepsSavedProfileAndOtherPreferences() {
        prefs.edit().putBoolean(Settings.PREF_THEME_KEY_BORDERS, true).commit()
        store.saveCurrent(ProfileGroup.DESIGN)
        store.reset(ProfileGroup.DESIGN)
        assertFalse(prefs.getBoolean(Settings.PREF_THEME_KEY_BORDERS, true))
        assertTrue(store.hasSaved(ProfileGroup.DESIGN))
        store.restoreSaved(ProfileGroup.DESIGN)
        assertTrue(prefs.getBoolean(Settings.PREF_THEME_KEY_BORDERS, false))
    }

    @Test fun keyArrangementRestoresWithoutChangingDesignOrLanguages() {
        val languageSetting = SettingsSubtype(Locale.ENGLISH, "").toPref()
        prefs.edit().putBoolean(Settings.PREF_SHOW_NUMBER_ROW, true)
            .putBoolean(Settings.PREF_SHOW_EMOJI_KEY, true).putString(Settings.PREF_ENABLED_SUBTYPES, languageSetting).commit()
        store.saveCurrent(ProfileGroup.LAYOUT)
        prefs.edit().putBoolean(Settings.PREF_SHOW_NUMBER_ROW, false)
            .putString(Settings.PREF_THEME_STYLE, KeyboardTheme.STYLE_HOLO).commit()
        store.restoreSaved(ProfileGroup.LAYOUT)
        assertTrue(prefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW, false))
        assertTrue(prefs.getBoolean(Settings.PREF_SHOW_EMOJI_KEY, false))
        assertEquals(languageSetting, prefs.getString(Settings.PREF_ENABLED_SUBTYPES, null))
        assertEquals(KeyboardTheme.STYLE_HOLO, prefs.getString(Settings.PREF_THEME_STYLE, null))
    }

    @Test fun rejectedImportCannotChangeCurrentOrSavedPreferences() {
        store.saveCurrent(ProfileGroup.DESIGN)
        val original = store.exportSaved(ProfileGroup.DESIGN)!!
        val before = prefs.all.toMap()
        for (bad in listOf(
            original.replace("\"formatVersion\":2", "\"formatVersion\":99"),
            original.replace("\"font_scale\":1.0", "\"font_scale\":9999"),
            original.replace("\"values\":{", "\"values\":{\"use_personalized_dicts\":true,"),
            original.replace("\"theme_key_borders\":false", "\"theme_key_borders\":\"false\""),
            "[broken", " ".repeat(16_385),
        )) {
            assertFails { store.importSaved(bad) }
            assertEquals(original, store.exportSaved(ProfileGroup.DESIGN))
            assertEquals(before, prefs.all)
        }
    }

    @Test fun gesturesRestoreIndependentlyAndRejectUnsupportedActions() {
        prefs.edit().putString(Settings.PREF_SPACE_HORIZONTAL_SWIPE, SwipeAction.SWITCH_LANGUAGE.name)
            .putString(Settings.PREF_SPACE_VERTICAL_SWIPE, SwipeAction.HIDE_KEYBOARD.name).commit()
        store.saveCurrent(ProfileGroup.GESTURES)
        val saved = store.exportSaved(ProfileGroup.GESTURES)!!
        store.reset(ProfileGroup.GESTURES)
        assertEquals(SwipeAction.MOVE_CURSOR.name, prefs.getString(Settings.PREF_SPACE_HORIZONTAL_SWIPE, null))
        store.restoreSaved(ProfileGroup.GESTURES)
        assertEquals(SwipeAction.SWITCH_LANGUAGE.name, prefs.getString(Settings.PREF_SPACE_HORIZONTAL_SWIPE, null))
        assertEquals(SwipeAction.HIDE_KEYBOARD.name, prefs.getString(Settings.PREF_SPACE_VERTICAL_SWIPE, null))
        assertFails { store.importSaved(saved.replace(SwipeAction.SWITCH_LANGUAGE.name, "EXECUTE_ARBITRARY_CODE")) }
        assertEquals(saved, store.exportSaved(ProfileGroup.GESTURES))
    }

    @Test fun exportsContainOnlyVersionedAllowlistedConfiguration() {
        prefs.edit().putString("api_key", "never-export-this").putString("typing_history", "private").commit()
        store.saveCurrent(ProfileGroup.DESIGN)
        val encoded = store.exportSaved(ProfileGroup.DESIGN)!!
        assertFalse(encoded.contains("never-export-this"))
        assertFalse(encoded.contains("typing_history"))
        store.importSaved(encoded)
        assertEquals(ProfileGroup.DESIGN, store.read(ProfileGroup.DESIGN)!!.group)
    }

    @Test fun versionOneGesturesMigrateWithoutOptingIntoWheel() {
        val legacy = """{"formatVersion":1,"group":"GESTURES","values":{"horizontal_space_swipe":"MOVE_CURSOR","vertical_space_swipe":"NONE","delete_swipe":true}}"""
        store.importSaved(legacy)
        val migrated = store.read(ProfileGroup.GESTURES)!!
        assertEquals(2, migrated.formatVersion)
        assertEquals(PreferenceValue.Flag(false), migrated.values[AgentPreferences.SPACE_LONG_PRESS])
        prefs.edit().putBoolean(AgentPreferences.SPACE_LONG_PRESS, true).commit()
        store.restoreSaved(ProfileGroup.GESTURES)
        assertFalse(prefs.getBoolean(AgentPreferences.SPACE_LONG_PRESS, true))
        store.saveCurrent(ProfileGroup.GESTURES)
        val current = store.exportSaved(ProfileGroup.GESTURES)!!
        assertFails { store.importSaved(current.replace("\"formatVersion\":2", "\"formatVersion\":1")) }
    }

    @Test fun importingIntoTheWrongAreaCannotChangeAnySavedOrActiveProfile() {
        store.saveCurrent(ProfileGroup.DESIGN)
        store.saveCurrent(ProfileGroup.LAYOUT)
        val design = store.exportSaved(ProfileGroup.DESIGN)
        val layout = store.exportSaved(ProfileGroup.LAYOUT)!!
        val before = prefs.all.toMap()
        assertFails { store.importSaved(layout, ProfileGroup.DESIGN) }
        assertEquals(design, store.exportSaved(ProfileGroup.DESIGN))
        assertEquals(layout, store.exportSaved(ProfileGroup.LAYOUT))
        assertEquals(before, prefs.all)
    }
}
