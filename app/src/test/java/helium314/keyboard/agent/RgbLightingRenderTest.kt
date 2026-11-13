// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.os.Looper
import android.os.PowerManager
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.TextView
import dev.agentkeyboard.core.RgbArea
import dev.agentkeyboard.core.RgbColors
import dev.agentkeyboard.core.RgbEngine
import dev.agentkeyboard.core.RgbLayer
import dev.agentkeyboard.core.RgbPattern
import dev.agentkeyboard.core.RgbRun
import dev.agentkeyboard.core.RgbSettings
import dev.agentkeyboard.core.RgbTapReaction
import dev.agentkeyboard.core.ProfileGroup
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.KeyboardElement
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.KeyboardMode
import helium314.keyboard.keyboard.MainKeyboardView
import helium314.keyboard.keyboard.internal.KeyboardBuilder
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.UniqueKeysCache
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.suggestions.SuggestionStripView
import helium314.keyboard.latin.utils.SubtypeUtilsAdditional
import helium314.keyboard.latin.utils.prefs
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Stage 26: the RGB lighting drawn by the real keyboard view (Flat style, known colours), the
 * suggestion strip, the battery rules in the app and the design profile. Robolectric with native
 * graphics, not a device test (P29).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w369dp-h821dp-520dpi", shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class RgbLightingRenderTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val prefs get() = latinIME.prefs()
    private val lit = RgbSettings(enabled = true, pattern = RgbPattern.STATIC_RAINBOW)

    @Before fun applyPreset() {
        prefs.edit().clear().commit()
        latinIME.getSharedPreferences(FlatPreset.BACKUP_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        latinIME.getSharedPreferences(KeyboardProfileStore.STORAGE_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        FlatPreset(latinIME).apply()
        reload()
    }

    /** Settings and the lighting are singletons (P32): later tests must see the defaults. */
    @After fun restoreDefaults() {
        android.provider.Settings.Global.putFloat(latinIME.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        prefs.edit().clear().commit()
        reload()
        RgbLighting.load(latinIME)
    }

    private fun reload() {
        KeyboardIconsSet.needsReload = true
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(EditorInfo(), false, latinIME.packageName))
    }

    private fun use(settings: RgbSettings) = RgbLighting.write(prefs, settings)

    @Test fun offDrawsExactlyLikeWithoutTheFeature() {
        val (keyboard, plain) = render("rgb-off.png")
        assertNull(lastView.rgbPainter, "no painter without the setting")
        assertEquals(RgbRun.Mode.OFF, RgbLighting.mode)
        // switched off, or on without anything lit: no painter and the very same pixels
        for (settings in listOf(lit.copy(enabled = false), lit.copy(layers = emptySet()), lit.copy(areas = emptySet()))) {
            use(settings)
            val (_, picture) = render("rgb-off-variant.png")
            assertNull(lastView.rgbPainter)
            assertTrue(plain.sameAs(picture), "$settings changed the drawing")
        }
        // and the Flat colours are the measured ones
        val q = keyboard.key("q")
        assertEquals("#4C4C4C", hex(plain, q.x + 12, q.y + q.height - 12))
    }

    @Test fun keyBackgroundsTakeTheLightAtTheirCentre() {
        use(lit)
        val (keyboard, picture) = render("rgb-keys.png")
        assertNotNull(lastView.rgbPainter)
        val engine = RgbEngine(lit).apply { aspect = HEIGHT.toFloat() / WIDTH }
        for (label in listOf("q", "g", "m")) {
            val key = keyboard.key(label)
            val expected = RgbColors.applyLight(colors.get(ColorType.KEY_BACKGROUND), engine.light(centreX(key), centreY(key), 0, 0))
            assertEquals(hex(expected), hex(picture, key.x + 12, key.y + key.height - 12), label)
        }
        assertNotEquals(hex(picture, keyboard.key("q").x + 12, keyboard.key("q").y + 20),
            hex(picture, keyboard.key("p").x + 12, keyboard.key("p").y + 20), "a rainbow across the row")
        // the gaps stay the keyboard background
        val q = keyboard.key("q")
        assertEquals("#000000", hex(picture, q.x + q.width + 8, q.y + q.height / 2))
    }

    @Test fun onlyTheChosenAreasLightUp() {
        use(lit.copy(areas = setOf(RgbArea.SPACE)))
        val (keyboard, picture) = render("rgb-space-only.png")
        val q = keyboard.key("q")
        assertEquals("#4C4C4C", hex(picture, q.x + 12, q.y + q.height - 12))
        val shift = keyboard.key(KeyCode.SHIFT)
        assertEquals("#0D0D0D", hex(picture, shift.x + 12, shift.y + shift.height - 12))
        val space = keyboard.key(Constants.CODE_SPACE)
        assertNotEquals("#4C4C4C", hex(picture, space.x + 40, space.y + space.height / 2))
        assertEquals(RgbArea.FUNCTIONAL, RgbKeyPainter.areaOf(shift))
        assertEquals(RgbArea.FUNCTIONAL, RgbKeyPainter.areaOf(keyboard.key(",")))
        assertEquals(RgbArea.FUNCTIONAL, RgbKeyPainter.areaOf(keyboard.key(Constants.CODE_ENTER)))
        assertEquals(RgbArea.LETTERS, RgbKeyPainter.areaOf(q))
        assertEquals(RgbArea.SPACE, RgbKeyPainter.areaOf(space))
    }

    @Test fun labelsStayReadableOnTheirOwnColour() {
        // labels and backgrounds in the same light: the label is pushed to a readable shade
        use(lit.copy(layers = setOf(RgbLayer.KEY_BACKGROUND, RgbLayer.LABEL)))
        val (keyboard, picture) = render("rgb-labels.png")
        val engine = RgbEngine(lit).apply { aspect = HEIGHT.toFloat() / WIDTH }
        val q = keyboard.key("q")
        val light = engine.light(centreX(q), centreY(q), 0, 0)
        val fill = RgbColors.applyLight(colors.get(ColorType.KEY_BACKGROUND), light)
        val label = RgbColors.label(colors.get(ColorType.KEY_TEXT), light, true, fill)
        assertTrue(RgbColors.contrast(label, fill) >= RgbColors.MIN_LABEL_CONTRAST)
        assertTrue(count(picture, q, label) > 200, "label in its readable colour")
        assertEquals(0, count(picture, q, Color.WHITE), "the white theme label is gone")
    }

    @Test fun keyboardBackgroundLayerFillsTheGaps() {
        use(lit.copy(layers = setOf(RgbLayer.KEYBOARD_BACKGROUND), areas = emptySet()))
        val (keyboard, picture) = render("rgb-background.png")
        assertNotNull(lastView.rgbPainter)
        val q = keyboard.key("q")
        assertNotEquals("#000000", hex(picture, q.x + q.width + 8, q.y + q.height / 2))
        assertEquals("#4C4C4C", hex(picture, q.x + 12, q.y + q.height - 12), "keys keep their colour")
    }

    @Test fun flashAndReactiveLightFollowKeyPresses() {
        use(lit.copy(tapReaction = RgbTapReaction.FLASH, reactiveOnly = true, glowMs = 5000))
        val (keyboard, before) = render("rgb-reactive-before.png")
        val q = keyboard.key("q")
        val w = keyboard.key("w")
        assertEquals("#4C4C4C", hex(before, q.x + 12, q.y + q.height - 12), "invisible until a tap")
        lastView.onKeyPressed(q, false)
        lastView.onKeyReleased(q, false)
        val after = draw(lastView)
        assertNotEquals("#4C4C4C", hex(after, q.x + 12, q.y + q.height - 12), "tapped key lights up")
        assertEquals("#4C4C4C", hex(after, w.x + 12, w.y + w.height - 12), "only there")
        assertTrue(RgbLighting.engine.hasActiveEffect(RgbLighting.drawTimeMs))
        RgbLighting.onHideWindow(lastView)
        assertFalse(RgbLighting.engine.hasActiveEffect(RgbLighting.drawTimeMs), "hiding drops the effects")
    }

    @Test fun animationsOffGiveAStillPictureAndIgnoreTaps() {
        android.provider.Settings.Global.putFloat(latinIME.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        use(RgbSettings(enabled = true, tapReaction = RgbTapReaction.FLASH, reactiveOnly = true))
        val (keyboard, _) = render("rgb-static.png")
        assertEquals(RgbRun.Mode.STATIC, RgbLighting.mode)
        val q = keyboard.key("q")
        lastView.onKeyPressed(q, false)
        lastView.onKeyReleased(q, false)
        assertFalse(RgbLighting.engine.hasActiveEffect(RgbLighting.drawTimeMs))
        val picture = draw(lastView)
        assertEquals("#4C4C4C", hex(picture, q.x + 12, q.y + q.height - 12), "reactive light stays off without animations")
    }

    @Test fun aHiddenKeyboardNeverRequestsFrames() {
        use(RgbSettings(enabled = true))
        render("rgb-hidden.png")
        // the test view has no window: shown nowhere, so no frame callbacks
        assertEquals(RgbRun.Mode.PAUSED, RgbLighting.mode)
    }

    @Test fun stripWordsAndButtonsTakeTheLightAndComeBack() {
        val context = ContextThemeWrapper(latinIME, R.style.KeyboardTheme_Flat)
        use(lit.copy(areas = setOf(RgbArea.SUGGESTIONS, RgbArea.TOOLBAR)))
        render("rgb-strip-keys.png") // loads the settings like opening the keyboard
        val container = LayoutInflater.from(context).inflate(R.layout.strip_container, null) as ViewGroup
        val strip = container.findViewById<SuggestionStripView>(R.id.suggestion_strip_view)
        assertSame(strip, RgbLighting.stripView, "the strip reports itself")
        strip.setToolbarVisibility(false)
        container.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(130, View.MeasureSpec.EXACTLY))
        container.layout(0, 0, WIDTH, 130)
        val info = SuggestedWordInfo("Hallo", "", 1, SuggestedWordInfo.KIND_TYPED, null, -1, -1)
        strip.setSuggestions(SuggestedWords(ArrayList(listOf(info)), null, info, true, false, false, 0, 0), false)
        container.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(130, View.MeasureSpec.EXACTLY))
        container.layout(0, 0, WIDTH, 130)
        val words = strip.findViewById<ViewGroup>(R.id.suggestions_strip)
        val word = (0 until words.childCount).map { words.getChildAt(it) }.filterIsInstance<TextView>().first { it.text.toString() == "Hallo" }
        val litBySetSuggestions = word.currentTextColor
        RgbStrip.paint(strip, false)
        val original = word.currentTextColor
        assertNotEquals(original, litBySetSuggestions, "new suggestions are coloured at once, also without frames")
        RgbStrip.paint(strip, true)
        assertNotEquals(original, word.currentTextColor, "word coloured")
        assertTrue(RgbColors.contrast(word.currentTextColor, colors.get(ColorType.STRIP_BACKGROUND)) >= RgbColors.MIN_LABEL_CONTRAST)
        strip.setToolbarVisibility(true)
        val toolbar = strip.findViewById<ViewGroup>(R.id.toolbar)
        val button = (0 until toolbar.childCount).map { toolbar.getChildAt(it) }.filterIsInstance<ImageView>().first()
        button.isActivated = true // a tool that is on (only that state takes the light)
        val buttonFilter = button.colorFilter
        val buttonColour = iconColour(button)
        RgbStrip.paint(strip, true)
        assertNotEquals(buttonColour, iconColour(button), "button coloured")
        assertTrue(RgbColors.contrast(iconColour(button), colors.get(ColorType.STRIP_BACKGROUND)) >= RgbColors.MIN_LABEL_CONTRAST)
        RgbStrip.paint(strip, false)
        assertEquals(original, word.currentTextColor, "word restored")
        assertEquals(buttonFilter, button.colorFilter, "button filter untouched")
        assertEquals(buttonColour, iconColour(button), "button restored")
    }

    @Test fun onlyTheMainKeyboardViewIsLit() {
        use(lit.copy(tapReaction = RgbTapReaction.FLASH, reactiveOnly = true, glowMs = 5000))
        val (keyboard, _) = render("rgb-main.png")
        val main = lastView
        assertNotNull(main.rgbPainter)
        assertSame(main, RgbLighting.controlledView)
        // the bottom rows of the emoji and clipboard views are MainKeyboardViews too
        val context = ContextThemeWrapper(latinIME, R.style.KeyboardTheme_Flat)
        for (id in listOf(R.id.bottom_row_keyboard, View.NO_ID)) {
            val bottomRow = MainKeyboardView(context, null).apply { this.id = id }
            bottomRow.setKeyboard(keyboard)
            assertNull(bottomRow.rgbPainter, "no painter for a bottom row")
            assertSame(main, RgbLighting.controlledView, "the main keyboard stays the lit view")
            // its taps, visibility and hiding do not touch the light of the main keyboard
            bottomRow.onKeyPressed(keyboard.key("q"), false)
            assertFalse(RgbLighting.engine.hasActiveEffect(RgbLighting.drawTimeMs), "a tap on the bottom row starts nothing")
            RgbLighting.onVisibilityChanged(bottomRow)
            RgbLighting.onHideWindow(bottomRow)
        }
        main.onKeyPressed(keyboard.key("q"), false)
        assertTrue(RgbLighting.engine.hasActiveEffect(RgbLighting.drawTimeMs), "the main keyboard still reacts")
    }

    @Test fun theLightStartsAfterTheFirstLayoutAndFollowsPowerSaving() {
        use(RgbSettings(enabled = true))
        reload()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val context = ContextThemeWrapper(activity, R.style.KeyboardTheme_Flat)
        KeyboardIconsSet.instance.loadIcons(context)
        val view = MainKeyboardView(context, null).apply { id = R.id.keyboard_view }
        view.setKeyboard(buildKeyboard(context, KeyboardElement.ALPHABET))
        assertEquals(RgbRun.Mode.PAUSED, RgbLighting.mode, "no window, no size yet")
        activity.setContentView(view)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(view.isAttachedToWindow && view.width > 0)
        // attached before laid out: only the size change after the first layout can start it
        assertEquals(RgbRun.Mode.RUNNING, RgbLighting.mode, "running after the first layout")
        assertTrue(RgbLighting.listensForPowerSave)

        // power saving switched on while running: still picture at once, no frames
        val power = shadowOf(latinIME.getSystemService(PowerManager::class.java))
        power.setIsPowerSaveMode(true)
        latinIME.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(RgbRun.Mode.STATIC, RgbLighting.mode, "power saving is seen without a frame")
        power.setIsPowerSaveMode(false)
        latinIME.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(RgbRun.Mode.RUNNING, RgbLighting.mode, "and its end, also from the still picture")

        // animations switched off while the keyboard was hidden: seen when it is shown again
        view.visibility = View.GONE
        assertEquals(RgbRun.Mode.PAUSED, RgbLighting.mode)
        android.provider.Settings.Global.putFloat(latinIME.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        view.visibility = View.VISIBLE
        assertEquals(RgbRun.Mode.STATIC, RgbLighting.mode)
        android.provider.Settings.Global.putFloat(latinIME.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        view.visibility = View.GONE
        view.visibility = View.VISIBLE
        assertEquals(RgbRun.Mode.RUNNING, RgbLighting.mode)

        // detached: no frames and no receiver left behind
        (view.parent as ViewGroup).removeView(view)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(view.isAttachedToWindow)
        assertEquals(RgbRun.Mode.PAUSED, RgbLighting.mode)
        assertFalse(RgbLighting.listensForPowerSave, "receiver unregistered")
        assertTrue(shadowOf(latinIME.application).registeredReceivers.none { it.broadcastReceiver.javaClass.name.contains("RgbLighting") })
        activity.finish()
    }

    @Test fun theLightOffUnregistersTheReceiver() {
        use(RgbSettings(enabled = true))
        reload()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val context = ContextThemeWrapper(activity, R.style.KeyboardTheme_Flat)
        KeyboardIconsSet.instance.loadIcons(context)
        val view = MainKeyboardView(context, null).apply { id = R.id.keyboard_view }
        val keyboard = buildKeyboard(context, KeyboardElement.ALPHABET)
        view.setKeyboard(keyboard)
        activity.setContentView(view)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(RgbLighting.listensForPowerSave)
        use(RgbSettings())
        view.setKeyboard(keyboard)
        assertEquals(RgbRun.Mode.OFF, RgbLighting.mode)
        assertFalse(RgbLighting.listensForPowerSave, "switched off: nothing registered")
        activity.finish()
    }

    @Test fun theShiftAccentIsTheBaseOfTheIconColour() {
        prefs.edit().putBoolean(Settings.PREF_ACCENT_SHIFTED_ICON, true).commit()
        reload()
        val accent = colors.get(ColorType.SHIFT_KEY_ICON)
        assertNotEquals(accent, colors.get(ColorType.KEY_ICON), "the accent is in use")
        // background only, a weak light: the shifted icon keeps exactly its accent
        use(lit.copy(areas = setOf(RgbArea.FUNCTIONAL), mix = 30))
        val (keyboard, still) = render("rgb-shift-accent.png", KeyboardElement.ALPHABET_MANUAL_SHIFTED)
        val shift = keyboard.key(KeyCode.SHIFT)
        assertTrue(count(still, shift, accent) > 20, "accent kept")
        // with the label layer the light is based on the accent, not on the plain icon colour
        use(lit.copy(areas = setOf(RgbArea.FUNCTIONAL), mix = 30, layers = setOf(RgbLayer.KEY_BACKGROUND, RgbLayer.LABEL)))
        val (_, picture) = render("rgb-shift-label.png", KeyboardElement.ALPHABET_MANUAL_SHIFTED)
        val engine = RgbEngine(lit.copy(mix = 30)).apply { aspect = HEIGHT.toFloat() / WIDTH }
        val light = engine.light(centreX(shift), centreY(shift), 0, 0)
        val fill = RgbColors.over(RgbColors.applyLight(colors.get(ColorType.FUNCTIONAL_KEY_BACKGROUND), light), colors.get(ColorType.MAIN_BACKGROUND))
        val fromAccent = RgbColors.label(accent, light, true, fill)
        val fromIcon = RgbColors.label(colors.get(ColorType.KEY_ICON), light, true, fill)
        assertNotEquals(fromAccent, fromIcon)
        assertTrue(count(picture, shift, fromAccent) > 20, "lit accent")
        assertEquals(0, count(picture, shift, fromIcon), "not the plain icon colour")
    }

    @Test fun semiTransparentLabelsAreReadableAsSeenAndMissingDrawablesAreSkipped() {
        use(lit.copy(layers = setOf(RgbLayer.KEY_BACKGROUND, RgbLayer.LABEL)))
        val (keyboard, _) = render("rgb-alpha.png")
        val painter = assertNotNull(lastView.rgbPainter)
        val q = keyboard.key("q")
        painter.beginDraw(lastView)
        painter.beginKey(q, q.drawX + lastView.paddingLeft, q.y + lastView.paddingTop)
        val light = RgbEngine(lit).apply { aspect = HEIGHT.toFloat() / WIDTH }.light(centreX(q), centreY(q), 0, 0)
        val behind = RgbColors.over(RgbColors.applyLight(colors.get(ColorType.KEY_BACKGROUND), light), colors.get(ColorType.MAIN_BACKGROUND))
        for (alpha in listOf(0x99, 0xCC, 0xFF)) {
            val label = painter.labelColor((alpha shl 24) or 0xFFFFFF)
            assertEquals(alpha, RgbColors.alpha(label))
            assertTrue(RgbColors.contrast(RgbColors.over(label, behind), behind) >= RgbColors.MIN_LABEL_CONTRAST, "alpha $alpha")
        }
        // a state list without a selected child: nothing to tint, no crash
        val empty = StateListDrawable()
        assertNull(empty.current)
        painter.tintBackground(empty)
        painter.restoreBackground()
    }

    @Test fun stripColoursComeBackWhenOnlyTheStripAreasAreDeselected() {
        use(lit.copy(areas = setOf(RgbArea.LETTERS, RgbArea.SUGGESTIONS)))
        val (strip, word) = stripWithWord()
        val lit = word.currentTextColor
        RgbStrip.paint(strip, false)
        val original = word.currentTextColor
        assertNotEquals(original, lit)
        RgbLighting.onStripChanged(strip)
        assertNotEquals(original, word.currentTextColor, "coloured again")
        // the keys stay lit, the strip area is switched off: the next keyboard load restores the strip
        use(this.lit.copy(areas = setOf(RgbArea.LETTERS)))
        render("rgb-strip-off.png")
        assertNotNull(lastView.rgbPainter, "keys still lit")
        assertEquals(original, word.currentTextColor, "strip text has its own colour again")
    }

    @Test fun toolbarButtonsKeepTheirStates() {
        use(lit.copy(areas = setOf(RgbArea.TOOLBAR)))
        val (strip, _) = stripWithWord()
        strip.setToolbarVisibility(true)
        val toolbar = strip.findViewById<ViewGroup>(R.id.toolbar)
        val button = (0 until toolbar.childCount).map { toolbar.getChildAt(it) }.filterIsInstance<ImageView>().first()
        button.isActivated = false
        val off = iconColour(button)
        button.isActivated = true
        val on = iconColour(button)
        assertNotEquals(off, on, "the theme shows switched-off tools")
        RgbStrip.paint(strip, true)
        val litOn = iconColour(button)
        assertNotEquals(on, litOn, "an active tool takes the light")
        button.isActivated = false
        assertEquals(off, iconColour(button), "a switched-off tool keeps its off colour")
        button.isActivated = true
        assertEquals(litOn, iconColour(button))
        // the derived list keeps pressed colours of themes that have them, and the alpha of each state
        val theme = android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()), intArrayOf(0x80FFFFFF.toInt(), 0x40AAAAAA))
        val derived = RgbStrip.litTint(theme, 0x40FF0000)
        assertEquals(0x80FFFFFF.toInt(), derived.getColorForState(intArrayOf(android.R.attr.state_pressed, android.R.attr.state_activated), 0))
        assertEquals(0x80FFFFFF.toInt(), derived.getColorForState(intArrayOf(android.R.attr.state_pressed), 0))
        assertEquals(0x40FF0000, derived.getColorForState(intArrayOf(android.R.attr.state_activated), 0))
        assertEquals(0x40AAAAAA, derived.getColorForState(intArrayOf(), 0))
        RgbStrip.paint(strip, false)
        assertEquals(on, iconColour(button), "restored")
        button.isActivated = false
        assertEquals(off, iconColour(button))
    }

    @Test fun designProfilesCarryTheLightAndClampImports() {
        val store = KeyboardProfileStore(latinIME)
        val mine = RgbSettings(enabled = true, pattern = RgbPattern.CIRCLE, mix = 40, areas = setOf(RgbArea.SPACE))
        use(mine)
        store.saveCurrent(ProfileGroup.DESIGN)
        use(RgbSettings())
        store.restoreSaved(ProfileGroup.DESIGN)
        assertEquals(mine, RgbLighting.read(prefs))
        // an imported profile with values out of range: clamped field by field
        val exported = store.exportSaved(ProfileGroup.DESIGN)!!
        store.importSaved(exported.replace("mix=40", "mix=900").replace("period=4000", "period=1"))
        store.restoreSaved(ProfileGroup.DESIGN)
        assertEquals(100, RgbLighting.read(prefs).mix)
        assertEquals(RgbSettings.PERIOD_RANGE.first, RgbLighting.read(prefs).periodMs)
        // a design profile from before stage 26 leaves the light as it is
        val legacy = exported.replace(Regex(",?\"${RgbLighting.PREF}\":\"[^\"]*\""), "")
        assertFalse(legacy.contains(RgbLighting.PREF))
        use(mine)
        store.importSaved(legacy)
        store.restoreSaved(ProfileGroup.DESIGN)
        assertEquals(mine, RgbLighting.read(prefs))
        // a number instead of text is rejected
        kotlin.test.assertFails { store.importSaved(exported.replace(Regex("\"${RgbLighting.PREF}\":\"[^\"]*\""), "\"${RgbLighting.PREF}\":5")) }
        // reset turns it off
        store.reset(ProfileGroup.DESIGN)
        assertFalse(RgbLighting.read(prefs).enabled)
    }

    // --- helpers ---

    private lateinit var lastView: MainKeyboardView
    private val colors get() = Settings.getValues().mColors

    private fun Keyboard.key(label: String) = sortedKeys.single { it.label == label }
    private fun Keyboard.key(code: Int) = sortedKeys.single { it.code == code }
    private fun centreX(key: Key) = (key.drawX + lastView.paddingLeft + key.drawWidth / 2f) / WIDTH
    private fun centreY(key: Key) = (key.y + lastView.paddingTop + key.height / 2f) / HEIGHT
    private fun hex(color: Int) = String.format("#%06X", color and 0xFFFFFF)
    private fun hex(picture: Bitmap, x: Int, y: Int) = hex(picture.getPixel(x, y))
    private fun count(picture: Bitmap, key: Key, colour: Int): Int {
        var n = 0
        for (y in key.y until key.y + key.height) for (x in key.x until key.x + key.width)
            if (picture.getPixel(x, y) == (colour or 0xFF000000.toInt())) n++
        return n
    }

    /** The most common opaque colour of [button]'s icon as drawn in its current state. */
    private fun iconColour(button: ImageView): Int {
        val drawable: Drawable = button.drawable
        drawable.state = button.drawableState
        val size = 48
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
        val counts = HashMap<Int, Int>()
        for (y in 0 until size) for (x in 0 until size) {
            val p = bitmap.getPixel(x, y)
            if (p ushr 24 == 0xFF) counts[p] = (counts[p] ?: 0) + 1
        }
        return counts.maxByOrNull { it.value }!!.key
    }

    /** A laid-out strip with the suggestion "Hallo", after the keyboard was loaded (settings read). */
    private fun stripWithWord(): Pair<SuggestionStripView, TextView> {
        val context = ContextThemeWrapper(latinIME, R.style.KeyboardTheme_Flat)
        render("rgb-strip-base.png")
        val container = LayoutInflater.from(context).inflate(R.layout.strip_container, null) as ViewGroup
        val strip = container.findViewById<SuggestionStripView>(R.id.suggestion_strip_view)
        strip.setToolbarVisibility(false)
        fun layout() {
            container.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(130, View.MeasureSpec.EXACTLY))
            container.layout(0, 0, WIDTH, 130)
        }
        layout()
        val info = SuggestedWordInfo("Hallo", "", 1, SuggestedWordInfo.KIND_TYPED, null, -1, -1)
        strip.setSuggestions(SuggestedWords(ArrayList(listOf(info)), null, info, true, false, false, 0, 0), false)
        layout()
        val words = strip.findViewById<ViewGroup>(R.id.suggestions_strip)
        val word = (0 until words.childCount).map { words.getChildAt(it) }.filterIsInstance<TextView>().first { it.text.toString() == "Hallo" }
        return strip to word
    }

    private fun buildKeyboard(context: Context, element: KeyboardElement): Keyboard {
        val layoutParams = KeyboardLayoutSet.Params()
        layoutParams.mode = KeyboardMode.TEXT
        layoutParams.editorInfo = EditorInfo()
        layoutParams.subtype = RichInputMethodSubtype.get(SubtypeUtilsAdditional.createEmojiCapableAdditionalSubtype(Locale.GERMANY, "qwertz", true))
        layoutParams.keyboardWidth = WIDTH
        layoutParams.keyboardHeight = HEIGHT
        layoutParams.settingsValues = Settings.getValues()
        val builder = KeyboardBuilder(context, KeyboardParams(UniqueKeysCache.NO_CACHE))
        builder.load(KeyboardId(element, layoutParams))
        return builder.build()
    }

    private fun render(fileName: String, element: KeyboardElement = KeyboardElement.ALPHABET): Pair<Keyboard, Bitmap> {
        reload()
        val context = ContextThemeWrapper(latinIME, R.style.KeyboardTheme_Flat)
        KeyboardIconsSet.instance.loadIcons(context)
        val layoutParams = KeyboardLayoutSet.Params()
        layoutParams.mode = KeyboardMode.TEXT
        layoutParams.editorInfo = EditorInfo()
        layoutParams.subtype = RichInputMethodSubtype.get(SubtypeUtilsAdditional.createEmojiCapableAdditionalSubtype(Locale.GERMANY, "qwertz", true))
        layoutParams.keyboardWidth = WIDTH
        layoutParams.keyboardHeight = HEIGHT
        layoutParams.settingsValues = Settings.getValues()
        val builder = KeyboardBuilder(context, KeyboardParams(UniqueKeysCache.NO_CACHE))
        builder.load(KeyboardId(element, layoutParams))
        val keyboard = builder.build()
        val view = MainKeyboardView(context, null)
        view.id = R.id.keyboard_view // only the main keyboard is lit
        view.setKeyboard(keyboard)
        view.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, WIDTH, HEIGHT)
        lastView = view
        val picture = draw(view)
        File("build/reports/rgb-lighting").apply { mkdirs() }.resolve(fileName).outputStream().use {
            picture.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return keyboard to picture
    }

    private fun draw(view: View): Bitmap {
        val picture = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(picture)
        canvas.drawColor(colors.get(ColorType.MAIN_BACKGROUND))
        view.draw(canvas)
        return picture
    }

    private companion object {
        const val WIDTH = 1200
        const val HEIGHT = 793
    }
}
