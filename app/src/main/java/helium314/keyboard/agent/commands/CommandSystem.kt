// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.inputmethod.InputMethodSubtype
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.CommandGrammar
import dev.agentkeyboard.core.CommandGrammar.Action
import dev.agentkeyboard.core.CommandGrammar.Area
import dev.agentkeyboard.core.CommandGrammar.Reading
import dev.agentkeyboard.core.CommandGrammar.Role
import dev.agentkeyboard.core.CommandGrammar.Step
import dev.agentkeyboard.core.CommandGrammar.Target
import dev.agentkeyboard.core.CommandNames
import dev.agentkeyboard.core.LanguageNames
import dev.agentkeyboard.core.NameMatch
import helium314.keyboard.agent.AgentPreferences
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.agent.KeyboardCommands.Chip
import helium314.keyboard.agent.KeyboardCommands.Host
import helium314.keyboard.agent.Memory
import helium314.keyboard.latin.R
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsSubtype.Companion.toSettingsSubtype
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.prefs

/**
 * Stage 52: the unified command system. `@lang`, `@snip`, `@clip`, `@guard`, `@mem` and `@group`
 * read their words with [CommandGrammar] (slots, tolerance, any order, chains). While such a
 * command is typed the strip shows each reading in plain words, coloured like a code editor
 * ([CommandColors]: area, action, target, value, separator, unknown); a tap on it runs it once,
 * removes the typed command and offers "Undo" for [UNDO_MILLIS]. Enter runs only exactly one
 * harmless reading ([isHarmless]: no delete, nothing for all apps, nothing that starts learning or
 * empties the clipboard history); for any other reading Enter only tells to tap, the text stays.
 *
 * Commands change only the user's own settings and data: languages per app, the format guard per
 * app, the memory's excluded apps, own groups, own snippets and the clipboard history switch. Never
 * tools, URLs, providers or permissions. Nothing here logs what is typed; nothing runs in password
 * fields, in incognito mode or while the device is locked ([KeyboardCommands.isBlocked]).
 *
 * A command in the old syntax of its area (`@lang spanisch`, `@deutsch`, `@snip adr`, `@clip Termin`)
 * is left to the area's old chips: [chips] and [onEnter] return null then.
 */
object CommandSystem {
    /** The unified areas by command name. */
    val AREAS: Map<String, Area> = Area.entries.associateBy { it.id }

    /** German second names of the areas (umlauts also as ae/oe/ue). */
    val ALIASES: Map<String, List<String>> = mapOf(
        "lang" to listOf("sprache"), "snip" to listOf("baustein"), "clip" to listOf("ablage"),
        "guard" to listOf("wächter"), "mem" to listOf("gedächtnis"), "group" to listOf("gruppe"),
    )

    /** How long "Undo" stays after a command ran. */
    const val UNDO_MILLIS = 8_000L
    /** Suggestions for a slot at most. */
    const val MAX_SUGGESTIONS = 4

    /** Time for the undo; replaced in tests. */
    @JvmStatic var clock: () -> Long = { SystemClock.uptimeMillis() }
    /** Runs [block] on the main thread after [delay] ms; replaced in tests. */
    @JvmStatic var schedule: (Long, () -> Unit) -> Unit = { delay, block -> Handler(Looper.getMainLooper()).postDelayed(block, delay) }

    /** Where `@snip add`/`del` write; replaced in tests. */
    @JvmStatic var snippetStore: (Context) -> SnippetStore = { SnippetStore(it) }

    /** What a tap ran last, while it can be undone. */
    private class Undo(val description: String, val packageName: String?, val cursor: Int, val until: Long,
                       val steps: List<(Host) -> Unit>) {
        var used = false
        /** Rebuilds the strip that shows the undo chip, when the time is up. */
        var refresh: (() -> Unit)? = null
        override fun toString() = "Undo(${steps.size} steps)"
    }

    @Volatile private var undo: Undo? = null

    /** Tests: no undo is offered any more. */
    internal fun dropUndo() { undo = null }

    // ---- reading ----

    private fun enabledAreas(): Map<String, Area> = AREAS.filterKeys { it in KeyboardCommands.activeNames() }

