// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.agent.commands.EmojiMixer
import helium314.keyboard.agent.commands.QrCommand
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.MainKeyboardView
import helium314.keyboard.keyboard.emoji.EmojiPalettesView
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants
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
import java.util.Locale
import kotlin.test.*

/** The mixer mode of the emoji palette and the two halves of its bottom row (stage 25a). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class EmojiMixPaletteTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val images = EditorInfo().apply {
        inputType = InputType.TYPE_CLASS_TEXT; packageName = "app"; fieldId = 2
        EditorInfoCompat.setContentMimeTypes(this, arrayOf("image/png"))
    }
    private val requests = mutableListOf<Request>()
    private val inserted = mutableListOf<String>()
    private var answer: (Request) -> Response = { response(it, 200, pngBytes) }

    private val pngBytes: ByteArray by lazy {
        val bitmap = Bitmap.createBitmap(534, 534, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun response(request: Request, code: Int, body: ByteArray = ByteArray(0)) =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("x")
            .body(body.toResponseBody("image/png".toMediaType())).build()

    private val http = OkHttpClient.Builder().followRedirects(false).addInterceptor { chain ->
        synchronized(requests) { requests += chain.request() }
        answer(chain.request())
    }.build()

    private lateinit var palette: LinearLayout
    private lateinit var pager: View
    private lateinit var mixer: EmojiMixPalette

    @Before fun setUp() {
        FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
            .let { field -> (field.get(null) as MutableMap<*, *>).let { synchronized(it) { it.clear() } } }
        latinIME.prefs().edit().clear().commit()
        load(images)
        EmojiMixer.resetForTest()
        EmojiMixer.calls = { http }
        EmojiMixer.blocked = { KeyboardCommands.isBlocked(Settings.getValues(), images) }
        EmojiMixer.sink = QrCommand.ContentSink { _, content -> inserted += content.contentUri.toString(); true }
        EmojiMixer.cache(latinIME).clear()
        EmojiMixer.table(latinIME)
        waitFor { EmojiMixer.table(latinIME) != null }
        palette = LinearLayout(latinIME).apply { orientation = LinearLayout.VERTICAL }
        pager = View(latinIME)
        palette.addView(pager, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 600))
        mixer = EmojiMixPalette(palette, pager)
        mixer.start(images)
    }

    @After fun tearDown() {
        EmojiMixer.cache(latinIME).clear()
        EmojiMixer.resetForTest()
        latinIME.prefs().edit().clear().commit()
        load(EditorInfo())
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

    private fun settle() {
        var done = false
        EmojiMixer.afterWork { done = true }
        waitFor { done }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun measure() = palette.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)).also { mixer.beforeMeasure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)) }

    @Test fun modeTogglesTheBarAndThePagesGiveUpItsHeight() {
        assertEquals(View.GONE, mixer.bar.visibility)
        assertSame(mixer.bar, palette.getChildAt(0))
        EmojiMixPalette.toggleCurrent() // the "Emoji-Mixer" key
        assertTrue(mixer.isActive)
        assertEquals(View.VISIBLE, mixer.bar.visibility)
        assertEquals(latinIME.getString(R.string.agent_emoji_mixer_hint), mixer.status.text.toString())
        measure()
        assertTrue(mixer.bar.measuredHeight > 0)
        assertEquals(600 - mixer.bar.measuredHeight, pager.layoutParams.height)
        mixer.onEmoji("🐶")
        mixer.toggle()
        assertFalse(mixer.isActive)
        assertEquals(View.GONE, mixer.bar.visibility)
        assertEquals(600, pager.layoutParams.height)
        assertEquals(emptyList(), mixer.selected, "leaving the mode clears the choice")
        assertFalse(mixer.onEmoji("🐶"), "outside the mode emojis are typed")
    }

    @Test fun twoEmojisLoadTheMixOnceAndTheStickerInserts() {
        mixer.toggle()
        assertTrue(mixer.onEmoji("🐶"))
        assertEquals(latinIME.getString(R.string.agent_emoji_mixer_hint_second), mixer.status.text.toString())
        settle()
        assertEquals(emptyList(), requests, "one emoji loads nothing")
        assertTrue(mixer.onEmoji("🔥"))
        assertEquals(latinIME.getString(R.string.agent_emoji_mixer_loading), mixer.status.text.toString())
        settle()
        assertEquals(1, requests.size)
        assertEquals("https://www.gstatic.com/android/keyboard/emojikitchen/20211115/u1f436/u1f436_u1f525.png", requests.single().url.toString())
        assertEquals(View.VISIBLE, mixer.preview.visibility)
        assertNotNull(mixer.preview.drawable)
        // further updates ask for nothing new
        mixer.remove(1)
        mixer.onEmoji("🔥")
        settle()
        assertEquals(1, requests.size, "a loaded pair is not requested again")
        assertTrue(mixer.preview.performClick())
        assertEquals(1, inserted.size)
        assertTrue(inserted.single().startsWith("content://${latinIME.getString(R.string.clipboard_provider_authority)}/"))
        assertEquals(emptyList(), mixer.selected, "after inserting the choice starts again")
        assertEquals(View.GONE, mixer.preview.visibility)
    }

    @Test fun atMostTwoAndATapOnAChosenEmojiRemovesIt() {
        mixer.toggle()
        answer = { response(it, 404) }
        mixer.onEmoji("😂")
        mixer.onEmoji("😂")
        mixer.onEmoji("🐶") // a third replaces the second
        assertEquals(listOf("😂", "🐶"), mixer.selected)
        mixer.remove(0)
        assertEquals(listOf("🐶"), mixer.selected)
        settle()
        // each chosen pair with a mix in the table was one request (😂😂 and, if it exists, 😂🐶); 🐶 alone is none
        val table = EmojiMixer.table(latinIME)!!
        val expected = listOfNotNull(table.find("😂", "😂"), table.find("😂", "🐶")).map { it.url }
        assertTrue(expected.isNotEmpty())
        assertEquals(expected, requests.map { it.url.toString() })
    }

    @Test fun aRedrawNeverRequestsTheSameChoiceAgain() {
        mixer.toggle()
        answer = { throw java.io.IOException("offline") }
        mixer.onEmoji("🐶")
        mixer.onEmoji("🔥")
        settle()
        assertEquals(1, requests.size)
        assertEquals(latinIME.getString(R.string.agent_emoji_mixer_failed), mixer.status.text.toString())
        // the states are dropped (e.g. "Gespeicherte Mischungen löschen" in the settings); the next change redraws the mixer
        EmojiMixer.clearCache(latinIME)
        settle()
        val table = EmojiMixer.table(latinIME)!!
        assertNull(EmojiMixer.state(latinIME, table.find("🐶", "🔥")!!), "the failed state is gone")
        // another pair loads (e.g. a strip chip): every state change redraws the mixer
        val dogFire = table.find("🐶", "🔥")!!.url
        EmojiMixer.request(latinIME, table.find("😂", "😂")!!)
        settle()
        assertEquals(2, requests.size)
        assertEquals(1, requests.count { it.url.toString() == dogFire }, "a redraw does not ask again for the same choice")
        assertEquals(latinIME.getString(R.string.agent_emoji_mixer_tap_to_load), mixer.status.text.toString())
        // the tap on the status is the new request
        answer = { response(it, 200, pngBytes) }
        assertTrue(mixer.status.performClick())
        settle()
        assertEquals(3, requests.size)
        assertEquals(dogFire, requests.last().url.toString())
        assertEquals(View.VISIBLE, mixer.preview.visibility)
    }

    @Test fun inTheMixerModeATappedEmojiKeyIsChosenNotTyped() {
        val listener = org.mockito.Mockito.mock(helium314.keyboard.keyboard.KeyboardActionListener::class.java)
        val view = startPalette(listener)
        view.setKeyboardActionListener(listener) // the switcher sets it for the real palette
        val dog = org.mockito.Mockito.mock(helium314.keyboard.keyboard.Key::class.java)
        org.mockito.Mockito.`when`(dog.code).thenReturn(0x1F436)
        val heart = org.mockito.Mockito.mock(helium314.keyboard.keyboard.Key::class.java)
        org.mockito.Mockito.`when`(heart.code).thenReturn(KeyCode.MULTIPLE_CODE_POINTS)
        org.mockito.Mockito.`when`(heart.outputText).thenReturn("❤️")
        val mixer = EmojiMixPalette.current()!!
        EmojiMixPalette.toggleCurrent()
        assertTrue(mixer.isActive)
        view.onReleaseKey(dog)
        view.onReleaseKey(heart)
        assertEquals(listOf("🐶", "❤️"), mixer.selected)
        org.mockito.Mockito.verify(listener, org.mockito.Mockito.never()).onCodeInput(org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyBoolean())
        org.mockito.Mockito.verify(listener, org.mockito.Mockito.never()).onTextInput(org.mockito.ArgumentMatchers.any())
        org.mockito.Mockito.verify(listener).onReleaseKey(0x1F436, false)
        // leaving the mode clears the choice (typing outside the mode is the unchanged upstream path)
        EmojiMixPalette.toggleCurrent()
        assertFalse(mixer.isActive)
        assertEquals(emptyList(), mixer.selected)
        settle()
        // the chosen pair was the only request (if the table has a mix for it)
        assertEquals(listOfNotNull(EmojiMixer.table(latinIME)!!.find("🐶", "❤️")?.url), requests.map { it.url.toString() })
        view.stopEmojiPalettes()
    }

    @Test fun aFieldThatBecomesBlockedStopsTheMixerBeforeAnyRequest() {
        mixer.toggle()
        mixer.onEmoji("🐶")
        mixer.onEmoji("🌮")
        settle()
        val partners = (mixer.bar.getChildAt(1) as ViewGroup).getChildAt(0) as LinearLayout
        assertTrue(partners.childCount > 0)
        // e.g. the device locked while the palette was open: a partner tap requests nothing and ends the mode
        EmojiMixer.blocked = { true }
        partners.getChildAt(0).performClick()
        settle()
        assertFalse(mixer.isActive)
        assertEquals(emptyList(), requests)
    }

    @Test fun pairWithoutMixSuggestsPartnersOffline() {
        mixer.toggle()
        mixer.onEmoji("🐶")
        mixer.onEmoji("🌮")
        settle()
        assertEquals(emptyList(), requests)
        assertEquals(latinIME.getString(R.string.agent_emoji_mixer_none), mixer.status.text.toString())
        val partners = (mixer.bar.getChildAt(1) as ViewGroup).getChildAt(0) as LinearLayout
        assertEquals(EmojiMixPalette.PARTNERS, partners.childCount)
        val first = partners.getChildAt(0) as TextView
        assertTrue(first.performClick())
        assertEquals(listOf("🐶", first.text.toString()), mixer.selected)
        settle()
        assertEquals(1, requests.size, "the chosen partner is the request")
    }

    @Test fun blockedFieldsKeepTheMixerOff() {
        latinIME.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, true).commit()
        load(images)
        mixer.toggle()
        assertFalse(mixer.isActive)
        assertEquals(latinIME.getString(R.string.agent_emoji_mixer_blocked), ShadowToast.getTextOfLatestToast())
        latinIME.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, false).commit()
        load(images)
        mixer.toggle()
        assertTrue(mixer.isActive)
        // blocked while choosing (e.g. a password field): the mode ends, the emoji is typed normally
        EmojiMixer.blocked = { true }
        assertFalse(mixer.onEmoji("🐶"))
        assertFalse(mixer.isActive)
        settle()
        assertEquals(emptyList(), requests)
    }

    @Test fun thePaletteHasEqualHalvesForSearchAndMixer() = checkHalves()

    @Test fun theFlatStyleHasTheSameHalves() {
        FlatPreset(latinIME).apply()
        helium314.keyboard.keyboard.internal.KeyboardIconsSet.needsReload = true
        load(images)
        assertTrue(helium314.keyboard.keyboard.FlatStyle.isActive(Settings.getValues().mColors))
        checkHalves()
    }

    private fun startPalette(listener: helium314.keyboard.keyboard.KeyboardActionListener): EmojiPalettesView {
        val switcher = KeyboardSwitcher.getInstance()
        val input = switcher.onCreateInputView(latinIME, true)
        switcher.reloadMainKeyboard()
        val view = input.findViewById<EmojiPalettesView>(R.id.emoji_palettes_view)
        view.startEmojiPalettes(switcher.mainKeyboardView!!.keyVisualAttribute, images, listener)
        return view
    }

    private fun checkHalves() {
        val view = startPalette(latinIME.mKeyboardActionListener)
        val keyboard = view.findViewById<MainKeyboardView>(R.id.bottom_row_keyboard).keyboard!!
        val keys = keyboard.sortedKeys
        val mixerKey = keys.single { it.code == KeyCode.AGENT_EMOJI_MIXER }
        // left half: the emoji search (the bundled emoji dictionaries make it available for German), or a
        // space bar where no emoji dictionary is there to search (layout fallback, not reachable here)
        val left = keys.single { it.code == KeyCode.EMOJI_SEARCH || it.code == Constants.CODE_SPACE }
        assertEquals(KeyCode.EMOJI_SEARCH, left.code)
        assertEquals(left.width, mixerKey.width)
        assertTrue(left.x < mixerKey.x)
        assertEquals(latinIME.getString(R.string.agent_emoji_mixer_key), mixerKey.label)
        if (left.code == KeyCode.EMOJI_SEARCH) assertEquals(latinIME.getString(R.string.agent_emoji_search_key), left.label)
        // the other keys of the row stay
        assertTrue(keys.any { it.code == KeyCode.ALPHA })
        assertTrue(keys.any { it.code == KeyCode.DELETE })
        assertTrue(keys.none { it.code == Constants.CODE_SPACE && left.code == KeyCode.EMOJI_SEARCH })
        // the key toggles the mixer of this palette
        val mixer = EmojiMixPalette.current()!!
        assertFalse(mixer.isActive)
        latinIME.mKeyboardActionListener.onCodeInput(KeyCode.AGENT_EMOJI_MIXER, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
        assertTrue(mixer.isActive)
        // leaving the palette ends the mode
        view.stopEmojiPalettes()
        assertFalse(mixer.isActive)
    }
}
