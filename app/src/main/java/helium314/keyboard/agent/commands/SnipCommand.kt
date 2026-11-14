// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.LocalMoment
import dev.agentkeyboard.core.SnippetApps
import dev.agentkeyboard.core.SnippetTemplate
import helium314.keyboard.agent.AgentPreferences
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.compat.ClipboardManagerCompat
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination
import java.util.Locale
import java.util.TimeZone

/**
 * `@snip name` (stage 24f): the user's own text snippets, kept in [SnippetStore] and edited on the
 * command settings page. `@snip` lists them, `@snip ad` those whose name starts with or contains
 * "ad"; a tap on a snippet replaces the command by its text. Local only; nothing is logged.
 *
 * Stage 42: the text is a [SnippetTemplate], expanded only on the tap (date, time and clipboard are
 * read then, never while the chips are built). A snippet with fields `{?Name}` first turns the command
 * into "@snip name Field: " and the strip into a small input: the user types the value after the
 * colon (the argument of the command, so it is neither corrected nor learned), Enter or "Next" asks
 * the next field, the last one inserts. The values stay in memory only while that command is typed.
 * `{cursor}` moves the cursor back after the insert. Snippets limited to apps appear only there.
 */
object SnipCommand : KeyboardCommands.Command {
    private const val TAG = "SnipCommand"
    const val MAX_CHIPS = 10
    /** Longest text an insert may produce (fields and the clipboard can grow a template). */
    const val MAX_INSERT = 10_000

    /** Where snippets come from; replaced in tests. */
    fun interface Source { fun read(context: Context): List<Snippet> }

    @JvmStatic var source: Source = Source { SnippetStore(it).read() }

    /** What `{zwischenablage}` may use now; replaced in tests. */
    sealed interface ClipRead {
        class Text(val text: String) : ClipRead { override fun toString() = "Text(${text.length} chars)" }
        /** Nothing is inserted for the placeholder; the user sees [reason]. */
        data class Missing(val reason: Reason) : ClipRead
        enum class Reason { HISTORY_OFF, LOCKED, SENSITIVE, EMPTY, TOO_LONG }
    }

    fun interface Clipboard { fun read(context: Context): ClipRead }

    @JvmStatic var clipboard: Clipboard = SystemClipboard

    /** The time of the insert; replaced in tests. */
    @JvmStatic var clock: () -> Long = { System.currentTimeMillis() }
    @JvmStatic var zone: () -> TimeZone = { TimeZone.getDefault() }

    /** Read once per typed command; dropped when the command is gone. */
    private var cached: List<Snippet>? = null

    /** The snippet whose fields are being filled in, or null; dropped with the command. */
    private var filling: Filling? = null

    private class Filling(val snippet: Snippet, val template: SnippetTemplate) {
        var index = 0
        val values = LinkedHashMap<String, String>()
        val field get() = template.fields[index]
        val isLast get() = index == template.fields.lastIndex
        /** The argument while [field] is asked: "name Field: ", then the value. */
        val argumentStart get() = argumentStart(snippet.name, field)
        override fun toString() = "Filling(${values.size} values)" // never the texts
    }

    override val name = "snip"
    override val protectsArgument = true
    /** Room for field values: "name Field: value" may be up to [dev.agentkeyboard.core.CommandSyntax.MAX_LONG_ARGUMENT] characters. */
    override val longArgument = true

    /** From this many characters left for a value on, the field chip shows how many remain. */
    const val ROOM_WARNING = 40
    /** The field chip shows at most the last this many characters of the value. */
    const val LABEL_VALUE = 40

    override fun description(context: Context) =
        context.getString(R.string.agent_command_snip, KeyboardCommands.prefix(context).toString())

    override fun reset() { cached = null; filling = null }

    /** Characters a field value may still grow by before "@snip name Field: value" is no command any more. */
    fun roomLeft(argument: String) = dev.agentkeyboard.core.CommandSyntax.MAX_LONG_ARGUMENT - argument.length

    /** The argument that asks [field] of [snippetName]. */
    fun argumentStart(snippetName: String, field: String) = "$snippetName $field: "

    override fun chips(host: KeyboardCommands.Host, call: CommandCall): List<KeyboardCommands.Chip> {
        // find() already refuses these fields; checked again so that nothing is read there
        if (KeyboardCommands.isBlocked(Settings.getValues(), host.editorInfo)) { reset(); return emptyList() }
        val context = host.context
        currentFilling(call)?.let { return fieldChips(host, call, it) }
        // unreadable (e.g. locked) storage shows nothing, not an invitation to add snippets
        val snippets = cached ?: (read(context) ?: return emptyList()).also { cached = it }
        if (snippets.isEmpty()) return listOf(KeyboardCommands.Chip(context.getString(R.string.agent_command_snip_add),
            context.getString(R.string.agent_command_snip_add_description)) { openSettings(context, SettingsDestination.AgentCommands) })
        val app = host.editorInfo?.packageName
        return match(call.argument, snippets.filter { SnippetApps.allows(it.apps, app) }).map { snippet ->
            KeyboardCommands.Chip("${snippet.name}: ${preview(snippet.text)}",
                context.getString(R.string.agent_command_snip_insert, snippet.name)) { start(host, call, snippet) }
        }
    }