    /** The words that start an area: the enabled names and their German second names, normalized. */
    private fun areaWords(): Map<String, Area> {
        val out = HashMap<String, Area>()
        for ((name, area) in enabledAreas()) {
            out[name] = area
            for (alias in ALIASES[name].orEmpty()) for (form in listOf(alias, alias.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")))
                out[LanguageNames.normalize(form)] = area
        }
        return out
    }

    private fun vocabulary(context: Context) = object : CommandGrammar.Vocabulary {
        override val apps get() = InstalledApps.apps
        override val groups by lazy { CommandAppSettings.groups(context) }
        override val snippets by lazy { SnipCommand.names(context) }
        override fun languages(word: String, exactOnly: Boolean) = LanguageCommand.matchLanguages(word, exactOnly)
    }

    /** The readings of [call] (the whole chain from its first area word), best first. */
    fun readings(host: Host, call: CommandCall): List<Reading> = readings(host.context, call.text, call.prefix)

    private fun readings(context: Context, text: String, prefix: Char): List<Reading> {
        val areas = areaWords()
        if (areas.isEmpty()) return emptyList()
        val langOn = "lang" in KeyboardCommands.activeNames()
        return CommandGrammar.parse(text, prefix, areas, vocabulary(context),
            claim = { word -> if (langOn && LanguageCommand.claims(word)) Area.LANG else null })
    }

    /** True when [readings] is the old syntax of its area only, which the area's own chips handle. */
    private fun isLegacy(readings: List<Reading>) = readings.isEmpty() || readings.all { it.legacy }

    /** True when [call] is the old syntax of its area (`@lang spanisch`, `@deutsch`, `@snip adr`). */
    fun isLegacy(host: Host, call: CommandCall): Boolean = isLegacy(readings(host, call))

    /**
     * Review of stage 52: whether the words of [call] are kept from the autocorrection
     * ([KeyboardCommands.Command.protects]): while they read as a command, and while only one word
     * follows the area word (an app or group name being typed, `@lang whatsap`, `@gruppe arbeit`, or
     * one the keyboard does not know yet). Text that only happens to follow an area word ("Hallo
     * @gruppe wer kommt heute?") is corrected as usual from its second word on.
     */
    fun protects(context: Context, call: CommandCall): Boolean {
        if (call.argument.isBlank()) return true
        return readings(context, call.text, call.prefix).any { reading ->
            reading.looksLikeCommand || reading.steps.singleOrNull()?.let { it.words.size <= 1 } == true
        }
    }

    /**
     * Review of stage 52: whether the area command [earlier] (up to the next area word on the same
     * line) starts a chain with it: when it ends in a separator (`…; @guard …`), or when it is a
     * complete reading of its own without unknown words and without an old-syntax step that does not
     * change anything (an old `@lang spanisch` switches the field and counts). Otherwise the later
     * command stands alone ("@gruppe hey, hier meine Adresse: @snip adr" is the snippet).
     */
    fun continuesChain(context: Context, prefix: Char, earlier: String): Boolean {
        if (CommandGrammar.endsWithSeparator(earlier)) return true
        val readings = readings(context, earlier, prefix)
        return readings.isNotEmpty() && readings.all { reading ->
            reading.unknownCount == 0 && reading.looksLikeCommand &&
                reading.steps.all { !it.legacy || (it.area == Area.LANG && it.language != null) }
        }
    }

    // ---- chips ----

    /**
     * The chips for [call] in the unified system, or null when it is the old syntax of its area (the
     * caller shows its old chips). [list] gives the old list of `@snip`/`@clip` for a search text.
     */
    fun chips(host: Host, call: CommandCall, list: ((String) -> List<Chip>)? = null): List<Chip>? {
        if (KeyboardCommands.isBlocked(Settings.getValues(), host.editorInfo)) return emptyList()
        val readings = readings(host, call)
        if (isLegacy(readings)) return null
        val context = host.context
        val dark = CommandColors.isDark(KeyboardCommands.chipBackground())
        val plans = readings.map { plan(host, it, dark) }
        val single = readings.singleOrNull()?.steps?.singleOrNull()
        val chips = ArrayList<Chip>()
        if (single != null && single.unknown.isEmpty() && plans.single().steps.single().showOnly) {
            // one step that only shows or lists: its entries instead of a preview ("@lang ?" also its slots)
            if (single.action == Action.LIST && call.text.trimEnd().endsWith("?")) chips += syntaxChip(context, single.area, call.prefix, dark)
            chips += entries(host, call, single, dark, list)
        } else for ((index, plan) in plans.withIndex()) chips += previewChip(host, call, plan, index, plans.size)
        chips += suggestions(host, call, readings.first(), dark)
        return chips.distinctBy { it.label.toString() }
    }

    /**
     * Enter while [call] is typed (stage 52): runs exactly one harmless, complete reading like a tap
     * and takes the key; any other reading of the unified syntax takes the key and only says why
     * ([Plan.hint]; the command is never sent to the app). Null for the old syntax (the area decides)
     * and, since the review, for words that do not read as a command ([Reading.looksLikeCommand]):
     * "Hallo @gruppe wer kommt heute?" or a bare "@mem" is sent as usual.
     */
    fun onEnter(host: Host, call: CommandCall): Boolean? {
        if (KeyboardCommands.isBlocked(Settings.getValues(), host.editorInfo)) return null
        val readings = readings(host, call)
        if (isLegacy(readings) || readings.none { it.looksLikeCommand }) return null
        val plans = readings.map { plan(host, it, dark = true) }
        val plan = plans.singleOrNull()
        if (plan != null && plan.runnable && plan.harmless) {
            // a gesture still being processed: the key is taken, a second Enter runs it (P149)
            if (host.canReplaceNow) execute(host, call, plan)
            return true
        }
        val hint = if (plan == null) host.context.getString(R.string.agent_unified_tap_to_run) else plan.hint(host.context)
        Toast.makeText(host.context, hint, Toast.LENGTH_SHORT).show()
        return true
    }

    /** `@?`: one chip per enabled area; a tap writes "@area ". */
    fun helpChips(host: Host, names: CommandNames): List<Chip> {
        if (KeyboardCommands.isBlocked(Settings.getValues(), host.editorInfo)) return emptyList()
        val context = host.context
        val dark = CommandColors.isDark(KeyboardCommands.chipBackground())
        return names.candidates.mapNotNull { name ->
            val area = AREAS[name] ?: return@mapNotNull null
            val word = "${names.prefix}$name"
            val label = Words(dark).add(word, Role.AREA).plain(" ").add(areaLabel(context, area), null).text
            Chip(label, context.getString(R.string.agent_unified_help_area, word, areaLabel(context, area)), Chip.Kind.TEMPLATE) {
                host.replace(names, "$word ")
            }
        }
    }

    // ---- undo ----

    /** "Undo" after a command ran, while the cursor is still where the command was. */
    fun undoChips(host: Host): List<Chip> {
        val state = undo ?: return emptyList()
        if (clock() >= state.until || state.used) { undo = null; return emptyList() }
        if (host.cursor != state.cursor || host.editorInfo?.packageName != state.packageName) return emptyList()
        if (KeyboardCommands.isBlocked(Settings.getValues(), host.editorInfo)) return emptyList()
        state.refresh = { host.refresh() }
        val context = host.context
        return listOf(Chip(context.getString(R.string.agent_unified_undo),
            context.getString(R.string.agent_unified_undo_description, state.description)) {
            if (undo !== state || state.used || clock() >= state.until) return@Chip
            state.used = true
            undo = null
            var failed = false
            for (step in state.steps.asReversed()) if (runCatching { step(host) }.isFailure) failed = true
            Toast.makeText(context, if (failed) R.string.agent_unified_failed else R.string.agent_unified_undone, Toast.LENGTH_SHORT).show()
            host.refresh()
        })
    }

    // ---- plans: what a reading does ----

    /**
     * What an effect did: [undo] puts it back; [after] runs once the command text is gone (switching
     * the keyboard's language, emptying the clipboard history), so that nothing in it has to be put
     * back when the text cannot be removed.
     */
    private class Done(val undo: (Host) -> Unit, val after: ((Host) -> Unit)? = null)

    /** Changes a setting and says how to undo it, or returns null with nothing changed. */
    private fun interface Effect { fun run(host: Host): Done? }

    /**
     * What one step does: its words in the preview, and its effect (null: it only shows something).
     * [problem] says why a step cannot run although every word is known (a limit, a chain).
     */
    private class StepPlan(val text: CharSequence, val plain: String, val effect: Effect?,
                           val complete: Boolean, val harmless: Boolean, val problem: String? = null) {
        /** A step that only shows or lists something, and can. */
        val showOnly get() = effect == null && complete && problem == null
    }

    private class Plan(val reading: Reading, val steps: List<StepPlan>, val label: CharSequence, val plain: String) {
        val hasEffect get() = steps.any { it.effect != null }
        val runnable get() = hasEffect && reading.unknownCount == 0 && steps.all { it.complete }
        val harmless get() = steps.all { it.harmless }

        /** Why this reading does not run on Enter (or on a tap of its grey chip). */
        fun hint(context: Context): String {
            val unknown = reading.steps.flatMap { it.unknown }
            if (unknown.isNotEmpty()) {
                // an unknown word where an app can stand: maybe not installed, maybe the apps are not read yet (P201)
                val apps = reading.steps.any { it.unknown.isNotEmpty() && it.area in APP_AREAS }
                return context.getString(if (apps) R.string.agent_unified_unknown_app_toast else R.string.agent_unified_unknown_toast,
                    unknown.joinToString(", "))
            }
            steps.firstNotNullOfOrNull { it.problem }?.let { return it }
            if (!steps.all { it.complete }) return context.getString(R.string.agent_unified_incomplete_toast)
            if (!hasEffect) return context.getString(R.string.agent_unified_show_only_toast)
            return context.getString(R.string.agent_unified_tap_to_run)
        }
    }

    /** Areas whose words can name apps. */
    private val APP_AREAS = setOf(Area.LANG, Area.GUARD, Area.MEM, Area.GROUP)

    private fun plan(host: Host, reading: Reading, dark: Boolean): Plan {
        var steps = reading.steps.map { stepPlan(host, it, dark) }
        if (steps.size > 1) {
            // review of stage 52: in a chain every step changes something; inserting (the old @snip
            // name, @clip word) and showing work only on their own, else a tap would only delete the text
            val chain = host.context.getString(R.string.agent_unified_chain_toast)
            steps = steps.map { if (it.showOnly) StepPlan(it.text, it.plain, null, false, it.harmless, chain) else it }
        }
        val label = Words(dark)
        steps.forEachIndexed { index, step ->
            if (index > 0) label.add(" ; ", Role.SEPARATOR)
            label.append(step.text)
        }
        return Plan(reading, steps, label.text, steps.joinToString(" ; ") { it.plain })
    }

    /**
     * A tap on a preview: runs every step, then removes the command, then what has to wait for that
     * (a language switch); offers "Undo". Review of stage 52: when a step fails, or the command cannot
     * be removed (the field changed), every step that ran is put back and the text stays.
     */
    private fun execute(host: Host, call: CommandCall, plan: Plan): Boolean {
        if (KeyboardCommands.isBlocked(Settings.getValues(), host.editorInfo) || !plan.runnable) return false
        val at = host.cursor
        val done = ArrayList<Done>()
        fun putBack() { for (step in done.asReversed()) runCatching { step.undo(host) } }
        for (step in plan.steps) {
            val effect = step.effect ?: continue
            val result = runCatching { effect.run(host) }.getOrNull()
            if (result == null) {
                putBack()
                Toast.makeText(host.context, R.string.agent_unified_failed, Toast.LENGTH_SHORT).show()
                return false
            }
            done += result
        }
        // the command goes, in the field it was typed in; when that fails nothing stays changed
        if (!host.replace(call, "")) { putBack(); return false }
        for (step in done) step.after?.let { runCatching { it(host) } }
        if (done.isNotEmpty()) {
            // the cursor the strip is built for once the command is gone
            val state = Undo(plan.plain, host.editorInfo?.packageName, if (at < 0) -1 else at - call.text.length,
                clock() + UNDO_MILLIS, done.map { it.undo })
            undo = state
            schedule(UNDO_MILLIS) { if (undo === state) { undo = null; state.refresh?.invoke() } }
        }
        return true
    }

    private fun previewChip(host: Host, call: CommandCall, plan: Plan, index: Int, count: Int): Chip {
        val context = host.context
        val plain = if (count > 1) context.getString(R.string.agent_unified_reading, index + 1, count, plan.plain) else plan.plain
        if (!plan.runnable) return Chip(plan.label, context.getString(R.string.agent_unified_cannot, plain), Chip.Kind.MUTED) {
            Toast.makeText(context, plan.hint(context), Toast.LENGTH_SHORT).show()
        }
        var used = false
        return Chip(plan.label, context.getString(R.string.agent_unified_run, plain)) {
            // one tap runs it once; the next strip comes with the changed field
            if (used) return@Chip
            used = true
            if (!execute(host, call, plan)) used = false
        }
    }

    private fun stepPlan(host: Host, step: Step, dark: Boolean): StepPlan {
        val context = host.context
        val words = Words(dark)
        fun unknown() { for (word in step.unknown) words.plain(" ").add("$word?", Role.UNKNOWN) }
        fun done(effect: Effect?, complete: Boolean, harmless: Boolean, problem: String? = null): StepPlan {
            unknown()
            // review of stage 52: a limit is checked here, before anything runs or the text goes
            return StepPlan(words.text, words.text.toString(), effect.takeIf { problem == null }, complete && problem == null, harmless, problem)
        }
        fun limit(max: Int) = context.getString(R.string.agent_unified_limit_toast, max)
        /** The problem when [adding] more entries than [existing] has room for, up to [max]. */
        fun room(existing: Collection<String>, adding: List<String>?, max: Int): String? =
            if (adding != null && (existing + adding).toSet().size > max && adding.any { it !in existing }) limit(max) else null
        words.add(areaLabel(context, step.area), Role.AREA)
        val here = host.editorInfo?.packageName
        val packages: List<String>? = when {
            step.all -> null
            step.targets.isEmpty() -> listOfNotNull(here)
            else -> step.targets.flatMap { target -> when (target) {
                is Target.App -> listOf(target.app.packageName)
                is Target.Group -> target.packages
                Target.All -> emptyList()
            } }.distinct()
        }
        fun targets() {
            words.plain(" ")
            if (step.targets.isEmpty()) words.add(context.getString(R.string.agent_unified_this_app, InstalledApps.label(here.orEmpty())), Role.TARGET)
            else step.targets.forEachIndexed { index, target ->
                if (index > 0) words.plain(", ")
                words.add(targetText(context, target), Role.TARGET)
            }
        }
        fun arrow(text: String, role: Role) { words.plain(" → ").add(text, role) }
        return when (step.area) {
            Area.LANG -> {
                val subtype = step.language?.let { LanguageCommand.subtypeFor(it, host.currentSubtype) }
                when {
                    step.action == Action.LIST -> { words.plain(": ").add(listSummary(context, step.area), Role.VALUE); done(null, true, true) }
                    step.legacy -> {
                        // the old "@lang spanisch" for the field typed in (a step of a chain)
                        words.plain(" ").add(context.getString(R.string.agent_unified_this_field), Role.TARGET)
                        arrow(subtype?.let { languageLabel(context, it) } ?: context.getString(R.string.agent_unified_missing), Role.VALUE)
                        done(subtype?.let { switchEffect(it) }, subtype != null, true)
                    }
                    step.action == Action.DELETE -> {
                        targets(); arrow(context.getString(R.string.agent_unified_delete), Role.ACTION)
                        done(languageEffect(context, packages, null), packages == null || packages.isNotEmpty(), false)
                    }
                    step.language != null || step.action == Action.SET -> {
                        targets(); arrow(subtype?.let { languageLabel(context, it) } ?: context.getString(R.string.agent_unified_missing), Role.VALUE)
                        done(subtype?.let { languageEffect(context, packages, it) }, subtype != null && (packages == null || packages.isNotEmpty()), !step.all,
                            room(CommandAppSettings.languages(context).keys, packages, CommandAppSettings.MAX_APPS))
                    }
                    else -> {
                        targets(); words.plain(": ").add(currentLanguages(context, packages), Role.VALUE)
                        done(null, true, true)
                    }
                }
            }
            Area.GUARD, Area.MEM -> {
                val memory = step.area == Area.MEM
                when (step.action) {
                    Action.LIST -> { words.plain(": ").add(listSummary(context, step.area), Role.VALUE); done(null, true, true) }
                    Action.ON, Action.OFF -> {
                        val on = step.action == Action.ON
                        targets(); arrow(context.getString(if (on) R.string.agent_unified_on else R.string.agent_unified_off), Role.ACTION)
                        val effect = if (memory) memoryEffect(context, packages, on) else guardEffect(context, packages, on)
                        val problem = if (memory) room(Memory.excludedApps(context), packages.takeIf { !on }, Memory.MAX_EXCLUDED)
                            else room(CommandAppSettings.guards(context).keys, packages, CommandAppSettings.MAX_APPS)
                        // switching learning on, or anything for all apps, needs the tap
                        done(effect, packages == null || packages.isNotEmpty(), !step.all && !(memory && on), problem)
                    }
                    Action.DELETE -> {
                        targets(); arrow(context.getString(R.string.agent_unified_default), Role.ACTION)
                        val effect = if (memory) memoryEffect(context, packages, true, reset = true) else guardEffect(context, packages, null)
                        done(effect, packages == null || packages.isNotEmpty(), false)
                    }
                    else -> {
                        targets(); words.plain(": ").add(if (memory) memoryState(context, packages) else guardState(context, packages), Role.VALUE)
                        done(null, true, true)
                    }
                }
            }
            Area.GROUP -> groupPlan(context, step, words, ::done, ::limit)
            Area.SNIP -> snipPlan(context, step, words, ::done, ::limit)
            Area.CLIP -> when (step.action) {
                Action.ON, Action.OFF -> {
                    val on = step.action == Action.ON
                    arrow(context.getString(if (on) R.string.agent_unified_on else R.string.agent_unified_off), Role.ACTION)
                    words.plain(" ").add(context.getString(R.string.agent_unified_clears), null)
                    done(clipEffect(context, on), true, false)
                }
                else -> done(null, true, true)
            }
        }
    }

    private fun groupPlan(context: Context, step: Step, words: Words,
                          done: (Effect?, Boolean, Boolean, String?) -> StepPlan, limit: (Int) -> String): StepPlan {
        val name = step.name
        val members = step.members.map { it.packageName }
        val groups = CommandAppSettings.groups(context)
        val existing = name?.let { groups[it] }
        fun memberText(packages: List<String>) = packages.joinToString(", ") { InstalledApps.label(it) }
        if (step.action == Action.LIST || name == null) {
            words.plain(": ").add(listSummary(context, Area.GROUP), Role.VALUE)
            return done(null, true, true, null)
        }
        words.plain(" ").add(name, Role.TARGET)
        return when {
            step.action == Action.DELETE && members.isEmpty() -> {
                words.plain(" → ").add(context.getString(R.string.agent_unified_delete), Role.ACTION)
                done(groupEffect(context, name, null), existing != null, false, null)
            }
            step.action == Action.DELETE -> {
                words.plain(" − ").add(memberText(members), Role.VALUE)
                val left = existing.orEmpty().filter { it !in members }
                done(groupEffect(context, name, left.ifEmpty { null }), existing != null, false, null)
            }
            step.action == Action.ADD || ((step.action == null || step.action == Action.SET) && members.isNotEmpty()) -> {
                val add = step.action == Action.ADD
                words.plain(if (add) " + " else " = ").add(memberText(members).ifEmpty { context.getString(R.string.agent_unified_missing) }, Role.VALUE)
                val result = if (add) (existing.orEmpty() + members).distinct() else members.distinct()
                // review of stage 52: the limits before anything runs; overwriting is harmless only when it keeps every app
                val problem = when {
                    existing == null && groups.size >= CommandAppSettings.MAX_GROUPS -> limit(CommandAppSettings.MAX_GROUPS)
                    result.size > CommandAppSettings.MAX_MEMBERS -> limit(CommandAppSettings.MAX_MEMBERS)
                    else -> null
                }
                done(groupEffect(context, name, result), members.isNotEmpty(), existing == null || result.containsAll(existing), problem)
            }
            else -> {
                words.plain(": ").add(existing?.let { memberText(it) } ?: context.getString(R.string.agent_unified_missing), Role.VALUE)
                done(null, existing != null, true, null)
            }
        }
    }

    private fun snipPlan(context: Context, step: Step, words: Words,
                         done: (Effect?, Boolean, Boolean, String?) -> StepPlan, limit: (Int) -> String): StepPlan {
        val name = step.name
        return when (step.action) {
            Action.ADD -> {
                words.plain(" ").add(name ?: context.getString(R.string.agent_unified_missing), Role.TARGET)
                val text = step.text
                words.plain(" = ").add(text?.let { "„${SnipCommand.preview(it, 24)}“" } ?: context.getString(R.string.agent_unified_missing), Role.VALUE)
                if (step.nameExists) words.plain(" ").add(context.getString(R.string.agent_unified_replaces), null)
                val valid = name != null && text != null && SnippetStore.isValidName(name) && SnippetStore.isValidText(text)
                val full = !step.nameExists && SnipCommand.names(context).size >= SnippetStore.MAX_SNIPPETS
                done(if (valid) snippetEffect(context, name!!, text) else null, valid, !step.nameExists,
                    if (valid && full) limit(SnippetStore.MAX_SNIPPETS) else null)
            }
            Action.DELETE -> {
                words.plain(" ").add(name ?: context.getString(R.string.agent_unified_missing), Role.TARGET)
                words.plain(" → ").add(context.getString(R.string.agent_unified_delete), Role.ACTION)
                done(if (name != null && step.nameExists) snippetEffect(context, name, null) else null, step.nameExists, false, null)
            }
            else -> done(null, true, true, null)
        }
    }

    // ---- effects; each returns how to undo it, or null with nothing changed ----

    private fun switchEffect(subtype: InputMethodSubtype) = Effect { host ->
        val before = host.currentSubtype
        if (subtype !in LanguageCommand.subtypes()) null
        else Done(undo = { back -> if (before != null && before != subtype && before in LanguageCommand.subtypes()) back.switchSubtype(before) },
            after = { now -> if (subtype != before) now.switchSubtype(subtype) })
    }

    /**
     * Sets (or with [subtype] null removes) the language of [packages], or of all apps with null.
     * Review of stage 52: the room is checked before anything is written, and a write that fails
     * puts back the ones before it.
     */
    private fun languageEffect(context: Context, packages: List<String>?, subtype: InputMethodSubtype?) = Effect { target ->
        val stored = subtype?.toSettingsSubtype()?.toPref()
        val own = CommandAppSettings.languages(context)
        if (packages != null && subtype != null && (own.keys + packages).toSet().size > CommandAppSettings.MAX_APPS) return@Effect null
        val apps = packages ?: (own.keys + CommandAppSettings.savedSubtypeApps(context)).toList()
        // what was there, for the undo: the command's own entries and HeliBoard's per-app record
        val before = apps.associateWith { CommandAppSettings.language(context, it) to CommandAppSettings.savedSubtype(context, it) }
        val allBefore = CommandAppSettings.language(context, CommandAppSettings.ALL)
        fun restore() {
            for ((app, values) in before) {
                CommandAppSettings.setLanguage(context, app, values.first)
                CommandAppSettings.setSavedSubtype(context, app, values.second)
            }
            if (packages == null) CommandAppSettings.setLanguage(context, CommandAppSettings.ALL, allBefore)
        }
        var ok = true
        for (app in apps) {
            if (app != CommandAppSettings.ALL) CommandAppSettings.setSavedSubtype(context, app, null) // the latest choice wins
            ok = CommandAppSettings.setLanguage(context, app, if (packages == null) null else stored) && ok
            if (!ok) break
        }
        if (ok && packages == null) ok = CommandAppSettings.setLanguage(context, CommandAppSettings.ALL, stored)
        if (!ok) { restore(); return@Effect null }
        // the app typed in gets the language at once, as at the next start of its field
        val here = target.editorInfo?.packageName
        val current = target.currentSubtype
        val switchNow = subtype != null && subtype != current && (packages == null || here in packages)
        Done(undo = { back ->
            restore()
            if (switchNow && current != null && current in LanguageCommand.subtypes()) back.switchSubtype(current)
        }, after = if (switchNow) { now -> now.switchSubtype(subtype!!) } else null)
    }

    /** On/off ([on]) or back to the switch (null) for [packages]; for all apps the switch itself and no entries. */
    private fun guardEffect(context: Context, packages: List<String>?, on: Boolean?) = Effect { _ ->
        val entries = CommandAppSettings.guards(context)
        if (packages != null && on != null && (entries.keys + packages).toSet().size > CommandAppSettings.MAX_APPS) return@Effect null
        val switch = helium314.keyboard.agent.FormatGuardChips.isEnabled(context)
        fun restore() {
            for (app in packages ?: entries.keys.toList()) CommandAppSettings.setGuard(context, app, entries[app])
            if (packages == null) context.prefs().edit { putBoolean(AgentPreferences.FORMAT_GUARD, switch) }
        }
        var ok = true
        for (app in packages ?: entries.keys.toList()) {
            ok = CommandAppSettings.setGuard(context, app, if (packages == null) null else on)
            if (!ok) break
        }
        if (!ok) { restore(); return@Effect null }
        if (packages == null && on != null) context.prefs().edit { putBoolean(AgentPreferences.FORMAT_GUARD, on) }
        Done(undo = { _ -> restore() })
    }

    /**
     * Memory on or off for [packages] (the memory's own excluded apps); for all apps the memory's
     * switch, and "on" also takes every app back in. [reset]: back to the switch (no exclusion).
     */
    private fun memoryEffect(context: Context, packages: List<String>?, on: Boolean, reset: Boolean = false) = Effect { _ ->
        val excluded = Memory.excludedApps(context)
        if (packages != null && !on && (excluded + packages).size > Memory.MAX_EXCLUDED) return@Effect null
        val switch = Memory.isEnabled(context)
        fun restore() {
            val now = Memory.excludedApps(context)
            (now - excluded).forEach { Memory.setExcluded(context, it, false) }
            (excluded - now).forEach { Memory.setExcluded(context, it, true) }
            if (packages == null) context.prefs().edit { putBoolean(AgentPreferences.MEMORY, switch) }
        }
        var ok = true
        if (packages == null) {
            if (on) for (app in excluded) { ok = Memory.setExcluded(context, app, false); if (!ok) break }
            if (ok && !reset) context.prefs().edit { putBoolean(AgentPreferences.MEMORY, on) }
        } else for (app in packages) { ok = Memory.setExcluded(context, app, !on); if (!ok) break }
        if (!ok) { restore(); return@Effect null }
        Done(undo = { _ -> restore() })
    }

    private fun groupEffect(context: Context, name: String, packages: List<String>?) = Effect { _ ->
        val before = CommandAppSettings.groups(context)[name]
        if (!CommandAppSettings.setGroup(context, name, packages)) null
        else Done(undo = { _ -> CommandAppSettings.setGroup(context, name, before) })
    }

    /** Stores [text] as the snippet [name] (replacing one of that name, its apps kept), or deletes it with null. */
    private fun snippetEffect(context: Context, name: String, text: String?) = Effect { _ ->
        val store = snippetStore(context)
        val old = store.read().firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (text == null) store.delete(name) else store.put(Snippet(old?.name ?: name, text, old?.apps.orEmpty()), old?.name)
        SnipCommand.reset()
        Done(undo = { _ ->
            val again = snippetStore(context)
            val now = again.read().firstOrNull { it.name.equals(name, ignoreCase = true) }
            when {
                old == null -> if (now != null) again.delete(now.name)
                now == null -> again.put(old)
                else -> again.put(old, now.name)
            }
            SnipCommand.reset()
        })
    }

    /** The clipboard history switch, with the same cleanup as the switch on the settings page (once the command is gone). */
    private fun clipEffect(context: Context, on: Boolean) = Effect { _ ->
        val before = ClipCommand.historyEnabled(context)
        context.prefs().edit { putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, on) }
        Done(undo = { _ -> context.prefs().edit { putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, before) } },
            after = { _ -> if (before != on) ClipboardDao.getInstance(context)?.clearNonPinned() })
    }

