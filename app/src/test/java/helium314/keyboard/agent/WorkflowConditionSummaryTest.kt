// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import dev.agentkeyboard.core.TextCondition
import dev.agentkeyboard.core.TextPredicate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkflowConditionSummaryTest {
    @Test fun approvalShowsLineSeparatorsAndQuotesAsLiteralData() {
        val condition = TextCondition(TextPredicate.CONTAINS_LITERAL, "first\u2028second\u2029third\n\t\\\"")
        val summary = condition.summary(RuntimeEnvironment.getApplication())
        for (escaped in listOf("\\u2028", "\\u2029", "\\n", "\\t", "\\\\", "\\\"")) assertContains(summary, escaped)
        assertTrue(summary.none { it == '\u2028' || it == '\u2029' || it == '\n' || it == '\t' })
    }
}
