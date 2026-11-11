// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import dev.agentkeyboard.core.*
import helium314.keyboard.latin.utils.prefs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*
import kotlinx.serialization.json.*
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SkillBindingStoreTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private fun store(access: Boolean = true) = SkillBindingStore(app) { access }

    @Test fun bindingsPersistIndependentlyFromKeyboardAndSecrets() {
        val before = app.prefs().all.toMap()
        val store = store()
        store.set(SkillBinding(2, "Translate", BuiltinAction.TRANSLATE))
        assertEquals(BuiltinAction.TRANSLATE, store().read().single { it.slot == 2 }.action)
        assertEquals(BuiltinAction.NONE, store().forTyping(1).action)
        assertEquals(before, app.prefs().all)
        assertEquals(setOf("formatVersion", "kind", "bindings"),
            kotlinx.serialization.json.Json.parseToJsonElement(store.export()).let { it as kotlinx.serialization.json.JsonObject }.keys)
    }

    @Test fun malformedFilesCannotPartiallyChangeBindings() {
        val store = store()
        store.set(SkillBinding(1, "Case", BuiltinAction.UPPERCASE))
        val original = store.export()
        for (bad in listOf(
            original.replace("\"formatVersion\":2", "\"formatVersion\":99"),
            original.replace("\"slot\":2", "\"slot\":1"),
            original.replace("local.uppercase", "https://example.invalid/run"),
            original.replace("\"bindings\":", "\"api_key\":\"not-allowed\",\"bindings\":"),
            original.replace("\"label\":\"Case\"", "\"label\":42"),
            original.replace("Case", "\\n"),
            "x".repeat(16_385),
        )) {
            assertFails { store.import(bad) }
            assertEquals(original, store.export())
        }
    }

    @Test fun unavailableConfigurationFallsBackWithoutChangingPersistedSlots() {
        store().set(SkillBinding(3, "Summary", BuiltinAction.SUMMARIZE))
        assertEquals(BuiltinAction.NONE, store(false).forTyping(3).action)
        assertFails { store(false).set(SkillBinding(3, "Case", BuiltinAction.LOWERCASE)) }
        assertEquals(BuiltinAction.SUMMARIZE, store().forTyping(3).action)
    }

    @Test fun labelsDoNotAcceptControlCharactersOrMalformedUnicode() {
        for (label in listOf("line\nbreak", "\uD800", "x".repeat(33), " ", "abc\u202Edef")) {
            assertFails { SkillBinding(1, label, BuiltinAction.CORRECT) }
        }
    }

    @Test fun workflowReferencesRoundTripWithoutEmbeddingWorkflowContents() {
        val id = UUID.randomUUID().toString()
        store().set(SkillBinding(1, "My workflow", BuiltinAction.WORKFLOW, id))
        assertEquals(id, store().read().first().workflowId)
        assertEquals(BuiltinAction.WORKFLOW, store().forTyping(1).action)
        assertNull(store().forTyping(2).workflowId)
        val root = Json.parseToJsonElement(store().export()).jsonObject
        assertEquals(2, root.getValue("formatVersion").jsonPrimitive.int)
        assertEquals(setOf("slot", "label", "action", "workflowId"), root.getValue("bindings").jsonArray.first().jsonObject.keys)
        assertEquals(BuiltinAction.NONE, store(false).forTyping(1).action)
    }

    @Test fun legacySkillFormatOneMigratesOnlyToBuiltinBindings() {
        val bindings = SkillBinding.defaults().map { if (it.slot == 2) SkillBinding(2, "Translate", BuiltinAction.TRANSLATE) else it }
        val root = Json.parseToJsonElement(SkillBindingStore.encode(bindings)).jsonObject
        val legacy = JsonObject(root + mapOf("formatVersion" to JsonPrimitive(1), "bindings" to JsonArray(
            root.getValue("bindings").jsonArray.map { JsonObject(it.jsonObject - "workflowId") })))
        assertEquals(bindings, SkillBindingStore.decode(legacy.toString()))
        store().import(legacy.toString())
        assertEquals(bindings, store().read())
        assertEquals(2, Json.parseToJsonElement(store().export()).jsonObject.getValue("formatVersion").jsonPrimitive.int)
        assertFails { SkillBindingStore.decode(JsonObject(root + ("formatVersion" to JsonPrimitive(1))).toString()) }
        assertFails { SkillBindingStore.decode(legacy.toString().replace("ai.translate", "workflow")) }
    }

    @Test fun invalidReferenceTypeOrActionCombinationCannotChangeAnySlot() {
        val store = store()
        store.set(SkillBinding(1, "Original", BuiltinAction.UPPERCASE))
        val before = store.export()
        val id = UUID.randomUUID().toString()
        val invalid = listOf(
            before.replace("local.uppercase", "workflow"),
            before.replace("\"workflowId\":null", "\"workflowId\":\"$id\""),
            before.replace("local.uppercase", "workflow").replace("\"workflowId\":null", "\"workflowId\":true"),
            before.replace("local.uppercase", "workflow").replace("\"workflowId\":null", "\"workflowId\":\"../private\""),
        )
        invalid.forEach { encoded ->
            assertFails { store.import(encoded) }
            assertEquals(before, store.export())
        }
    }

    @Test fun staleSlotEditorCannotOverwriteAnImportButIndependentSlotsStillMerge() {
        val store = store()
        val before = store.read()
        val imported = SkillBinding(1, "Imported", BuiltinAction.WORKFLOW, UUID.randomUUID().toString())
        store.set(imported)
        assertFails { store.set(SkillBinding(1, "Old draft", BuiltinAction.UPPERCASE), before.first()) }
        store.set(SkillBinding(2, "Independent", BuiltinAction.LOWERCASE), before[1])
        assertEquals(imported, store.read().first())
        assertEquals(BuiltinAction.LOWERCASE, store.read()[1].action)
    }
}
