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

    /**
     * HyperOS force-stops a third-party app whose dma-buf use is above 1,572,864 kB (system text of the fifth device run: "[FORCE STOP] stop <app>
     * due to used too many dmabuf resource, dmabuf used rss:3966224kb, dmabuf threshold:1572864kb"). Whether the limit depends on the RAM or the
     * foreground state is not documented (guess: fixed).
     */
    const val HYPEROS_DMABUF_LIMIT_BYTES = 1_572_864L * 1024

    /**
     * The brake ends the app below the limit: 1350 MiB is 88 % of the limit (1536 MiB). The margin of 186 MiB covers the growth between two samples
     * (100 ms): the observed load reached 3.8 GiB, so even a context binary mapped in one step (the largest part of the 1.7B bundle is 594 MiB)
     * may overshoot a lower threshold; a higher one would risk HyperOS acting first. Guess.
     */
    const val DMABUF_BRAKE_BYTES = 1350L * MIB

    /** Observed: 3,966,224 kB of dma-buf for the Qwen3-1.7B bundle with 1,742,958,592 bytes of context binaries (factor 2.33); rounded up. */
    const val DMABUF_FACTOR = 2.4

    fun dmabufPredicted(binBytes: Long): Long = (binBytes * DMABUF_FACTOR).toLong()

    /** If the size of the context binaries cannot be read, the guard assumes the 4B bundle. */
    const val UNKNOWN_BIN_BYTES = 3_200_000_000L

    /** During a load: if the system's available memory falls under this, the app ends itself (guess; LMK thresholds are not readable). */
    const val HARD_FLOOR_BYTES = 800L * MIB

    /** onTrimMemory level that triggers the emergency brake while loading: TRIM_MEMORY_RUNNING_CRITICAL. */
    const val TRIM_ABORT_LEVEL = 15

    data class Verdict(
        val ok: Boolean,
        val binBytes: Long,
        val neededBytes: Long,
        val availBytes: Long,
        /** False when the predicted dma-buf use is above the HyperOS limit (the memory rule alone may still be fine). */
        val dmabufOk: Boolean = true,
        val dmabufPredictedBytes: Long = 0,
        /** True when the app's dma-buf use cannot be read here and the model is too big to load without the brake. */
        val noMeter: Boolean = false,
    ) {
        val memoryOk: Boolean get() = availBytes >= neededBytes

        fun ruleText(): String =
            "Regel 1: frei sein müssen mindestens das ${Metrics.num(FACTOR, 0)}-Fache der Rechenprogramme (${Metrics.gb(binBytes)}) plus ${Metrics.mb(RESERVE_BYTES)} " +
                "= ${Metrics.gb(neededBytes)}; frei sind jetzt ${Metrics.gb(availBytes)}." +
                if (dmabufPredictedBytes > 0) {
                    " Regel 2: Chip-Speicher (dma-buf) vorhergesagt ${Metrics.gb(dmabufPredictedBytes)} (${DMABUF_FACTOR}-faches der Rechenprogramme), " +
                        "HyperOS beendet Apps ab ${Metrics.gb(HYPEROS_DMABUF_LIMIT_BYTES)}: " + if (dmabufOk) "ok." else "ZU VIEL."
                } else ""
    }

    fun verdict(binBytes: Long, availBytes: Long): Verdict {
        val needed = (binBytes * FACTOR).toLong() + RESERVE_BYTES
        return Verdict(availBytes >= needed, binBytes, needed, availBytes)
    }

    /** Both rules: free memory (rule 1) and the predicted dma-buf use against the HyperOS limit [dmabufLimitBytes] (rule 2). */
    /** Without a readable dma-buf value only a model of at most this many bytes of context binaries is loaded (the 0.6B bundle: 0.75 GB). */
    const val NO_METER_MAX_BIN_BYTES = 1_000_000_000L

    fun fullVerdict(
        binBytes: Long,
        availBytes: Long,
        dmabufLimitBytes: Long = HYPEROS_DMABUF_LIMIT_BYTES,
        meterReadable: Boolean = true,
    ): Verdict {
        val v = verdict(binBytes, availBytes)
        val predicted = dmabufPredicted(binBytes)
        val dmaOk = predicted < dmabufLimitBytes
        val noMeter = !meterReadable && binBytes > NO_METER_MAX_BIN_BYTES
        return v.copy(ok = v.ok && dmaOk && !noMeter, dmabufOk = dmaOk, dmabufPredictedBytes = predicted, noMeter = noMeter)
    }

    sealed interface Decision {
        data object Continue : Decision
        data class Abort(val reason: String) : Decision
    }

    fun decide(availBytes: Long, maxTrimLevelSinceStart: Int, dmabufBytes: Long = -1): Decision = when {
        dmabufBytes >= DMABUF_BRAKE_BYTES ->
            Decision.Abort("dma-buf der App stieg auf ${Metrics.mb(dmabufBytes)} (Notbremse ${Metrics.mb(DMABUF_BRAKE_BYTES)}, HyperOS beendet ab ${Metrics.mb(HYPEROS_DMABUF_LIMIT_BYTES)})")
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

    /** The app's own dma-buf use in bytes (what HyperOS limits), or -1 when unreadable. */
    fun dmabufBytes(): Long = -1L
}

