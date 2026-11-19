package dev.personal.secondhand.companion

// Pure helpers of the load diagnostics (stage 80c): a tiny JSON reader, the facts of a pulled bundle, QAIRT
// version arithmetic and the filter for the runtime's own log lines. No Android, no SDK: unit-tested.

/** A small JSON reader (the unit tests run on the JVM, where android.jar's org.json is only a stub). Returns Map, List, String, Double, Boolean or null. */
object MiniJson {
    fun parse(text: String): Any? = try {
        val p = Parser(text)
        p.skip()
        val v = p.value()
        p.skip()
        if (p.i == text.length) v else null
    } catch (e: Exception) {
        null
    }

    private class Parser(val s: String) {
        var i = 0

        fun skip() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(): Any? {
            skip()
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw IllegalArgumentException("unexpected $c at $i")
            }
        }

        fun lit(word: String, v: Any?): Any? {
            require(s.startsWith(word, i))
            i += word.length
            return v
        }

        fun num(): Double {
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(st, i).toDouble()
        }

        fun str(): String {
            val sb = StringBuilder()
            i++
            while (s[i] != '"') {
                if (s[i] == '\\') {
                    i++
                    when (val e = s[i]) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000c')
                        'u' -> {
                            sb.append(s.substring(i + 1, i + 5).toInt(16).toChar())
                            i += 4
                        }
                        else -> sb.append(e)
                    }
                } else {
                    sb.append(s[i])
                }
                i++
            }
            i++
            return sb.toString()
        }

        fun arr(): List<Any?> {
            val out = mutableListOf<Any?>()
            i++
            skip()
            if (s[i] == ']') {
                i++
                return out
            }
            while (true) {
                out += value()
                skip()
                if (s[i] == ',') i++ else break
            }
            require(s[i] == ']')
            i++
            return out
        }

        fun obj(): Map<String, Any?> {
            val out = linkedMapOf<String, Any?>()
            i++
            skip()
            if (s[i] == '}') {
                i++
                return out
            }
            while (true) {
                skip()
                val k = str()
                skip()
                require(s[i] == ':')
                i++
                out[k] = value()
                skip()
                if (s[i] == ',') i++ else break
            }
            require(s[i] == '}')
            i++
            return out
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun path(root: Any?, vararg keys: Any): Any? {
        var cur = root
        for (k in keys) {
            cur = when {
                k is String && cur is Map<*, *> -> (cur as Map<String, Any?>)[k]
                k is Int && cur is List<*> -> cur.getOrNull(k)
                else -> return null
            }
        }
        return cur
    }
}

