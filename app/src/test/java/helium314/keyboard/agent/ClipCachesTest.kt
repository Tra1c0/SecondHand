// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.os.Looper
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import dev.agentkeyboard.core.clip.ClipKind
import java.time.Duration
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Review of stage 64b (M-1): the caches of the live clip never outlive the vault's promise about a secret. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ClipCachesTest {
    private val secret = "correct horse battery staple 7491"

    @Before @After fun reset() { ClipVault.clear(); ClipVault.clock = { System.currentTimeMillis() }; ClipVault.currentClip = null }

    private fun fill() {
        ClipVault.currentClip = { Triple(secret, System.currentTimeMillis(), false) }
        ClipVault.freshest(androidx.test.core.app.ApplicationProvider.getApplicationContext(), ClipKind.OTP, 60_000L)
        ClipVault.classifyLive(secret)
    }

    @Test fun theCachesHoldTheClipOnlyBriefly() {
        fill()
        assertTrue(ClipVault.holdsText(secret), "filled")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ClipVault.CACHE_MILLIS + 100))
        assertFalse(ClipVault.holdsText(secret), "dropped after ${ClipVault.CACHE_MILLIS} ms")
    }

    @Test fun aLockEventDropsThem() {
        fill()
        LockState.drop()
        assertFalse(ClipVault.holdsText(secret))
    }

    @Test fun clearingTheVaultDropsThem() {
        fill()
        ClipVault.clear()
        assertFalse(ClipVault.holdsText(secret))
    }

    @Test fun smartPasteForgetsWhatItRecognised() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        SmartPasteChips.actions(ctx, "Anna Beispiel +49 30 1234567", System.currentTimeMillis(), false, null)
        assertTrue(SmartPasteChips.hasCached)
        ClipVault.forgetCaches()
        assertFalse(SmartPasteChips.hasCached)
    }
}
