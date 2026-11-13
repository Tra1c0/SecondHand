// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.edit
import dev.agentkeyboard.core.EmojiClusters
import dev.agentkeyboard.core.EmojiMix
import dev.agentkeyboard.core.EmojiMixTable
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.utils.prefs

/**
 * Stage 32: the pairs mixed last, for the row "Zuletzt gemischt" in the mixer of the emoji palette.
 * Stored are only pair keys (code points, see [EmojiMix.key]), at most [MAX], newest first, in the
 * preference [PREF]. A pair counts as mixed when a tap made its sticker ready ([EmojiMixer.request])
 * and again when its sticker went out (inserted or shared). The row shows a pair only while its
 * image is in the cache on the device ([shown]), so it never loads anything from the network; a pair
 * the cache dropped disappears. Nothing is recorded in incognito mode, in a password field or on a
 * locked device ([recording]). "Gespeicherte Mischungen löschen" clears the list ([clear]).
 * Main thread only; pictures are decoded on the worker of [EmojiMixer].
 */
object EmojiMixRecents {
    const val PREF = "agent_emoji_mix_recent"
    const val MAX = 12
    /** Longest side of a picture in the row. */
    const val PICTURE_PX = 160
    private val KEY = Regex("[0-9a-f]{1,6}(-[0-9a-f]{1,6})*_[0-9a-f]{1,6}(-[0-9a-f]{1,6})*")

    /** False in incognito mode (also a password field or a field that asks for no learning) and on a locked device. */
    fun recording(): Boolean {
        val values: SettingsValues? = Settings.getValues()
        return values != null && !values.mIncognitoModeEnabled && !values.mIsLocked
    }

    /** The stored keys, newest first; anything that is not a pair key is ignored. */
    fun keys(context: Context): List<String> =
        context.prefs().getString(PREF, null)?.split(',')?.filter { KEY.matches(it) }?.distinct()?.take(MAX) ?: emptyList()

    /** [mix] becomes the newest entry; the oldest beyond [MAX] goes. Nothing while not [recording]. */
    fun record(context: Context, mix: EmojiMix) {
        if (!recording() || !KEY.matches(mix.key)) return
        val keys = listOf(mix.key) + keys(context).filter { it != mix.key }
        context.prefs().edit { putString(PREF, keys.take(MAX).joinToString(",")) }
    }

    /** Forgets the list and the pictures (with "Gespeicherte Mischungen löschen"). */
    fun clear(context: Context) {
        context.prefs().edit { remove(PREF) }
        forgetPictures()
    }

    /** The stored pairs whose image is on the device, newest first. Reads no image and nothing from the network. */
    fun shown(context: Context, table: EmojiMixTable): List<EmojiMix> {
        val cache = EmojiMixer.cache(context)
        return keys(context).mapNotNull { mixOf(table, it) }.filter { cache.file(it).isFile }
    }

    /** The table's pair for a stored [key], or null if the table does not know it (any more). */
    fun mixOf(table: EmojiMixTable, key: String): EmojiMix? {
        if (!KEY.matches(key)) return null
        val (left, right) = key.split('_')
        // a code point beyond Unicode would throw: such a key is simply unknown
        val mix = runCatching { table.find(EmojiClusters.fromCodepoints(left), EmojiClusters.fromCodepoints(right)) }.getOrNull() ?: return null
        return mix.takeIf { it.key == key }
    }

    // access order: pictures of pairs no longer shown go first
    private val pictures = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > MAX
    }
    /** Pairs whose picture is being read, or could not be read (not tried again until [clear]). */
    private val reads = HashSet<String>()
    private var generation = 0

    /**
     * The small picture of [mix] for the row, or null while it is read from the cached file on the
     * worker; the listeners of [EmojiMixer] hear when it is there. Separate from the states of
     * [EmojiMixer], so the row never pushes the chosen pair out of memory (P74).
     */
    fun picture(context: Context, mix: EmojiMix): Bitmap? {
        pictures[mix.key]?.let { return it }
        if (!reads.add(mix.key)) return null
        val file = EmojiMixer.cache(context).file(mix)
        val started = generation
        EmojiMixer.afterWork {
            val bitmap = if (file.isFile) EmojiMixer.thumbnail(file, PICTURE_PX) else null
            EmojiMixer.toMain {
                if (started != generation || bitmap == null) return@toMain
                reads.remove(mix.key)
                pictures[mix.key] = bitmap
                EmojiMixer.changed()
            }
        }
        return null
    }

    private fun forgetPictures() {
        generation++
        pictures.clear()
        reads.clear()
    }

    @JvmStatic fun resetForTest() = forgetPictures()
}
