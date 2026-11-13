// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.App
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.checkVersionUpgrade
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

/**
 * Build 12 fix (P84): an update of the fork must not run HeliBoard's old version migrations. They
 * once reset the key gaps of the owner's own design to 1.75 on every update.
 */
@RunWith(RobolectricTestRunner::class)
class ForkUpgradeTest {
    private val context = ApplicationProvider.getApplicationContext<App>()

    @Before fun clearPrefs() { context.prefs().edit(commit = true) { clear() } }

    @Test fun anUpdateKeepsTheKeyGapsOfAnOwnDesign() {
        val prefs = context.prefs()
        assertNotEquals(11, BuildConfig.VERSION_CODE) // else nothing would be upgraded here
        prefs.edit(commit = true) {
            putInt(Settings.PREF_VERSION_CODE, 11) // the build before, a fork build number
            putFloat(MyDesigns.GAP_KEY, 0.6f) // the design's own narrow gaps
        }
        checkVersionUpgrade(context)
        assertEquals(0.6f, prefs.getFloat(MyDesigns.GAP_KEY, -1f))
        assertEquals(BuildConfig.VERSION_CODE, prefs.getInt(Settings.PREF_VERSION_CODE, 0))
        assertFalse(prefs.contains("narrow_key_gaps"))
    }

    @Test fun theFirstUpdateAfterBuild12GivesTheSelectedDesignItsGapsBack() {
        val prefs = context.prefs()
        val design = MyDesigns.save(context, MyDesigns.newDesign(context, "Mein Design").copy(keyGap = 0.5f, keyHeight = 1.2f))
        MyDesigns.select(context, design)
        prefs.edit(commit = true) {
            putInt(Settings.PREF_VERSION_CODE, 12)
            putFloat(MyDesigns.GAP_KEY, 1.75f) // what the old migration left behind
            putFloat(MyDesigns.HEIGHT_KEY, 1f)
        }
        checkVersionUpgrade(context)
        assertEquals(0.5f, prefs.getFloat(MyDesigns.GAP_KEY, -1f))
        assertEquals(1.2f, prefs.getFloat(MyDesigns.HEIGHT_KEY, -1f))
        // later updates leave a gap changed by hand alone
        prefs.edit(commit = true) { putInt(Settings.PREF_VERSION_CODE, 13); putFloat(MyDesigns.GAP_KEY, 0.8f) }
        checkVersionUpgrade(context)
        assertEquals(0.8f, prefs.getFloat(MyDesigns.GAP_KEY, -1f))
    }
}