    // ---- shown values ----

    private fun areaLabel(context: Context, area: Area): String = context.getString(when (area) {
        Area.LANG -> R.string.agent_unified_area_lang
        Area.SNIP -> R.string.agent_unified_area_snip
        Area.CLIP -> R.string.agent_unified_area_clip
        Area.GUARD -> R.string.agent_unified_area_guard
        Area.MEM -> R.string.agent_unified_area_mem
        Area.GROUP -> R.string.agent_unified_area_group
    })

    private fun targetText(context: Context, target: Target): String = when (target) {
        Target.All -> context.getString(R.string.agent_unified_all)
        is Target.Group -> "${target.name} (${target.packages.joinToString(", ") { InstalledApps.label(it) }})"
        is Target.App -> target.app.label
    }

    private fun languageLabel(context: Context, subtype: InputMethodSubtype) =
        LanguageCommand.label(context, subtype, LanguageCommand.subtypes()).removePrefix("🌐 ")

    private fun storedLanguageLabel(context: Context, stored: String): String =
        CommandAppSettings.resolve(stored)?.let { languageLabel(context, it.rawSubtype) }
            ?: runCatching { stored.toSettingsSubtype().locale.let { it.getDisplayName(it) } }.getOrDefault(stored)

    private fun effectiveLanguage(context: Context, packageName: String): String? =
        CommandAppSettings.savedSubtype(context, packageName)?.takeIf { perAppSwitch(context) }
            ?: CommandAppSettings.language(context, packageName) ?: CommandAppSettings.language(context, CommandAppSettings.ALL)

