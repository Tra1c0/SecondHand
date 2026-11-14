// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.provider.Settings.Global
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import dev.agentkeyboard.core.RgbArea
import dev.agentkeyboard.core.RgbBlend
import dev.agentkeyboard.core.RgbGenerator
import dev.agentkeyboard.core.RgbLayer
import dev.agentkeyboard.core.RgbLayerReaction
import dev.agentkeyboard.core.RgbOwnPattern
import dev.agentkeyboard.core.RgbPattern
import dev.agentkeyboard.core.RgbPatternJson
import dev.agentkeyboard.core.RgbPatternLayer
import dev.agentkeyboard.core.RgbPatternRead
import dev.agentkeyboard.core.RgbSettings
import dev.agentkeyboard.core.RgbStop
import dev.agentkeyboard.core.RgbTarget
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.screens.AgentDiagnoseScreen
import helium314.keyboard.settings.screens.AgentRgbScreen
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowToast
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stage 34: the editor of own light patterns, their choice per element, files, the keyboard pipeline and Diagnose. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "de-w411dp-h1000dp")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RgbPatternEditorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app: Application = RuntimeEnvironment.getApplication()
    private val prefs get() = app.prefs()
    private val saved get() = RgbLighting.read(prefs)
    private val library get() = RgbOwnPatterns.library(prefs)

    @Before @After fun clear() {
        prefs.edit().clear().commit()
        Global.putFloat(app.contentResolver, Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    // file pickers answered by the test: what was asked for, and the document returned
    private val asked = mutableListOf<Any?>()
    private var answer: Uri? = null
    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            asked += input
            dispatchResult(requestCode, answer)
        }
    }
    private val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }

    private fun page(still: Boolean = true) {
        // without animations the previews have no frame loop, so the test clock can idle
        if (still) Global.putFloat(app.contentResolver, Global.ANIMATOR_DURATION_SCALE, 0f)
        compose.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) { Theme(true) { AgentRgbScreen {} } } }
        compose.waitForIdle()
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 20_000
        while (!condition()) {
            assertTrue(System.currentTimeMillis() < end, "timed out waiting for $what")
            Thread.sleep(20)
            compose.mainClock.advanceTimeBy(50)
            compose.waitForIdle()
        }
    }

    private val sparkle = RgbOwnPattern("p1", "Funken", listOf(
        RgbPatternLayer(RgbGenerator.PLASMA, listOf(RgbStop(0f, 0xFF2040FF.toInt()), RgbStop(1f, 0xFFFF2080.toInt())), speed = 0.5f),
        RgbPatternLayer(RgbGenerator.SPOTLIGHT, listOf(RgbStop(0f, 0xFFFFFFFF.toInt()), RgbStop(1f, 0xFFFFFFFF.toInt())), blend = RgbBlend.ADD,
            reaction = RgbLayerReaction.NONE),
    )).normalized()

    // --- editor: create, duplicate, rename, delete ---

    @Test fun anOwnPatternIsMadeFromTheChosenOneEditedRenamedDuplicatedAndDeleted() {
        RgbLighting.write(prefs, RgbSettings(enabled = true, pattern = RgbPattern.CIRCLE))
        page()
        compose.onNode(hasText("Noch keine eigenen Muster", substring = true)).performScrollTo().assertExists()
        click("agent_rgb_own_new")
        // the copy of the circle is stored, chosen for the keys and open in the editor
        val made = library.patterns.single()
        assertEquals("p1", made.id)
        assertEquals("Kreis von der Mitte (eigen)", made.name)
        assertEquals(RgbGenerator.RADIAL, made.layers.single().generator)
        assertEquals("p1", saved.own?.id)
        assertEquals(made, saved.own, "the setting resolves to the stored pattern")
        assertTrue(";own=p1" in prefs.getString(RgbLighting.PREF, null)!!, "the setting names only the id")
        compose.onNodeWithTag("agent_rgb_pattern_editor").assertExists()
        compose.onNodeWithTag("agent_rgb_preview").assertExists()
        // every option of the layer is saved at once
        click("agent_rgb_pattern_gen_CONIC")
        assertEquals(RgbGenerator.CONIC, library["p1"]!!.layers[0].generator)
        click("agent_rgb_pattern_blend_SCREEN")
        assertEquals(RgbBlend.SCREEN, library["p1"]!!.layers[0].blend)
        click("agent_rgb_pattern_reaction_KEY_GLOW")
        assertEquals(RgbLayerReaction.KEY_GLOW, library["p1"]!!.layers[0].reaction)
        click("agent_rgb_pattern_palette_HUE_CYCLE")
        click("agent_rgb_pattern_wave_SINE")
        assertEquals(dev.agentkeyboard.core.RgbCurve.preset(dev.agentkeyboard.core.RgbWaveform.SINE), library["p1"]!!.layers[0].curve)
        val stops = library["p1"]!!.layers[0].stops.size
        click("agent_rgb_pattern_stop_add")
        assertEquals(stops + 1, library["p1"]!!.layers[0].stops.size)
        click("agent_rgb_pattern_stop_remove")
        assertEquals(stops, library["p1"]!!.layers[0].stops.size)
        // a slider: saved when it is let go
        compose.onNodeWithTag("agent_rgb_pattern_opacity").performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(55f) }
        compose.waitForIdle()
        assertEquals(55, library["p1"]!!.layers[0].opacity, "opacity saved when let go")
        // layers: add (on top, chosen), move down, remove
        click("agent_rgb_pattern_layer_add")
        assertEquals(2, library["p1"]!!.layers.size)
        compose.onNodeWithTag("agent_rgb_pattern_layer_1").assertIsSelected()
        click("agent_rgb_pattern_gen_STRIPES")
        click("agent_rgb_pattern_layer_down")
        assertEquals(listOf(RgbGenerator.STRIPES, RgbGenerator.CONIC), library["p1"]!!.layers.map { it.generator })
        click("agent_rgb_pattern_layer_remove")
        assertEquals(listOf(RgbGenerator.CONIC), library["p1"]!!.layers.map { it.generator })
        // rename: a valid name is saved, an address is refused with a hint
        compose.onNodeWithTag("agent_rgb_pattern_name").performScrollTo().performTextReplacement("Polarlicht")
        compose.waitForIdle()
        assertEquals("Polarlicht", library["p1"]!!.name)
        compose.onNodeWithTag("agent_rgb_pattern_name").performTextReplacement("https://x.de")
        compose.waitForIdle()
        assertEquals("Polarlicht", library["p1"]!!.name)
        compose.onNode(hasText("keine Zeilenumbrüche", substring = true)).assertExists()
        // duplicate: a second pattern, opened and chosen
        click("agent_rgb_pattern_duplicate")
        assertEquals(listOf("p1", "p2"), library.patterns.map { it.id })
        assertEquals("Polarlicht (eigen)", library["p2"]!!.name)
        assertEquals(library["p1"]!!.layers, library["p2"]!!.layers)
        assertEquals("p2", saved.own?.id)
        // delete with confirmation: the element falls back to its built-in pattern
        click("agent_rgb_pattern_delete")
        compose.onNodeWithTag("agent_rgb_pattern_delete_confirm").performClick()
        compose.waitForIdle()
        assertEquals(listOf("p1"), library.patterns.map { it.id })
        assertNull(saved.own, "deleted: no own pattern any more")
        assertEquals(RgbPattern.CIRCLE, saved.pattern)
        assertFalse("own=" in prefs.getString(RgbLighting.PREF, null)!!)
        compose.onNodeWithTag("agent_rgb_options").assertExists()
        // the remaining one is in the gallery and can be opened again
        compose.onNodeWithTag("agent_rgb_own_p1").performScrollTo().assertIsNotSelected()
        click("agent_rgb_own_p1")
        compose.onNodeWithTag("agent_rgb_own_p1").assertIsSelected()
        compose.onNodeWithTag("agent_rgb_pattern_CIRCLE").assertIsNotSelected()
        click("agent_rgb_own_edit")
        compose.onNodeWithTag("agent_rgb_pattern_editor").assertExists()
    }

    @Test fun eachElementChoosesItsOwnPatternLikeABuiltInOne() {
        RgbOwnPatterns.write(prefs, dev.agentkeyboard.core.RgbPatternLibrary(listOf(sparkle)))
        RgbLighting.write(prefs, RgbSettings(enabled = true, layers = setOf(RgbLayer.KEY_BACKGROUND, RgbLayer.KEYBOARD_BACKGROUND)))
        page()
        click("agent_rgb_same_everywhere")
        click("agent_rgb_target_BACKGROUND")
        click("agent_rgb_own_p1")
        assertEquals(sparkle, saved.lookFor(RgbTarget.BACKGROUND).own)
        assertNull(saved.lookFor(RgbTarget.KEYS).own, "the keys keep their pattern")
        // an own look hides the curve and the palette of the built-in patterns, speed and strength stay
        compose.onNodeWithTag("agent_rgb_curve").assertDoesNotExist()
        compose.onNodeWithTag("agent_rgb_palette_own").assertDoesNotExist()
        compose.onNodeWithTag("agent_rgb_period").assertExists()
        compose.onNodeWithTag("agent_rgb_mix").assertExists()
        // a built-in pattern again
        click("agent_rgb_pattern_INK")
        assertNull(saved.lookFor(RgbTarget.BACKGROUND).own)
        assertEquals(RgbPattern.INK, saved.lookFor(RgbTarget.BACKGROUND).pattern)
        compose.onNodeWithTag("agent_rgb_own_p1").assertIsNotSelected()
        // the keyboard reads the same
        assertEquals(saved, RgbSettings.parse(prefs.getString(RgbLighting.PREF, null)).resolve(library))
    }

    // --- preview ---

    @Test fun theEditorPreviewRendersAndMovesAtTheRealFrameRate() {
        val fast = sparkle.copy(layers = sparkle.layers.map { it.copy(speed = 2f) })
        RgbOwnPatterns.write(prefs, dev.agentkeyboard.core.RgbPatternLibrary(listOf(fast)))
        RgbLighting.write(prefs, RgbSettings(enabled = true, own = fast, fps = 60, periodMs = 2000,
            layers = setOf(RgbLayer.KEY_BACKGROUND, RgbLayer.KEYBOARD_BACKGROUND), areas = RgbArea.entries.toSet()))
        val settings = RgbLighting.read(prefs)
        // the editor itself with a running preview (the page's own preview would run as well)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Theme(true) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val design = androidx.compose.runtime.remember { MyDesigns.newDesign(context, "") }
                helium314.keyboard.settings.screens.RgbPatternEditor(fast, { helium314.keyboard.settings.screens.previewWith(settings, RgbTarget.KEYS, it) },
                    design, animate = true, onChange = {}, onRename = { true }, onDuplicate = {}, onExport = {}, onDelete = {}, onBack = {})
            }
        }
        compose.mainClock.advanceTimeBy(200)
        compose.onNode(hasText("Live-Vorschau mit 60 Hz", substring = true)).assertExists()
        RgbWork.awaitIdle()
        compose.mainClock.advanceTimeBy(100)
        val first = previewPixel()
        compose.mainClock.advanceTimeBy(700)
        RgbWork.awaitIdle()
        compose.mainClock.advanceTimeBy(50)
        val shot = screenshot()
        assertNotEquals(first, previewPixel(shot), "the pattern moves in the preview")
        File("build/agent-previews").mkdirs()
        File("build/agent-previews/rgb-pattern-editor.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // the light in the preview is the own pattern's: many colours, not the theme grey
        val bounds = compose.onNodeWithTag("agent_rgb_preview").fetchSemanticsNode().boundsInRoot
        val colours = HashSet<Int>()
        for (i in 0 until 40) colours += shot.getPixel((bounds.left + bounds.width * (i + 0.5f) / 40).roundToInt(), (bounds.top + bounds.height * 0.55f).roundToInt())
        assertTrue(colours.size > 10, "a lit preview (${colours.size} colours)")
    }

    // a point of the second key row in the preview
    private fun previewPixel(shot: Bitmap = screenshot()): Int {
        val bounds = compose.onNodeWithTag("agent_rgb_preview").fetchSemanticsNode().boundsInRoot
        return shot.getPixel((bounds.left + bounds.width * 0.3f).roundToInt(), (bounds.top + bounds.height * 0.55f).roundToInt())
    }

    private fun screenshot(): Bitmap {
        lateinit var bitmap: Bitmap
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        return bitmap
    }

    // --- files ---

    @Test fun aPatternIsExportedAndImportedAsASmallJsonFile() {
        RgbOwnPatterns.write(prefs, dev.agentkeyboard.core.RgbPatternLibrary(listOf(sparkle)))
        RgbLighting.write(prefs, RgbSettings(enabled = true, own = sparkle))
        page()
        // export: the file name from the pattern, the content only shapes, colours and numbers
        val out = Uri.parse("content://agent.test.documents/funken.json")
        val written = ByteArrayOutputStream()
        shadowOf(app.contentResolver).registerOutputStreamSupplier(out) { written }
        answer = out
        click("agent_rgb_own_edit")
        click("agent_rgb_pattern_export")
        waitUntil("the file") { written.size() > 0 }
        assertEquals("AgentKeyboard-Lichtmuster-Funken.json", asked.last())
        val text = written.toString("UTF-8")
        assertEquals(sparkle.copy(id = ""), (RgbPatternJson.decode(text) as RgbPatternRead.Ok).pattern)
        assertFalse("\"id\"" in text)
        waitUntil("the message") { ShadowToast.getTextOfLatestToast() == "„Funken“ exportiert" }
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
        compose.onNodeWithTag("agent_rgb_options").assertExists()

        // import: added with a new id and a unique name
        val good = Uri.parse("content://agent.test.documents/good.json")
        shadowOf(app.contentResolver).registerInputStream(good, ByteArrayInputStream(text.toByteArray()))
        answer = good
        click("agent_rgb_own_import")
        waitUntil("the import") { library.patterns.size == 2 }
        assertEquals(listOf("application/json", "text/*", "application/octet-stream"), (asked.last() as Array<*>).toList())
        val imported = library["p2"]!!
        assertEquals("Funken 2", imported.name)
        assertEquals(sparkle.layers, imported.layers)
        waitUntil("the message") { ShadowToast.getTextOfLatestToast() == "„Funken 2“ importiert" }
        compose.onNodeWithTag("agent_rgb_own_p2").performScrollTo().assertExists()

        // a file with anything else is refused as a whole, with the reason
        val evil = Uri.parse("content://agent.test.documents/evil.json")
        shadowOf(app.contentResolver).registerInputStream(evil, ByteArrayInputStream(
            text.replace("\"name\"", "\"tool\":\"https://evil.example\",\"name\"").toByteArray()))
        answer = evil
        click("agent_rgb_own_import")
        waitUntil("the refusal") { ShadowToast.getTextOfLatestToast() == "Datei nicht übernommen: enthält unbekannte Felder" }
        assertEquals(2, library.patterns.size)
        val big = Uri.parse("content://agent.test.documents/big.json")
        shadowOf(app.contentResolver).registerInputStream(big, ByteArrayInputStream(ByteArray(RgbPatternJson.MAX_FILE_BYTES + 1) { ' '.code.toByte() }))
        answer = big
        click("agent_rgb_own_import")
        waitUntil("the refusal") { ShadowToast.getTextOfLatestToast() == "Datei nicht übernommen: zu groß (höchstens 16 KB)" }
        assertEquals(2, library.patterns.size)
        // nothing picked: nothing happens
        answer = null
        click("agent_rgb_own_import")
        assertEquals(2, library.patterns.size)
    }

    // --- the keyboard's pipeline ---

    @Test fun theKeyboardDrawsOwnPatternsThroughTheGridWithoutATile() {
        RgbOwnPatterns.write(prefs, dev.agentkeyboard.core.RgbPatternLibrary(listOf(sparkle)))
        RgbLighting.write(prefs, RgbSettings(enabled = true, pattern = RgbPattern.LED, own = sparkle))
        val settings = RgbLighting.read(prefs)
        assertEquals(sparkle, settings.own)
        val engine = dev.agentkeyboard.core.RgbEngine(settings).apply { aspect = 0.5f; advance(0); advance(500) }
        assertNull(engine.tilePattern, "own patterns have no detail tile")
        val texture = RgbTexture()
        texture.update(engine, 0f, 0f, 480f, 240f, 500, -1, wait = true)
        assertFalse(texture.hasTile)
        for ((c, r) in listOf(0 to 0, 10 to 5, 30 to 12, 47 to 23)) {
            val x = (c + 0.5f) / RgbTexture.COLUMNS
            val y = (r + 0.5f) / texture.gridRows
            assertEquals(engine.light(x, y, -1, 500), texture.gridPixel(c, r), "grid cell $c,$r is the engine's light")
        }
        // a changed pattern in the library makes the keyboard read it again
        RgbLighting.load(app)
        assertEquals(sparkle, RgbLighting.settings.own)
        val changed = sparkle.copy(layers = sparkle.layers.take(1))
        RgbOwnPatterns.save(prefs, changed)
        RgbLighting.load(app)
        assertEquals(changed, RgbLighting.settings.own)
        // a spotlight follows taps: taps count although no tap reaction is set
        assertTrue(RgbLighting.settings.reactsToTaps().not(), "plasma alone ignores taps")
        RgbOwnPatterns.save(prefs, sparkle)
        RgbLighting.load(app)
        assertTrue(RgbLighting.settings.reactsToTaps(), "the spotlight layer follows the keys")
        // the built-in fallback when the library loses it
        RgbOwnPatterns.delete(prefs, "p1")
        RgbLighting.load(app)
        assertNull(RgbLighting.settings.own)
        assertEquals(RgbPattern.LED, RgbLighting.settings.pattern)
    }

    // --- Diagnose ---

    @Test fun diagnoseListsAndMeasuresOwnPatterns() {
        RgbOwnPatterns.write(prefs, dev.agentkeyboard.core.RgbPatternLibrary(listOf(sparkle)))
        RgbLighting.write(prefs, RgbSettings(enabled = true, own = sparkle, layers = setOf(RgbLayer.KEY_BACKGROUND, RgbLayer.KEYBOARD_BACKGROUND)).withSeparate(true)
            .let { it.withLook(RgbTarget.BACKGROUND, it.lookFor(RgbTarget.BACKGROUND).copy(own = null, pattern = RgbPattern.INK)) })
        val rows = Diagnose.lightPatterns(app, RgbLighting.read(prefs))
        assertEquals("Funken (eigen), Tinte", rows.first().value)
        val grids = rows.drop(1)
        assertEquals(listOf("Raster 48 × 32 „Funken (eigen)“ (jetzt gemessen)", "Raster 48 × 32 „Tinte“ (jetzt gemessen)"), grids.map { it.label })
        for (g in grids) assertTrue(Regex("\\d+[.,]\\d\\d ms").matches(g.value), g.value)
        assertEquals(emptyList(), Diagnose.lightPatterns(app, RgbSettings()), "light off: nothing")
        // on the page, also without measured frames
        Global.putFloat(app.contentResolver, Global.ANIMATOR_DURATION_SCALE, 0f)
        compose.setContent { Theme(true) { AgentDiagnoseScreen {} } }
        waitUntil("the patterns") { compose.onAllNodes(hasText("Funken (eigen), Tinte")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Benutzte Muster")).assertExists()
        compose.onNode(hasText("Gemessene Bilder")).assertExists()
    }

    @Test fun nameAndFileNameAreSafe() {
        assertEquals("AgentKeyboard-Lichtmuster-Polar_licht_.json", helium314.keyboard.settings.screens.fileName("Polar/licht!"))
        assertEquals("AgentKeyboard-Lichtmuster-Muster.json", helium314.keyboard.settings.screens.fileName("   "))
        assertNotNull(RgbOwnPatterns.add(prefs, sparkle))
        assertFalse(RgbOwnPatterns.rename(prefs, "p1", "a\nb"))
        assertTrue(RgbOwnPatterns.rename(prefs, "p1", "Neu"))
        assertEquals("Neu", library["p1"]!!.name)
    }
}
