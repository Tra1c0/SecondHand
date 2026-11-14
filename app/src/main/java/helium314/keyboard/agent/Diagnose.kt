// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.os.Build
import android.os.Looper
import android.text.format.Formatter
import android.view.accessibility.AccessibilityManager
import dev.agentkeyboard.core.EmojiMixTable
import dev.agentkeyboard.core.RgbEngine
import dev.agentkeyboard.core.RgbEngines
import dev.agentkeyboard.core.RgbLook
import dev.agentkeyboard.core.RgbSettings
import dev.agentkeyboard.core.RgbTarget
import helium314.keyboard.agent.commands.EmojiMixClient
import helium314.keyboard.agent.commands.EmojiMixer
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.screens.getUserAndInternalDictionaries
import java.util.Locale

/**
 * Stage 30: what the page Einstellungen → Diagnose shows, so the owner can send the lead what the
 * phone really does. Every value is a state, a count or a version: never text from a field, the
 * clipboard, a key, learned data or an identifier of the phone (no IMEI, serial or account). Nothing
 * here is stored or logged. The only network use is [checkGstatic], and only on a tap.
 */
object Diagnose {
    class Row(val label: String, val value: String)
    enum class Part { HAPTICS, SYMBOLS, LANGUAGE, EMOJI_MIX, LIGHTING, DEVICE }
    class Section(val part: Part, val title: String, val rows: List<Row>)

    // --- haptics (HapticChain) ---

    /** `VibrationEffect.Composition.PRIMITIVE_*` (API 30, low tick API 31) by their names. */
    private val PRIMITIVES = listOf(1 to "click", 2 to "thud", 3 to "spin", 4 to "quick_rise", 5 to "slow_rise",
        6 to "quick_fall", HapticChain.PRIMITIVE_TICK to "tick", 8 to "low_tick")

    enum class Tier { PRIMITIVES, WAVEFORM, PULSES }
    /**
     * What the space-bar detents play (stage 33): the [plan] of the last detent ("stretch") for the
     * test button; [tier]/[primitive] of a light detent tick, [stretchTier]/[stretchPrimitive] of the
     * last detent (it needs other primitives, so its tier can be lower).
     */
    class Chain(val plan: HapticChain.Plan, val tier: Tier, val primitive: Int?, val stretchTier: Tier, val stretchPrimitive: Int?)

    /**
     * The only use of HapticChain's plan API on this page (adapt here when the chain changes): what
     * the chain plays on [device] (the phone's vibrator), null without a vibrator.
     */
    fun chain(device: HapticChain.Device?): Chain? {
        if (device == null || !device.hasVibrator) return null
        fun tierOf(plan: HapticChain.Plan) = when (plan) {
            is HapticChain.Plan.Primitives -> Tier.PRIMITIVES to plan.steps.last().primitive
            is HapticChain.Plan.Waveform -> Tier.WAVEFORM to null
            is HapticChain.Plan.Pulses -> Tier.PULSES to null
        }
        val (tier, primitive) = tierOf(HapticChain.plan(HapticChain.Feel.DETENT, device))
        val stretch = HapticChain.plan(HapticChain.Feel.STRETCH, device)
        val (stretchTier, stretchPrimitive) = tierOf(stretch)
        return Chain(stretch, tier, primitive, stretchTier, stretchPrimitive)
    }

    private fun tierText(context: Context, tier: Tier?, primitive: Int?) = when (tier) {
        null -> context.getString(R.string.agent_diagnose_tier_none)
        Tier.PRIMITIVES -> context.getString(R.string.agent_diagnose_tier_primitives,
            PRIMITIVES.firstOrNull { it.first == primitive }?.second ?: primitive.toString())
        Tier.WAVEFORM -> context.getString(R.string.agent_diagnose_tier_waveform)
        Tier.PULSES -> context.getString(R.string.agent_diagnose_tier_pulses)
    }

