// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.app.KeyguardManager
import android.os.UserManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.agentkeyboard.core.AvailableBuild
import dev.agentkeyboard.core.ReleaseAsset
import dev.agentkeyboard.core.UpdateReleases
import helium314.keyboard.agent.updates.UpdatePrompts
import helium314.keyboard.agent.updates.UpdatePrompts.Prompt
import helium314.keyboard.agent.updates.UpdateScheduler
import helium314.keyboard.agent.updates.UpdateSettings
import helium314.keyboard.agent.updates.UpdateStatus
import helium314.keyboard.settings.screens.AgentUpdatePrompts
import helium314.keyboard.settings.screens.UPDATE_DIALOG_TAG
import helium314.keyboard.settings.screens.UPDATE_INSTALL_TAG
import helium314.keyboard.settings.screens.UPDATE_LATER_TAG
import helium314.keyboard.settings.screens.UPDATE_NOTES_MORE_TAG
import helium314.keyboard.settings.screens.WHATS_NEW_TAG
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.File
import kotlin.test.*

/** Stage 54: the update dialogs of the settings, from stored results and the notes in the APK only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "de")
@LooperMode(LooperMode.Mode.PAUSED)
class UpdatePromptsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val settings get() = UpdateSettings(app)

    private fun build(number: Long, notes: String = "- Neu: Punkt $number") = AvailableBuild(number,
        ReleaseAsset(UpdateReleases.apkName(number), 1000, UpdateReleases.downloadUrl("build-$number", UpdateReleases.apkName(number))),
        ReleaseAsset(UpdateReleases.checksumName(number), 100, UpdateReleases.downloadUrl("build-$number", UpdateReleases.checksumName(number))),
        notes)

    @Before fun reset() {
        settings.apply { status = UpdateStatus.Unknown; checkedAt = 0; laterBuild = 0; seenVersion = 0; remember(null, 0) }
        UpdatePrompts.takeInstallRequest(0)
    }

    @Test fun theNotesOfThisBuildAreEmbeddedFromTheReleaseNotesFile() {
        val notes = UpdatePrompts.embeddedNotes(app)
        // the unit tests run in the app module; the asset is generated from release-notes/next.md
        assertEquals(File("../release-notes/next.md").readText(), notes)
        assertTrue(notes.isNotBlank())
    }

    @Test fun whatsNewComesOnceAfterAVersionChangeAndNotAfterAFreshInstall() {
        // fresh install: the version is only remembered
        assertNull(UpdatePrompts.next(app, 20) { true })
        assertEquals(20L, settings.seenVersion)
        assertNull(UpdatePrompts.next(app, 20) { false })
        // updated to 21: once
        val prompt = assertIs<Prompt.WhatsNew>(UpdatePrompts.next(app, 21) { false })
        assertEquals(21L, prompt.version)
        assertEquals(UpdatePrompts.embeddedNotes(app), prompt.notes)
        assertIs<Prompt.WhatsNew>(UpdatePrompts.next(app, 21) { false }, "not seen yet: still there")
        UpdatePrompts.markSeen(app, 21)
        assertNull(UpdatePrompts.next(app, 21) { false })
        // an update over an older build without this stage (nothing seen yet, not fresh): shown
        settings.seenVersion = 0
        assertIs<Prompt.WhatsNew>(UpdatePrompts.next(app, 21) { false })
        // also on the Updates page itself
        assertIs<Prompt.WhatsNew>(UpdatePrompts.next(app, 21, available = false) { false })
    }

    @Test fun availableDialogPerBuildWithLaterAndGoneAfterTheInstall() {
        settings.seenVersion = 20
        assertNull(UpdatePrompts.next(app, 20), "nothing known")
        settings.remember(build(22, "## Build 22\n- Neu: X"), 20)
        val prompt = assertIs<Prompt.Available>(UpdatePrompts.next(app, 20))
        assertEquals(22L, prompt.build)
        assertEquals("## Build 22\n- Neu: X", prompt.notes)
        assertFalse(prompt.truncated)
        assertNull(UpdatePrompts.next(app, 20, available = false), "not on the Updates page itself")
        // "Später" for 22: no dialog for 22 ...
        UpdatePrompts.later(app, 22)
        assertNull(UpdatePrompts.next(app, 20))
        // ... but for 23
        settings.remember(build(23), 20)
        assertEquals(23L, assertIs<Prompt.Available>(UpdatePrompts.next(app, 20)).build)
        // installed 23: gone (and then "Neu in Build 23" once)
        assertIs<Prompt.WhatsNew>(UpdatePrompts.next(app, 23) { false })
        UpdatePrompts.markSeen(app, 23)
        assertNull(UpdatePrompts.next(app, 23))
    }

    @Test fun noDialogWhileLockedOrBeforeTheFirstUnlock() {
        settings.apply { seenVersion = 20; remember(build(22), 20) }
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        assertNull(UpdatePrompts.next(app, 20))
        assertNull(UpdatePrompts.next(app, 21) { false })
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        shadowOf(app.getSystemService(UserManager::class.java)).setUserUnlocked(false)
        assertNull(UpdatePrompts.next(app, 20))
        shadowOf(app.getSystemService(UserManager::class.java)).setUserUnlocked(true)
        assertNotNull(UpdatePrompts.next(app, 20))
    }

    @Test fun dialogShowsTheNotesAndLaterHidesItForThatBuild() {
        settings.apply { seenVersion = 20; remember(build(22, "# Build 22\n- Neu: **Update-Fenster**\n- Behoben: Y"), 20) }
        val installs = mutableListOf<Long>()
        var round by mutableIntStateOf(0)
        compose.setContent {
            // a new key on each round: like opening the settings again
            androidx.compose.runtime.key(round) { AgentUpdatePrompts(installed = 20, freshInstall = { false }, onInstall = { installs += it }) }
        }
        compose.waitForIdle()
        compose.onNodeWithTag(UPDATE_DIALOG_TAG).assertExists()
        compose.onNodeWithText("Build 22 ist da").assertExists()
        compose.onNodeWithText("Neu: Update-Fenster").assertExists()
        compose.onNodeWithText("Behoben: Y").assertExists()
        compose.onNodeWithTag(UPDATE_LATER_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(UPDATE_DIALOG_TAG).assertDoesNotExist()
        assertEquals(22L, settings.laterBuild)
        assertEquals(emptyList(), installs)
        round++
        compose.waitForIdle()
        compose.onNodeWithTag(UPDATE_DIALOG_TAG).assertDoesNotExist()
        // a newer build shows again; "Installieren" hands the build to the Updates page
        settings.remember(build(23), 20)
        round++
        compose.waitForIdle()
        compose.onNodeWithText("Build 23 ist da").assertExists()
        compose.onNodeWithTag(UPDATE_INSTALL_TAG).performClick()
        compose.waitForIdle()
        assertEquals(listOf(23L), installs)
        compose.onNodeWithTag(UPDATE_DIALOG_TAG).assertDoesNotExist()
        assertEquals(22L, settings.laterBuild, "installing is not \"Später\"")
        // nothing was checked or scheduled by the dialogs
        assertEquals(0L, settings.checkedAt)
        assertNull(UpdateScheduler.pending(app))
    }

    @Test fun installFromTheDialogIsOneRequestForThatBuild() {
        UpdatePrompts.requestInstall(23)
        assertFalse(UpdatePrompts.takeInstallRequest(22))
        assertFalse(UpdatePrompts.takeInstallRequest(23), "any look clears the request")
        UpdatePrompts.requestInstall(23)
        assertTrue(UpdatePrompts.takeInstallRequest(23))
        assertFalse(UpdatePrompts.takeInstallRequest(23))
    }

    @Test fun whatsNewDialogComesOnceThenTheAvailableOne() {
        settings.apply { seenVersion = 20; remember(build(23), 21) }
        var round by mutableIntStateOf(0)
        compose.setContent {
            androidx.compose.runtime.key(round) { AgentUpdatePrompts(installed = 21, freshInstall = { false }, onInstall = {}) }
        }
        compose.waitForIdle()
        compose.onNodeWithTag(WHATS_NEW_TAG).assertExists()
        compose.onNodeWithText("Neu in Build 21").assertExists()
        assertEquals(21L, settings.seenVersion, "shown counts as seen")
        // long notes are collapsed
        compose.onNodeWithTag(UPDATE_NOTES_MORE_TAG).assertExists()
        compose.onNodeWithText("OK").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(WHATS_NEW_TAG).assertDoesNotExist()
        compose.onNodeWithText("Build 23 ist da").assertExists()
        round++
        compose.waitForIdle()
        compose.onNodeWithTag(WHATS_NEW_TAG).assertDoesNotExist()
        assertTrue(compose.onAllNodesWithText("Neu in Build 21").fetchSemanticsNodes().isEmpty())
    }

    @Test fun truncatedNotesSayWhereTheRestIs() {
        settings.apply { seenVersion = 20; remember(build(22, (1..3000).joinToString("\n") { "- Punkt $it" }), 20) }
        assertTrue(settings.knownNotesTruncated)
        compose.setContent { AgentUpdatePrompts(installed = 20, freshInstall = { false }, onInstall = {}) }
        compose.waitForIdle()
        compose.onNodeWithTag(UPDATE_NOTES_MORE_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Gekürzt. Alle Notizen: ${UpdateReleases.RELEASES_PAGE}/tag/build-22").assertExists()
    }
}
