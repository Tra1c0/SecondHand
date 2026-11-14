// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import dev.agentkeyboard.core.suggest.ContextWeights
import dev.agentkeyboard.core.suggest.SuggestKind
import helium314.keyboard.latin.settings.SettingsValues

/**
 * Stage 45 context learning: which context chips the owner taps or passes by, as small bounded
 * weights per cue id and kind ([ContextWeights]); no text, no app, no field is stored. Only with
 * the learning switch ("Personalisierte Vorschläge") on, not paused, not in incognito (which also
 * covers no-learning and password fields) and not while locked. Used only while the learning switch
 * is on. Device-protected storage like the other learned data (`agent_context_weights`); deleted with
 * "Gelernte Wörter löschen".
 */
object ContextLearning {
    const val FILE = "agent_context_weights"
    private const val KEY = "weights"
    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

    /** Wall clock, replaced in tests. */
    @JvmStatic var clock: () -> Long = { System.currentTimeMillis() }
    @Volatile private var cached: ContextWeights? = null
    private val lock = Any()

    private fun today() = clock() / DAY_MILLIS
    private fun prefs(context: Context) = AgentStorageContexts.keyboard(context).getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun mayLearn(settings: SettingsValues?): Boolean = settings != null && settings.mUsePersonalizedDicts && !settings.mLearningPaused &&
        !settings.mIncognitoModeEnabled && !settings.mIsLocked

    /** The weights for scoring: the learned ones while learning is switched on, else none. */
    fun weights(context: Context, settings: SettingsValues?): ContextWeights {
        if (settings == null || !settings.mUsePersonalizedDicts) return ContextWeights.empty(today())
        return loaded(context)
    }

    private fun loaded(context: Context): ContextWeights {
        cached?.let { return it.decayed(today()) }
        synchronized(lock) {
            cached?.let { return it.decayed(today()) }
            val read = ContextWeights.parse(runCatching { prefs(context).getString(KEY, null) }.getOrNull(), today())
            cached = read
            return read
        }
    }

    /** The chip for [cueId] and [kind] was tapped ([accepted]) or went away without a tap. */
    fun record(context: Context, settings: SettingsValues?, cueId: String, kind: SuggestKind, accepted: Boolean) {
        if (!mayLearn(settings)) return
        synchronized(lock) {
            val current = loaded(context)
            val next = if (accepted) current.accept(cueId, kind, today()) else current.ignore(cueId, kind, today())
            cached = next
            runCatching { prefs(context).edit().putString(KEY, next.encode()).apply() }
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            check(prefs(context).edit().clear().commit())
            cached = ContextWeights.empty(today())
        }
    }

    /** What is stored, for the tests and the docs: only "v1;<day>;<id>:<KIND>=<number>;…". */
    internal fun stored(context: Context): String? = prefs(context).getString(KEY, null)

    internal fun resetForTest() = synchronized(lock) { cached = null }
}
