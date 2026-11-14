// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import dev.agentkeyboard.core.CommandCall
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.compat.ClipboardManagerCompat
import helium314.keyboard.latin.R
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsDestination
import java.util.Locale

/**
 * `@clip word` (stage 24f): searches the keyboard's own clipboard history, which is opt-in. `@clip`
 * shows the newest text clips, `@clip word` the clips containing the word; a tap replaces the
 * command by the whole clip. Images are left out, and so is a clip that was sensitive when it was
 * copied: marked so by the copying app or copied in a password field ([SensitiveClips]; clips copied
 * before that record existed are only known when they are still the current clip). With the history
 * off, nothing is read and one chip leads to the setting.
 */
object ClipCommand : KeyboardCommands.Command {
    private const val TAG = "ClipCommand"
    /** Chips without a search word. */
    const val NEWEST = 5
    /** Chips for a search word. */
    const val MAX_CHIPS = 8

    /** A text clip of the history; [sensitive] when it was sensitive when copied. [toString] never shows the text. */
    class Clip(val text: String, val timestamp: Long, val sensitive: Boolean = false) {
        override fun toString() = "Clip(${text.length} chars)"
    }

    /** Where clips come from; replaced in tests. Only asked while the history is switched on. */
    fun interface Source { fun clips(context: Context): List<Clip> }

    @JvmStatic var source: Source = History

    /** Read once per typed command; dropped when the command is gone. */
    private var cached: List<Clip>? = null

    override val name = "clip"
    override val protectsArgument = true
    /** Stage 52: also `@ablage`; `@clip on`/`off` switches the history, `@clip list` lists ([CommandSystem]). */
    override val aliases: List<String> get() = CommandSystem.ALIASES[name].orEmpty()

    override fun onEnter(host: KeyboardCommands.Host, call: CommandCall) = CommandSystem.onEnter(host, call) ?: false

    override fun description(context: Context) =
        context.getString(R.string.agent_command_clip, KeyboardCommands.prefix(context).toString())

    override fun reset() { cached = null }

    /** The keyboard's clipboard history setting (off by default). */
    fun historyEnabled(context: Context) =
        context.prefs().getBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, Defaults.PREF_ENABLE_CLIPBOARD_HISTORY)

    override fun chips(host: KeyboardCommands.Host, call: CommandCall): List<KeyboardCommands.Chip> {
        // find() already refuses these fields; checked again so that nothing is read there
        if (KeyboardCommands.isBlocked(Settings.getValues(), host.editorInfo)) { reset(); return emptyList() }
        val context = host.context
        // stage 52: "@clip on" / "@clip aus" switch the history; "@clip list" is the old list
        CommandSystem.chips(host, call) { query -> listChips(host, call, query) }?.let { return it }
        return listChips(host, call, call.argument)
    }

    /** The clips for [query] as chips, or the chip to the setting while the history is off. */
    private fun listChips(host: KeyboardCommands.Host, call: CommandCall, query: String): List<KeyboardCommands.Chip> {
        val context = host.context
        if (!historyEnabled(context)) {
            reset()
            return listOf(KeyboardCommands.Chip(context.getString(R.string.agent_command_clip_enable),
                context.getString(R.string.agent_command_clip_enable_description)) {
                SnipCommand.openSettings(context, SettingsDestination.Preferences)
            })
        }
        val clips = cached ?: read(context).also { cached = it }
        return match(query, clips).map { clip ->
            KeyboardCommands.Chip(SnipCommand.preview(clip.text),
                context.getString(R.string.agent_command_clip_insert, SnipCommand.preview(clip.text, 60))) {
                if (host.replace(call, clip.text)) reset()
            }
        }
    }

    private fun read(context: Context): List<Clip> = try {
        source.clips(context)
    } catch (e: Exception) {
        Log.w(TAG, "clipboard history could not be read: ${e.javaClass.simpleName}")
        emptyList()
    }

    /**
     * Clips for [query], newest first: without a query the newest [NEWEST], otherwise at most
     * [MAX_CHIPS] that contain it (case does not matter). Sensitive and blank clips never match;
     * the same text appears once.
     */
    fun match(query: String, clips: List<Clip>): List<Clip> {
        val typed = query.trim().lowercase(Locale.ROOT)
        val usable = clips.asSequence()
            .filter { !it.sensitive && it.text.isNotBlank() }
            .sortedByDescending { it.timestamp }
            .distinctBy { it.text }
        return if (typed.isEmpty()) usable.take(NEWEST).toList()
            else usable.filter { it.text.lowercase(Locale.ROOT).contains(typed) }.take(MAX_CHIPS).toList()
    }

    /** The keyboard's clipboard history (ClipboardDao); only text clips, within the retention time. */
    object History : Source {
        override fun clips(context: Context): List<Clip> {
            val dao = ClipboardDao.getInstance(context) ?: return emptyList()
            val entries = synchronized(dao) { dao.getAll().toList() }
            // the history does not keep the sensitive mark: SensitiveClips remembered which texts were
            // sensitive when they were copied; the current clip is checked directly as well
            val sensitiveText = sensitivePrimaryText(context)
            val wasSensitive = SensitiveClips.matcher(context)
            // clips past the retention time are only removed when the history view opens
            val retention = Settings.getValues()?.mClipboardHistoryRetentionTime ?: 121L
            val oldest = if (retention > 120) Long.MIN_VALUE else System.currentTimeMillis() - retention * 60 * 1000L
            return entries.mapNotNull { entry ->
                val text = entry.text
                if (entry.filename != null || text.isNullOrBlank()) return@mapNotNull null
                if (!entry.isPinned && entry.timeStamp < oldest) return@mapNotNull null
                // stage 44: what the guard sorts as secret (e.g. pinned before the guard) counts as sensitive
                val secret = dev.agentkeyboard.core.clip.ClipGuard.classify(text).category == dev.agentkeyboard.core.clip.ClipCategory.SECRET
                Clip(text, entry.timeStamp, sensitive = secret || text == sensitiveText || wasSensitive(text))
            }
        }

        private fun sensitivePrimaryText(context: Context): String? = runCatching {
            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return@runCatching null
            val clip = manager.primaryClip ?: return@runCatching null
            if (ClipboardManagerCompat.getClipSensitivity(clip.description) != true || clip.itemCount == 0) return@runCatching null
            clip.getItemAt(0).coerceToText(context)?.toString()
        }.getOrNull()
    }
}
