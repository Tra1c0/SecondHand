// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.app.KeyguardManager
import android.os.UserManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.Lifecycle
import helium314.keyboard.keyboard.KeyboardActionListener
import dev.agentkeyboard.core.ProfileGroup
import dev.agentkeyboard.core.UpdateReleases
import helium314.keyboard.agent.updates.GitHubUpdateClient
import helium314.keyboard.agent.updates.UpdateError
import helium314.keyboard.agent.updates.UpdateSettings
import helium314.keyboard.agent.updates.UpdateStatus
import helium314.keyboard.agent.updates.Updates
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.SettingsWithoutKey
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.screens.AREA_ROW_TAG
import helium314.keyboard.settings.screens.AREA_TAG
import helium314.keyboard.settings.screens.AgentAreaScreen
import helium314.keyboard.settings.screens.AgentAreaSystemScreen
import helium314.keyboard.settings.screens.HERO_TAG
import helium314.keyboard.settings.screens.PROFILE_CARD_TAG
import helium314.keyboard.settings.screens.SYSTEM_APPLY_TAG
import helium314.keyboard.settings.screens.SYSTEM_CHECK_TAG
import helium314.keyboard.settings.screens.SYSTEM_EXPORT_TAG
import helium314.keyboard.settings.screens.SYSTEM_INSTALL_TAG
import helium314.keyboard.settings.screens.SYSTEM_ROW_TAG
import helium314.keyboard.settings.screens.SYSTEM_UPDATES_DETAIL_TAG
import helium314.keyboard.settings.screens.SYSTEM_UPDATES_HEADLINE_TAG
import helium314.keyboard.settings.screens.SYSTEM_UPDATES_TAG
import helium314.keyboard.settings.screens.SettingsArea
import helium314.keyboard.settings.screens.SettingsAreas
import helium314.keyboard.settings.screens.emojiVersionName
import helium314.keyboard.settings.screens.updateCardText
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Stage 38h: the area page "System & Daten" (mockup System.dc.html) on the real settings, offline. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "de-w390dp-h2400dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class SettingsSystemAreaTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app: Application = RuntimeEnvironment.getApplication()
    private val routes = mutableListOf<String>()
    private val requests = CopyOnWriteArrayList<Request>()
    private val installed by lazy { Updates.installedVersion(app) }

    /** Keys this page writes; removed after each test (Fallstrick P32). */
    private val written = listOf(Settings.PREF_URL_DETECTION, Settings.PREF_ENABLE_HARDWARE_KEYBOARD, Settings.PREF_EMOJI_MAX_SDK,
        Settings.PREF_THEME_STYLE) + FlatPreset(RuntimeEnvironment.getApplication()).values().keys +
        // applying the gestures profile writes all its keys
        KeyboardProfileStore(RuntimeEnvironment.getApplication()).capture(ProfileGroup.GESTURES).values.keys

    @Before fun resetUpdates() {
        UpdateSettings(app).apply { daily = false; status = UpdateStatus.Unknown; checkedAt = 0 }
    }

    @After fun reset() {
        app.prefs().edit().apply { written.forEach { remove(it) } }.commit()
        app.getSharedPreferences(KeyboardProfileStore.STORAGE_NAME, 0).edit().clear().commit()
        app.getSharedPreferences(FlatPreset.BACKUP_NAME, 0).edit().clear().commit()
        Settings.getInstance().loadSettings(app)
    }

    private fun releasesJson(vararg numbers: Long) = buildJsonArray {
        for (n in numbers) add(buildJsonObject {
            put("tag_name", "build-$n"); put("draft", false); put("prerelease", false)
            put("assets", buildJsonArray {
                for ((name, size) in listOf(UpdateReleases.apkName(n) to 1000L, UpdateReleases.checksumName(n) to 100L))
                    add(buildJsonObject {
                        put("id", n * 10); put("name", name); put("size", size)
                        put("browser_download_url", UpdateReleases.downloadUrl("build-$n", name))
                    })
            })
        })
    }.toString()

    /** Offline fake of the releases API; every request is recorded. */
    private fun fakeClient(releases: String): GitHubUpdateClient = GitHubUpdateClient(OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        requests += request
        val ok = request.url.toString() == GitHubUpdateClient.RELEASES_URL
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(if (ok) 200 else 404).message("x")
            .body((if (ok) releases else "").toByteArray().toResponseBody()).build()
    }.build())

    private var clientsMade = 0

    private fun start(releases: String = releasesJson(installed + 1)) {
        initPreview(app)
        compose.setContent {
            Theme(dark = true) {
                AgentAreaSystemScreen(onNavigate = { routes += it }, onClickBack = {}) { clientsMade++; fakeClient(releases) }
            }
        }
        compose.waitForIdle()
    }

    private fun exists(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    private fun text(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        .config.getOrNull(SemanticsProperties.Text)?.joinToString("").orEmpty()

    @Test fun showsTheMockupTextsInItsSections() {
        start()
        compose.onNodeWithTag(AREA_TAG + SettingsDestination.AreaSystem).assertExists()
        compose.onNodeWithTag(HERO_TAG).assert(hasText("07")).assert(hasText("System & Daten"))
            .assert(hasText("Sichern, wiederherstellen, aktuell halten, nachsehen."))
        listOf("UPDATES", "Jetzt prüfen", "Diagnose", "PROFILE", "FLACHER LOOK", "Vorlage nach deinen Screenshots",
            "+ NEUES PROFIL", "Aktuellen Stand sichern", "Sichern", "Sichern und Wiederherstellen", "Alle Einstellungen als Datei",
            "Profile exportieren", "Design, Tasten, Gesten einzeln", "Für Fortgeschrittene", "Emoji-Version",
            "Welche Emojis angeboten werden", "URL-Erkennung", "Adressen als ein Wort", "Hardware-Tastatur",
            "Unterstützung für externe Tastaturen", "Gesten-Bibliothek laden", "Für Gestenschreiben", "Debug-Einstellungen",
            "Freigeschaltet", "Alle erweiterten Einstellungen", "Über", app.getString(R.string.english_ime_name) /* "Agent Keyboard", in debug builds with a suffix */,
            "Version ${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}", "Lizenz", "GNU GPL v3.0 · basiert auf HeliBoard",
            "Versteckte Funktionen", "Was man leicht übersieht", "Protokoll speichern", "Für Fehlermeldungen", "Alle Infos und Links")
            .forEach { assertTrue(exists(it), "missing text: $it") }
        assertFalse(exists(app.getString(R.string.agent_area_pages_note)))
        // section order as in the mockup
        val tops = listOf("UPDATES", "PROFILE", "Sichern und Wiederherstellen", "Emoji-Version", "Lizenz")
            .map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops)
    }

    @Test fun updateCardTextFollowsTheStoredResult() {
        val now = 10_000_000_000L
        val twoHours = now - 2 * DateUtils.HOUR_IN_MILLIS
        val relative = DateUtils.getRelativeTimeSpanString(twoHours, now, DateUtils.MINUTE_IN_MILLIS).toString()
            .replaceFirstChar { it.lowercase() }
        updateCardText(app, UpdateStatus.Unknown, 16, 0, false, now).let {
            assertEquals("Noch nicht geprüft", it.headline)
            assertEquals("Installiert: Build 16 · automatische Prüfung aus", it.detail)
            assertEquals(null, it.available)
        }
        updateCardText(app, UpdateStatus.Current(16), 16, twoHours, true, now).let {
            assertEquals("Build 16 ist aktuell", it.headline)
            assertEquals("Zuletzt geprüft $relative · täglich automatisch", it.detail)
        }
        updateCardText(app, UpdateStatus.Available(17), 16, twoHours, false, now).let {
            assertEquals("Build 17 verfügbar", it.headline)
            assertEquals(17L, it.available)
        }
        // a result from before an update of this app claims nothing about the new build
        assertEquals("Build 17 installiert", updateCardText(app, UpdateStatus.Current(16), 17, twoHours, true, now).headline)
        assertEquals(null, updateCardText(app, UpdateStatus.Available(17), 17, twoHours, true, now).available)
        updateCardText(app, UpdateStatus.Failed(UpdateError.NETWORK), 16, twoHours, true, now).let {
            assertEquals("Prüfung fehlgeschlagen", it.headline)
            assertEquals("Keine Verbindung zu GitHub · Zuletzt geprüft $relative · täglich automatisch", it.detail)
        }
        // download and install failures (stored by the Updates page) are not failed checks
        for (error in listOf(UpdateError.CHECKSUM, UpdateError.WRONG_SIGNATURE, UpdateError.WRONG_PACKAGE, UpdateError.INSTALL))
            assertEquals("Installation fehlgeschlagen", updateCardText(app, UpdateStatus.Failed(error), 16, twoHours, true, now).headline, error.name)
        assertEquals("Prüfung fehlgeschlagen", updateCardText(app, UpdateStatus.Failed(UpdateError.RATE_LIMIT), 16, twoHours, true, now).headline)
        // under a minute "gerade eben", not "vor 0 Minuten"
        assertEquals("Zuletzt geprüft gerade eben · täglich automatisch",
            updateCardText(app, UpdateStatus.Current(16), 16, now - 30_000, true, now).detail)
        assertEquals("Zuletzt geprüft vor 1 Minute · täglich automatisch",
            updateCardText(app, UpdateStatus.Current(16), 16, now - DateUtils.MINUTE_IN_MILLIS, true, now).detail)
        // from a week on DateUtils gives a date: "am <Datum>", not lowercased
        val old = now - 10 * DateUtils.DAY_IN_MILLIS
        val date = DateUtils.getRelativeTimeSpanString(old, now, DateUtils.MINUTE_IN_MILLIS).toString()
        assertFalse(date.startsWith("vor", ignoreCase = true), date)
        assertEquals("Zuletzt geprüft am $date · täglich automatisch", updateCardText(app, UpdateStatus.Current(16), 16, old, true, now).detail)
    }

    @Test fun theCardShowsTheStoredStateAndUsesTheNetworkOnlyOnTap() {
        UpdateSettings(app).apply { status = UpdateStatus.Current(installed); checkedAt = System.currentTimeMillis() - 2 * DateUtils.HOUR_IN_MILLIS; daily = true }
        start()
        assertEquals("Build $installed ist aktuell", text(SYSTEM_UPDATES_HEADLINE_TAG))
        assertTrue(text(SYSTEM_UPDATES_DETAIL_TAG).startsWith("Zuletzt geprüft vor 2 Stunden"), text(SYSTEM_UPDATES_DETAIL_TAG))
        assertTrue(text(SYSTEM_UPDATES_DETAIL_TAG).endsWith(" · täglich automatisch"), text(SYSTEM_UPDATES_DETAIL_TAG))
        compose.onNodeWithTag(SYSTEM_INSTALL_TAG).assertDoesNotExist()
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
        // opening the page made no client and sent nothing
        assertEquals(0, clientsMade)
        assertTrue(requests.isEmpty())

        val before = UpdateSettings(app).checkedAt
        compose.onNodeWithTag(SYSTEM_CHECK_TAG).performClick()
        compose.waitUntil(5_000) { compose.waitForIdle(); text(SYSTEM_UPDATES_HEADLINE_TAG) == "Build ${installed + 1} verfügbar" }
        assertEquals(1, clientsMade)
        assertEquals(GitHubUpdateClient.RELEASES_URL, requests.single().url.toString())
        assertEquals(null, requests.single().header("Authorization"))
        assertEquals(UpdateStatus.Available(installed + 1), UpdateSettings(app).status)
        assertTrue(UpdateSettings(app).checkedAt > before)
        // download and install stay on the Updates page
        compose.onNodeWithTag(SYSTEM_INSTALL_TAG).assert(hasText("Build ${installed + 1} installieren")).performClick()
        assertEquals(listOf(SettingsDestination.AgentUpdates), routes)
    }

    @Test fun whileLockedTheCardOnlySaysSoLikeTheOldPage() {
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        start()
        compose.onNodeWithTag(SYSTEM_UPDATES_TAG).assertExists()
        assertTrue(exists(app.getString(R.string.agent_updates_unlock)))
        compose.onNodeWithTag(SYSTEM_CHECK_TAG).assertDoesNotExist()
        compose.onNodeWithTag(SYSTEM_UPDATES_HEADLINE_TAG).assertDoesNotExist()
        assertProfilesShown()
        compose.onNodeWithTag(AREA_ROW_TAG + SettingsDestination.AgentDiagnose).performClick()
        assertEquals(listOf(SettingsDestination.AgentDiagnose), routes)
        assertEquals(0, clientsMade)
    }

    /**
     * Before the first unlock (Direct Boot) the keyguard may already be gone; the user is still locked (P145).
     * Only the update card (credential-protected) waits; profiles and the gesture library are device-protected.
     */
    @Test fun beforeTheFirstUnlockOnlyTheUpdateCardWaits() {
        KeyboardProfileStore(app).saveCurrent(ProfileGroup.DESIGN)
        shadowOf(app.getSystemService(UserManager::class.java)).setUserUnlocked(false)
        assertFalse(app.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        start()
        assertTrue(exists(app.getString(R.string.agent_updates_unlock)))
        compose.onNodeWithTag(SYSTEM_CHECK_TAG).assertDoesNotExist()
        assertProfilesShown()
        // the rest of the page stays usable
        compose.onNodeWithTag(SYSTEM_ROW_TAG + Settings.PREF_URL_DETECTION).assertExists()
        compose.onNodeWithTag(SYSTEM_ROW_TAG + SettingsWithoutKey.BACKUP_RESTORE).assertExists()
        // after unlocking, the next resume shows the cards
        shadowOf(app.getSystemService(UserManager::class.java)).setUserUnlocked(true)
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        compose.onNodeWithTag(PROFILE_CARD_TAG + ProfileGroup.DESIGN).assertExists()
        compose.onNodeWithTag(SYSTEM_CHECK_TAG).assertExists()
    }

    /** While locked the profile cards and the gesture library stay: they are in device-protected storage (P145). */
    private fun assertProfilesShown() {
        listOf(PROFILE_CARD_TAG + "FLAT", AREA_ROW_TAG + SettingsDestination.AgentProfiles,
            SYSTEM_ROW_TAG + SettingsWithoutKey.LOAD_GESTURE_LIB).forEach { compose.onNodeWithTag(it).performScrollTo().assertExists() }
    }

    @Test fun profileCardsAreButtonsThatApplyTheRealProfiles() {
        val store = KeyboardProfileStore(app)
        app.prefs().edit().putString(Settings.PREF_THEME_STYLE, KeyboardTheme.STYLE_ROUNDED).commit()
        store.saveCurrent(ProfileGroup.DESIGN)
        start()
        fun node(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode()
        val tags = listOf(PROFILE_CARD_TAG + ProfileGroup.DESIGN, PROFILE_CARD_TAG + "FLAT", AREA_ROW_TAG + SettingsDestination.AgentProfiles)
        tags.forEach {
            assertEquals(Role.Button, node(it).config.getOrNull(SemanticsProperties.Role), it)
            assertEquals(null, node(it).config.getOrNull(SemanticsProperties.Selected), it)
        }
        // only saved areas get a card, in the order of the store
        compose.onNodeWithTag(PROFILE_CARD_TAG + ProfileGroup.LAYOUT).assertDoesNotExist()
        compose.onNodeWithTag(PROFILE_CARD_TAG + ProfileGroup.DESIGN).assert(hasText("DESIGN")).assert(hasText("Gesicherter Stand · antippen zum Anwenden"))
        // highlighted: what equals the current settings
        val active = app.getString(R.string.agent_system_profile_active)
        assertEquals(active, node(tags[0]).config.getOrNull(SemanticsProperties.StateDescription))
        assertEquals(null, node(tags[1]).config.getOrNull(SemanticsProperties.StateDescription))

        // the flat look: confirm, then FlatPreset.apply writes its values
        compose.onNodeWithTag(tags[1]).performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(exists("Flacher Look anwenden?"))
        compose.onNodeWithTag(SYSTEM_APPLY_TAG).performClick()
        compose.waitForIdle()
        assertEquals(KeyboardTheme.STYLE_FLAT, app.prefs().getString(Settings.PREF_THEME_STYLE, null))
        assertTrue(FlatPreset(app).isApplied())
        assertTrue(FlatPreset(app).canRestore)
        assertEquals(active, node(tags[1]).config.getOrNull(SemanticsProperties.StateDescription))
        // the saved design (rounded) no longer matches
        assertEquals(null, node(tags[0]).config.getOrNull(SemanticsProperties.StateDescription))

        // the saved design profile: cancel changes nothing, apply restores it
        compose.onNodeWithTag(tags[0]).performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(exists("Design anwenden?"))
        compose.onNodeWithText("Abbrechen").performClick()
        compose.waitForIdle()
        assertEquals(KeyboardTheme.STYLE_FLAT, app.prefs().getString(Settings.PREF_THEME_STYLE, null))
        compose.onNodeWithTag(tags[0]).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(SYSTEM_APPLY_TAG).performClick()
        compose.waitForIdle()
        assertEquals(KeyboardTheme.STYLE_ROUNDED, app.prefs().getString(Settings.PREF_THEME_STYLE, null))
        assertEquals(active, node(tags[0]).config.getOrNull(SemanticsProperties.StateDescription))

        // "+ NEUES PROFIL" opens the profiles page
        compose.onNodeWithTag(tags[2]).performScrollTo().performClick()
        assertEquals(listOf(SettingsDestination.AgentProfiles), routes)
    }

    @Test fun aCurrentValueOutsideTheProfileBindingsDoesNotBreakThePage() {
        KeyboardProfileStore(app).saveCurrent(ProfileGroup.GESTURES)
        // e.g. from a restored backup: KeyboardProfileStore.capture would throw for it
        app.prefs().edit().putString(Settings.PREF_SPACE_HORIZONTAL_SWIPE, KeyboardActionListener.SwipeAction.HIDE_KEYBOARD.name).commit()
        start()
        val card = compose.onNodeWithTag(PROFILE_CARD_TAG + ProfileGroup.GESTURES).fetchSemanticsNode()
        assertEquals(null, card.config.getOrNull(SemanticsProperties.StateDescription))
        compose.onNodeWithTag(PROFILE_CARD_TAG + ProfileGroup.GESTURES).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(SYSTEM_APPLY_TAG).performClick()
        compose.waitForIdle()
        // applying the saved profile writes a valid value again, and the card matches
        assertEquals(KeyboardProfileStore(app).read(ProfileGroup.GESTURES)!!.values[Settings.PREF_SPACE_HORIZONTAL_SWIPE],
            dev.agentkeyboard.core.PreferenceValue.Choice(app.prefs().getString(Settings.PREF_SPACE_HORIZONTAL_SWIPE, null)!!))
        assertEquals(app.getString(R.string.agent_system_profile_active),
            compose.onNodeWithTag(PROFILE_CARD_TAG + ProfileGroup.GESTURES).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
    }

    @Test fun switchesAndRowsUseTheRealPreferencesAndDialogs() {
        start()
        val prefs = app.prefs()
        fun state(key: String) = compose.onNodeWithTag(SYSTEM_ROW_TAG + key).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState)
        for (key in listOf(Settings.PREF_URL_DETECTION, Settings.PREF_ENABLE_HARDWARE_KEYBOARD)) {
            assertEquals(ToggleableState.Off, state(key))
            compose.onNodeWithTag(SYSTEM_ROW_TAG + key).performScrollTo().performClick()
            compose.waitForIdle()
            assertTrue(prefs.getBoolean(key, false), key)
            assertEquals(ToggleableState.On, state(key))
        }
        // emoji version: the slider of Erweitert, value as "Android N"
        prefs.edit().putInt(Settings.PREF_EMOJI_MAX_SDK, 34).commit()
        compose.onNodeWithTag(SYSTEM_ROW_TAG + Settings.PREF_URL_DETECTION).performClick() // any write recomposes
        compose.waitForIdle()
        // the redraw tap switched URL detection off again
        assertFalse(prefs.getBoolean(Settings.PREF_URL_DETECTION, true))
        assertEquals(ToggleableState.Off, state(Settings.PREF_URL_DETECTION))
        compose.onNodeWithTag(SYSTEM_ROW_TAG + Settings.PREF_EMOJI_MAX_SDK).assert(hasText("Android 14"))
        assertEquals("Android 16", emojiVersionName(36, "?"))
        assertEquals("?", emojiVersionName(0, "?"))
        compose.onNodeWithTag(SYSTEM_ROW_TAG + Settings.PREF_EMOJI_MAX_SDK).performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(exists(app.getString(R.string.prefs_key_emoji_max_sdk)))
        // a value from the slider: OK writes it
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performSemanticsAction(SemanticsActions.SetProgress) { it(30f) }
        compose.waitForIdle()
        compose.onNodeWithText(app.getString(android.R.string.ok)).performClick()
        compose.waitForIdle()
        assertFalse(exists(app.getString(R.string.prefs_key_emoji_max_sdk)))
        assertEquals(30, prefs.getInt(Settings.PREF_EMOJI_MAX_SDK, 0))
        compose.onNodeWithTag(SYSTEM_ROW_TAG + Settings.PREF_EMOJI_MAX_SDK).assert(hasText("Android 11"))
        compose.onNodeWithTag(SYSTEM_ROW_TAG + Settings.PREF_EMOJI_MAX_SDK).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText(app.getString(R.string.button_default)).performClick()
        compose.waitForIdle()
        assertFalse(exists(app.getString(R.string.prefs_key_emoji_max_sdk)))
        // "Standard" removes the key; SupportedEmojis.load then determines the level of the device again
        // and stores it: at least the Android version of the device (here API 28)
        assertTrue(prefs.contains(Settings.PREF_EMOJI_MAX_SDK))
        val level = prefs.getInt(Settings.PREF_EMOJI_MAX_SDK, 0)
        assertTrue(level >= android.os.Build.VERSION.SDK_INT, "level $level")
        compose.onNodeWithTag(SYSTEM_ROW_TAG + Settings.PREF_EMOJI_MAX_SDK).assert(hasText(emojiVersionName(level, "?")))

        // backup and restore: the dialog of BackupRestorePreference
        compose.onNodeWithTag(SYSTEM_ROW_TAG + SettingsWithoutKey.BACKUP_RESTORE).performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(exists(app.getString(R.string.backup_restore_message)))
        compose.onNodeWithText(app.getString(R.string.button_backup)).assertExists()
        compose.onNodeWithText(app.getString(R.string.button_restore)).assertExists()
        compose.onNodeWithText(app.getString(android.R.string.cancel)).performClick()
        compose.waitForIdle()
        assertFalse(exists(app.getString(R.string.backup_restore_message)))

        // gesture library: the dialog of LoadGestureLibPreference
        compose.onNodeWithTag(SYSTEM_ROW_TAG + SettingsWithoutKey.LOAD_GESTURE_LIB).performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(exists(app.getString(R.string.load_gesture_library_button_load)))
        compose.onNodeWithText(app.getString(android.R.string.cancel)).performClick()
        compose.waitForIdle()

        // hidden features: the AlertDialog of the about page
        compose.onNodeWithTag(SYSTEM_ROW_TAG + SettingsWithoutKey.HIDDEN_FEATURES).performScrollTo().performClick()
        compose.waitForIdle()
        val dialog = assertNotNull(ShadowAlertDialog.getLatestAlertDialog())
        assertTrue(dialog.isShowing)
        assertEquals(app.getString(R.string.hidden_features_title), shadowOf(dialog).title.toString())
        dialog.dismiss()
    }

    @Test fun everyOldEntryOfThisAreaIsReachable() {
        start()
        // P119: the pages of SettingsAreas.entries(SYSTEM), each from its own row, card or button
        val pages = SettingsAreas.entries(SettingsArea.SYSTEM).filter { it.visible() }.map { it.route }
        assertTrue(pages.containsAll(listOf(SettingsDestination.AgentProfiles, SettingsDestination.AgentUpdates,
            SettingsDestination.AgentDiagnose, SettingsDestination.Advanced, SettingsDestination.About)), pages.toString())
        pages.forEach { compose.onNodeWithTag(AREA_ROW_TAG + it).performScrollTo().performClick() }
        assertEquals(pages, routes)
        routes.clear()
        // debug settings (visible in debug builds or once unlocked) and the export row
        compose.onNodeWithTag(SYSTEM_ROW_TAG + SettingsDestination.Debug).performScrollTo().performClick()
        compose.onNodeWithTag(SYSTEM_EXPORT_TAG).performScrollTo().performClick()
        assertEquals(listOf(SettingsDestination.Debug, SettingsDestination.AgentProfiles), routes)
        // the leftovers of Erweitert (numpad symbols, emoji on physical keyboards, app icon) stay on the old page
        assertTrue(exists("Alle erweiterten Einstellungen"))
    }

    @Test fun theAreaDispatcherOpensThisPage() {
        initPreview(app)
        compose.setContent { Theme(dark = true) { AgentAreaScreen(SettingsArea.SYSTEM, onNavigate = { routes += it }, onClickBack = {}) } }
        compose.waitForIdle()
        compose.onNodeWithTag(SYSTEM_UPDATES_TAG).assertExists()
        compose.onNodeWithTag(SYSTEM_CHECK_TAG).assertExists()
    }

    @Test fun everyClickableIsAtLeast44dp() {
        UpdateSettings(app).status = UpdateStatus.Available(installed + 1)
        KeyboardProfileStore(app).saveCurrent(ProfileGroup.GESTURES)
        start()
        val min = 44 * app.resources.displayMetrics.density - 1
        val clickables = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(clickables.size >= 20, "clickables: ${clickables.size}")
        clickables.forEach { assertTrue(it.size.height >= min && it.size.width >= min, "too small: ${it.size} ${it.config}") }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun renderPage() {
        UpdateSettings(app).apply { status = UpdateStatus.Current(installed); checkedAt = System.currentTimeMillis() - 2 * DateUtils.HOUR_IN_MILLIS; daily = true }
        start()
        lateinit var bitmap: Bitmap
        // captureToImage waits for a redraw that never comes in Robolectric (Fallstrick P15)
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        val file = File("build/agent-previews/settings-area_system-full.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        fun near(expected: Int, pixel: Int) = listOf(16, 8, 0).maxOf { kotlin.math.abs((expected shr it and 0xFF) - (pixel shr it and 0xFF)) } <= 40
        // the updates card starts green (#1B3A2E) at the top left and ends in the card colour
        val card = compose.onNodeWithTag(SYSTEM_UPDATES_TAG).fetchSemanticsNode().boundsInRoot
        val green = bitmap.getPixel(card.left.toInt() + 20, card.top.toInt() + 20)
        assertTrue(near(0xFF1B3A2E.toInt(), green), "updates card #%08X".format(green))
        val end = bitmap.getPixel(card.right.toInt() - 20, card.bottom.toInt() - 20)
        assertTrue(near(0xFF18181D.toInt(), end), "updates card end #%08X".format(end))
        // the hero in the area grey
        val hero = compose.onNodeWithTag(HERO_TAG).fetchSemanticsNode().boundsInRoot
        val heroPixel = bitmap.getPixel(hero.left.toInt() + 6, hero.top.toInt() + 40)
        assertTrue(near(0xFF94A3B8.toInt(), heroPixel), "hero #%08X".format(heroPixel))
        assertEquals(androidx.compose.ui.graphics.Color(0xFF0B0B0F).toArgb(), bitmap.getPixel(2, bitmap.height - 2))
    }
}
