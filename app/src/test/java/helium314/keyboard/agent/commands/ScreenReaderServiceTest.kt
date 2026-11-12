// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityNodeInfo
import helium314.keyboard.latin.R
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.xmlpull.v1.XmlPullParser
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
class ScreenReaderServiceTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val android = "http://schemas.android.com/apk/res/android"

    @After fun tearDown() = ScreenReaderService.setConnectedForTest(null)

    @Test fun configSubscribesToNoEventsAndAsksForNothingButWindowContent() {
        val parser = app.resources.getXml(R.xml.agent_screen_reader)
        while (parser.next() != XmlPullParser.START_TAG) Unit
        assertEquals("accessibility-service", parser.name)
        // no event types: the system never delivers an event and never wakes the service
        assertNull(parser.getAttributeValue(android, "accessibilityEventTypes"))
        assertEquals(0, parser.getAttributeIntValue(android, "accessibilityEventTypes", 0))
        assertNull(parser.getAttributeValue(android, "packageNames"))
        assertTrue(parser.getAttributeBooleanValue(android, "canRetrieveWindowContent", false))
        for (power in listOf("canPerformGestures", "canRequestFilterKeyEvents", "canRequestTouchExplorationMode",
            "canRequestEnhancedWebAccessibility", "canRequestFingerprintGestures", "canControlMagnification",
            "canTakeScreenshot", "isAccessibilityTool"))
            assertFalse(parser.getAttributeBooleanValue(android, power, false), power)
        // only the window list; no key filtering, touch exploration or other request flags
        assertEquals(AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS,
            parser.getAttributeIntValue(android, "accessibilityFlags", -1))
        val names = (0 until parser.attributeCount).map { parser.getAttributeName(it) }.toSet()
        assertEquals(setOf("description", "summary", "accessibilityFeedbackType", "accessibilityFlags",
            "canRetrieveWindowContent", "notificationTimeout"), names)
    }

    @Test fun serviceIsDeclaredForTheSystemOnly() {
        val info = app.packageManager.getServiceInfo(ComponentName(app, ScreenReaderService::class.java), PackageManager.GET_META_DATA)
        assertEquals(Manifest.permission.BIND_ACCESSIBILITY_SERVICE, info.permission)
        assertEquals(R.xml.agent_screen_reader, info.metaData.getInt("android.accessibilityservice"))
    }

    @Test fun withoutConnectionNothingIsRead() {
        assertFalse(ScreenReaderService.isAvailable)
        var result: List<CharSequence>? = listOf("x")
        ScreenReaderService.read { result = it }
        assertNull(result)
    }

    @Test fun collectsVisibleTextButSkipsPasswordsSensitiveNodesAndTheKeyboard() {
        fun node(text: String?, pkg: String = "com.example.chat") = AccessibilityNodeInfo.obtain().apply {
            this.text = text
            packageName = pkg
        }
        val root = node(null)
        val message = node("Schau https://example.com/a")
        val password = node("geheim.de").apply { isPassword = true }
        val nested = node("Liste")
        val inner = node("www.spiegel.de")
        shadowOf(nested).addChild(inner)
        val sensitive = node("bank.de").apply { isAccessibilityDataSensitive = true }
        val blank = node("   ")
        for (child in listOf(message, password, nested, sensitive, blank)) shadowOf(root).addChild(child)
        val keyboard = node("tastatur.de", pkg = app.packageName)

        val texts = ScreenReaderService.collectTexts(listOf(keyboard, root), app.packageName).map { it.toString() }
        assertEquals(listOf("Schau https://example.com/a", "Liste", "www.spiegel.de"), texts)
    }

    @Test fun readingStopsAtTheCharacterLimit() {
        val root = AccessibilityNodeInfo.obtain().apply { packageName = "a.b" }
        repeat(5) { shadowOf(root).addChild(AccessibilityNodeInfo.obtain().apply { text = "x".repeat(ScreenReaderService.MAX_CHARS / 3) }) }
        val texts = ScreenReaderService.collectTexts(listOf(root), app.packageName)
        assertEquals(ScreenReaderService.MAX_CHARS, texts.sumOf { it.length })
    }
}
