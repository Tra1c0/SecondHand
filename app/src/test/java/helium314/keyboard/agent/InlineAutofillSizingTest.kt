// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Stage 79 (W-a): the width the inline autofill request asks for follows the strip and the density. */
class InlineAutofillSizingTest {
    @Test fun phoneInPortraitUsesTheStripWidthMinusTheStripsOwnKeys() {
        // 1080 px at density 2.75: 66 dp are 182 px (rounded)
        assertEquals(1080 - 182, InlineAutofillSizing.maxWidthPx(1080, 1080, 2.75f))
    }

    @Test fun smallPhoneGetsLessThanTheOldFixed740() {
        val max = InlineAutofillSizing.maxWidthPx(720, 720, 2f)
        assertEquals(720 - 132, max)
        assertTrue(max < 740)
    }

    @Test fun tabletGetsMoreThanTheOldFixed740() {
        val max = InlineAutofillSizing.maxWidthPx(2560, 2560, 2f)
        assertEquals(2560 - 132, max)
        assertTrue(max > 740)
    }

    @Test fun landscapeIsWiderThanPortraitOnTheSameDevice() {
        val portrait = InlineAutofillSizing.maxWidthPx(1080, 1080, 2.75f)
        val landscape = InlineAutofillSizing.maxWidthPx(2400, 2400, 2.75f)
        assertTrue(landscape > portrait)
    }

    @Test fun higherDensityReservesMorePixels() {
        val low = InlineAutofillSizing.maxWidthPx(1000, 1000, 1f)
        val high = InlineAutofillSizing.maxWidthPx(1000, 1000, 3f)
        assertEquals(1000 - 66, low)
        assertEquals(1000 - 198, high)
    }

    @Test fun stripNotLaidOutYetFallsBackToTheDisplayWidth() {
        assertEquals(1080 - 182, InlineAutofillSizing.maxWidthPx(0, 1080, 2.75f))
    }

    @Test fun theStripWidthWinsOverTheDisplayWidth() {
        // a floating or narrowed keyboard
        assertEquals(800 - 182, InlineAutofillSizing.maxWidthPx(800, 1080, 2.75f))
    }

    @Test fun nothingKnownFallsBackToAFixedWidthInDp() {
        assertEquals(Math.round(InlineAutofillSizing.FALLBACK_MAX_DP * 2.5f), InlineAutofillSizing.maxWidthPx(0, 0, 2.5f))
        // a bad density counts as 1
        assertEquals(InlineAutofillSizing.FALLBACK_MAX_DP, InlineAutofillSizing.maxWidthPx(0, 0, 0f))
    }

    @Test fun theMaximumIsNeverBelowTheMinimum() {
        for (density in listOf(0f, 1f, 2.75f, 4f)) {
            for (strip in listOf(-5, 0, 1, 50, 120, 300)) {
                val min = InlineAutofillSizing.minWidthPx(density)
                assertTrue(InlineAutofillSizing.maxWidthPx(strip, 60, density) >= min, "strip=$strip density=$density")
            }
        }
    }

    @Test fun theMinimumScalesWithDensityAndIsReasonable() {
        assertEquals(40, InlineAutofillSizing.minWidthPx(1f))
        assertEquals(110, InlineAutofillSizing.minWidthPx(2.75f))
        assertEquals(40, InlineAutofillSizing.minWidthPx(-1f))
    }
}
