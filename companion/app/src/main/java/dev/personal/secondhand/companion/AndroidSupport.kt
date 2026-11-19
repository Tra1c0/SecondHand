package dev.personal.secondhand.companion

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.StatFs
import android.os.SystemClock
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// The Android-dependent parts of the spike (stage 80): measurements, step markers, remembered choices,
// device header. Logging: only step names and error classes, never texts of any kind.
private const val TAG = "SecondHandCompanion"

class RealClock : Clock {
    override fun nowMs(): Long = SystemClock.elapsedRealtime()
}

class AndroidProbe(private val context: Context) : Probe {
    override fun memory(): MemSnapshot {
        val pss = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss.toLong() * 1024L
        val system = ActivityManager.MemoryInfo().also {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
        }.availMem
        return MemSnapshot(Debug.getNativeHeapAllocatedSize(), pss, system)
    }

    override fun freeStorageBytes(): Long = runCatching { StatFs(context.filesDir.path).availableBytes }.getOrDefault(-1L)

    override fun networkType(): String = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        when {
            caps == null -> "kein Netz"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WLAN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobilfunk"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "anderes Netz"
        }
    } catch (t: Throwable) {
        "unbekannt"
    }

    override fun batteryTempC(): Double? {
        val intent: Intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val tenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        return if (tenths == Int.MIN_VALUE) null else tenths / 10.0
    }
}

/** Remembers the running step (written synchronously) so that the next start can report a crash in it. */
class PrefsStepTracker(context: Context) : StepTracker {
    private val prefs = context.getSharedPreferences("spike", Context.MODE_PRIVATE)

    override fun begin(step: String) {
        runCatching { prefs.edit().putString(KEY_RUNNING, step).commit() }
        Log.i(TAG, "step begin: $step")
    }

    override fun end(step: String) {
        runCatching { prefs.edit().remove(KEY_RUNNING).commit() }
        Log.i(TAG, "step end: $step")
    }

    override fun failed(step: String, errorClass: String) {
        runCatching { prefs.edit().remove(KEY_RUNNING).commit() }
        Log.w(TAG, "step failed: $step error class: $errorClass")
    }

    /** The step that was running when the last process ended, or null. Clears the mark. */
    fun takeInterruptedStep(): String? {
        val step = prefs.getString(KEY_RUNNING, null)
        if (step != null) runCatching { prefs.edit().remove(KEY_RUNNING).commit() }
        return step
    }

    private companion object {
        const val KEY_RUNNING = "running_step"
    }
}

/**
 * The candidate that was pulled and the candidates given up (with the short error that made it so), kept across
 * app starts. A candidate is identified by Candidate.id.
 */
class SpikePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("spike", Context.MODE_PRIVATE)

    var pulledCandidateId: String?
        get() = prefs.getString("pulled_candidate", null)
        set(value) {
            prefs.edit().apply { if (value == null) remove("pulled_candidate") else putString("pulled_candidate", value) }.apply()
        }

    /** Entries "id<TAB>error". */
    private var abandonedRaw: Set<String>
        get() = prefs.getStringSet("abandoned_candidates", emptySet())?.toSet() ?: emptySet()
        set(value) {
            prefs.edit().putStringSet("abandoned_candidates", value).apply()
        }

    val abandoned: Map<String, String>
        get() = abandonedRaw.associate { it.substringBefore('\t') to it.substringAfter('\t', "") }

    fun abandon(candidateId: String, error: String) {
        abandonedRaw = abandonedRaw.filterNot { it.substringBefore('\t') == candidateId }.toSet() + (candidateId + "\t" + error)
    }

    fun clearAbandoned() {
        abandonedRaw = emptySet()
    }
}

object DeviceInfo {
    fun collect(context: Context): DeviceHeader {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val free = runCatching { StatFs(context.filesDir.path).availableBytes }.getOrDefault(-1L)
        val soc = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        return DeviceHeader(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            device = Build.DEVICE,
            hardware = Build.HARDWARE,
            socManufacturer = if (soc) Build.SOC_MANUFACTURER else null,
            socModel = if (soc) Build.SOC_MODEL else null,
            androidRelease = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS.joinToString(","),
            freeStorageBytes = free,
            totalRamBytes = mem.totalMem,
            appVersion = BuildConfigLike.VERSION,
        )
    }