    fun haptics(context: Context, device: HapticChain.Device?): Section {
        val vibrator = device?.hasVibrator == true
        val primitives = when {
            device == null || !vibrator -> context.getString(R.string.agent_diagnose_none)
            device.sdk < Build.VERSION_CODES.R -> context.getString(R.string.agent_diagnose_primitives_old)
            else -> PRIMITIVES.filter { device.supportsPrimitive(it.first) }.joinToString(", ") { it.second }
                .ifEmpty { context.getString(R.string.agent_diagnose_none) }
        }
        val chain = chain(device)
        val tier = tierText(context, chain?.tier, chain?.primitive)
        val stretchTier = tierText(context, chain?.stretchTier, chain?.stretchPrimitive)
        val keyVibration = context.prefs().getBoolean(Settings.PREF_VIBRATE_ON, Defaults.PREF_VIBRATE_ON)
        return Section(Part.HAPTICS, context.getString(R.string.agent_diagnose_haptics), listOf(
            Row(context.getString(R.string.agent_diagnose_vibrator), yesNo(context, vibrator)),
            Row(context.getString(R.string.agent_diagnose_amplitude), yesNo(context, vibrator && device?.hasAmplitudeControl == true)),
            Row(context.getString(R.string.agent_diagnose_primitives), primitives),
            Row(context.getString(R.string.agent_diagnose_tier), tier),
            Row(context.getString(R.string.agent_diagnose_tier_stretch), stretchTier),
            Row(context.getString(R.string.agent_diagnose_key_vibration), onOff(context, keyVibration)),
        ))
    }

    // --- drawn symbols ---

    fun symbols(context: Context): Section {
        val prefs = context.prefs()
        val drawing = prefs.getBoolean(AgentPreferences.SYMBOL_DRAWING, false)
        val gestureTyping = JniUtils.sHaveGestureLib && prefs.getBoolean(Settings.PREF_GESTURE_INPUT, Defaults.PREF_GESTURE_INPUT)
        val locale = currentLocale(context)
        val mainDictionary = locale?.let { hasMainDictionary(context, it) } == true
        val touchExploration = (context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)
            ?.isTouchExplorationEnabled == true
        // the same conditions as GestureEnabler (LatinIME: gesture typing or drawn symbols by the user)
        val handling = when {
            !drawing && !gestureTyping -> context.getString(R.string.agent_diagnose_gestures_off_user)
            !mainDictionary -> context.getString(R.string.agent_diagnose_gestures_off_dictionary)
            touchExploration -> context.getString(R.string.agent_diagnose_gestures_off_talkback)
            else -> context.getString(R.string.agent_diagnose_gestures_on)
        }
        return Section(Part.SYMBOLS, context.getString(R.string.agent_diagnose_symbols), listOf(
            Row(context.getString(R.string.agent_symbols_title), onOff(context, drawing)),
            Row(context.getString(R.string.agent_diagnose_gesture_library),
                context.getString(if (JniUtils.sHaveGestureLib) R.string.agent_diagnose_present else R.string.agent_diagnose_gesture_library_missing)),
            Row(context.getString(R.string.agent_diagnose_main_dictionary, locale?.toLanguageTag() ?: "?"),
                context.getString(if (mainDictionary) R.string.agent_diagnose_present else R.string.agent_diagnose_missing)),
            Row(context.getString(R.string.agent_diagnose_talkback), onOff(context, touchExploration)),
            Row(context.getString(R.string.agent_diagnose_gestures), handling),
        ))
    }

    private fun currentLocale(context: Context): Locale? =
        runCatching { SubtypeSettings.getSelectedSubtype(context.prefs()).locale() }.getOrNull()

    /** How often [hasMainDictionary] listed the files, and whether the last time was on the main thread; for tests. */
    @Volatile internal var dictionaryLookups = 0
        private set
    @Volatile internal var dictionaryLookupOnMainThread = false
        private set