/** Reads the app's dma-buf use from its own /proc files (parsing only; the Android class reads the files). */
object DmaBufParse {
    /** (inode or null, size) of a /proc/<pid>/fdinfo text of a dma-buf fd (it has "exp_name:" and "size:"), or null for any other fd. */
    fun fdinfo(text: String): Pair<Long?, Long>? {
        var size: Long? = null
        var ino: Long? = null
        var exp = false
        for (line in text.lineSequence()) {
            val k = line.substringBefore(':').trim()
            val v = line.substringAfter(':', "").trim()
            when (k) {
                "size" -> size = v.toLongOrNull()
                "ino" -> ino = v.toLongOrNull()
                "exp_name" -> exp = true
            }
        }
        return if (exp && size != null) ino to size else null
    }

    /** Sum of the sizes of the distinct dma-bufs (fds duplicated with the same inode count once; fds without an inode count each). */
    fun sumFdinfos(texts: List<String>): Long {
        val seen = mutableSetOf<Long>()
        var sum = 0L
        for (t in texts) {
            val (ino, size) = fdinfo(t) ?: continue
            if (ino == null || seen.add(ino)) sum += size
        }
        return sum
    }

    /** The first integer of a /proc/self/dmabuf_rss style file, or null. */
    fun firstNumber(text: String): Long? = Regex("\\d+").find(text)?.value?.toLongOrNull()

    /**
     * The file's unit is not documented. "kB" in the text means kilobytes; otherwise the factor (1 or 1024) that brings the value closest to what the
     * fd and map sums say (when they say more than 64 MiB); without that, kilobytes (HyperOS prints "kb" in its force-stop text). Guess.
     */
    fun procBytes(text: String, fdMapBytes: Long): Long? {
        val n = firstNumber(text) ?: return null
        if (text.contains("kb", ignoreCase = true)) return n * 1024
        if (fdMapBytes > 64L * 1048576) {
            val asKb = n * 1024.0
            val asBytes = n.toDouble()
            val rKb = Math.abs(Math.log((asKb + 1) / fdMapBytes))
            val rB = Math.abs(Math.log((asBytes + 1) / fdMapBytes))
            return if (rKb <= rB) n * 1024 else n
        }
        return n * 1024
    }

    /** Sum of the address ranges of the mappings whose name contains "dmabuf" in /proc/<pid>/maps. */
    fun mapsBytes(lines: List<String>): Long {
        var sum = 0L
        for (l in lines) {
            if (!l.contains("dmabuf", ignoreCase = true)) continue
            val range = l.substringBefore(' ').split('-')
            if (range.size != 2) continue
            val a = range[0].toLongOrNull(16) ?: continue
            val b = range[1].toLongOrNull(16) ?: continue
            if (b > a) sum += b - a
        }
        return sum
    }
}

