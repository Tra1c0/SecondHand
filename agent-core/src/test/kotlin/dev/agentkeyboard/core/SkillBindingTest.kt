// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import java.util.UUID
import kotlin.test.*

class SkillBindingTest {
    @Test fun workflowBindingRequiresExactlyOneCanonicalReference() {
        val id = "12345678-abcd-1234-abcd-1234567890ab"
        assertEquals(id, SkillBinding(1, "My workflow", BuiltinAction.WORKFLOW, id).workflowId)
        for (invalid in listOf(null, "", "../private", id.uppercase())) {
            assertFails { SkillBinding(1, "My workflow", BuiltinAction.WORKFLOW, invalid) }
        }
    }

    @Test fun builtinAndUnassignedSlotsCannotCarryHiddenWorkflowReferences() {
        for (action in BuiltinAction.entries.filter { it != BuiltinAction.WORKFLOW }) {
            assertFails { SkillBinding(1, "Label", action, UUID.randomUUID().toString()) }
        }
        assertTrue(SkillBinding.defaults().all { it.action == BuiltinAction.NONE && it.workflowId == null })
    }

}
