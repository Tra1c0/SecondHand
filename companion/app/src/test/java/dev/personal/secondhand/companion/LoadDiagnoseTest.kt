package dev.personal.secondhand.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadDiagnoseTest {
    // the real files of the Qwen3-4B-Instruct-2507 bundle of release v0.67.0 (shortened)
    private val metadata = """{
        "aihm_version": "0.1.dev1+ga62ece148", "model_id": "qwen3_4b_instruct_2507", "model_name": "Qwen3-4B-Instruct-2507",
        "runtime": "RUNTIME_GENIEX_QAIRT", "precision": "PRECISION_W4A16",
        "tool_versions": {"qairt": "2.51.0.260929181845"},
        "model_files": {"part1_of_4.bin": {"inputs": {"a": [1, 2.5, -3e2]}}},
        "chipset_attributes": {"name": "qualcomm-snapdragon-8-elite-for-galaxy",
          "aliases": ["qualcomm-snapdragon-8-elite-for-galaxy", "sm8750-ac"], "htp_version": 79, "soc_model": 69, "supports_fp16": true}
    }"""
    private val htp = """{"devices": [{"soc_model": 69, "dsp_arch": "v79", "cores": [{"core_id": 0}]}], "memory": {"mem_type": "shared_buffer"}}"""

    @Test fun miniJsonReadsObjectsArraysNumbersStringsAndLiterals() {
        val v = MiniJson.parse("""{"a": [1, 2.5, -3e2, "x\"yA\n", true, false, null], "b": {}, "c": []}""")
        assertEquals(2.5, MiniJson.path(v, "a", 1))
        assertEquals(-300.0, MiniJson.path(v, "a", 2))
        assertEquals("x\"yA\n", MiniJson.path(v, "a", 3))
        assertEquals(true, MiniJson.path(v, "a", 4))
        assertNull(MiniJson.path(v, "a", 6))
        assertEquals(emptyMap<String, Any?>(), MiniJson.path(v, "b"))
        assertNull(MiniJson.path(v, "a", 99))
        assertNull(MiniJson.path(v, "zzz", "y"))
    }

    @Test fun miniJsonRefusesBrokenText() {
        assertNull(MiniJson.parse("{\"a\": "))
        assertNull(MiniJson.parse("{\"a\": 1} trailing"))
        assertNull(MiniJson.parse(""))
        assertNull(MiniJson.parse("not json"))
    }

    @Test fun bundleFactsComeFromMetadataAndTheHtpConfig() {
        val f = BundleFacts.extract(metadata, htp)
        assertEquals("2.51.0.260929181845", f.qairt)
        assertEquals("qwen3_4b_instruct_2507", f.modelId)
        assertEquals("RUNTIME_GENIEX_QAIRT", f.runtime)
        assertEquals("qualcomm-snapdragon-8-elite-for-galaxy", f.chipsetName)
        assertEquals(listOf("qualcomm-snapdragon-8-elite-for-galaxy", "sm8750-ac"), f.chipsetAliases)
        assertEquals(69, f.socModel)
        assertEquals("v79", f.dspArch)
    }

    @Test fun bundleFactsSurviveMissingOrBrokenFiles() {
        val f = BundleFacts.extract(null, "{broken")
        assertNull(f.qairt)
        assertNull(f.socModel)
        assertEquals(emptyList<String>(), f.chipsetAliases)
        assertEquals(69, BundleFacts.extract("{\"chipset_attributes\":{\"soc_model\":69}}", null).socModel)
    }

    @Test fun qairtVersionsParseFromBundleAndRuntimeStrings() {
        assertEquals(Triple(2, 51, 0), QairtVersion.parse("2.51.0.260929181845"))
        assertEquals(Triple(2, 50, 40), QairtVersion.parse("v2.50.40.260831140417"))
        assertEquals("2.50", QairtVersion.majorMinor("2.50.0.260828221209"))
        assertNull(QairtVersion.parse("unknown"))
        assertNull(QairtVersion.parse(null))
    }

    @Test fun aBundleNewerThanTheRuntimeIsFlagged() {
        assertEquals(Compat.BUNDLE_NEWER, QairtVersion.compat("2.51.0.260929181845", "v2.50.40.260831140417"))
        assertEquals(Compat.BUNDLE_SAME_OR_OLDER, QairtVersion.compat("2.50.0.260828221209", "v2.50.40.260831140417"))
        assertEquals(Compat.BUNDLE_SAME_OR_OLDER, QairtVersion.compat("2.45.0.260326154327", "v2.50.40.260831140417"))
        assertEquals(Compat.BUNDLE_NEWER, QairtVersion.compat("2.50.41.1", "v2.50.40.260831140417"))
        assertEquals(Compat.UNKNOWN, QairtVersion.compat(null, "v2.50.40.1"))
        assertEquals(Compat.UNKNOWN, QairtVersion.compat("2.51.0.1", null))
    }

    @Test fun theRuntimeVersionIsFoundInsideABinary() {
        val bytes = ByteArray(40) + "some\u0000text v2.50.40.260831140417 more".toByteArray(Charsets.ISO_8859_1) + ByteArray(9)
        assertEquals("v2.50.40.260831140417", QairtVersion.findInBinary(bytes))
        assertNull(QairtVersion.findInBinary(ByteArray(100)))
    }

    private val forbidden = listOf("Spaziergang", "Berger")

    @Test fun logFilterKeepsNpuLinesAndWarningsAndDropsTheRest() {
        val lines = listOf(
            "11-19 10:00:00.000  100  100 I chatty  : plain info about something",
            "11-19 10:00:00.001  100  100 E libgeniex: [ERROR] \"Device Creation failure\"",
            "11-19 10:00:00.002  100  100 I QnnHtp  : created backend",
            "11-19 10:00:00.003  100  100 W SomeTag : a warning of any tag",
            "11-19 10:00:00.004  100  100 D other   : debug without keyword",
        )
        val out = LogFilter.relevant(lines, forbidden, 100)
        assertEquals(3, out.size)
        assertTrue(out.any { "Device Creation failure" in it })
        assertTrue(out.any { "QnnHtp" in it })
        assertTrue(out.any { "a warning of any tag" in it })
        assertFalse(out.any { "plain info" in it })
    }

    @Test fun logFilterDropsLinesWithFragmentsOfTheFixedTextsEvenIfTheyMatch() {
        val lines = listOf(
            "11-19 10:00:00.000  100  100 D GenieXSdk: prompt Wir haben einen langen Spaziergang gemacht",
            "11-19 10:00:00.001  100  100 E GenieXSdk: Sehr geehrte Frau BERGER",
            "11-19 10:00:00.002  100  100 E GenieXSdk: load failed",
        )
        assertEquals(listOf("11-19 10:00:00.002  100  100 E GenieXSdk: load failed"), LogFilter.relevant(lines, forbidden, 100))
    }

    @Test fun logFilterCutsLongLinesAndKeepsTheLastOnes() {
        val long = "11-19 10:00:00.000  100  100 E QnnHtp: " + "x".repeat(500)
        val cut = LogFilter.relevant(listOf(long), forbidden, 10).single()
        assertEquals(303, cut.length)
        assertTrue(cut.endsWith("..."))
        val many = (1..300).map { "11-19 10:00:00.000  100  100 E QnnHtp: line $it" }
        val last = LogFilter.relevant(many, forbidden, 150)
        assertEquals(150, last.size)
        assertTrue(last.last().endsWith("line 300"))
        assertTrue(last.first().endsWith("line 151"))
    }

    @Test fun logAfterMarkerCutsEverythingUpToTheLastMarker() {
        val lines = listOf("a", "x MARK1 y", "b", "x MARK1 z", "c")
        assertEquals(listOf("c"), LogFilter.afterMarker(lines, "MARK1"))
        assertEquals(lines, LogFilter.afterMarker(lines, "OTHER"))
    }

    @Test fun fixedTextFragmentsAreForbiddenInTheLog() {
        val f = FixedTests.forbiddenLogFragments()
        assertTrue(f.contains("Spatziergang") && f.contains("Kundendienst"))
        assertNotNull(f.firstOrNull { it.startsWith("Rechtschreibung") })
    }

    @Test fun bundleLinesNameTheHypothesisAndTheVersionVerdict() {
        val i = BundleInspection(
            "/data/x", "qairt",
            listOf(BundleFile("part1_of_4.bin", 100), BundleFile("metadata.json", 5)),
            mapOf("htp_backend_ext_config.json" to htp),
        )
        val facts = BundleFacts.extract(metadata, htp)
        val newer = DiagnoseFormatter.bundleLines(i, facts, "v2.50.40.260831140417").joinToString("\n")
        assertTrue(newer.contains("H5") && newer.contains("H1"))
        assertTrue(newer.contains("Paket gebaut mit QAIRT: 2.51.0.260929181845"))
        assertTrue(newer.contains("Das Paket ist NEUER als die Laufzeit"))
        assertTrue(newer.contains("SoC-Nummer 69, DSP-Architektur v79"))
        assertTrue(newer.contains("part1_of_4.bin  100"))
        val unknown = DiagnoseFormatter.bundleLines(i, facts, null).joinToString("\n")
        assertTrue(unknown.contains("Versionsvergleich nicht möglich"))
    }
}