data class MemSample(val tMs: Long, val availMb: Long, val rssMb: Long, val swapFreeMb: Long, val dmabufMb: Long = -1)
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
        append("last=").append(last?.let { "${it.tMs},${it.availMb},${it.rssMb},${it.swapFreeMb},${it.dmabufMb}" } ?: "").append('\n')
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
            out += "  frei im System: ${l.availMb} MB, App (RSS): ${l.rssMb} MB, dma-buf der App: ${if (l.dmabufMb >= 0) "${l.dmabufMb} MB" else "nicht lesbar"}, freier Auslagerungsspeicher: ${l.swapFreeMb} MB, " +
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
                    last = kv["last"]?.takeIf { it.isNotBlank() }?.split(',')?.let { MemSample(it[0].toLong(), it[1].toLong(), it[2].toLong(), it[3].toLong(), it.getOrNull(4)?.toLongOrNull() ?: -1) },
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

    /** Tells the watchdog how big the context binaries are (for the line about how far the load got). */
    fun setBinBytes(bytes: Long) {}

    /** False when the dma-buf use cannot be read: a load would run without the brake that protects against the HyperOS limit. */
    fun canMeasureDmabuf(): Boolean = true

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
    private val intervalMs: Long = 100,
    /** Called with one compact line per sample (the Android app writes it to the run file, not to the screen). */
    private val onSampleLine: (String) -> Unit = {},
) : Watchdog {
    /** The size of the context binaries of the model being loaded, for the line "so weit kam das Laden" (0 = unknown). */
    @Volatile private var binBytes = 0L

    override fun setBinBytes(bytes: Long) {
        binBytes = bytes
    }

    /** True when the app's dma-buf use can be read at all (a number, even 0): without it the brake is blind. */
    override fun canMeasureDmabuf(): Boolean = meter.dmabufBytes() >= 0

    private val lock = Any()
    private var step = ""
    private var startedAt = 0L
    private var count = 0
    private var minAvail = Long.MAX_VALUE
    private var maxDma = -1L
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
            maxDma = -1
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
            val dma = meter.dmabufBytes()
            val s = MemSample(clock.nowMs() - startedAt, avail.toMb(), meter.rssBytes().toMb(), meter.swapFreeBytes().toMb(), dma.toMb())
            last = s
            count++
            if (s.availMb >= 0 && s.availMb < minAvail) minAvail = s.availMb
            if (s.dmabufMb > maxDma) maxDma = s.dmabufMb
            abort = MemoryGuard.decide(avail, maxTrimSinceStart, dma) as? MemoryGuard.Decision.Abort
            run {
                onSampleLine("Messung ${"%.1f".format(java.util.Locale.GERMAN, s.tMs / 1000.0)} s: frei ${s.availMb} MB, App ${s.rssMb} MB, dma-buf ${s.dmabufMb} MB")
            }
            if (abort != null) selfKill = "Selbst beendet: ${abort.reason}; so weit kam das Laden: ${progressNote(s)}"
            persist(finished = false)
        }
        if (abort != null) {
            running = false
            killer()
        }
    }

    private fun progressNote(s: MemSample): String {
        val bin = binBytes
        val ratio = if (bin > 0 && s.dmabufMb >= 0) " = ${"%.2f".format(java.util.Locale.GERMAN, s.dmabufMb * 1048576.0 / bin)}-fache der Rechenprogramme (${Metrics.mb(bin)})" else ""
        return "nach ${"%.1f".format(java.util.Locale.GERMAN, s.tMs / 1000.0)} s, dma-buf ${s.dmabufMb} MB$ratio, frei ${s.availMb} MB, App ${s.rssMb} MB"
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
                    "zuletzt ${last?.let { "${it.availMb} MB frei, App ${it.rssMb} MB, dma-buf ${it.dmabufMb} MB" } ?: "-"}, höchstes dma-buf ${if (maxDma < 0) "-" else "$maxDma MB"}, onTrimMemory-Meldungen: " +
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

/** Which lines of the previous run's memory measurement the next start shows. */
object StartupReport {
    /**
     * The block is shown when the sampling did not finish OR a step marker was left (the process ended inside a step). Version 0.5 showed it
     * only for an unfinished sampling, and on the fifth device run no block appeared at all (cause not proven, see docs).
     */
    fun lastRunLines(run: StoredRun?, interruptedStep: String?): List<String> = when {
        run != null && (!run.finished || interruptedStep != null) -> run.reportLines()
        run == null && interruptedStep != null -> listOf("Keine gespeicherte Speichermessung des vorherigen Laufs gefunden (Schritt '$interruptedStep' blieb offen).")
        else -> emptyList()
    }
}
