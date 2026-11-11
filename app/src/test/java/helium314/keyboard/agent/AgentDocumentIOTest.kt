// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.net.Uri
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.CharacterCodingException
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AgentDocumentIOTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val uri = Uri.parse("content://agent.test.documents/config.json")

    @Test fun unicodeAndBomReadCorrectlyAndInputIsClosed() {
        var closed = false
        val input = object : ByteArrayInputStream("\uFEFF{\"label\":\"Grüße 😀\"}".toByteArray()) {
            override fun close() { closed = true; super.close() }
        }
        shadowOf(app.contentResolver).registerInputStream(uri, input)
        assertEquals("{\"label\":\"Grüße 😀\"}", AgentDocumentIO.readJson(app, uri))
        assertTrue(closed)
    }

    @Test fun oversizedOrMalformedUtf8DoesNotReachAConfigurationParser() {
        shadowOf(app.contentResolver).registerInputStream(uri, ByteArrayInputStream(ByteArray(17)))
        assertFailsWith<IllegalArgumentException> { AgentDocumentIO.readJson(app, uri, 16) }
        shadowOf(app.contentResolver).registerInputStream(uri, ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28)))
        assertFailsWith<CharacterCodingException> { AgentDocumentIO.readJson(app, uri) }
    }

    @Test fun oversizedExportIsRejectedBeforeOpeningOrTruncatingTheDocument() {
        var opened = 0
        val output = ByteArrayOutputStream()
        shadowOf(app.contentResolver).registerOutputStreamSupplier(uri) { opened++; output }
        assertFailsWith<IllegalArgumentException> { AgentDocumentIO.writeJson(app, uri, "ä".repeat(9), 16) }
        assertEquals(0, opened)
        AgentDocumentIO.writeJson(app, uri, "{\"a\":1}", 16)
        assertEquals(1, opened)
        assertEquals("{\"a\":1}", output.toString("UTF-8"))
    }

    @Test fun webAndRawFilesystemUrisAreNeverOpened() {
        for (value in listOf("https://example.invalid/config.json", "file:///private/config.json")) {
            val unsupported = Uri.parse(value)
            assertFailsWith<IllegalArgumentException> { AgentDocumentIO.readJson(app, unsupported) }
            assertFailsWith<IllegalArgumentException> { AgentDocumentIO.writeJson(app, unsupported, "{}") }
        }
    }
}
