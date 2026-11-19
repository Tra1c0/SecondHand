package dev.personal.secondhand.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PullPlannerTest {
    private val instruct = PullPlanner.INSTRUCT_V66
    private val qwen3 = PullPlanner.QWEN3_V66

    @Test fun theOrderIsInstructFirstThenQwen3_bothFromTheReleaseBuiltWithTheRuntimesQairt() {
        assertEquals(listOf(instruct, qwen3), PullPlanner.CANDIDATES)
        assertEquals("ai-hub-models/Qwen3-4B-Instruct-2507", instruct.modelName)
        assertEquals("ai-hub-models/Qwen3-4B", qwen3.modelName)
        // Qualcomm's list knows "sm8750" as no alias, only "sm8750-ac": plain SM8750 is no candidate
        assertTrue(PullPlanner.CANDIDATES.all { it.chipset == "SM8750-AC" })
        // the newest release (v0.67.0, QAIRT 2.51) did not load; v0.66.0 was built with QAIRT 2.50.0, the runtime is 2.50.40
        assertTrue(PullPlanner.CANDIDATES.all { it.release == "v0.66.0" && it.expectedQairt == "2.50" })
        assertNotEquals(instruct.id, qwen3.id)
        assertEquals("ai-hub-models/Qwen3-4B-Instruct-2507|SM8750-AC|v0.66.0", instruct.id)
    }

    @Test fun aBundleBelongsToACandidateByModelAndQairtVersion() {
        assertEquals(instruct, PullPlanner.matching("ai-hub-models/Qwen3-4B-Instruct-2507", "2.50.0.260828221209"))
        assertEquals(qwen3, PullPlanner.matching("ai-hub-models/Qwen3-4B", "2.50.0.260828221209"))
        // the bundle that failed on the phone (QAIRT 2.51) is none of the candidates
        assertNull(PullPlanner.matching("ai-hub-models/Qwen3-4B-Instruct-2507", "2.51.0.260929181845"))
        assertNull(PullPlanner.matching("ai-hub-models/Qwen3-4B-Instruct-2507", null))
        assertNull(PullPlanner.matching(null, "2.50.0.1"))
    }

    @Test fun everyCandidateDialogTextSaysSizeAndThatItMayFail() {
        PullPlanner.CANDIDATES.forEach {
            assertTrue(it.sizeText.contains("2,7 GB"))
            assertTrue(it.difference.contains("Es kann scheitern"))
        }
        assertTrue(qwen3.difference.contains("ANDERES Modell"))
    }

    @Test fun currentIsTheFirstCandidateNotGivenUp() {
        assertEquals(instruct, PullPlanner.current(emptySet()))
        assertEquals(qwen3, PullPlanner.current(setOf(instruct.id)))
        assertNull(PullPlanner.current(setOf(instruct.id, qwen3.id)))
    }

    @Test fun nextSkipsGivenUpOnesAndEnds() {
        assertEquals(qwen3, PullPlanner.next(instruct, emptySet()))
        assertNull(PullPlanner.next(instruct, setOf(qwen3.id)))
        assertNull(PullPlanner.next(qwen3, emptySet()))
    }

    @Test fun chipsetUnavailableCodeMeansGiveUpAndNoRepeat() {
        val a = PullPlanner.adviceAfterPullFailure(-100015, "geniex_model_pull failed (rc=-100015)")
        assertTrue(a.giveUp)
        assertFalse(a.repeatUseful)
        assertFalse(a.cancelled)
        assertTrue(PullPlanner.adviceAfterPullFailure(-100010, null).giveUp)
    }

    @Test fun networkAndServerErrorsAllowARepeatAndDoNotGiveUp() {
        for (code in listOf(-100005, -100011, -100012, -100017, -100000)) {
            val a = PullPlanner.adviceAfterPullFailure(code, "x")
            assertFalse("code $code", a.giveUp)
            assertTrue("code $code", a.repeatUseful)
        }
    }

    @Test fun invalidBundleAsksToDeleteFirst() {
        val a = PullPlanner.adviceAfterPullFailure(-100203, "bad")
        assertTrue(a.deleteFirst)
        assertFalse(a.giveUp)
    }

    @Test fun cancelledByTheUserIsNotAnError() {
        val a = PullPlanner.adviceAfterPullFailure(-100006, "cancelled")
        assertTrue(a.cancelled)
        assertFalse(a.giveUp)
    }

    @Test fun unknownCodeWithNotFoundTextStillGivesUp() {
        assertTrue(PullPlanner.adviceAfterPullFailure(-7, "Requested chipset not available for this model").giveUp)
        assertTrue(PullPlanner.adviceAfterPullFailure(null, "HTTP status 403 for url").giveUp)
        assertFalse(PullPlanner.adviceAfterPullFailure(null, "connection reset by peer").giveUp)
        assertFalse(PullPlanner.adviceAfterPullFailure(null, null).giveUp)
    }

    @Test fun sdkCodeNamesMatchTheGenieXSource() {
        assertEquals("COMMON_CHIPSET_UNAVAILABLE", SdkCodes.name(-100015))
        assertEquals("COMMON_CANCELLED", SdkCodes.name(-100006))
        assertEquals("COMMON_INSUFFICIENT_DISK_SPACE", SdkCodes.name(-100017))
        assertNull(SdkCodes.name(-1))
    }

    @Test fun looksLikeChipsetUnavailableIsCaseInsensitive() {
        assertTrue(PullPlanner.looksLikeChipsetUnavailable("REQUESTED CHIPSET NOT AVAILABLE FOR THIS MODEL"))
        assertFalse(PullPlanner.looksLikeChipsetUnavailable("   "))
        assertFalse(PullPlanner.looksLikeChipsetUnavailable("size 14031 bytes"))
    }
}
