// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import dev.agentkeyboard.core.ApplyOutcome
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.CommandContext
import dev.agentkeyboard.core.CommandNames
import dev.agentkeyboard.core.CommandSyntax
import dev.agentkeyboard.core.MathExpression
import helium314.keyboard.keyboard.FlatStyle
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.utils.InputTypeUtils
import helium314.keyboard.latin.utils.prefs

/**
 * Commands typed into the field (stage 24), e.g. `@math`. While the text before the cursor is a
 * command, the strip shows chips instead of word suggestions; only a tap on a chip removes the
 * command text and runs something. Recognition rules: [CommandSyntax].
 *
 * Nothing here logs or stores what is typed. Commands are off in password fields, in incognito mode
 * and while the device is locked; typed command words are neither corrected nor learned
 * ([isCommandWord], used by Suggest and InputLogic).
 */
object KeyboardCommands {
    /** A chip in the strip. [onTap] runs on the main thread. */
    class Chip(val label: CharSequence, val description: CharSequence = label, val onTap: () -> Unit)

    /** What a command may do: replace its own text, nothing else of the field. */
    interface Host {
        val context: Context
        val editorInfo: EditorInfo?
        /** Language of the current input, e.g. "de". */
        val language: String
        /** Replaces the typed command (exactly [CommandContext.text]) by [replacement] in one commit; false if the field changed. */
        fun replace(command: CommandContext, replacement: String): Boolean
        /** Shows the calculator in place of the keys (stage 24b). */
        fun openCalculator(initial: String): Boolean
        val canOpenCalculator: Boolean
    }

    /** A built-in command. Commands never come from files or models. */
    interface Command {
        /** Lower-case ASCII letters, as typed after the prefix. */
        val name: String
        /** One line for the settings page. */
        fun description(context: Context): String
        /** True if the words of the argument are text the user writes and must not be autocorrected. */
        val protectsArgument: Boolean get() = false
        /** The chips for a complete command; empty to show nothing (then the normal strip stays). */
        fun chips(host: Host, call: CommandCall): List<Chip>
    }

    /** Built-in commands in the order of the settings page; each stage adds its own. */
    val BUILT_IN: List<Command> = listOf(MathCommand)

    /** Replaced in tests. */
    @JvmStatic var registry: List<Command> = BUILT_IN

    @Volatile private var active: Active? = null
    /** True while the text before the cursor is a complete command: its words are not autocorrected. */
    @Volatile private var inCommand = false

    private class Active(val prefix: Char, val names: Set<String>)

    fun isEnabled(context: Context) = context.prefs().getBoolean(AgentPreferences.COMMANDS, true)

    fun prefix(context: Context): Char {
        val stored = context.prefs().getString(AgentPreferences.COMMAND_PREFIX, null)
        val char = stored?.singleOrNull()
        return if (char != null && CommandSyntax.isAllowedPrefix(char)) char else CommandSyntax.DEFAULT_PREFIX
    }

    fun setPrefix(context: Context, prefix: Char) {
        require(CommandSyntax.isAllowedPrefix(prefix))
        context.prefs().edit { putString(AgentPreferences.COMMAND_PREFIX, prefix.toString()) }
        load(context)
    }

    fun isCommandEnabled(context: Context, name: String) =
        name !in context.prefs().getStringSet(AgentPreferences.COMMANDS_DISABLED, emptySet()).orEmpty()

    fun setCommandEnabled(context: Context, name: String, enabled: Boolean) {
        val disabled = context.prefs().getStringSet(AgentPreferences.COMMANDS_DISABLED, emptySet()).orEmpty().toMutableSet()
        if (enabled) disabled.remove(name) else disabled.add(name)
        context.prefs().edit { putStringSet(AgentPreferences.COMMANDS_DISABLED, disabled) }
        load(context)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.prefs().edit { putBoolean(AgentPreferences.COMMANDS, enabled) }
        load(context)
    }

    /** Reads the settings; called when an input view starts and when the settings page changes them. */
    @JvmStatic fun load(context: Context) {
        val names = registry.map { it.name }.filter { isCommandEnabled(context, it) }.toSet()
        active = if (isEnabled(context) && names.isNotEmpty()) Active(prefix(context), names) else null
        inCommand = false
    }

    /** A typed command word like "@math" (trailing punctuation ignored); never corrected or learned. */
    @JvmStatic fun isCommandWord(word: CharSequence?): Boolean {
        val current = active ?: return false
        if (word.isNullOrEmpty()) return false
        val trimmed = word.trimEnd { !it.isLetter() }
        return CommandSyntax.isCommandWord(trimmed, current.prefix, current.names)
    }

    /** True while a complete command is typed: the words of its argument are not autocorrected either. */
    @JvmStatic fun suppressesCorrection(): Boolean = inCommand && active != null

    fun isBlocked(settings: SettingsValues?, editorInfo: EditorInfo?): Boolean {
        if (settings == null || settings.mIncognitoModeEnabled || settings.mIsLocked) return true
        val inputType = editorInfo?.inputType ?: return true
        // only text fields: the insertion checks refuse anything else anyway
        if (inputType and android.text.InputType.TYPE_MASK_CLASS != android.text.InputType.TYPE_CLASS_TEXT) return true
        return InputTypeUtils.isAnyPasswordInputType(inputType)
    }