    private fun perAppSwitch(context: Context) =
        context.prefs().getBoolean(Settings.PREF_SAVE_SUBTYPE_PER_APP, helium314.keyboard.latin.settings.Defaults.PREF_SAVE_SUBTYPE_PER_APP)

    private fun currentLanguages(context: Context, packages: List<String>?): String {
        val none = context.getString(R.string.agent_unified_none)
        if (packages == null) return CommandAppSettings.language(context, CommandAppSettings.ALL)?.let { storedLanguageLabel(context, it) } ?: none
        return packages.joinToString(", ") { app ->
            val value = effectiveLanguage(context, app)?.let { storedLanguageLabel(context, it) } ?: none
            if (packages.size > 1) "${InstalledApps.label(app)}: $value" else value
        }
    }

    private fun onOff(context: Context, on: Boolean) = context.getString(if (on) R.string.agent_unified_on else R.string.agent_unified_off)

    private fun guardState(context: Context, packages: List<String>?): String {
        if (packages == null) return onOff(context, helium314.keyboard.agent.FormatGuardChips.isEnabled(context))
        return packages.joinToString(", ") { app ->
            val value = onOff(context, CommandAppSettings.guardAllows(context, app))
            if (packages.size > 1) "${InstalledApps.label(app)}: $value" else value
        }
    }

