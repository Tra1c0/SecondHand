// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.clip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stage 102 (`docs/SCREENSHOTS-KONZEPT.md`, E3–E5, E11, E12): the model of the screenshot shelf. */
class ScreenshotShelfTest {
    private val minute = 60_000L
    private val now = 100 * 24 * 60 * minute
    private val tenMinutes = 10 * minute

    private fun shot(id: Long, minutesAgo: Long = 1) = ShelfShot(id, now - minutesAgo * minute)
    private fun sync(state: ShelfState, shots: List<ShelfShot>, retention: Long? = tenMinutes, expire: Boolean = true, at: Long = now) =
        ScreenshotShelf.sync(state, shots, at, retention, expire)
    private fun items(state: ShelfState, shots: List<ShelfShot>, pinnedFirst: Boolean = true) = ScreenshotShelf.items(state, shots, pinnedFirst)
    private fun describe(items: List<ShelfItem>) = items.map {
        when (it) {
            is ShelfItem.Folder -> "F" + it.folder.id + it.shots.map { s -> s.id } + (if (it.pinned) "p" else "")
            is ShelfItem.Card -> "C" + it.shot.id + (if (it.pinned) "p" else "")
        }
    }

    @Test fun twoScreenshotsAreCardsNewestFirst() {
        val shots = listOf(shot(1, 3), shot(2, 2))
        val state = sync(ShelfState(), shots)
        assertEquals(listOf(2L, 1L), state.fresh)
        assertEquals(listOf("C2", "C1"), describe(items(state, shots)))
    }

    @Test fun theThirdBundlesAllThreeIntoTheAutomaticFolder() {
        val shots = listOf(shot(1, 3), shot(2, 2))
        var state = sync(ShelfState(), shots)
        val three = shots + shot(3, 1)
        state = sync(state, three)
        assertTrue(state.fresh.isEmpty())
        val auto = state.autoFolder!!
        assertEquals(listOf(3L, 2L, 1L), auto.members)
        assertEquals(listOf("F1[3, 2, 1]"), describe(items(state, three)))
        // three at once (taken while the keyboard did not run) bundle the same way
        assertEquals(listOf(3L, 2L, 1L), sync(ShelfState(), three).autoFolder!!.members)
    }

    @Test fun newScreenshotsGoToTheFrontOfTheAutomaticFolderAndItStaysAFolderWhenItShrinks() {
        val shots = (1L..3L).map { shot(it, 5 - it) }
        var state = sync(ShelfState(), shots)
        val four = shots + shot(4, 1)
        state = sync(state, four)
        assertEquals(listOf(4L, 3L, 2L, 1L), state.autoFolder!!.members)
        state = ScreenshotShelf.moveToLoose(state, 4)
        state = ScreenshotShelf.moveToLoose(state, 3)
        state = ScreenshotShelf.moveToLoose(state, 2)
        assertEquals(listOf(1L), state.autoFolder!!.members) // one left: still the folder
        state = ScreenshotShelf.moveToLoose(state, 1)
        assertNull(state.autoFolder) // empty: gone
        // loose ones are never bundled again; the next new ones start fresh
        val more = four + shot(5, 0) + shot(6, 0)
        state = sync(state, more)
        assertEquals(listOf(6L, 5L), state.fresh)
        assertNull(state.autoFolder)
    }

    @Test fun aRenamedOrPinnedAutomaticFolderIsTheUsersAndTakesNoNewScreenshots() {
        val shots = (1L..3L).map { shot(it, 5 - it) }
        for (change in listOf<(ShelfState, Long) -> ShelfState>(
            { s, id -> ScreenshotShelf.rename(s, id, "Urlaub") }, { s, id -> ScreenshotShelf.togglePinFolder(s, id) })) {
            var state = sync(ShelfState(), shots)
            val id = state.autoFolder!!.id
            state = change(state, id)
            assertNull(state.autoFolder)
            state = sync(state, shots + shot(9, 0))
            assertEquals(listOf(1L, 2L, 3L).reversed(), state.folder(id)!!.members)
            assertEquals(listOf(9L), state.fresh)
        }
    }

    @Test fun noDuplicatesAfterRepeatedAnswersAndARestart() {
        val shots = (1L..5L).map { shot(it, 6 - it) }
        val first = sync(ShelfState(), shots)
        val again = sync(first, shots)
        assertSame(first, again) // nothing new: the same instance
        assertEquals(5, again.placed().size)
        assertEquals(5, again.autoFolder!!.members.distinct().size)
        // a "restart" is a new process with the stored state: the same answer gives the same shelf
        assertEquals(first, sync(first.copy(), shots))
    }