    /** The command before the cursor, or null. [before] is the text before a plain cursor. */
    fun find(before: CharSequence?, settings: SettingsValues?, editorInfo: EditorInfo?): CommandContext? {
        val current = active ?: return null
        if (before == null || isBlocked(settings, editorInfo)) return null
        return CommandSyntax.parse(before, current.prefix, current.names)
    }

    /**
     * The chips view for the strip, or null when no command is typed. Reads only the text before
     * the cursor that the keyboard already holds.
     */
    @JvmStatic fun createChipsView(ime: LatinIME, parent: ViewGroup): View? {
        val cursor = ime.agentCommandCursor
        val context = if (cursor < 0) null
            else find(ime.getAgentTextBeforeCursor(CommandSyntax.LOOKBACK + 1), Settings.getValues(), ime.currentInputEditorInfo)
        inCommand = context is CommandCall && registry.firstOrNull { it.name == context.name }?.protectsArgument == true
        context ?: return null
        val host = object : Host {
            override val context: Context get() = ime
            override val editorInfo: EditorInfo? get() = ime.currentInputEditorInfo
            override val language: String get() = Settings.getValues()?.mLocale?.language.orEmpty()
            override fun replace(command: CommandContext, replacement: String): Boolean {
                val outcome = ime.replaceCommandText(command.text, replacement, cursor)
                if (outcome == ApplyOutcome.REJECTED)
                    android.widget.Toast.makeText(ime, R.string.agent_command_expired, android.widget.Toast.LENGTH_SHORT).show()
                return outcome == ApplyOutcome.SUBMITTED
            }
            override fun openCalculator(initial: String) = ime.agentEditorController.openCalculator(initial)
            override val canOpenCalculator: Boolean get() = ime.agentEditorController.canOpenCalculator
        }
        val chips = chipsFor(context, host)
        if (chips.isEmpty()) return null
        return chipRow(parent, chips)
    }

    /** Name candidates complete the name; a complete command asks the command. */
    fun chipsFor(context: CommandContext, host: Host): List<Chip> = when (context) {
        is CommandNames -> context.candidates.map { name ->
            val word = "${context.prefix}$name"
            Chip(word, host.context.getString(R.string.agent_command_complete, word)) { host.replace(context, "$word ") }
        }
        is CommandCall -> registry.firstOrNull { it.name == context.name }?.chips(host, context).orEmpty()
    }

    internal fun chipRow(parent: ViewGroup, chips: List<Chip>): View {
        val context = parent.context
        val colors = Settings.getValues().mColors
        val flat = FlatStyle.isActive(colors)
        val chipColor = if (flat) 0xFF191919.toInt() else colors.get(ColorType.KEY_BACKGROUND)
        val textColor = if (flat) FlatStyle.LABEL else colors.get(ColorType.KEY_TEXT)
        fun dp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isBaselineAligned = false // two-line labels must not move down (P35)
            setPadding(dp(4f), 0, dp(4f), 0)
            id = R.id.agent_command_chips
        }
        for (chip in chips) {
            row.addView(TextView(context).apply {
                text = chip.label
                contentDescription = chip.description
                setSingleLine()
                ellipsize = TextUtils.TruncateAt.END
                maxWidth = context.resources.displayMetrics.widthPixels * 2 / 3
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
                if (flat) typeface = FlatStyle.labelTypeface
                gravity = Gravity.START or Gravity.CENTER_VERTICAL // centred single lines do not draw in render tests (P40)
                setPadding(dp(14f), 0, dp(14f), 0)
                background = GradientDrawable().apply { cornerRadius = dp(17f).toFloat(); setColor(chipColor) }
                isClickable = true
                isFocusable = true
                setOnClickListener { chip.onTap() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34f)).apply { marginEnd = dp(6f) })
        }
        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }
}

/** `@math` (stage 24b): a chip with the result of a typed calculation and one that opens the calculator. */
object MathCommand : KeyboardCommands.Command {
    override val name = "math"

    override fun description(context: Context) = context.getString(R.string.agent_command_math, KeyboardCommands.prefix(context).toString())

    fun storedStyle(context: Context) = runCatching {
        MathExpression.Style.valueOf(context.prefs().getString(AgentPreferences.CALCULATOR_STYLE, null) ?: "EQUATION")
    }.getOrDefault(MathExpression.Style.EQUATION)

    override fun chips(host: KeyboardCommands.Host, call: CommandCall): List<KeyboardCommands.Chip> {
        val chips = mutableListOf<KeyboardCommands.Chip>()
        val outcome = if (call.argument.isBlank()) null else MathExpression.evaluate(call.argument) as? MathExpression.Value
        if (outcome != null) {
            val text = MathExpression.format(outcome, storedStyle(host.context), MathExpression.Locale.forLanguage(host.language))
            chips += KeyboardCommands.Chip(text, host.context.getString(R.string.agent_command_math_insert, text)) { host.replace(call, text) }
        }
        // without a calculator to open, the command text stays
        if (host.canOpenCalculator) chips += KeyboardCommands.Chip(host.context.getString(R.string.agent_command_math_open)) {
            if (host.canOpenCalculator && host.replace(call, "")) host.openCalculator(call.argument)
        }
        return chips
    }
}