    private fun memoryState(context: Context, packages: List<String>?): String {
        if (packages == null) return onOff(context, Memory.isEnabled(context))
        return packages.joinToString(", ") { app ->
            val value = onOff(context, Memory.isEnabled(context) && !Memory.isExcluded(context, app))
            if (packages.size > 1) "${InstalledApps.label(app)}: $value" else value
        }
    }

    private fun listSummary(context: Context, area: Area): String = when (area) {
        Area.LANG -> CommandAppSettings.languages(context).size.toString()
        Area.GUARD -> CommandAppSettings.guards(context).size.toString()
        Area.MEM -> Memory.excludedApps(context).size.toString()
        Area.GROUP -> CommandAppSettings.groups(context).size.toString()
        else -> ""
    }

    /** The entries of a step that shows or lists: one chip each; a tap writes "@area word " to change it. */
    private fun entries(host: Host, call: CommandCall, step: Step, dark: Boolean, list: ((String) -> List<Chip>)?): List<Chip> {
        val context = host.context
        val prefix = call.prefix
        fun entry(word: String, label: String, value: String, area: Area = step.area): Chip {
            val text = Words(dark).add(label, Role.TARGET).plain(": ").add(value, Role.VALUE).text
            return Chip(text, context.getString(R.string.agent_unified_entry_description, label), Chip.Kind.TEMPLATE) {
                host.replace(call, "$prefix${area.id} $word ")
            }
        }
        fun appWord(packageName: String) = if (packageName == CommandAppSettings.ALL) "all"
            else InstalledApps.apps.firstOrNull { it.packageName == packageName }?.let { it.normalized.ifEmpty { null } }
                ?.let { "app:$it" } ?: packageName
        fun appLabel(packageName: String) = if (packageName == CommandAppSettings.ALL) context.getString(R.string.agent_unified_all) else InstalledApps.label(packageName)
        val here = host.editorInfo?.packageName
        val packages: List<String>? = when {
            step.all -> null
            step.targets.isEmpty() -> if (step.action == Action.LIST) null else listOfNotNull(here)
            else -> step.targets.flatMap { when (it) {
                is Target.App -> listOf(it.app.packageName); is Target.Group -> it.packages; Target.All -> emptyList() } }.distinct()
        }
        val listing = step.action == Action.LIST || step.all
        val chips: List<Chip> = when (step.area) {
            Area.LANG -> {
                val apps = if (listing) (CommandAppSettings.languages(context).keys +
                    (if (perAppSwitch(context)) CommandAppSettings.savedSubtypeApps(context) else emptyList())).toList().sortedBy { appLabel(it).lowercase() }
                    else packages.orEmpty()
                apps.mapNotNull { app ->
                    val value = (if (app == CommandAppSettings.ALL) CommandAppSettings.language(context, app) else effectiveLanguage(context, app))
                        ?.let { storedLanguageLabel(context, it) }
                    if (value == null && listing) null else entry(appWord(app), appLabel(app), value ?: context.getString(R.string.agent_unified_none))
                }
            }
            Area.GUARD -> {
                val apps = if (listing) CommandAppSettings.guards(context).keys.sortedBy { appLabel(it).lowercase() } else packages.orEmpty()
                listOf(entry("all", context.getString(R.string.agent_unified_main_switch), onOff(context, helium314.keyboard.agent.FormatGuardChips.isEnabled(context)))) +
                    apps.map { entry(appWord(it), appLabel(it), onOff(context, CommandAppSettings.guardAllows(context, it))) }
            }
            Area.MEM -> {
                val apps = if (listing) Memory.excludedApps(context).sortedBy { appLabel(it).lowercase() } else packages.orEmpty()
                listOf(entry("all", context.getString(R.string.agent_unified_main_switch), onOff(context, Memory.isEnabled(context)))) +
                    apps.map { entry(appWord(it), appLabel(it), onOff(context, Memory.isEnabled(context) && !Memory.isExcluded(context, it))) }
            }
            Area.GROUP -> {
                val groups = CommandAppSettings.groups(context)
                val shown = if (step.name != null && step.action != Action.LIST) groups.filterKeys { it == step.name } else groups
                shown.map { (name, members) -> entry(name, name, members.joinToString(", ") { InstalledApps.label(it) }) }
            }
            Area.SNIP, Area.CLIP -> list?.invoke(step.text.orEmpty()).orEmpty()
        }
        if (chips.isNotEmpty() || step.area == Area.SNIP || step.area == Area.CLIP) return chips
        val empty = context.getString(if (step.area == Area.GROUP) R.string.agent_unified_no_groups else R.string.agent_unified_nothing_set)
        return listOf(Chip(empty, empty, Chip.Kind.MUTED) {})
    }

