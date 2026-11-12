// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Activity
import android.graphics.Color
import android.provider.Settings
import android.widget.FrameLayout
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.test.*

/** The waiting dots of a typed AI request in the strip (Variante A). */
@RunWith(RobolectricTestRunner::class)
class LoadingDotsViewTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    @After fun tearDown() {
        Settings.Global.putFloat(activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    private fun attach(): Pair<FrameLayout, LoadingDotsView> {
        val root = FrameLayout(activity)
        activity.setContentView(root)
        val dots = LoadingDotsView(activity, Color.BLUE)
        root.addView(dots)
        return root to dots
    }

    @Test fun animatesWhileAttachedAndStopsWhenDetached() {
        Settings.Global.putFloat(activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val dots = LoadingDotsView(activity, Color.BLUE)
        assertFalse(dots.isAnimating) // not before it is shown
        val (root, shown) = attach()
        assertTrue(shown.isAnimating)
        root.removeView(shown)
        assertFalse(shown.isAnimating)
        assertFalse(dots.isAnimating)
    }

    @Test fun standsStillWithAnimationsOff() {
        Settings.Global.putFloat(activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val (_, dots) = attach()
        assertFalse(dots.isAnimating)
        // all three dots the same, whatever the phase
        val levels = (0 until LoadingDotsView.DOTS).map { dots.wave(it, 0.2f) }
        assertEquals(1, levels.toSet().size)
        assertEquals(levels, (0 until LoadingDotsView.DOTS).map { dots.wave(it, 0.7f) })
    }

    @Test fun theDotsRiseOneAfterAnotherInAWave() {
        Settings.Global.putFloat(activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val (_, dots) = attach()
        assertEquals(1200L, LoadingDotsView.CYCLE_MS)
        assertEquals(150L, LoadingDotsView.STAGGER_MS)
        // a quarter cycle in: the first dot is at its top, the next ones follow 150 ms later each
        val quarter = 0.25f
        assertEquals(1f, dots.wave(0, quarter), 0.001f)
        assertTrue(dots.wave(1, quarter) < 1f && dots.wave(1, quarter + 0.125f) > 0.999f)
        assertTrue(dots.wave(2, quarter + 0.25f) > 0.999f)
        // and rest during the second half of their cycle
        assertEquals(0f, dots.wave(0, 0.75f))
        assertTrue((0..100).all { step -> dots.wave(1, step / 100f) in 0f..1f })
    }
}
