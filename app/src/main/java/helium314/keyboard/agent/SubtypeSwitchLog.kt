// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.os.SystemClock
import android.view.inputmethod.InputMethodSubtype
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.utils.locale
import java.util.Locale

/**
 * Stage 30 (Diagnose): why the keyboard language changed, for the umlaut riddle on the owner's
 * phone (P66): did the app's hint, the per-app language, a language command, the keyboard's own
 * language key or picker, or the system switch it?
 *
 * Memory only, never stored or logged, gone with the process: the last [MAX_ENTRIES] switches with
 * their reason, when they happened ([SystemClock.elapsedRealtime]) and the language tag of the new
 * subtype. No field contents, no package names.
 *
 * LatinIME calls in with one line per path: [expectCommand] (language command), [expectFromStart]
 * (onStartInputInternal posts a switch for hint locales or the per-app subtype), [internalSwitch]
 * (every switchToSubtype), [onSwitched] (onCurrentInputMethodSubtypeChanged once the subtype really
 * changes). A change without an internal switch just before it came from the system.
 * [SubtypeChoice.holds][helium314.keyboard.agent.commands.SubtypeChoice.holds] reports [onKept].
 */
object SubtypeSwitchLog {
    enum class Reason {
        /** The field's hint locales (EditorInfo.hintLocales). */
        APP_HINT,
        /** A field without hints after one with: back to the subtype the hint replaced. */
        HINT_ENDED,
        /** The subtype saved for the app ("Sprache pro App"). */
        PER_APP,
        /** A language command (`@deutsch` …). */
        COMMAND,
        /** The keyboard itself: language key, language picker, space bar slide, a subtype turned off. */
        KEYBOARD,
        /** The system called onCurrentInputMethodSubtypeChanged (system picker, MIUI …). */
        SYSTEM,
        /** No switch: the app restarted the field and the command's choice was kept (P66). */
        KEPT_BY_CHOICE,
    }

    class Entry(val reason: Reason, val atElapsedMillis: Long, val languageTag: String)

    const val MAX_ENTRIES = 5
    /** A reason noted for a coming switch counts only this long (posted switches run right away). */
    private const val NOTE_MILLIS = 2_000L

    private class Note(val reason: Reason, val target: InputMethodSubtype, val at: Long)

    private val entries = ArrayDeque<Entry>()
    /** Set by a specific path (command, field start), taken by the next [internalSwitch] to the same subtype. */
    private var expected: Note? = null
    /** Set by [internalSwitch], taken by [onSwitched]. */
    private var pending: Note? = null

    /** A language command switches to [target] now. */
    @JvmStatic @Synchronized fun expectCommand(target: InputMethodSubtype) {
        expected = Note(Reason.COMMAND, target, now())
    }

    /**
     * onStartInputInternal posts a switch to [target], chosen by HeliBoard's `SubtypeState` from the
     * field's [hintLocales] and the [subtypeForApp] (same order of rules).
     */
    @JvmStatic @Synchronized fun expectFromStart(target: InputMethodSubtype, subtypeForApp: RichInputMethodSubtype?, hintLocales: List<Locale>) {
        val reason = when {
            hintLocales.isEmpty() && subtypeForApp == null -> Reason.HINT_ENDED
            subtypeForApp != null && subtypeForApp.rawSubtype == target -> Reason.PER_APP
            else -> Reason.APP_HINT
        }
        expected = Note(reason, target, now())
    }

    /** LatinIME.switchToSubtype: an internal switch; the keyboard's own unless a path noted more. */
    @JvmStatic @Synchronized fun internalSwitch(target: InputMethodSubtype) {
        val noted = expected?.takeIf { it.target == target && fresh(it) }
        expected = null
        pending = Note(noted?.reason ?: Reason.KEYBOARD, target, now())
    }

    /** onCurrentInputMethodSubtypeChanged after its early returns: the subtype really changes to [subtype]. */
    @JvmStatic @Synchronized fun onSwitched(subtype: InputMethodSubtype) {
        val noted = pending?.takeIf { it.target == subtype && fresh(it) }
        pending = null
        add(Entry(noted?.reason ?: Reason.SYSTEM, now(), tag(subtype)))
    }

    /** The app restarted the field and a command's choice of [subtype] was kept; repeated restarts make one entry. */
    @JvmStatic @Synchronized fun onKept(subtype: InputMethodSubtype?) {
        val tag = subtype?.let { tag(it) } ?: return
        val last = entries.firstOrNull()
        if (last != null && last.reason == Reason.KEPT_BY_CHOICE && last.languageTag == tag) entries.removeFirst()
        add(Entry(Reason.KEPT_BY_CHOICE, now(), tag))
    }

    /** Newest first. */
    @JvmStatic @Synchronized fun entries(): List<Entry> = entries.toList()

    @JvmStatic @Synchronized fun clear() {
        entries.clear()
        expected = null
        pending = null
    }

    private fun add(entry: Entry) {
        entries.addFirst(entry)
        while (entries.size > MAX_ENTRIES) entries.removeLast()
    }

    private fun tag(subtype: InputMethodSubtype) = runCatching { subtype.locale().toLanguageTag() }.getOrDefault("?")

    private fun fresh(note: Note) = now() - note.at <= NOTE_MILLIS

    private fun now() = SystemClock.elapsedRealtime()
}