    /** "@lang ?": how the area is written, coloured like the preview; a tap does nothing. */
    private fun syntaxChip(context: Context, area: Area, prefix: Char, dark: Boolean): Chip {
        val words = Words(dark).add("$prefix${area.id}", Role.AREA)
        fun slot(text: Int, role: Role) { words.plain(" ").add(context.getString(text), role) }
        fun actions() { words.plain(" ").add(CommandGrammar.ACTIONS.getValue(area).sorted().joinToString("|") { actionWord(it) }, Role.ACTION) }
        when (area) {
            Area.LANG -> { slot(R.string.agent_unified_slot_targets, Role.TARGET); slot(R.string.agent_unified_slot_language, Role.VALUE); actions() }
            Area.GUARD, Area.MEM -> { slot(R.string.agent_unified_slot_targets, Role.TARGET); actions() }
            Area.GROUP -> { slot(R.string.agent_unified_slot_name, Role.TARGET); slot(R.string.agent_unified_slot_apps, Role.VALUE); actions() }
            Area.SNIP -> { actions(); slot(R.string.agent_unified_slot_name, Role.TARGET); slot(R.string.agent_unified_slot_text, Role.VALUE) }
            Area.CLIP -> actions()
        }
        val text = words.text
        return Chip(text, context.getString(R.string.agent_unified_help_syntax, "$prefix${area.id}") + ": " + text, Chip.Kind.MUTED) {}
    }