    /** Why the previous process of this app ended (Android 11 and newer): tells an out-of-memory kill from a crash. */
    fun lastExitLines(context: Context): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val last = am.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull() ?: return emptyList()
            val whenText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.GERMAN).format(Date(last.timestamp))
            listOf(
                "Ende des vorherigen App-Prozesses: " + reasonName(last.reason) + " (Status ${last.status}) am $whenText",
                "  Beschreibung des Systems: " + (last.description ?: "(keine)"),
            )
        } catch (t: Throwable) {
            emptyList()
        }
    }

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "Absturz (Java)"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "Absturz in nativem Code"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "vom System wegen Speichermangel beendet"
        ApplicationExitInfo.REASON_ANR -> "App reagiert nicht"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "vom Nutzer beendet"
        ApplicationExitInfo.REASON_EXIT_SELF -> "App hat sich selbst beendet"
        ApplicationExitInfo.REASON_SIGNALED -> "per Signal beendet"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "zu hoher Ressourcenverbrauch"
        else -> "Grund-Code $reason"
    }
}

object BuildConfigLike {
    const val VERSION = "Begleit-Spike 0.4 (Etappe 80), SDK geniex-android 0.10.0"
}

/**
 * Stage 80c: facts about the phone and the process for the NPU load. Reads only system files that apps may read,
 * the app's own process and the app's own log; nothing of it is user text.
 */
class AndroidDiagnostics(private val context: Context) : Diagnostics {
    private val libDir: String get() = context.applicationInfo.nativeLibraryDir

    private fun fileFact(path: String): String {
        val f = java.io.File(path)
        return if (f.exists()) "vorhanden (${f.length()} Bytes)" else "nicht vorhanden oder für Apps nicht sichtbar"
    }

    private fun listing(path: String, filter: (String) -> Boolean = { true }, max: Int = 25): String {
        val names = java.io.File(path).list()
        return if (names == null) "nicht lesbar" else {
            val sel = names.filter(filter).sorted()
            "${sel.size} Einträge" + if (sel.isEmpty()) "" else ": " + sel.take(max).joinToString(", ") + if (sel.size > max) ", ..." else ""
        }
    }

