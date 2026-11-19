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
        assertTrue(PullPlanner.CANDIDATES.all { it.release == "v0.66.0" && it.expectedQairt == "2.50" })
        assertNotEquals(instruct.id, qwen3.id)
        assertEquals("local/qwen3-4b-instruct-2507-v066|v0.66.0", instruct.id)
    }

    @Test fun theZipsAreTheExactFilesOfReleaseV066() {
        assertTrue(instruct.zipUrl.contains("/qwen3_4b_instruct_2507/releases/v0.66.0/") && instruct.zipUrl.endsWith("-qualcomm_snapdragon_8_elite_for_galaxy.zip"))
        assertTrue(qwen3.zipUrl.contains("/qwen3_4b/releases/v0.66.0/"))
        assertEquals(2_659_737_495L, instruct.zipBytes)
        assertEquals(2_660_599_539L, qwen3.zipBytes)
        assertTrue(instruct.zipUrl.startsWith("https://qaihub-public-assets.s3.us-west-2.amazonaws.com/"))
        assertEquals("qwen3_4b_instruct_2507-geniex_qairt-w4a16-qualcomm_snapdragon_8_elite_for_galaxy.zip", instruct.zipFileName)
    }

    @Test fun storeNamesAreLocalAndTheEarlierAppsNamesAreKnownForCleanup() {
        assertTrue(PullPlanner.CANDIDATES.all { it.modelName.startsWith("local/") })
        assertTrue(PullPlanner.ALL_MODEL_NAMES.containsAll(listOf("ai-hub-models/Qwen3-4B", "ai-hub-models/Qwen3-4B-Instruct-2507")))
        assertEquals(PullPlanner.CANDIDATES.map { it.modelName }, PullPlanner.ALL_MODEL_NAMES.take(2)) // candidates preferred
    }

    @Test fun aBundleBelongsToACandidateByStoreNameAndQairtVersion() {
        assertEquals(instruct, PullPlanner.matching("local/qwen3-4b-instruct-2507-v066", "2.50.0.260828221209"))
        assertEquals(qwen3, PullPlanner.matching("local/qwen3-4b-v066", "2.50.0.260828221209"))
        // the bundle that failed on the phone (QAIRT 2.51, an AI Hub name) is none of the candidates
        assertNull(PullPlanner.matching("ai-hub-models/Qwen3-4B-Instruct-2507", "2.51.0.260929181845"))
        assertNull(PullPlanner.matching("local/qwen3-4b-instruct-2507-v066", "2.51.0.260929181845"))
        assertNull(PullPlanner.matching(null, "2.50.0.1"))
    }

    @Test fun downloadFailuresAreAdvisedByKind() {
        val notFound = PullPlanner.adviceAfterDownloadFailure(DownloadException.Kind.HTTP_STATUS, 404)
        assertTrue(notFound.giveUp && !notFound.repeatUseful)
        assertTrue(PullPlanner.adviceAfterDownloadFailure(DownloadException.Kind.HTTP_STATUS, 403).giveUp)
        assertFalse(PullPlanner.adviceAfterDownloadFailure(DownloadException.Kind.HTTP_STATUS, 503).giveUp)
        for (k in listOf(DownloadException.Kind.NETWORK, DownloadException.Kind.NO_SPACE, DownloadException.Kind.SIZE_MISMATCH, DownloadException.Kind.ZIP_INVALID)) {
            val a = PullPlanner.adviceAfterDownloadFailure(k, null)
            assertFalse("$k", a.giveUp)
            assertTrue("$k", a.repeatUseful)
        }
        assertTrue(PullPlanner.adviceAfterDownloadFailure(DownloadException.Kind.CANCELLED, null).cancelled)
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
