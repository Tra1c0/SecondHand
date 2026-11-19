package dev.personal.secondhand.companion

// Stage 80e. Load of the 4B bundle (3.2 GB of context binaries) on the Xiaomi 15 with about 4.2 GB available ended in
// Android's low-memory killer: it killed the app and the launcher ("vom System wegen Speichermangel beendet"). This is the
// memory guard (decide BEFORE a load whether it can fit) and the watchdog (sample DURING the load, persist the samples, pull
// the emergency brake). The numbers are guesses calibrated by one observation (3.2 GB bundle, 4.2 GB available: killed) and
// are meant to be corrected from the persisted samples. No Android classes here: unit-tested.

object MemoryGuard {
    private const val MIB = 1L shl 20

    /** The rule: free memory must be at least FACTOR times the context binaries plus a reserve. */
    const val FACTOR = 2.0
    const val RESERVE_BYTES = 512L * MIB

    /** If the size of the context binaries cannot be read, the guard assumes the 4B bundle. */
    const val UNKNOWN_BIN_BYTES = 3_200_000_000L

    /** During a load: if the system's available memory falls under this, the app ends itself (guess; LMK thresholds are not readable). */
    const val HARD_FLOOR_BYTES = 800L * MIB

    /** onTrimMemory level that triggers the emergency brake while loading: TRIM_MEMORY_RUNNING_CRITICAL. */
    const val TRIM_ABORT_LEVEL = 15

    data class Verdict(val ok: Boolean, val binBytes: Long, val neededBytes: Long, val availBytes: Long) {
        fun ruleText(): String =
            "Regel: frei sein müssen mindestens das ${Metrics.num(FACTOR, 0)}-Fache der Rechenprogramme (${Metrics.gb(binBytes)}) plus ${Metrics.mb(RESERVE_BYTES)} " +
                "= ${Metrics.gb(neededBytes)}; frei sind jetzt ${Metrics.gb(availBytes)}."
    }

    fun verdict(binBytes: Long, availBytes: Long): Verdict {
        val needed = (binBytes * FACTOR).toLong() + RESERVE_BYTES
        return Verdict(availBytes >= needed, binBytes, needed, availBytes)
    }

    sealed interface Decision {
        data object Continue : Decision
        data class Abort(val reason: String) : Decision
    }

    fun decide(availBytes: Long, maxTrimLevelSinceStart: Int): Decision = when {
        maxTrimLevelSinceStart >= TRIM_ABORT_LEVEL ->
            Decision.Abort("onTrimMemory meldete ${trimName(maxTrimLevelSinceStart)} (Stufe $maxTrimLevelSinceStart) während des Ladens")
        availBytes in 0 until HARD_FLOOR_BYTES ->
            Decision.Abort("freier Speicher fiel auf ${Metrics.mb(availBytes)} (Untergrenze ${Metrics.mb(HARD_FLOOR_BYTES)})")
        else -> Decision.Continue
    }

    fun trimName(level: Int): String = when (level) {
        5 -> "TRIM_MEMORY_RUNNING_MODERATE"
        10 -> "TRIM_MEMORY_RUNNING_LOW"
        15 -> "TRIM_MEMORY_RUNNING_CRITICAL"
        20 -> "TRIM_MEMORY_UI_HIDDEN"
        40 -> "TRIM_MEMORY_BACKGROUND"
        60 -> "TRIM_MEMORY_MODERATE"
        80 -> "TRIM_MEMORY_COMPLETE"
        else -> "TRIM_MEMORY_$level"
    }
}

/** What the watchdog reads. */
interface MemoryMeter {
    /** MemAvailable of the system, in bytes (-1 when unreadable). */
    fun availBytes(): Long

    /** The app's resident set size in bytes (-1 when unreadable). */
    fun rssBytes(): Long

    /** Free swap (zram) in bytes (-1 when unreadable). */
    fun swapFreeBytes(): Long
}

data class MemSample(val tMs: Long, val availMb: Long, val rssMb: Long, val swapFreeMb: Long)
data class TrimEvent(val level: Int, val tMs: Long)

