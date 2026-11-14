// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.widget.Toast
import dev.agentkeyboard.core.LocalMoment
import dev.agentkeyboard.core.SnippetApps
import dev.agentkeyboard.core.SnippetTemplate
import dev.agentkeyboard.core.memory.PhraseText
import dev.agentkeyboard.core.memory.ShortcutBook
import dev.agentkeyboard.core.memory.SnippetMatcher
import helium314.keyboard.agent.commands.SnipCommand
import helium314.keyboard.agent.commands.Snippet
import helium314.keyboard.agent.commands.SnippetStore
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValues

/**
 * Stage 46: the chips of the memory, built by [KeyboardCommands] when no command is typed (after
 * the format guard and the field suggestions). Only between words (the text before the cursor ends
 * with a space, as in stage 45), never mid-word, so the word suggestions and the single-word
 * autocorrection stay as they are.
 *
 * Up to three things, each one chip: the sure rest of a remembered phrase ("… sobald ich mehr
 * weiß"), a snippet that fits the last words, and once a due abbreviation offer. When one of them is
 * there, the row also carries the usual word predictions (a tap picks them like in the normal strip).
 * Every chip only appends at the cursor it was built for, on a tap, once; nothing is replaced.
 */
object MemoryChips {
    /** Text before the cursor that is read. */
    const val LOOKBACK = Memory.LOOKBACK
    const val MAX_PREDICTIONS = 3
    private const val LABEL = 40

    /** Replaced in tests: whether the device (or the user) is locked right now. */
    @JvmStatic var deviceLocked: (Context) -> Boolean = { Memory.deviceLocked(it) }

    private class Index(val source: SnipCommand.Source, val version: Int, val matcher: SnippetMatcher, val snippets: Map<String, Snippet>)
    @Volatile private var index: Index? = null

    /** The offer shown now and the cursor it was shown at: it stays while the cursor does. */
    private var showing: Pair<ShortcutBook.Offer, Int>? = null

    /** Drops what is held in memory (the snippet index, the shown offer); a new field or a locked device. */
    fun forget() { index = null; showing = null }

    fun stripChips(host: KeyboardCommands.Host, settings: SettingsValues? = Settings.getValues()): List<KeyboardCommands.Chip> {
        val context = host.context
        val memory = Memory.isEnabled(context)
        val snippets = Memory.snippetsEnabled(context)
        if (!memory && !snippets) return emptyList()
        if (Memory.isBlocked(context, settings, host.editorInfo)) return emptyList()
        val before = host.textBeforeCursor(LOOKBACK)?.toString() ?: return emptyList()
        if (before.isEmpty() || !before.last().isWhitespace() || before.endsWith("\n")) return emptyList()
        if (deviceLocked(context)) { forget(); return emptyList() }
        val chips = ArrayList<KeyboardCommands.Chip>()
        if (memory) Memory.predict(context, before)?.let { prediction ->
            val rest = prediction.rest ?: return@let
            chips += restChip(host, settings, before, rest, prediction.restKey)
        }
        if (snippets) snippetChip(host, before)?.let { chips += it }
        if (memory) {
            // offered once: marked as shown the first time, kept only while the cursor stays (the page keeps it)
            val offer = showing?.takeIf { it.second == host.cursor && host.cursor >= 0 }?.first
                ?: Memory.dueOffer(context)?.also {
                    Memory.markOffer(context, it, ShortcutBook.State.SHOWN)
                    showing = it to host.cursor
                }
            if (offer == null) showing = null else chips += offerChip(host, offer)
        }
        if (chips.isEmpty()) return emptyList()
        val predictions = host.predictions(MAX_PREDICTIONS).map { word ->
            KeyboardCommands.Chip(word, word) { host.pickPrediction(word) }
        }
        // the sure rest first, then the usual next words, then snippet and offer
        return chips.take(1).filter { it.kind == KeyboardCommands.Chip.Kind.RESULT } + predictions +
            chips.filterNot { it.kind == KeyboardCommands.Chip.Kind.RESULT }
    }

    private fun ellipsize(text: String) = if (text.length <= LABEL) text else text.take(LABEL - 1).trimEnd() + "…"

