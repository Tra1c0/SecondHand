// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.util.AtomicFile
import dev.agentkeyboard.core.*
import helium314.keyboard.agent.security.credentialAccessAllowed
import helium314.keyboard.agent.security.credentialContext
import helium314.keyboard.agent.security.readBounded
import kotlinx.serialization.json.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.CharacterCodingException
import java.security.MessageDigest
import java.util.Collections
import java.util.UUID

class WorkflowConfiguration internal constructor(val generation: String, definitions: List<WorkflowDefinition>) {
    val definitions: List<WorkflowDefinition> = Collections.unmodifiableList(definitions.toList())
    override fun toString() = "WorkflowConfiguration(count=${definitions.size})"
}

/** Opaque fingerprint of an unreadable file, never configuration content. */
class WorkflowRecovery internal constructor(internal val fingerprint: String) {
    override fun toString() = "WorkflowRecovery"
}

/** Configuration only. Loading/importing never schedules or executes a workflow. */
class WorkflowStore(context: Context, private val accessible: () -> Boolean = { credentialAccessAllowed(context) }) {
    private val storage = credentialContext(context.applicationContext)
    private val file get() = AtomicFile(File(storage.noBackupFilesDir, "agent_workflows.json"))
    private fun checkAccess() { check(runCatching(accessible).getOrDefault(false)) { "Workflow configuration unavailable" } }

    fun read(): List<WorkflowDefinition> = snapshot().definitions

