// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.UrlDetector
import helium314.keyboard.agent.AgentPreferences
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.compat.ClipboardManagerCompat
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.settings.SettingsActivity

/**
 * `@url` (stages 24d and 24e).
 *
 * - `@url heise.de`: one chip "Open: https://heise.de" that starts the browser; only after the
 *   start succeeded is the command text removed (P43).
 * - `@url` alone: addresses from the clipboard's text and from the field around the cursor as chips;
 *   a tap replaces the command text by the address, a small second chip opens it. A further chip
 *   reads the screen once through [ScreenReaderService] (only after that tap), or opens the settings
 *   page to switch the service on.
 *
 * Only addresses [UrlDetector] accepts count. Nothing is read in password fields, in incognito
 * mode or while the device is locked; nothing read is stored or logged.
 */
object UrlCommand : KeyboardCommands.Command {
    override val name = "url"
    /** Characters of the field read on each side of the cursor. */
    const val FIELD_CHARS = 2000
    /** Address chips shown at most. */
    const val MAX_URLS = 6

    /** Replaced in tests. */
    @JvmStatic var clipboard: (Context) -> CharSequence? = ::readClipboard
    @JvmStatic var screen: ScreenTextReader = ScreenReaderService
    @JvmStatic var blocked: (KeyboardCommands.Host) -> Boolean = { KeyboardCommands.isBlocked(Settings.getValues(), it.editorInfo) }

    override fun description(context: Context) = context.getString(R.string.agent_command_url, KeyboardCommands.prefix(context).toString())

    /** Suggestions read once for a typed command at one cursor; strip updates reuse them. Dropped with [reset]. */
    private class Cached(val text: String, val cursor: Int, val urls: List<String>)
    private var cached: Cached? = null

    override fun reset() { cached = null }

    // a typed address is no word: never correct "heise.de" or learn it
    override val protectsArgument: Boolean get() = true

    override fun chips(host: KeyboardCommands.Host, call: CommandCall): List<KeyboardCommands.Chip> {
        if (blocked(host)) { reset(); return emptyList() }
        val context = host.context
        if (call.argument.isNotBlank()) {
            val url = UrlDetector.normalize(call.argument) ?: return emptyList()
            return listOf(KeyboardCommands.Chip(context.getString(R.string.agent_command_url_open, url)) { open(host, call, url) })
        }
        val urls = cached?.takeIf { it.text == call.text && it.cursor == host.cursor && it.cursor >= 0 }?.urls
            ?: suggestions(host, call).also { cached = Cached(call.text, host.cursor, it) }
        val chips = addressChips(host, call, urls)
        chips += screenChip(host, call, urls, chips.toList())
        return chips
    }

    /** Addresses from the clipboard, then the field before the cursor (nearest first), then after it. */
    internal fun suggestions(host: KeyboardCommands.Host, call: CommandCall): List<String> {
        val urls = LinkedHashSet<String>()
        clipboard(host.context)?.let { urls += UrlDetector.find(it, MAX_URLS) }
        host.textBeforeCursor(FIELD_CHARS + call.text.length)?.let { before ->
            // without the command itself
            val text = if (before.endsWith(call.text)) before.subSequence(0, before.length - call.text.length) else before
            urls += UrlDetector.find(text, Int.MAX_VALUE).asReversed()
        }
        host.textAfterCursor(FIELD_CHARS)?.let { urls += UrlDetector.find(it, MAX_URLS) }
        return urls.take(MAX_URLS)
    }

    private fun addressChips(host: KeyboardCommands.Host, call: CommandCall, urls: List<String>): MutableList<KeyboardCommands.Chip> {
        val context = host.context
        val chips = mutableListOf<KeyboardCommands.Chip>()
        for (url in urls) {
            chips += KeyboardCommands.Chip(url.removePrefix("https://"), context.getString(R.string.agent_command_url_insert, url)) {
                host.replace(call, url)
            }
            chips += KeyboardCommands.Chip("↗", context.getString(R.string.agent_command_url_open_short, url)) { open(host, call, url) }
        }
        return chips
    }

    private fun screenChip(host: KeyboardCommands.Host, call: CommandCall, shown: List<String>,
                           others: List<KeyboardCommands.Chip>): KeyboardCommands.Chip {
        val context = host.context
        if (!screen.isAvailable) return KeyboardCommands.Chip(context.getString(R.string.agent_command_url_read_screen_off)) {
            openCommandSettings(context)
        }
        return KeyboardCommands.Chip(context.getString(R.string.agent_command_url_read_screen)) {
            // read once per tap, and only if the field still allows it
            if (!blocked(host)) screen.read { texts ->
                if (texts == null) return@read toast(context, R.string.agent_command_url_screen_failed)
                val found = LinkedHashSet<String>()
                for (text in texts) {
                    if (found.size >= MAX_URLS) break
                    // addresses the row already offers are not repeated
                    found += UrlDetector.find(text, MAX_URLS + shown.size).filter { it !in shown }
                }
                if (found.isEmpty()) return@read toast(context, R.string.agent_command_url_screen_none)
                host.showChips(addressChips(host, call, found.take(MAX_URLS)) + others)
            }
        }
    }

    /** Starts the browser; the command text goes only after the start succeeded (P43). */
    internal fun open(host: KeyboardCommands.Host, call: CommandCall, url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            host.context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast(host.context, R.string.agent_command_url_no_browser)
            return false
        } catch (e: SecurityException) {
            toast(host.context, R.string.agent_command_url_no_browser)
            return false
        }
        host.replace(call, "")
        return true
    }

    /** The commands page, where the screen reader is explained and switched on. */
    internal fun openCommandSettings(context: Context) {
        runCatching {
            context.startActivity(Intent(context, SettingsActivity::class.java)
                // clear an existing settings task: it would only come to the front and ignore the destination (P39)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(AgentPreferences.OPEN_SETTINGS_DESTINATION, AgentPreferences.COMMANDS_DESTINATION))
        }
    }

    /** Text of the primary clip; null for sensitive clips, non-text clips and empty clipboards. */
    internal fun readClipboard(context: Context): CharSequence? {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = runCatching { manager.primaryClip }.getOrNull() ?: return null
        val description = clip.description ?: return null
        if (ClipboardManagerCompat.getClipSensitivity(description) == true) return null
        if (!description.hasMimeType("text/*") || clip.itemCount == 0) return null
        val text = clip.getItemAt(0).text ?: return null // never resolves URIs or intents
        return if (text.length > FIELD_CHARS) text.subSequence(0, FIELD_CHARS) else text
    }

    private fun toast(context: Context, text: Int) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}
