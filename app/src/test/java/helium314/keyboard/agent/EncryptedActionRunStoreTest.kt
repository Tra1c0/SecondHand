// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import dev.agentkeyboard.core.AiTextAction
import helium314.keyboard.agent.security.*
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import javax.crypto.KeyGenerator
import kotlin.test.*
import org.json.JSONObject
import android.content.ContextWrapper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EncryptedActionRunStoreTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = AesGcmSecretCipher { key }
    private var time = 1_000_000_000L
    private var unlocked = true
    private val owner = UUID.randomUUID().toString()
    private fun store(process: String = owner) = EncryptedActionRunStore(app, cipher, { unlocked }, process, { time })
    private val directory get() = File(credentialContext(app).noBackupFilesDir, "agent_runs")

    @Before fun clear() { directory.deleteRecursively() }

    @Test fun encryptedMetadataSurvivesNewStoreAndTerminalStatesCannotBeRewritten() = runBlocking {
        val id = store().begin(AiTextAction.CORRECT, "openai", "model")
        assertEquals(ActionRunState.RUNNING, store().records().single().state)
        assertFalse(directory.listFiles()!!.single().readBytes().toString(Charsets.ISO_8859_1).contains(id))
        time++
        store().finish(id, ActionRunState.SUCCEEDED)
        store().finish(id, ActionRunState.FAILED)
        val record = store().records().single()
        assertEquals(ActionRunState.SUCCEEDED, record.state)
        assertEquals(time, record.updatedAt)
        assertFalse(credentialContext(app).isDeviceProtectedStorage)
    }

    @Test fun newProcessMarksActiveRunsUnknownAndNeverResumesOrChangesCompletedRuns() = runBlocking {
        val active = store().begin(AiTextAction.TRANSLATE, "openai", "model")
        val done = store().begin(AiTextAction.REPLY, "openai", "model")
        store().finish(done, ActionRunState.SUCCEEDED)
        val recovered = store(UUID.randomUUID().toString()).records().associateBy { it.id }
        assertEquals(ActionRunState.UNKNOWN, recovered.getValue(active).state)
        assertEquals(ActionRunState.SUCCEEDED, recovered.getValue(done).state)
        store().finish(active, ActionRunState.SUCCEEDED)
        assertEquals(ActionRunState.UNKNOWN, store().records().first { it.id == active }.state)
    }

    @Test fun limitsEvictOnlyTerminalRunsAndRetentionLeavesLiveRunsAlone() = runBlocking {
        val active = store().begin(AiTextAction.CORRECT, "openai", "model")
        repeat(EncryptedActionRunStore.MAX_RECORDS + 3) {
            val id = store().begin(AiTextAction.REPLY, "openai", "model")
            store().finish(id, ActionRunState.FAILED)
        }
        assertEquals(EncryptedActionRunStore.MAX_RECORDS, store().records().size)
        assertTrue(store().records().any { it.id == active })
        time += EncryptedActionRunStore.MAX_AGE_MS + 1
        assertEquals(listOf(active), store().records().map { it.id })
        store().clear()
        store().finish(active, ActionRunState.CANCELLED)
        assertTrue(store().records().isEmpty())
    }

    @Test fun fullJournalCannotForgetAnActiveIntentToStartAnother() = runBlocking {
        repeat(EncryptedActionRunStore.MAX_RECORDS) { store().begin(AiTextAction.CORRECT, "openai", "model") }
        assertFailsWith<RunStoreFailure> { store().begin(AiTextAction.CORRECT, "openai", "model") }
        assertEquals(EncryptedActionRunStore.MAX_RECORDS, store().records().size)
    }

    @Test fun inaccessibleOrTamperedJournalFailsClosedAndPreservesBytes() = runBlocking {
        store().begin(AiTextAction.CORRECT, "openai", "model")
        val file = directory.listFiles()!!.single()
        val valid = file.readBytes()
        unlocked = false
        assertFailsWith<RunStoreFailure> { store().records() }
        assertFailsWith<RunStoreFailure> { store().begin(AiTextAction.REPLY, "openai", "model") }
        assertFailsWith<RunStoreFailure> { store().clear() }
        assertContentEquals(valid, file.readBytes())
        unlocked = true
        val corrupt = valid.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        file.writeBytes(corrupt)
        val failure = assertFailsWith<RunStoreFailure> { store().begin(AiTextAction.REPLY, "openai", "model") }
        // Coroutine debug recovery may attach another sanitized copy, never the crypto failure.
        generateSequence<Throwable>(failure) { it.cause }.forEach {
            assertTrue(it is RunStoreFailure)
            assertEquals("RUN_STORAGE_UNAVAILABLE", it.message)
        }
        assertEquals("RUN_STORAGE_UNAVAILABLE", failure.message)
        assertContentEquals(corrupt, file.readBytes())
    }

    @Test fun invalidMetadataCannotMutateAnExistingJournal() = runBlocking {
        store().begin(AiTextAction.CORRECT, "openai", "model")
        val file = directory.listFiles()!!.single(); val original = file.readBytes()
        assertFailsWith<RunStoreFailure> { store().begin(AiTextAction.REPLY, "with spaces", "model") }
        assertFailsWith<RunStoreFailure> { store().begin(AiTextAction.REPLY, "openai", "https://invalid.test") }
        assertContentEquals(original, file.readBytes())
    }

    @Test fun completionBlockedByDeviceLockIsReconciledAfterUnlockInTheSameProcess() = runBlocking {
        val id = store().begin(AiTextAction.CORRECT, "openai", "model")
        unlocked = false
        assertFailsWith<RunStoreFailure> { store().finish(id, ActionRunState.CANCELLED) }
        unlocked = true
        val record = store().records().single()
        assertEquals(id, record.id)
        assertEquals(ActionRunState.UNKNOWN, record.state)
        time += EncryptedActionRunStore.MAX_AGE_MS + 1
        assertTrue(store().records().isEmpty())
        assertTrue(store().begin(AiTextAction.CORRECT, "openai", "model").isNotBlank())
    }

    @Test fun workflowBuilderAndLinkedModelSubjectsRoundTripWithoutAnyContentFields() = runBlocking {
        val workflow = RunSubject.Workflow(UUID.randomUUID().toString(), 7)
        val parent = store().begin(workflow)
        val link = WorkflowStepLink(parent, workflow.definitionId, workflow.revision, "translate")
        val child = store().begin(RunSubject.TextAction(AiTextAction.TRANSLATE, link), "openai", "model")
        val builder = store().begin(RunSubject.WorkflowBuilder, "openai", "model")
        store().finish(child, ActionRunState.SUCCEEDED)
        store().finish(parent, ActionRunState.SUCCEEDED)
        store().finish(builder, ActionRunState.FAILED)
        val records = store().records().associateBy { it.id }
        assertEquals(workflow, records.getValue(parent).subject)
        assertNull(records.getValue(parent).provider)
        assertNull(records.getValue(parent).model)
        assertEquals(link, (records.getValue(child).subject as RunSubject.TextAction).workflow)
        assertEquals(RunSubject.WorkflowBuilder, records.getValue(builder).subject)
        val plain = cipher.open(directory.listFiles()!!.single().readBytes(), "AgentKeyboard/ActionRuns/v1".toByteArray())
        val root = JSONObject(plain.toString(Charsets.UTF_8))
        assertEquals(2, root.getInt("version"))
        val row = root.getJSONArray("runs").getJSONObject(0)
        assertEquals(setOf("id", "subject", "provider", "model", "startedAt", "updatedAt", "state", "owner"), row.keys().asSequence().toSet())
        plain.fill(0)
    }

    @Test fun versionOneActionMetadataMigratesUnderTheSameKeyAndAuthenticationDomain() = runBlocking {
        val id = store().begin(AiTextAction.CORRECT, "openai", "model")
        store().finish(id, ActionRunState.SUCCEEDED)
        val file = directory.listFiles()!!.single()
        val aad = "AgentKeyboard/ActionRuns/v1".toByteArray()
        val plain = cipher.open(file.readBytes(), aad)
        val root = JSONObject(plain.toString(Charsets.UTF_8)); plain.fill(0)
        root.put("version", 1)
        val row = root.getJSONArray("runs").getJSONObject(0)
        row.remove("subject"); row.put("action", "CORRECT")
        file.writeBytes(cipher.seal(root.toString().toByteArray(), aad))
        val migrated = store().records().single()
        assertEquals(id, migrated.id)
        assertEquals(RunSubject.TextAction(AiTextAction.CORRECT), migrated.subject)
        assertEquals(ActionRunState.SUCCEEDED, migrated.state)
        val after = cipher.open(file.readBytes(), aad)
        assertEquals(2, JSONObject(after.toString(Charsets.UTF_8)).getInt("version"))
        after.fill(0)
    }

    @Test fun inconsistentOrUnknownSubjectsNeverOverwriteSavedStatus() = runBlocking {
        store().begin(AiTextAction.CORRECT, "openai", "model")
        val file = directory.listFiles()!!.single(); val before = file.readBytes()
        assertFailsWith<RunStoreFailure> { store().begin(RunSubject.Workflow(UUID.randomUUID().toString(), 1), "openai", "model") }
        assertFailsWith<RunStoreFailure> { store().begin(RunSubject.WorkflowBuilder) }
        assertContentEquals(before, file.readBytes())
        assertFails { WorkflowStepLink("../invalid", UUID.randomUUID().toString(), 1, "step1") }
        assertFails { RunSubjectCodec.decode(JSONObject().put("kind", "arbitrary-code")) }
        assertFails { RunSubjectCodec.decode(JSONObject().put("kind", "workflow-builder").put("input", "must not be retained")) }
        Unit
    }

    @Test fun lockedCompletionCleanupDoesNotTouchTheCredentialProtectedDirectory() = runBlocking {
        var pathAvailable = true
        val base = credentialContext(app)
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext() = this
            override fun isDeviceProtectedStorage() = false
            override fun getNoBackupFilesDir(): File {
                check(pathAvailable) { "CE_UNAVAILABLE" }
                return base.noBackupFilesDir
            }
        }
        val journal = EncryptedActionRunStore(context, cipher, { unlocked }, owner, { time })
        val id = journal.begin(RunSubject.Workflow(UUID.randomUUID().toString(), 1))
        unlocked = false; pathAvailable = false
        assertFailsWith<RunStoreFailure> { journal.finish(id, ActionRunState.CANCELLED) }
        unlocked = true; pathAvailable = true
        assertEquals(ActionRunState.UNKNOWN, journal.records().single().state)
    }
}