    fun snapshot(): WorkflowConfiguration = synchronized(lock) {
        checkAccess()
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists())
            return@synchronized WorkflowConfiguration("absent", emptyList())
        val bytes = file.openRead().use { it.readBounded(MAX_STORAGE_BYTES) }
        val encoded = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        val root = Json.parseToJsonElement(encoded).jsonObject
        require(root.keys == setOf("storageVersion", "generation", "configuration") && root.integer("storageVersion") == 1)
        val generation = root.string("generation")
        require(UUID.fromString(generation).toString() == generation)
        WorkflowConfiguration(generation, decode(root.getValue("configuration").toString()))
    }

    /** Optimistic revisions prevent an editor from overwriting a newer import or edit. */
    fun save(definition: WorkflowDefinition, expectedRevision: Int? = null, expectedGeneration: String) = synchronized(lock) {
        val snapshot = snapshot()
        require(snapshot.generation == expectedGeneration)
        val current = snapshot.definitions
        val previous = current.singleOrNull { it.id == definition.id }
        if (previous == null) require(expectedRevision == null && definition.revision == 1)
        else require(expectedRevision == previous.revision && definition.revision == previous.revision + 1)
        write(current.filterNot { it.id == definition.id } + definition)
    }

    fun delete(id: String, expectedRevision: Int, expectedGeneration: String) = synchronized(lock) {
        val snapshot = snapshot()
        require(snapshot.generation == expectedGeneration)
        val current = snapshot.definitions
        require(current.single { it.id == id }.revision == expectedRevision)
        write(current.filterNot { it.id == id })
    }

    /** Explicit import activation only, after the UI has displayed the complete validated draft. */
    fun replaceAll(definitions: List<WorkflowDefinition>, expectedGeneration: String) = synchronized(lock) {
        require(snapshot().generation == expectedGeneration)
        write(definitions)
    }

    /** Healthy, absent, locked or inaccessible storage is never a reset candidate. */
    fun recoveryToken(): WorkflowRecovery? = synchronized(lock) {
        checkAccess()
        try { snapshot(); return@synchronized null }
        catch (failure: Exception) {
            if (failure !is IllegalArgumentException && failure !is IllegalStateException &&
                failure !is NoSuchElementException && failure !is CharacterCodingException) throw failure
        }
        checkAccess()
        val digest = MessageDigest.getInstance("SHA-256")
        file.openRead().use { input ->
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_RECOVERY_BYTES)
                digest.update(buffer, 0, count)
            }
        }
        checkAccess()
        WorkflowRecovery(digest.digest().joinToString("") { "%02x".format(it) })
    }

    /** Explicit recovery only; a repaired or replaced file invalidates the displayed approval. */
    fun resetUnreadable(expected: WorkflowRecovery) = synchronized(lock) {
        val current = requireNotNull(recoveryToken())
        require(current.fingerprint == expected.fingerprint)
        write(emptyList())
    }

    private fun write(definitions: List<WorkflowDefinition>) {
        checkAccess()
        val configuration = Json.parseToJsonElement(encode(definitions))
        val bytes = buildJsonObject {
            put("storageVersion", 1); put("generation", UUID.randomUUID().toString())
            put("configuration", configuration)
        }.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_STORAGE_BYTES)
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) }
        catch (failure: Exception) { file.failWrite(output); throw failure }
    }

    fun export(): String = encode(read())

    companion object {
        const val MAX_BYTES = 32_768
        private const val MAX_RECOVERY_BYTES = 1_048_576
        private const val MAX_STORAGE_BYTES = MAX_BYTES + 256
        const val MAX_WORKFLOWS = 10
        private val lock = Any()

        fun encode(definitions: List<WorkflowDefinition>): String {
            val frozen = definitions.toList()
            require(frozen.size <= MAX_WORKFLOWS && frozen.map { it.id }.toSet().size == frozen.size)
            val encoded = buildJsonObject {
                put("formatVersion", WorkflowDefinition.FORMAT_VERSION)
                put("kind", "agent-keyboard/workflows")
                put("workflows", buildJsonArray {
                    frozen.forEach { definition -> add(buildJsonObject {
                        put("id", definition.id); put("revision", definition.revision); put("name", definition.name)
                        put("steps", buildJsonArray {
                            definition.steps.forEach { step -> add(buildJsonObject {
                                put("id", step.id)
                                put("input", when (val input = step.input) {
                                    WorkflowInput.Selection -> "selection"
                                    is WorkflowInput.StepResult -> "step:${input.stepId}"
                                })
                                put("condition", step.condition?.let { condition -> buildJsonObject {
                                    put("predicate", condition.predicate.name)
                                    put("literal", condition.literal)
                                    put("minimumLength", condition.minimumLength)
                                    put("negated", condition.negated)
                                } } ?: JsonNull)
                                when (step) {
                                    is WorkflowStep.Local -> {
                                        put("kind", "local")
                                        put("action", BuiltinAction.entries.first { it.local == step.action }.id)
                                    }
                                    is WorkflowStep.Ai -> {
                                        put("kind", "ai")
                                        put("action", BuiltinAction.entries.first { it.ai == step.action }.id)
                                        put("provider", step.provider.id); put("model", step.model)
                                        put("language", step.language.code); put("tone", step.tone.name)
                                    }
                                }
                            }) }
                        })
                    }) }
                })
            }.toString()
            require(encoded.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
            return encoded
        }

        fun decode(encoded: String): List<WorkflowDefinition> {
            require(encoded.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
            val root = Json.parseToJsonElement(encoded).jsonObject
            require(root.keys == setOf("formatVersion", "kind", "workflows"))
            val version = root.integer("formatVersion")
            require(version in 1..WorkflowDefinition.FORMAT_VERSION)
            require(root.string("kind") == "agent-keyboard/workflows")
            val entries = root.getValue("workflows").jsonArray
            require(entries.size <= MAX_WORKFLOWS)
            val definitions = entries.map { element ->
                val row = element.jsonObject
                require(row.keys == setOf("id", "revision", "name", "steps"))
                val steps = row.getValue("steps").jsonArray
                val maxSteps = if (version <= 2) 2 else WorkflowDefinition.MAX_STEPS
                require(steps.size in 1..maxSteps)
                WorkflowDefinition(row.string("id"), row.integer("revision"), row.string("name"), steps.map { raw ->
                    val value = raw.jsonObject
                    val id = value.string("id")
                    val ref = value.string("input")
                    val input = when {
                        ref == "selection" -> WorkflowInput.Selection
                        ref.startsWith("step:") -> WorkflowInput.StepResult(ref.removePrefix("step:"))
                        else -> error("Invalid workflow input")
                    }
                    val action = requireNotNull(BuiltinAction.fromId(value.string("action")))
                    // Explicit v1 migration: there was no condition field or implicit rule.
                    val conditionFields = if (version == 1) emptySet() else setOf("condition")
                    val condition = if (version == 1) null else value.getValue("condition").let { rawCondition ->
                        if (rawCondition == JsonNull) null else {
                            val rule = rawCondition.jsonObject
                            require(rule.keys == setOf("predicate", "literal", "minimumLength", "negated"))
                            val negated = rule.getValue("negated").jsonPrimitive
                            require(!negated.isString)
                            TextCondition(TextPredicate.valueOf(rule.string("predicate")), rule.string("literal"),
                                rule.integer("minimumLength"), requireNotNull(negated.booleanOrNull))
                        }
                    }
                    when (value.string("kind")) {
                        "local" -> {
                            require(value.keys == setOf("id", "input", "kind", "action") + conditionFields)
                            WorkflowStep.Local(id, input, requireNotNull(action.local), condition)
                        }
                        "ai" -> {
                            require(value.keys == setOf("id", "input", "kind", "action", "provider", "model", "language", "tone") + conditionFields)
                            WorkflowStep.Ai(id, input, requireNotNull(action.ai),
                                WorkflowProvider.entries.single { it.id == value.string("provider") }, value.string("model"),
                                requireNotNull(TranslationLanguage.fromCode(value.string("language"))),
                                WritingTone.valueOf(value.string("tone")), condition)
                        }
                        else -> error("Invalid workflow step")
                    }
                })
            }
            require(definitions.map { it.id }.toSet().size == definitions.size)
            return definitions
        }

        private fun JsonObject.string(key: String): String {
            val value = getValue(key).jsonPrimitive
            require(value.isString)
            return value.content
        }
        private fun JsonObject.integer(key: String): Int {
            val value = getValue(key).jsonPrimitive
            require(!value.isString)
            return requireNotNull(value.intOrNull)
        }
    }
}
