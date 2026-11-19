package dev.personal.secondhand.companion

/** Process memory as three different numbers; the report says which is which. */
data class MemSnapshot(
    /** Debug.getNativeHeapAllocatedSize: malloc'd native memory of this app (memory-mapped weights are not in it). */
    val nativeHeapBytes: Long,
    /** Debug.MemoryInfo.getTotalPss, converted to bytes: this app's share of all pages, mapped files included. */
    val pssBytes: Long,
    /** ActivityManager.MemoryInfo.availMem: what the whole system still has free. */
    val systemAvailBytes: Long,
)

/** Measurements that need Android. The fake in the unit tests returns fixed values. */
interface Probe {
    fun memory(): MemSnapshot

    /** Battery temperature in degrees Celsius (ACTION_BATTERY_CHANGED), or null when the phone does not report it. */
    fun batteryTempC(): Double?

    /** MemAvailable of the system in bytes (what the memory guard compares), or -1. */
    fun memAvailableBytes(): Long

    /** Free space of the app's storage in bytes, or -1. */
    fun freeStorageBytes(): Long

    /** "WLAN", "Mobilfunk", "anderes Netz", "kein Netz" or "unbekannt" (ConnectivityManager, needs ACCESS_NETWORK_STATE). */
    fun networkType(): String
}

interface Clock {
    /** Monotonic milliseconds. */
    fun nowMs(): Long
}

/**
 * Step markers: the Android implementation logs only the step name and the error class (android.util.Log,
 * nothing else) and remembers the running step, so that a native crash can be reported at the next start.
 */
interface StepTracker {
    fun begin(step: String)
    fun end(step: String)
    fun failed(step: String, errorClass: String)
}

/** The lines shown on the screen and copied by "Bericht kopieren". Thread-safe; the listener is called on the writing thread. */
class ReportLog {
    private val lines = mutableListOf<String>()
    @Volatile
    var listener: (() -> Unit)? = null

    /** Every line that is added is also handed to this sink (the run file, written and forced to disk line by line). */
    @Volatile
    var sink: ((String) -> Unit)? = null

    fun add(line: String) {
        synchronized(lines) { lines += line }
        sink?.invoke(line)
        listener?.invoke()
    }

    fun addAll(newLines: List<String>) {
        synchronized(lines) { lines += newLines }
        newLines.forEach { sink?.invoke(it) }
        listener?.invoke()
    }

    /** A line only for the run file (the memory samples during a load): it does not appear on the screen or in the copied current report. */
    fun addQuiet(line: String) {
        sink?.invoke(line)
    }

    fun snapshot(): List<String> = synchronized(lines) { lines.toList() }

    fun text(): String = snapshot().joinToString("\n")
}

/**
 * Facts about the phone and the process that decide whether the NPU runtime can load (stage 80c). Every line the
 * implementation returns is a fact of the device or the app's own process, never text of a user or a prompt.
 */
interface Diagnostics {
    /** Vendor libraries, the library lists the vendor publishes, DSP directories, memory (tests H2, H3, H4). */
    fun environmentLines(): List<String>

    /** The result of loading each library of the NPU stack by hand, with the system's own error text (tests H2). */
    fun nativeLoadLines(): List<String>

    /** Environment variables and the libraries of the NPU stack that are mapped into the process right now. */
    fun processLines(): List<String>

    /** One line with the process memory at a phase of the run (SDK start, before and after the load, inference, release): dma-buf, its high-water mark, RSS, native heap, free memory. */
    fun phaseLine(label: String): String = ""

    /** The files that might show dma-buf use or its limit, and what could be read from them (this phone's kernel files are not documented). */
    fun dmabufLines(): List<String> = emptyList()

    /** The version string compiled into the SDK's libQnnHtp.so, or null. */
    fun runtimeQairtVersion(): String?

    /** Writes a marker line to the app's own log, so [logSince] can cut everything before it. */
    fun markLog(marker: String)

    /** The app's own log lines after [marker] (raw; the runner filters them). */
    fun logSince(marker: String): List<String>
}

object NoDiagnostics : Diagnostics {
    override fun environmentLines(): List<String> = emptyList()
    override fun nativeLoadLines(): List<String> = emptyList()
    override fun processLines(): List<String> = emptyList()
    override fun runtimeQairtVersion(): String? = null
    override fun markLog(marker: String) {}
    override fun logSince(marker: String): List<String> = emptyList()
}

/**
 * The app's own files around the SDK's model store (stage 80d): the store lives in the app's private folder
 * (files/geniex/models/<org>/<repo>), so the app can verify and, if the SDK leaves files behind, remove them itself.
 */
interface StoreFiles {
    /** The SDK's model store: models/<org>/<repo> below it. */
    fun modelsRoot(): String
    fun downloadsDir(): String
    fun backupDir(): String
    fun exists(path: String): Boolean

    /** Names of the sub-folders of [path] (empty when it does not exist). */
    fun listDirs(path: String): List<String>
    fun treeBytes(path: String): Long
    fun deleteTree(path: String): Boolean
    fun readText(path: String): String?
    fun writeText(path: String, text: String): Boolean
    fun copyFile(from: String, to: String): Boolean
    fun freeBytes(): Long
}

object NoStore : StoreFiles {
    override fun modelsRoot() = "/none/models"
    override fun downloadsDir() = "/none/dl"
    override fun backupDir() = "/none/backup"
    override fun exists(path: String) = false
    override fun listDirs(path: String) = emptyList<String>()
    override fun treeBytes(path: String) = 0L
    override fun deleteTree(path: String) = true
    override fun readText(path: String): String? = null
    override fun writeText(path: String, text: String) = false
    override fun copyFile(from: String, to: String) = false
    override fun freeBytes() = -1L
}