    private fun actionWord(action: Action) = CommandGrammar.ACTION_WORDS.entries.first { it.value == action }.key

    /**
     * Suggestions for the slot being typed (apps, groups, languages, snippets, actions), or for the
     * next empty slot after a space; a tap completes the word, nothing runs.
     */
    private fun suggestions(host: Host, call: CommandCall, reading: Reading, dark: Boolean): List<Chip> {
        val text = call.text
        val step = reading.steps.lastOrNull() ?: return emptyList()
        if (step.area == Area.SNIP && step.action == Action.ADD) return emptyList() // free text
        val context = host.context
        val typed = if (text.isEmpty() || text.last().isWhitespace()) "" else text.substring(text.indexOfLast { it.isWhitespace() } + 1)
        if (typed.startsWith(call.prefix) || typed.any { !it.isLetter() && it != ':' }) return emptyList()
        val options = ArrayList<Pair<String, String>>() // shown, written
        fun apps() = InstalledApps.apps.forEach { options += it.label to it.normalized }
        fun groups() = CommandAppSettings.groups(context).keys.forEach { options += it to it }
        fun languages() = LanguageCommand.subtypes().distinctBy { it.locale().language }.forEach { subtype ->
            val name = LanguageCommand.nativeName(subtype.locale().language)
            options += name to LanguageNames.normalize(name)
        }
        fun actions(vararg list: Action) = list.forEach { options += actionWord(it) to actionWord(it) }
        if (typed.isEmpty()) {
            // the next empty slot after a space
            when (step.area) {
                Area.LANG -> if (step.targets.isNotEmpty() && step.language == null && step.action == null) languages()
                Area.GUARD, Area.MEM -> if (step.targets.isNotEmpty() && step.action == null) actions(Action.ON, Action.OFF)
                Area.SNIP -> if (step.action == Action.DELETE && step.name == null) SnipCommand.names(context).forEach { options += it to it }
                else -> {}
            }
        } else {
            val bare = typed.removePrefix(CommandGrammar.APP_PREFIX)
            when (step.area) {
                Area.LANG -> { apps(); groups(); languages() }
                Area.GUARD, Area.MEM -> { apps(); groups() }
                // the first word is the group's name, the others are its apps
                Area.GROUP -> if (step.words.firstOrNull { it.role != Role.ACTION }?.end == text.length) groups() else apps()
                Area.SNIP -> if (step.action == Action.DELETE) SnipCommand.names(context).forEach { options += it to it }
                Area.CLIP -> {}
            }
            val started = NameMatch.started(bare, options.map { it.first }).toSet()
            options.retainAll { it.first in started }
            options.sortBy { LanguageNames.normalize(it.first).length }
        }
        val base = text.substring(0, text.length - typed.length)
        return options.distinctBy { it.second }.filter { it.second.isNotEmpty() }.take(MAX_SUGGESTIONS).map { (shown, written) ->
            val label = Words(dark).add(shown, if (step.area == Area.LANG && step.targets.isNotEmpty() && typed.isEmpty()) Role.VALUE else Role.TARGET).text
            Chip(label, context.getString(R.string.agent_command_template, shown), Chip.Kind.TEMPLATE) {
                host.replace(call, "$base$written ")
            }
        }
    }

