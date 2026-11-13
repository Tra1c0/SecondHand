// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import dev.agentkeyboard.core.EmojiMix
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.agent.commands.EmojiMixRecents
import helium314.keyboard.agent.commands.EmojiMixer
import helium314.keyboard.agent.commands.QrCommand
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
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.test.*

/** "Zuletzt gemischt" in the mixer of the emoji palette (stage 32): only cached stickers, no network. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class EmojiMixRecentsTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val images = EditorInfo().apply {
        inputType = InputType.TYPE_CLASS_TEXT; packageName = "app"; fieldId = 2
        EditorInfoCompat.setContentMimeTypes(this, arrayOf("image/png"))
    }
    private val requests = mutableListOf<Request>()
    private val inserted = mutableListOf<String>()

    private val pngBytes: ByteArray by lazy {
        val bitmap = Bitmap.createBitmap(534, 534, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private val http = OkHttpClient.Builder().followRedirects(false).addInterceptor { chain ->
        synchronized(requests) { requests += chain.request() }
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("x")
            .body(pngBytes.toResponseBody("image/png".toMediaType())).build()
    }.build()

    private lateinit var palette: LinearLayout
    private lateinit var mixer: EmojiMixPalette
    private val table get() = EmojiMixer.table(latinIME)!!
    private fun mix(a: String, b: String): EmojiMix = table.find(a, b)!!

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
        val pager = View(latinIME)
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

    /** A sticker on the device as if loaded before; recorded as mixed. */
    private fun cached(m: EmojiMix) {
        EmojiMixer.cache(latinIME).put(m, pngBytes)
        EmojiMixRecents.record(latinIME, m)
    }

    private val items: LinearLayout get() = ((mixer.recentsRow.getChildAt(1) as HorizontalScrollView).getChildAt(0) as LinearLayout)

    /** The row after the pictures were read from the device. */
    private fun shownRow(): List<String> {
        mixer.toggle()
        settle()
        settle()
        return EmojiMixRecents.shown(latinIME, table).map { it.key }
    }

    @Test fun theListKeepsTheNewestTwelveAndARepeatMovesToTheFront() {
        val partners = table.partners("😂", dev.agentkeyboard.core.EmojiMixTable.POPULAR, 14)
        assertEquals(14, partners.size)
        partners.forEach { EmojiMixRecents.record(latinIME, it) }
        val keys = EmojiMixRecents.keys(latinIME)
        assertEquals(EmojiMixRecents.MAX, keys.size)
        assertEquals(partners.takeLast(12).reversed().map { it.key }, keys, "newest first, the oldest two are gone")
        EmojiMixRecents.record(latinIME, partners[5])
        assertEquals(partners[5].key, EmojiMixRecents.keys(latinIME).first())
        assertEquals(EmojiMixRecents.MAX, EmojiMixRecents.keys(latinIME).size)
        // a damaged preference is read as far as it makes sense
        latinIME.prefs().edit().putString(EmojiMixRecents.PREF, "x,${partners[0].key},../a,ffffff_1f436").commit()
        assertEquals(listOf(partners[0].key, "ffffff_1f436"), EmojiMixRecents.keys(latinIME))
        assertEquals(null, EmojiMixRecents.mixOf(table, "ffffff_1f436"), "a code point beyond Unicode is unknown, not a crash")
    }

    @Test fun theRowShowsOnlyCachedStickersAndLoadsNothing() {
        val dogFire = mix("🐶", "🔥")
        val laugh = mix("😂", "😂")
        cached(dogFire)
        cached(laugh)
        EmojiMixRecents.record(latinIME, mix("🐱", "🔥")) // recorded, but its image is not on the device
        assertEquals(listOf(laugh.key, dogFire.key), shownRow())
        assertEquals(View.VISIBLE, mixer.recentsRow.visibility)
        assertEquals(2, items.childCount)
        assertTrue((0 until items.childCount).all { items.getChildAt(it) is ImageView }, "pictures read from the device")
        assertTrue((items.getChildAt(0) as ImageView).drawable != null)
        assertEquals(latinIME.getString(R.string.agent_emoji_mix_insert_description,
            latinIME.getString(R.string.agent_emoji_mix_pair, laugh.leftEmoji, laugh.rightEmoji)), items.getChildAt(0).contentDescription)
        assertEquals(emptyList(), requests, "the row never loads anything")
        // the cache drops a sticker (LRU or deleted): it disappears from the row with the next redraw
        assertTrue(EmojiMixer.cache(latinIME).file(laugh).delete())
        mixer.onEmoji("🐶")
        assertEquals(1, items.childCount)
        assertEquals(View.VISIBLE, mixer.recentsRow.visibility, "still shown with one emoji chosen")
        mixer.onEmoji("🌮")
        assertEquals(View.GONE, mixer.recentsRow.visibility, "a chosen pair takes the place")
        settle()
        assertEquals(emptyList(), requests)
    }

    @Test fun aTapInsertsLikeAFreshStickerAndMovesItToTheFront() {
        val dogFire = mix("🐶", "🔥")
        val laugh = mix("😂", "😂")
        cached(dogFire)
        cached(laugh)
        shownRow()
        mixer.onEmoji("🐶") // the choice starts again after inserting
        // the older sticker, second in the row
        assertTrue(items.getChildAt(1).performClick())
        assertEquals(1, inserted.size)
        assertTrue(inserted.single().startsWith("content://${latinIME.getString(R.string.clipboard_provider_authority)}/"))
        assertTrue(inserted.single().endsWith("${dogFire.key}.png"))
        assertEquals(emptyList(), mixer.selected)
        assertEquals(listOf(dogFire.key, laugh.key), EmojiMixRecents.keys(latinIME))
        assertEquals(emptyList(), requests)
    }

    @Test fun nothingIsInsertedInABlockedField() {
        cached(mix("🐶", "🔥"))
        shownRow()
        EmojiMixer.blocked = { true } // e.g. the device locked while the palette was open
        assertTrue(items.getChildAt(0).performClick())
        assertEquals(emptyList(), inserted)
        assertFalse(mixer.isActive)
    }

    @Test fun aNewMixIsRecordedButNotInIncognitoMode() {
        mixer.toggle()
        mixer.onEmoji("🐶")
        mixer.onEmoji("🔥")
        settle()
        assertEquals(1, requests.size)
        assertEquals(listOf(mix("🐶", "🔥").key), EmojiMixRecents.keys(latinIME), "a ready mix counts as mixed")
        mixer.toggle()
        // incognito: neither a new mix nor a direct record is stored
        latinIME.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, true).commit()
        load(images)
        assertFalse(EmojiMixRecents.recording())
        EmojiMixer.request(latinIME, mix("😂", "😂"))
        settle()
        assertEquals(2, requests.size)
        EmojiMixRecents.record(latinIME, mix("🐱", "🔥"))
        assertEquals(listOf(mix("🐶", "🔥").key), EmojiMixRecents.keys(latinIME))
    }

    @Test fun deletingTheStoredMixesClearsTheList() {
        cached(mix("🐶", "🔥"))
        assertEquals(1, shownRow().size)
        EmojiMixer.clearCache(latinIME) // "Gespeicherte Mischungen löschen"
        settle()
        assertEquals(emptyList(), EmojiMixRecents.keys(latinIME))
        assertNull(latinIME.prefs().getString(EmojiMixRecents.PREF, null))
        assertEquals(View.GONE, mixer.recentsRow.visibility)
    }
}
