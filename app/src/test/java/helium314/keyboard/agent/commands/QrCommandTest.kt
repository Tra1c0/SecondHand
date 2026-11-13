// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Looper
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.CommandContext
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.agent.AgentStorageContexts
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.agent.SearchCommand
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast
import java.io.File
import java.util.Locale
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class QrCommandTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val plain = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; packageName = "app"; fieldId = 1 }
    private val images = EditorInfo().apply {
        inputType = InputType.TYPE_CLASS_TEXT; packageName = "app"; fieldId = 2
        EditorInfoCompat.setContentMimeTypes(this, arrayOf("image/png", "image/gif"))
    }
    /** Everything that happens, in order: "insert …", "start …", "replace …". */
    private val events = mutableListOf<String>()
    private val inserted = mutableListOf<InputContentInfoCompat>()
    private val started = mutableListOf<Intent>()
    private var sinkAccepts = true
    private var launcherAccepts = true

    private val host = object : KeyboardCommands.Host {
        override val context: Context get() = latinIME
        override var editorInfo: EditorInfo? = plain
        override val language = "de"
        override fun replace(command: CommandContext, replacement: String): Boolean {
            events += "replace ${command.text} -> $replacement"
            return true
        }
        override fun openCalculator(command: CommandCall) = false
        override val canOpenCalculator = false
    }

    @Before fun setUp() {
        // FileProvider caches its roots per authority for the whole JVM, but Robolectric gives each
        // test its own data directory: forget the roots of an earlier test in this class
        FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
            .let { field -> (field.get(null) as MutableMap<*, *>).let { synchronized(it) { it.clear() } } }
        latinIME.prefs().edit().clear().commit()
        load(plain)
        KeyboardCommands.registry = KeyboardCommands.BUILT_IN
        KeyboardCommands.load(latinIME)
        QrCommand.resetForTest()
        QrCommand.sink = QrCommand.ContentSink { _, content ->
            inserted += content
            events += "insert ${content.contentUri.authority}"
            sinkAccepts
        }
        QrCommand.launcher = SearchCommand.Launcher { _, intent ->
            started += intent
            events += "start ${intent.action}"
            launcherAccepts
        }
        QrCommand.cleanUp(latinIME, 0, all = true)
    }

    @After fun tearDown() {
        QrCommand.cleanUp(latinIME, 0, all = true)
        QrCommand.resetForTest()
        latinIME.prefs().edit().clear().commit()
        load(EditorInfo())
        KeyboardCommands.registry = KeyboardCommands.BUILT_IN
        KeyboardCommands.load(latinIME)
    }

    private fun load(editorInfo: EditorInfo) =
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(editorInfo, false, latinIME.packageName))

    private fun call(typed: String, editor: EditorInfo) = KeyboardCommands.find(typed, Settings.getValues(), editor) as? CommandCall

    private fun chips(typed: String, editor: EditorInfo): List<KeyboardCommands.Chip> {
        host.editorInfo = editor
        val call = call(typed, editor) ?: return emptyList()
        return KeyboardCommands.chipsFor(call, host)
    }

    private val directory get() = File(AgentStorageContexts.keyboard(latinIME).filesDir, QrCommand.DIRECTORY)

    /** Runs the main looper until [done] or a few seconds passed: the code is made in the background. */
    private fun waitFor(millis: Long = 5_000, done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + millis
        while (!done() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun decode(bitmap: Bitmap): String {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    }

    private fun decode(uri: Uri): String {
        val bitmap = latinIME.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
        assertNotNull(bitmap)
        assertEquals(QrCommand.SIZE, bitmap.width)
        assertEquals(QrCommand.SIZE, bitmap.height)
        return decode(bitmap)
    }

    @Test fun isBuiltInProtectsItsArgumentAndDescribesItself() {
        assertTrue(QrCommand in KeyboardCommands.BUILT_IN)
        assertTrue(QrCommand.protectsArgument)
        assertTrue(KeyboardCommands.guardsTextBefore("hi @qr https://example.org/Sehr langer Text"))
        assertTrue("@qr" in QrCommand.description(latinIME))
    }

    @Test fun encodedCodesDecodeToTheSameText() {
        for (text in listOf("https://example.org/path?q=1", "WIFI:T:WPA;S:Heim-Netz;P:geheim;;", "Grüße aus Köln – 1 € ✓",
            "x".repeat(200))) {
            val bitmap = QrCommand.encode(text)
            assertEquals(QrCommand.SIZE, bitmap.width)
            assertEquals(QrCommand.SIZE, bitmap.height)
            assertEquals(-1, bitmap.getPixel(0, 0)) // white quiet zone in the corner
            assertEquals(text, decode(bitmap))
        }
    }

    @Test fun chipsDependOnTheImageTypesTheFieldTakes() {
        load(images)
        val insert = chips("@qr https://example.org", images)
        assertEquals(listOf(latinIME.getString(R.string.agent_command_qr_insert)), insert.map { it.label.toString() })
        assertTrue("https://example.org" in insert.single().description.toString())

        val any = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; EditorInfoCompat.setContentMimeTypes(this, arrayOf("image/*")) }
        assertEquals(latinIME.getString(R.string.agent_command_qr_insert), chips("@qr Hallo", any).single().label.toString())

        val gifOnly = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; EditorInfoCompat.setContentMimeTypes(this, arrayOf("image/gif")) }
        assertEquals(latinIME.getString(R.string.agent_command_qr_share), chips("@qr Hallo", gifOnly).single().label.toString())

        load(plain)
        assertEquals(latinIME.getString(R.string.agent_command_qr_share), chips("@qr Hallo", plain).single().label.toString())
        assertEquals(emptyList(), chips("@qr   ", plain)) // nothing to encode
        // building chips encodes nothing: no file before a tap
        assertTrue(directory.listFiles().isNullOrEmpty())
        assertEquals(emptyList(), events)
    }

    @Test fun tapHandsAnOwnProviderPngToTheFieldAndThenRemovesTheCommand() {
        load(images)
        val text = "https://example.org/a?b=c"
        chips("hi @qr $text", images).single().onTap()
        waitFor { events.size >= 2 }
        assertEquals(listOf("insert ${latinIME.getString(R.string.clipboard_provider_authority)}", "replace @qr $text -> "), events)
        val content = inserted.single()
        assertTrue(content.description.hasMimeType("image/png"))
        assertEquals(text, decode(content.contentUri))
        assertEquals(1, directory.listFiles()!!.size)

        // old files are removed when the next one is made
        directory.listFiles()!!.single().setLastModified(System.currentTimeMillis() - QrCommand.KEEP_MILLIS - 1)
        val chip = chips("@qr zwei", images).single()
        chip.onTap()
        chip.onTap() // a second tap on the same chip does nothing
        waitFor { events.size >= 4 }
        waitFor(300) { false }
        assertEquals(4, events.size)
        assertEquals(1, directory.listFiles()!!.size)
        assertEquals("zwei", decode(inserted.last().contentUri))
    }

    @Test fun nothingIsRemovedWhenTheFieldChangedOrRefusedTheImage() {
        load(images)
        chips("@qr eins", images).single().onTap()
        host.editorInfo = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; packageName = "app"; fieldId = 9
            EditorInfoCompat.setContentMimeTypes(this, arrayOf("image/png")) }
        waitFor(1_000) { false }
        assertEquals(emptyList(), events) // another field: nothing inserted, nothing removed

        sinkAccepts = false
        chips("@qr zwei", images).single().onTap()
        waitFor { events.isNotEmpty() }
        waitFor(300) { false }
        assertEquals(1, events.size) // handed over failed: the command stays
        assertTrue(events.single().startsWith("insert"))
        assertEquals(latinIME.getString(R.string.agent_image_not_inserted), ShadowToast.getTextOfLatestToast())

        // blocked by the time the code is ready (e.g. locked): nothing leaves the keyboard
        events.clear()
        sinkAccepts = true
        val chip = chips("@qr drei", images).single()
        QrCommand.blocked = { true }
        chip.onTap()
        waitFor(1_000) { false }
        assertEquals(emptyList(), events)
    }

    @Test fun shareOpensTheChooserWithTheGrantedPngAndThenRemovesTheCommand() {
        val text = "WIFI:T:WPA;S:Netz;P:pass;;"
        chips("@qr $text", plain).single().onTap()
        waitFor { events.size >= 2 }
        assertEquals(listOf("start ${Intent.ACTION_CHOOSER}", "replace @qr $text -> "), events)
        val chooser = started.single()
        assertTrue(chooser.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("image/png", send.type)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        @Suppress("DEPRECATION")
        val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals(latinIME.getString(R.string.clipboard_provider_authority), uri.authority)
        assertEquals(uri, send.clipData!!.getItemAt(0).uri)
        assertEquals(text, decode(uri))

        // no app to share with: a message, and the command stays
        events.clear()
        launcherAccepts = false
        chips("@qr zwei", plain).single().onTap()
        waitFor { events.isNotEmpty() }
        waitFor(300) { false }
        assertEquals(listOf("start ${Intent.ACTION_CHOOSER}"), events)
        assertEquals(latinIME.getString(R.string.agent_command_qr_none), ShadowToast.getTextOfLatestToast())
    }

    @Test fun blockedFieldsGetNoChips() {
        for (type in listOf(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_CLASS_NUMBER)) {
            val field = EditorInfo().apply { inputType = type; EditorInfoCompat.setContentMimeTypes(this, arrayOf("image/png")) }
            load(field)
            assertEquals(emptyList(), chips("@qr geheim", field), "input type $type")
        }
        latinIME.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, true).commit()
        load(images)
        assertEquals(emptyList(), chips("@qr Hallo", images))
        // even if asked directly, e.g. while the device is locked
        latinIME.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, false).commit()
        load(images)
        QrCommand.blocked = { true }
        assertEquals(emptyList(), QrCommand.chips(host, CommandCall(0, "@qr Hallo", '@', "qr", "Hallo")))
        assertTrue(KeyboardCommands.isBlocked(null, images)) // no settings before the first unlock
        assertTrue(directory.listFiles().isNullOrEmpty())
    }
}