    @Test fun retentionRemovesUnprotectedAndKeepsPinnedOnesInPinnedFoldersAndPinnedInUnpinnedFolders() {
        // A13, A14, A15
        val old = listOf(shot(1, 30), shot(2, 29), shot(3, 28), shot(4, 27))
        var state = sync(ShelfState(), old, retention = null) // all visible without a limit: automatic folder 1..4
        val auto = state.autoFolder!!.id
        val (withFolder, user) = ScreenshotShelf.newFolder(state, listOf(1L))
        state = ScreenshotShelf.togglePinFolder(withFolder, user!!) // pinned folder with 1
        state = ScreenshotShelf.togglePinShot(state, 3) // pinned screenshot in the unpinned automatic folder
        state = ScreenshotShelf.moveToLoose(state, 4) // loose and not pinned
        val fresh = old + shot(5, 1)
        state = sync(state, fresh) // 10 minutes: 2 and 4 are too old
        assertEquals(listOf(1L), state.folder(user)!!.members)
        assertEquals(listOf(5L, 3L), state.folder(auto)!!.members) // the new one goes into the automatic folder
        assertTrue(4L !in state.placed())
        assertTrue(2L !in state.placed())
        assertEquals(listOf("F${user}[1]p", "F${auto}[5, 3]"), describe(items(state, fresh)))
    }

    @Test fun nothingExpiresWhileTheViewIsShown() {
        val shots = listOf(shot(1, 30))
        val state = sync(ShelfState(), shots, retention = null)
        assertEquals(listOf(1L), sync(state, shots, expire = false).fresh)
        assertTrue(sync(state, shots, expire = true).fresh.isEmpty())
    }

    @Test fun oldScreenshotsOutsideTheRetentionDoNotComeIn() {
        val shots = listOf(shot(1, 60), shot(2, 1))
        assertEquals(listOf(2L), sync(ShelfState(), shots).fresh)
        assertEquals(listOf(2L, 1L), sync(ShelfState(), shots, retention = null).fresh) // unlimited: every screenshot
    }

    @Test fun deletedScreenshotsLeaveButAnEmptyAnswerDeletesNothing() {
        // A16, E12
        val shots = (1L..4L).map { shot(it, 5 - it) }
        var state = ScreenshotShelf.togglePinShot(sync(ShelfState(), shots), 2)
        val (withFolder, folder) = ScreenshotShelf.newFolder(state, listOf(1L))
        state = ScreenshotShelf.togglePinFolder(withFolder, folder!!)
        assertSame(state, sync(state, emptyList()))
        state = sync(state, listOf(shot(3, 2)))
        assertEquals(setOf(3L), state.placed())
        assertTrue(state.pinnedShots.isEmpty())
        assertTrue(state.folder(folder)!!.pinned) // the pinned folder stays, empty
        assertEquals(listOf("F${folder}[]p", "F${state.autoFolder!!.id}[3]"), describe(items(state, listOf(shot(3, 2)))))
    }

    @Test fun aFolderOfWhichNoScreenshotExistsIsNotShown() {
        val shots = (1L..3L).map { shot(it, 4 - it) }
        val state = sync(ShelfState(), shots)
        assertTrue(items(state, emptyList()).isEmpty())
    }

    @Test fun clearingRemovesUnprotectedAndKeepsScreenshotsMadeAfterwards() {
        val shots = listOf(shot(1, 3), shot(2, 2))
        var state = ScreenshotShelf.togglePinShot(sync(ShelfState(), shots), 1)
        state = ScreenshotShelf.clear(state, now)
        state = sync(state, shots)
        assertEquals(listOf(1L), state.fresh)
        val later = shots + ShelfShot(3, now / 1000 * 1000) // within the same second as the clearing
        assertEquals(listOf(3L, 1L), sync(state, later).fresh)
    }

    @Test fun keepPinnedOnlyWhenSwitchedOff() {
        val shots = (1L..4L).map { shot(it, 5 - it) }
        var state = sync(ShelfState(), shots)
        state = ScreenshotShelf.togglePinShot(state, 2)
        val (f, id) = ScreenshotShelf.newFolder(state, listOf(1L))
        state = ScreenshotShelf.keepPinnedOnly(ScreenshotShelf.togglePinFolder(f, id!!), now)
        assertEquals(setOf(1L, 2L), state.placed())
        assertNull(state.autoFolder)
    }

