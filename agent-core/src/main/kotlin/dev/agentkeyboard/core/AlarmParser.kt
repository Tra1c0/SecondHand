// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

/**
 * Reads the alarm time of `@wecker 6:30 Training` with the time rules of [EventParser] in alarm
 * mode: "7:30", "7 Uhr", "7" (a bare number is an hour), "halb acht" (7:30, colloquial times stay
 * in the morning), "viertel nach sechs", "6pm", "abends halb acht" (19:30). The other words are
 * the alarm's label. Pure and offline; "now" is passed in.
 *
 * The clock app's "set alarm" takes only hour and minute and rings at their next occurrence. A day
 * word ("morgen", "Montag", "24.12.") is therefore only checked: if the day is not that next
 * occurrence, [Result.dayIgnored] is true and the command says so instead of pretending.
 */
object AlarmParser {
    /** [time] in 24-hour form; [title] the remaining words; [dayIgnored] if a typed day cannot be set. */
    data class Result(val time: ClockTime, val title: String, val dayIgnored: Boolean)

    /** Null without a time, for an impossible time or date, or for text over [EventParser.MAX_INPUT] characters. */
    fun parse(text: String, now: LocalMoment, language: String): Result? {
        val found = EventParser.parse(text, now, language, alarm = true) ?: return null
        val time = found.time ?: return null
        return Result(time, found.title, dayIgnored = found.date != nextDay(time, now))
    }

    /** The day an alarm at [time] rings: today if [time] is still ahead, otherwise tomorrow. */
    fun nextDay(time: ClockTime, now: LocalMoment): CivilDate = if (time > now.time) now.date else now.date.plusDays(1)
}
