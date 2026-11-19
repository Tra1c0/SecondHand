package dev.personal.secondhand.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PullPlannerTest {
    @Test fun firstTapUsesTheDocumentedId() {
        assertEquals(PullPlanner.Offer.Primary, PullPlanner.nextOffer(emptySet()))
    }

    @Test fun afterSm8750FailedTheGalaxyBundleIsOfferedNotPulled() {
        assertEquals(PullPlanner.Offer.GalaxyBundle, PullPlanner.nextOffer(setOf("SM8750")))
    }

    @Test fun afterBothFailedNothingIsLeft() {
        assertEquals(PullPlanner.Offer.Exhausted, PullPlanner.nextOffer(setOf("SM8750", "SM8750-AC")))
    }

    @Test fun notFoundOnTheDocumentedIdOffersTheGalaxyBundle() {
        assertEquals(
            PullPlanner.AfterFailure.OfferGalaxyBundle,
            PullPlanner.afterPullFailure("SM8750", 5, "Requested chipset not available for this model"),
        )
        assertEquals(
            PullPlanner.AfterFailure.OfferGalaxyBundle,
            PullPlanner.afterPullFailure("SM8750", 5, "No pre-compiled assets available for ai-hub-models/Qwen3-4B"),
        )
        assertEquals(PullPlanner.AfterFailure.OfferGalaxyBundle, PullPlanner.afterPullFailure("SM8750", 1, "HTTP status 403 for url"))
    }

    @Test fun networkAndStorageErrorsDoNotOfferAnything() {
        assertEquals(PullPlanner.AfterFailure.ShowError, PullPlanner.afterPullFailure("SM8750", 9, "connection reset by peer"))
        assertEquals(PullPlanner.AfterFailure.ShowError, PullPlanner.afterPullFailure("SM8750", 9, "No space left on device"))
        assertEquals(PullPlanner.AfterFailure.ShowError, PullPlanner.afterPullFailure("SM8750", 9, null))
        assertEquals(PullPlanner.AfterFailure.ShowError, PullPlanner.afterPullFailure("SM8750", 9, "timed out after 3040 ms"))
    }

    @Test fun aFailureOfTheGalaxyBundleOffersNothingFurther() {
        assertEquals(
            PullPlanner.AfterFailure.ShowError,
            PullPlanner.afterPullFailure("SM8750-AC", 5, "Requested chipset not available for this model"),
        )
    }

    @Test fun looksLikeChipsetUnavailableIsCaseInsensitive() {
        assertTrue(PullPlanner.looksLikeChipsetUnavailable("REQUESTED CHIPSET NOT AVAILABLE FOR THIS MODEL"))
        assertFalse(PullPlanner.looksLikeChipsetUnavailable("   "))
        assertFalse(PullPlanner.looksLikeChipsetUnavailable("size 14031 bytes"))
    }
}