    override fun environmentLines(): List<String> {
        val out = mutableListOf<String>()
        out += "Umgebung des Telefons (prüft ${DiagnoseFormatter.H2}, ${DiagnoseFormatter.H3}, ${DiagnoseFormatter.H4}):"
        runCatching {
            listOf(
                "/vendor/lib64/libcdsprpc.so", "/vendor/lib64/libadsprpc.so", "/system/lib64/libcdsprpc.so",
                "/system_ext/lib64/libcdsprpc.so", "/odm/lib64/libcdsprpc.so", "/vendor/lib64/libOpenCL.so",
            ).forEach { out += "  $it: " + fileFact(it) }
        }
        runCatching {
            listOf("/vendor/etc", "/system/etc", "/system_ext/etc", "/product/etc", "/odm/etc").forEach { dir ->
                java.io.File(dir).listFiles { f -> f.name.startsWith("public.libraries") }?.sortedBy { it.name }?.forEach { f ->
                    val text = runCatching { f.readText() }.getOrNull()
                    out += "  ${f.path}: " + if (text == null) "nicht lesbar" else
                        "${text.lines().count { it.isNotBlank() }} Zeilen; cdsprpc ${if ("cdsprpc" in text) "JA" else "nein"}, " +
                            "adsprpc ${if ("adsprpc" in text) "JA" else "nein"}, OpenCL ${if ("OpenCL" in text) "JA" else "nein"}"
                }
            }
        }
        runCatching {
            out += "  /vendor/lib64 (Treiber des KI-Chips): " +
                listing("/vendor/lib64", { n -> listOf("libQnn", "cdsprpc", "adsprpc", "libsnpe", "libcdsp").any { n.contains(it) } })
            listOf("/vendor/dsp", "/vendor/dsp/cdsp", "/vendor/lib/rfsa/adsp", "/vendor/lib/rfsa/dsp").forEach { out += "  $it: " + listing(it) }
        }
        runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            out += "  Speicher laut System: frei ${Metrics.mb(mi.availMem)} von ${Metrics.mb(mi.totalMem)}, Schwelle ${Metrics.mb(mi.threshold)}, " +
                "lowMemory=${mi.lowMemory}"
            val keys = listOf("MemTotal", "MemAvailable", "SwapTotal", "SwapFree")
            java.io.File("/proc/meminfo").readLines().filter { l -> keys.any { l.startsWith("$it:") } }
                .forEach { out += "  /proc/meminfo " + it.replace(Regex("\\s+"), " ") }
        }
        return out
    }

    override fun nativeLoadLines(): List<String> {
        val out = mutableListOf<String>()
        out += "Einzel-Ladetest der Bibliotheken des KI-Chip-Teils, ohne Modell (prüft ${DiagnoseFormatter.H2}; die Java-Schnittstelle des SDK " +
            "bietet keinen Chip-Selbsttest ohne Modell, darum wird nur das Laden der Bibliotheken geprüft):"
        fun attempt(label: String, block: () -> Unit) {
            out += "  $label: " + try {
                block()
                "OK"
            } catch (t: Throwable) {
                "FEHLER ${t.javaClass.simpleName}: " + (t.message ?: "").replace('\n', ' ').take(250)
            }
        }
        attempt("libcdsprpc.so (Systembibliothek, nach Name)") { System.loadLibrary("cdsprpc") }
        attempt("libadsprpc.so (Systembibliothek, nach Name)") { System.loadLibrary("adsprpc") }
        listOf("libQnnSystem.so", "libQnnHtp.so", "libQnnHtpV79Stub.so", "libgeniex_core.so", "libgeniex_plugin_qairt.so").forEach { name ->
            attempt("$name (aus dem App-Ordner)") { System.load("$libDir/$name") }
        }
        out += "  Skel-Dateien im App-Ordner: " + listing(libDir, { it.contains("Skel") })
        out += "  App-Ordner der Bibliotheken: $libDir"
        return out
    }

    override fun processLines(): List<String> {
        val out = mutableListOf<String>()
        for (name in listOf("ADSP_LIBRARY_PATH", "LD_LIBRARY_PATH", "GENIEX_LOG", "GENIEX_AIHUBVERSION", "QUALCOMM_QAIRT_LOG")) {
            out += "  Umgebungsvariable $name = " + (runCatching { android.system.Os.getenv(name) }.getOrNull() ?: "(nicht gesetzt)")
        }
        runCatching {
            val keys = listOf("cdsprpc", "adsprpc", "QnnHtp", "QnnSystem", "geniex", "OpenCL", "ggml")
            val names = java.io.File("/proc/self/maps").readLines()
                .mapNotNull { it.substringAfterLast('/', "").takeIf { n -> n.endsWith(".so") } }
                .filter { n -> keys.any { n.contains(it) } }.distinct().sorted()
            out += "  Im Prozess geladene Bibliotheken des KI-Chip-Teils: " + names.joinToString(", ").ifBlank { "(keine)" }
        }
        return out
    }

    override fun runtimeQairtVersion(): String? = runCatching {
        QairtVersion.findInBinary(java.io.File(libDir, "libQnnHtp.so").readBytes())
    }.getOrNull()

    override fun markLog(marker: String) {
        Log.i(TAG, marker)
    }

    override fun logSince(marker: String): List<String> = try {
        val proc = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", "4000").redirectErrorStream(true).start()
        val lines = java.util.Collections.synchronizedList(mutableListOf<String>())
        val reader = Thread { runCatching { proc.inputStream.bufferedReader().forEachLine { lines += it } } }
        reader.start()
        reader.join(8000)
        proc.destroy()
        synchronized(lines) { lines.toList() }
    } catch (t: Throwable) {
        emptyList()
    }
}

class AndroidStoreFiles(private val context: Context) : StoreFiles {
    override fun modelsRoot(): String = java.io.File(context.filesDir, "geniex/models").path
    override fun downloadsDir(): String = java.io.File(context.filesDir, "companion-dl").path
    override fun backupDir(): String = java.io.File(context.filesDir, "companion-backup").path
    override fun exists(path: String): Boolean = java.io.File(path).exists()
    override fun listDirs(path: String): List<String> = java.io.File(path).listFiles { f -> f.isDirectory }?.map { it.name }?.sorted() ?: emptyList()
    override fun treeBytes(path: String): Long = java.io.File(path).walkTopDown().filter { it.isFile }.sumOf { it.length() }
    override fun deleteTree(path: String): Boolean = java.io.File(path).let { !it.exists() || it.deleteRecursively() }
    override fun readText(path: String): String? = runCatching { java.io.File(path).readText() }.getOrNull()
    override fun writeText(path: String, text: String): Boolean = runCatching {
        java.io.File(path).also { it.parentFile?.mkdirs() }.writeText(text)
    }.isSuccess

    override fun copyFile(from: String, to: String): Boolean = runCatching {
        java.io.File(to).parentFile?.mkdirs()
        java.io.File(from).copyTo(java.io.File(to), overwrite = false)
    }.isSuccess

    override fun freeBytes(): Long = runCatching { StatFs(context.filesDir.path).availableBytes }.getOrDefault(-1L)
}
