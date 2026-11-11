// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Button
import android.widget.TextView
import dev.agentkeyboard.core.ToolId
import dev.agentkeyboard.core.Tools
import helium314.keyboard.latin.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CalendarToolLauncherTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()

    @Test fun intentCarriesOnlyThePreviewedTitleAndDescription() {
        val request = Tools.prepare(ToolId.CALENDAR_INSERT, "Dentist\nBring the referral")
        val started = mutableListOf<Intent>()
        val context = object : ContextWrapper(app) { override fun startActivity(intent: Intent) { started.add(intent) } }
        assertTrue(CalendarToolLauncher(context).launch(request))
        val intent = started.single()
        assertEquals(Intent.ACTION_INSERT, intent.action)
        assertEquals(CalendarContract.Events.CONTENT_URI, intent.data)
        assertNull(intent.component); assertNull(intent.`package`) // the user's own calendar app answers
        assertEquals(setOf(CalendarContract.Events.TITLE, CalendarContract.Events.DESCRIPTION), intent.extras!!.keySet())
        assertEquals("Dentist", intent.getStringExtra(CalendarContract.Events.TITLE))
        assertEquals("Dentist\nBring the referral", intent.getStringExtra(CalendarContract.Events.DESCRIPTION))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test fun missingOrRefusingAppIsReportedInsteadOfThrown() {
        val request = Tools.prepare(ToolId.CALENDAR_INSERT, "Dentist")
        for (failure in listOf(ActivityNotFoundException(), SecurityException())) {
            val context = object : ContextWrapper(app) { override fun startActivity(intent: Intent) { throw failure } }
            assertFalse(CalendarToolLauncher(context).launch(request))
        }
        assertFails { CalendarToolLauncher(app).launch(Tools.prepare(ToolId.TEST_COUNT, "Dentist")) }
    }

    @Test fun panelShowsTheExactPayloadBeforeOneSeparateHandOverClick() {
        val view = AgentPanelView(app)
        val chosen = mutableListOf<ToolId>(); var sent = 0
        view.onTool = { chosen.add(it) }
        view.onSendCloud = { sent++ }
        assertFalse(view.findViewById<Button>(R.id.agent_tool_calendar).isEnabled) // nothing selected yet
        view.showSource("Dentist\nBring the referral")
        view.findViewById<Button>(R.id.agent_tool_calendar).performClick()
        view.findViewById<Button>(R.id.agent_tool_test).performClick()
        assertEquals(listOf(ToolId.CALENDAR_INSERT, ToolId.TEST_COUNT), chosen)
        assertEquals(0, sent)
        val request = Tools.prepare(ToolId.CALENDAR_INSERT, "Dentist\nBring the referral")
        view.showToolConfirmation(toolApprovalSummary(app, request), true)
        val preview = view.findViewById<TextView>(R.id.agent_cloud_confirmation).text.toString()
        assertTrue(preview.contains("Dentist\nBring the referral") && preview.contains("Title: Dentist"))
        val send = view.findViewById<Button>(R.id.agent_cloud_send)
        assertEquals(app.getString(R.string.agent_tool_hand_over), send.text.toString())
        assertFalse(view.findViewById<Button>(R.id.agent_apply).isEnabled)
        send.performClick()
        assertEquals(1, sent)
        view.showToolResult("chars=7 words=1 lines=1")
        assertFalse(view.findViewById<Button>(R.id.agent_apply).isEnabled) // a report never replaces text
        assertTrue(view.findViewById<Button>(R.id.agent_copy).isEnabled)
        assertEquals(app.getString(R.string.agent_tool_test_ready), view.findViewById<TextView>(R.id.agent_status).text.toString())
    }
}