/** What the files of a pulled bundle say about it (metadata.json and htp_backend_ext_config.json). */
data class BundleFacts(
    val modelId: String?,
    val runtime: String?,
    val precision: String?,
    /** The QAIRT version the context binaries were built with, e.g. "2.51.0.260929181845". */
    val qairt: String?,
    val chipsetName: String?,
    val chipsetAliases: List<String>,
    val socModel: Int?,
    val dspArch: String?,
) {
    companion object {
        fun extract(metadataJson: String?, htpConfigJson: String?): BundleFacts {
            val meta = metadataJson?.let { MiniJson.parse(it) }
            val htp = htpConfigJson?.let { MiniJson.parse(it) }
            fun s(root: Any?, vararg k: Any) = MiniJson.path(root, *k) as? String
            fun n(root: Any?, vararg k: Any) = (MiniJson.path(root, *k) as? Double)?.toInt()
            return BundleFacts(
                modelId = s(meta, "model_id"),
                runtime = s(meta, "runtime"),
                precision = s(meta, "precision"),
                qairt = s(meta, "tool_versions", "qairt"),
                chipsetName = s(meta, "chipset_attributes", "name"),
                chipsetAliases = (MiniJson.path(meta, "chipset_attributes", "aliases") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                socModel = n(htp, "devices", 0, "soc_model") ?: n(meta, "chipset_attributes", "soc_model"),
                dspArch = s(htp, "devices", 0, "dsp_arch"),
            )
        }
    }
}

enum class Compat { BUNDLE_NEWER, BUNDLE_SAME_OR_OLDER, UNKNOWN }

object QairtVersion {
    private val PARSE = Regex("""v?(\d+)\.(\d+)\.(\d+)""")
    private val IN_BINARY = Regex("""v2\.\d+\.\d+\.\d{6,}""")

    /** (major, minor, patch) of "2.51.0.260929181845" or "v2.50.40.260831140417", or null. */
    fun parse(text: String?): Triple<Int, Int, Int>? {
        val m = PARSE.find(text ?: return null) ?: return null
        return Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
    }

    /** "2.50" of any version string; the part that decides which candidate a bundle belongs to. */
    fun majorMinor(text: String?): String? = parse(text)?.let { "${it.first}.${it.second}" }

    fun compat(bundle: String?, runtime: String?): Compat {
        val b = parse(bundle) ?: return Compat.UNKNOWN
        val r = parse(runtime) ?: return Compat.UNKNOWN
        val cmp = compareValuesBy(b, r, { it.first }, { it.second }, { it.third })
        return if (cmp > 0) Compat.BUNDLE_NEWER else Compat.BUNDLE_SAME_OR_OLDER
    }

    /** The version string compiled into libQnnHtp.so (the QAIRT runtime the SDK ships), found in the file's bytes. */
    fun findInBinary(bytes: ByteArray): String? = IN_BINARY.find(String(bytes, Charsets.ISO_8859_1))?.value
}

/** Filters the app's own logcat lines to what concerns the NPU runtime. */
object LogFilter {
    private val KEYWORDS = listOf(
        "geniex", "qnn", "htp", "genie", "dsp", "fastrpc", "cdsp", "adsp", "skel", "qairt", "qualla", "rpcmem", "dmabuf",
        "dlopen", "context binary", "device creation", "stdout", "stderr", "libc ",
    )
    private val THREADTIME = Regex("""^\S+\s+\S+\s+\d+\s+\d+\s+([VDIWEF])\s+(.*)$""")
    private const val MAX_LINE = 300

    /** The lines after the last line that contains [marker]; all lines when the marker is not there. */
    fun afterMarker(lines: List<String>, marker: String): List<String> {
        val i = lines.indexOfLast { it.contains(marker) }
        return if (i < 0) lines else lines.drop(i + 1)
    }

    /**
     * Keeps lines with a keyword of the NPU stack, or of priority warning, error or fatal, drops lines with any of
     * [forbidden] (fragments of the app's fixed test texts), cuts long lines and keeps the last [max] lines.
     */
    fun relevant(lines: List<String>, forbidden: List<String>, max: Int): List<String> =
        lines.asSequence()
            .filter { line ->
                val prio = THREADTIME.find(line)?.groupValues?.get(1)
                val lower = line.lowercase()
                (prio != null && prio in "WEF") || KEYWORDS.any { it in lower }
            }
            .filter { line -> forbidden.none { line.contains(it, ignoreCase = true) } }
            .map { if (it.length > MAX_LINE) it.take(MAX_LINE) + "..." else it }
            .toList()
            .takeLast(max)
}

/** Report lines about a pulled bundle and the runtime it meets; each block says which hypothesis it tests. */
object DiagnoseFormatter {
    const val H1 = "H1 (Paket mit neuerem QAIRT gebaut als die Laufzeit)"
    const val H2 = "H2 (App kann die Systembibliotheken für den KI-Chip nicht laden)"
    const val H3 = "H3 (Chip-Zugriff oder Paket zum Chip passt nicht)"
    const val H4 = "H4 (zu wenig Arbeitsspeicher beim Laden)"
    const val H5 = "H5 (Paketdateien unvollständig oder beschädigt)"

    fun bundleLines(i: BundleInspection, facts: BundleFacts, runtimeQairt: String?): List<String> {
        val lines = mutableListOf<String>()
        lines += "Paket im Modellspeicher (prüft $H5 und $H1):"
        lines += "  Ordner: ${i.modelDir ?: "unbekannt"}, Laufzeit laut Modellspeicher: ${i.runtimeId ?: "unbekannt"}"
        val total = i.files.sumOf { it.size }
        lines += "  ${i.files.size} Dateien, zusammen ${Metrics.gb(total)}:"
        i.files.sortedBy { it.name }.forEach { lines += "    ${it.name}  ${it.size}" }
        val bins = i.files.filter { it.name.endsWith(".bin") }
        lines += if (bins.isEmpty()) "  ACHTUNG: keine .bin-Dateien im Paket" else "  .bin-Teile: ${bins.size} (kleinster ${bins.minOf { it.size }} Bytes)"
        lines += "  Paket gebaut mit QAIRT: ${facts.qairt ?: "nicht gefunden"} (metadata.json, tool_versions)"
        lines += "  Laufzeit des SDK (libQnnHtp.so): ${runtimeQairt ?: "nicht gefunden"}"
        lines += "  Paket: Modell ${facts.modelId ?: "?"}, ${facts.runtime ?: "?"}, ${facts.precision ?: "?"}, Chip ${facts.chipsetName ?: "?"} " +
            "(${facts.chipsetAliases.joinToString("/")}), SoC-Nummer ${facts.socModel ?: "?"}, DSP-Architektur ${facts.dspArch ?: "?"}"
        lines += when (QairtVersion.compat(facts.qairt, runtimeQairt)) {
            Compat.BUNDLE_NEWER -> "  -> Das Paket ist NEUER als die Laufzeit: $H1 ist möglich."
            Compat.BUNDLE_SAME_OR_OLDER -> "  -> Paket nicht neuer als die Laufzeit: $H1 ist damit unwahrscheinlich."
            Compat.UNKNOWN -> "  -> Versionsvergleich nicht möglich ($H1 offen)."
        }
        i.texts["htp_backend_ext_config.json"]?.let { lines += "  htp_backend_ext_config.json: " + it.replace(Regex("\\s+"), " ").take(400) }
        i.texts["genie_config.json"]?.let { lines += "  genie_config.json (Anfang): " + it.replace(Regex("\\s+"), " ").take(600) }
        return lines
    }
}
