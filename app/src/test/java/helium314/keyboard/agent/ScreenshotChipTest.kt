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
        ScreenshotChip.onChange = null // the keyboard's own handler would add to the history itself; the tests call it
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
        ScreenshotChip.findOnly { _, _ -> queries++; shot(10_000) }
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
        ScreenshotChip.findOnly { _, _ -> shot(10_000) }
        assertNull(ScreenshotChip.current(latinIME, text, now))
    }

    @Test fun neverInPasswordOrNumberFieldsIncognitoOrLocked() {
        enable()
        ScreenshotChip.findOnly { _, _ -> shot(1_000) }
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
        ScreenshotChip.findOnly { _, _ -> shot }
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
        ScreenshotChip.findOnly { _, _ -> other }
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
        val start = android.os.SystemClock.uptimeMillis()
        ScreenshotChip.clock = { now + android.os.SystemClock.uptimeMillis() - start } // the wall clock goes on with the looper
        ScreenshotChip.scheduleExpiry(shot(ScreenshotChip.RECENT_MILLIS - 1_000), now)
        looper.idleFor(java.time.Duration.ofMillis(900))
        assertEquals(1, changes)
        looper.idleFor(java.time.Duration.ofMillis(300))
        assertEquals(2, changes)

        // the wall clock lags behind the uptime the expiry was planned with: it waits again instead of giving up
        ScreenshotChip.clock = { now }
        ScreenshotChip.scheduleExpiry(shot(ScreenshotChip.RECENT_MILLIS - 1_000), now)
        looper.idleFor(java.time.Duration.ofMillis(1_200))
        assertEquals(2, changes) // still fresh by the wall clock: no report, planned again
        ScreenshotChip.clock = { now + 1_100 }
        looper.idleFor(java.time.Duration.ofMillis(1_200))
        assertEquals(3, changes)
        ScreenshotChip.onChange = null
    }

    /** A media change, reported after the debounce; the background work runs in place unless a test set its own. */
    private fun seeChange() {
        ScreenshotChip.mediaChanged()
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ScreenshotChip.DEBOUNCE_MILLIS + 100))
    }

    /** Runs the queued background work on a thread of its own; what it posts to the main thread waits for the looper. */
    private fun runInBackground(tasks: MutableList<Runnable>) {
        val queued = tasks.toList()
        tasks.clear()
        Thread { queued.forEach { it.run() } }.apply { start(); join() }
    }

    /** The history switched on, the screenshots watched, the clock fixed at [now]. */
    private fun historyOn(): ClipboardDao {
        val dao = assertNotNull(ClipboardDao.getInstance(latinIME))
        dao.clear()
        enable()
        latinIME.prefs().edit().putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, true).commit()
        load(text)
        ScreenshotChip.clock = { now - 30_000 } // the keyboard started watching half a minute ago
        ScreenshotChip.start(latinIME)
        ScreenshotChip.clock = { now }
        return dao
    }

    @Test fun aScreenshotFromBeforeTheKeyboardWatchedNeverGoesIntoTheHistory() {
        // review build 12: taken on the lock screen while the keyboard was not running, never seen
        // by an observer, then found together with a new screenshot after the start
        val dao = historyOn()
        fun add() = ScreenshotChip.addToHistory(latinIME, Settings.getValues(), dao, now) { true }
        val unseen = ScreenshotChip.Shot(30, Uri.fromFile(image), "image/png", now - 45_000) // within the history age, before the start
        val second = File(latinIME.cacheDir, "Screenshot_new.png")
        Bitmap.createBitmap(30, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            .let { bitmap -> second.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val fresh = ScreenshotChip.Shot(31, Uri.fromFile(second), "image/png", now - 1_000)
        ScreenshotChip.finder = { _, _ -> listOf(fresh, unseen) }
        seeChange()
        assertEquals(31L, ScreenshotChip.current(latinIME, text, now)?.id)
        assertTrue(add())
        assertEquals(listOf(now - 1_000), dao.getAll().map { it.timeStamp }) // only the new one
        assertFalse(add())
    }

    @Test fun theMediaStoreIsAskedInTheBackgroundAndANewScreenshotIsReportedToTheStrip() {
        enable()
        ScreenshotChip.clock = { now }
        val tasks = mutableListOf<Runnable>()
        ScreenshotChip.background = java.util.concurrent.Executor { tasks += it }
        val mainThread = Thread.currentThread()
        val queryThreads = mutableListOf<Thread>()
        ScreenshotChip.findOnly { _, _ -> queryThreads += Thread.currentThread(); shot(1_000) }
        var changes = 0
        ScreenshotChip.onChange = { changes++ }
        val looper = shadowOf(android.os.Looper.getMainLooper())

        assertNull(ScreenshotChip.current(latinIME, text, now)) // nothing known yet, and no query on this thread
        assertNull(ScreenshotChip.current(latinIME, text, now)) // no second query while one is open
        assertTrue(queryThreads.isEmpty())
        assertEquals(2, tasks.size) // the query and one clean-up of old copies, both in the background
        runInBackground(tasks)
        assertEquals(1, queryThreads.size)
        assertTrue(queryThreads.single() !== mainThread)
        assertEquals(0, changes) // the result comes back to the main thread
        looper.idle()
        assertEquals(1, changes) // a new screenshot: the strip is told
        assertEquals(7L, ScreenshotChip.current(latinIME, text, now)?.id)
        assertTrue(tasks.isEmpty()) // no new query and no clean-up without a reason

        // a media change: debounced, queried in the background, then reported
        ScreenshotChip.findOnly { _, _ -> queryThreads += Thread.currentThread(); shot(500, id = 8) }
        repeat(3) { ScreenshotChip.mediaChanged() }
        looper.idleFor(java.time.Duration.ofMillis(ScreenshotChip.DEBOUNCE_MILLIS + 100))
        assertEquals(1, tasks.size)
        assertEquals(1, changes)
        assertEquals(7L, ScreenshotChip.current(latinIME, text, now)?.id) // the known one until the query is done
        runInBackground(tasks)
        looper.idle()
        assertEquals(2, changes)
        assertEquals(8L, ScreenshotChip.current(latinIME, text, now)?.id)
        assertTrue(queryThreads.none { it === mainThread })
        ScreenshotChip.onChange = null
    }

    @Test fun theCopyForTheHistoryIsMadeInTheBackgroundAndTheEntryOnTheMainThread() {
        val dao = historyOn()
        ScreenshotChip.findOnly { _, _ -> shot(1_000) }
        seeChange()
        val tasks = mutableListOf<Runnable>()
        ScreenshotChip.background = java.util.concurrent.Executor { tasks += it }
        val mainThread = Thread.currentThread()
        var storeThread: Thread? = null
        assertTrue(ScreenshotChip.addToHistory(latinIME, Settings.getValues(), dao, now) { storeThread = Thread.currentThread(); true })
        assertNull(storeThread) // size check, copy and checksum wait for the background
        assertEquals(0, dao.count())
        assertFalse(ScreenshotChip.addToHistory(latinIME, Settings.getValues(), dao, now) { true }) // not twice while it is made
        runInBackground(tasks)
        assertTrue(storeThread != null && storeThread !== mainThread)
        assertEquals(0, dao.count()) // the entry is made on the main thread
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, dao.count())
        assertEquals(helium314.keyboard.latin.utils.ChecksumCalculator.checksum(image) + ".png", dao.getAt(0).filename)
        dao.clear()
    }

    @Test fun aScreenshotFirstSeenWhileLockedOrInAnIncognitoFieldNeverGoesIntoTheHistory() {
        val dao = historyOn()
        // each case at its own time, so that one case does not keep out the next
        var time = now
        ScreenshotChip.clock = { time }
        fun add() = ScreenshotChip.addToHistory(latinIME, Settings.getValues(), dao, time) { true }
        fun takenAt(id: Long, millis: Long) = ScreenshotChip.Shot(id, Uri.fromFile(image), "image/png", millis)
        val keyguard = latinIME.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager

        // taken while locked: also not after unlocking, on the next change or the expiry
        shadowOf(keyguard).setIsDeviceLocked(true)
        ScreenshotChip.findOnly { _, _ -> takenAt(7, time - 500) }
        seeChange()
        shadowOf(keyguard).setIsDeviceLocked(false)
        assertFalse(add())
        time += 1_000
        seeChange()
        assertFalse(add())
        ScreenshotChip.resetForTest() // nor after a restart of the keyboard
        historyOn()
        ScreenshotChip.clock = { time }
        ScreenshotChip.findOnly { _, _ -> takenAt(7, time - 1_500) }
        seeChange()
        assertFalse(add())

        // first seen in a field without learning (counts as incognito)
        time += 10_000
        load(EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        })
        ScreenshotChip.findOnly { _, _ -> takenAt(8, time - 500) }
        seeChange()
        load(text)
        assertFalse(add())

        // no known field (none since the keyboard started): not allowed either
        time += 10_000
        load(EditorInfo())
        ScreenshotChip.findOnly { _, _ -> takenAt(9, time - 500) }
        seeChange()
        load(text)
        assertFalse(add())

        // a change while locked whose query failed: what was taken before it stays out after unlocking
        time += 10_000
        shadowOf(keyguard).setIsDeviceLocked(true)
        val lockedAt = time
        ScreenshotChip.findOnly { _, _ -> throw IllegalStateException("not readable while locked") }
        seeChange()
        shadowOf(keyguard).setIsDeviceLocked(false)
        time += 1_000
        ScreenshotChip.findOnly { _, _ -> takenAt(10, lockedAt - 500) }
        seeChange()
        assertFalse(add())

        // the screenshot found when the keyboard starts (not reported by a change) stays out too
        time += 10_000
        ScreenshotChip.resetForTest()
        historyOn()
        ScreenshotChip.clock = { time }
        ScreenshotChip.findOnly { _, _ -> takenAt(11, time - 500) }
        assertNotNull(ScreenshotChip.current(latinIME, text, time))
        assertFalse(add())
        assertEquals(0, dao.count())

        // taken later, unlocked, in a normal field and reported by a change: it goes in
        time += 10_000
        ScreenshotChip.findOnly { _, _ -> takenAt(12, time - 500) }
        seeChange()
        assertTrue(add())
        assertEquals(1, dao.count())
        dao.clear()
    }

    @Test fun onlyEntriesMadeFromScreenshotsAreRemovedAndAFailedCopyIsTriedOnceMore() {
        val dao = historyOn()
        fun add() = ScreenshotChip.addToHistory(latinIME, Settings.getValues(), dao, now) { true }
        val missing = File(latinIME.cacheDir, "Screenshot_missing.png").apply { delete() }
        ScreenshotChip.findOnly { _, _ -> ScreenshotChip.Shot(7, Uri.fromFile(missing), "image/png", now - 1_000) }
        seeChange()
        assertTrue(add()) // started, the copy fails
        assertEquals(0, dao.count())
        assertTrue(add()) // tried once more
        assertFalse(add()) // then given up
        assertEquals(0, dao.count())

        ScreenshotChip.findOnly { _, _ -> shot(1_000, id = 8) }
        seeChange()
        assertTrue(add())
        assertEquals(1, dao.count())
        val copy = File(ClipboardDao.clipFilesDir, dao.getAt(0).filename!!)
        // the user copies the same picture later: the entry moves to that time and is the user's now
        dao.addClipUri(now + 5_000, false, Uri.fromFile(image), android.content.ClipDescription("own", arrayOf("image/png")), latinIME)
        assertEquals(1, dao.count())
        assertEquals(now + 5_000, dao.getAt(0).timeStamp)
        latinIME.prefs().edit().putBoolean(AgentPreferences.SCREENSHOT_CHIP, false).commit()
        ScreenshotChip.removeFromHistory(latinIME)
        assertEquals(1, dao.count())
        assertTrue(copy.exists())
        dao.clear()
    }

    @Test fun newScreenshotGoesIntoTheKeyboardHistoryOnlyWithTheHistoryOnAndNeverIntoTheSystemClipboard() {
        val dao = assertNotNull(ClipboardDao.getInstance(latinIME))
        dao.clear()
        val system = latinIME.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val systemClip = system.primaryClip
        fun values() = Settings.getValues()
        var shot = shot(1_000)
        ScreenshotChip.findOnly { _, _ -> shot }
        enable()
        ScreenshotChip.start(latinIME)
        ScreenshotChip.clock = { now }
        seeChange() // a new screenshot, reported by the media store

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
        // (checked in the background with the copy, so the attempt itself is started)
        shot = shot(1_000, id = 8)
        seeChange()
        assertTrue(ScreenshotChip.addToHistory(latinIME, values(), dao, now) { false })
        assertEquals(1, dao.count())
        assertFalse(ScreenshotChip.addToHistory(latinIME, values(), dao, now) { true }) // decided once
        assertEquals(1, dao.count())

        // the history removes the copy with its entry, as for any image clip
        dao.deleteClipAt(0)
        assertFalse(copy.exists())

        // switching the screenshots off removes their entries and copies, pinned ones stay
        val second = File(latinIME.cacheDir, "Screenshot_second.png")
        Bitmap.createBitmap(30, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            .let { bitmap -> second.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        shot = shot(1_000, id = 9)
        seeChange()
        assertTrue(ScreenshotChip.addToHistory(latinIME, values(), dao, now) { true })
        shot = ScreenshotChip.Shot(10, Uri.fromFile(second), "image/png", now - 500)
        seeChange()
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

    @Test fun twoScreenshotsWithinTheDebounceTimeBothGoIntoTheHistory() {
        val dao = historyOn()
        fun add() = ScreenshotChip.addToHistory(latinIME, Settings.getValues(), dao, now) { true }
        val second = File(latinIME.cacheDir, "Screenshot_second.png")
        Bitmap.createBitmap(30, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            .let { bitmap -> second.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val first = ScreenshotChip.Shot(20, Uri.fromFile(image), "image/png", now - 700)
        val next = ScreenshotChip.Shot(21, Uri.fromFile(second), "image/png", now - 400)
        var shots = listOf(first)
        ScreenshotChip.finder = { _, _ -> shots }
        ScreenshotChip.mediaChanged() // the first screenshot
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ScreenshotChip.DEBOUNCE_MILLIS - 200))
        shots = listOf(next, first) // the second one, before the first change has settled: one query finds both
        seeChange()
        assertEquals(21L, ScreenshotChip.current(latinIME, text, now)?.id) // the newest is offered
        assertTrue(add())
        assertEquals(2, dao.count())
        assertEquals(setOf(now - 700, now - 400), dao.getAll().mapTo(HashSet()) { it.timeStamp })
        assertFalse(add()) // each one once

        // both stay decided over a restart: reported again, neither goes in a second time
        dao.clear()
        ScreenshotChip.resetForTest()
        historyOn()
        ScreenshotChip.finder = { _, _ -> shots }
        seeChange()
        assertFalse(add())
        assertEquals(0, dao.count())
    }

    @Test fun copiesForTheHistoryLeftByAStoppedKeyboardAreRemoved() {
        historyOn()
        ScreenshotChip.findOnly { _, _ -> null }
        val pending = File(ClipboardDao.clipFilesDir.parentFile, "agent_screenshot_pending").apply { mkdirs() }
        val wallClock = System.currentTimeMillis() // the clean-up uses the real time, like the file system
        val left = File(pending, "screenshot-5").apply { writeText("x"); setLastModified(wallClock - ScreenshotChip.KEEP_MILLIS - 60_000) }
        val inProgress = File(pending, "screenshot-6").apply { writeText("x"); setLastModified(wallClock) }
        ScreenshotChip.current(latinIME, text, now) // the first look after the start cleans up
        assertFalse(left.exists())
        assertTrue(inProgress.exists()) // a copy being made is young
        // switched off: everything goes
        latinIME.prefs().edit().putBoolean(AgentPreferences.SCREENSHOT_CHIP, false).commit()
        ScreenshotChip.current(latinIME, text, now)
        assertFalse(inProgress.exists())
    }

    @Test fun theEndOfTheKeyboardStopsTheObserverAndEverythingPosted() {
        enable()
        ScreenshotChip.clock = { now }
        var queries = 0
        ScreenshotChip.findOnly { _, _ -> queries++; null }
        val resolver = shadowOf(latinIME.application.contentResolver)
        val images = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val manager = helium314.keyboard.latin.ClipboardHistoryManager(latinIME)
        manager.onCreate()
        assertEquals(1, resolver.getContentObservers(images).size)
        ScreenshotChip.mediaChanged() // a report and an expiry are waiting
        ScreenshotChip.scheduleExpiry(shot(1_000), now)
        manager.onDestroy()
        assertTrue(resolver.getContentObservers(images).isEmpty())
        // the next keyboard sets its own handler: nothing of the old one may reach it
        var changes = 0
        ScreenshotChip.onChange = { changes++ }
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ScreenshotChip.RECENT_MILLIS + 1_000))
        assertEquals(0, queries)
        assertEquals(0, changes)
        ScreenshotChip.onChange = null
    }
}
