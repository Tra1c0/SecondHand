// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Activity
import android.view.WindowManager
import helium314.keyboard.settings.screens.SecureWindow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** P144: nested protected pages (AI area -> AI provider page) must not drop FLAG_SECURE during the nav animation. */
@RunWith(RobolectricTestRunner::class)
class SecureWindowTest {
    private fun secure(activity: Activity) = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    @Test fun theOldPageLeavingDoesNotUnprotectTheNewPage() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        assertFalse(secure(activity))
        SecureWindow.acquire(activity.window) // AI area page
        SecureWindow.acquire(activity.window) // AI provider page starts while the area page is still composed
        SecureWindow.release(activity.window) // area page disposed after the transition
        assertTrue(secure(activity), "the key entry page must stay protected")
        SecureWindow.release(activity.window)
        assertFalse(secure(activity), "flag falls with the last holder")
    }

    @Test fun aFlagSetBeforeTheFirstHolderStays() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        SecureWindow.acquire(activity.window)
        SecureWindow.acquire(activity.window)
        SecureWindow.release(activity.window)
        SecureWindow.release(activity.window)
        assertTrue(secure(activity))
    }

    @Test fun anUnbalancedReleaseChangesNothing() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        SecureWindow.release(activity.window)
        assertFalse(secure(activity))
        SecureWindow.acquire(activity.window)
        assertEquals(true, secure(activity))
        SecureWindow.release(activity.window)
        SecureWindow.release(activity.window)
        assertFalse(secure(activity))
    }
}
