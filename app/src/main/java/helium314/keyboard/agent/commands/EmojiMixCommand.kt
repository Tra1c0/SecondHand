// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.util.TypedValue
import android.widget.Toast
import androidx.core.content.edit
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.EmojiClusters
import dev.agentkeyboard.core.EmojiMix
import dev.agentkeyboard.core.EmojiMixTable
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.agent.KeyboardCommands.Chip
import helium314.keyboard.agent.ScreenshotChip
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.prefs

/**
 * `@mix 🐶🔥` and the strip chip after two typed emojis (stage 25a). Both show the pair first as a
 * chip with both emojis ("🐶 + 🔥 mischen · Google"); only a tap on it loads that one image
 * ([EmojiMixer.request]). Then a result chip with the thumbnail; its tap inserts the image (or
 * shares it in fields without images) and only afterwards removes the typed command or the two
 * typed emojis, if they are still unchanged before the cursor.
 */
object EmojiMixCommand : KeyboardCommands.Command {
    override val name = "mix"
    // the argument is emojis, never a word to correct or learn
    override val protectsArgument: Boolean get() = true

    /** "Emoji-Mix in der Leiste anbieten" (default on); only while commands are on. */
    const val STRIP_PREFERENCE = "agent_emoji_mix_strip"
    /** Characters before the cursor read for the strip chip: enough for two long ZWJ sequences. */
    const val LOOKBACK = 32
    /** Partner chips for `@mix 🐶`. */
    const val PARTNERS = 6

    fun stripEnabled(context: Context) = context.prefs().getBoolean(STRIP_PREFERENCE, true)
    fun setStripEnabled(context: Context, enabled: Boolean) = context.prefs().edit { putBoolean(STRIP_PREFERENCE, enabled) }

    override fun description(context: Context) =
        context.getString(R.string.agent_command_mix, KeyboardCommands.prefix(context).toString())

    override fun load(context: Context) { EmojiMixer.table(context) }

    override fun templates(host: KeyboardCommands.Host) = listOf("😂", "🥰", "🐶", "🔥", "❤️").map { KeyboardCommands.Template(it) }

    override fun chips(host: KeyboardCommands.Host, call: CommandCall): List<Chip> {
        if (EmojiMixer.blocked(host.editorInfo)) return emptyList()
        val argument = call.argument.trim()
        if (argument.isEmpty()) return emptyList()
        val context = host.context
        val table = EmojiMixer.table(context)
        listen(host)
        if (table == null) return listOf(Chip(context.getString(R.string.agent_emoji_mix_table_loading), kind = Chip.Kind.MUTED) {})
        val emojis = EmojiClusters.split(argument).filter { it.isNotBlank() && it != "+" }
        val usage = Chip(context.getString(R.string.agent_emoji_mix_usage, KeyboardCommands.prefix(context).toString()), kind = Chip.Kind.MUTED) {}
        if (emojis.isEmpty() || emojis.size > 2 || emojis.any { !table.contains(it) }) return listOf(usage)
        val replace = { host.replace(call, "") }
        if (emojis.size == 1) {
            val partners = table.partners(emojis[0], EmojiMixTable.POPULAR, PARTNERS)
            if (partners.isEmpty()) return listOf(Chip(context.getString(R.string.agent_emoji_mix_none), kind = Chip.Kind.MUTED) {})
            return partners.flatMap { pairChips(host, it, call.text, replace, removeOnShare = true, short = true) }
        }
        val mix = table.find(emojis[0], emojis[1])
            ?: return listOf(Chip(context.getString(R.string.agent_emoji_mix_none), kind = Chip.Kind.MUTED) {})
        return pairChips(host, mix, call.text, replace, removeOnShare = true, short = false)
    }

    /**
     * The strip chip: the text before the cursor ends with two emojis that have a mix in the table.
     * Empty when commands or the setting are off, in protected fields, or while the table loads.
     */
    fun stripChips(host: KeyboardCommands.Host, before: CharSequence?): List<Chip> {
        if (before.isNullOrEmpty()) return emptyList()
        // cheap test first: this runs on every strip update
        val last = before[before.length - 1]
        if (!Character.isSurrogate(last) && last.code < 0x2000 && last.code != 0xFE0F) return emptyList()
        val context = host.context
        if (!KeyboardCommands.isEnabled(context) || !stripEnabled(context) || EmojiMixer.blocked(host.editorInfo)) return emptyList()
        val table = EmojiMixer.table(context) ?: return emptyList()
        val clusters = EmojiClusters.split(before.takeLast(LOOKBACK))
        if (clusters.size < 2) return emptyList()
        val first = clusters[clusters.size - 2]
        val second = clusters[clusters.size - 1]
        val mix = table.find(first, second) ?: return emptyList()
        val typed = first + second
        // Host.replace only uses the text: the two typed emojis are replaced like a command's own text
        val pair = CommandCall(start = 0, text = typed, prefix = KeyboardCommands.prefix(context), name = name, argument = "")
        listen(host)
        return pairChips(host, mix, typed, { host.replace(pair, "") }, removeOnShare = false, short = false)
    }

