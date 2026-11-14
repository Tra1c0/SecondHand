// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.clip.ClipKind
import dev.agentkeyboard.core.suggest.AppCategory
import dev.agentkeyboard.core.suggest.Candidate
import dev.agentkeyboard.core.suggest.ContextCues
import dev.agentkeyboard.core.suggest.ContextScorer
import dev.agentkeyboard.core.suggest.FieldClass
import dev.agentkeyboard.core.suggest.FieldSignals
import dev.agentkeyboard.core.suggest.FieldTargets
import dev.agentkeyboard.core.suggest.SuggestKind
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.utils.InputTypeUtils
import helium314.keyboard.latin.utils.prefs

/**
 * Stage 45 "Feld- und Kontext-Vorschläge": the glue between the pure rules (agent-core
 * `dev.agentkeyboard.core.suggest`), the vault ([ClipVault.freshest], never a secret), "Meine
 * Daten" ([MyDataCache]) and the strip. Built by [KeyboardCommands] when no command is typed, after
 * the format guard and before the emoji mix.
 *
 * Two ways to a chip: the focused field asks for something ([FieldTargets.target]: e-mail, code,
 * phone, IBAN, address, name), or cue words before the cursor in a normal text field reach the
 * weighted score ([ContextScorer.THRESHOLD]). Nothing is inserted by itself: only the tap on a chip
 * inserts, once, through [KeyboardCommands.Host]. Nothing in password fields, incognito, no-learning
 * fields or while the device is locked; nothing is logged.
 */
object FieldSuggestionChips {
    /** Text before the cursor that is read: the cue window plus one (more means a long field). */
    const val LOOKBACK = ContextCues.WINDOW + 1
    /** Context chips at most; field chips at most [MAX_FIELD_CHIPS]. */
    const val MAX_CONTEXT_CHIPS = 2
    const val MAX_FIELD_CHIPS = 3
    /** Characters of the text before the cursor that a context insertion replaces by themselves plus the value. */
    private const val ANCHOR = 24
    private const val NAME = "suggest"

    fun isEnabled(context: Context) = context.prefs().getBoolean(AgentPreferences.FIELD_SUGGESTIONS, true)

    /** Replaced in tests: the owner's data, null while locked or unreadable. */
    @JvmStatic var myData: (Context, Boolean) -> MyData? = { context, locked -> MyDataCache.get(context, locked) }
    /** Replaced in tests: the freshest clip of a kind, never a secret. */
    @JvmStatic var fresh: (Context, ClipKind, Long) -> ClipVault.Fresh? = { context, kind, age -> ClipVault.freshest(context, kind, age) }
    /** Replaced in tests: whether the device (or the user) is locked right now. */
    @JvmStatic var deviceLocked: (Context) -> Boolean = {
        helium314.keyboard.compat.isDeviceLocked(it) || runCatching { helium314.keyboard.compat.isUserLocked(it) }.getOrDefault(false)
    }

    /**
     * No suggestions without settings, in incognito (also no-learning and password fields, P139),
     * for the no-learning flag of any field type, in password fields and while the device is locked.
     */
    fun isBlocked(context: Context, settings: SettingsValues?, editorInfo: EditorInfo?): Boolean =
        FormatGuardChips.isBlocked(settings, editorInfo) || deviceLocked(context)

    /** The input type class of the field, or null for fields that never get suggestions (password, URI, date, unknown). */
    fun fieldClass(editorInfo: EditorInfo?): FieldClass? {
        val type = editorInfo?.inputType ?: return null
        if (InputTypeUtils.isAnyPasswordInputType(type)) return null
        val variation = type and InputType.TYPE_MASK_VARIATION
        return when (type and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> when {
                InputTypeUtils.isEmailVariation(variation) -> FieldClass.EMAIL
                variation == InputType.TYPE_TEXT_VARIATION_URI || variation == InputType.TYPE_TEXT_VARIATION_FILTER -> null
                variation == InputType.TYPE_TEXT_VARIATION_PERSON_NAME -> FieldClass.PERSON_NAME
                variation == InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS -> FieldClass.POSTAL_ADDRESS
                variation == InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE -> FieldClass.SHORT_MESSAGE
                variation == InputType.TYPE_TEXT_VARIATION_NORMAL || variation == InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE ||
                    variation == InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT || variation == InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT -> FieldClass.TEXT
                else -> FieldClass.OTHER
            }
            InputType.TYPE_CLASS_PHONE -> FieldClass.PHONE
            // decimal and signed numbers are amounts, never codes
            InputType.TYPE_CLASS_NUMBER ->
                if (type and (InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED) != 0) FieldClass.OTHER else FieldClass.NUMBER
            else -> null
        }
    }

