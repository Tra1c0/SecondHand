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
    const val VERSION = "Begleit-Spike 0.2 (Etappe 80), SDK geniex-android 0.10.0"
}
