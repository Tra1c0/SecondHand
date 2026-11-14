// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.Manifest
import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.UserManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.agentkeyboard.core.BuiltinAction
import dev.agentkeyboard.core.LocalTextAction
import dev.agentkeyboard.core.SkillBinding
import dev.agentkeyboard.core.TranslationLanguage
import dev.agentkeyboard.core.WorkflowDefinition
import dev.agentkeyboard.core.WorkflowInput
import dev.agentkeyboard.core.WorkflowProvider
import dev.agentkeyboard.core.WorkflowStep
import dev.agentkeyboard.core.WritingTone
import helium314.keyboard.agent.providers.CloudProviders
import helium314.keyboard.agent.security.AesGcmSecretCipher
import helium314.keyboard.agent.security.CredentialSlot
import helium314.keyboard.agent.security.EncryptedCredentialStore
import helium314.keyboard.agent.security.credentialContext
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.screens.AI_LOCKED_TAG
import helium314.keyboard.settings.screens.AI_ROW_TAG
import helium314.keyboard.settings.screens.AREA_ROW_TAG
import helium314.keyboard.settings.screens.AREA_TAG
import helium314.keyboard.settings.screens.AgentAreaAiScreen
import helium314.keyboard.settings.screens.AgentAreaScreen
import helium314.keyboard.settings.screens.AgentSettingsTokens
import helium314.keyboard.settings.screens.BACK_TAG
import helium314.keyboard.settings.screens.CHOICE_TAG
import helium314.keyboard.settings.screens.HERO_TAG
import helium314.keyboard.settings.screens.IMPORT_EXPORT_ROW
import helium314.keyboard.settings.screens.KEY_STATUS_TAG
import helium314.keyboard.settings.screens.LANGUAGE_ROW
import helium314.keyboard.settings.screens.NEW_WORKFLOW_ROW
import helium314.keyboard.settings.screens.PROVIDER_TAG
import helium314.keyboard.settings.screens.RUNS_ROW
import helium314.keyboard.settings.screens.SKILL_TAG
import helium314.keyboard.settings.screens.SettingsArea
import helium314.keyboard.settings.screens.SettingsAreas
import helium314.keyboard.settings.screens.TONE_ROW
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
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.crypto.KeyGenerator
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Stage 38g: the area page "KI & Automationen" (mockup KI.dc.html) on the real provider, key, skill, workflow and dictation state. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "de-w390dp-h2200dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class SettingsAreaAiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app: Application = RuntimeEnvironment.getApplication()
    private val routes = mutableListOf<String>()
    private var back = 0
    private val keyguard get() = shadowOf(app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
    private val credentials get() = File(credentialContext(app).noBackupFilesDir, "agent_credentials")
    private val aesKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val secret = "sk-offline-test-SECRET-4711"

    @Before fun prepare() {
        keyguard.setKeyguardLocked(false)
        shadowOf(app.getSystemService(Context.USER_SERVICE) as UserManager).setUserUnlocked(true)
        for (name in listOf("agent_skill_keys.json", "agent_workflows.json")) {
            val file = File(credentialContext(app).noBackupFilesDir, name)
            file.delete(); File(file.path + ".bak").delete()
        }
        credentials.listFiles()?.forEach { it.delete() }
        credentialContext(app).getSharedPreferences("agent_models", Context.MODE_PRIVATE).edit().clear().commit()
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
    }

    @After fun reset() {
        keyguard.setKeyguardLocked(false)
        credentials.listFiles()?.forEach { it.delete() }
        app.prefs().edit().clear().commit()
    }

    private fun page(dictation: (() -> Boolean)? = null) {
        initPreview(app)
        compose.setContent {
            Theme(dark = true) {
                if (dictation == null) AgentAreaScreen(SettingsArea.AI, onNavigate = { routes += it }, onClickBack = { back++ })
                else AgentAreaAiScreen(onNavigate = { routes += it }, onClickBack = { back++ }, dictationAvailable = dictation)
            }
        }
        compose.waitForIdle()
    }

    private fun waitForText(text: String) =
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun selected(provider: WorkflowProvider) =
        compose.onNodeWithTag(CHOICE_TAG + PROVIDER_TAG + provider.id).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected)

    private fun workflow(name: String) = WorkflowDefinition(UUID.randomUUID().toString(), 1, name,
        listOf(WorkflowStep.Local("first", WorkflowInput.Selection, LocalTextAction.UPPERCASE)))

    /** Every text, editable text and description of the page, merged and unmerged. */
    private fun allTexts(): List<String> = listOf(false, true).flatMap { unmerged ->
        compose.onAllNodes(SemanticsMatcher("any") { true }, useUnmergedTree = unmerged).fetchSemanticsNodes().flatMap { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                listOfNotNull(node.config.getOrNull(SemanticsProperties.EditableText)?.text) +
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        }
    }

    @Test fun pageHasTheMockupStructure() {
        page()
        compose.onNodeWithTag(AREA_TAG + SettingsArea.AI.route).assertExists()
        compose.onNodeWithTag(HERO_TAG).assert(hasText("06")).assert(hasText("KI & Automationen"))
            .assert(hasText("Nur mit deinem eigenen Schlüssel – nichts geht ohne deinen Tipp raus."))
        waitForText("Kein Schlüssel gespeichert")
        listOf("ANBIETER", "OPENAI", "ANTHROPIC", "GOOGLE GEMINI", "Kein Schlüssel gespeichert", "Ändern",
            "Skill-Tasten", "1", "2", "3",
            "Texte", "Zielsprache für Übersetzungen", "Für @translate und Skill-Tasten", "Tonfall beim Umformulieren",
            "Neutral, freundlich, professionell", "Neutral",
            "Workflows", "Meine Workflows", "Bis zu 8 Schritte, 3 KI-Anfragen", "Neuer Workflow", "Selbst bauen oder mit KI entwerfen", "+",
            "Import & Export", "Nur Konfiguration, keine Schlüssel",
            "Diktat & Verlauf", "Diktat auf dem Gerät", "Auftragsstatus", "Letzte 20 Läufe, 7 Tage")
            .forEach { assertTrue(compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty(), "missing text: $it") }
        // sections in the order of the mockup
        val tops = listOf("ANBIETER", "Skill-Tasten", "Texte", "Workflows", "Diktat & Verlauf")
            .map { compose.onAllNodesWithText(it).fetchSemanticsNodes().first().boundsInRoot.top }
        assertEquals(tops.sorted(), tops)
        // the three skill tiles side by side, equally wide
        val tiles = listOf(AREA_ROW_TAG + SettingsDestination.AgentSkills, SKILL_TAG + 2, SKILL_TAG + 3)
            .map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        assertEquals(tiles[0].top, tiles[1].top, 1f); assertEquals(tiles[1].top, tiles[2].top, 1f)
        assertEquals(tiles[0].width, tiles[2].width, 1f)
        assertTrue(tiles[1].left > tiles[0].right && tiles[2].left > tiles[1].right)
        // the old page "Seiten" is gone from this area
        assertTrue(compose.onAllNodesWithText("Seiten").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag(BACK_TAG).performClick()
        assertEquals(1, back)
        // like the old AI page: no screenshots while it is shown
        assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
    }

    @Test fun providerCardsMapToTheRealPreferenceBothWays() {
        ModelSettings(app).setProvider(WorkflowProvider.CLAUDE)
        ModelSettings(app).setModel(WorkflowProvider.OPENAI, "gpt-own-model")
        page()
        assertEquals(true, selected(WorkflowProvider.CLAUDE))
        assertEquals(false, selected(WorkflowProvider.OPENAI))
        assertEquals(false, selected(WorkflowProvider.GEMINI))
        // the hint is the real model id, Gemini has no default
        compose.onNodeWithTag(CHOICE_TAG + PROVIDER_TAG + "openai").assert(hasText("OPENAI")).assert(hasText("Modell: gpt-own-model"))
        compose.onNodeWithTag(CHOICE_TAG + PROVIDER_TAG + "claude").assert(hasText("ANTHROPIC"))
            .assert(hasText("Modell: " + ModelSettings.DEFAULT_ANTHROPIC_MODEL))
        compose.onNodeWithTag(CHOICE_TAG + PROVIDER_TAG + "gemini").assert(hasText("GOOGLE GEMINI")).assert(hasText("Modell-ID selbst eintragen"))
        // writing: a tap stores the provider the keyboard's AI actions use
        compose.onNodeWithTag(CHOICE_TAG + PROVIDER_TAG + "gemini").performClick()
        compose.waitForIdle()
        assertEquals(WorkflowProvider.GEMINI, ModelSettings(app).provider)
        assertEquals(true, selected(WorkflowProvider.GEMINI))
        assertEquals(false, selected(WorkflowProvider.CLAUDE))
        compose.onNodeWithTag(CHOICE_TAG + PROVIDER_TAG + "openai").performClick()
        compose.waitForIdle()
        assertEquals(WorkflowProvider.OPENAI, ModelSettings(app).provider)
        // the model ids are not touched by choosing a provider
        assertEquals("gpt-own-model", ModelSettings(app).model(WorkflowProvider.OPENAI))
        assertEquals(ModelSettings.DEFAULT_ANTHROPIC_MODEL, ModelSettings(app).model(WorkflowProvider.CLAUDE))
    }

    @Test fun keyStatusFollowsTheProviderAndNeverShowsTheKey() {
        EncryptedCredentialStore(app, AesGcmSecretCipher { aesKey }).save(CredentialSlot.OPENAI, secret.toCharArray())
        page()
        waitForText("Schlüssel gespeichert · verschlüsselt auf dem Gerät")
        compose.onNodeWithTag(KEY_STATUS_TAG).assert(hasAnyDescendant(hasText("Schlüssel gespeichert · verschlüsselt auf dem Gerät")))
        assertTrue(allTexts().none { secret in it || it.contains("SECRET") }, "the key must never appear")
        // another provider has its own slot
        compose.onNodeWithTag(CHOICE_TAG + PROVIDER_TAG + "claude").performClick()
        waitForText("Kein Schlüssel gespeichert")
        compose.onNodeWithTag(KEY_STATUS_TAG).assert(hasAnyDescendant(hasText("Kein Schlüssel gespeichert")))
        compose.onNodeWithTag(CHOICE_TAG + PROVIDER_TAG + "openai").performClick()
        waitForText("Schlüssel gespeichert · verschlüsselt auf dem Gerät")
        assertTrue(allTexts().none { secret in it || it.contains("SECRET") }, "the key must never appear")
        // the key itself is still where it was, and unchanged
        EncryptedCredentialStore(app, AesGcmSecretCipher { aesKey }).read(CredentialSlot.OPENAI)!!.use { assertEquals(secret, it.value()) }
        // "Ändern" opens the key entry of the old AI page
        compose.onNodeWithTag(AREA_ROW_TAG + SettingsDestination.AgentAi).performClick()
        assertEquals(listOf(SettingsDestination.AgentAi), routes)
    }

    @Test fun aLockedDeviceOpensNoProtectedStorageAndKeepsThePagesReachable() {
        keyguard.setKeyguardLocked(true)
        page()
        compose.onNodeWithTag(AI_LOCKED_TAG).assert(hasAnyDescendant(hasText("Gerät entsperren, um die KI-Anbieter einzurichten.")))
        // nothing that needs the credential-protected storage
        assertTrue(compose.onAllNodesWithTag(KEY_STATUS_TAG).fetchSemanticsNodes().isEmpty())
        CloudProviders.SUPPORTED.forEach {
            assertTrue(compose.onAllNodesWithTag(CHOICE_TAG + PROVIDER_TAG + it.id).fetchSemanticsNodes().isEmpty())
        }
        assertTrue(compose.onAllNodesWithTag(SKILL_TAG + 2).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Meine Workflows").fetchSemanticsNodes().isEmpty())
        // the old pages stay reachable; they show their own lock note
        val pages = SettingsAreas.entries(SettingsArea.AI).map { it.route }
        pages.forEach { compose.onNodeWithTag(AREA_ROW_TAG + it).performScrollTo().performClick() }
        assertEquals(pages, routes)
        assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
    }

    @Test fun skillTilesShowTheRealNamesAndOpenTheSkillPage() {
        val workflows = WorkflowStore(app)
        val mail = workflow("Mail")
        workflows.save(mail, expectedGeneration = workflows.snapshot().generation)
        val skills = SkillBindingStore(app)
        skills.set(SkillBinding(1, "Korrigieren", BuiltinAction.CORRECT))
        skills.set(SkillBinding(2, "", BuiltinAction.NONE))
        skills.set(SkillBinding(3, "Workflow „Mail“", BuiltinAction.WORKFLOW, mail.id))
        page()
        waitForText("Korrigieren")
        compose.onNodeWithTag(AREA_ROW_TAG + SettingsDestination.AgentSkills).assert(hasText("1")).assert(hasText("Korrigieren"))
        // an empty slot shows what the skill page shows for it
        compose.onNodeWithTag(SKILL_TAG + 2).assert(hasText("2")).assert(hasText("Nicht belegt"))
        compose.onNodeWithTag(SKILL_TAG + 3).assert(hasText("3")).assert(hasText("Workflow „Mail“"))
        listOf(AREA_ROW_TAG + SettingsDestination.AgentSkills, SKILL_TAG + 2, SKILL_TAG + 3).forEach {
            compose.onNodeWithTag(it).assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).performClick()
        }
        assertEquals(List(3) { SettingsDestination.AgentSkills }, routes)
    }

    @Test fun textsAndWorkflowsShowTheRealValues() {
        val models = ModelSettings(app)
        models.setTranslationLanguage(TranslationLanguage.FRENCH)
        models.setWritingTone(WritingTone.FRIENDLY)
        val workflows = WorkflowStore(app)
        workflows.save(workflow("Eins"), expectedGeneration = workflows.snapshot().generation)
        workflows.save(workflow("Zwei"), expectedGeneration = workflows.snapshot().generation)
        page()
        fun name(language: TranslationLanguage) = Locale.forLanguageTag(language.code).getDisplayLanguage(Locale.getDefault())
        compose.onNodeWithTag(AI_ROW_TAG + LANGUAGE_ROW).assert(hasText(name(TranslationLanguage.FRENCH)))
        compose.onNodeWithTag(AI_ROW_TAG + TONE_ROW).assert(hasText("Freundlich"))
        compose.waitUntil(10_000) {
            compose.onNodeWithTag(AREA_ROW_TAG + SettingsDestination.AgentWorkflows).fetchSemanticsNode()
                .config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "2" }
        }
        // the lists of the old AI page write the real values
        compose.onNodeWithTag(AI_ROW_TAG + LANGUAGE_ROW).performScrollTo().performClick()
        compose.onNodeWithText(name(TranslationLanguage.SPANISH)).performClick()
        compose.waitForIdle()
        assertEquals(TranslationLanguage.SPANISH, ModelSettings(app).translationLanguage)
        compose.onNodeWithTag(AI_ROW_TAG + LANGUAGE_ROW).assert(hasText(name(TranslationLanguage.SPANISH)))
        compose.onNodeWithTag(AI_ROW_TAG + TONE_ROW).performScrollTo().performClick()
        compose.onNodeWithText("Professionell").performClick()
        compose.waitForIdle()
        assertEquals(WritingTone.PROFESSIONAL, ModelSettings(app).writingTone)
        compose.onNodeWithTag(AI_ROW_TAG + TONE_ROW).assert(hasText("Professionell"))
        // the workflow rows and the run status open their existing pages
        listOf(AREA_ROW_TAG + SettingsDestination.AgentWorkflows, AI_ROW_TAG + NEW_WORKFLOW_ROW, AI_ROW_TAG + IMPORT_EXPORT_ROW, AI_ROW_TAG + RUNS_ROW)
            .forEach { compose.onNodeWithTag(it).performScrollTo().performClick() }
        assertEquals(List(3) { SettingsDestination.AgentWorkflows } + SettingsDestination.AgentAi, routes)
    }

    @Test fun dictationShowsTheRecognizerAndTheMicrophonePermission() {
        // Android 9 has no on-device recognizer
        page()
        compose.onNodeWithTag(AREA_ROW_TAG + SettingsDestination.AgentDictation).assert(hasText("Nicht verfügbar"))
            .assert(hasText("Mikrofon nicht erlaubt · ab Android 12"))
        compose.onNodeWithTag(AREA_ROW_TAG + SettingsDestination.AgentDictation).performScrollTo().performClick()
        assertEquals(listOf(SettingsDestination.AgentDictation), routes)
    }

    @Test fun dictationAsksForThePermissionWhenOnlyTheRecognizerIsThere() {
        page(dictation = { true })
        compose.onNodeWithTag(AREA_ROW_TAG + SettingsDestination.AgentDictation).assert(hasText("Erlauben"))
            .assert(hasText("Mikrofon nicht erlaubt · ab Android 12"))
    }

    @Test fun dictationIsReadyWithRecognizerAndPermission() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        page(dictation = { true })
        compose.onNodeWithTag(AREA_ROW_TAG + SettingsDestination.AgentDictation).assert(hasText("Bereit"))
            .assert(hasText("Mikrofon erlaubt · ab Android 12"))
        assertFalse(compose.onAllNodesWithText("Erlauben").fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun everyClickableIsAtLeast44dp() {
        page()
        waitForText("Kein Schlüssel gespeichert")
        val min = 44 * app.resources.displayMetrics.density - 1
        val clickables = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick), useUnmergedTree = true).fetchSemanticsNodes()
        // back, three providers, "Ändern", three skill tiles, two text rows, three workflow rows, two dictation rows
        assertTrue(clickables.size >= 1 + 3 + 1 + 3 + 2 + 3 + 2, "clickables: ${clickables.size}")
        clickables.forEach { assertTrue(it.size.height >= min && it.size.width >= min, "too small: ${it.size} ${it.config}") }
    }

    /** The whole page as in the mockup (390 dp wide): OpenAI with a stored key, skill keys as in the mockup, four workflows. */
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun renderAiPage() {
        EncryptedCredentialStore(app, AesGcmSecretCipher { aesKey }).save(CredentialSlot.OPENAI, secret.toCharArray())
        val workflows = WorkflowStore(app)
        val mail = workflow("Mail")
        workflows.save(mail, expectedGeneration = workflows.snapshot().generation)
        listOf("Termin", "Kurz", "Antwort").forEach { workflows.save(workflow(it), expectedGeneration = workflows.snapshot().generation) }
        val skills = SkillBindingStore(app)
        skills.set(SkillBinding(1, "Korrigieren", BuiltinAction.CORRECT))
        skills.set(SkillBinding(2, "Übersetzen", BuiltinAction.TRANSLATE))
        skills.set(SkillBinding(3, "Workflow „Mail“", BuiltinAction.WORKFLOW, mail.id))
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        page(dictation = { true })
        waitForText("Schlüssel gespeichert · verschlüsselt auf dem Gerät")
        waitForText("Workflow „Mail“")
        compose.waitUntil(10_000) {
            compose.onNodeWithTag(AREA_ROW_TAG + SettingsDestination.AgentWorkflows).fetchSemanticsNode()
                .config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "4" }
        }
        val image = capture("settings-area_ai-full")
        assertEquals(AgentSettingsTokens.Background.toArgb(), image.getPixel(2, image.height - 2))
        val hero = compose.onNodeWithTag(HERO_TAG).fetchSemanticsNode().boundsInRoot
        assertClose(SettingsArea.AI.from.toArgb(), image.getPixel(hero.left.toInt() + 24, hero.top.toInt() + 24), 40)
        // the tiles in the mockup's colours
        val d = app.resources.displayMetrics.density
        listOf(AREA_ROW_TAG + SettingsDestination.AgentSkills to 0xFF22D3EE.toInt(), SKILL_TAG + 2 to 0xFF818CF8.toInt(), SKILL_TAG + 3 to 0xFFA5B4FC.toInt())
            .forEach { (tag, color) ->
                val b = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                assertClose(color, image.getPixel((b.right - 8 * d).toInt(), (b.bottom - 8 * d).toInt()), 8)
            }
        // the chosen provider card (OpenAI) at full colour, Anthropic with its gradient dimmed over the dark card (choiceDim)
        val openAi = compose.onNodeWithTag(CHOICE_TAG + PROVIDER_TAG + "openai").fetchSemanticsNode().boundsInRoot
        assertClose(0xFF22D3EE.toInt(), image.getPixel((openAi.left + 6 * d).toInt(), (openAi.top + 30 * d).toInt()), 40)
        val claude = compose.onNodeWithTag(CHOICE_TAG + PROVIDER_TAG + "claude").fetchSemanticsNode().boundsInRoot
        val dim = image.getPixel((claude.left + 6 * d).toInt(), (claude.bottom - 6 * d).toInt())
        assertTrue((dim shr 16 and 0xFF) < 0xFF - 40, "dimmed #%08X".format(dim))
    }

    private fun assertClose(expected: Int, actual: Int, tolerance: Int) {
        val d = listOf(16, 8, 0).maxOf { shift -> kotlin.math.abs((expected shr shift and 0xFF) - (actual shr shift and 0xFF)) }
        assertTrue(d <= tolerance, "expected ~#%08X, got #%08X".format(expected, actual))
    }

    // captureToImage waits for a redraw that never comes in Robolectric (Fallstrick P15)
    private fun capture(name: String): Bitmap {
        lateinit var bitmap: Bitmap
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        val file = File("build/agent-previews/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }
}
