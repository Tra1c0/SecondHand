package dev.personal.secondhand.companion

// Stage 80d. The AI Hub bundles of release v0.67.0 and newer describe their graphs in metadata.json with
// `model_files` as a LIST of {filename, inputs:[{name,...}], outputs:[...]} (dtype "DTYPE_UINT16", io_type, runtime
// "RUNTIME_GENIEX_QAIRT"); SDK 0.10.0 reads only the older OBJECT form {filename: {inputs:{name:{...}}, outputs:{...}}}
// (dtype "uint16", runtime "geniex_qairt") and fails with "metadata.json missing 'model_files' object". Qualcomm's
// loader at main reads both. This converts the new form to the old one, deterministically; it changes no value
// (the shapes, types and quantization parameters of v0.66.0 and v0.67.0 were compared: identical). It cannot make a
// context binary built with QAIRT 2.51 load on the 2.50.40 runtime: that part stays unknown.
object MetadataCompat {
    sealed interface Result {
        data class Converted(val json: String, val files: Int, val tensors: Int) : Result
        data object AlreadyOldFormat : Result
        data class Failed(val reason: String) : Result
    }

    fun isNewFormat(metadataJson: String?): Boolean =
        metadataJson?.let { MiniJson.parse(it) }?.let { MiniJson.path(it, "model_files") } is List<*>

    @Suppress("UNCHECKED_CAST")
    fun convert(metadataJson: String): Result {
        val root = MiniJson.parse(metadataJson) as? Map<String, Any?> ?: return Result.Failed("metadata.json ist kein JSON-Objekt")
        val files = root["model_files"]
        if (files is Map<*, *>) return Result.AlreadyOldFormat
        if (files !is List<*>) return Result.Failed("model_files fehlt")
        var tensors = 0
        val newFiles = linkedMapOf<String, Any?>()
        for (e in files) {
            val entry = e as? Map<String, Any?> ?: return Result.Failed("Eintrag in model_files ist kein Objekt")
            val name = entry["filename"] as? String ?: return Result.Failed("Eintrag in model_files ohne filename")
            if (name in newFiles) return Result.Failed("filename doppelt: $name")
            val out = linkedMapOf<String, Any?>()
            for ((k, v) in entry) {
                if (k == "filename") continue
                if ((k == "inputs" || k == "outputs") && v is List<*>) {
                    val byName = linkedMapOf<String, Any?>()
                    for (t in v) {
                        val tensor = t as? Map<String, Any?> ?: return Result.Failed("Tensor in $name ist kein Objekt")
                        val tn = tensor["name"] as? String ?: return Result.Failed("Tensor ohne name in $name")
                        if (tn in byName) return Result.Failed("Tensorname doppelt in $name: $tn")
                        byName[tn] = oldTensor(tensor)
                        tensors++
                    }
                    out[k] = byName
                } else {
                    out[k] = v
                }
            }
            newFiles[name] = out
        }
        val result = LinkedHashMap<String, Any?>(root)
        result["model_files"] = newFiles
        (root["supplementary_files"] as? List<*>)?.let { list ->
            val m = linkedMapOf<String, Any?>()
            for (x in list) {
                val sf = x as? Map<String, Any?> ?: continue
                (sf["filename"] as? String)?.let { m[it] = sf["description"] }
            }
            result["supplementary_files"] = m
        }
        (root["runtime"] as? String)?.let { result["runtime"] = it.removePrefix("RUNTIME_").lowercase() }
        (root["precision"] as? String)?.let { result["precision"] = it.removePrefix("PRECISION_").lowercase() }
        return Result.Converted(MiniJson.write(result), newFiles.size, tensors)
    }

    private fun oldTensor(t: Map<String, Any?>): Map<String, Any?> {
        val o = linkedMapOf<String, Any?>()
        o["shape"] = t["shape"]
        (t["dtype"] as? String)?.let { o["dtype"] = it.removePrefix("DTYPE_").lowercase() }
        t["quantization_parameters"]?.let { o["quantization_parameters"] = it }
        return o
    }
}
