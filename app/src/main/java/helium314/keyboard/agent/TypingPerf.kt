// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.os.Looper
import java.util.concurrent.atomic.AtomicLong

/**
 * Stage 64a: what the fork's own code costs per key press, for the card "Tippen" on the Diagnose page.
 *
 * Numbers only, never text: the API takes a [Hook] (a fixed name) and counts, nothing a user typed or a
 * field held. Everything is in memory (a ring of the last [PRESSES] key presses and the [SLOWEST] slowest
 * hook runs), gone with the process, and [reset] clears it.
 *
 * Cost while typing: a measured place calls [begin] and [end] (two `System.nanoTime()` and a few array
 * writes), a request counter is one atomic increment, and [keyPress] closes the previous press into the
 * ring. No allocation on any of these paths, no string building, no logging. Only the main thread is timed
 * (the fork's work on other threads costs the main thread nothing); the request counters count every thread.
 *
 * A "key press" runs from one [keyPress] call to the next, so the work a press causes after the key itself
 * (the strip update, the learning hooks) belongs to it. Hooks nest (the chip sources run inside the chips
 * of the strip): only the outermost one adds to the time of a press, the inner ones still show up in the
 * list of slow runs.
 */
object TypingPerf {
    /** The places the fork measures, by a fixed name (never anything typed). */
    enum class Hook {
        /** The text before the cursor read for a typed command: guard and parse. */
        COMMAND,
        /** The whole chip row of the strip (all chip sources below run inside it). */
        STRIP_CHIPS,
        FORMAT_CHIPS,
        FIELD_CHIPS,
        MEMORY_CHIPS,
        UPDATE_CHIP,
        /** Settings and data the keyboard reads when a field starts. */
        FIELD_START,
        /** What the fork does with a word the engine just committed (memory). */
        WORD_COMMIT,
        /** The learning hooks (typos, field corrections, touch). */
        LEARNING,
    }

    /** Key presses kept; older ones fall out. */
    const val PRESSES = 500
    /** Slowest hook runs kept. */
    const val SLOWEST = 10
    private const val DEPTH = 8

    /** One slow hook run: [nanos] it took and [requests] (app and system) that happened meanwhile. */
    class Run(val hook: Hook, val nanos: Long, val requests: Int)

    /**
     * What the card shows. [presses] closed key presses since [sinceMillis]; the times are per press (the fork's
     * time on the main thread, in nanoseconds), the requests per press as counted.
     */
    class Snapshot(
        val presses: Int, val sinceMillis: Long,
        val medianNanos: Long, val p95Nanos: Long, val maxNanos: Long,
        val appAverage: Double, val appMax: Int, val systemAverage: Double, val systemMax: Int,
        val slowest: List<Run>,
    )

    /** The clock, replaced in tests. */
    fun interface NanoSource { fun now(): Long }
    @Volatile internal var nanoSource = NanoSource { System.nanoTime() }
    @Volatile internal var wallClock: () -> Long = { System.currentTimeMillis() }

    private val mainThread: Thread? by lazy { runCatching { Looper.getMainLooper()?.thread }.getOrNull() }
    /** Tests run hooks on their own thread. */
    @Volatile internal var timedThread: Thread? = null

    // requests, counted by every thread
    private val appRequests = AtomicLong()
    private val systemRequests = AtomicLong()

    // state of the main thread, under [lock] only where another thread (the settings page) can read it
    private val lock = Any()
    private val startNanos = LongArray(DEPTH)
    private val startRequests = LongArray(DEPTH)
    private var depth = 0
    private var pressNanos = 0L

    private val ringNanos = LongArray(PRESSES)
    private val ringApp = IntArray(PRESSES)
    private val ringSystem = IntArray(PRESSES)
    private var ringCount = 0
    private var ringNext = 0
    private var pressesSeen = false
    private var lastApp = 0L
    private var lastSystem = 0L
    private var sinceMillis = wallClock()

    private val slowHook = arrayOfNulls<Hook>(SLOWEST)
    private val slowNanos = LongArray(SLOWEST)
    private val slowRequests = IntArray(SLOWEST)
    private var slowCount = 0
    @Volatile private var slowMin = 0L

    /**
     * One reading request to the edited app (an `InputConnection` call that leaves the process: text before or after
     * the cursor, the selection, the extracted text). Writing calls (`commitText`, `setComposingText`,
     * `deleteSurroundingText`, `sendKeyEvent`, `setSelection`) are not counted. Never throws.
     */
    @JvmStatic fun app() { appRequests.incrementAndGet() }

    /** One request to the system (lock state, clipboard, device abilities). Never throws. */
    @JvmStatic fun system() { systemRequests.incrementAndGet() }

    /** Requests counted so far (for tests and the hooks). */
    internal fun appRequestCount() = appRequests.get()
    internal fun systemRequestCount() = systemRequests.get()

