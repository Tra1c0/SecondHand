// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import helium314.keyboard.latin.settings.SettingsValues
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Stages 107/108: the corrections the keyboard's own weighted rules offered as automatic correction lately (rare word ->
 * very common neighbour, "gibts" -> "gibt's"), so that keeping or undoing one counts in the pair's weights
 * ([RejectedCorrections] stores such a pair with "Personalisierte Vorschläge" on, also without the switch "Rückgängig
 * gemachte Korrekturen merken"), and two counters for the diagnosis card (numbers only, since the keyboard started). Kept in memory only, never
 * logged; the pairs are the words of the current typing and are dropped beyond [MAX_RECENT].
 */
object RuleCorrections {
    private const val MAX_RECENT = 16
    private const val SEPARATOR = '\u0000'
    private val recent = LinkedHashSet<String>()
    private val lock = Any()

    /** Automatic rule corrections committed (kept at that moment) and taken back with Backspace, since the start. */
    @JvmField val kept = AtomicInteger()
    @JvmField val undone = AtomicInteger()

    private fun key(typed: String, correction: String) = typed.lowercase(Locale.ROOT) + SEPARATOR + correction.lowercase(Locale.ROOT)

    /** The suggestion thread offers [correction] for [typed] as the automatic correction of a rule. */
    @JvmStatic fun offered(typed: String, correction: String) {
        val k = key(typed, correction)
        synchronized(lock) {
            recent.remove(k)
            recent.add(k)
            while (recent.size > MAX_RECENT) recent.remove(recent.first())
        }
    }

    @JvmStatic fun wasOffered(typed: String, correction: String): Boolean = synchronized(lock) { key(typed, correction) in recent }

    /** A rejection of a rule correction is learning data: only with "Personalisierte Vorschläge", not paused, incognito or locked. */
    @JvmStatic fun mayLearn(settings: SettingsValues?): Boolean =
        settings != null && settings.mUsePersonalizedDicts && !settings.mLearningPaused && !settings.mIncognitoModeEnabled && !settings.mIsLocked

    /** A word was committed; counts a rule correction that went into the field. */
    @JvmStatic fun onCommitted(typed: String?, word: String) {
        if (typed != null && typed != word && wasOffered(typed, word)) kept.incrementAndGet()
    }

    internal fun resetForTest() { synchronized(lock) { recent.clear() }; kept.set(0); undone.set(0) }
}