    /**
     * An internal or user-added main dictionary for [locale], as the dictionary page counts them.
     * Lists files: the page asks once, off the main thread (stage 30 fix).
     */
    private fun hasMainDictionary(context: Context, locale: Locale): Boolean = runCatching {
        dictionaryLookups++
        dictionaryLookupOnMainThread = Looper.myLooper() == Looper.getMainLooper()
        val (userDicts, internal) = getUserAndInternalDictionaries(context, locale)
        internal || userDicts.any { it.name.startsWith(helium314.keyboard.latin.dictionary.Dictionary.TYPE_MAIN) }
    }.getOrDefault(false)

    // --- language switches (SubtypeSwitchLog) ---

    fun languages(context: Context, entries: List<SubtypeSwitchLog.Entry>, nowElapsedMillis: Long): Section {
        val prefs = context.prefs()
        val rows = mutableListOf(
            Row(context.getString(R.string.agent_diagnose_language_now), currentLocale(context)?.toLanguageTag() ?: "?"),
            Row(context.getString(R.string.agent_diagnose_per_app),
                onOff(context, prefs.getBoolean(Settings.PREF_SAVE_SUBTYPE_PER_APP, Defaults.PREF_SAVE_SUBTYPE_PER_APP))),
        )
        if (entries.isEmpty()) rows += Row(context.getString(R.string.agent_diagnose_switch_last), context.getString(R.string.agent_diagnose_switch_none))
        entries.forEachIndexed { index, entry ->
            val label = if (index == 0) context.getString(R.string.agent_diagnose_switch_last)
                else context.getString(R.string.agent_diagnose_switch_before, index + 1)
            val seconds = ((nowElapsedMillis - entry.atElapsedMillis) / 1000).coerceAtLeast(0)
            rows += Row(label, context.getString(R.string.agent_diagnose_switch_value, reason(context, entry.reason), entry.languageTag, ago(context, seconds)))
        }
        return Section(Part.LANGUAGE, context.getString(R.string.agent_diagnose_language), rows)
    }

    fun reason(context: Context, reason: SubtypeSwitchLog.Reason): String = context.getString(when (reason) {
        SubtypeSwitchLog.Reason.APP_HINT -> R.string.agent_diagnose_reason_hint
        SubtypeSwitchLog.Reason.HINT_ENDED -> R.string.agent_diagnose_reason_hint_ended
        SubtypeSwitchLog.Reason.PER_APP -> R.string.agent_diagnose_reason_per_app
        SubtypeSwitchLog.Reason.COMMAND -> R.string.agent_diagnose_reason_command
        SubtypeSwitchLog.Reason.KEYBOARD -> R.string.agent_diagnose_reason_keyboard
        SubtypeSwitchLog.Reason.SYSTEM -> R.string.agent_diagnose_reason_system
        SubtypeSwitchLog.Reason.KEPT_BY_CHOICE -> R.string.agent_diagnose_reason_kept
    })

    private fun ago(context: Context, seconds: Long) =
        if (seconds < 120) context.getString(R.string.agent_diagnose_seconds_ago, seconds)
        else context.getString(R.string.agent_diagnose_minutes_ago, seconds / 60)

    // --- Emoji-Mix ---

    /** The result of the reachability check; [Running] while it runs. */
    sealed interface Reach {
        object Running : Reach
        class Reachable(val bytes: Int) : Reach
        /** The server answered, but has no image for the test pair (404). */
        object NoImage : Reach
        class Failed(val reason: EmojiMixClient.Reason) : Reach
        /** Without the pair table there is no allowed URL to ask for. */
        object NoTable : Reach
    }

    /** The pair whose image the check loads (in the table, see EmojiMixTest). */
    const val TEST_LEFT = "🐶"
    const val TEST_RIGHT = "🐱"

    /** The pair table from the asset (off the main thread), or null when it cannot be read. */
    fun readTable(context: Context): EmojiMixTable? =
        runCatching { EmojiMixer.tableSource(context).use { EmojiMixTable.read(it) } }.getOrNull()

    /** Number and bytes of the stored images (off the main thread). */
    fun cacheStats(context: Context): Pair<Int, Long> {
        val cache = EmojiMixer.cache(context)
        val files = cache.directory.listFiles()?.filter { it.isFile }.orEmpty()
        return files.size to files.sumOf { it.length() }
    }