    /**
     * What the field tells: type, hint text and label, id name. `EditorInfo` carries no autofill
     * hints on any API level (they reach only autofill services), so there are none here.
     */
    fun signals(editorInfo: EditorInfo, fieldClass: FieldClass): FieldSignals {
        val hint = listOfNotNull(editorInfo.hintText, editorInfo.label).joinToString(" ").take(120)
        return FieldSignals(fieldClass, hint, editorInfo.fieldName.orEmpty().take(80))
    }

    private fun clipKind(kind: SuggestKind): ClipKind? = when (kind) {
        SuggestKind.EMAIL -> ClipKind.EMAIL
        SuggestKind.PHONE -> ClipKind.PHONE
        SuggestKind.IBAN -> ClipKind.IBAN
        SuggestKind.ADDRESS -> ClipKind.ADDRESS
        SuggestKind.OTP -> ClipKind.OTP
        SuggestKind.LINK -> ClipKind.LINK
        SuggestKind.NAME -> null
    }

    /** Context chips shown and not yet tapped: cue id, kind and field. Memory only, no text. */
    private data class Offer(val cueId: String, val kind: SuggestKind, val field: String)
    private var shown: Set<Offer> = emptySet()

    /** Offers that went away without a tap count as passed by (once each). */
    private fun settle(context: Context, settings: SettingsValues?, now: Set<Offer>) {
        for (offer in shown) if (offer !in now) ContextLearning.record(context, settings, offer.cueId, offer.kind, accepted = false)
        shown = now
    }

    /** Forgets the shown offers without learning anything (blocked field, tests). */
    fun forget() { shown = emptySet() }

    private class Item(val kind: SuggestKind, val value: String, val own: Boolean, val score: Double, val offer: Offer?)

    /** The chips for the field of [host], or an empty list. */
    fun stripChips(host: KeyboardCommands.Host, settings: SettingsValues? = Settings.getValues()): List<KeyboardCommands.Chip> {
        val context = host.context
        val editorInfo = host.editorInfo
        val fieldClass = fieldClass(editorInfo)
        // cheap checks first: this runs on every strip update
        if (editorInfo == null || fieldClass == null || !isEnabled(context) || FormatGuardChips.isBlocked(settings, editorInfo)) {
            forget()
            return emptyList()
        }
        val before = host.textBeforeCursor(LOOKBACK)?.toString() ?: return emptyList<KeyboardCommands.Chip>().also { settle(context, settings, emptySet()) }
        val target = FieldTargets.target(signals(editorInfo, fieldClass))
        val matches = if (target == null && (fieldClass == FieldClass.TEXT || fieldClass == FieldClass.SHORT_MESSAGE))
            ContextCues.find(before) else emptyList()
        if (target == null && matches.isEmpty()) {
            settle(context, settings, emptySet())
            return emptyList()
        }
        // something could be offered: now the live lock state, before any own data or clip is read
        if (deviceLocked(context)) {
            MyDataCache.drop()
            forget()
            return emptyList()
        }
        val items = if (target != null) fieldItems(host, before, target.kind, target.weak)
            else contextItems(host, settings, before, fieldClass, matches)
        settle(context, settings, items.mapNotNull { it.offer }.toSet())
        val typed = if (target != null) before else ""
        return items.map { chip(host, settings, before, typed, it) }
    }

    /** The field asks for [kind]: own entries first, then the fresh clip; each only if it fits what is typed. */
    private fun fieldItems(host: KeyboardCommands.Host, before: String, kind: SuggestKind, weak: Boolean): List<Item> {
        // the whole field before the cursor only, and nothing behind the cursor
        if (before.length >= LOOKBACK) return emptyList()
        if (host.textAfterCursor(1)?.isNotEmpty() == true) return emptyList()
        val context = host.context
        val values = ArrayList<Item>()
        myData(context, false)?.entries(kind)?.forEach { values += Item(kind, it, own = true, score = 0.0, offer = null) }
        clipKind(kind)?.let { clip ->
            val maxAge = when {
                kind != SuggestKind.OTP -> ContextScorer.MAX_AGE_MILLIS
                weak -> FieldTargets.WEAK_OTP_MILLIS
                else -> FieldTargets.OTP_MILLIS
            }
            fresh(context, clip, maxAge)?.let { values += Item(kind, it.text.trim(), own = false, score = 0.0, offer = null) }
        }
        return values.filter { it.value.isNotEmpty() && FieldTargets.fits(before, it.value, kind) }
            .distinctBy { it.value.lowercase() }.take(MAX_FIELD_CHIPS)
    }

