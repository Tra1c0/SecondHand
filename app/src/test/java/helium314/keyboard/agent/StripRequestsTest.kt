// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.content.Intent
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import dev.agentkeyboard.core.CommandContext
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import dev.agentkeyboard.core.AvailableBuild
import dev.agentkeyboard.core.ReleaseAsset
import dev.agentkeyboard.core.UpdateReleases
import helium314.keyboard.agent.updates.UpdateChip
import helium314.keyboard.agent.updates.UpdateSettings
import helium314.keyboard.agent.updates.UpdateStatus
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Stage 64b: fewer requests to the system and to the app per strip update, with the behaviour unchanged:
 * the shared lock state ([LockState]) and what it must never do (miss a lock), the text behind the cursor
 * and the clipboard in field chips, the clip classification, Smart Paste, the rows and the update chip,
 * the haptic plan. Counts come from the code's own seams and from the [TypingPerf] counters.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class], qualifiers = "de")
class StripRequestsTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private var t = 0L
    private var raw = false
    private val defaultClip = ClipVault.currentClip
    private val defaultMyData = FieldSuggestionChips.myData

    @Before fun setUp() {
        LockState.resetForTest()
        t = 0L
        raw = false
        LockState.clock = { t }
        LockState.normal.read = { raw }
        LockState.credentials.read = { raw }
        latinIME.prefs().edit().clear().putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, false).commit()
        ClipVault.clear()
    }

    @After fun tearDown() {
        LockState.resetForTest()
        ClipVault.currentClip = defaultClip
        ClipVault.clear()
        ClipVault.clock = { System.currentTimeMillis() }
        FieldSuggestionChips.forget()
        FieldSuggestionChips.fieldStarted()
        FieldSuggestionChips.myData = defaultMyData
        latinIME.prefs().edit().clear().commit()
    }

    private fun broadcast(action: String) {
        RuntimeEnvironment.getApplication().sendBroadcast(Intent(action))
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    // --- the shared lock state ---

    @Test fun anUnlockedAnswerIsAskedOnceForAboutASecond() {
        repeat(50) { assertFalse(LockState.isLocked(latinIME)) }
        assertEquals(1, LockState.normal.reads, "fifty asks, one request to the system")
        t = LockState.UNLOCKED_MILLIS - 1
        assertFalse(LockState.isLocked(latinIME))
        assertEquals(1, LockState.normal.reads)
        t = LockState.UNLOCKED_MILLIS
        assertFalse(LockState.isLocked(latinIME))
        assertEquals(2, LockState.normal.reads, "after the cache time it asks again")
    }

    @Test fun aLockBetweenTwoKeyPressesIsSeenAtTheLatestAfterTheCacheTime() {
        assertFalse(LockState.isLocked(latinIME))
        raw = true
        t = 500
        assertFalse(LockState.isLocked(latinIME), "inside the cache time the old answer stands (this is the bound)")
        t = LockState.UNLOCKED_MILLIS
        assertTrue(LockState.isLocked(latinIME), "at the cache time the lock is seen")
    }

    @Test fun aScreenEventDropsTheAnswerAtOnce() {
        for (action in listOf(Intent.ACTION_SCREEN_OFF, Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT)) {
            LockState.drop()
            raw = false
            assertFalse(LockState.isLocked(latinIME)) // also registers the receiver
            raw = true
            assertFalse(LockState.isLocked(latinIME), "still cached")
            broadcast(action)
            assertTrue(LockState.isLocked(latinIME), "$action: seen at once, no waiting for the cache time")
            raw = false
            broadcast(action)
            assertFalse(LockState.isLocked(latinIME), "$action: and unlocking is seen at once, too")
        }
    }

    @Test fun lockedIsKeptFromMemoryButNotForLongAndAFailingReadCountsAsLocked() {
        raw = true
        assertTrue(LockState.isLocked(latinIME))
        raw = false
        t = LockState.LOCKED_MILLIS - 1
        assertTrue(LockState.isLocked(latinIME), "from memory")
        t = LockState.LOCKED_MILLIS
        assertFalse(LockState.isLocked(latinIME))
        LockState.drop()
        LockState.normal.read = { error("the system does not answer") }
        assertTrue(LockState.isLocked(latinIME), "fail safe")
    }

    @Test fun everyCallerThatProtectsDataAsksTheSharedStateAndSeesALockAtOnce() {
        // the four seams of the strip path, as they are by default
        val callers = listOf<(Context) -> Boolean>(Memory.deviceLocked, MemoryChips.deviceLocked, FieldSuggestionChips.deviceLocked,
            SmartPasteChips.deviceLocked)
        repeat(10) { for (ask in callers) assertFalse(ask(latinIME)) }
        assertEquals(1, LockState.normal.reads, "forty asks of four callers, one request to the system")
        repeat(10) { assertFalse(UpdateChip.locked(latinIME)) }
        assertEquals(1, LockState.credentials.reads)
        raw = true
        broadcast(Intent.ACTION_SCREEN_OFF)
        for (ask in callers) assertTrue(ask(latinIME), "locked at once after the broadcast")
        assertTrue(UpdateChip.locked(latinIME))
        // and the blocks that use them
        val editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; packageName = "app" }
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(editor, false, latinIME.packageName))
        raw = false
        broadcast(Intent.ACTION_USER_PRESENT)
        assertFalse(SmartPasteChips.isBlocked(latinIME, Settings.getValues(), editor), "unlocked: a normal text field")
        assertFalse(FieldSuggestionChips.isBlocked(latinIME, Settings.getValues(), editor))
        raw = true
        broadcast(Intent.ACTION_SCREEN_OFF)
        assertTrue(SmartPasteChips.isBlocked(latinIME, Settings.getValues(), editor), "locked: blocked at once")
        assertTrue(FieldSuggestionChips.isBlocked(latinIME, Settings.getValues(), editor))
    }

    @Test fun aFieldStartAsksTheSystemOnceMoreAndATapOnAChipAsksLive() {
        assertFalse(LockState.isLocked(latinIME))
        assertEquals(1, LockState.normal.reads)
        raw = true
        val editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; packageName = "app"; fieldId = 7 }
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(editor, false, latinIME.packageName))
        TouchModel.load(latinIME, Settings.getValues(), editor, false)
        assertTrue(LockState.isLocked(latinIME), "a new field: the lock state of now")
        val reads = LockState.normal.reads
        for (i in 1..5) assertTrue(LockState.isLocked(latinIME))
        assertEquals(reads, LockState.normal.reads, "and then shared")
        // a tap
        raw = false
        var tapped = 0
        val row = KeyboardCommands.rowFor(FrameLayout(latinIME), listOf(KeyboardCommands.Chip("a") { tapped++ }))
        (row.getChildAt(0) as LinearLayout).getChildAt(0).performClick()
        assertEquals(1, tapped)
        assertFalse(LockState.isLocked(latinIME), "the tap dropped the old answer: asked live")
    }

    // --- the text behind the cursor and the clipboard in field chips (P1) ---

    private val email = EditorInfo().apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS; packageName = "app"; fieldId = 1
    }
    private var before = ""
    private var after = ""
    private var afterAsks = 0
    private val replaced = mutableListOf<String>()
    private val host = object : KeyboardCommands.Host {
        override val context: Context get() = latinIME
        override val editorInfo: EditorInfo? get() = email
        override val language = "de"
        override fun replace(command: CommandContext, replacement: String): Boolean {
            if (!before.endsWith(command.text)) return false
            before = before.dropLast(command.text.length) + replacement
            replaced += replacement
            return true
        }
        override fun openCalculator(command: dev.agentkeyboard.core.CommandCall) = false
        override val canOpenCalculator = false
        override val cursor: Int get() = before.length
        override fun textBeforeCursor(length: Int): CharSequence = before.takeLast(length)
        override fun textAfterCursor(length: Int): CharSequence { afterAsks++; return after.take(length) }
        override fun markPrivate(text: String) {}
    }

    private fun typeEmail(text: String): List<KeyboardCommands.Chip> {
        before = text
        return FieldSuggestionChips.stripChips(host)
    }

    private fun emailField() {
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(email, false, latinIME.packageName))
        FieldSuggestionChips.myData = { _, _ -> MyData(emails = listOf("max@example.com")) }
        FieldSuggestionChips.fieldStarted()
        after = ""
        afterAsks = 0
    }

    @Test fun typingInAnEmailFieldAsksTheAppForTheTextBehindTheCursorOnce() {
        emailField()
        val system = TypingPerf.systemRequestCount()
        for (typed in listOf("m", "ma", "max", "max@", "max@e", "max@ex", "max@exa", "max@exam", "max@examp")) {
            assertEquals(listOf("✉️ max@example.com"), typeEmail(typed).map { it.label.toString() }, typed)
        }
        assertEquals(1, afterAsks, "nine strip updates, one request to the app")
        assertTrue(TypingPerf.systemRequestCount() - system <= 2, "and at most two to the system (the lock state and the clipboard, once each)")
    }

    @Test fun aCursorThatWentBackAsksAgainAndTextBehindItIsAskedEveryTime() {
        emailField()
        typeEmail("max@exa"); typeEmail("max@exam")
        assertEquals(1, afterAsks)
        // the cursor moved back into the text: the old answer is void, and now there is text behind it
        after = "ple.com"
        assertEquals(emptyList(), typeEmail("max").map { it.label.toString() })
        assertEquals(2, afterAsks)
        typeEmail("max@"); typeEmail("max@e")
        assertEquals(4, afterAsks, "with text behind the cursor every update asks")
        // the text behind the cursor is gone again (the user went to the end): asked, found empty, remembered from there
        after = ""
        assertEquals(listOf("✉️ max@example.com"), typeEmail("max@ex").map { it.label.toString() })
        assertEquals(5, afterAsks)
        typeEmail("max@exa"); typeEmail("max@exam")
        assertEquals(5, afterAsks)
    }

    @Test fun aNewFieldAsksAgain() {
        emailField()
        typeEmail("ma"); typeEmail("max")
        assertEquals(1, afterAsks)
        after = "tail"
        KeyboardCommands.load(latinIME) // a field starts
        assertEquals(emptyList(), typeEmail("maxi").map { it.label.toString() })
        assertEquals(2, afterAsks, "the cursor is further than before, but this is another field")
    }

    @Test fun theTapAsksAgainSoNothingLandsInFrontOfText() {
        emailField()
        val chip = typeEmail("ma").single()
        assertEquals(1, afterAsks)
        after = "x" // text appeared behind the cursor after the chips were built
        chip.onTap()
        assertEquals(emptyList(), replaced, "nothing inserted")
        assertEquals("ma", before)
        // one tap, one attempt (as before): the refused chip is used up; the next strip update builds a new one
        after = ""
        chip.onTap()
        assertEquals(emptyList(), replaced)
        val again = typeEmail("ma").single()
        again.onTap()
        assertEquals(listOf("max@example.com"), replaced)
    }

    @Test fun theClipboardIsReadAndClassifiedOncePerClip() {
        var clock = 1_000_000L
        ClipVault.clock = { clock }
        var reads = 0
        ClipVault.currentClip = { reads++; Triple("max@example.com", clock - 1000, false) }
        val classified = ClipVault.classifications
        val system = TypingPerf.systemRequestCount()
        repeat(30) {
            val fresh = ClipVault.freshest(latinIME, dev.agentkeyboard.core.clip.ClipKind.EMAIL, 60_000L)
            assertEquals("max@example.com", fresh?.text)
            assertEquals(null, ClipVault.freshest(latinIME, dev.agentkeyboard.core.clip.ClipKind.PHONE, 60_000L))
        }
        assertEquals(1, reads, "sixty asks, one read of the system clipboard")
        assertEquals(1, ClipVault.classifications - classified, "and one classification of this text")
        assertEquals(1L, TypingPerf.systemRequestCount() - system)
        // after the reuse time the clipboard is read again; the same text is not classified again
        clock += ClipVault.LIVE_MILLIS
        ClipVault.freshest(latinIME, dev.agentkeyboard.core.clip.ClipKind.EMAIL, 60_000L)
        assertEquals(2, reads)
        assertEquals(1, ClipVault.classifications - classified)
        // a new clip (copied text arrives through offer) is read at once and classified
        ClipVault.offer("other@example.com", false, clock, "DE", false)
        ClipVault.currentClip = { reads++; Triple("other@example.com", clock, false) }
        assertEquals("other@example.com", ClipVault.freshest(latinIME, dev.agentkeyboard.core.clip.ClipKind.EMAIL, 60_000L)?.text)
        assertEquals(3, reads)
        assertTrue(ClipVault.classifications - classified >= 2)
    }

    @Test fun aSecretClipIsStillNeverOffered() {
        ClipVault.clock = { 1_000_000L }
        ClipVault.currentClip = { Triple("AKIAIOSFODNN7EXAMPLE secret key", 999_000L, false) }
        repeat(3) { assertEquals(null, ClipVault.freshest(latinIME, dev.agentkeyboard.core.clip.ClipKind.TEXT, 60_000L)) }
        // marked by the app: never, however often it is asked
        ClipVault.currentClip = { Triple("max@example.com", 999_000L, true) }
        ClipVault.clear()
        assertEquals(null, ClipVault.freshest(latinIME, dev.agentkeyboard.core.clip.ClipKind.EMAIL, 60_000L))
    }

    // --- Smart Paste (P7) ---

    @Test fun theSameClipIsRecognisedOnceNotOnEveryNeutralStrip() {
        val defaultClock = DateCommand.clock
        DateCommand.clock = { 1_800_000_000_000L } // a minute boundary must not fall into the test
        try { recognisedOnce() } finally { DateCommand.clock = defaultClock }
    }

    private fun recognisedOnce() {
        val settings = Settings.getValues()
        val link = "https://www.example.com/watch?v=abc&utm_source=newsletter"
        val before = SmartPasteChips.recognitions
        repeat(20) { assertTrue(SmartPasteChips.actions(latinIME, link, 5_000L, false, settings).isNotEmpty()) }
        assertEquals(1, SmartPasteChips.recognitions - before, "twenty strips, one recognition")
        // another clip, another copy time, another language: recognised again
        SmartPasteChips.actions(latinIME, "$link&x=1", 5_000L, false, settings)
        assertEquals(2, SmartPasteChips.recognitions - before)
        SmartPasteChips.actions(latinIME, "$link&x=1", 6_000L, false, settings)
        assertEquals(3, SmartPasteChips.recognitions - before)
        // marked sensitive: nothing, remembered or not
        assertEquals(emptyList(), SmartPasteChips.actions(latinIME, link, 5_000L, true, settings))
        // a secret by content stays refused
        assertEquals(emptyList(), SmartPasteChips.actions(latinIME, "AKIAIOSFODNN7EXAMPLE", 7_000L, false, settings))
    }

    // --- rows and the update chip (P8) ---

    @Test fun aRowIsNotBuiltAgainForTheSameChipsButItsTapsBelongToTheNewOnes() {
        val parent = FrameLayout(latinIME)
        val built = KeyboardCommands.rowsBuilt
        var first = 0
        var second = 0
        val a = KeyboardCommands.rowFor(parent, listOf(KeyboardCommands.Chip("@math 1+1") { first++ }, KeyboardCommands.Chip("Rechner") { first++ }))
        val b = KeyboardCommands.rowFor(parent, listOf(KeyboardCommands.Chip("@math 1+1") { second++ }, KeyboardCommands.Chip("Rechner") { second++ }))
        assertSame(a, b)
        assertEquals(1, KeyboardCommands.rowsBuilt - built, "one row for two updates")
        (b.getChildAt(0) as LinearLayout).getChildAt(1).performClick()
        assertEquals(0, first, "a tap never runs what the old chips would have done")
        assertEquals(1, second)
        // other text, another kind of chip, another strip: a new row each
        val c = KeyboardCommands.rowFor(parent, listOf(KeyboardCommands.Chip("@math 1+2") { }, KeyboardCommands.Chip("Rechner") { }))
        assertNotSame(b, c)
        val d = KeyboardCommands.rowFor(parent, listOf(KeyboardCommands.Chip("@math 1+2") { }, KeyboardCommands.Chip("Rechner", kind = KeyboardCommands.Chip.Kind.MUTED) { }))
        assertNotSame(c, d)
        val e = KeyboardCommands.rowFor(FrameLayout(latinIME), listOf(KeyboardCommands.Chip("@math 1+2") { }, KeyboardCommands.Chip("Rechner", kind = KeyboardCommands.Chip.Kind.MUTED) { }))
        assertNotSame(d, e)
        assertEquals(4, KeyboardCommands.rowsBuilt - built)
    }

    @Test fun theUpdateChipRowIsBuiltOnceForTheSameBuild() {
        val defaultLocked = UpdateChip.locked
        val defaultInstalled = UpdateChip.installed
        try {
            UpdateChip.locked = { false }
            UpdateChip.installed = { 20L }
            fun asset(name: String, size: Long, number: Long) = ReleaseAsset(name, size, UpdateReleases.downloadUrl("build-$number", name))
            UpdateSettings(latinIME).remember(AvailableBuild(22, asset(UpdateReleases.apkName(22), 1000, 22),
                asset(UpdateReleases.checksumName(22), 100, 22), "- Neu"), 20L)
            val parent = FrameLayout(latinIME)
            val built = UpdateChip.rowsBuilt
            val first = UpdateChip.createView(latinIME, parent)
            repeat(10) { assertSame(first, UpdateChip.createView(latinIME, parent)) }
            assertEquals(1, UpdateChip.rowsBuilt - built)
            assertNotSame(first, UpdateChip.createView(latinIME, FrameLayout(latinIME)), "another strip, another row")
            // a newer build is another chip; a lock is never hidden by the reuse
            UpdateSettings(latinIME).remember(AvailableBuild(23, asset(UpdateReleases.apkName(23), 1000, 23),
                asset(UpdateReleases.checksumName(23), 100, 23), "- Neu"), 20L)
            assertNotSame(first, UpdateChip.createView(latinIME, parent))
            UpdateChip.locked = { true }
            assertEquals(null, UpdateChip.createView(latinIME, parent))
        } finally {
            UpdateChip.locked = defaultLocked
            UpdateChip.installed = defaultInstalled
            UpdateSettings(latinIME).apply { status = UpdateStatus.Unknown; checkedAt = 0; remember(null, 0) }
        }
    }

    // --- haptics (P13) ---

    private class CountingDevice(override val sdk: Int = 33) : HapticChain.Device {
        var abilityQuestions = 0
        var played = 0
        override val hasVibrator: Boolean get() { abilityQuestions++; return true }
        override val hasAmplitudeControl: Boolean get() { abilityQuestions++; return true }
        override fun supportsPrimitive(primitive: Int): Boolean { abilityQuestions++; return true }
        override fun play(plan: HapticChain.Plan) { played++ }
    }

    @Test fun theVibratorsAbilitiesAreAskedOncePerVibratorNotPerDetent() {
        latinIME.prefs().edit().putBoolean(Settings.PREF_VIBRATE_ON, true).commit()
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(email, false, latinIME.packageName))
        val device = CountingDevice()
        val decisions = HapticChain.decisions
        repeat(30) { HapticChain.play(latinIME, Settings.getValues(), HapticChain.Feel.DETENT, device) }
        assertEquals(30, device.played, "every detent still plays")
        val asked = device.abilityQuestions
        assertTrue(asked in 1..6, "the abilities were asked $asked times for 30 detents")
        repeat(10) { HapticChain.play(latinIME, Settings.getValues(), HapticChain.Feel.DETENT, device) }
        assertEquals(asked, device.abilityQuestions, "no more questions for the same vibrator")
        HapticChain.play(latinIME, Settings.getValues(), HapticChain.Feel.STRETCH, device)
        assertEquals(2, HapticChain.decisions - decisions, "one plan per feel")
        // another vibrator decides again
        val other = CountingDevice(sdk = 26)
        HapticChain.play(latinIME, Settings.getValues(), HapticChain.Feel.DETENT, other)
        assertEquals(1, other.played)
        assertTrue(other.abilityQuestions > 0)
    }
}
