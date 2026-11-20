// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Stage 87b: the status lines of the gestures page (computed from the preferences with the listener's own functions) and the
 * card "Cursor-Trackpad" of the diagnose page. The decisions of the listener itself are tested in InputLogicTest (cursor87b...).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "de-w411dp-h2000dp")
class CursorTrackDiagTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val prefs get() = app.prefs()

    @Before fun clear() { prefs.edit().clear().commit() }

    private fun space() = CursorTrackText.spaceLine(app, CursorTrackVerdict.space(prefs))
    private fun delete() = CursorTrackText.deleteLine(app, CursorTrackVerdict.delete(prefs))

    @Test fun qualifyingBindingsSayActive() {
        assertEquals("Leertaste: aktiv", space())
        assertEquals("Löschtaste: aktiv", delete())
        SpaceGestures.write(prefs, SpaceGesture.SWIPE_UP, SpaceAction.MOVE_CURSOR) // up and down may also move the cursor or do nothing
        assertEquals("Leertaste: aktiv", space())
    }

    @Test fun leftBoundToAnotherActionSaysWhatToChange() {
        SpaceGestures.write(prefs, SpaceGesture.SWIPE_LEFT, SpaceAction.UNDO)
        assertEquals("Leertaste: nicht aktiv – ← Wischen ist mit „Rückgängig“ belegt; setze ← und → auf „Cursor bewegen“", space())
        assertEquals("Löschtaste: aktiv", delete()) // the delete key does not depend on the space bar
    }

    @Test fun upBoundToAnotherActionSaysWhatToChange() {
        SpaceGestures.write(prefs, SpaceGesture.SWIPE_UP, SpaceAction.REDO)
        val line = space()
        assertTrue(line.startsWith("Leertaste: nicht aktiv – ↑ Wischen ist mit „"), line)
        assertTrue(line.endsWith("setze ↑ und ↓ auf „Nichts“ oder „Cursor bewegen“"), line)
    }

    @Test fun theSwitchOffSaysSoForBothKeys() {
        prefs.edit().putBoolean(AgentPreferences.CURSOR_TRACKPAD, false).commit()
        assertEquals("Leertaste: nicht aktiv – „Cursor als Trackpad“ ist aus; schalte es ein", space())
        assertEquals("Löschtaste: nicht aktiv – „Cursor als Trackpad“ ist aus; schalte es ein", delete())
    }

    @Test fun theDeleteSwipeOffSaysSo() {
        prefs.edit().putBoolean(Settings.PREF_DELETE_SWIPE, false).commit()
        assertEquals("Löschtaste: nicht aktiv – „Löschen durch Wischen“ ist aus", delete())
        assertEquals("Leertaste: aktiv", space())
    }

    @Test fun theRuleIsTheListenersOwn() {
        // the same function the listener asks: whatever it says, the line says
        for (action in SpaceAction.entries) for (gesture in listOf(SpaceGesture.SWIPE_LEFT, SpaceGesture.SWIPE_UP)) {
            prefs.edit().clear().commit()
            if (action !in SpaceGestures.choices(gesture)) continue
            SpaceGestures.write(prefs, gesture, action)
            assertEquals(CursorTrackpad.appliesToSpace(SpaceGestures.read(prefs)), CursorTrackVerdict.space(prefs).active, "$gesture $action")
        }
    }

    @Test fun theCardShowsTheConfigurationBeforeAnyTouch() {
        SpaceGestures.write(prefs, SpaceGesture.SWIPE_LEFT, SpaceAction.UNDO)
        val card = Diagnose.trackpad(app, prefs, CursorTrackCounters().snapshot())
        assertEquals(Diagnose.Part.TRACKPAD, card.part)
        val rows = card.rows.associate { it.label to it.value }
        assertEquals("an", rows["Trackpad: Schalter „Cursor als Trackpad“"])
        assertEquals("← Rückgängig, → Cursor bewegen", rows["Trackpad: Leertaste ← und →"])
        assertEquals("↑ Nichts, ↓ Nichts", rows["Trackpad: Leertaste ↑ und ↓"])
        assertTrue(rows["Trackpad: Leertaste jetzt"]!!.startsWith("nicht aktiv, weil ← Wischen mit „Rückgängig“ belegt ist"))
        assertEquals("Trackpad aktiv", rows["Trackpad: Löschtaste jetzt"])
        assertEquals("noch keiner seit dem Start", rows["Letzter Wisch auf der Leertaste"])
        assertEquals("noch kein Trackpad-Wisch", rows["Leertaste: Feld des letzten Trackpad-Wischs"])
    }

    @Test fun theCardRendersTheLastDecisionInPlainGerman() {
        val counters = CursorTrackCounters()
        val key = CursorTrackCounters.Key.SPACE
        counters.onTouch(key)
        counters.onClaimed(key, android.text.InputType.TYPE_CLASS_TEXT)
        counters.onSteps(key, 14)
        counters.onSuppressedArrows(key, 3)
        val rows = Diagnose.trackpad(app, prefs, counters.snapshot()).rows.associate { it.label to it.value }
        assertEquals("Trackpad, 14 Schritte, senkrecht nicht möglich, weil das Feld einzeilig gemeldet ist", rows["Letzter Wisch auf der Leertaste"])
        assertEquals("Textklasse ja, Mehrzeilig-Flag nein", rows["Leertaste: Feld des letzten Trackpad-Wischs"])
        assertEquals("14 Schritte waagerecht, 0 Pfeile senkrecht gesendet, 3 senkrecht unterdrückt (Feld einzeilig), 0 Rasten", rows["Leertaste: Bewegung"])
        assertFalse(Diagnose.text(app, listOf(Diagnose.trackpad(app, prefs, counters.snapshot()))).isEmpty())
    }

    @Test fun everyOffReasonIsRenderedWithItsWords() {
        val key = CursorTrackCounters.Key.SPACE
        val expected = mapOf(
            CursorTrackCounters.OffReason.SWITCH to "Trackpad nicht gestartet, weil der Schalter aus ist",
            CursorTrackCounters.OffReason.BLOCKED to "Trackpad nicht gestartet, weil die Eingabe gesperrt war (ein Panel ist offen)",
            CursorTrackCounters.OffReason.BINDINGS to "Trackpad nicht gestartet, weil die Belegung nicht passt (SWIPE_UP = UNDO)",
            CursorTrackCounters.OffReason.SELECTION to "Trackpad nicht gestartet, weil eine Markierung bestand",
        )
        for ((reason, words) in expected) {
            val counters = CursorTrackCounters()
            counters.onOff(key, reason, if (reason == CursorTrackCounters.OffReason.BINDINGS) SpaceGesture.SWIPE_UP to SpaceAction.UNDO else null)
            val rows = Diagnose.trackpad(app, prefs, counters.snapshot()).rows.associate { it.label to it.value }
            assertEquals(words, rows["Letzter Wisch auf der Leertaste"], reason.name)
        }
    }

    @Test fun countersDoNotMixTheTwoKeysAndResetStartsOver() {
        val counters = CursorTrackCounters()
        counters.onTouch(CursorTrackCounters.Key.DELETE)
        counters.onDeleteLift(7, false)
        val s = counters.snapshot()
        assertEquals(0, s.space.touches); assertEquals(1, s.delete.touches)
        assertEquals(7, s.delete.lastSelectedChars); assertEquals(false, s.delete.lastLiftDeleted)
        counters.reset()
        assertEquals(0, counters.snapshot().delete.touches)
        assertEquals(1, s.delete.touches) // a snapshot is a copy
    }
}
