package dev.personal.secondhand.companion

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeClock(var now: Long = 0) : Clock {
    override fun nowMs(): Long = now
}

class FakeProbe(var temp: Double? = 31.0) : Probe {
    private var call = 0
    override fun memory(): MemSnapshot {
        call++
        // the second call (after the run) sees more memory in use
        return MemSnapshot(100L * 1048576 * call, 200L * 1048576 * call, 6000L * 1048576 / call)
    }

    override fun batteryTempC(): Double? = temp?.also { temp = it + 1.5 }
    var free = 90L * 1073741824
    var network = "WLAN"
    override fun freeStorageBytes(): Long = free
    override fun networkType(): String = network
}

class FakeTracker : StepTracker {
    val events = mutableListOf<String>()
    override fun begin(step: String) { events += "begin:$step" }
    override fun end(step: String) { events += "end:$step" }
    override fun failed(step: String, errorClass: String) { events += "failed:$step:$errorClass" }
}

/** A scripted NpuPort: records the calls and replays what the test sets. */
class FakePort(private val clock: FakeClock) : NpuPort {
    val calls = mutableListOf<String>()
    var initError: Throwable? = null
    /** Model names that are in the store. */
    val present = mutableSetOf(PullPlanner.INSTRUCT_V66.modelName)
    var hub: List<HubEntry> = listOf(HubEntry("qwen3_4b", listOf("qualcomm-snapdragon-8-elite-for-galaxy")))
    var hubError: Throwable? = null
    val loadedNames = mutableListOf<String>()
    val deletedNames = mutableListOf<String>()
    val deleteResults = mutableMapOf<String, Int>()
    var pullUpdates: List<PullUpdate> = listOf(PullUpdate.Completed)
    var pullThrows: Throwable? = null
    var loadInfo = LoadInfo("qairt", "/models/x")
    var loadError: Throwable? = null
    var loadTakesMs = 4000L
    var tokens = listOf("Wir ", "haben ", "gestern ", "einen ", "langen ", "Spaziergang ", "gemacht ", "und ", "danach ", "Kaffee ", "getrunken.")
    var tokenGapMs = 50L
    var profile: GenProfile? = GenProfile(310.0, 280.0, 5000.0, 40, 11, 142.0, 2.2, "stop")
    var generateEvents: List<GenEvent>? = null
    var deleteResult = 0
    val importedNames = mutableListOf<String>()
    var importAddsPresent = true

    /** The QAIRT version the fake bundle in the store was built with. */
    var bundleQairt: String? = "2.50.0.260828221209"
    var inspectError: Throwable? = null
    var noInspection = false
    var newFormat = false

    /** Lets a test serve the metadata.json the app wrote (a null result means: the generated one). */
    var metaProvider: (() -> String?)? = null
    /** Called inside load(): lets a test write log lines while the load runs. */
    var onLoad: (() -> Unit)? = null

    override suspend fun init(): InitInfo {
        calls += "init"
        initError?.let { throw it }
        return InitInfo("0.10.0", "2.50", "/data/qairt", "SM8750")
    }

    override suspend fun isModelPresent(modelName: String): Boolean { calls += "present:$modelName"; return modelName in present }

    override suspend fun inspect(modelName: String): BundleInspection? {
        calls += "inspect:$modelName"
        inspectError?.let { throw it }
        if (noInspection || modelName !in present) return null
        val modelFiles = if (newFormat) {
            "[{\"filename\":\"part1_of_4.bin\",\"inputs\":[{\"name\":\"input_ids\",\"shape\":[1,1],\"dtype\":\"DTYPE_INT32\",\"io_type\":\"IO_TYPE_TENSOR\"}],\"outputs\":[]}]"
        } else {
            "{\"part1_of_4.bin\":{\"inputs\":{\"input_ids\":{\"shape\":[1,1],\"dtype\":\"int32\"}},\"outputs\":{}}}"
        }
        val generated = "{\"model_id\":\"qwen3_4b_instruct_2507\",\"runtime\":\"RUNTIME_GENIEX_QAIRT\",\"precision\":\"PRECISION_W4A16\"," +
            "\"tool_versions\":{\"qairt\":\"$bundleQairt\"},\"chipset_attributes\":{\"name\":\"qualcomm-snapdragon-8-elite-for-galaxy\"," +
            "\"aliases\":[\"sm8750-ac\"],\"soc_model\":69},\"model_files\":$modelFiles}"
        val meta = metaProvider?.invoke() ?: generated
        val htp = "{\"devices\":[{\"soc_model\":69,\"dsp_arch\":\"v79\"}]}"
        return BundleInspection(
            "/data/geniex/models/x", "qairt",
            listOf(BundleFile("part1_of_4.bin", 778182656), BundleFile("part2_of_4.bin", 659685376), BundleFile("metadata.json", meta.length.toLong())),
            mapOf("metadata.json" to meta, "htp_backend_ext_config.json" to htp, "genie_config.json" to "{\"dialog\":{}}"),
        )
    }

