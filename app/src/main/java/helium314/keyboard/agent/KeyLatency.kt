// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent

/**
 * Stages 104/105: measurement points for the latency of a key (numbers only, never text). Off unless the device has the
 * log tag switched on (`adb shell setprop log.tag.SHLatency DEBUG`, as the emulator measurement of
 * `tools/devicetest/devicetest.py` does); the check is repeated at most every [CHECK_MILLIS], so the cost while typing is a
 * read of [enabled] and, now and then, one property lookup.
 *
 * One line per key: its kind (letter, space, enter, delete, other), the time from the end of the touch
 * (`MotionEvent.getEventTime`, uptime) to the end of `LatinIME.onEvent`, and the time `onEvent` itself took. A second kind of
 * line gives the time of a sentence check at the end of a key ([retro], already part of the key's time).
 */
object KeyLatency {
    const val TAG = "SHLatency"
    private const val CHECK_MILLIS = 3_000L

    @Volatile @JvmField var enabled = false
    /** Uptime of the last property check; far in the past at the start (no overflow in the difference). */
    @Volatile private var checkedAt = -CHECK_MILLIS * 1000
    @Volatile private var lastUpMillis = 0L
    private var startNanos = 0L

    private fun refresh(now: Long) {
        if (now - checkedAt < CHECK_MILLIS) return
        checkedAt = now
        enabled = try { Log.isLoggable(TAG, Log.DEBUG) } catch (_: Throwable) { false }
    }

    /** A touch event reached the keyboard view (main thread). */
    @JvmStatic fun touch(action: Int, eventTime: Long) {
        if (!enabled) return
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) lastUpMillis = eventTime
    }

    /** `LatinIME.onEvent` starts. */
    @JvmStatic fun eventStarted() {
        refresh(SystemClock.uptimeMillis())
        if (enabled) startNanos = System.nanoTime()
    }

    /** `LatinIME.onEvent` is done with the key of [codePoint] / [keyCode]. */
    @JvmStatic fun eventEnded(codePoint: Int, keyCode: Int) {
        if (!enabled) return
        // review N4: not even numbers from a password field (length and rhythm of a password)
        if (try { helium314.keyboard.latin.settings.Settings.getValues()?.mInputAttributes?.mIsPasswordField == true } catch (_: Throwable) { true }) return
        try {
            val workMicros = (System.nanoTime() - startNanos) / 1000
            val up = lastUpMillis
            lastUpMillis = 0L
            val sinceTouch = if (up > 0) SystemClock.uptimeMillis() - up else -1L
            val kind = when {
                codePoint == ' '.code -> "space"
                codePoint == '\n'.code -> "enter"
                codePoint > 0 && Character.isLetter(codePoint) -> "letter"
                keyCode == helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode.DELETE -> "delete"
                else -> "other"
            }
            Log.d(TAG, "key=$kind touch_ms=$sinceTouch work_us=$workMicros")
        } catch (_: Throwable) {}
    }

    /** A sentence check took [nanos]; [outcome] is a fixed word (none, applied, refused, reverted, private). */
    @JvmStatic fun retro(nanos: Long, outcome: String) {
        if (!enabled) return
        try { Log.d(TAG, "retro=$outcome work_us=${nanos / 1000}") } catch (_: Throwable) {}
    }
}