    @Test fun pinnedItemsComeFirstOrLastAsTheClipboardSetting() {
        val shots = listOf(shot(1, 3), shot(2, 2))
        val state = ScreenshotShelf.togglePinShot(sync(ShelfState(), shots), 1)
        assertEquals(listOf("C1p", "C2"), describe(items(state, shots, pinnedFirst = true)))
        assertEquals(listOf("C2", "C1p"), describe(items(state, shots, pinnedFirst = false)))
    }

    @Test fun dropOnAnotherCardMakesAFolderWithBothTargetFirst() {
        // A10
        val shots = listOf(shot(1, 3), shot(2, 2))
        val state = sync(ShelfState(), shots)
        val (next, folder) = ScreenshotShelf.drop(state, 2, ShelfTarget.Card(1))
        assertEquals(listOf(1L, 2L), next.folder(folder!!)!!.members)
        assertEquals(1, next.folder(folder)!!.number)
        assertFalse(next.folder(folder)!!.auto)
        assertTrue(next.fresh.isEmpty())
    }

    @Test fun dropOnAFolderMovesToItsFrontAndBetweenFolders() {
        // A8
        val shots = (1L..5L).map { shot(it, 6 - it) }
        var state = sync(ShelfState(), shots) // auto: 5 4 3 2 1
        val auto = state.autoFolder!!.id
        val (made, b) = ScreenshotShelf.newFolder(state, listOf(1L))
        state = made
        state = ScreenshotShelf.drop(state, 4, ShelfTarget.Folder(b!!)).first
        assertEquals(listOf(4L, 1L), state.folder(b)!!.members)
        assertEquals(listOf(5L, 3L, 2L), state.folder(auto)!!.members)
        assertEquals(1, state.placed().count { it == 4L })
        assertEquals(5, state.placed().size)
    }

    @Test fun dropOnTheNewFolderAreaMakesAFolderWithOnlyThatScreenshotAndNumbersAreTheSmallestFree() {
        // A9
        val shots = (1L..4L).map { shot(it, 5 - it) }
        var state = sync(ShelfState(), shots)
        val (a, first) = ScreenshotShelf.drop(state, 4, ShelfTarget.NewFolder)
        assertEquals(listOf(4L), a.folder(first!!)!!.members)
        val (b, second) = ScreenshotShelf.drop(a, 3, ShelfTarget.NewFolder)
        assertEquals(2, b.folder(second!!)!!.number)
        state = ScreenshotShelf.moveToLoose(b, 4) // folder 1 empties and goes
        val (c, third) = ScreenshotShelf.drop(state, 2, ShelfTarget.NewFolder)
        assertEquals(1, c.folder(third!!)!!.number)
    }

    @Test fun cancelledOrPointlessDropsChangeNothing() {
        // A11: the same instance comes back
        val shots = (1L..4L).map { shot(it, 5 - it) }
        val state = sync(ShelfState(), shots)
        val auto = state.autoFolder!!.id
        assertSame(state, ScreenshotShelf.drop(state, 2, ShelfTarget.None).first)
        assertSame(state, ScreenshotShelf.drop(state, 2, ShelfTarget.Folder(auto)).first) // its own folder
        assertSame(state, ScreenshotShelf.drop(state, 2, ShelfTarget.Card(2)).first) // its own card
        assertSame(state, ScreenshotShelf.drop(state, 2, ShelfTarget.Order(auto, state.folder(auto)!!.members)).first)
        assertSame(state, ScreenshotShelf.drop(state, 99, ShelfTarget.NewFolder).first) // unknown screenshot
        val loose = ScreenshotShelf.moveToLoose(state, 2)
        assertSame(loose, ScreenshotShelf.drop(loose, 2, ShelfTarget.Out).first) // already loose
    }

    @Test fun dropOutOfAFolderMakesItLooseAndAFreshCardLoose() {
        // A7
        val shots = (1L..4L).map { shot(it, 5 - it) }
        val state = sync(ShelfState(), shots)
        val out = ScreenshotShelf.drop(state, 3, ShelfTarget.Out).first
        assertEquals(listOf(3L), out.loose)
        assertEquals(listOf(4L, 2L, 1L), out.autoFolder!!.members)
        val two = sync(ShelfState(), shots.take(2))
        val placed = ScreenshotShelf.drop(two, 1, ShelfTarget.Out).first
        assertEquals(listOf(1L), placed.loose)
        // a loose one is not bundled with new ones
        assertEquals(listOf(9L, 2L), sync(placed, shots.take(2) + shot(9, 0)).fresh)
    }

