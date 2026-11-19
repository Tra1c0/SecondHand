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

    fun add(line: String) {
        synchronized(lines) { lines += line }
        listener?.invoke()
    }

    fun addAll(newLines: List<String>) {
        synchronized(lines) { lines += newLines }
        listener?.invoke()
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
