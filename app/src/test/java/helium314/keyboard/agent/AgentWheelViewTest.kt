// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.Button
import dev.agentkeyboard.core.LocalTextAction
import helium314.keyboard.latin.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AgentWheelViewTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()

    @Test fun openingAndClosingWheelDoNotExecuteAnAction() {
        val view = AgentWheelView(app)
        var actions = 0
        var chosen: LocalTextAction? = null
        view.onAction = { actions++; chosen = it }
        view.onClose = { view.visibility = View.GONE }
        view.visibility = View.VISIBLE
        assertEquals(0, actions)
        view.findViewById<Button>(R.id.agent_wheel_close).performClick()
        assertEquals(0, actions)
        assertEquals(View.GONE, view.visibility)
        view.visibility = View.VISIBLE
        view.findViewById<Button>(R.id.agent_wheel_uppercase).performClick()
        assertEquals(1, actions)
        assertEquals(LocalTextAction.UPPERCASE, chosen)
    }

    @Test fun radialAndCompactTargetsRemainInBoundsWithoutOverlap() {
        val density = app.resources.displayMetrics.density
        for ((w, h) in listOf(360 to 240, 280 to 180, 240 to 120, 360 to 140)) {
            val view = AgentWheelView(app)
            val width = (w * density).toInt(); val height = (h * density).toInt()
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
            val bounds = (0 until view.childCount).map {
                val child = view.getChildAt(it)
                assertTrue(child.width >= 48 * density && child.height >= 48 * density)
                Rect(child.left, child.top, child.right, child.bottom).also { rect ->
                    assertTrue(Rect(0, 0, width, height).contains(rect))
                }
            }
            bounds.forEachIndexed { i, a -> bounds.drop(i + 1).forEach { b -> assertFalse(Rect.intersects(a, b)) } }
        }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun renderWheelPreview() {
        val view = AgentWheelView(ContextThemeWrapper(app, android.R.style.Theme_Material_Light))
        view.visibility = View.VISIBLE
        view.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 360, 240)
        val bitmap = Bitmap.createBitmap(360, 240, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File("build/agent-previews").mkdirs()
        File("build/agent-previews/wheel.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
