// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import dev.agentkeyboard.core.EmojiKeywords
import helium314.keyboard.keyboard.emoji.SupportedEmojis
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.settings.SettingsValues
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tolerant emoji suggestions in the strip (stage 35): word forms, case and umlauts, word starts,
 * one typo, German and English mixed, synonyms ([EmojiKeywords]). Local only, no network.
 *
 * The keyword table (`assets/emoji_keywords.bin`, about 120 KB) is read once on a background
 * thread the first time a word is typed; until it is there, nothing is added, so typing never
 * waits for it. Each lookup is a few hash lookups and at most a short scan of sorted keys.
 *
 * Added emojis go where HeliBoard puts its own emoji suggestions: after the first two words, so
 * they never become the autocorrection; together with HeliBoard's emojis there are at most
 * [MAX_EMOJIS]. Off with the emoji suggestion setting, in password fields, in incognito and while
 * the device is locked.
 */
object EmojiSuggestions {
    const val MAX_EMOJIS = 2
    private const val ASSET = "emoji_keywords.bin"

    /** Marks the emojis this adds; the type is the emoji dictionary's, so they are treated like its emojis. */
    @JvmField val DICTIONARY = Dictionary.PhonyDictionary(Dictionary.TYPE_EMOJI)

    @Volatile private var table: EmojiKeywords? = null
    private val loadStarted = AtomicBoolean(false)

    /** Starts reading the table once, off the calling thread. */
    fun ensureLoading(context: Context) {
        if (!loadStarted.compareAndSet(false, true)) return
        val app = context.applicationContext ?: context
        Thread({
            table = try {
                app.assets.open(ASSET).use { EmojiKeywords.read(it) }
            } catch (e: Exception) {
                // no table, no extra emojis; never logs typed text
                android.util.Log.w("EmojiSuggestions", "emoji keyword table not readable: ${e.javaClass.simpleName}")
                null
            }
        }, "agent-emoji-keywords").apply { isDaemon = true; start() }
    }

    private const val LOCK_CHECK_MS = 2_000L
    @Volatile private var lockCheckedAt = 0L
    @Volatile private var locked = false

    /** `KeyguardManager.isDeviceLocked` is a call into the system: asked at most every [LOCK_CHECK_MS]. */
    private fun isLocked(context: Context): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        if (lockCheckedAt == 0L || now - lockCheckedAt >= LOCK_CHECK_MS) {
            locked = helium314.keyboard.compat.isDeviceLocked(context)
            lockCheckedAt = now
        }
        return locked
    }

    /** Tests: use [keywords] (null = no table) and never load from assets; forgets the lock state. */
    fun setTableForTest(keywords: EmojiKeywords?) {
        loadStarted.set(true)
        table = keywords
        lockCheckedAt = 0L
    }

    /** Tests: ask the lock state again on the next hit. */
    fun forgetLockStateForTest() { lockCheckedAt = 0L }

    fun isOn(settings: SettingsValues): Boolean =
        settings.mSuggestEmojis && !settings.mIncognitoModeEnabled && !settings.mInputAttributes.mIsPasswordField

    /** Main language first, then German and English (mixed typing), Spanish only as main language. */
    fun languages(mainLocale: Locale?): List<String> =
        (listOfNotNull(mainLocale?.language?.takeIf { it.isNotEmpty() }) + listOf("de", "en")).distinct()

    /**
     * Adds up to [MAX_EMOJIS] minus the emojis already in [suggestions] for [typedWord]. [typedIsWord]:
     * the word is in a dictionary (no word starts, no typos). With [hasAutoCorrection] nothing is put
     * at the first place, which would be the correction.
     */
    fun addTo(
        suggestions: MutableList<SuggestedWordInfo>, typedWord: String, mainLocale: Locale?, typedIsWord: Boolean,
        hasAutoCorrection: Boolean, settings: SettingsValues, context: Context?,
    ) {
        if (!isOn(settings) || typedWord.none { it.isLetter() }) return
        if (KeyboardCommands.isCommandWord(typedWord)) return
        val keywords = table ?: run { context?.let { ensureLoading(it) }; return }
        val present = suggestions.count { it.isEmoji }
        val wanted = MAX_EMOJIS - present
        if (wanted <= 0) return
        val found = keywords.suggest(typedWord, languages(mainLocale), limit = MAX_EMOJIS + 2, isWord = typedIsWord)
            .filter { emoji -> !SupportedEmojis.isUnsupported(emoji) && suggestions.none { it.mWord == emoji } }
            .take(wanted)
        if (found.isEmpty()) return
        // locked device: nothing extra (asked only after a hit, at most every LOCK_CHECK_MS)
        if (context != null && isLocked(context)) return
        var index = minOf(2, suggestions.size)
        // keep HeliBoard's own emojis first
        while (index < suggestions.size && suggestions[index].isEmoji) index++
        if (index == 0 && hasAutoCorrection) return
        for (emoji in found) {
            suggestions.add(index++, SuggestedWordInfo(emoji, "", 0, SuggestedWordInfo.KIND_CORRECTION, DICTIONARY,
                SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE))
        }
    }
}
