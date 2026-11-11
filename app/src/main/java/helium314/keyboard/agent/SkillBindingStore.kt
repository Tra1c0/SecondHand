// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.util.AtomicFile
import dev.agentkeyboard.core.BuiltinAction
import dev.agentkeyboard.core.SkillBinding
import helium314.keyboard.agent.security.credentialAccessAllowed
import helium314.keyboard.agent.security.credentialContext
import helium314.keyboard.agent.security.readBounded
import kotlinx.serialization.json.*
import java.io.File

/** Configuration only. The file never contains editor data, model results or credential slots. */
class SkillBindingStore(context: Context, private val accessible: () -> Boolean = { credentialAccessAllowed(context) }) {
    private val storageContext = credentialContext(context.applicationContext)
    private val file get() = AtomicFile(File(storageContext.noBackupFilesDir, "agent_skill_keys.json"))

    private fun checkAccess() { check(runCatching(accessible).getOrDefault(false)) { "Skill configuration unavailable" } }

    fun read(): List<SkillBinding> = synchronized(lock) {
        checkAccess()
        val target = file
        if (!target.baseFile.exists() && !File(target.baseFile.path + ".bak").exists()) return@synchronized SkillBinding.defaults()
        val encoded = target.openRead().use { it.readBounded(MAX_BYTES).toString(Charsets.UTF_8) }
        decode(encoded)
    }

    fun forTyping(slot: Int): SkillBinding = runCatching { read().single { it.slot == slot } }
        .getOrElse { SkillBinding(slot, "", BuiltinAction.NONE) }

    fun write(bindings: List<SkillBinding>) = synchronized(lock) {
        checkAccess()
        validate(bindings)
        val encoded = encode(bindings).toByteArray(Charsets.UTF_8)
        require(encoded.size <= MAX_BYTES)
        val output = file.startWrite()
        try { output.write(encoded); file.finishWrite(output) }
        catch (failure: Exception) { file.failWrite(output); throw failure }
    }

    fun set(binding: SkillBinding, expected: SkillBinding? = null) = synchronized(lock) {
        val current = read()
        require(expected == null || (expected.slot == binding.slot && current.single { it.slot == binding.slot } == expected))
        write(current.map { if (it.slot == binding.slot) binding else it })
    }

    fun export(): String = encode(read())
    fun import(encoded: String) = write(decode(encoded))

    companion object {
        const val MAX_BYTES = 16_384
        private val lock = Any()
        private fun validate(bindings: List<SkillBinding>) {
            require(bindings.size == 3 && bindings.map { it.slot }.toSet() == setOf(1, 2, 3))
        }

        fun encode(bindings: List<SkillBinding>): String {
            validate(bindings)
            return buildJsonObject {
                put("formatVersion", 2)
                put("kind", "agent-keyboard/skill-keys")
                put("bindings", buildJsonArray {
                    bindings.sortedBy { it.slot }.forEach { binding -> add(buildJsonObject {
                        put("slot", binding.slot); put("label", binding.label); put("action", binding.action.id)
                        put("workflowId", binding.workflowId?.let(::JsonPrimitive) ?: JsonNull)
                    }) }
                })
            }.toString()
        }

        fun decode(encoded: String): List<SkillBinding> {
            require(encoded.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
            val root = Json.parseToJsonElement(encoded).jsonObject
            require(root.keys == setOf("formatVersion", "kind", "bindings"))
            val version = root.getValue("formatVersion").jsonPrimitive
            val format = requireNotNull(version.intOrNull)
            require(!version.isString && format in 1..2)
            val kind = root.getValue("kind").jsonPrimitive
            require(kind.isString && kind.content == "agent-keyboard/skill-keys")
            val bindings = root.getValue("bindings").jsonArray.map { element ->
                val value = element.jsonObject
                require(value.keys == setOf("slot", "label", "action") + if (format == 2) setOf("workflowId") else emptySet())
                val slot = value.getValue("slot").jsonPrimitive
                require(!slot.isString)
                val label = value.getValue("label").jsonPrimitive
                val action = value.getValue("action").jsonPrimitive
                require(label.isString && action.isString)
                val builtin = requireNotNull(BuiltinAction.fromId(action.content))
                require(format != 1 || builtin != BuiltinAction.WORKFLOW)
                val workflowId = if (format == 1) null else value.getValue("workflowId").let {
                    if (it == JsonNull) null else it.jsonPrimitive.let { id -> require(id.isString); id.content }
                }
                SkillBinding(requireNotNull(slot.intOrNull), label.content, builtin, workflowId)
            }
            validate(bindings)
            return bindings.sortedBy { it.slot }
        }
    }
}