    override fun importLocal(modelName: String, localPath: String): Flow<PullUpdate> = flow {
        calls += "import:$modelName:$localPath"
        importedNames += modelName
        if (importAddsPresent) present += modelName
        pullThrows?.let { throw it }
        pullUpdates.forEach { emit(it) }
    }

    override suspend fun deleteModel(modelName: String): Int {
        calls += "delete:$modelName"
        deletedNames += modelName
        return deleteResults[modelName] ?: deleteResult
    }

    override suspend fun listHub(): List<HubEntry> {
        calls += "listHub"
        hubError?.let { throw it }
        return hub
    }

    override suspend fun load(modelName: String): LoadInfo {
        calls += "load"
        loadedNames += modelName
        onLoad?.invoke()
        clock.now += loadTakesMs
        loadError?.let { throw it }
        return loadInfo
    }

    override fun generate(systemInstruction: String, userText: String, maxTokens: Int): Flow<GenEvent> = flow {
        calls += "generate:$maxTokens"
        clock.now += 300 // time to the first token
        generateEvents?.let { list -> list.forEach { emit(it) }; return@flow }
        tokens.forEach {
            emit(GenEvent.Token(it))
            clock.now += tokenGapMs
        }
        profile?.let { emit(GenEvent.Done(it)) }
    }

    override suspend fun stop() { calls += "stop" }
    override suspend fun unload() { calls += "unload" }
}

class FakeDiagnostics : Diagnostics {
    val calls = mutableListOf<String>()
    var runtimeVersion: String? = "v2.50.40.260831140417"
    var log: List<String> = emptyList()
    override fun environmentLines() = listOf("Umgebung des Telefons (prüft H2):", "  /vendor/lib64/libcdsprpc.so: vorhanden (1 Bytes)").also { calls += "env" }
    override fun nativeLoadLines() = listOf("Einzel-Ladetest:", "  libcdsprpc.so (Systembibliothek, nach Name): OK").also { calls += "nativeLoad" }
    override fun processLines() = listOf("  Umgebungsvariable ADSP_LIBRARY_PATH = /data/app/x/lib").also { calls += "process" }
    override fun runtimeQairtVersion(): String? = runtimeVersion
    override fun markLog(marker: String) { calls += "mark"; log = log + "01-01 00:00:00.000 1 1 I SecondHandCompanion: $marker" }
    override fun logSince(marker: String): List<String> = log
}

class FakeStore : StoreFiles {
    val files = linkedMapOf<String, Long>()
    val texts = mutableMapOf<String, String>()
    var free = 100L * 1073741824
    val undeletable = mutableSetOf<String>()
    var onDeleted: (String) -> Unit = {}
    var copyOk = true
    var writeOk = true

    override fun modelsRoot() = "/data/files/geniex/models"
    override fun downloadsDir() = "/data/files/companion-dl"
    override fun backupDir() = "/data/files/companion-backup"
    override fun exists(path: String) = files.keys.any { it == path || it.startsWith("$path/") } || texts.containsKey(path)
    override fun listDirs(path: String): List<String> =
        files.keys.filter { it.startsWith("$path/") }.map { it.removePrefix("$path/").substringBefore('/') }
            .filter { name -> files.keys.any { it.startsWith("$path/$name/") } }.distinct().sorted()
    override fun treeBytes(path: String) = files.filterKeys { it == path || it.startsWith("$path/") }.values.sum()
    override fun deleteTree(path: String): Boolean {
        if (path in undeletable) return false
        files.keys.removeAll { it == path || it.startsWith("$path/") }
        onDeleted(path)
        return true
    }
    override fun readText(path: String): String? = texts[path]
    override fun writeText(path: String, text: String): Boolean = writeOk.also { if (it) texts[path] = text }
    override fun copyFile(from: String, to: String): Boolean = copyOk.also { if (it) texts[to] = texts[from] ?: "" }
    override fun freeBytes() = free
}

class FakeDownloader(private val store: FakeStore) : ZipDownloader {
    val calls = mutableListOf<String>()
    var downloadError: DownloadException? = null
    var verifyError: DownloadException? = null
    var progressAt: List<Pair<Long, Long>> = emptyList()
    override fun download(url: String, dest: String, expectedBytes: Long, onProgress: (Long, Long) -> Unit, cancelled: () -> Boolean) {
        calls += "download:$url"
        progressAt.forEach { onProgress(it.first, it.second) }
        downloadError?.let { throw it }
        store.files[dest] = expectedBytes
    }
    override fun verifyZip(path: String, onProgress: (Long, Long) -> Unit, cancelled: () -> Boolean): ZipSummary {
        calls += "verify:$path"
        verifyError?.let { throw it }
        return ZipSummary(17, 4, true, 3_000_000_000L)
    }
}
