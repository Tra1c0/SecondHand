// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.os.Build
import android.util.AtomicFile
import dev.agentkeyboard.core.AiTextAction
import helium314.keyboard.agent.security.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class RunStoreFailure : Exception("RUN_STORAGE_UNAVAILABLE")

/** Bounded CE/no-backup journal. No scheduling, replay, source or result persistence. */
class EncryptedActionRunStore(
    context: Context,
    cipher: SecretCipher? = null,
    private val unlocked: () -> Boolean = { credentialAccessAllowed(context) },
    private val process: String = processId,
    private val now: () -> Long = System::currentTimeMillis,
) : ActionRunJournal {
    override val supportsWorkflowRecords: Boolean get() = Build.VERSION.SDK_INT >= 23
    private val storage = credentialContext(context.applicationContext)
    private val file get() = AtomicFile(File(storage.noBackupFilesDir, "agent_runs/status.sealed"))
    private fun liveKey(id: String) = Triple(file.baseFile.absolutePath, process, id)
    private val cipher = cipher ?: androidSecretCipher("agent.runs.v1") {
        !file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()
    }

    override suspend fun begin(subject: RunSubject, provider: String?, model: String?): String = io {
        RunSubjectCodec.validate(subject, provider, model)
        val records = read().toMutableList()
        while (records.size >= MAX_RECORDS) {
            val oldestTerminal = records.indexOfFirst { it.state != ActionRunState.RUNNING }
            check(oldestTerminal >= 0)
            records.removeAt(oldestTerminal)
        }
        val time = now().coerceAtLeast(0)
        val id = UUID.randomUUID().toString()
        records.add(ActionRunRecord(id, subject, provider, model, time, time, ActionRunState.RUNNING, process))
        write(records)
        activeRuns.add(liveKey(id))
        id
    }

    override suspend fun finish(id: String, state: ActionRunState) {
        require(state != ActionRunState.RUNNING)
        try {
            io {
                val records = read().toMutableList()
                val index = records.indexOfFirst { it.id == id && it.owner == process && it.state == ActionRunState.RUNNING }
                // Clearing history while a request runs must not recreate its deleted record.
                if (index >= 0) {
                    val record = records[index]
                    records[index] = record.copy(state = state, updatedAt = maxOf(now(), record.startedAt))
                    write(records)
                }
            }
        } finally {
            // Metadata only, also permitted while locked. If the disk completion failed,
            // the next unlocked access reconciles this known-inactive run to UNKNOWN.
            synchronized(lock) { activeRuns.removeAll { it.second == process && it.third == id } }
        }
    }

    suspend fun records(): List<ActionRunRecord> = io {
        val records = read()
        // Persist recovery/retention even when merely opening the status view.
        if (exists()) write(records)
        records.asReversed()
    }

    suspend fun clear() = io {
        file.delete()
        check(!exists())
        activeRuns.removeAll { it.first == file.baseFile.absolutePath }
    }

    private suspend fun <T> io(operation: () -> T): T = withContext(Dispatchers.IO) {
        synchronized(lock) {
            try {
                check(Build.VERSION.SDK_INT >= 23 && unlocked())
                operation()
            } catch (_: Exception) { throw RunStoreFailure() }
        }
    }

    private fun exists() = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()

    private fun read(): List<ActionRunRecord> {
        if (!exists()) return emptyList()
        val plain = cipher.open(file.openRead().use { it.readBounded(MAX_BYTES) }, AAD)
        try {
            require(plain.size <= MAX_BYTES)
            val root = JSONObject(plain.toString(Charsets.UTF_8))
            require(root.keys().asSequence().toSet() == setOf("version", "runs"))
            val version = root.strictInt("version")
            require(version in 1..RunSubjectCodec.SCHEMA)
            val array = root.getJSONArray("runs")
            require(array.length() <= MAX_RECORDS)
            val ids = hashSetOf<String>()
            val cutoff = now().coerceAtLeast(0) - MAX_AGE_MS
            return (0 until array.length()).map { index ->
                val row = array.getJSONObject(index)
                row.requireFields(if (version == 1) LEGACY_FIELDS else FIELDS)
                val id = row.strictString("id"); val owner = row.strictString("owner")
                require(UUID.fromString(id).toString() == id && ids.add(id))
                require(UUID.fromString(owner).toString() == owner)
                val subject = if (version == 1) RunSubject.TextAction(AiTextAction.valueOf(row.strictString("action")))
                    else RunSubjectCodec.decode(row.getJSONObject("subject"), version)
                val provider = row.nullableString("provider"); val model = row.nullableString("model")
                RunSubjectCodec.validate(subject, provider, model)
                val started = row.strictLong("startedAt"); val updated = row.strictLong("updatedAt")
                require(started >= 0 && updated >= started)
                val state = ActionRunState.valueOf(row.strictString("state"))
                ActionRunRecord(id, subject, provider, model, started, updated,
                    if (state == ActionRunState.RUNNING &&
                        (owner != process || liveKey(id) !in activeRuns)) ActionRunState.UNKNOWN else state, owner)
            }.filter { it.updatedAt >= cutoff || it.state == ActionRunState.RUNNING }
        } finally { plain.fill(0) }
    }

    private fun write(records: List<ActionRunRecord>) {
        val array = JSONArray()
        records.forEach { r -> array.put(JSONObject().put("id", r.id).put("subject", RunSubjectCodec.encode(r.subject))
            .put("provider", r.provider ?: JSONObject.NULL).put("model", r.model ?: JSONObject.NULL).put("startedAt", r.startedAt)
            .put("updatedAt", r.updatedAt).put("state", r.state.name).put("owner", r.owner)) }
        val plain = JSONObject().put("version", RunSubjectCodec.SCHEMA).put("runs", array).toString().toByteArray(Charsets.UTF_8)
        val encrypted = try { require(plain.size <= MAX_BYTES - 29); cipher.seal(plain, AAD) }
            finally { plain.fill(0) }
        require(encrypted.size <= MAX_BYTES)
        check(file.baseFile.parentFile!!.isDirectory || file.baseFile.parentFile!!.mkdirs())
        val output = file.startWrite()
        try { output.write(encrypted); file.finishWrite(output) }
        catch (failure: Exception) { file.failWrite(output); throw failure }
    }

    companion object {
        const val MAX_RECORDS = 20
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
        private const val MAX_BYTES = 32_768
        // Crypto domain remains stable when the authenticated JSON schema migrates.
        private val AAD = "AgentKeyboard/ActionRuns/v1".toByteArray(Charsets.UTF_8)
        private val LEGACY_FIELDS = setOf("id", "action", "provider", "model", "startedAt", "updatedAt", "state", "owner")
        private val FIELDS = LEGACY_FIELDS - "action" + "subject"
        private val processId = UUID.randomUUID().toString()
        private val lock = Any()
        private val activeRuns = hashSetOf<Triple<String, String, String>>()
    }
}
