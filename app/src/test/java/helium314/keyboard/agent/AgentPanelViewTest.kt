// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.TextView
import android.widget.FrameLayout
import android.view.Gravity
import dev.agentkeyboard.core.LocalTextAction
import helium314.keyboard.latin.InputView
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.getPinnedToolbarKeys
import helium314.keyboard.latin.utils.prefs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import java.io.File
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AgentPanelViewTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()

    @Test fun previewRequiresSeparateExplicitApplyAndClearErasesText() {
        val view = AgentPanelView(app)
        var applied = 0
        view.onAction = { view.showResult(it.transform("Hallo")) }
        view.onApply = { applied++ }
        assertFalse(view.findViewById<Button>(R.id.agent_apply).isEnabled)
        view.showSource("Hallo")
        assertFalse(view.findViewById<Button>(R.id.agent_apply).isEnabled)
        view.findViewById<Button>(R.id.agent_uppercase).performClick()
        assertEquals("HALLO", view.findViewById<TextView>(R.id.agent_result).text.toString())
        assertEquals(0, applied)
        view.findViewById<Button>(R.id.agent_apply).performClick()
        assertEquals(1, applied)
        view.clear()
        assertEquals(View.GONE, view.visibility)
        assertEquals("", view.findViewById<TextView>(R.id.agent_source).text.toString())
        assertEquals("", view.findViewById<TextView>(R.id.agent_result).text.toString())
        assertFalse(view.findViewById<Button>(R.id.agent_copy).isEnabled)
    }

    @Test fun panelNeverBecomesAnEditableInputTarget() {
        val view = AgentPanelView(app)
        assertFalse(view.onCheckIsTextEditor())
        assertNull(view.onCreateInputConnection(EditorInfo()))
        assertFalse(view.isFocusableInTouchMode)
        assertTrue(getPinnedToolbarKeys(app.prefs()).contains(ToolbarKey.AGENT))
    }

    @Test fun expiredContextErasesBothPreviewsAndDisablesCopyAndApply() {
        val view = AgentPanelView(app)
        view.showSource("private source")
        view.showResult("private result")
        view.showExpired()
        assertEquals("", view.findViewById<TextView>(R.id.agent_source).text.toString())
        assertEquals("", view.findViewById<TextView>(R.id.agent_result).text.toString())
        assertFalse(view.findViewById<Button>(R.id.agent_copy).isEnabled)
        assertFalse(view.findViewById<Button>(R.id.agent_apply).isEnabled)
    }

    @Test fun aiActionRequiresASeparateSendFromTheVisibleConfirmation() {
        val view = AgentPanelView(app)
        var sent = 0
        view.onPrepareCloud = { view.showCloudConfirmation("gpt-6-luna", 5) }
        view.onSendCloud = { sent++; view.showCloudBusy() }
        view.showSource("Hallo")
        assertEquals(View.GONE, view.findViewById<Button>(R.id.agent_cloud_send).visibility)
        view.findViewById<Button>(R.id.agent_cloud_correct).performClick()
        assertEquals(0, sent)
        assertTrue(view.findViewById<TextView>(R.id.agent_cloud_confirmation).text.contains("gpt-6-luna"))
        assertFalse(view.findViewById<Button>(R.id.agent_apply).isEnabled)
        view.findViewById<Button>(R.id.agent_cloud_send).performClick()
        assertEquals(1, sent)
        assertFalse(view.findViewById<Button>(R.id.agent_cloud_correct).isEnabled)
        assertEquals(View.VISIBLE, view.findViewById<Button>(R.id.agent_cloud_cancel).visibility)
        view.clear()
        assertEquals("", view.findViewById<TextView>(R.id.agent_cloud_confirmation).text.toString())
        assertEquals(View.GONE, view.findViewById<Button>(R.id.agent_cloud_send).visibility)
    }

    @Test fun visiblePanelBypassesLegacyTouchForwarding() {
        val inputView = InputView(app, null)
        val panel = AgentPanelView(app)
        panel.showSource("Hallo")
        inputView.addView(panel)
        ReflectionHelpers.setField(inputView, "mAgentPanel", panel)
        val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 2f, 2f, 0)
        try {
            // Legacy forwarders intentionally are not initialized: the panel must
            // bypass them before any access, so its controls cannot type through.
            assertFalse(inputView.onInterceptTouchEvent(event))
        } finally { event.recycle() }
    }

    @Test fun overlayLayoutBelongsToBottomKeyboardFrameInsteadOfFullScreenInputView() {
        // Read the shipped layout contract, then exercise Android's two-pass
        // FrameLayout measurement with a full-height IME root.
        val xml = app.resources.getLayout(R.layout.main_keyboard_frame)
        var panelIsChild = false
        var depth = -1
        while (xml.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (xml.eventType != org.xmlpull.v1.XmlPullParser.START_TAG) continue
            if (depth == -1) { assertEquals(AgentKeyboardFrame::class.java.name, xml.name); depth = xml.depth }
            if (xml.name == AgentPanelView::class.java.name) {
                panelIsChild = xml.depth == depth + 1
            }
        }
        xml.close()
        assertTrue(panelIsChild)
        val root = FrameLayout(app)
        val keyboardFrame = AgentKeyboardFrame(app, null)
        keyboardFrame.addView(View(app), FrameLayout.LayoutParams(360, 250))
        val panel = AgentPanelView(app).apply { showSource("Hallo") }
        keyboardFrame.addView(panel, FrameLayout.LayoutParams(-1, -1))
        panel.clear()
        panel.showSource("Hallo")
        root.addView(keyboardFrame, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 360, 800)
        assertEquals(250, panel.height)
        assertEquals(550, keyboardFrame.top)
        assertEquals(0, panel.top)
        assertEquals(360, panel.width)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, keyboardFrame.getChildAt(0).importantForAccessibility)
        panel.clear()
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, keyboardFrame.getChildAt(0).importantForAccessibility)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun renderLightAndDarkPanelPreviews() {
        val output = File("build/agent-previews").apply { mkdirs() }
        for ((name, theme) in listOf("panel-light" to android.R.style.Theme_Material_Light,
            "panel-dark" to android.R.style.Theme_Material)) {
            val view = AgentPanelView(ContextThemeWrapper(app, theme))
            view.showSource("Hallo Welt!\nEine lokale Textaktion mit Umlauten: Grüße 😀")
            view.showResult(LocalTextAction.UPPERCASE.transform("Hallo Welt!\nEine lokale Textaktion mit Umlauten: Grüße 😀"))
            val width = (360 * app.resources.displayMetrics.density).toInt()
            val height = (380 * app.resources.displayMetrics.density).toInt()
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            assertTrue(view.findViewById<Button>(R.id.agent_apply).isEnabled)
        }
    }
}