    /**
     * After a tap only: one GET of the test pair's image from www.gstatic.com, with the Emoji-Mix
     * client and its checks (table URL only, no cookies, no redirects, PNG). The image is not kept.
     * Blocking: call off the main thread.
     */
    fun checkGstatic(context: Context, table: EmojiMixTable?): Reach {
        val mix = table?.find(TEST_LEFT, TEST_RIGHT) ?: return Reach.NoTable
        return try {
            when (val result = EmojiMixClient(EmojiMixer.calls(context)).fetch(mix.url)) {
                is EmojiMixClient.Result.Image -> Reach.Reachable(result.bytes.size)
                EmojiMixClient.Result.Missing -> Reach.NoImage
                is EmojiMixClient.Result.Failed -> Reach.Failed(result.reason)
            }
        } catch (_: Exception) {
            Reach.Failed(EmojiMixClient.Reason.INTERNAL)
        }
    }

    /** [table] null and [tableRead] false: still reading. [cache]: count and bytes, null while counting. */
    fun emojiMix(context: Context, table: EmojiMixTable?, tableRead: Boolean, cache: Pair<Int, Long>?, reach: Reach?): Section {
        val tableText = when {
            table != null -> context.getString(R.string.agent_diagnose_table_value, table.pairCount, table.emojiCount)
            tableRead -> context.getString(R.string.agent_diagnose_table_unreadable)
            else -> context.getString(R.string.agent_diagnose_reading)
        }
        val cacheText = cache?.let { (count, bytes) ->
            context.getString(R.string.agent_diagnose_cache_value, count, Formatter.formatShortFileSize(context, bytes))
        } ?: context.getString(R.string.agent_diagnose_reading)
        val reachText = when (reach) {
            null -> context.getString(R.string.agent_diagnose_gstatic_unchecked)
            Reach.Running -> context.getString(R.string.agent_diagnose_gstatic_running)
            is Reach.Reachable -> context.getString(R.string.agent_diagnose_gstatic_ok, Formatter.formatShortFileSize(context, reach.bytes.toLong()))
            Reach.NoImage -> context.getString(R.string.agent_diagnose_gstatic_no_image)
            Reach.NoTable -> context.getString(R.string.agent_diagnose_table_unreadable)
            is Reach.Failed -> context.getString(R.string.agent_diagnose_gstatic_failed, failure(context, reach.reason))
        }
        return Section(Part.EMOJI_MIX, context.getString(R.string.agent_diagnose_emoji_mix), listOf(
            Row(context.getString(R.string.agent_diagnose_table), tableText),
            Row(context.getString(R.string.agent_diagnose_cache), cacheText),
            Row(context.getString(R.string.agent_diagnose_gstatic), reachText),
        ))
    }

    private fun failure(context: Context, reason: EmojiMixClient.Reason) = context.getString(when (reason) {
        EmojiMixClient.Reason.NETWORK -> R.string.agent_diagnose_failure_network
        EmojiMixClient.Reason.TIMEOUT -> R.string.agent_diagnose_failure_timeout
        EmojiMixClient.Reason.REDIRECT -> R.string.agent_diagnose_failure_redirect
        EmojiMixClient.Reason.SERVICE -> R.string.agent_diagnose_failure_service
        EmojiMixClient.Reason.NOT_PNG, EmojiMixClient.Reason.TOO_LARGE -> R.string.agent_diagnose_failure_answer
        EmojiMixClient.Reason.NOT_ALLOWED, EmojiMixClient.Reason.STORAGE, EmojiMixClient.Reason.INTERNAL -> R.string.agent_diagnose_failure_internal
    })

    // --- RGB lighting cost (build 14 feedback: the keyboard lagged) ---

