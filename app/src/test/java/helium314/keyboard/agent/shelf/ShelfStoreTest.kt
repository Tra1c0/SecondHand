// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.shelf

import android.net.Uri
import android.text.InputType
import android.view.inputmethod.EditorInfo
import dev.agentkeyboard.core.clip.ScreenshotShelf
import dev.agentkeyboard.core.clip.ShelfFolder
import dev.agentkeyboard.core.clip.ShelfState
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.agent.AgentPreferences
import helium314.keyboard.agent.AgentStorageContexts
import helium314.keyboard.agent.ScreenshotChip
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.LatinIME
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
import java.io.File
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stage 102 (E12, E13, K4): the shelf's file, a restart, and the migration of the history copies made before 102. */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class ShelfStoreTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val now = 1_800_000_000_000L
    private val file get() = File(AgentStorageContexts.keyboard(latinIME).filesDir, ShelfStore.FILE_NAME)

    @Before fun setUp() {
        latinIME.prefs().edit().clear().commit()
        Shelf.resetForTest()
        Shelf.clock = { now }
        file.delete()
        File(file.parentFile, file.name + ".bad").delete()
        latinIME.prefs().edit().putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, true)
            .putBoolean(AgentPreferences.SCREENSHOT_CHIP, true).putInt(Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME, 121).commit()
        shadowOf(latinIME.application).grantPermissions(ScreenshotChip.permission())
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY,
            InputAttributes(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }, false, latinIME.packageName))
    }

    @After fun tearDown() {
        Shelf.resetForTest()
        latinIME.prefs().edit().clear().commit()
        file.delete()
    }

    private fun shots(vararg ids: Long) = ids.map { Shelf.Shot(it, Uri.parse("content://media/external/images/media/$it"), "image/png", now - (100 - it) * 1000) }

    @Test fun aRoundTripKeepsEveryFieldAndTheStrictReaderRefusesBrokenFiles() {
        val state = ShelfState(
            folders = listOf(ShelfFolder(3, "Urlaub", 1, pinned = true, auto = false, members = listOf(5, 6)), ShelfFolder(4, null, 0, false, true, listOf(7, 8, 9))),
            loose = listOf(10), fresh = listOf(11), pinnedShots = setOf(5, 10), clearedBefore = 1_000, nextFolderId = 5)
        val text = ShelfStore.encode(state, migrated = true)
        val read = assertNotNull(ShelfStore.decode(text))
        assertEquals(state, read.state)
        assertTrue(read.migrated)
        // a screenshot in two places, an unknown key, a newer version, two automatic folders, a pin of nothing, a bad name
        val broken = listOf(
            text.replace("\"loose\":[10]", "\"loose\":[5]"),
            text.replace("\"version\":1", "\"version\":1,\"extra\":1"),
            text.replace("\"version\":1", "\"version\":2"),
            text.replace("\"auto\":false", "\"auto\":true"),
            text.replace("\"pinned\":[5,10]", "\"pinned\":[5,99]"),
            text.replace("\"Urlaub\"", "\"  Urlaub\""),
            text.replace("\"nextFolderId\":5", "\"nextFolderId\":4"),
            "not json",
        )
        for (bad in broken) assertNull(ShelfStore.decode(bad), bad)
    }

    @Test fun aRestartReadsTheSameShelfAndAnUnreadableFileIsMovedAsideNotOverwritten() {
        Shelf.finder = { shots(1, 2, 3, 4) }
        Shelf.refresh(latinIME, Settings.getValues())
        val auto = assertNotNull(Shelf.state.autoFolder).id
        Shelf.apply(ScreenshotShelf.rename(Shelf.state, auto, "Belege"))
        Shelf.apply(ScreenshotShelf.togglePinShot(Shelf.state, 2))
        val before = Shelf.state
        assertTrue(file.exists())
        // a new process
        Shelf.resetForTest()
        Shelf.clock = { now }
        Shelf.finder = { shots(1, 2, 3, 4) }
        Shelf.refresh(latinIME, Settings.getValues())
        assertEquals(before, Shelf.state)
        assertEquals("Belege", Shelf.state.folder(auto)?.name)
        // a broken file: moved aside, the shelf starts empty and does not lose the broken file
        file.writeText("{\"version\":7}")
        Shelf.resetForTest()
        Shelf.finder = { shots(1) }
        Shelf.refresh(latinIME, Settings.getValues())
        assertTrue(File(file.parentFile, file.name + ".bad").exists())
        assertEquals(listOf(1L), Shelf.state.fresh)
    }

    @Test fun nothingIsDeletedWhenTheQueryFailsOrIsEmpty() {
        Shelf.finder = { shots(1, 2, 3) }
        Shelf.refresh(latinIME, Settings.getValues())
        val before = Shelf.state
        Shelf.finder = { null }
        Shelf.refresh(latinIME, Settings.getValues())
        assertEquals(before, Shelf.state)
        Shelf.finder = { emptyList() }
        Shelf.refresh(latinIME, Settings.getValues())
        assertEquals(before, Shelf.state)
    }

    @Test fun whileAFingerDragsAnswersWaitAndComeAfterwards() {
        Shelf.finder = { shots(1, 2) }
        Shelf.refresh(latinIME, Settings.getValues())
        Shelf.hold(latinIME, Settings.getValues(), true)
        Shelf.finder = { shots(1, 2, 3) }
        Shelf.refresh(latinIME, Settings.getValues())
        assertEquals(listOf(2L, 1L), Shelf.state.fresh)
        Shelf.hold(latinIME, Settings.getValues(), false)
        assertNotNull(Shelf.state.autoFolder)
    }

    @Test fun oldCopiesOfScreenshotsAreTakenOverWithoutLossOrDuplicates() {
        // E13, K4: three old copies in the history: one unchanged with its original (goes), one whose original is gone
        // (stays), one pinned whose original is found by its checksum (pinned in the shelf, the copy goes)
        val dao = assertNotNull(ClipboardDao.getInstance(latinIME))
        dao.clear()
        val originals = shots(1, 2, 3)
        dao.insertNewEntry(originals[0].addedMillis, false, "Screenshot", "a".repeat(64) + ".png", listOf("image/png"), null)
        dao.insertNewEntry(now - 500_000, false, "Screenshot", "b".repeat(64) + ".png", listOf("image/png"), null)
        dao.insertNewEntry(now - 10, true, "Screenshot", "c".repeat(64) + ".png", listOf("image/png"), null)
        dao.insertNewEntry(now - 20, false, "eigener Text", null, null, null)
        val entries = dao.getAll().filter { it.filename != null }
        val keys = entries.mapTo(HashSet()) { "${it.id}:${if (it.isPinned) it.timeStamp - 1 else it.timeStamp}" } // the pinned one has a new time
        latinIME.prefs().edit().putStringSet("agent_screenshot_history_entries", keys).commit()
        Shelf.checksumOf = { _, uri -> if (uri.lastPathSegment == "3") "c".repeat(64) else "f".repeat(64) }
        Shelf.finder = { originals }
        Shelf.refresh(latinIME, Settings.getValues())
        val left = dao.getAll()
        assertEquals(listOf("b".repeat(64) + ".png"), left.mapNotNull { it.filename }, "only the copy without original stays")
        assertTrue(left.any { it.text == "eigener Text" })
        assertTrue(3L in Shelf.state.pinnedShots)
        assertEquals(3, Shelf.state.placed().size, "every original once")
        assertFalse(latinIME.prefs().contains("agent_screenshot_history_entries"))
        // a second start changes nothing
        val before = Shelf.state
        Shelf.resetForTest()
        Shelf.clock = { now }
        Shelf.finder = { originals }
        Shelf.refresh(latinIME, Settings.getValues())
        assertEquals(before, Shelf.state)
        assertEquals(left.size, dao.getAll().size)
    }

    @Test fun switchingOffKeepsOnlyWhatIsPinned() {
        Shelf.finder = { shots(1, 2, 3, 4) }
        Shelf.refresh(latinIME, Settings.getValues())
        Shelf.apply(ScreenshotShelf.togglePinShot(Shelf.state, 3))
        Shelf.keepPinnedOnly(latinIME)
        assertEquals(setOf(3L), Shelf.state.placed())
    }
}
