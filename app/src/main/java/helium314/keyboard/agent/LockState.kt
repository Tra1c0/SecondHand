// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Stage 64b: one shared, short-lived answer to "is the device (or the user's storage) locked?", so the
 * strip update, the word hooks and the field start do not each call into the system (P2). Until now
 * every word, space and strip update asked `KeyguardManager` and `UserManager` again, in four or five
 * places.
 *
 * Fail safe by construction: an answer "unlocked" is trusted for [UNLOCKED_MILLIS] only (about a second),
 * "locked" for [LOCKED_MILLIS]; a failing read counts as locked. The answer is dropped at once on
 * `ACTION_SCREEN_OFF`, `ACTION_SCREEN_ON` and `ACTION_USER_PRESENT` (a receiver registered with the first
 * read), at every field start ([drop], called from [TouchModel.load]) and at every tap on a chip
 * ([KeyboardCommands.chipRow], so what a tap inserts is checked live). A lock between two key presses is
 * therefore seen by the next read after the broadcast, and at the latest after [UNLOCKED_MILLIS].
 *
 * Callers that protect data from a locked device keep their protection: they ask [isLocked] where they
 * asked the system before. The few that decide something irreversible (the vault, the credential store,
 * the editor panel) keep their live reads.
 */
object LockState {
    const val UNLOCKED_MILLIS = 1_000L
    const val LOCKED_MILLIS = 5_000L

    private class Answer(val locked: Boolean, val at: Long)

    /** One cached question; [read] is asked when no fresh answer is held. */
    class Probe internal constructor(private val defaultRead: (Context) -> Boolean) {
        @Volatile private var answer: Answer? = null
        /** The real question, replaced in tests. */
        @Volatile var read: (Context) -> Boolean = defaultRead
        /** How often [read] was asked (tests). */
        @Volatile var reads = 0
            internal set

        fun isLocked(context: Context): Boolean {
            val now = LockState.clock()
            val known = answer
            if (known != null) {
                val age = now - known.at
                if (age >= 0 && age < (if (known.locked) LOCKED_MILLIS else UNLOCKED_MILLIS)) return known.locked
            }
            LockState.register(context)
            reads++
            TypingPerf.system()
            val value = try { read(context) } catch (_: Throwable) { true }
            answer = Answer(value, now)
            return value
        }

        internal fun drop() { answer = null }
        internal fun reset() { answer = null; reads = 0; read = defaultRead }
    }

    /** Device locked, or the user's storage not unlocked yet (what the learning stores and chips ask). */
    @JvmField val normal = Probe { context ->
        helium314.keyboard.compat.isDeviceLocked(context) || runCatching { helium314.keyboard.compat.isUserLocked(context) }.getOrDefault(false)
    }
    /** The stricter question of the credential store (the screen lock is showing, too); for the update chip. */
    @JvmField val credentials = Probe { context -> !helium314.keyboard.agent.security.credentialAccessAllowed(context) }

    private val registered = AtomicBoolean(false)

    /** Monotonic clock, replaced in tests. */
    @JvmStatic var clock: () -> Long = { SystemClock.elapsedRealtime() }

    /** Whether the device or the user's storage is locked; never throws, "locked" when it cannot tell. */
    @JvmStatic fun isLocked(context: Context): Boolean = normal.isLocked(context)

    /**
     * Forgets the answers: the next ask goes to the system. Field start, screen events, a tap on a chip. Also drops the
     * clip caches ([ClipVault.forgetCaches]): they hold clip text and must not survive a lock.
     */
    @JvmStatic fun drop() { normal.drop(); credentials.drop(); ClipVault.forgetCaches() }

    private fun register(context: Context) {
        if (!registered.compareAndSet(false, true)) return
        try {
            val app = context.applicationContext ?: return
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) { drop() }
            }, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (_: Throwable) {
            // without the receiver the short lifetime of an answer still keeps it safe
        }
    }

    /** Tests: forget the answers, the counters and the replaced seams. */
    internal fun resetForTest() {
        registered.set(false) // every test has its own application
        normal.reset()
        credentials.reset()
        clock = { SystemClock.elapsedRealtime() }
    }
}
