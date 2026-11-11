// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import dev.agentkeyboard.core.*
import helium314.keyboard.agent.security.credentialContext
import kotlinx.serialization.json.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkflowStoreTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private var accessible = true
    private val store get() = WorkflowStore(app) { accessible }
    private val file get() = File(credentialContext(app).noBackupFilesDir, "agent_workflows.json")
    private fun workflow(id: String = UUID.randomUUID().toString(), revision: Int = 1) = WorkflowDefinition(id, revision, "Grüße 😀", listOf(
        WorkflowStep.Local("clean", WorkflowInput.Selection, LocalTextAction.CLEAN_SPACES),
        WorkflowStep.Ai("translate", WorkflowInput.StepResult("clean"), AiTextAction.TRANSLATE,
            WorkflowProvider.OPENAI, "model", TranslationLanguage.GERMAN, WritingTone.PROFESSIONAL)))

    @Before fun clear() { file.delete(); File(file.path + ".bak").delete() }

    @Test fun roundTripPreservesTypedStepsInCredentialProtectedNoBackupStorage() {
        val original = workflow()
        store.save(original, expectedGeneration = store.snapshot().generation)
        val restored = store.read().single()
        assertEquals(original.id, restored.id)
        assertEquals(original.name, restored.name)
        assertEquals(original.steps, restored.steps)
        assertFalse(credentialContext(app).isDeviceProtectedStorage)
        assertFalse(file.toPath().startsWith(app.filesDir.toPath()))
        assertEquals(store.export(), WorkflowStore.encode(WorkflowStore.decode(store.export())))
    }

    @Test fun editsAndDeletesCannotOverwriteANewerRevision() {
        val original = workflow()
        store.save(original, expectedGeneration = store.snapshot().generation)
        val next = workflow(original.id, 2)
        store.save(next, 1, store.snapshot().generation)
        val before = file.readBytes()
        assertFails { store.save(workflow(original.id, 2), 1, store.snapshot().generation) }
        assertFails { store.save(workflow(original.id, 3), expectedGeneration = store.snapshot().generation) }
        assertFails { store.delete(original.id, 1, store.snapshot().generation) }
        assertContentEquals(before, file.readBytes())
        store.delete(original.id, 2, store.snapshot().generation)
        assertTrue(store.read().isEmpty())
    }

    @Test fun malformedImportsAreFullyRejectedBeforeAnExistingConfigurationChanges() {
        store.save(workflow(), expectedGeneration = store.snapshot().generation)
        val valid = store.export(); val before = file.readBytes()
        val root = Json.parseToJsonElement(valid).jsonObject
        val invalid = listOf(
            valid.replace("\"formatVersion\":3", "\"formatVersion\":99"),
            valid.replace("\"formatVersion\":3", "\"formatVersion\":\"3\""),
            valid.replace("\"revision\":1", "\"revision\":true"),
            valid.replace("\"local.clean_spaces\"", "\"arbitrary.code\""),
            valid.replace("\"step:clean\"", "\"step:translate\""),
            valid.replace("\"provider\":\"openai\"", "\"provider\":\"https://invalid.test\""),
            valid.replace("\"language\":\"de\"", "\"language\":\"custom prompt\""),
            valid.replace("\"model\":\"model\"", "\"model\":\"https://invalid.test\""),
            JsonObject(root + ("credentials" to JsonPrimitive("not-allowed"))).toString(),
            valid + " trailing",
            "x".repeat(WorkflowStore.MAX_BYTES + 1),
        )
        invalid.forEach { encoded ->
            assertFails { store.replaceAll(WorkflowStore.decode(encoded), store.snapshot().generation) }
            assertContentEquals(before, file.readBytes())
        }
    }

    @Test fun duplicateIdsUnknownStepFieldsAndActionKindMismatchesAreRejected() {
        val original = workflow()
        assertFails { WorkflowStore.encode(listOf(original, original)) }
        val valid = WorkflowStore.encode(listOf(original))
        for (encoded in listOf(
            valid.replace("\"kind\":\"local\"", "\"kind\":\"ai\""),
            valid.replace("\"action\":\"local.clean_spaces\"", "\"action\":\"local.clean_spaces\",\"url\":\"https://invalid.test\""),
            valid.replace("\"clean\",\"input\"", "\"translate\",\"input\""),
        )) assertFails { WorkflowStore.decode(encoded) }
    }

    @Test fun limitsAndLockedStorageNeverSilentlyEraseOrCreateWorkflows() {
        assertTrue(store.read().isEmpty())
        assertFalse(file.exists())
        val workflows = (1..WorkflowStore.MAX_WORKFLOWS).map { workflow() }
        store.replaceAll(workflows, store.snapshot().generation)
        val before = file.readBytes()
        assertFails { store.save(workflow(), expectedGeneration = store.snapshot().generation) }
        assertContentEquals(before, file.readBytes())
        accessible = false
        assertFails { store.read() }
        assertFails { store.replaceAll(emptyList(), "irrelevant-while-locked") }
        assertContentEquals(before, file.readBytes())
    }

    @Test fun malformedOrCorruptExistingFileIsNotTreatedAsAnEmptyCollection() {
        val invalid = byteArrayOf(0xC3.toByte(), 0x28)
        file.writeBytes(invalid)
        assertFails { store.read() }
        assertFails { store.save(workflow(), expectedGeneration = store.snapshot().generation) }
        assertContentEquals(invalid, file.readBytes())
    }

    @Test fun explicitRecoveryResetsOnlyTheApprovedUnreadableWorkflowFile() {
        val sibling = File(file.parentFile, "unrelated-profile.json").apply { writeText("keep") }
        val invalid = byteArrayOf(0xC3.toByte(), 0x28)
        file.writeBytes(invalid)
        val token = assertNotNull(store.recoveryToken())
        assertContentEquals(invalid, file.readBytes())
        store.resetUnreadable(token)
        assertTrue(store.read().isEmpty())
        assertNotEquals("absent", store.snapshot().generation)
        assertEquals("keep", sibling.readText())
        assertNull(store.recoveryToken())
        assertFails { store.resetUnreadable(token) }
        store.save(workflow(), expectedGeneration = store.snapshot().generation)
        assertEquals(1, store.read().size)
    }

    @Test fun recoveryApprovalRejectsAReplacedCorruptFileOrARepairedConfiguration() {
        file.writeText("broken")
        val token = assertNotNull(store.recoveryToken())
        file.writeText("another broken file")
        assertFails { store.resetUnreadable(token) }
        assertEquals("another broken file", file.readText())
        file.delete()
        store.save(workflow(), expectedGeneration = store.snapshot().generation)
        val healthy = file.readBytes()
        assertFails { store.resetUnreadable(token) }
        assertContentEquals(healthy, file.readBytes())
    }

    @Test fun lockedOrMissingConfigurationIsNeverRecoverableButOversizedDataIs() {
        assertNull(store.recoveryToken())
        file.writeBytes(ByteArray(1_048_577))
        assertFails { store.recoveryToken() }
        assertEquals(1_048_577L, file.length())
        file.writeText("x".repeat(WorkflowStore.MAX_BYTES + 257))
        val token = assertNotNull(store.recoveryToken())
        val bytes = file.readBytes()
        accessible = false
        assertFails { store.recoveryToken() }
        assertFails { store.resetUnreadable(token) }
        assertContentEquals(bytes, file.readBytes())
        accessible = true
        store.resetUnreadable(token)
        assertTrue(store.read().isEmpty())
    }

    @Test fun sameRevisionImportAndDeleteRecreateInvalidateEveryStaleWriter() {
        val original = workflow()
        store.save(original, expectedGeneration = store.snapshot().generation)
        val beforeImport = store.snapshot()
        val imported = WorkflowDefinition(original.id, original.revision, "Imported", original.steps)
        store.replaceAll(listOf(imported), beforeImport.generation)
        val importedBytes = file.readBytes()
        assertFails { store.save(workflow(original.id, 2), 1, beforeImport.generation) }
        assertFails { store.delete(original.id, 1, beforeImport.generation) }
        assertFails { store.replaceAll(listOf(original), beforeImport.generation) }
        assertContentEquals(importedBytes, file.readBytes())

        store.replaceAll(emptyList(), store.snapshot().generation)
        store.save(original, expectedGeneration = store.snapshot().generation)
        assertNotEquals(beforeImport.generation, store.snapshot().generation)
        assertFails { store.save(workflow(original.id, 2), 1, beforeImport.generation) }
        assertFails { store.delete(original.id, 1, beforeImport.generation) }
        assertEquals("Grüße 😀", store.read().single().name)
    }

    @Test fun currentFormatPreservesConditionsWithoutChangingOtherWorkflows() {
        val original = workflow()
        store.save(original, expectedGeneration = store.snapshot().generation)
        val conditional = WorkflowDefinition(UUID.randomUUID().toString(), 1, "Conditional", listOf(
            WorkflowStep.Local("first", WorkflowInput.Selection, LocalTextAction.UPPERCASE,
                TextCondition(TextPredicate.NON_EMPTY))))
        store.save(conditional, expectedGeneration = store.snapshot().generation)
        assertEquals(conditional.steps, store.read().single { it.id == conditional.id }.steps)
        assertEquals(original.steps, store.read().single { it.id == original.id }.steps)
        assertEquals(3, Json.parseToJsonElement(store.export()).jsonObject.getValue("formatVersion").jsonPrimitive.int)
    }

    @Test fun legacyFormatOneMigratesToUnconditionalStepsAndExportsAsCurrentFormat() {
        val original = workflow()
        val root = Json.parseToJsonElement(WorkflowStore.encode(listOf(original))).jsonObject
        val rows = root.getValue("workflows").jsonArray.map { element ->
            val row = element.jsonObject
            JsonObject(row + ("steps" to JsonArray(row.getValue("steps").jsonArray.map {
                JsonObject(it.jsonObject - "condition")
            })))
        }
        val legacy = JsonObject(root + mapOf("formatVersion" to JsonPrimitive(1), "workflows" to JsonArray(rows)))
        val migrated = WorkflowStore.decode(legacy.toString()).single()
        assertEquals(original.steps, migrated.steps)
        assertTrue(migrated.steps.all { it.condition == null })
        store.save(original, expectedGeneration = store.snapshot().generation)
        val wrapper = Json.parseToJsonElement(file.readText()).jsonObject
        file.writeText(JsonObject(wrapper + ("configuration" to legacy)).toString())
        assertEquals(original.steps, store.read().single().steps)
        assertEquals(3, Json.parseToJsonElement(store.export()).jsonObject.getValue("formatVersion").jsonPrimitive.int)
        // A v1 file carrying new fields is not an implicit grant to activate them.
        assertFails { WorkflowStore.decode(JsonObject(root + ("formatVersion" to JsonPrimitive(1))).toString()) }
    }

    @Test fun malformedConditionTypesAndUnknownFieldsCannotChangeTheSavedCollection() {
        store.save(workflow(), expectedGeneration = store.snapshot().generation)
        val before = file.readBytes(); val valid = store.export()
        val invalidRules = listOf(
            "{\"predicate\":\"UNKNOWN\",\"literal\":\"\",\"minimumLength\":0,\"negated\":false}",
            "{\"predicate\":\"NON_EMPTY\",\"literal\":\"\",\"minimumLength\":0,\"negated\":\"false\"}",
            "{\"predicate\":\"LENGTH_AT_LEAST\",\"literal\":\"\",\"minimumLength\":\"100\",\"negated\":false}",
            "{\"predicate\":\"CONTAINS_LITERAL\",\"literal\":\"\",\"minimumLength\":0,\"negated\":false}",
            "{\"predicate\":\"NON_EMPTY\",\"literal\":\"\",\"minimumLength\":0,\"negated\":false,\"url\":\"https://invalid.test\"}",
        )
        invalidRules.forEach { rule ->
            assertFails { store.replaceAll(WorkflowStore.decode(valid.replace("\"condition\":null", "\"condition\":$rule")), store.snapshot().generation) }
            assertContentEquals(before, file.readBytes())
        }
    }

    @Test fun legacyFormatTwoKeepsConditionsAndItsOriginalTwoStepLimit() {
        val condition = TextCondition(TextPredicate.CONTAINS_LITERAL, "marker", negated = true)
        val first = WorkflowStep.Local("first", WorkflowInput.Selection, LocalTextAction.UPPERCASE, condition)
        val current = WorkflowDefinition(UUID.randomUUID().toString(), 1, "Legacy conditional", listOf(first))
        val root = Json.parseToJsonElement(WorkflowStore.encode(listOf(current))).jsonObject
        val legacy = JsonObject(root + ("formatVersion" to JsonPrimitive(2)))
        assertEquals(current.steps, WorkflowStore.decode(legacy.toString()).single().steps)
        val larger = WorkflowDefinition(UUID.randomUUID().toString(), 1, "Larger", listOf(first,
            WorkflowStep.Local("second", WorkflowInput.StepResult("first"), LocalTextAction.LOWERCASE),
            WorkflowStep.Local("third", WorkflowInput.StepResult("first"), LocalTextAction.CLEAN_SPACES)))
        val currentRoot = Json.parseToJsonElement(WorkflowStore.encode(listOf(larger))).jsonObject
        assertEquals(larger.steps, WorkflowStore.decode(currentRoot.toString()).single().steps)
        assertFails { WorkflowStore.decode(JsonObject(currentRoot + ("formatVersion" to JsonPrimitive(2))).toString()) }
        val v1Rows = currentRoot.getValue("workflows").jsonArray.map { row -> JsonObject(row.jsonObject + (
            "steps" to JsonArray(row.jsonObject.getValue("steps").jsonArray.map { JsonObject(it.jsonObject - "condition") }))) }
        assertFails { WorkflowStore.decode(JsonObject(currentRoot + mapOf("formatVersion" to JsonPrimitive(1), "workflows" to JsonArray(v1Rows))).toString()) }
    }
}