    /** A key press starts: closes the previous one into the ring. Main thread; never throws. */
    @JvmStatic fun keyPress() {
        try {
            if (!timed()) return
            synchronized(lock) {
                val app = appRequests.get()
                val system = systemRequests.get()
                if (pressesSeen) {
                    ringNanos[ringNext] = pressNanos
                    ringApp[ringNext] = (app - lastApp).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    ringSystem[ringNext] = (system - lastSystem).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    ringNext = (ringNext + 1) % PRESSES
                    if (ringCount < PRESSES) ringCount++
                }
                pressesSeen = true
                pressNanos = 0L
                lastApp = app
                lastSystem = system
            }
        } catch (_: Throwable) {}
    }

    private fun timed(): Boolean {
        val current = Thread.currentThread()
        return current === timedThread || current === mainThread
    }

    /**
     * Starts measuring one run of a hook; the result goes to [end]. -1 (not measured) off the main thread
     * or when nested too deep. Never throws.
     */
    @JvmStatic fun begin(): Int {
        try {
            if (!timed()) return -1
            val d = depth
            if (d >= DEPTH) return -1
            startNanos[d] = nanoSource.now()
            startRequests[d] = appRequests.get() + systemRequests.get()
            depth = d + 1
            return d
        } catch (_: Throwable) { return -1 }
    }

    /** Ends the run [begin] started (its result is [token]). Never throws. */
    @JvmStatic fun end(hook: Hook, token: Int) {
        if (token < 0) return
        try {
            val elapsed = nanoSource.now() - startNanos[token]
            val requests = (appRequests.get() + systemRequests.get() - startRequests[token]).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            depth = token
            if (token == 0) synchronized(lock) { pressNanos += elapsed }
            if (elapsed > slowMin || slowCount < SLOWEST) record(hook, elapsed, requests)
        } catch (_: Throwable) {}
    }

    private fun record(hook: Hook, nanos: Long, requests: Int) = synchronized(lock) {
        if (slowCount < SLOWEST) {
            slowHook[slowCount] = hook; slowNanos[slowCount] = nanos; slowRequests[slowCount] = requests; slowCount++
        } else {
            var smallest = 0
            for (i in 1 until SLOWEST) if (slowNanos[i] < slowNanos[smallest]) smallest = i
            if (nanos <= slowNanos[smallest]) return@synchronized
            slowHook[smallest] = hook; slowNanos[smallest] = nanos; slowRequests[smallest] = requests
        }
        slowMin = if (slowCount < SLOWEST) 0L else slowNanos.min()
    }

    /** Measures [block] as one run of [hook]. */
    inline fun <T> measure(hook: Hook, block: () -> T): T {
        val token = begin()
        try {
            return block()
        } finally {
            end(hook, token)
        }
    }

    /** Forgets everything measured and starts again (the button of the card). */
    @JvmStatic fun reset() = synchronized(lock) {
        ringCount = 0; ringNext = 0; pressesSeen = false
        slowCount = 0; slowMin = 0L
        for (i in 0 until SLOWEST) slowHook[i] = null
        pressNanos = 0L
        depth = 0
        lastApp = appRequests.get(); lastSystem = systemRequests.get()
        sinceMillis = wallClock()
    }

    /** The numbers of the closed key presses, for the card. */
    fun snapshot(): Snapshot = synchronized(lock) {
        val n = ringCount
        val times = LongArray(n)
        var appSum = 0L; var appMax = 0; var systemSum = 0L; var systemMax = 0
        for (i in 0 until n) {
            times[i] = ringNanos[i]
            appSum += ringApp[i]; if (ringApp[i] > appMax) appMax = ringApp[i]
            systemSum += ringSystem[i]; if (ringSystem[i] > systemMax) systemMax = ringSystem[i]
        }
        times.sort()
        fun percentile(p: Double): Long = if (n == 0) 0L else times[(Math.ceil(p * n).toInt() - 1).coerceIn(0, n - 1)]
        val slowest = (0 until slowCount).map { Run(slowHook[it]!!, slowNanos[it], slowRequests[it]) }.sortedByDescending { it.nanos }
        Snapshot(n, sinceMillis, percentile(0.5), percentile(0.95), if (n == 0) 0L else times[n - 1],
            if (n == 0) 0.0 else appSum.toDouble() / n, appMax, if (n == 0) 0.0 else systemSum.toDouble() / n, systemMax, slowest)
    }

    // --- failures of single commands (stage 64c): the command name and the class of the exception, never its message ---

    /** A command that threw and was skipped: its [name] (a fixed built-in name), how often and the exception class. */
    class Failure(val name: String, val count: Int, val errorClass: String)

    private val failures = LinkedHashMap<String, Pair<Int, String>>()

    /** Counts one failure of the command [name] (call sites pass the registry name). The message is never read. */
    @JvmStatic fun commandFailed(name: String, error: Throwable) {
        try {
            val cls = error.javaClass.simpleName.ifEmpty { "Throwable" }
            synchronized(failures) {
                if (failures.size >= MAX_FAILURE_NAMES && name !in failures) return
                val old = failures[name]
                failures[name] = ((old?.first ?: 0) + 1) to cls
            }
        } catch (_: Throwable) {}
    }

    private const val MAX_FAILURE_NAMES = 40

    fun failures(): List<Failure> = synchronized(failures) { failures.map { Failure(it.key, it.value.first, it.value.second) } }

    fun resetFailures() = synchronized(failures) { failures.clear() }
}
