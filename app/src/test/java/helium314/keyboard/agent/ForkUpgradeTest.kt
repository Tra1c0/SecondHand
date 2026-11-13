// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.App
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.checkVersionUpgrade
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.createPrefKeyForBooleanSettings
import helium314.keyboard.latin.utils.prefs
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Build 12 fix (P84): an update of the fork must not run HeliBoard's old version migrations. They
 * reset the key gaps of the owner's own design to 1.75 on every update.
 */
@RunWith(RobolectricTestRunner::class)
class ForkUpgradeTest {
    private val context = ApplicationProvider.getApplicationContext<App>()
    private val prefs get() = context.prefs()
    private fun gapKey(index: Int) = createPrefKeyForBooleanSettings(Settings.PREF_KEY_GAP_SCALE_PREFIX, index, 2)

    @Before fun setUp() {
        prefs.edit(commit = true) { clear() }
        // the version stored before the update must differ from the running one, else nothing upgrades
        assertTrue(BuildConfig.VERSION_CODE !in listOf(12, 13))
    }

    private fun ownDesign(gap: Float) = MyDesigns.save(context, MyDesigns.newDesign(context, "Mein Design").copy(keyGap = gap))

    @Test fun aLaterUpdateLeavesEverySettingOfTheMigrationsAlone() {
        MyDesigns.select(context, ownDesign(0.6f))
        prefs.edit(commit = true) {
            putInt(Settings.PREF_VERSION_CODE, 13) // after the fix: no repair any more
            putFloat(gapKey(1), 1.3f)
            putBoolean(Settings.PREF_SUGGEST_PUNCTUATION, true)
            putBoolean(Settings.PREF_BIGRAM_PREDICTIONS, true) // <= 3201 forced punctuation to !bigram
        }
        checkVersionUpgrade(context)
        assertEquals(0.6f, prefs.getFloat(gapKey(0), -1f))
        assertEquals(1.3f, prefs.getFloat(gapKey(1), -1f))
        assertTrue(prefs.getBoolean(Settings.PREF_SUGGEST_PUNCTUATION, false))
        assertEquals(BuildConfig.VERSION_CODE, prefs.getInt(Settings.PREF_VERSION_CODE, 0))
    }

    @Test fun theFirstUpdateAfterBuild12RepairsOnlyTheGapsTheMigrationSet() {
        MyDesigns.select(context, ownDesign(0.5f))
        prefs.edit(commit = true) {
            putInt(Settings.PREF_VERSION_CODE, 12)
            putFloat(gapKey(0), 1.75f) // what the old migration left behind
            putFloat(gapKey(1), 1.1f)
            putFloat(gapKey(2), 1.75f)
            putFloat(gapKey(3), 1.4f) // changed by hand since then
            putFloat(MyDesigns.HEIGHT_KEY, 1.3f) // the height was never touched
        }
        checkVersionUpgrade(context)
        assertEquals(0.5f, prefs.getFloat(gapKey(0), -1f)) // the design's own gap
        assertFalse(prefs.contains(gapKey(1))) // back to the default
        assertFalse(prefs.contains(gapKey(2)))
        assertEquals(1.4f, prefs.getFloat(gapKey(3), -1f))
        assertEquals(1.3f, prefs.getFloat(MyDesigns.HEIGHT_KEY, -1f))
    }

    @Test fun withoutAnOwnDesignTheMigratedGapGoesBackToTheDefault() {
        prefs.edit(commit = true) {
            putInt(Settings.PREF_VERSION_CODE, 11)
            putFloat(gapKey(0), 1.75f)
        }
        checkVersionUpgrade(context)
        assertFalse(prefs.contains(gapKey(0)))
    }

    @Test fun aGapChangedByHandSurvivesTheRepair() {
        MyDesigns.select(context, ownDesign(0.5f))
        prefs.edit(commit = true) {
            putInt(Settings.PREF_VERSION_CODE, 12)
            putFloat(gapKey(0), 0.8f)
        }
        checkVersionUpgrade(context)
        assertEquals(0.8f, prefs.getFloat(gapKey(0), -1f))
    }
}
