// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.inputmethod.EditorInfo
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.format.FieldKind
import dev.agentkeyboard.core.format.FormatGuard
import dev.agentkeyboard.core.format.FormatHint
import helium314.keyboard.compat.EditorInfoCompatUtils
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.utils.InputTypeUtils
import helium314.keyboard.latin.utils.prefs

/**
 * Stage 41 "Formatwächter": the glue between [FormatGuard] (agent-core) and the strip. Shows a hint
 * chip in fields with an unambiguous type (e-mail, phone, decimal number; an IBAN in plain text
 * fields), built by [KeyboardCommands] when no command is typed. Nothing changes by itself: only a
 * tap on a chip replaces the field text, through [KeyboardCommands.Host.replace], and only while
 * the field still holds exactly the checked text (otherwise the replacement is refused).
 */
object FormatGuardChips {
    /** One more than [FormatGuard.MAX_LENGTH]: a longer field is not checked. */
    const val LOOKBACK = FormatGuard.MAX_LENGTH + 1
    private const val NAME = "format"
    private const val GREEN = 0xFF34C759.toInt()
    private const val RED = 0xFFFF453A.toInt()

    fun isEnabled(context: Context) = context.prefs().getBoolean(AgentPreferences.FORMAT_GUARD, true)

    /** The kind of field, or null for fields without an unambiguous type (and every password field). */
    fun fieldKind(editorInfo: EditorInfo?): FieldKind? {
        val type = editorInfo?.inputType ?: return null
        if (InputTypeUtils.isAnyPasswordInputType(type)) return null
        val variation = type and InputType.TYPE_MASK_VARIATION
        return when (type and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> when {
                InputTypeUtils.isEmailVariation(variation) -> FieldKind.EMAIL
                variation == InputType.TYPE_TEXT_VARIATION_URI -> null
                else -> FieldKind.TEXT
            }
            InputType.TYPE_CLASS_PHONE -> FieldKind.PHONE
            InputType.TYPE_CLASS_NUMBER ->
                if (type and InputType.TYPE_NUMBER_FLAG_DECIMAL != 0) FieldKind.DECIMAL else null
            else -> null
        }
    }

    /** No hints without settings, in incognito (also no-learning and password fields) or while the device is locked. */
    fun isBlocked(settings: SettingsValues?, editorInfo: EditorInfo?): Boolean {
        if (settings == null || settings.mIncognitoModeEnabled || settings.mIsLocked) return true
        val type = editorInfo?.inputType ?: return true
        return InputTypeUtils.isAnyPasswordInputType(type)
    }

    /**
     * The chips for the field of [host], or an empty list. Reads at most [LOOKBACK] characters before
     * the cursor and, only when there is a hint, one after it: the hint needs the whole field.
     */
    fun stripChips(host: KeyboardCommands.Host, settings: SettingsValues? = Settings.getValues()): List<KeyboardCommands.Chip> {
        val kind = fieldKind(host.editorInfo) ?: return emptyList()
        val context = host.context
        if (isBlocked(settings, host.editorInfo) || !isEnabled(context)) return emptyList()
        val before = host.textBeforeCursor(LOOKBACK)?.toString() ?: return emptyList()
        if (before.isEmpty() || before.length > FormatGuard.MAX_LENGTH) return emptyList()
        val locale = EditorInfoCompatUtils.getHintLocales(host.editorInfo).firstOrNull()
        val language = locale?.language?.takeIf { it.isNotEmpty() } ?: host.language
        val country = locale?.country?.takeIf { it.isNotEmpty() } ?: settings?.mLocale?.country.orEmpty()
        val hints = FormatGuard.check(kind, before, language, country)
        if (hints.isEmpty()) return emptyList()
        // the whole field only: text after the cursor (or an unknown one) means no hint
        val after = host.textAfterCursor(1) ?: return emptyList()
        if (after.isNotEmpty()) return emptyList()
        val call = CommandCall(start = 0, text = before, prefix = KeyboardCommands.prefix(context), name = NAME, argument = "")
        return hints.map { chip(context, host, call, it) }
    }

    private fun chip(context: Context, host: KeyboardCommands.Host, call: CommandCall, hint: FormatHint): KeyboardCommands.Chip {
        val replacement = hint.replacement
        val apply: () -> Unit = { if (replacement != null) host.replace(call, replacement) }
        val action = KeyboardCommands.Chip.Kind.ACTION
        val muted = KeyboardCommands.Chip.Kind.MUTED
        return when (hint) {
            is FormatHint.EmailSpaces -> KeyboardCommands.Chip(context.getString(R.string.agent_format_email_spaces, hint.replacement),
                context.getString(R.string.agent_format_apply, hint.replacement), action, apply)
            is FormatHint.EmailDomain -> KeyboardCommands.Chip(context.getString(R.string.agent_format_email_domain, hint.domain),
                context.getString(R.string.agent_format_apply, hint.replacement), action, apply)
            is FormatHint.EmailMissingAt -> KeyboardCommands.Chip(context.getString(R.string.agent_format_email_at, hint.replacement),
                context.getString(R.string.agent_format_apply, hint.replacement), action, apply)
            is FormatHint.Phone -> KeyboardCommands.Chip(context.getString(R.string.agent_format_phone, hint.replacement),
                context.getString(R.string.agent_format_apply, hint.replacement), action, apply)
            is FormatHint.Iban -> {
                val text = context.getString(when {
                    !hint.valid -> R.string.agent_format_iban_invalid
                    replacement != null -> R.string.agent_format_iban_group
                    else -> R.string.agent_format_iban_valid
                })
                val label = SpannableStringBuilder("● ").append(text).apply {
                    setSpan(ForegroundColorSpan(if (hint.valid) GREEN else RED), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                val description = if (replacement != null) context.getString(R.string.agent_format_apply, replacement) else text
                KeyboardCommands.Chip(label, description, if (replacement != null) action else muted, apply)
            }
            is FormatHint.Decimal -> KeyboardCommands.Chip(context.getString(R.string.agent_format_decimal, hint.suggested),
                context.getString(R.string.agent_format_decimal_description, hint.separator.toString(), hint.suggested), muted, apply)
        }
    }
}