    /** Builds coloured text. */
    internal class Words(private val dark: Boolean) {
        private val out = SpannableStringBuilder()
        val text: CharSequence get() = out
        fun add(text: CharSequence, role: Role?): Words {
            val start = out.length
            out.append(text)
            if (role != null && out.length > start)
                out.setSpan(ForegroundColorSpan(CommandColors.of(role, dark)), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return this
        }
        fun plain(text: CharSequence) = add(text, null)
        fun append(text: CharSequence): Words { out.append(text); return this }
    }
}

/**
 * Stage 52: the colours of a command, like a code editor: one per [Role], a palette for dark and
 * one for light chips (the strip follows the keyboard's colours), each readable on its background.
 */
object CommandColors {
    private val DARK = mapOf(
        Role.AREA to 0xFF82AAFF.toInt(), Role.ACTION to 0xFFC792EA.toInt(), Role.TARGET to 0xFFFFCB6B.toInt(),
        Role.VALUE to 0xFFC3E88D.toInt(), Role.SEPARATOR to 0xFF89DDFF.toInt(), Role.UNKNOWN to 0xFFFF5370.toInt(),
    )
    private val LIGHT = mapOf(
        Role.AREA to 0xFF1E5BD6.toInt(), Role.ACTION to 0xFF7A2FD0.toInt(), Role.TARGET to 0xFF9A5200.toInt(),
        Role.VALUE to 0xFF2E7D32.toInt(), Role.SEPARATOR to 0xFF00788A.toInt(), Role.UNKNOWN to 0xFFC4142F.toInt(),
    )

    fun of(role: Role, dark: Boolean): Int = (if (dark) DARK else LIGHT).getValue(role)

    /** True when [background] is dark enough for the light colours. */
    fun isDark(background: Int): Boolean = ColorUtils.calculateLuminance(background or 0xFF000000.toInt()) < 0.5
}