    private fun restChip(host: KeyboardCommands.Host, settings: SettingsValues?, before: String, rest: String, key: String?): KeyboardCommands.Chip {
        val context = host.context
        var used = false
        return KeyboardCommands.Chip("… " + ellipsize(rest), context.getString(R.string.agent_memory_chip_rest, rest),
            KeyboardCommands.Chip.Kind.RESULT) {
            // one tap, one insert, only where the chip was built: appended after the cursor, nothing replaced
            if (!used && host.textBeforeCursor(LOOKBACK)?.toString() == before && host.insertAtCursor(rest)) {
                used = true
                key?.let { Memory.accepted(context, settings, host.editorInfo, it) }
            }
        }
    }

    // ---- snippets ----

    private fun currentIndex(context: Context): Index? {
        val source = SnipCommand.source
        index?.let { if (it.source === source && it.version == SnippetStore.version) return it }
        val read = try { source.read(context) } catch (e: Exception) { return null } // locked or unreadable: no chip, nothing logged
        val documents = read.map { SnippetMatcher.Document(it.name, it.name, it.template.parts.filterIsInstance<SnippetTemplate.Part.Text>().joinToString(" ") { p -> p.text }) }
        return Index(source, SnippetStore.version, SnippetMatcher(documents), read.associateBy { it.name }).also { index = it }
    }

    private fun snippetChip(host: KeyboardCommands.Host, before: String): KeyboardCommands.Chip? {
        val context = host.context
        val words = PhraseText.currentSentence(before).takeLast(SnippetMatcher.QUERY_WORDS)
        if (words.size < SnippetMatcher.MIN_QUERY_WORDS) return null
        val current = currentIndex(context) ?: return null
        val app = host.editorInfo?.packageName
        val match = current.matcher.best(words.joinToString(" ")) ?: return null
        val snippet = current.snippets[match.id]?.takeIf { SnippetApps.allows(it.apps, app) } ?: return null
        // a snippet with fields goes through @snip, which asks them; only when that command is there
        val template = snippet.template
        if (template.fields.isNotEmpty() && !(KeyboardCommands.isEnabled(context) && KeyboardCommands.isCommandEnabled(context, SnipCommand.name)))
            return null
        var used = false
        return KeyboardCommands.Chip("📋 " + ellipsize("${snippet.name}: ${SnipCommand.preview(snippet.text, 24)}"),
            context.getString(R.string.agent_memory_chip_snippet, snippet.name)) {
            if (!used && host.textBeforeCursor(LOOKBACK)?.toString() == before) {
                used = insertSnippet(host, snippet, template)
            }
        }
    }

    /** Like a tap on the snippet after `@snip`, but appended at the cursor: nothing typed is replaced. */
    private fun insertSnippet(host: KeyboardCommands.Host, snippet: Snippet, template: SnippetTemplate): Boolean {
        val context = host.context
        if (template.fields.isNotEmpty()) return host.insertAtCursor("${KeyboardCommands.prefix(context)}${SnipCommand.name} ${snippet.name}")
        val clip = if (template.usesClipboard) runCatching { SnipCommand.clipboard.read(context) }.getOrNull() else null
        val now = LocalMoment.of(SnipCommand.clock(), SnipCommand.zone())
        val expansion = template.expand(SnippetTemplate.Values(now.date, now.time, SnippetTemplate.language(host.language),
            (clip as? SnipCommand.ClipRead.Text)?.text))
        if (expansion.text.isEmpty() || expansion.text.length > SnipCommand.MAX_INSERT) return false
        if (!host.insertAtCursor(expansion.text)) return false
        val back = expansion.cursor?.let { expansion.text.length - it } ?: 0
        if (back > 0) host.moveCursorBack(back)
        return true
    }

    // ---- abbreviation offer ----

    private fun offerChip(host: KeyboardCommands.Host, offer: ShortcutBook.Offer): KeyboardCommands.Chip {
        val context = host.context
        var used = false
        return KeyboardCommands.Chip(context.getString(R.string.agent_memory_chip_offer, offer.short, ellipsize(offer.expansion)),
            context.getString(R.string.agent_memory_chip_offer_description, offer.short, offer.expansion), KeyboardCommands.Chip.Kind.TEMPLATE) {
            if (!used) {
                used = true
                val created = Memory.acceptOffer(context, offer)
                Toast.makeText(context, if (created) R.string.agent_memory_offer_created else R.string.agent_memory_offer_failed,
                    Toast.LENGTH_SHORT).show()
                if (created) host.refresh()
            }
        }
    }
}
