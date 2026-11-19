package dev.personal.secondhand.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataCompatTest {
    // shortened real metadata.json of release v0.67.0 (new form)
    private val newForm = """{
      "aihm_version": "0.1.dev1+ga62ece148", "model_id": "qwen3_4b_instruct_2507", "model_name": "Qwen3-4B-Instruct-2507",
      "runtime": "RUNTIME_GENIEX_QAIRT", "precision": "PRECISION_W4A16",
      "tool_versions": {"qairt": "2.51.0.260929181845"},
      "model_files": [
        {"filename": "part1_of_4.bin",
         "inputs": [{"name": "input_ids", "shape": [1, 1], "dtype": "DTYPE_INT32", "io_type": "IO_TYPE_TENSOR"}],
         "outputs": [{"name": "embedding", "shape": [1, 1, 2560], "dtype": "DTYPE_UINT16",
                      "quantization_parameters": {"scale": 7.1228637352760416e-06, "zero_point": 30985}, "io_type": "IO_TYPE_TENSOR"}]},
        {"filename": "part2_of_4.bin",
         "inputs": [{"name": "embedding", "shape": [1, 1, 2560], "dtype": "DTYPE_UINT16",
                     "quantization_parameters": {"scale": 7.1228637352760416e-06, "zero_point": 30985}, "io_type": "IO_TYPE_TENSOR"}],
         "outputs": [{"name": "logits", "shape": [1, 1, 151936], "dtype": "DTYPE_UINT16", "io_type": "IO_TYPE_TENSOR"}]}
      ],
      "supplementary_files": [{"filename": "genie_config.json", "description": "Genie SDK configuration."}],
      "chipset_attributes": {"name": "qualcomm-snapdragon-8-elite-for-galaxy", "soc_model": 69}
    }"""

    // the same content in the old form of release v0.66.0
    private val oldForm = """{
      "aihm_version": "0.1.dev1+ga62ece148", "model_id": "qwen3_4b_instruct_2507", "model_name": "Qwen3-4B-Instruct-2507",
      "runtime": "geniex_qairt", "precision": "w4a16",
      "tool_versions": {"qairt": "2.51.0.260929181845"},
      "model_files": {
        "part1_of_4.bin": {
          "inputs": {"input_ids": {"shape": [1, 1], "dtype": "int32"}},
          "outputs": {"embedding": {"shape": [1, 1, 2560], "dtype": "uint16",
                      "quantization_parameters": {"scale": 7.1228637352760416e-06, "zero_point": 30985}}}},
        "part2_of_4.bin": {
          "inputs": {"embedding": {"shape": [1, 1, 2560], "dtype": "uint16",
                     "quantization_parameters": {"scale": 7.1228637352760416e-06, "zero_point": 30985}}},
          "outputs": {"logits": {"shape": [1, 1, 151936], "dtype": "uint16"}}}
      },
      "supplementary_files": {"genie_config.json": "Genie SDK configuration."},
      "chipset_attributes": {"name": "qualcomm-snapdragon-8-elite-for-galaxy", "soc_model": 69}
    }"""

    @Test fun theNewFormIsRecognisedAndTheOldFormIsNot() {
        assertTrue(MetadataCompat.isNewFormat(newForm))
        assertFalse(MetadataCompat.isNewFormat(oldForm))
        assertFalse(MetadataCompat.isNewFormat(null))
        assertFalse(MetadataCompat.isNewFormat("not json"))
    }

    @Test fun conversionGivesExactlyTheOldForm() {
        val r = MetadataCompat.convert(newForm) as MetadataCompat.Result.Converted
        assertEquals(2, r.files)
        assertEquals(4, r.tensors)
        assertEquals(MiniJson.parse(oldForm), MiniJson.parse(r.json))
    }

    @Test fun integersStayIntegersSoALoaderNeverMeetsEightPointZero() {
        val r = MetadataCompat.convert(newForm) as MetadataCompat.Result.Converted
        assertTrue(r.json.contains("\"shape\":[1,1,2560]"))
        assertTrue(r.json.contains("\"zero_point\":30985"))
        assertFalse(r.json.contains("30985.0"))
        assertFalse(r.json.contains("2560.0"))
    }

    @Test fun theBundleFactsOfTheConvertedFileSaySObject() {
        val r = MetadataCompat.convert(newForm) as MetadataCompat.Result.Converted
        assertEquals(true, BundleFacts.extract(newForm, null).modelFilesIsList)
        val after = BundleFacts.extract(r.json, null)
        assertEquals(false, after.modelFilesIsList)
        assertEquals("2.51.0.260929181845", after.qairt) // the version of the programs is not changed
        assertEquals("qwen3_4b_instruct_2507", after.modelId)
    }

    @Test fun convertingTwiceIsANoOp() {
        val r = MetadataCompat.convert(newForm) as MetadataCompat.Result.Converted
        assertEquals(MetadataCompat.Result.AlreadyOldFormat, MetadataCompat.convert(r.json))
        assertEquals(MetadataCompat.Result.AlreadyOldFormat, MetadataCompat.convert(oldForm))
    }

    @Test fun brokenInputIsRefusedWithAReason() {
        assertTrue(MetadataCompat.convert("not json") is MetadataCompat.Result.Failed)
        assertTrue(MetadataCompat.convert("{}") is MetadataCompat.Result.Failed)
        val dup = """{"model_files":[{"filename":"a.bin","inputs":[],"outputs":[]},{"filename":"a.bin","inputs":[],"outputs":[]}]}"""
        val r = MetadataCompat.convert(dup) as MetadataCompat.Result.Failed
        assertTrue(r.reason.contains("doppelt"))
        val noName = """{"model_files":[{"inputs":[],"outputs":[]}]}"""
        assertTrue((MetadataCompat.convert(noName) as MetadataCompat.Result.Failed).reason.contains("filename"))
        val dupTensor = """{"model_files":[{"filename":"a.bin","inputs":[{"name":"x","shape":[1]},{"name":"x","shape":[1]}],"outputs":[]}]}"""
        assertTrue((MetadataCompat.convert(dupTensor) as MetadataCompat.Result.Failed).reason.contains("Tensorname doppelt"))
    }

    @Test fun jsonWriterRoundTripsStringsWithEscapes() {
        val v = mapOf("a" to listOf(1L, 2.5, true, null, "x\"y\\z\n\t"), "b" to emptyMap<String, Any?>())
        assertEquals(v, MiniJson.parse(MiniJson.write(v)))
        assertNotNull(MiniJson.parse(MiniJson.write(mapOf("k" to "\u0001"))))
    }

    @Test fun existingBundleFilesKeepTheirOtherKeys() {
        val r = MetadataCompat.convert(newForm) as MetadataCompat.Result.Converted
        val m = MiniJson.parse(r.json)
        assertEquals("0.1.dev1+ga62ece148", MiniJson.path(m, "aihm_version"))
        assertEquals(69L, MiniJson.path(m, "chipset_attributes", "soc_model"))
    }
}

