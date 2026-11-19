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

    @Test fun theSmallModelsComeFirstThenTheTwo4BOnes_allFromTheReleaseBuiltWithTheRuntimesQairt() {
        assertEquals(listOf(PullPlanner.QWEN3_1_7B_V66, PullPlanner.QWEN3_0_6B_V66, instruct, qwen3), PullPlanner.CANDIDATES)
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
        assertEquals(PullPlanner.CANDIDATES.map { it.modelName }, PullPlanner.ALL_MODEL_NAMES.take(PullPlanner.CANDIDATES.size)) // candidates preferred
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
            assertTrue(it.sizeText.contains(" GB"))
            assertTrue(it.difference.contains("Es kann scheitern"))
        }
        assertTrue(qwen3.difference.contains("ANDERES Modell"))
    }

    @Test fun currentIsTheFirstCandidateNotGivenUp() {
        assertEquals(PullPlanner.QWEN3_1_7B_V66, PullPlanner.current(emptySet()))
        assertEquals(qwen3, PullPlanner.current(PullPlanner.CANDIDATES.map { it.id }.toSet() - qwen3.id))
        assertNull(PullPlanner.current(PullPlanner.CANDIDATES.map { it.id }.toSet()))
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

    @Test fun theSmallModelsAreTheExactFilesOfReleaseV066() {
        val s17 = PullPlanner.QWEN3_1_7B_V66
        val s06 = PullPlanner.QWEN3_0_6B_V66
        assertTrue(s17.zipUrl.contains("/qwen3_1_7b/releases/v0.66.0/qwen3_1_7b-geniex_qairt-w4a16-qualcomm_snapdragon_8_elite_for_galaxy.zip"))
        assertTrue(s06.zipUrl.contains("/qwen3_0_6b/releases/v0.66.0/"))
        assertEquals(1_534_158_813L, s17.zipBytes)
        assertEquals(640_473_082L, s06.zipBytes)
        assertEquals("local/qwen3-1-7b-v066", s17.modelName)
        assertTrue(s17.zipBytes < instruct.zipBytes / 1.5)
    }

    @Test fun theNextDownloadIsTheSmallOneAndTheFallbackFollowsWhenItFails() {
        assertEquals(PullPlanner.QWEN3_1_7B_V66, PullPlanner.current(emptySet()))
        assertEquals(PullPlanner.QWEN3_0_6B_V66, PullPlanner.current(setOf(PullPlanner.QWEN3_1_7B_V66.id)))
        assertEquals(instruct, PullPlanner.current(setOf(PullPlanner.QWEN3_1_7B_V66.id, PullPlanner.QWEN3_0_6B_V66.id)))
    }

    @Test fun theStoreNamesAreDifferentSoTheBundlesCoexist() {
        assertEquals(PullPlanner.CANDIDATES.size, PullPlanner.CANDIDATES.map { it.modelName }.toSet().size)
        assertEquals(PullPlanner.CANDIDATES.map { it.modelName }, PullPlanner.ALL_MODEL_NAMES.take(4))
    }
}
