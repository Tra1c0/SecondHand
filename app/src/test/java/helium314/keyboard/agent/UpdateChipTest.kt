// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import dev.agentkeyboard.core.AvailableBuild
import dev.agentkeyboard.core.ReleaseAsset
import dev.agentkeyboard.core.UpdateReleases
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.agent.updates.UpdateChip
import helium314.keyboard.agent.updates.UpdateScheduler
import helium314.keyboard.agent.updates.UpdateSettings
import helium314.keyboard.agent.updates.UpdateStatus
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.*

/** Stage 54: the update chip of the keyboard strip. */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class], qualifiers = "de")
class UpdateChipTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private var installed = 20L
    private var locked = false
    private val defaultLocked = UpdateChip.locked
    private val defaultInstalled = UpdateChip.installed

    private fun known(number: Long) = UpdateSettings(latinIME).remember(AvailableBuild(number,
        ReleaseAsset(UpdateReleases.apkName(number), 1000, UpdateReleases.downloadUrl("build-$number", UpdateReleases.apkName(number))),
        ReleaseAsset(UpdateReleases.checksumName(number), 100, UpdateReleases.downloadUrl("build-$number", UpdateReleases.checksumName(number))),
        "- Neu"), installed)

    @Before fun setUp() {
        UpdateSettings(latinIME).apply { status = UpdateStatus.Unknown; checkedAt = 0; remember(null, 0) }
        UpdateChip.locked = { locked }
        UpdateChip.installed = { installed }
    }

    @After fun tearDown() {
        UpdateChip.locked = defaultLocked
        UpdateChip.installed = defaultInstalled
    }

    private fun chip(): TextView? {
        val row = UpdateChip.createView(latinIME, FrameLayout(latinIME)) as HorizontalScrollView? ?: return null
        assertEquals(UpdateChip.TAG, row.tag)
        val inner = row.getChildAt(0) as LinearLayout
        assertNotEquals(R.id.agent_command_chips, inner.id, "not a typed command row")
        return inner.getChildAt(0) as TextView
    }

    @Test fun shownWhileANewerBuildIsKnownAndGoneOnceItIsInstalled() {
        assertNull(chip(), "nothing known")
        known(22)
        assertEquals("⬆ Build 22 verfügbar", chip()!!.text.toString())
        assertEquals("Build 22 ist verfügbar. Öffnet die Seite Updates.", chip()!!.contentDescription)
        // a failed check later keeps it (only a successful one changes what is known)
        UpdateSettings(latinIME).status = UpdateStatus.Failed(helium314.keyboard.agent.updates.UpdateError.NETWORK)
        assertNotNull(chip())
        installed = 22
        assertNull(chip(), "installed: gone")
        installed = 23
        assertNull(chip())
        // a successful check without a newer build forgets it
        installed = 20
        UpdateSettings(latinIME).remember(null, 20)
        assertNull(chip())
    }

    @Test fun neverWhileLocked() {
        known(22)
        locked = true
        assertNull(chip())
        assertNull(UpdateChip.build(latinIME, Settings.getValues()))
        locked = false
        assertEquals(22L, UpdateChip.build(latinIME, Settings.getValues()))
        assertNull(UpdateChip.build(latinIME, null))
    }

    private fun startField(inputType: Int) {
        // the service reads the field from the shadow, as in the other keyboard tests
        helium314.keyboard.ShadowInputMethodService.text = ""
        helium314.keyboard.ShadowInputMethodService.selectionStart = 0
        helium314.keyboard.ShadowInputMethodService.selectionEnd = 0
        helium314.keyboard.ShadowInputMethodService.currentInputType = inputType
        helium314.keyboard.ShadowInputMethodService.currentImeOptions = 0
        val info = android.view.inputmethod.EditorInfo().apply { this.inputType = inputType }
        latinIME.onStartInput(info, false)
        latinIME.onStartInputView(info, false)
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    // the field shadow only here: tapOpensOnlyTheUpdatesPage needs Robolectric's own service shadow
    @Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class, helium314.keyboard.ShadowInputMethodService::class], qualifiers = "de")
    @Test fun neverInAPasswordField() {
        known(22)
        startField(android.text.InputType.TYPE_CLASS_TEXT)
        assertEquals(22L, UpdateChip.build(latinIME, Settings.getValues()), "a normal field shows it")
        assertNotNull(chip())
        startField(android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)
        assertNull(UpdateChip.build(latinIME, Settings.getValues()))
        assertNull(chip(), "no chip in a password field")
        startField(android.text.InputType.TYPE_CLASS_TEXT)
        assertNotNull(chip(), "and back in the next normal field")
    }

    @Test fun tapOpensOnlyTheUpdatesPage() {
        known(22)
        val view = chip()!!
        val service = shadowOf(latinIME)
        assertTrue(view.performClick())
        val started = assertNotNull(service.nextStartedActivity)
        assertEquals(helium314.keyboard.settings.SettingsActivity::class.java.name, started.component?.className)
        assertEquals(AgentPreferences.UPDATES_DESTINATION, started.getStringExtra(AgentPreferences.OPEN_SETTINGS_DESTINATION))
        assertNull(started.data)
        // locked by the time of the tap: nothing opens
        locked = true
        assertTrue(view.performClick())
        assertNull(service.nextStartedActivity)
        // showing and tapping the chip never checks or schedules anything
        assertEquals(0L, UpdateSettings(latinIME).checkedAt)
        assertNull(UpdateScheduler.pending(latinIME))
    }
}