    @Test fun reorderTakesOnlyThePermutationOfTheMembers() {
        // A6
        val shots = (1L..4L).map { shot(it, 5 - it) }
        val state = sync(ShelfState(), shots)
        val auto = state.autoFolder!!.id
        val sorted = ScreenshotShelf.drop(state, 1, ShelfTarget.Order(auto, listOf(1L, 4L, 3L, 2L))).first
        assertEquals(listOf(1L, 4L, 3L, 2L), sorted.folder(auto)!!.members)
        assertSame(state, ScreenshotShelf.reorder(state, auto, listOf(1L, 4L, 3L)))
        assertSame(state, ScreenshotShelf.reorder(state, auto, listOf(1L, 4L, 3L, 7L)))
        // the order survives the next answer and new screenshots go in front of it
        val next = sync(sorted, shots + shot(5, 0))
        assertEquals(listOf(5L, 1L, 4L, 3L, 2L), next.folder(auto)!!.members)
    }

    @Test fun namesAreCleanedAndRefusedWhenUnusable() {
        val shots = (1L..3L).map { shot(it, 4 - it) }
        val state = sync(ShelfState(), shots)
        val id = state.autoFolder!!.id
        assertEquals("Urlaub 2026", ScreenshotShelf.rename(state, id, "  Urlaub\n 2026 ").folder(id)!!.name)
        assertSame(state, ScreenshotShelf.rename(state, id, "   "))
        assertSame(state, ScreenshotShelf.rename(state, id, "x".repeat(41)))
        assertNotNull(ScreenshotShelf.cleanName("x".repeat(40)))
        assertNull(ScreenshotShelf.cleanName("a\u0007b"))
        assertNull(ScreenshotShelf.cleanName("a\uD83D"))
        assertEquals("😀", ScreenshotShelf.cleanName("😀"))
    }

    @Test fun pinsFollowTheScreenshotAndToggle() {
        val shots = (1L..3L).map { shot(it, 4 - it) }
        var state = sync(ShelfState(), shots)
        state = ScreenshotShelf.togglePinShot(state, 2)
        assertTrue(state.isProtected(2))
        state = ScreenshotShelf.moveToLoose(state, 2)
        assertTrue(2L in state.pinnedShots)
        state = ScreenshotShelf.togglePinShot(state, 2)
        assertFalse(state.isProtected(2))
        assertSame(state, ScreenshotShelf.togglePinShot(state, 42))
    }

    @Test fun aScreenshotTakenPrivatelyIsHiddenForGoodAndAShownOneStays() {
        // E10: a change seen in a private state hides the new screenshots it reported, never one the shelf already shows
        val shown = shot(1, 5)
        var state = sync(ShelfState(), listOf(shown))
        val private = ShelfShot(2, now - 10_000)
        val change = now - 8_000
        val taken = ScreenshotShelf.takenPrivately(state, listOf(shown, private), listOf(change))
        assertEquals(setOf(2L), taken)
        state = sync(ScreenshotShelf.hide(state, taken), listOf(shown, private))
        assertEquals(listOf(1L), state.fresh)
        assertEquals(setOf(2L), state.hidden)
        // later answers (unlocked, a normal field) never bring it in, also not with an unlimited retention
        state = sync(state, listOf(shown, private, shot(3, 0)), retention = null)
        assertEquals(listOf(3L, 1L), state.fresh)
        assertTrue(2L !in state.placed())
        // a screenshot from long before the private change is not touched, nor one the shelf knows
        assertTrue(ScreenshotShelf.takenPrivately(state, listOf(ShelfShot(4, change - 5 * minute)), listOf(change)).isEmpty())
        assertTrue(ScreenshotShelf.takenPrivately(state, listOf(shown), listOf(now)).isEmpty())
        // deleted in the gallery: forgotten
        assertTrue(sync(state, listOf(shown)).hidden.isEmpty())
        assertSame(state, ScreenshotShelf.hide(state, setOf(2L)))
    }

    @Test fun manyScreenshotsStayFast() {
        val shots = (1L..5000L).map { ShelfShot(it, now - (5000 - it) * 1000) }
        val started = System.nanoTime()
        var state = sync(ShelfState(), shots, retention = null)
        var all = shots
        repeat(20) { all = all + ShelfShot(10_000L + it, now); state = sync(state, all, retention = null, expire = false) }
        val items = items(state, all)
        val millis = (System.nanoTime() - started) / 1_000_000
        assertEquals(1, items.size)
        assertEquals(5020, state.autoFolder!!.members.size)
        assertTrue("took $millis ms", millis < 3000)
    }
}
