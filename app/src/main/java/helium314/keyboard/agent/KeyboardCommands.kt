// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.graphics.Color
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
import androidx.core.graphics.ColorUtils
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
    class Chip(val label: CharSequence, val description: CharSequence = label, val kind: Kind = Kind.ACTION, val onTap: () -> Unit) {
        enum class Kind {
            /** A chip that does what it says. */
            ACTION,
            /** A secondary chip: cancel, discard, retry, an error that opens the details. */
            MUTED,
            /** Waiting for an answer: moving dots before the label ([LoadingDotsView]); a tap does nothing. */
            LOADING,
            /** An answer: as wide as its label (the row scrolls), a tap inserts it. */
            RESULT,
            /** A template of the command ([Template]): outlined; a tap only writes it after the command name. */
            TEMPLATE,
        }
    }

    /**
     * An argument pattern a command accepts, shown after the command's own chips while its argument
     * is empty. [text] is what a tap writes after "@name " (e.g. "pfeile", "oz in g ", `spanisch "`);
     * nothing runs and nothing is sent, the strip then shows the command's chips for that argument.
     */
    class Template(val label: String, val text: String = label)

    /** The controller that runs typed AI requests from the strip; set by the controller of the running keyboard. */
    internal var aiRequests: java.lang.ref.WeakReference<AgentEditorController>? = null

    /** Cancels a typed AI request sent from the strip and drops its answer (no command typed any more, blocked field). */
    fun dropAiRequests() { aiRequests?.get()?.dropCommandRequest() }

    /** What a command may do: replace its own text, nothing else of the field. */
    interface Host {
        val context: Context
        val editorInfo: EditorInfo?
        /** Language of the current input, e.g. "de". */
        val language: String
        /** Replaces the typed command (exactly [CommandContext.text]) by [replacement] in one commit; false if the field changed. */
        fun replace(command: CommandContext, replacement: String): Boolean
        /** Shows the calculator in place of the keys (stage 24b); "Insert" replaces [command]. */
        fun openCalculator(command: CommandCall): Boolean
        val canOpenCalculator: Boolean
        /** The cursor the chips were built for, or -1 when unknown; lets a command tell a tap's context apart. */
        val cursor: Int get() = -1
        /** Builds the chips again for the current text, e.g. after a chip changed what a command shows. */
        fun refresh() {}
        /** Up to [length] characters of the field before the cursor (ending with the command), or null (stage 24d). */
        fun textBeforeCursor(length: Int): CharSequence? = null
        /**
         * Up to [length] characters before the cursor as the editor reports them now, not from the
         * keyboard's cache; e.g. after an image went in (P83). By default [textBeforeCursor].
         */
        fun editorTextBeforeCursor(length: Int): CharSequence? = textBeforeCursor(length)
        /** Up to [length] characters of the field after the cursor, or null (stage 24d). */
        fun textAfterCursor(length: Int): CharSequence? = null
        /** Replaces the chips in the strip, e.g. after a read; false if the strip shows something else by now. */
        fun showChips(chips: List<Chip>): Boolean = false
        /**
         * Hands the saved workflow [workflowId] to the text actions (stage 24f `@wf`): prepared like a
         * skill key with selected text, otherwise remembered until the panel opens with a selection.
         * The field stays unchanged. False if the panel cannot open now.
         */
        fun openWorkflow(workflowId: String): Boolean = false
        /** Display name of the AI provider a send chip sends to (`@ki`, `@translate`), or null without AI here. */
        val aiProvider: String? get() = null
        /** The typed AI request sent for exactly [command] at this cursor, or null; its changes rebuild these chips. */
        fun aiRequest(command: CommandCall): helium314.keyboard.agent.commands.CommandAiRequest? = null
        /**
         * The tap on a send chip: the one-time approval of exactly [request] for [command], consumed by
         * starting one run (or a failed request that says why nothing was sent). The field stays unchanged.
         */
        fun sendAi(command: CommandCall, request: String, action: dev.agentkeyboard.core.AiTextAction):
            helium314.keyboard.agent.commands.CommandAiRequest? = null
        /** Cancels the request of [command] or discards its answer; nothing is inserted. */
        fun dropAi(command: CommandCall) {}
        /** The tap on an answer: replaces [command] by it in one commit (or inserts it at the cursor). */
        fun insertAi(command: CommandCall): Boolean = false
        /** The tap on a failed request: the panel with the details, or the AI settings for a missing key or model. */
        fun aiDetails(command: CommandCall): Boolean = false
        /** Switches the keyboard to the enabled [subtype] (stage 24g), like the language key; false if it cannot. */
        fun switchSubtype(subtype: android.view.inputmethod.InputMethodSubtype): Boolean = false
        /** The subtype the keyboard uses now (stage 24g), or null when unknown; it gets no switch chip. */
        val currentSubtype: android.view.inputmethod.InputMethodSubtype? get() = null
        /** Stage 42: moves the plain cursor [count] characters back, right after a replacement; false if it cannot. */
        fun moveCursorBack(count: Int): Boolean = false
        /** Stage 42 after review: false while [replace] would be refused for now (a gesture still being processed). */
        val canReplaceNow: Boolean get() = true
    }

    /** A built-in command. Commands never come from files or models. */
    interface Command {
        /** Lower-case ASCII letters, as typed after the prefix. */
        val name: String
        /** One line for the settings page. */
        fun description(context: Context): String
        /** True if the words of the argument are text the user writes and must not be autocorrected. */
        val protectsArgument: Boolean get() = false
        /** True if the argument may be up to [CommandSyntax.MAX_LONG_ARGUMENT] characters instead of [CommandSyntax.MAX_ARGUMENT]. */
        val longArgument: Boolean get() = false
        /** The chips for a complete command; empty to show nothing (then the normal strip stays). */
        fun chips(host: Host, call: CommandCall): List<Chip>
        /** The runtime permission the command needs, requested on the settings page; null for none. */
        val permission: String? get() = null
        /** Forgets what the command keeps in memory; called when no command is typed any more. */
        fun reset() {}
        /** The argument patterns offered while the argument is empty ([Template]); none by default. */
        fun templates(host: Host): List<Template> = emptyList()
        /**
         * True if the command takes the unknown typed [word] as its own (stage 24g: `@sp` for `@lang`).
         * Asked only after every command name, started name and typo of a name; may run on the suggestion thread.
         */
        fun claims(word: String): Boolean = false
        /** Reads what the command needs before it is typed, e.g. the enabled languages; called by [load] while enabled. */
        fun load(context: Context) {}
        /**
         * Stage 42: the Enter key while [call] is before the cursor. True if the command used the key
         * (it is then neither typed nor sent to the app); false lets Enter work as usual (the default).
         */
        fun onEnter(host: Host, call: CommandCall): Boolean = false
    }

    /** Built-in commands in the order of the settings page; each stage adds its own. */
    val BUILT_IN: List<Command> = listOf(
        MathCommand,
        ContactCommand,
        helium314.keyboard.agent.commands.UrlCommand,
        SearchCommand,
        CharacterCommand,
        EmojiCommand,
        ConvertCommand,
        helium314.keyboard.agent.commands.SnipCommand,
        helium314.keyboard.agent.commands.ClipCommand,
        helium314.keyboard.agent.commands.WorkflowCommand,
        helium314.keyboard.agent.commands.AskCommand,
        DateCommand,
        EventCommand,
        PlaceCommand,
        helium314.keyboard.agent.commands.QrCommand,
        helium314.keyboard.agent.commands.TranslateCommand,
        helium314.keyboard.agent.commands.AlarmCommand,
        helium314.keyboard.agent.commands.TimerCommand,
        helium314.keyboard.agent.commands.LanguageCommand,
        helium314.keyboard.agent.commands.EmojiMixCommand,
    )

    /** Replaced in tests. */
    @JvmStatic var registry: List<Command> = BUILT_IN

    @Volatile private var active: Active? = null
    /** True while the text before the cursor is a complete command: its words are not autocorrected. */
    @Volatile private var inCommand = false

    private class Active(val prefix: Char, val names: Set<String>, val longNames: Set<String>, val claimers: List<Command>) {
        /** Stage 24g: the enabled command that takes an unknown word as its own, or null. */
        val claim: (String) -> String? = { word -> claimers.firstOrNull { it.claims(word) }?.name }

        /** Typos of names offer the name to complete (stage 24g); every reader parses the same way. */
        fun parse(before: CharSequence) = CommandSyntax.parse(before, prefix, names, longNames, fuzzyNames = true,
            claim = claim.takeIf { claimers.isNotEmpty() })
    }

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
        val longNames = registry.filter { it.longArgument && it.name in names }.map { it.name }.toSet()
        val enabled = registry.filter { it.name in names }
        if (isEnabled(context)) enabled.forEach { it.load(context) }
        active = if (isEnabled(context) && names.isNotEmpty()) Active(prefix(context), names, longNames, enabled) else null
        inCommand = false
        forget()
    }

    /** Lets every command drop what it holds in memory (e.g. contacts found for the typed name). */
    fun forget() {
        registry.forEach { it.reset() }
    }

    /** A typed command word like "@math" (trailing punctuation ignored); never corrected or learned. */
    @JvmStatic fun isCommandWord(word: CharSequence?): Boolean {
        val current = active ?: return false
        if (word.isNullOrEmpty()) return false
        val trimmed = word.trimEnd { !it.isLetter() }
        return CommandSyntax.isCommandWord(trimmed, current.prefix, current.names, current.claim.takeIf { current.claimers.isNotEmpty() })
    }

    /**
     * True while the word at the cursor belongs to a command: its name (with URL detection off the
     * prefix is not part of the composing word, so the engine only sees "math") or the argument of
     * a command that protects it. Set from the text before the cursor on the main thread
     * ([updateGuard]); read by the suggestion worker.
     */
    @JvmStatic fun suppressesCorrection(): Boolean = inCommand && active != null

    /** Whether the text before the cursor ends in a command name or a protected argument. */
    @JvmStatic fun guardsTextBefore(before: CharSequence?): Boolean {
        val current = active ?: return false
        if (before == null) return false
        return when (val context = current.parse(before)) {
            null -> false
            is CommandNames -> true
            is CommandCall -> context.argument.isBlank() ||
                registry.firstOrNull { it.name == context.name }?.protectsArgument == true
        }
    }

    /**
     * Called with the text before the cursor whenever the strip is updated, also when no strip is
     * shown; null while text is selected. Without a command before the cursor, every command drops
     * what it found, so nothing stays in memory when the chips are never built.
     */
    @JvmStatic fun updateGuard(before: CharSequence?) {
        inCommand = guardsTextBefore(before)
        val current = active
        if (current == null || before == null || current.parse(before) == null) forget()
    }

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
        return current.parse(before)
    }

    /**
     * The chips view for the strip, or null when no command is typed. Reads only the text before
     * the cursor that the keyboard already holds.
     */
    @JvmStatic fun createChipsView(ime: LatinIME, parent: ViewGroup): View? =
        createChipsView(ime, parent) { ime.setNeutralSuggestionStrip() }

    /** [neutral] shows the normal strip again when a rebuilt row would be empty. */
    internal fun createChipsView(ime: LatinIME, parent: ViewGroup, neutral: () -> Unit): View? {
        var view: HorizontalScrollView? = null
        // a chip asked to show other chips for the same command: swap the row in place
        fun swap(chips: List<Chip>, onlyIfShown: Boolean): Boolean {
            val shown = view ?: return false
            // a later answer (e.g. a screen read) only while the strip still shows this row
            if (chips.isEmpty() || (onlyIfShown && shown.parent == null)) return false
            val row = chipRow(parent, chips) as HorizontalScrollView
            val inner = row.getChildAt(0)
            row.removeView(inner)
            shown.removeAllViews()
            shown.addView(inner)
            shown.scrollTo(0, 0)
            return true
        }
        fun build(): List<Chip>? = buildChips(ime, onRefresh = {
            val rebuilt = build()
            // nothing left to show for this text: the normal strip, not the old row
            if (rebuilt.isNullOrEmpty()) { if (view?.parent != null) neutral() }
            else swap(rebuilt, onlyIfShown = false)
        }, onShow = { swap(it, onlyIfShown = true) })
        val chips = build()
        if (chips.isNullOrEmpty()) return null
        return (chipRow(parent, chips) as HorizontalScrollView).also { view = it }
    }

    /**
     * Stage 42: the Enter key (soft key in `LatinIME.onEvent`, hardware key in
     * `KeyboardActionListenerImpl.onKeyDown`), before the engine sees it. Only a command that is before a plain
     * cursor and wants the key ([Command.onEnter]) takes it; otherwise false and Enter works as usual.
     */
    @JvmStatic fun onEnter(ime: LatinIME): Boolean {
        val position = ime.agentCommandCursor
        if (position < 0) return false
        val call = find(ime.getAgentTextBeforeCursor(CommandSyntax.LOOKBACK + 1), Settings.getValues(), ime.currentInputEditorInfo)
            as? CommandCall ?: return false
        val command = registry.firstOrNull { it.name == call.name } ?: return false
        // the strip is rebuilt by the normal update after the command changed the field
        return command.onEnter(host(ime, position, onRefresh = {}, onShow = { false }), call)
    }

    private fun buildChips(ime: LatinIME, onRefresh: () -> Unit, onShow: (List<Chip>) -> Boolean): List<Chip>? {
        val position = ime.agentCommandCursor
        val context = if (position < 0) null
            else find(ime.getAgentTextBeforeCursor(CommandSyntax.LOOKBACK + 1), Settings.getValues(), ime.currentInputEditorInfo)
        if (context == null) {
            forget() // nothing a command found stays in memory once the command is gone
            if (position < 0) return null
        }
        val host = host(ime, position, onRefresh, onShow)
        // stage 41: no command, but a hint for a field with an unambiguous type (e-mail, phone, IBAN, decimal)
        if (context == null) FormatGuardChips.stripChips(host).let { if (it.isNotEmpty()) return it }
        // stage 25a: no command, but two typed emojis with a mix
        if (context == null) return helium314.keyboard.agent.commands.EmojiMixCommand.stripChips(host,
            ime.getAgentTextBeforeCursor(helium314.keyboard.agent.commands.EmojiMixCommand.LOOKBACK)).ifEmpty { null }
        return chipsFor(context, host)
    }

    /** The real host for chips built (or the Enter key pressed) at the cursor [position]. */
    private fun host(ime: LatinIME, position: Int, onRefresh: () -> Unit, onShow: (List<Chip>) -> Boolean): Host =
        object : Host {
            override val context: Context get() = ime
            override val editorInfo: EditorInfo? get() = ime.currentInputEditorInfo
            override val language: String get() = Settings.getValues()?.mLocale?.language.orEmpty()
            override fun replace(command: CommandContext, replacement: String): Boolean {
                val outcome = ime.replaceCommandText(command.text, replacement, position)
                if (outcome == ApplyOutcome.REJECTED)
                    android.widget.Toast.makeText(ime, R.string.agent_command_expired, android.widget.Toast.LENGTH_SHORT).show()
                return outcome == ApplyOutcome.SUBMITTED
            }
            override fun openCalculator(command: CommandCall) =
                ime.agentEditorController.openCalculator(command.argument, command.text, position)
            override val canOpenCalculator: Boolean get() = ime.agentEditorController.canOpenCalculator
            override val cursor: Int get() = position
            override fun refresh() = onRefresh()
            override fun textBeforeCursor(length: Int) = ime.getAgentTextBeforeCursor(length)
            override fun editorTextBeforeCursor(length: Int): CharSequence? = ime.currentInputConnection?.getTextBeforeCursor(length, 0)
            override fun textAfterCursor(length: Int): CharSequence? = ime.currentInputConnection?.getTextAfterCursor(length, 0)
            // e.g. after reading the screen: only while the strip still shows this command at this cursor
            override fun showChips(chips: List<Chip>): Boolean = ime.agentCommandCursor == position && onShow(chips)
            override fun openWorkflow(workflowId: String) = ime.agentEditorController.openWorkflow(workflowId)
            override val aiProvider: String? get() = ime.agentEditorController.commandProviderName
            // the chips built last for this request are the ones its answer updates
            override fun aiRequest(command: CommandCall) =
                ime.agentEditorController.commandRequest(command.text, position)?.also { it.onChange = onRefresh }
            override fun sendAi(command: CommandCall, request: String, action: dev.agentkeyboard.core.AiTextAction) =
                ime.agentEditorController.sendCommandRequest(request, command.text, position, action, onRefresh)
            override fun dropAi(command: CommandCall) { ime.agentEditorController.dropCommandRequest(command.text, position) }
            override fun insertAi(command: CommandCall) = ime.agentEditorController.insertCommandAnswer(command.text, position)
            override fun aiDetails(command: CommandCall) = ime.agentEditorController.openCommandRequestDetails(command.text, position)
            // called after the command is gone, so the new keyboard starts on the changed field
            override fun switchSubtype(subtype: android.view.inputmethod.InputMethodSubtype): Boolean {
                ime.switchToSubtypeByCommand(subtype)
                return true
            }
            override val currentSubtype: android.view.inputmethod.InputMethodSubtype?
                get() = runCatching { helium314.keyboard.latin.RichInputMethodManager.getInstance().currentSubtype.rawSubtype }.getOrNull()
            override fun moveCursorBack(count: Int) = ime.moveAgentCursorBack(count)
            override val canReplaceNow: Boolean get() = ime.canReplaceAgentCommand()
        }

    /** Name candidates complete the name; a complete command asks the command. */
    fun chipsFor(context: CommandContext, host: Host): List<Chip> = when (context) {
        is CommandNames -> context.candidates.map { name ->
            val word = "${context.prefix}$name"
            Chip(word, host.context.getString(R.string.agent_command_complete, word)) { host.replace(context, "$word ") }
        }
        is CommandCall -> registry.firstOrNull { it.name == context.name }?.let { command ->
            val own = command.chips(host, context)
            if (context.argument.isNotBlank()) own else own + templateChips(command, context, host)
        }.orEmpty()
    }

    /**
     * The templates of [command] for a call without argument: a tap replaces the typed command
     * (e.g. "@zeichen" or "@zeichen ") by "@zeichen pfeile" in one commit, nothing else.
     */
    internal fun templateChips(command: Command, call: CommandCall, host: Host): List<Chip> {
        // like the commands themselves: nothing in a protected field (find() already refuses those)
        if (isBlocked(Settings.getValues(), host.editorInfo)) return emptyList()
        return command.templates(host).map { template ->
            Chip(template.label, host.context.getString(R.string.agent_command_template, template.label), Chip.Kind.TEMPLATE) {
                host.replace(call, "${call.prefix}${command.name} ${template.text}")
            }
        }
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
        val accent = if (flat) FlatStyle.ACCENT else colors.get(ColorType.ACTION_KEY_BACKGROUND)
        for (chip in chips) {
            val muted = chip.kind == Chip.Kind.MUTED
            val template = chip.kind == Chip.Kind.TEMPLATE
            val label = TextView(context).apply {
                text = chip.label
                setSingleLine()
                ellipsize = TextUtils.TruncateAt.END
                // an answer is as wide as its (already shortened) label: the row scrolls instead
                maxWidth = if (chip.kind == Chip.Kind.RESULT) Int.MAX_VALUE else context.resources.displayMetrics.widthPixels * 2 / 3
                setTextColor(if (muted) ColorUtils.setAlphaComponent(textColor, 0x99)
                    else if (template) ColorUtils.setAlphaComponent(textColor, 0xCC) else textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
                if (flat) typeface = FlatStyle.labelTypeface
                gravity = Gravity.START or Gravity.CENTER_VERTICAL // centred single lines do not draw in render tests (P40)
            }
            val view: View = if (chip.kind == Chip.Kind.LOADING) LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isBaselineAligned = false
                addView(LoadingDotsView(context, accent), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8f) })
                addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
            } else label
            view.apply {
                contentDescription = chip.description
                setPadding(dp(14f), 0, dp(14f), 0)
                background = GradientDrawable().apply {
                    cornerRadius = dp(17f).toFloat()
                    // a template is outlined, so it stands apart from results and actions
                    if (template) {
                        setColor(Color.TRANSPARENT)
                        setStroke(dp(1f), ColorUtils.setAlphaComponent(textColor, 0x66))
                    } else setColor(if (muted) ColorUtils.setAlphaComponent(chipColor, Color.alpha(chipColor) / 2) else chipColor)
                }
                if (chip.kind != Chip.Kind.LOADING) {
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { chip.onTap() }
                }
            }
            row.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34f)).apply { marginEnd = dp(6f) })
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
            // the field stays unchanged until "Insert" replaces the command by the result
            if (host.canOpenCalculator) host.openCalculator(call)
        }
        return chips
    }

    /** Characters that are not on the letter keys: tapped, the user types the number after them. */
    override fun templates(host: KeyboardCommands.Host) = listOf(KeyboardCommands.Template("√"), KeyboardCommands.Template("π"))
}