/** The real metadata.json files of Qwen3-4B-Instruct-2507 (for_galaxy, GENIEX_QAIRT) of the two releases, read from Qualcomm's server on 2026-11-19. */
class RealMetadataCompatTest {
    private fun res(name: String): String =
        javaClass.getResourceAsStream("/metadata/$name")!!.bufferedReader().readText()

    private val v66 = res("qwen3_4b_instruct_2507-v0.66.0.json")
    private val v67 = res("qwen3_4b_instruct_2507-v0.67.0.json")

    @Test fun theRealFormatsAreWhatTheSdkSourceSays() {
        assertEquals(false, BundleFacts.extract(v66, null).modelFilesIsList) // SDK 0.10.0 reads this
        assertEquals(true, BundleFacts.extract(v67, null).modelFilesIsList) // SDK 0.10.0 refuses this
        assertEquals("2.50.0.260828221209", BundleFacts.extract(v66, null).qairt)
        assertEquals("2.51.0.260929181845", BundleFacts.extract(v67, null).qairt)
    }

    @Test fun theConvertedV067ModelFilesEqualTheRealV066ModelFilesExactly() {
        val converted = MiniJson.parse((MetadataCompat.convert(v67) as MetadataCompat.Result.Converted).json)
        val real66 = MiniJson.parse(v66)
        // every graph file, every tensor name, shape, dtype and quantization parameter is the same as in the object form of v0.66.0
        assertEquals(MiniJson.path(real66, "model_files"), MiniJson.path(converted, "model_files"))
        assertEquals(MiniJson.path(real66, "model_id"), MiniJson.path(converted, "model_id"))
        assertEquals(MiniJson.path(real66, "runtime"), MiniJson.path(converted, "runtime"))
        assertEquals(MiniJson.path(real66, "precision"), MiniJson.path(converted, "precision"))
        assertEquals(MiniJson.path(real66, "supplementary_files"), MiniJson.path(converted, "supplementary_files"))
    }
}
