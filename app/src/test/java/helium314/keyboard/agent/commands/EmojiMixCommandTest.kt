// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Looper
import android.text.InputType
import android.text.Spanned
import android.text.style.ImageSpan
import android.view.inputmethod.EditorInfo
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.CommandContext
import dev.agentkeyboard.core.EmojiMix
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.agent.SearchCommand
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale
import kotlin.test.*

/** `@mix`, the strip chip and the image client of stage 25a, offline (fake HTTP, fake field). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class EmojiMixCommandTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val plain = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; packageName = "app"; fieldId = 1 }
    private val images = EditorInfo().apply {
        inputType = InputType.TYPE_CLASS_TEXT; packageName = "app"; fieldId = 2
        EditorInfoCompat.setContentMimeTypes(this, arrayOf("image/png", "image/gif"))
    }
    private val events = mutableListOf<String>()
    private val requests = mutableListOf<Request>()
    private val inserted = mutableListOf<InputContentInfoCompat>()
    private val started = mutableListOf<Intent>()
    /** What the fake server answers. */
    private var answer: (Request) -> Response = { png(it) }
    private var refreshes = 0
    /** The field's text before the cursor, as the host reports it. */
    private var before = ""

    private val host = object : KeyboardCommands.Host {
        override val context: Context get() = latinIME
        override var editorInfo: EditorInfo? = plain
        override val language = "de"
        override fun replace(command: CommandContext, replacement: String): Boolean {
            events += "replace ${command.text} -> $replacement"
            if (!before.endsWith(command.text)) return false
            before = before.dropLast(command.text.length) + replacement
            return true
        }
        override fun openCalculator(command: CommandCall) = false
        override val canOpenCalculator = false
        override fun refresh() { refreshes++ }
        override fun textBeforeCursor(length: Int): CharSequence = before.takeLast(length)
    }

    private val pngBytes: ByteArray by lazy {
        val bitmap = Bitmap.createBitmap(534, 534, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.YELLOW) }
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun response(request: Request, code: Int, body: ByteArray = ByteArray(0), type: String? = "image/png", headers: Map<String, String> = emptyMap()) =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("x")
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .body(body.toResponseBody(type?.toMediaType())).build()

    private fun png(request: Request) = response(request, 200, pngBytes)

    private val http = OkHttpClient.Builder().followRedirects(false).addInterceptor { chain ->
        val request = chain.request()
        synchronized(requests) { requests += request }
        answer(request)
    }.build()

    @Before fun setUp() {
        FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
            .let { field -> (field.get(null) as MutableMap<*, *>).let { synchronized(it) { it.clear() } } }
        latinIME.prefs().edit().clear().commit()
        load(plain)
        KeyboardCommands.registry = KeyboardCommands.BUILT_IN
        KeyboardCommands.load(latinIME)
        EmojiMixer.resetForTest()
        EmojiMixer.calls = { http }
        EmojiMixer.sink = QrCommand.ContentSink { _, content ->
            inserted += content
            events += "insert ${content.contentUri.authority}"
            true
        }
        EmojiMixer.launcher = SearchCommand.Launcher { _, intent ->
            started += intent
            events += "start ${intent.action}"
            true
        }
        EmojiMixer.cache(latinIME).clear()
        EmojiMixer.table(latinIME)
        waitFor { EmojiMixer.table(latinIME) != null }
        assertNotNull(EmojiMixer.table(latinIME))
    }

    @After fun tearDown() {
        EmojiMixer.cache(latinIME).clear()
        EmojiMixer.resetForTest()
        latinIME.prefs().edit().clear().commit()
        load(EditorInfo())
        KeyboardCommands.load(latinIME)
    }

    private fun load(editorInfo: EditorInfo) =
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(editorInfo, false, latinIME.packageName))

    private fun waitFor(millis: Long = 5_000, done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + millis
        while (!done() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    /** Lets the worker finish what it has queued and the main thread take the result. */
    private fun settle() {
        var done = false
        EmojiMixer.afterWork { done = true }
        waitFor { done }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun chips(typed: String, editor: EditorInfo): List<KeyboardCommands.Chip> {
        host.editorInfo = editor
        before = typed
        val call = KeyboardCommands.find(typed, Settings.getValues(), editor) as? CommandCall ?: return emptyList()
        return KeyboardCommands.chipsFor(call, host)
    }

    private fun strip(typed: String, editor: EditorInfo): List<KeyboardCommands.Chip> {
        host.editorInfo = editor
        before = typed
        return EmojiMixCommand.stripChips(host, typed)
    }

    private fun offer(pair: String) = latinIME.getString(R.string.agent_emoji_mix_offer, pair)
    private val dogFire = EmojiMix("1f436", "1f525", "20211115")
    private fun labels(chips: List<KeyboardCommands.Chip>) = chips.map { it.label.toString() }

    @Test fun isBuiltInAndProtectsItsArgument() {
        assertTrue(EmojiMixCommand in KeyboardCommands.BUILT_IN)
        assertTrue(KeyboardCommands.guardsTextBefore("hi @mix 🐶🔥"))
        assertTrue("@mix" in EmojiMixCommand.description(latinIME))
        // without argument only the templates (no request)
        assertEquals(listOf("😂", "🥰", "🐶", "🔥", "❤️"), labels(chips("@mix ", plain)))
        assertTrue(chips("@mix ", plain).all { it.kind == KeyboardCommands.Chip.Kind.TEMPLATE })
    }

    @Test fun chipsShowBothEmojisAndNeedNoNetwork() {
        val chip = chips("@mix 🐶🔥", plain).single()
        assertEquals(offer("🐶 + 🔥"), chip.label.toString())
        assertTrue("www.gstatic.com" in chip.description.toString())
        // reversed and with a plus or spaces: the same pair in the table's order
        assertEquals(offer("🐶 + 🐱"), chips("@mix 🐱 + 🐶", plain).single().label.toString())
        // one emoji: partners that exist, nothing requested
        val partners = chips("@mix 🐶", plain)
        assertEquals(EmojiMixCommand.PARTNERS, partners.size)
        assertTrue(partners.all { it.label.toString().contains("🐶") && it.kind == KeyboardCommands.Chip.Kind.ACTION })
        assertFalse(partners.any { it.label.toString().contains("🌮") }, "no dog-taco mix in the table")
        // a pair without entry in the table: muted, offline
        val none = chips("@mix 🐶🌮", plain).single()
        assertEquals(KeyboardCommands.Chip.Kind.MUTED, none.kind)
        assertEquals(latinIME.getString(R.string.agent_emoji_mix_none), none.label.toString())
        none.onTap()
        // text or three emojis: a usage hint
        assertEquals(KeyboardCommands.Chip.Kind.MUTED, chips("@mix hund", plain).single().kind)
        assertEquals(KeyboardCommands.Chip.Kind.MUTED, chips("@mix 🐶🔥😂", plain).single().kind)
        settle()
        assertEquals(emptyList(), requests)
        assertTrue(EmojiMixer.cache(latinIME).directory.listFiles().isNullOrEmpty())
    }

    @Test fun oneTapIsExactlyOneRequestThenTheResultInsertsAndRemovesTheCommand() {
        load(images)
        val tapped = chips("Hallo @mix 🐶🔥", images).single()
        tapped.onTap()
        tapped.onTap() // a second tap while loading does nothing
        assertEquals(KeyboardCommands.Chip.Kind.LOADING, chips("Hallo @mix 🐶🔥", images).single().kind)
        settle()
        val request = requests.single()
        assertEquals(dogFire.url, request.url.toString())
        assertEquals("GET", request.method)
        assertNull(request.header("Authorization"))
        assertNull(request.header("Cookie"))
        assertTrue(refreshes > 0, "the chips are rebuilt when the image is there")

        val result = chips("Hallo @mix 🐶🔥", images).single()
        assertEquals(KeyboardCommands.Chip.Kind.RESULT, result.kind)
        assertTrue(result.label.toString().endsWith(latinIME.getString(R.string.agent_emoji_mix_insert)))
        val spans = (result.label as Spanned).getSpans(0, result.label.length, ImageSpan::class.java)
        assertEquals(1, spans.size, "the thumbnail is in the chip")
        result.onTap()
        assertEquals(listOf("insert ${latinIME.getString(R.string.clipboard_provider_authority)}", "replace @mix 🐶🔥 -> "), events)
        assertEquals("Hallo ", before)
        val content = inserted.single()
        assertTrue(content.description.hasMimeType("image/png"))
        val decoded = latinIME.contentResolver.openInputStream(content.contentUri)!!.use { BitmapFactory.decodeStream(it) }
        assertEquals(534, decoded.width)
        // the cached image answers the next tap without the network
        EmojiMixer.resetForTest()
        EmojiMixer.calls = { http }
        EmojiMixer.sink = QrCommand.ContentSink { _, _ -> true }
        assertEquals(KeyboardCommands.Chip.Kind.ACTION, chips("@mix 🔥🐶", images).single().kind)
        settle()
        assertEquals(KeyboardCommands.Chip.Kind.RESULT, chips("@mix 🔥🐶", images).single().kind, "cached mix shows its thumbnail at once")
        assertEquals(1, requests.size)
    }

    @Test fun fieldsWithoutImagesShareAndAChangedFieldGetsNothing() {
        chips("@mix 🐶🔥", plain).single().onTap()
        settle()
        val result = chips("@mix 🐶🔥", plain).single()
        assertTrue(result.label.toString().endsWith(latinIME.getString(R.string.agent_emoji_mix_share)))
        // the text changed before the tap: nothing goes out
        before = "@mix 🐶🔥 und mehr"
        result.onTap()
        assertEquals(emptyList(), events)
        assertEquals(latinIME.getString(R.string.agent_command_expired), ShadowToast.getTextOfLatestToast())
        before = "@mix 🐶🔥"
        result.onTap()
        assertEquals(listOf("start ${Intent.ACTION_CHOOSER}", "replace @mix 🐶🔥 -> "), events)
        @Suppress("DEPRECATION")
        val send = started.single().getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals("image/png", send.type)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test fun missingAndFailedPairs() {
        answer = { response(it, 404, "<html>".toByteArray(), "text/html") }
        chips("@mix 🐶🔥", plain).single().onTap()
        settle()
        val missing = chips("@mix 🐶🔥", plain).single()
        assertEquals(KeyboardCommands.Chip.Kind.MUTED, missing.kind)
        assertEquals(latinIME.getString(R.string.agent_emoji_mix_none), missing.label.toString())
        missing.onTap() // no new request for a known missing pair
        settle()
        assertEquals(1, requests.size)

        answer = { throw IOException("offline") }
        chips("@mix 😂😂", plain).single().onTap()
        settle()
        val failed = chips("@mix 😂😂", plain).single()
        assertEquals(KeyboardCommands.Chip.Kind.MUTED, failed.kind)
        assertEquals(latinIME.getString(R.string.agent_emoji_mix_failed, "😂 + 😂"), failed.label.toString())
        // a failed load is tried again only with a new tap
        answer = { png(it) }
        failed.onTap()
        settle()
        assertEquals(3, requests.size)
        assertEquals(KeyboardCommands.Chip.Kind.RESULT, chips("@mix 😂😂", plain).single().kind)
    }

    @Test fun stripOffersTheMixOfTheLastTwoEmojisAndReplacesThem() {
        load(images)
        assertEquals(emptyList(), strip("Hallo", images))
        assertEquals(emptyList(), strip("Hallo 🐶", images))
        assertEquals(emptyList(), strip("🐶🔥 ", images), "only right at the cursor")
        assertEquals(emptyList(), strip("🐶🌮", images), "only pairs with a mix in the table")
        val tapped = strip("Na 🐶🔥", images).single()
        assertEquals(offer("🐶 + 🔥"), tapped.label.toString())
        // also with FE0F and reversed
        assertEquals(offer("❤️ + 😂"), strip("x😂❤", images).single().label.toString())
        settle()
        assertEquals(emptyList(), requests, "nothing is loaded while typing")
        strip("Na 🐶🔥", images).single().onTap()
        settle()
        assertEquals(1, requests.size)
        val result = strip("Na 🐶🔥", images).single()
        assertEquals(KeyboardCommands.Chip.Kind.RESULT, result.kind)
        result.onTap()
        assertEquals(listOf("insert ${latinIME.getString(R.string.clipboard_provider_authority)}", "replace 🐶🔥 -> "), events)
        assertEquals("Na ", before)
        // sharing keeps the typed emojis
        events.clear()
        load(plain)
        strip("Na 🐶🔥", plain).single().onTap()
        assertEquals(listOf("start ${Intent.ACTION_CHOOSER}"), events)
        assertEquals("Na 🐶🔥", before)
    }

    @Test fun stripFollowsItsSettingAndTheCommandsSwitch() {
        assertEquals(1, strip("🐶🔥", plain).size)
        EmojiMixCommand.setStripEnabled(latinIME, false)
        assertEquals(emptyList(), strip("🐶🔥", plain))
        EmojiMixCommand.setStripEnabled(latinIME, true)
        KeyboardCommands.setEnabled(latinIME, false)
        assertEquals(emptyList(), strip("🐶🔥", plain))
        KeyboardCommands.setEnabled(latinIME, true)
        assertEquals(1, strip("🐶🔥", plain).size)
    }

    @Test fun blockedFieldsGetNothing() {
        for (type in listOf(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_CLASS_NUMBER)) {
            val field = EditorInfo().apply { inputType = type; EditorInfoCompat.setContentMimeTypes(this, arrayOf("image/png")) }
            load(field)
            assertEquals(emptyList(), chips("@mix 🐶🔥", field), "input type $type")
            assertEquals(emptyList(), strip("🐶🔥", field), "input type $type")
        }
        latinIME.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, true).commit()
        load(images)
        assertEquals(emptyList(), chips("@mix 🐶🔥", images))
        assertEquals(emptyList(), strip("🐶🔥", images))
        latinIME.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, false).commit()
        load(images)
        // blocked between building and tapping (e.g. locked): no request, nothing inserted
        val tapped = strip("🐶🔥", images).single()
        EmojiMixer.blocked = { true }
        tapped.onTap()
        assertEquals(emptyList(), EmojiMixCommand.chips(host, CommandCall(0, "@mix 🐶🔥", '@', "mix", "🐶🔥")))
        settle()
        assertEquals(emptyList(), requests)
        assertFalse(EmojiMixer.deliver(latinIME, images, EmojiMixer.cache(latinIME).file(dogFire), {}, {}))
        assertEquals(emptyList(), events)
    }

    @Test fun clientRefusesOtherHostsRedirectsWrongTypesAndLargeBodies() {
        val client = EmojiMixClient(http)
        assertIs<EmojiMixClient.Result.Image>(client.fetch(dogFire.url))
        assertEquals(1, requests.size)
        for (url in listOf("http://www.gstatic.com/android/keyboard/emojikitchen/20211115/u1f436/u1f436_u1f525.png",
            "https://evil.example/android/keyboard/emojikitchen/20211115/u1f436/u1f436_u1f525.png",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20211115/u1f436/u1f436_u1f525.png#x"))
            assertEquals(EmojiMixClient.Reason.NOT_ALLOWED, (client.fetch(url) as EmojiMixClient.Result.Failed).reason, url)
        assertEquals(1, requests.size, "refused URLs are never requested")

        answer = { response(it, 302, headers = mapOf("Location" to "https://evil.example/x.png")) }
        assertEquals(EmojiMixClient.Reason.REDIRECT, (client.fetch(dogFire.url) as EmojiMixClient.Result.Failed).reason)
        assertEquals(2, requests.size, "the redirect is not followed")
        answer = { response(it, 200, pngBytes, "text/html") }
        assertEquals(EmojiMixClient.Reason.NOT_PNG, (client.fetch(dogFire.url) as EmojiMixClient.Result.Failed).reason)
        answer = { response(it, 200, "<html>".toByteArray(), "image/png") }
        assertEquals(EmojiMixClient.Reason.NOT_PNG, (client.fetch(dogFire.url) as EmojiMixClient.Result.Failed).reason)
        answer = { response(it, 200, pngBytes + ByteArray(EmojiMixClient.MAX_BYTES.toInt()), "image/png") }
        assertEquals(EmojiMixClient.Reason.TOO_LARGE, (client.fetch(dogFire.url) as EmojiMixClient.Result.Failed).reason)
        answer = { response(it, 404) }
        assertSame(EmojiMixClient.Result.Missing, client.fetch(dogFire.url))
        answer = { response(it, 500) }
        assertEquals(EmojiMixClient.Reason.SERVICE, (client.fetch(dogFire.url) as EmojiMixClient.Result.Failed).reason)
        answer = { throw java.io.InterruptedIOException("timeout") }
        assertEquals(EmojiMixClient.Reason.TIMEOUT, (client.fetch(dogFire.url) as EmojiMixClient.Result.Failed).reason)
    }

    /** The real one-commit replacement removes exactly the two typed emojis (surrogates, ZWJ, FE0F). */
    @Test fun theTwoTypedEmojisAreReplacedInOneCommit() {
        for ((text, pair) in listOf("Na 🐶🔥" to "🐶🔥", "x ❤️😂" to "❤️😂", "😮‍💨😂" to "😮‍💨😂")) {
            val field = android.widget.EditText(latinIME).apply { setText(text); setSelection(text.length) }
            var commits = 0
            val connection = object : android.view.inputmethod.BaseInputConnection(field, true) {
                override fun getEditable(): android.text.Editable = field.editableText
                override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean { commits++; return super.commitText(text, newCursorPosition) }
                override fun getExtractedText(request: android.view.inputmethod.ExtractedTextRequest?, flags: Int) =
                    android.view.inputmethod.ExtractedText().apply {
                        startOffset = 0; selectionStart = field.selectionStart; selectionEnd = field.selectionEnd; this.text = field.text
                    }
            }
            val service = org.mockito.Mockito.mock(android.inputmethodservice.InputMethodService::class.java)
            org.mockito.Mockito.`when`(service.currentInputConnection).thenReturn(connection)
            val rich = helium314.keyboard.latin.RichInputConnection(service)
            assertTrue(rich.resetCachesUponCursorMoveAndReturnSuccess(text.length, text.length, false))
            val chips = EmojiMixCommand.stripChips(host.also { it.editorInfo = plain }, text)
            assertEquals(1, chips.size, text)
            // what the strip chip hands to Host.replace is exactly the pair before the cursor
            assertEquals(dev.agentkeyboard.core.ApplyOutcome.SUBMITTED, rich.replaceTextBeforeCursorIfUnchanged(pair, "", text.length))
            assertEquals(text.dropLast(pair.length), field.text.toString())
            assertEquals(1, commits)
        }
    }
}