    /** State changes rebuild the chips built last (a row no longer shown ignores it). */
    private fun listen(host: KeyboardCommands.Host) = EmojiMixer.listen(LISTENER) { host.refresh() }

    const val LISTENER = "chips"

    /** The chips of one pair, by its state. [expected] is what must still stand before the cursor when the image goes out. */
    internal fun pairChips(host: KeyboardCommands.Host, mix: EmojiMix, expected: String, replace: () -> Boolean,
                           removeOnShare: Boolean, short: Boolean): List<Chip> {
        val context = host.context
        val pair = context.getString(R.string.agent_emoji_mix_pair, mix.leftEmoji, mix.rightEmoji)
        return when (val state = EmojiMixer.state(context, mix)) {
            null -> listOf(Chip(if (short) pair else context.getString(R.string.agent_emoji_mix_offer, pair),
                context.getString(R.string.agent_emoji_mix_offer_description, pair)) { tap(host, mix) })
            EmojiMixer.State.Loading -> listOf(Chip(context.getString(R.string.agent_emoji_mix_loading, pair), kind = Chip.Kind.LOADING) {})
            EmojiMixer.State.Missing -> listOf(Chip(if (short) "$pair · ${context.getString(R.string.agent_emoji_mix_none)}"
                else context.getString(R.string.agent_emoji_mix_none), context.getString(R.string.agent_emoji_mix_none_description, pair),
                Chip.Kind.MUTED) {})
            is EmojiMixer.State.Failed -> listOf(Chip(context.getString(R.string.agent_emoji_mix_failed, pair),
                context.getString(R.string.agent_emoji_mix_offer_description, pair), Chip.Kind.MUTED) { tap(host, mix) })
            is EmojiMixer.State.Ready -> {
                val inserts = ScreenshotChip.editorAccepts(host.editorInfo, EmojiMixer.MIME_TYPE)
                val text = context.getString(if (inserts) R.string.agent_emoji_mix_insert else R.string.agent_emoji_mix_share)
                val label = SpannableStringBuilder()
                state.thumbnail?.let { bitmap ->
                    val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 28f, context.resources.displayMetrics).toInt()
                    val drawable = BitmapDrawable(context.resources, bitmap).apply { setBounds(0, 0, size, size) }
                    label.append("￼", ImageSpan(drawable), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE).append("  ")
                }
                label.append(text)
                listOf(Chip(label, context.getString(if (inserts) R.string.agent_emoji_mix_insert_description
                    else R.string.agent_emoji_mix_share_description, pair), Chip.Kind.RESULT) {
                    deliver(host, mix, state, expected, replace, removeOnShare)
                })
            }
        }
    }

    /** One tap, one request; the chips change through the listener. */
    private fun tap(host: KeyboardCommands.Host, mix: EmojiMix) {
        if (EmojiMixer.blocked(host.editorInfo)) return
        EmojiMixer.request(host.context, mix)
    }

    private fun deliver(host: KeyboardCommands.Host, mix: EmojiMix, state: EmojiMixer.State.Ready, expected: String, replace: () -> Boolean, removeOnShare: Boolean) {
        val context = host.context
        // only while the typed text is unchanged before the cursor: the replacement checks it again
        if (host.textBeforeCursor(expected.length)?.toString() != expected) {
            Toast.makeText(context, R.string.agent_command_expired, Toast.LENGTH_SHORT).show()
            return
        }
        EmojiMixer.deliver(context, host.editorInfo, state.file, afterInsert = { QrCommand.removeAfterInsert(host, expected, replace) },
            afterShare = { if (removeOnShare) replace() }, mix = mix)
    }

    // reset(): nothing to drop. The pair states hold no typed text (only which pairs were asked
    // for, at most EmojiMixer.MAX_STATES) and stay, so a strip chip keeps its image.
}
