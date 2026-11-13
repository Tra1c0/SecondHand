// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.inputmethod.InputContentInfoCompat
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.ClipboardThumbnails
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.database.ClipboardDao
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
import java.io.File
import java.util.Locale
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w369dp-h821dp-520dpi", shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class ScreenshotChipTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val now = 1_800_000_000_000L
    private lateinit var image: File
    private val text = EditorInfo().apply {
        inputType = InputType.TYPE_CLASS_TEXT
        androidx.core.view.inputmethod.EditorInfoCompat.setContentMimeTypes(this, arrayOf("image/*"))
    }

    @Before fun setUp() {
        latinIME.prefs().edit().clear().commit()
        latinIME.getSharedPreferences(FlatPreset.BACKUP_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        ScreenshotChip.resetForTest()
        ClipboardThumbnails.clear()
        image = File(latinIME.cacheDir, "Screenshot_test.png")
        val bitmap = Bitmap.createBitmap(120, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(30, 120, 220)) }
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        load(text)
    }

    @After fun tearDown() {
        ScreenshotChip.resetForTest()
        latinIME.prefs().edit().clear().commit()
        load(EditorInfo())
    }

    private fun load(editorInfo: EditorInfo) {
        KeyboardIconsSet.needsReload = true
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(editorInfo, false, latinIME.packageName))
        KeyboardIconsSet.instance.loadIcons(ContextThemeWrapper(latinIME, R.style.KeyboardTheme_Flat))
    }

    private fun shot(ageMillis: Long, id: Long = 7) = ScreenshotChip.Shot(id, Uri.fromFile(image), "image/png", now - ageMillis)

    private fun enable() {
        latinIME.prefs().edit().putBoolean(AgentPreferences.SCREENSHOT_CHIP, true).commit()
        shadowOf(latinIME.application).grantPermissions(ScreenshotChip.permission())
    }

    @Test fun offersOnlyAFreshScreenshotWithSwitchAndPermission() {
        var queries = 0
        ScreenshotChip.finder = { _, _ -> queries++; shot(10_000) }
        assertNull(ScreenshotChip.current(latinIME, text, now)) // off by default
        latinIME.prefs().edit().putBoolean(AgentPreferences.SCREENSHOT_CHIP, true).commit()
        assertNull(ScreenshotChip.current(latinIME, text, now)) // no permission yet
        assertEquals(0, queries) // nothing is read without both
        enable()
        assertEquals(7L, ScreenshotChip.current(latinIME, text, now)?.id)
        assertNull(ScreenshotChip.current(latinIME, text, now + ScreenshotChip.RECENT_MILLIS)) // too old by then
        assertEquals(1, queries) // the result is kept until the media store changes

        val plain = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
        assertNull(ScreenshotChip.current(latinIME, plain, now)) // a field that takes no images gets no chip

        ScreenshotChip.dismiss(latinIME, shot(10_000))
        assertNull(ScreenshotChip.current(latinIME, text, now)) // a used or closed screenshot does not return
        ScreenshotChip.resetForTest() // also after a restart of the keyboard
        ScreenshotChip.finder = { _, _ -> shot(10_000) }
        assertNull(ScreenshotChip.current(latinIME, text, now))
    }

    @Test fun neverInPasswordOrNumberFieldsIncognitoOrLocked() {
        enable()
        ScreenshotChip.finder = { _, _ -> shot(1_000) }
        assertNotNull(ScreenshotChip.current(latinIME, text, now))
        for (type in listOf(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_CLASS_NUMBER)) {
            val field = EditorInfo().apply { inputType = type }
            load(field)
            assertNull(ScreenshotChip.current(latinIME, field, now), "input type $type")
        }
        latinIME.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, true).commit()
        load(text)
        assertNull(ScreenshotChip.current(latinIME, text, now))
        assertTrue(ScreenshotChip.isBlocked(null, text)) // no settings, e.g. while locked before the first unlock
    }

    @Test fun tapInsertsACopyThatTheEditorMayReadAndClosesTheChip() {
        enable()
        FlatPreset(latinIME).apply()
        load(text)
        val shot = shot(1_000)
        ScreenshotChip.finder = { _, _ -> shot }
        var field: EditorInfo = text
        val parent = LinearLayout(ContextThemeWrapper(latinIME, R.style.KeyboardTheme_Flat))
        var done = 0
        val inserted = mutableListOf<InputContentInfoCompat>()
        val chip = assertNotNull(ScreenshotChip.createView(latinIME, parent, shot, { done++ }, { inserted += it }, { field })) as ViewGroup
        val label = chip.findViewById<TextView>(R.id.clipboard_suggestion_text)
        assertEquals("Screenshot", label.text.toString())
        assertEquals(View.VISIBLE, chip.findViewById<View>(R.id.clipboard_suggestion_image).visibility)

        parent.addView(chip)
        parent.measure(View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(130, View.MeasureSpec.EXACTLY))
        parent.layout(0, 0, 1200, 130)
        val picture = Bitmap.createBitmap(1200, 130, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        parent.draw(Canvas(picture))
        File("build/reports/flat-style").apply { mkdirs() }.resolve("screenshot-chip.png").outputStream().use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }

        label.performClick()
        assertEquals(1, done)
        val deadline = System.currentTimeMillis() + 5_000 // the copy is made in the background
        while (inserted.isEmpty() && System.currentTimeMillis() < deadline) { Thread.sleep(10); shadowOf(android.os.Looper.getMainLooper()).idle() }
        val content = inserted.single()
        assertEquals(latinIME.getString(R.string.clipboard_provider_authority), content.contentUri.authority)
        assertTrue(content.description.hasMimeType("image/png"))
        val copy = latinIME.contentResolver.openInputStream(content.contentUri)!!.use { it.readBytes() }
        assertContentEquals(image.readBytes(), copy)
        assertNull(ScreenshotChip.current(latinIME, text, now)) // used once, then gone
        val copies = File(AgentStorageContexts.keyboard(latinIME).filesDir, "agent_screenshot")
        assertEquals(1, copies.listFiles()!!.size)
        copies.listFiles()!!.single().setLastModified(System.currentTimeMillis() - ScreenshotChip.KEEP_MILLIS - 1)
        ScreenshotChip.current(latinIME, text, now)
        assertEquals(0, copies.listFiles()!!.size) // old copies are removed

        // a long press only closes it (Flat style), nothing is inserted
        ScreenshotChip.resetForTest()
        enable()
        val other = shot(1_000, id = 8)
        ScreenshotChip.finder = { _, _ -> other }
        val second = ScreenshotChip.createView(latinIME, parent, other, { done++ }, { inserted += it }, { field }) as ViewGroup
        assertTrue(second.findViewById<TextView>(R.id.clipboard_suggestion_text).performLongClick())
        assertEquals(2, done)
        assertEquals(1, inserted.size)
        assertNull(ScreenshotChip.current(latinIME, text, now))

        // the field changed while the copy was made: nothing is inserted anywhere
        val third = shot(1_000, id = 9)
        val chip3 = ScreenshotChip.createView(latinIME, parent, third, { done++ }, { inserted += it }, { field }) as ViewGroup
        chip3.findViewById<TextView>(R.id.clipboard_suggestion_text).performClick()
        field = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; fieldId = 42 }
        val wait = System.currentTimeMillis() + 1_000
        while (System.currentTimeMillis() < wait) { Thread.sleep(10); shadowOf(android.os.Looper.getMainLooper()).idle() }
        assertEquals(1, inserted.size)
    }

    @Test fun mediaChangesAreReportedOnceAfterTheyHaveSettled() {
        var changes = 0
        ScreenshotChip.onChange = { changes++ }
        val looper = shadowOf(android.os.Looper.getMainLooper())
        repeat(4) { ScreenshotChip.mediaChanged() } // insert, then updates while the file is written
        looper.idleFor(java.time.Duration.ofMillis(ScreenshotChip.DEBOUNCE_MILLIS - 100))
        assertEquals(0, changes)
        ScreenshotChip.mediaChanged()
        looper.idleFor(java.time.Duration.ofMillis(ScreenshotChip.DEBOUNCE_MILLIS - 100))
        assertEquals(0, changes) // each change starts the wait again
        looper.idleFor(java.time.Duration.ofMillis(200))
        assertEquals(1, changes)
        // an offered screenshot reports itself again when it gets too old
        ScreenshotChip.scheduleExpiry(shot(ScreenshotChip.RECENT_MILLIS - 1_000), now)
        looper.idleFor(java.time.Duration.ofMillis(900))
        assertEquals(1, changes)
        looper.idleFor(java.time.Duration.ofMillis(300))
        assertEquals(2, changes)
        ScreenshotChip.onChange = null
    }

    @Test fun newScreenshotGoesIntoTheKeyboardHistoryOnlyWithTheHistoryOnAndNeverIntoTheSystemClipboard() {
        val dao = assertNotNull(ClipboardDao.getInstance(latinIME))
        dao.clear()
        val system = latinIME.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val systemClip = system.primaryClip
        fun values() = Settings.getValues()
        var shot = shot(1_000)
        ScreenshotChip.finder = { _, _ -> shot }
        enable()

        assertFalse(ScreenshotChip.addToHistory(latinIME, values(), dao, now) { true }) // history is off by default
        assertEquals(0, dao.count())
        latinIME.prefs().edit().putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, true).commit()
        latinIME.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, true).commit()
        load(text)
        assertFalse(ScreenshotChip.addToHistory(latinIME, values(), dao, now) { true }) // not in incognito mode
        latinIME.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, false).commit()
        load(text)
        val keyguard = latinIME.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        shadowOf(keyguard).setIsDeviceLocked(true)
        assertFalse(ScreenshotChip.addToHistory(latinIME, values(), dao, now) { true }) // not while locked
        shadowOf(keyguard).setIsDeviceLocked(false)
        assertEquals(0, dao.count())

        assertTrue(ScreenshotChip.addToHistory(latinIME, values(), dao, now) { true })
        val entry = dao.getAt(0)
        assertEquals(listOf("image/png"), entry.mimeTypes)
        assertEquals(shot.addedMillis, entry.timeStamp)
        val copy = File(ClipboardDao.clipFilesDir, entry.filename!!)
        assertEquals("clipboard", copy.parentFile!!.name)
        // a private copy in the history's own folder, which the clipboard provider serves like any image clip
        // (no FileProvider call here: it keeps the paths of the first test app for later tests)
        assertContentEquals(image.readBytes(), copy.readBytes())
        assertEquals(systemClip?.description?.label, system.primaryClip?.description?.label)
        assertEquals(systemClip?.itemCount, system.primaryClip?.itemCount) // the system clipboard stays as it was
        assertFalse(ScreenshotChip.addToHistory(latinIME, values(), dao, now) { true }) // each screenshot once
        assertEquals(1, dao.count())

        // the history's own rules: no files allowed or too large, nothing is stored
        shot = shot(1_000, id = 8)
        ScreenshotChip.mediaChanged()
        assertFalse(ScreenshotChip.addToHistory(latinIME, values(), dao, now) { false })
        assertEquals(1, dao.count())

        // the history removes the copy with its entry, as for any image clip
        dao.deleteClipAt(0)
        assertFalse(copy.exists())

        // switching the screenshots off removes their entries and copies, pinned ones stay
        val second = File(latinIME.cacheDir, "Screenshot_second.png")
        Bitmap.createBitmap(30, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            .let { bitmap -> second.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        shot = shot(1_000, id = 9)
        ScreenshotChip.mediaChanged()
        assertTrue(ScreenshotChip.addToHistory(latinIME, values(), dao, now) { true })
        shot = ScreenshotChip.Shot(10, Uri.fromFile(second), "image/png", now - 500)
        ScreenshotChip.mediaChanged()
        assertTrue(ScreenshotChip.addToHistory(latinIME, values(), dao, now) { true })
        dao.addClip(now, false, "own text")
        assertEquals(3, dao.count())
        val pinned = dao.getAll().single { it.timeStamp == now - 500 }
        dao.togglePinned(pinned.id)
        val unpinnedCopy = File(ClipboardDao.clipFilesDir, dao.getAll().single { it.filename != null && !it.isPinned }.filename!!)
        latinIME.prefs().edit().putBoolean(AgentPreferences.SCREENSHOT_CHIP, false).commit()
        ScreenshotChip.removeFromHistory(latinIME)
        assertEquals(2, dao.count())
        assertTrue(dao.getAll().any { it.text == "own text" }) // other entries are left alone
        assertFalse(unpinnedCopy.exists())
        assertTrue(dao.getAll().single { it.filename != null }.isPinned)
        dao.clear()
    }

    @Test fun sameFieldMeansSameAppFieldAndType() {
        val a = EditorInfo().apply { packageName = "app"; fieldId = 1; inputType = InputType.TYPE_CLASS_TEXT }
        assertTrue(ScreenshotChip.isSameField(a, EditorInfo().apply { packageName = "app"; fieldId = 1; inputType = InputType.TYPE_CLASS_TEXT }))
        assertTrue(!ScreenshotChip.isSameField(a, EditorInfo().apply { packageName = "other"; fieldId = 1; inputType = InputType.TYPE_CLASS_TEXT }))
        assertTrue(!ScreenshotChip.isSameField(a, EditorInfo().apply { packageName = "app"; fieldId = 2; inputType = InputType.TYPE_CLASS_TEXT }))
        assertTrue(!ScreenshotChip.isSameField(a, null))
    }
}
