// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

/**
 * Stage 56 "Neue Builds sofort melden": when the keyboard opens it may ask the public release list
 * once, at most every [INTERVAL_MILLIS] since the last attempt. Each failed check in a row doubles
 * the wait, up to [MAX_INTERVAL_MILLIS]; a successful one (also "not modified") resets it.
 * Pure rules, no clock and no storage of its own.
 */
object UpdateOnOpenPolicy {
    const val INTERVAL_MILLIS = 15L * 60 * 1000
    const val MAX_INTERVAL_MILLIS = 4L * 60 * 60 * 1000
    /** Failures beyond this change nothing more (15 min · 2⁴ = 4 h already). */
    const val MAX_FAILURES = 4
    /** Longest ETag that is stored and sent back; GitHub's are about 70 characters. */
    const val MAX_ETAG_LENGTH = 256

    /** The wait after [failures] failed checks in a row: 15 min, 30 min, 1 h, 2 h, then 4 h. */
    fun interval(failures: Int): Long {
        val steps = failures.coerceIn(0, MAX_FAILURES)
        return (INTERVAL_MILLIS shl steps).coerceAtMost(MAX_INTERVAL_MILLIS)
    }

    /**
     * Whether a check may start at [now] after the attempt at [lastAttempt] (0 = never). A clock
     * set back before the last attempt allows one check, which then stores the new time.
     */
    fun due(now: Long, lastAttempt: Long, failures: Int): Boolean =
        lastAttempt <= 0 || now < lastAttempt || now - lastAttempt >= interval(failures)

    /** Failed checks in a row after one more check: a success resets, a failure counts up to [MAX_FAILURES]. */
    fun failuresAfter(previous: Int, success: Boolean): Int =
        if (success) 0 else (previous.coerceIn(0, MAX_FAILURES) + 1).coerceAtMost(MAX_FAILURES)

    /**
     * The `ETag` header of a 200 answer as it may be stored and sent back in `If-None-Match`, or
     * null: only visible ASCII without spaces (a quoted value, maybe with `W/`), at most
     * [MAX_ETAG_LENGTH] characters.
     */
    fun etag(header: String?): String? =
        header?.takeIf { it.length in 1..MAX_ETAG_LENGTH && it.all { c -> c in '!'..'~' } }
}
