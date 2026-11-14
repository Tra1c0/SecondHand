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

    // a twelve-character id like the ones made now (a short one like "p1" would get a new id when the page opens)
    private val sparkle = RgbOwnPattern("funkenmuster", "Funken", listOf(
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
        val id = made.id
        assertTrue(Regex("[a-z0-9]{12}").matches(id), "a random id: $id")
        assertEquals("Kreis von der Mitte (eigen)", made.name)
        assertEquals(RgbGenerator.RADIAL, made.layers.single().generator)
        assertEquals(id, saved.own?.id)
        assertEquals(made, saved.own, "the setting resolves to the stored pattern")
        assertTrue(";own=$id.${made.fingerprint()}" in prefs.getString(RgbLighting.PREF, null)!!, "the setting names only the id and the check")
        compose.onNodeWithTag("agent_rgb_pattern_editor").assertExists()
        compose.onNodeWithTag("agent_rgb_preview").assertExists()
        // every option of the layer is saved at once; the setting follows each change (its check)
        click("agent_rgb_pattern_gen_CONIC")
        assertEquals(RgbGenerator.CONIC, library[id]!!.layers[0].generator)
        assertEquals(library[id], saved.own, "the setting follows the edit")
        // the lowest layer blends with nothing: no blend mode to choose (review N11)
        compose.onNodeWithTag("agent_rgb_pattern_blend_SCREEN").assertDoesNotExist()
        click("agent_rgb_pattern_reaction_KEY_GLOW")
        assertEquals(RgbLayerReaction.KEY_GLOW, library[id]!!.layers[0].reaction)
        click("agent_rgb_pattern_palette_HUE_CYCLE")
        click("agent_rgb_pattern_wave_SINE")
        assertEquals(dev.agentkeyboard.core.RgbCurve.preset(dev.agentkeyboard.core.RgbWaveform.SINE), library[id]!!.layers[0].curve)
        val stops = library[id]!!.layers[0].stops.size
        click("agent_rgb_pattern_stop_add")
        assertEquals(stops + 1, library[id]!!.layers[0].stops.size)
        click("agent_rgb_pattern_stop_remove")
        assertEquals(stops, library[id]!!.layers[0].stops.size)
        // colour stops: 48 dp to touch, named with their colour for TalkBack (review M4)
        val first = library[id]!!.layers[0].stops[0]
        val stopNode = compose.onNodeWithTag("agent_rgb_pattern_stop_0").performScrollTo()
        stopNode.assertWidthIsAtLeast(androidx.compose.ui.unit.Dp(48f)).assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(48f))
        stopNode.assert(hasContentDescription("Farbstopp 1, #" + dev.agentkeyboard.core.RgbSettings.hex(first.color)))
        // the chosen stop moved past the next one: still chosen at its new place (review N1)
        compose.onNodeWithTag("agent_rgb_pattern_stop_0").assertIsSelected()
        val firstColor = library[id]!!.layers[0].stops[0].color
        compose.onNodeWithTag("agent_rgb_pattern_stop_position").performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0.9f) }
        compose.waitForIdle()
        val sorted = library[id]!!.layers[0].stops
        val movedTo = sorted.indexOfFirst { it.color == firstColor && it.position == 0.9f }
        assertTrue(movedTo > 0, "sorted to a later place: $sorted")
        compose.onNodeWithTag("agent_rgb_pattern_stop_$movedTo").assertIsSelected()
        compose.onNodeWithTag("agent_rgb_pattern_stop_0").assertIsNotSelected()
        // a slider: saved when it is let go, named by its label for TalkBack
        compose.onNodeWithTag("agent_rgb_pattern_opacity").performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(55f) }
        compose.waitForIdle()
        assertEquals(55, library[id]!!.layers[0].opacity, "opacity saved when let go")
        compose.onNodeWithTag("agent_rgb_pattern_opacity").assert(hasContentDescription("Deckkraft: 55 %"))
        // fine steps: speed 0.05, offset 0.01 (review N11)
        compose.onNodeWithTag("agent_rgb_pattern_speed").performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0.35f) }
        compose.waitForIdle()
        assertEquals(0.35f, library[id]!!.layers[0].speed)
        compose.onNodeWithTag("agent_rgb_pattern_offset_x").performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0.13f) }
        compose.waitForIdle()
        assertEquals(0.13f, library[id]!!.layers[0].offsetX)
        // layers: add (on top, chosen; it blends with the one below), move down, remove
        click("agent_rgb_pattern_layer_add")
        assertEquals(2, library[id]!!.layers.size)
        compose.onNodeWithTag("agent_rgb_pattern_layer_1").assertIsSelected()
        click("agent_rgb_pattern_blend_MULTIPLY")
        assertEquals(RgbBlend.MULTIPLY, library[id]!!.layers[1].blend)
        click("agent_rgb_pattern_gen_STRIPES")
        click("agent_rgb_pattern_layer_down")
        assertEquals(listOf(RgbGenerator.STRIPES, RgbGenerator.CONIC), library[id]!!.layers.map { it.generator })
        click("agent_rgb_pattern_layer_remove")
        assertEquals(listOf(RgbGenerator.CONIC), library[id]!!.layers.map { it.generator })
        // rename: kept in the field while typed, saved on "Done" (review M2), an address is refused with a hint
        val keyboardText = prefs.getString(RgbOwnPatterns.PREF, null)
        compose.onNodeWithTag("agent_rgb_pattern_name").performScrollTo().assert(hasText("Name des Musters"))
        compose.onNodeWithTag("agent_rgb_pattern_name").performTextReplacement("Polarlicht")
        compose.waitForIdle()
        assertEquals("Kreis von der Mitte (eigen)", library[id]!!.name, "not saved per keystroke")
        assertEquals(keyboardText, prefs.getString(RgbOwnPatterns.PREF, null), "the stored patterns (and the keyboard) unchanged while typing")
        compose.onNodeWithTag("agent_rgb_pattern_name").performImeAction()
        compose.waitForIdle()
        assertEquals("Polarlicht", library[id]!!.name)
        compose.onNodeWithTag("agent_rgb_pattern_name").performTextReplacement("https://x.de")
        compose.waitForIdle()
        compose.onNode(hasText("keine Zeilenumbrüche", substring = true)).assertExists()
        compose.onNodeWithTag("agent_rgb_pattern_name").performImeAction()
        compose.waitForIdle()
        assertEquals("Polarlicht", library[id]!!.name)
        compose.onNodeWithTag("agent_rgb_pattern_name").assert(hasText("Polarlicht"))
        // duplicate: a second pattern, opened and chosen; a name typed but not finished is saved first
        compose.onNodeWithTag("agent_rgb_pattern_name").performTextReplacement("Nordlicht")
        click("agent_rgb_pattern_duplicate")
        assertEquals("Nordlicht", library[id]!!.name)
        val copy = library.patterns.single { it.id != id }
        assertEquals(listOf(id, copy.id), library.patterns.map { it.id })
        assertEquals("Nordlicht (Kopie)", copy.name)
        assertEquals(library[id]!!.layers, copy.layers)
        assertEquals(copy.id, saved.own?.id)
        // the same name again: the stored, unique name is shown
        compose.onNodeWithTag("agent_rgb_pattern_name").performTextReplacement("Nordlicht")
        compose.onNodeWithTag("agent_rgb_pattern_name").performImeAction()
        compose.waitForIdle()
        assertEquals("Nordlicht 2", library[copy.id]!!.name)
        compose.onNodeWithTag("agent_rgb_pattern_name").assert(hasText("Nordlicht 2"))
        // delete with confirmation: the element falls back to its built-in pattern
        click("agent_rgb_pattern_delete")
        compose.onNodeWithTag("agent_rgb_pattern_delete_confirm").performClick()
        compose.waitForIdle()
        assertEquals(listOf(id), library.patterns.map { it.id })
        assertNull(saved.own, "deleted: no own pattern any more")
        assertEquals(RgbPattern.CIRCLE, saved.pattern)
        assertFalse("own=" in prefs.getString(RgbLighting.PREF, null)!!)
        compose.onNodeWithTag("agent_rgb_options").assertExists()
        // the remaining one is in the gallery and can be opened again
        compose.onNodeWithTag("agent_rgb_own_$id").performScrollTo().assertIsNotSelected()
        click("agent_rgb_own_$id")
        compose.onNodeWithTag("agent_rgb_own_$id").assertIsSelected()
        compose.onNodeWithTag("agent_rgb_pattern_CIRCLE").assertIsNotSelected()
        click("agent_rgb_own_edit")
        compose.onNodeWithTag("agent_rgb_pattern_editor").assertExists()
    }

    /** Review M3: the own gallery draws a picture per content, not per name, and waits while the colours change. */
    @Test fun theOwnGalleryDrawsOnlyWhatChangedAndWaitsForTheColoursToRest() {
        val look = androidx.compose.runtime.mutableStateOf(dev.agentkeyboard.core.RgbLook(own = sparkle))
        val lib = androidx.compose.runtime.mutableStateOf(dev.agentkeyboard.core.RgbPatternLibrary(listOf(sparkle)))
        compose.mainClock.autoAdvance = false
        val start = helium314.keyboard.settings.screens.ownPictures.get()
        compose.setContent { Theme(true) { helium314.keyboard.settings.screens.OwnGallery(lib.value, look.value) {} } }
        waitUntil("the first picture") { helium314.keyboard.settings.screens.ownPictures.get() == start + 1 }
        // renamed: the same picture
        lib.value = dev.agentkeyboard.core.RgbPatternLibrary(listOf(sparkle.copy(name = "Anders")))
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        assertEquals(start + 1, helium314.keyboard.settings.screens.ownPictures.get(), "a rename draws nothing")
        // a slider dragged over the saturation: no picture per step, one once it rests
        for (sat in 90 downTo 40 step 10) {
            look.value = look.value.copy(saturation = sat)
            compose.mainClock.advanceTimeBy(50)
            compose.waitForIdle()
        }
        assertEquals(start + 1, helium314.keyboard.settings.screens.ownPictures.get(), "none while the colours change")
        waitUntil("the picture after the rest") { helium314.keyboard.settings.screens.ownPictures.get() == start + 2 }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        assertEquals(start + 2, helium314.keyboard.settings.screens.ownPictures.get(), "only the last colours")
    }

    @Test fun eachElementChoosesItsOwnPatternLikeABuiltInOne() {
        RgbOwnPatterns.write(app, dev.agentkeyboard.core.RgbPatternLibrary(listOf(sparkle)))
        RgbLighting.write(prefs, RgbSettings(enabled = true, layers = setOf(RgbLayer.KEY_BACKGROUND, RgbLayer.KEYBOARD_BACKGROUND)))
        page()
        click("agent_rgb_same_everywhere")
        click("agent_rgb_target_BACKGROUND")
        click("agent_rgb_own_funkenmuster")
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
        compose.onNodeWithTag("agent_rgb_own_funkenmuster").assertIsNotSelected()
        // the keyboard reads the same
        assertEquals(saved, RgbSettings.parse(prefs.getString(RgbLighting.PREF, null)).resolve(library))
    }

    // --- preview ---

    @Test fun theEditorPreviewRendersAndMovesAtTheRealFrameRate() {
        val fast = sparkle.copy(layers = sparkle.layers.map { it.copy(speed = 2f) })
        RgbOwnPatterns.write(app, dev.agentkeyboard.core.RgbPatternLibrary(listOf(fast)))
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
                    design, animate = true, onChange = {}, onRename = { it }, onDuplicate = {}, onExport = {}, onDelete = {}, onBack = {})
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
        RgbOwnPatterns.write(app, dev.agentkeyboard.core.RgbPatternLibrary(listOf(sparkle)))
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
        val imported = library.patterns.single { it.id != sparkle.id }
        assertTrue(Regex("[a-z0-9]{12}").matches(imported.id), imported.id)
        assertEquals("Funken 2", imported.name)
        assertEquals(sparkle.layers, imported.layers)
        waitUntil("the message") { ShadowToast.getTextOfLatestToast() == "„Funken 2“ importiert" }
        compose.onNodeWithTag("agent_rgb_own_${imported.id}").performScrollTo().assertExists()

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
        // review N5: a document the app may not read, or one that breaks off, is not "too large"
        val denied = Uri.parse("content://agent.test.documents/denied.json")
        shadowOf(app.contentResolver).registerInputStreamSupplier(denied) { throw SecurityException("no grant") }
        answer = denied
        click("agent_rgb_own_import")
        waitUntil("the refusal") {
            ShadowToast.getTextOfLatestToast() == "Datei nicht übernommen: kein Zugriff auf die Datei (verschoben, gelöscht oder nicht erlaubt)"
        }
        val broken = Uri.parse("content://agent.test.documents/broken.json")
        shadowOf(app.contentResolver).registerInputStreamSupplier(broken) {
            object : java.io.InputStream() { override fun read(): Int = throw java.io.IOException("gone") }
        }
        answer = broken
        click("agent_rgb_own_import")
        waitUntil("the refusal") { ShadowToast.getTextOfLatestToast() == "Datei nicht übernommen: Datei nicht lesbar" }
        assertEquals(2, library.patterns.size)
        // nothing picked: nothing happens
        answer = null
        click("agent_rgb_own_import")
        assertEquals(2, library.patterns.size)
    }

    // --- the keyboard's pipeline ---

    @Test fun theKeyboardDrawsOwnPatternsThroughTheGridWithoutATile() {
        RgbOwnPatterns.write(app, dev.agentkeyboard.core.RgbPatternLibrary(listOf(sparkle)))
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
        RgbOwnPatterns.save(app, changed)
        RgbLighting.load(app)
        assertEquals(changed, RgbLighting.settings.own, "the setting followed the edit (new content check)")
        // a spotlight follows taps: taps count although no tap reaction is set
        assertTrue(RgbLighting.settings.reactsToTaps().not(), "plasma alone ignores taps")
        RgbOwnPatterns.save(app, sparkle)
        RgbLighting.load(app)
        assertTrue(RgbLighting.settings.reactsToTaps(), "the spotlight layer follows the keys")
        // the built-in fallback when the library loses it
        RgbOwnPatterns.delete(app, sparkle.id)
        RgbLighting.load(app)
        assertNull(RgbLighting.settings.own)
        assertEquals(RgbPattern.LED, RgbLighting.settings.pattern)
    }

    // --- review of stage 34 ---

    /** M1: the ids of the first build become random ones; the lighting and the saved design profile follow, also an edit. */
    @Test fun legacyIdsAreMigratedAndTheSettingAndProfileFollow() {
        val legacy = sparkle.copy(id = "p1")
        prefs.edit().putString(RgbOwnPatterns.PREF, RgbPatternJson.encodeLibrary(dev.agentkeyboard.core.RgbPatternLibrary(listOf(legacy)))).commit()
        // the setting of the first build: a bare id
        prefs.edit().putString(RgbLighting.PREF, RgbSettings(enabled = true, pattern = RgbPattern.LED, own = RgbOwnPattern.reference("p1")).encode()).commit()
        assertTrue(prefs.getString(RgbLighting.PREF, null)!!.contains(";own=p1;") || prefs.getString(RgbLighting.PREF, null)!!.endsWith(";own=p1"))
        assertEquals(legacy, saved.own, "before the change the bare id still finds its pattern")
        val profiles = KeyboardProfileStore(app)
        app.getSharedPreferences(KeyboardProfileStore.STORAGE_NAME, android.content.Context.MODE_PRIVATE).edit().clear().commit()
        profiles.saveCurrent(dev.agentkeyboard.core.ProfileGroup.DESIGN)
        RgbLighting.load(app)
        val migrated = library.patterns.single()
        assertTrue(Regex("[a-z0-9]{12}").matches(migrated.id), migrated.id)
        assertEquals(legacy.layers, migrated.layers)
        assertEquals(migrated, RgbLighting.settings.own, "the keyboard draws the same pattern under its new id")
        assertEquals(migrated, saved.own)
        fun profileText() = (profiles.read(dev.agentkeyboard.core.ProfileGroup.DESIGN)!!.values[RgbLighting.PREF]
            as dev.agentkeyboard.core.PreferenceValue.Choice).value
        assertTrue(";own=${migrated.id}.${migrated.fingerprint()}" in profileText(), profileText())
        assertTrue(profiles.matchesCurrent(dev.agentkeyboard.core.ProfileGroup.DESIGN))
        // an edit: setting and profile follow with the new check
        val edited = migrated.copy(layers = migrated.layers.take(1))
        RgbOwnPatterns.save(app, edited)
        assertEquals(edited, saved.own)
        assertTrue(";own=${edited.id}.${edited.fingerprint()}" in profileText(), profileText())
        profiles.restoreSaved(dev.agentkeyboard.core.ProfileGroup.DESIGN)
        assertEquals(edited, saved.own)
        // a profile from another phone (its own id and check): the built-in pattern, never a local one
        val foreign = RgbSettings(enabled = true, pattern = RgbPattern.INK, own = sparkle.copy(id = "zz9zz9zz9zz9")).encode()
        prefs.edit().putString(RgbLighting.PREF, foreign).commit()
        assertNull(saved.own)
        assertEquals(RgbPattern.INK, saved.pattern)
        // the old bare id from a profile of the first build finds nothing any more
        prefs.edit().putString(RgbLighting.PREF, "v=2;on=1;own=p1").commit()
        assertNull(saved.own)
    }

    /** N6: a stored list this version cannot read is kept as it is, nothing is written over it, the page says so. */
    @Test fun anUnreadableStoredListIsKeptAndNotOverwritten() {
        val newer = RgbPatternJson.encodeLibrary(dev.agentkeyboard.core.RgbPatternLibrary(listOf(sparkle))).replace("\"version\":1", "\"version\":2")
        prefs.edit().putString(RgbOwnPatterns.PREF, newer).commit()
        assertTrue(RgbOwnPatterns.locked(prefs))
        assertTrue(library.patterns.isEmpty())
        assertFalse(RgbOwnPatterns.write(app, dev.agentkeyboard.core.RgbPatternLibrary(listOf(sparkle))))
        assertNull(RgbOwnPatterns.add(app, sparkle))
        RgbOwnPatterns.migrate(app)
        assertEquals(newer, prefs.getString(RgbOwnPatterns.PREF, null), "kept as it was")
        page()
        compose.onNodeWithTag("agent_rgb_own_locked").performScrollTo().assertExists()
        compose.onNode(hasText("neueren Version", substring = true)).assertExists()
        compose.onNodeWithTag("agent_rgb_own_new").assertDoesNotExist()
        compose.onNodeWithTag("agent_rgb_own_import").assertDoesNotExist()
        assertEquals(newer, prefs.getString(RgbOwnPatterns.PREF, null))
        val big = "{" + " ".repeat(RgbPatternJson.MAX_LIBRARY_BYTES) + "}"
        prefs.edit().putString(RgbOwnPatterns.PREF, big).commit()
        assertFalse(RgbOwnPatterns.write(app, dev.agentkeyboard.core.RgbPatternLibrary.EMPTY))
        assertEquals(big, prefs.getString(RgbOwnPatterns.PREF, null))
    }

    @Test fun aStopKeepsItsPlaceAfterSorting() {
        val a = RgbStop(0f, 0xFFFF0000.toInt())
        val b = RgbStop(0.5f, 0xFF00FF00.toInt())
        val c = RgbStop(1f, 0xFF0000FF.toInt())
        val before = listOf(a, b, c)
        assertEquals(2, helium314.keyboard.settings.screens.stopAfterSort(listOf(b, c, a.copy(position = 1f)), 0, before))
        assertEquals(0, helium314.keyboard.settings.screens.stopAfterSort(listOf(c.copy(position = 0f), a, b), 2, before))
        // moved onto an identical stop: one of the two equal places, never another colour
        assertEquals(1, helium314.keyboard.settings.screens.stopAfterSort(listOf(a, a, c), 1, listOf(a, a.copy(position = 0.3f), c)))
    }

    // --- Diagnose ---

    @Test fun diagnoseListsAndMeasuresOwnPatterns() {
        RgbOwnPatterns.write(app, dev.agentkeyboard.core.RgbPatternLibrary(listOf(sparkle)))
        RgbLighting.write(prefs, RgbSettings(enabled = true, own = sparkle, layers = setOf(RgbLayer.KEY_BACKGROUND, RgbLayer.KEYBOARD_BACKGROUND)).withSeparate(true)
            .let { it.withLook(RgbTarget.BACKGROUND, it.lookFor(RgbTarget.BACKGROUND).copy(own = null, pattern = RgbPattern.INK)) })
        val rows = Diagnose.lightPatterns(app, RgbLighting.read(prefs), library)
        // review N10: the page can be copied into a report, so own patterns appear by number, never by the user's name
        assertEquals("eigenes Muster 1, Tinte", rows.first().value)
        val grids = rows.drop(1)
        assertEquals(listOf("Raster 48 × 32 „eigenes Muster 1“ (jetzt gemessen)", "Raster 48 × 32 „Tinte“ (jetzt gemessen)"), grids.map { it.label })
        assertTrue(rows.none { "Funken" in it.label || "Funken" in it.value })
        for (g in grids) assertTrue(Regex("\\d+[.,]\\d\\d ms").matches(g.value), g.value)
        assertEquals(emptyList(), Diagnose.lightPatterns(app, RgbSettings()), "light off: nothing")
        // on the page, also without measured frames
        Global.putFloat(app.contentResolver, Global.ANIMATOR_DURATION_SCALE, 0f)
        compose.setContent { Theme(true) { AgentDiagnoseScreen {} } }
        waitUntil("the patterns") { compose.onAllNodes(hasText("eigenes Muster 1, Tinte")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Benutzte Muster")).assertExists()
        compose.onNode(hasText("Gemessene Bilder")).assertExists()
    }

    @Test fun nameAndFileNameAreSafe() {
        assertEquals("AgentKeyboard-Lichtmuster-Polar_licht_.json", helium314.keyboard.settings.screens.fileName("Polar/licht!"))
        assertEquals("AgentKeyboard-Lichtmuster-Muster.json", helium314.keyboard.settings.screens.fileName("   "))
        val added = assertNotNull(RgbOwnPatterns.add(app, sparkle))
        assertNull(library.rename(added.id, "a\nb"))
        assertTrue(RgbOwnPatterns.write(app, library.rename(added.id, "Neu")!!))
        assertEquals("Neu", library[added.id]!!.name)
    }
}