    /**
     * Stage 42: Enter while a field is asked moves on like "Next"; otherwise the key works as usual.
     * While a field is asked Enter is always taken, so the app never gets "@snip name Field: value"
     * (e.g. sent in a chat): if the field cannot be changed right now (a gesture still being processed)
     * nothing happens and the field stays asked; a second Enter moves on.
     */
    override fun onEnter(host: KeyboardCommands.Host, call: CommandCall): Boolean {
        if (KeyboardCommands.isBlocked(Settings.getValues(), host.editorInfo)) { reset(); return false }
        val state = currentFilling(call) ?: return false
        if (host.canReplaceNow) next(host, call, state)
        return true
    }

    /** The filling that [call] continues, or null (then any old one is dropped). */
    private fun currentFilling(call: CommandCall): Filling? {
        val state = filling ?: return null
        if (call.argument.startsWith(state.argumentStart)) return state
        filling = null
        return null
    }

    private fun fieldChips(host: KeyboardCommands.Host, call: CommandCall, state: Filling): List<KeyboardCommands.Chip> {
        val context = host.context
        val value = call.argument.substring(state.argumentStart.length)
        val count = state.template.fields.size
        val room = roomLeft(call.argument)
        // a long value: its end, where the user types
        val shown = if (value.length > LABEL_VALUE) "…" + value.takeLast(LABEL_VALUE) else value.ifEmpty { "…" }
        val label = "${state.field} (${state.index + 1}/$count): $shown" +
            if (room <= ROOM_WARNING) " " + context.getString(R.string.agent_snip_field_room, room) else ""
        val nextLabel = context.getString(if (state.isLast) R.string.agent_snip_field_insert else R.string.agent_snip_field_next)
        return listOf(
            KeyboardCommands.Chip(label, context.getString(R.string.agent_snip_field_description, state.field, state.snippet.name),
                KeyboardCommands.Chip.Kind.RESULT) { next(host, call, state) },
            KeyboardCommands.Chip(nextLabel) { next(host, call, state) },
            KeyboardCommands.Chip(context.getString(R.string.agent_snip_field_cancel), kind = KeyboardCommands.Chip.Kind.MUTED) {
                // back to the typed command; nothing is inserted
                filling = null
                host.replace(call, "${call.prefix}$name ${state.snippet.name}")
            },
        )
    }

    /** The tap on a snippet: insert it, or ask its first field. */
    private fun start(host: KeyboardCommands.Host, call: CommandCall, snippet: Snippet) {
        if (KeyboardCommands.isBlocked(Settings.getValues(), host.editorInfo)) return
        val template = snippet.template
        if (template.fields.isEmpty()) { insert(host, call, template, emptyMap()); return }
        // set before the replacement: the strip may be rebuilt while the field changes
        val state = Filling(snippet, template)
        filling = state
        if (host.replace(call, "${call.prefix}$name ${state.argumentStart}")) cached = null
        else if (filling === state) filling = null
    }

    private fun next(host: KeyboardCommands.Host, call: CommandCall, state: Filling) {
        if (filling !== state || !call.argument.startsWith(state.argumentStart)) return
        if (KeyboardCommands.isBlocked(Settings.getValues(), host.editorInfo)) { reset(); return }
        val value = call.argument.substring(state.argumentStart.length).trim()
        if (state.isLast) {
            val values = state.values + (state.field to value)
            insert(host, call, state.template, values)
            return
        }
        // moved on before the replacement (the strip may be rebuilt meanwhile), back if it fails
        val field = state.field
        state.values[field] = value
        state.index++
        if (!host.replace(call, "${call.prefix}$name ${state.argumentStart}")) {
            state.index--
            state.values.remove(field)
        }
    }

    private fun insert(host: KeyboardCommands.Host, call: CommandCall, template: SnippetTemplate, fields: Map<String, String>) {
        val context = host.context
        val clip = if (template.usesClipboard) readClipboard(context) else null
        val now = LocalMoment.of(clock(), zone())
        val expansion = template.expand(SnippetTemplate.Values(now.date, now.time, SnippetTemplate.language(host.language),
            (clip as? ClipRead.Text)?.text, fields))
        if (expansion.text.length > MAX_INSERT) {
            Toast.makeText(context, R.string.agent_snip_too_long, Toast.LENGTH_SHORT).show()
            return
        }
        if (!host.replace(call, expansion.text)) return
        reset()
        val back = expansion.cursor?.let { expansion.text.length - it } ?: 0
        if (back > 0) host.moveCursorBack(back)
        if (clip is ClipRead.Missing) Toast.makeText(context, clipNote(clip.reason), Toast.LENGTH_LONG).show()
    }