    /** Cue words before the cursor in a normal text field, scored; only items at or above the threshold. */
    private fun contextItems(host: KeyboardCommands.Host, settings: SettingsValues?, before: String, fieldClass: FieldClass,
                             matches: List<dev.agentkeyboard.core.suggest.CueMatch>): List<Item> {
        val context = host.context
        val editorInfo = host.editorInfo
        val app = AppCategory.of(editorInfo?.packageName)
        val weights = ContextLearning.weights(context, settings)
        val field = "${editorInfo?.packageName}:${editorInfo?.fieldId}"
        val data by lazy { myData(context, false) }
        val items = ArrayList<Item>()
        for (match in matches) {
            val offer = Offer(match.cueId, match.kind, field)
            data?.entries(match.kind)?.forEach { value ->
                items += Item(match.kind, value, true, ContextScorer.score(match, Candidate(match.kind, true), app, fieldClass, weights), offer)
            }
            clipKind(match.kind)?.let { clip ->
                fresh(context, clip, ContextScorer.MAX_AGE_MILLIS)?.let {
                    val candidate = Candidate(match.kind, false, it.ageMillis)
                    items += Item(match.kind, it.text.trim(), false, ContextScorer.score(match, candidate, app, fieldClass, weights), offer)
                }
            }
        }
        val lower = before.lowercase()
        return items.filter { it.score >= ContextScorer.THRESHOLD && it.value.isNotEmpty() && !lower.contains(it.value.lowercase()) }
            .sortedByDescending { it.score }.distinctBy { it.value.lowercase() }.take(MAX_CONTEXT_CHIPS)
    }

    private fun chip(host: KeyboardCommands.Host, settings: SettingsValues?, before: String, typed: String, item: Item): KeyboardCommands.Chip {
        val context = host.context
        val label = label(item.kind, item.value)
        val description = context.getString(if (item.own) R.string.agent_suggest_insert_own else R.string.agent_suggest_insert_clip,
            kindName(context, item.kind), item.value)
        var used = false
        return KeyboardCommands.Chip(label, description, KeyboardCommands.Chip.Kind.ACTION) {
            // one tap, one attempt: a second tap on the same chip does nothing
            if (!used) {
                used = true
                val inserted = insert(host, before, typed, item.value)
                item.offer?.let { offer ->
                    shown = shown - offer
                    if (inserted) ContextLearning.record(context, settings, offer.cueId, offer.kind, accepted = true)
                }
            }
        }
    }

    /**
     * Inserts [value] once. In a field that asks for it: an empty field gets it at the cursor, typed
     * text that starts it is replaced by it. After cue words: the last characters before the cursor
     * are replaced by themselves plus [value] (the editor must still show exactly them), with a space
     * after a colon.
     */
    private fun insert(host: KeyboardCommands.Host, before: String, typed: String, value: String): Boolean {
        if (typed.isEmpty() && before.isEmpty()) return host.insertAtCursor(value)
        if (typed.isNotEmpty()) return host.replace(call(host.context, typed), value)
        var anchor = before.takeLast(ANCHOR)
        if (anchor.isNotEmpty() && Character.isLowSurrogate(anchor[0])) anchor = anchor.drop(1)
        if (anchor.isEmpty()) return false
        val separator = if (anchor.last().isWhitespace()) "" else " "
        return host.replace(call(host.context, anchor), anchor + separator + value)
    }

    private fun call(context: Context, text: String) =
        CommandCall(start = 0, text = text, prefix = KeyboardCommands.prefix(context), name = NAME, argument = "")

    private fun kindName(context: Context, kind: SuggestKind) = context.getString(when (kind) {
        SuggestKind.EMAIL -> R.string.agent_vault_label_email
        SuggestKind.PHONE -> R.string.agent_vault_label_phone
        SuggestKind.IBAN -> R.string.agent_vault_label_iban
        SuggestKind.ADDRESS -> R.string.agent_vault_label_address
        SuggestKind.NAME -> R.string.agent_suggest_kind_name
        SuggestKind.OTP -> R.string.agent_suggest_kind_code
        SuggestKind.LINK -> R.string.agent_suggest_kind_link
    })

    /** The chip text: a symbol for the kind and a short form of the value. */
    fun label(kind: SuggestKind, value: String): String = when (kind) {
        SuggestKind.LINK -> "🔗 " + shortLink(value)
        SuggestKind.EMAIL -> "✉️ " + ellipsize(value, 32)
        SuggestKind.PHONE -> "📞 " + ellipsize(value, 24)
        SuggestKind.IBAN -> value.filter { it.isLetterOrDigit() }.let { "🏦 " + if (it.length > 10) "${it.take(4)} …${it.takeLast(4)}" else it }
        SuggestKind.ADDRESS -> "🏠 " + ellipsize(value, 28)
        SuggestKind.NAME -> "👤 " + ellipsize(value, 28)
        SuggestKind.OTP -> "🔢 $value"
    }

    /** "https://www.youtube.com/watch?v=x" → "youtube.com/…". */
    fun shortLink(url: String): String {
        val rest = url.trim().substringAfter("://", url.trim()).removePrefix("www.")
        val host = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        val more = rest.length > host.length && rest.substring(host.length).trim('/').isNotEmpty()
        return ellipsize(host, 28) + if (more) "/…" else ""
    }

    private fun ellipsize(value: String, max: Int) = if (value.length <= max) value else value.take(max - 1) + "…"
}