    /** What drawing the light cost in the last seconds the keyboard was lit ([RgbLighting.lastFrameWork]). */
    fun lighting(context: Context, work: dev.agentkeyboard.core.RgbFrameStats.Summary, patterns: List<Row> = emptyList()): Section {
        fun ms(avg: Double, max: Double) = context.getString(R.string.agent_diagnose_ms, avg, max)
        val rows = if (work.frames == 0 && work.jobs == 0) listOf(Row(context.getString(R.string.agent_diagnose_light_frames),
            context.getString(R.string.agent_diagnose_light_none)))
        else listOf(
            Row(context.getString(R.string.agent_diagnose_light_frames), work.frames.toString()),
            Row(context.getString(R.string.agent_diagnose_light_ui), ms(work.uiAvgMs, work.uiMaxMs)),
            Row(context.getString(R.string.agent_diagnose_light_draw), ms(work.drawAvgMs, work.drawMaxMs)),
            Row(context.getString(R.string.agent_diagnose_light_background), "${work.jobs} · " + ms(work.backgroundAvgMs, work.backgroundMaxMs)),
        )
        return Section(Part.LIGHTING, context.getString(R.string.agent_diagnose_lighting), rows + patterns)
    }

    /**
     * The patterns the lit elements use (stage 34: own patterns by name, marked "own") and what one
     * light grid of each costs on this phone, measured now (48 x 32 values, the best of three).
     * Measure off the main thread; reads only the setting, no text.
     */
    fun lightPatterns(context: Context, settings: RgbSettings): List<Row> {
        if (!settings.enabled) return emptyList()
        val lit = RgbTarget.entries.filter { settings.lights(it) }
        if (lit.isEmpty()) return emptyList()
        fun name(look: RgbLook) = if (look.usesOwn) "${look.own!!.name} (${context.getString(R.string.agent_diagnose_light_own)})"
            else context.getString(helium314.keyboard.settings.screens.patternLabelRes(look.pattern))
        val engines = RgbEngines(settings)
        val rows = mutableListOf(Row(context.getString(R.string.agent_diagnose_light_patterns),
            lit.map { name(settings.lookFor(it)) }.distinct().joinToString(", ")))
        val measured = HashSet<RgbEngine>()
        for (target in lit) {
            val engine = engines.forTarget(target)
            if (!measured.add(engine)) continue
            val ms = gridMs(engine)
            rows += Row(context.getString(R.string.agent_diagnose_light_grid, name(settings.lookFor(target))),
                String.format(Locale.getDefault(), "%.2f ms", ms))
        }
        return rows
    }

    /** Time of one 48 x 32 light grid of [engine] in ms, the best of three after a warm-up. */
    fun gridMs(engine: RgbEngine): Double {
        engine.aspect = 0.45f
        engine.advance(0)
        engine.advance(40)
        var sink = 0
        var best = Double.MAX_VALUE
        repeat(4) { round ->
            val start = System.nanoTime()
            for (r in 0 until 32) for (c in 0 until 48) sink += engine.light((c + 0.5f) / 48, (r + 0.5f) / 32, -1, 40)
            if (round > 0) best = minOf(best, (System.nanoTime() - start) / 1e6)
        }
        if (sink == Int.MIN_VALUE) best = 0.0
        return best
    }

    // --- app and phone ---

    fun device(context: Context): Section = Section(Part.DEVICE, context.getString(R.string.agent_diagnose_device), listOf(
        Row(context.getString(R.string.agent_diagnose_app), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})"),
        Row(context.getString(R.string.agent_diagnose_android), "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
        Row(context.getString(R.string.agent_diagnose_model), "${Build.MANUFACTURER} ${Build.MODEL}"),
    ))

    /** Exactly what the page shows, as text for the clipboard. */
    fun text(context: Context, sections: List<Section>): String = buildString {
        append(context.getString(R.string.agent_diagnose_copy_heading))
        for (section in sections) {
            append("\n\n").append(section.title)
            for (row in section.rows) append('\n').append(row.label).append(": ").append(row.value)
        }
    }

    private fun yesNo(context: Context, value: Boolean) =
        context.getString(if (value) R.string.agent_diagnose_yes else R.string.agent_diagnose_no)

    private fun onOff(context: Context, value: Boolean) =
        context.getString(if (value) R.string.agent_diagnose_on else R.string.agent_diagnose_off)
}