    private fun clipNote(reason: ClipRead.Reason) = when (reason) {
        ClipRead.Reason.HISTORY_OFF -> R.string.agent_snip_clip_history_off
        ClipRead.Reason.LOCKED -> R.string.agent_snip_clip_locked
        ClipRead.Reason.SENSITIVE -> R.string.agent_snip_clip_sensitive
        ClipRead.Reason.EMPTY -> R.string.agent_snip_clip_empty
        ClipRead.Reason.TOO_LONG -> R.string.agent_snip_clip_too_long
    }

    private fun readClipboard(context: Context): ClipRead = try {
        clipboard.read(context)
    } catch (e: Exception) {
        Log.w(TAG, "clipboard could not be read: ${e.javaClass.simpleName}")
        ClipRead.Missing(ClipRead.Reason.EMPTY)
    }

    /**
     * The current clip for `{zwischenablage}`: only with the clipboard history on (the user opted in to
     * the keyboard keeping clips), never while the device is locked, never a clip marked sensitive or
     * one [SensitiveClips] remembers as copied sensitive; text clips only.
     */
    object SystemClipboard : Clipboard {
        override fun read(context: Context): ClipRead {
            if (!ClipCommand.historyEnabled(context)) return ClipRead.Missing(ClipRead.Reason.HISTORY_OFF)
            if (Settings.getValues()?.mIsLocked != false) return ClipRead.Missing(ClipRead.Reason.LOCKED)
            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = manager?.primaryClip
            if (clip == null || clip.itemCount == 0 || clip.description?.hasMimeType("text/*") != true)
                return ClipRead.Missing(ClipRead.Reason.EMPTY)
            if (ClipboardManagerCompat.getClipSensitivity(clip.description) == true) return ClipRead.Missing(ClipRead.Reason.SENSITIVE)
            // the same conversion as the history and SensitiveClips use
            val text = clip.getItemAt(0)?.coerceToText(context)?.toString()
            if (text.isNullOrEmpty()) return ClipRead.Missing(ClipRead.Reason.EMPTY)
            if (SensitiveClips.matcher(context)(text)) return ClipRead.Missing(ClipRead.Reason.SENSITIVE)
            // stage 44: a clip the guard sorts as secret is never put into a snippet
            if (dev.agentkeyboard.core.clip.ClipGuard.classify(text).category == dev.agentkeyboard.core.clip.ClipCategory.SECRET)
                return ClipRead.Missing(ClipRead.Reason.SENSITIVE)
            if (text.length > SnippetStore.MAX_TEXT) return ClipRead.Missing(ClipRead.Reason.TOO_LONG)
            return ClipRead.Text(text)
        }
    }

    private fun read(context: Context): List<Snippet>? = try {
        source.read(context)
    } catch (e: Exception) {
        // texts are never logged
        Log.w(TAG, "snippets could not be read: ${e.javaClass.simpleName}")
        null
    }

    /**
     * Snippets for the typed [query], best first: same name, name starts with it, name contains it.
     * Without a query all snippets by name. Case does not matter. At most [MAX_CHIPS].
     */
    fun match(query: String, snippets: List<Snippet>): List<Snippet> {
        val typed = query.trim().lowercase(Locale.ROOT)
        return snippets.mapNotNull { snippet ->
            val name = snippet.name.lowercase(Locale.ROOT)
            val score = when {
                typed.isEmpty() -> 1
                name == typed -> 0
                name.startsWith(typed) -> 1
                name.contains(typed) -> 2
                else -> return@mapNotNull null
            }
            Triple(snippet, score, name)
        }.sortedWith(compareBy({ it.second }, { it.third })).take(MAX_CHIPS).map { it.first }
    }

    /** Where the editor preview shows the cursor mark. */
    const val PREVIEW_CURSOR = "│"

    /**
     * Stage 42: the snippet editor's live preview of [template] at [millis] in [language]: fields as
     * ‹Name›, the clipboard as [clipboardLabel] (never read in the settings), the cursor as [PREVIEW_CURSOR].
     */
    fun editorPreview(template: SnippetTemplate, language: String, clipboardLabel: String,
                      millis: Long = clock(), zone: TimeZone = this.zone()): String {
        val now = LocalMoment.of(millis, zone)
        val expansion = template.expand(SnippetTemplate.Values(now.date, now.time, SnippetTemplate.language(language),
            clipboardLabel, template.fields.associateWith { "‹$it›" }))
        val cursor = expansion.cursor ?: return expansion.text
        return expansion.text.substring(0, cursor) + PREVIEW_CURSOR + expansion.text.substring(cursor)
    }

    /** The start of [text] on one line, for a chip label. */
    fun preview(text: String, length: Int = 30): String {
        val line = text.trim().replace(Regex("\\s+"), " ")
        return if (line.length <= length) line else line.take(length).trimEnd() + "…"
    }

    /** The keyboard cannot show settings itself: a settings page opens in its own task (P39). */
    internal fun openSettings(context: Context, destination: String) {
        context.startActivity(
            Intent(context, SettingsActivity::class.java)
                // an existing settings task would only come to the front and ignore the destination (P39)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(AgentPreferences.OPEN_SETTINGS_DESTINATION, destination)
        )
    }
}