/** Everything the next start can report about the last run, persisted synchronously. */
data class StoredRun(
    val step: String,
    val last: MemSample?,
    val minAvailMb: Long,
    val samples: Int,
    val trims: List<TrimEvent>,
    /** Set when the app ended itself (emergency brake): the reason. */
    val selfKill: String?,
    /** True when the step ended normally: nothing to report then. */
    val finished: Boolean,
) {
    fun serialize(): String = buildString {
        append("step=").append(step.replace('\n', ' ')).append('\n')
        append("last=").append(last?.let { "${it.tMs},${it.availMb},${it.rssMb},${it.swapFreeMb}" } ?: "").append('\n')
        append("min=").append(minAvailMb).append('\n')
        append("n=").append(samples).append('\n')
        append("trims=").append(trims.joinToString(";") { "${it.level},${it.tMs}" }).append('\n')
        append("selfkill=").append(selfKill?.replace('\n', ' ') ?: "").append('\n')
        append("finished=").append(finished).append('\n')
    }

    fun reportLines(): List<String> {
        val out = mutableListOf<String>()
        out += "Letzte Speichermessung vor dem Ende des vorherigen Laufs (Schritt '$step'):"
        val l = last
        if (l == null) {
            out += "  (keine Messung gespeichert)"
        } else {
            out += "  frei im System: ${l.availMb} MB, App (RSS): ${l.rssMb} MB, freier Auslagerungsspeicher: ${l.swapFreeMb} MB, " +
                "${Metrics.num(l.tMs / 1000.0, 1)} s nach Beginn; niedrigster Wert frei: $minAvailMb MB; $samples Messungen"
        }
        out += "  onTrimMemory-Meldungen: " + if (trims.isEmpty()) "keine" else trims.joinToString(", ") { "${MemoryGuard.trimName(it.level)} nach ${Metrics.num(it.tMs / 1000.0, 1)} s" }
        selfKill?.let { out += "  Die App hat sich selbst beendet (Notbremse), um das Telefon zu schützen: $it" }
        return out
    }

    companion object {
        fun parse(text: String?): StoredRun? {
            if (text.isNullOrBlank()) return null
            val kv = text.lines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
            return try {
                StoredRun(
                    step = kv["step"] ?: return null,
                    last = kv["last"]?.takeIf { it.isNotBlank() }?.split(',')?.let { MemSample(it[0].toLong(), it[1].toLong(), it[2].toLong(), it[3].toLong()) },
                    minAvailMb = kv["min"]?.toLong() ?: -1,
                    samples = kv["n"]?.toInt() ?: 0,
                    trims = kv["trims"]?.takeIf { it.isNotBlank() }?.split(';')?.map { e -> e.split(',').let { TrimEvent(it[0].toInt(), it[1].toLong()) } } ?: emptyList(),
                    selfKill = kv["selfkill"]?.takeIf { it.isNotBlank() },
                    finished = kv["finished"] == "true",
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/** Where the run is persisted. The Android implementation writes with commit() (synchronously), so that it survives a kill. */
interface SampleStore {
    fun save(run: StoredRun)
    fun load(): StoredRun?
    fun clear()
}

object NoSampleStore : SampleStore {
    override fun save(run: StoredRun) {}
    override fun load(): StoredRun? = null
    override fun clear() {}
}

/** What the runner needs: start sampling before a load, stop afterwards. */
interface Watchdog {
    fun start(step: String)

    /** Ends sampling after the step finished normally: the stored run is marked finished. Returns report lines about the samples. */
    fun stop(): List<String>
}

object NoWatchdog : Watchdog {
    override fun start(step: String) {}
    override fun stop(): List<String> = emptyList()
}

/**
 * Samples during a load (every [intervalMs] on its own thread), persists each sample, and ends the process when
 * [MemoryGuard.decide] says so: ending the app early frees its memory before Android's low-memory killer takes the launcher
 * and other apps. [tick] is public so the unit tests drive it without a thread.
 */
class LoadWatchdog(
    private val meter: MemoryMeter,
    private val store: SampleStore,
    private val clock: Clock,
    private val killer: () -> Unit,
    private val intervalMs: Long = 200,
) : Watchdog {
    private val lock = Any()
    private var step = ""
    private var startedAt = 0L
    private var count = 0
    private var minAvail = Long.MAX_VALUE
    private var last: MemSample? = null
    private val trims = mutableListOf<TrimEvent>()
    private var maxTrimSinceStart = 0
    private var selfKill: String? = null
    @Volatile private var running = false
    private var thread: Thread? = null

    override fun start(step: String) {
        synchronized(lock) {
            this.step = step
            startedAt = clock.nowMs()
            count = 0
            minAvail = Long.MAX_VALUE
            last = null
            trims.clear()
            maxTrimSinceStart = 0
            selfKill = null
            running = true
            persist(finished = false)
        }
        tick()
        thread = Thread {
            while (running) {
                try {
                    Thread.sleep(intervalMs)
                } catch (e: InterruptedException) {
                    return@Thread
                }
                if (running) tick()
            }
        }.also { it.isDaemon = true; it.start() }
    }

    /** One sample: read, record, persist, decide. */
    fun tick() {
        val abort: MemoryGuard.Decision.Abort?
        synchronized(lock) {
            if (!running) return
            val avail = meter.availBytes()
            val s = MemSample(clock.nowMs() - startedAt, avail.toMb(), meter.rssBytes().toMb(), meter.swapFreeBytes().toMb())
            last = s
            count++
            if (s.availMb >= 0 && s.availMb < minAvail) minAvail = s.availMb
            abort = MemoryGuard.decide(avail, maxTrimSinceStart) as? MemoryGuard.Decision.Abort
            if (abort != null) selfKill = abort.reason
            persist(finished = false)
        }
        if (abort != null) {
            running = false
            killer()
        }
    }

    /** Called for every onTrimMemory of the activity; only events during a load are recorded and persisted. */
    fun onTrim(level: Int) {
        synchronized(lock) {
            if (!running) return
            trims += TrimEvent(level, clock.nowMs() - startedAt)
            if (level > maxTrimSinceStart) maxTrimSinceStart = level
            persist(finished = false)
        }
        if (level >= MemoryGuard.TRIM_ABORT_LEVEL) tick()
    }

    override fun stop(): List<String> {
        val lines: List<String>
        synchronized(lock) {
            running = false
            persist(finished = true)
            lines = listOf(
                "Speicherwächter während des Ladens: $count Messungen, niedrigster freier Speicher ${if (minAvail == Long.MAX_VALUE) "-" else "$minAvail MB"}, " +
                    "zuletzt ${last?.let { "${it.availMb} MB frei, App ${it.rssMb} MB" } ?: "-"}, onTrimMemory-Meldungen: " +
                    if (trims.isEmpty()) "keine" else trims.joinToString(", ") { MemoryGuard.trimName(it.level) },
            )
        }
        thread?.interrupt()
        thread = null
        return lines
    }

    private fun persist(finished: Boolean) {
        store.save(StoredRun(step, last, if (minAvail == Long.MAX_VALUE) -1 else minAvail, count, trims.toList(), selfKill, finished))
    }

    private fun Long.toMb(): Long = if (this < 0) -1 else this / (1L shl 20)
}
