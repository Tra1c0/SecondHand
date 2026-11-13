// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import helium314.keyboard.agent.AgentPreferences
import helium314.keyboard.agent.SpaceAction
import helium314.keyboard.agent.SpaceGesture
import helium314.keyboard.keyboard.internal.LayoutDirective
import helium314.keyboard.agent.RejectedCorrections
import helium314.keyboard.agent.TypoMemory
import android.os.Handler
import android.os.Message
import android.text.InputType
import android.view.inputmethod.*
import androidx.core.content.edit
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowInputMethodService
import helium314.keyboard.ShadowInputMethodService.Companion.composingEnd
import helium314.keyboard.ShadowInputMethodService.Companion.composingStart
import helium314.keyboard.ShadowInputMethodService.Companion.selectedText
import helium314.keyboard.ShadowInputMethodService.Companion.selectionEnd
import helium314.keyboard.ShadowInputMethodService.Companion.selectionStart
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.MainKeyboardView
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.ShadowFacilitator2.Companion.lastAddedWord
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.inputlogic.InputLogic
import helium314.keyboard.latin.inputlogic.SpaceState
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ScriptUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.getTimestampFormatter
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLog
import java.util.*
import kotlin.math.min
import kotlin.streams.asSequence
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    ShadowLocaleManagerCompat::class,
    ShadowInputMethodManager2::class,
    ShadowInputMethodService::class,
    ShadowKeyboardSwitcher::class,
    ShadowHandler::class,
    ShadowFacilitator2::class,
])
class InputLogicTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val settingsValues get() = Settings.getValues()
    private val inputLogic get() = latinIME.mInputLogic
    private val connection: RichInputConnection get() = inputLogic.mConnection
    private val composerReader = InputLogic::class.java.getDeclaredField("mWordComposer").apply { isAccessible = true }
    private val composer get() = composerReader.get(inputLogic) as WordComposer
    private val spaceStateReader = InputLogic::class.java.getDeclaredField("mSpaceState").apply { isAccessible = true }
    private val spaceState get() = spaceStateReader.get(inputLogic) as Int
    private val beforeComposingReader = RichInputConnection::class.java.getDeclaredField("mCommittedTextBeforeComposingText").apply { isAccessible = true }
    private val connectionTextBeforeComposingText get() = (beforeComposingReader.get(connection) as CharSequence).toString()
    private val composingReader = RichInputConnection::class.java.getDeclaredField("mComposingText").apply { isAccessible = true }
    private val connectionComposingText get() = (composingReader.get(connection) as CharSequence).toString()

    private val textBeforeCursor get() = ShadowInputMethodService.textBeforeCursor
    private val textAfterCursor get() = ShadowInputMethodService.textAfterCursor
    private val composingText get() = ShadowInputMethodService.composingText
    private val text get() = ShadowInputMethodService.text
    private val cursor get() = ShadowInputMethodService.cursor
    private var currentInputType get() = ShadowInputMethodService.currentInputType
        set(value) { ShadowInputMethodService.currentInputType = value }

    init {
        ShadowLog.setupLogging()
        ShadowLog.stream = System.out
    }

    @Test fun inputCode() {
        input('c')
        assertEquals("c", textBeforeCursor)
        assertEquals("c", getTextFromConnection())
        assertEquals("", textAfterCursor)
        assertEquals("c", composingText)
        latinIME.mHandler.onFinishInput()
        assertEquals("", composingText)
    }

    @Test fun delete() {
        setText("hello there ")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello there", text)
        assertEquals("there", composingText)
    }

    @Test fun deleteMultiCodepointText() {
        setText("hello there \uD83E\uDF00")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello there ", text)
    }

    @Test fun deleteCombinedText() {
        setText("hello there э́")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello there ", text)

        setText("hello there H̵̛͕̞̦̰̜͍̰̥̟͆̏͂̌͑́ͅ")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello there ", text)
    }

    @Test fun deleteInsideWord() {
        setText("hello you there")
        setCursorPosition(8) // after o in you
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello yu there", text)
        assertEquals("yu", composingText)
    }

    @Test fun insertLetterIntoWord() {
        setText("hello")
        setCursorPosition(3) // after first l
        input('i')
        assertEquals("helilo", getWordAtCursor())
        assertEquals("helilo", getTextFromConnection())
        assertEquals(4, getCursorPosition())
        assertEquals(4, cursor)
        assertEquals("", composingText)
    }

    @Test fun insertLetterIntoWordWithWeirdEditor() {
        currentInputType = 180225 // should not change much, but just to be sure
        setText("hello")
        setCursorPosition(3, weirdTextField = true) // after first l
        input('i')
        assertEquals("helilo", getWordAtCursor())
        assertEquals("helilo", getTextFromConnection())
        assertEquals(4, getCursorPosition())
        assertEquals(4, cursor)
    }

    @Test fun insertLetterIntoOneOfSeveralWords() {
        setText("hello my friend")
        setCursorPosition(7) // between m and y
        input('a')
        assertEquals("may", getWordAtCursor())
        assertEquals("hello may friend", getTextFromConnection())
        assertEquals(8, getCursorPosition())
        assertEquals(8, cursor)
    }

    @Test fun combineHangul() {
        val ko = SubtypeSettings.getResourceSubtypesForLocale("ko".constructLocale()).first()
        latinIME.switchToSubtype(ko)
        chainInput("ㅂㄱㅑ")
        assertEquals("ㅂ갸", text)
    }

    @Test fun emojiHangul() {
        val ko = SubtypeSettings.getResourceSubtypesForLocale("ko".constructLocale()).first()
        latinIME.switchToSubtype(ko)
        input(0x1F970)
        assertEquals("\uD83E\uDD70", text)
    }

    // todo: make it work, but it might not be that simple because adding is done in combiner
    //  https://github.com/HeliBorg/HeliBoard/issues/214
    @Test fun insertLetterIntoWordHangulFails() {
        if (BuildConfig.BUILD_TYPE == "runTests") return
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("ko".constructLocale()).first())
        chainInput("ㅛㅎㄹㅎㅕㅛ")
        setCursorPosition(3)
        input('ㄲ') // fails, as expected from the hangul issue when processing the event in onCodeInput
        assertEquals("ㅛㅎㄹㄲ혀ㅛ", getWordAtCursor())
        assertEquals("ㅛㅎㄹㄲ혀ㅛ", getTextFromConnection())
        assertEquals("ㅛㅎㄹㄲ혀ㅛ", textBeforeCursor + textAfterCursor)
        assertEquals(4, getCursorPosition())
        assertEquals(4, cursor)
    }

    // see issue 1447
    @Test fun separatorAfterHangul() {
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("ko".constructLocale()).first())
        chainInput("ㅛ.")
        assertEquals("ㅛ.", text)
    }

    @Test fun deleteHangulInDebugMode() { // issue 1551, later only happened on phone
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("ko".constructLocale()).first())
        setText("ㅛㅛ ")
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE)
    }

    @Test fun separatorUnselectsWord() {
        setText("hello")
        assertEquals("hello", composingText)
        input('.')
        assertEquals("", composingText)
    }

    @Test fun autospace() {
        setText("hello")
        input('.')
        input('a')
        assertEquals("hello.a", textBeforeCursor)
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        setText("hello")
        input('.')
        input('a')
        assertEquals("hello. a", textBeforeCursor)
    }

    @Test fun autospaceButWithTextAfter() {
        setText("hello there")
        setCursorPosition(5) // after hello
        input('.')
        input('a')
        assertEquals("hello.a", textBeforeCursor)
        assertEquals("hello.a there", text)
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        setText("hello there")
        setCursorPosition(5) // after hello
        input('.')
        input('a')
        assertEquals("hello. a", textBeforeCursor)
        assertEquals("hello. a there", text)
    }

    @Test fun noAutospaceInUrlField() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("example.net")
        assertEquals("example. net", text)
        lastAddedWord = ""
        setText("")
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        chainInput("example.net")
        assertEquals("", lastAddedWord)
        assertEquals("example.net", text)
        assertEquals("example.net", composingText)
    }

    @Test fun noAutospaceInUrlFieldWhenPickingSuggestion() {
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        chainInput("exam")
        pickSuggestion("example")
        assertEquals("example", text)
        input('.')
        assertEquals("example.", text)
    }

    @Test fun noAutospaceForDetectedUrl() { // "light" version, should work without url detection
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("http://example.net")
        assertEquals("http://example.net", text)
        assertEquals("http", lastAddedWord)
        assertEquals("example.net", composingText)
    }

    @Test fun noAutospaceForDetectedEmail() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("mail@example.com")
        assertEquals("mail@example.com", text)
        assertEquals("mail@example", lastAddedWord) // todo: do we want this? not really nice, but don't want to be too aggressive with URL detection disabled
        assertEquals("com", composingText) // todo: maybe this should still see the whole address as a single word? or don't be too aggressive?
        setText("")
        lastAddedWord = ""
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("mail@example.com")
        assertEquals("", lastAddedWord)
        assertEquals("mail@example.com", composingText)
    }

    @Test fun urlDetectionThings() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("...h")
        assertEquals("...h", text)
        assertEquals("h", composingText)
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("bla..")
        assertEquals("bla..", text)
        assertEquals("", composingText)
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("bla.c")
        assertEquals("bla.c", text)
        assertEquals("bla.c", composingText)
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        latinIME.prefs().edit { putBoolean(Settings.PREF_SHIFT_REMOVES_AUTOSPACE, true) }
        input("bla")
        input('.')
        functionalKeyPress(KeyCode.SHIFT) // should remove the phantom space (in addition to normal effect)
        input('c')
        assertEquals("bla.c", text)
        assertEquals("bla.c", composingText)
    }

    @Test fun stripSeparatorsBeforeAddingToHistoryWithURLDetection() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("example.com.")
        assertEquals("example.com.", composingText)
        input(' ')
        assertEquals("example.com", lastAddedWord)
    }

    @Test fun dontSelectConsecutiveSeparatorsWithURLDetection() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("bla..")
        assertEquals("", composingText)
        assertEquals("bla..", text)
    }

    @Test fun selectDoesSelect() {
        setText("this is some text")
        setCursorPosition(3, 8)
        assertEquals("s is ", text.substring(3, 8))
    }

    @Test fun noComposingForPasswordFields() {
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
        input('a')
        input('b')
        assertEquals("", composingText)
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        input('.')
        input('c')
        assertEquals("", composingText)
    }

    @Test fun `don't select whole thing as composing word if URL detection disabled`() {
        setText("http://example.com")
        setCursorPosition(13) // between l and e
        assertEquals("example", composingText)
    }

    @Test fun `select whole thing except http(s) as composing word if URL detection enabled and selecting`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setText("http://example.com")
        setCursorPosition(13) // between l and e
        assertEquals("example.com", composingText)
        setText("http://bla.com http://example.com ")
        setCursorPosition(29) // between l and e
        assertEquals("example.com", composingText)
    }

    @Test fun `select whole thing except http(s) as composing word if URL detection enabled and typing`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("http://example.com")
        assertEquals("example.com", composingText)
    }

    @Test fun `don't add partial URL to history`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setText("http:/") // just so lastAddedWord isn't set to http
        chainInput("/bla.com")
        assertEquals("", lastAddedWord)
    }

    @Test fun urlProperlySelected() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        setText("http://example.com/here")
        setCursorPosition(18) // after .com
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE) // delete com
        // todo: do we really want no composing text?
        //  probably not... try not to break composing
        assertEquals("", composingText)
        chainInput("net")
        assertEquals("example.net", composingText)
    }

    @Test fun urlProperlySelectedWhenNotDeletingFullTld() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setText("http://example.com/here")
        setCursorPosition(18) // after .com
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE) // delete om
        // todo: this is a weird difference to deleting the full TLD (see urlProperlySelected)
        //  what do we want here? (probably consistency)
        assertEquals("example.c/here", composingText)
        chainInput("z")
        assertEquals("", composingText) // todo: this is a weird difference to deleting the full TLD
//        assertEquals("example.cz", composingText) // fails, but probably would be better than above
    }

    @Test fun dontCommitPartialUrlBeforeFirstPeriod() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        // type http://bla. -> bla not selected, but clearly url, also means http://bla is committed which we probably don't want
        chainInput("http://bla.")
        assertEquals("bla.", composingText)
    }

    @Test fun `intermediate commits in text field without protocol`() {
        chainInput("bla.")
        assertEquals("bla", lastAddedWord)
        chainInput("com/")
        assertEquals("com", lastAddedWord)
        chainInput("img.jpg")
        assertEquals("img", lastAddedWord)
        assertEquals("jpg", composingText)
    }

    @Test fun `intermediate commit in text field without protocol and with URL detection`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("bla.com/img.jpg")
        assertEquals("bla", lastAddedWord)
        assertEquals("bla.com/img.jpg", composingText)
    }

    @Test fun `only protocol commit in text field with protocol and URL detection`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("http://bla.com/img.jpg")
        assertEquals("http", lastAddedWord)
        assertEquals("bla.com/img.jpg", composingText)
    }

    @Test fun `no intermediate commit in URL field with protocol`() {
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        chainInput("http://bla.com/img.jpg")
        assertEquals("http", lastAddedWord) // todo: somehow avoid?
        assertEquals("http://bla.com/img.jpg", text)
        assertEquals("bla.com/img.jpg", composingText)
    }

    @Test fun `no intermediate commit in URL field with protocol and URL detection`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        chainInput("http://bla.com/img.jpg")
        assertEquals("http", lastAddedWord) // todo: somehow avoid?
        assertEquals("http://bla.com/img.jpg", text)
        assertEquals("bla.com/img.jpg", composingText)
    }

    @Test fun `no intermediate commit in URL field without protocol`() {
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        chainInput("bla.com/img.jpg")
        assertEquals("", lastAddedWord)
        assertEquals("bla.com/img.jpg", text)
        assertEquals("bla.com/img.jpg", composingText)
    }

    @Test fun `no intermediate commit in URL field without protocol and with URL detection`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        chainInput("bla.com/img.jpg")
        assertEquals("", lastAddedWord)
        assertEquals("bla.com/img.jpg", text)
        assertEquals("bla.com/img.jpg", composingText)
    }

    @Test fun `don't accidentally detect some other text fields as URI`() {
        // see comment in InputLogic.textBeforeCursorMayBeUrlOrSimilar
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE)
        chainInput("Hey,why")
        assertEquals("Hey, why", text)
    }

    @Test fun `URL detection does not trigger on non-words`() {
        // first make sure it works without URL detection
        chainInput("15:50-17")
        assertEquals("15:50-17", text)
        assertEquals("", composingText)
        // then with URL detection
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("15:50-17")
        assertEquals("15:50-17", text)
        assertEquals("", composingText)
    }

    @Test fun `autospace after selecting a suggestion`() {
        pickSuggestion("this")
        input('b')
        assertEquals("this b", text)
        assertEquals("b", composingText)
    }

    @Test fun `autospace works in URL field when input isn't URL`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        pickSuggestion("this")
        input('b')
        assertEquals("this b", text)
        assertEquals("b", composingText)
    }

    // https://github.com/HeliBorg/HeliBoard/issues/215
    // https://github.com/HeliBorg/HeliBoard/issues/229
    @Test fun `autospace works in URL field when input isn't URL, also for multiple suggestions`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        pickSuggestion("this")
        pickSuggestion("is")
        assertEquals("this is", text)
        pickSuggestion("not")
        assertEquals("this is not", text)
        input('c')
        assertEquals("this is not c", text)
        assertEquals("c", composingText)
    }

    @Test fun `emoji is added to dictionary`() {
        // check both text and codepoint input
        chainInput("hello ")
        input(0x1F36D)
        assertEquals(StringUtils.newSingleCodePointString(0x1F36D), lastAddedWord)
        reset()
        chainInput("hello ")
        input("🤗")
        assertEquals("\uD83E\uDD17", lastAddedWord)

        reset()
        chainInput("hello ")
        input("why 🤗 ") // not added because it's not only emoji (input can come from pasting)
        assertEquals("hello", lastAddedWord)
    }

    @Test fun `emoji uses phantom space`() {
        // check both text and codepoint input
        pickSuggestion("hi")
        input("🤗")
        assertEquals("\uD83E\uDD17", lastAddedWord)
        assertEquals("hi \uD83E\uDD17", text)
        reset()
        pickSuggestion("hi")
        input(0x1F36D)
        assertEquals(StringUtils.newSingleCodePointString(0x1F36D), lastAddedWord)
        assertEquals("hi ${StringUtils.newSingleCodePointString(0x1F36D)}", text)
    }

    // https://github.com/HeliBorg/HeliBoard/issues/230
    @Test fun `no autospace after opening quotes`() {
        chainInput("\"Hi\" \"h")
        assertEquals("\"Hi\" \"h", text)
        assertEquals("h", composingText)
        reset()
        chainInput("\"Hi\", \"h")
        assertEquals("\"Hi\", \"h", text)
        assertEquals("h", composingText)
    }

    @Test fun `autospace works in URL field when starting with quotes`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        input("\"")
        pickSuggestion("this")
        input("i")
        assertEquals("\"this i", text)
    }

    @Test fun `double space results in period and space, and delete removes the period`() {
        chainInput("hello")
        input(' ')
        input(' ')
        assertEquals("hello. ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello ", text)
    }

    @Test fun `no weird space inside multi-"`() {
        chainInput("\"\"\"")
        assertEquals("\"\"\"", text)

        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("\"\"\"")
        assertEquals("\"\"\"", text)
    }

    @Test fun `autospace still happens after "`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("\"hello\"you")
        assertEquals("\"hello\" you", text)
    }

    @Test fun `autospace still happens after " if next word is in quotes`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("\"hello\"\"you\"")
        assertEquals("\"hello\" \"you\"", text)
    }

    @Test fun `autospace propagates over "`() {
        input('"')
        pickSuggestion("hello")
        assertEquals(spaceState, SpaceState.PHANTOM) // picking a suggestion sets phantom space state
        chainInput("\"you")
        assertEquals("\"hello\" you", text)
    }

    @Test fun `autospace still happens after " if nex word is in " and after comma`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("\"hello\",\"you\"")
        assertEquals("\"hello\", \"you\"", text)
    }

    @Test fun `autospace in json editor`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("{\"label\":\"")
        assertEquals("{\"label\": \"", text)
        input('c')
        assertEquals("{\"label\": \"c", text)
    }

    @Test fun `text input and delete`() {
        input("hello")
        assertEquals("hello", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hell", text)

        reset()
        input("hello ")
        assertEquals("hello ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello", text)
    }

    @Test fun `emoji text input and delete`() {
        input("🕵🏼")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("", text)

        reset()
        input("\uD83D\uDD75\uD83C\uDFFC")
        input(' ')
        assertEquals("🕵🏼 ", text)
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("", text)
    }

    // emoRegex update to unicode 16.0 was required, https://github.com/HeliBorg/HeliBoard/issues/1760
    @Test fun `emojis deleted one by one`() {
        chainInput("\uD83E\uDEC6\uD83E\uDEC6\uD83E\uDEC6")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("\uD83E\uDEC6\uD83E\uDEC6", text)
    }

    @Test fun `revert autocorrect on delete`() {
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        chainInput("hullo")
        getAutocorrectedWithSpaceAfter("hello", "hullo")
        assertEquals("hello ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hullo", text)

        reset()
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        latinIME.prefs().edit { putBoolean(Settings.PREF_BACKSPACE_REVERTS_AUTOCORRECT, false) }
        chainInput("hullo")
        getAutocorrectedWithSpaceAfter("hello", "hullo")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello", text)
    }

    // fork, stage 20a
    @Test fun `undone correction is remembered only when switched on and not in incognito`() {
        RejectedCorrections.clear(latinIME)
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        chainInput("hullo")
        getAutocorrectedWithSpaceAfter("hello", "hullo")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hullo", text)
        assertFalse(RejectedCorrections.contains(latinIME, "hullo", "hello")) // off by default

        reset()
        latinIME.prefs().edit { putBoolean(AgentPreferences.AUTOCORRECT_REMEMBER_REJECTED, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        chainInput("hullo")
        getAutocorrectedWithSpaceAfter("hello", "hullo")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hullo", text)
        assertTrue(RejectedCorrections.contains(latinIME, "hullo", "hello"))

        RejectedCorrections.clear(latinIME)
        reset()
        latinIME.prefs().edit {
            putBoolean(AgentPreferences.AUTOCORRECT_REMEMBER_REJECTED, true)
            putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, true)
        }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        chainInput("hullo")
        getAutocorrectedWithSpaceAfter("hello", "hullo")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hullo", text)
        assertFalse(RejectedCorrections.contains(latinIME, "hullo", "hello"))
        RejectedCorrections.clear(latinIME)
    }

    // fork, stage 20b
    @Test fun `picking another word confirms a typo pair only when learning typos is on`() {
        TypoMemory.clear(latinIME)
        chainInput("nciht")
        pickSuggestion("nicht")
        assertEquals("nicht", text)
        assertEquals(0, TypoMemory.all(latinIME).size)

        reset()
        latinIME.prefs().edit { putBoolean(AgentPreferences.AUTOCORRECT_LEARN_TYPOS, true) }
        chainInput("das nciht")
        pickSuggestion("nicht")
        assertEquals("das nicht", text)
        assertEquals(listOf(Triple("nciht", "nicht", 1)), TypoMemory.all(latinIME))
        repeat(2) { chainInput(" das nciht"); pickSuggestion("nicht") }
        assertEquals("nicht", TypoMemory.meantWord(latinIME, "nciht", "das", true)) // learned with the previous word
        TypoMemory.clear(latinIME)
    }

    @Test fun `remove glide typing word on delete`() {
        glideTypingInput("hello")
        assertEquals("hello", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("", text)

        // todo: now we want some way to disable delete-all on backspace, either per setting or something else
        //  need to avoid getting into the mWordComposer.isBatchMode() part of handleBackspaceEvent
    }

    @Test fun timestamp() {
        chainInput("hello")
        functionalKeyPress(KeyCode.TIMESTAMP)
        assertEquals(Calendar.getInstance().time.time.toDouble(),
            getTimestampFormatter(latinIME).parse(text.substring(5))!!.time.toDouble(), 1000.0)
    }

    @Test fun inlineEmojiSearchStart() {
        assertEquals(true, InputLogic.isStartOfInlineEmojiSearch('t'.code, ':'.code, ' '.code, settingsValues))
        assertEquals(false, InputLogic.isStartOfInlineEmojiSearch(' '.code, ':'.code, ' '.code, settingsValues))
        assertEquals(true, InputLogic.isStartOfInlineEmojiSearch('t'.code, ':'.code, '.'.code, settingsValues))
        assertEquals(true, InputLogic.isStartOfInlineEmojiSearch('t'.code, ':'.code, "🌍".codePoints().asSequence().last(), settingsValues))
        assertEquals(false, InputLogic.isStartOfInlineEmojiSearch('t'.code, ':'.code, 't'.code, settingsValues))
        assertEquals(false, InputLogic.isStartOfInlineEmojiSearch('t'.code, ':'.code, '3'.code, settingsValues))
    }

    @Test fun inlineEmojiSearchString() {
        assertEquals("test", InputLogic.getInlineEmojiSearchString(":test"))
        assertEquals(null, InputLogic.getInlineEmojiSearchString("test"))
        assertEquals("test", InputLogic.getInlineEmojiSearchString(" :test"))
        assertEquals(null, InputLogic.getInlineEmojiSearchString("t:test"))
        assertEquals(null, InputLogic.getInlineEmojiSearchString("6:test"))
        assertEquals("test", InputLogic.getInlineEmojiSearchString("🌍:test"))
        assertEquals("test", InputLogic.getInlineEmojiSearchString(",:test"))
        assertEquals(null, InputLogic.getInlineEmojiSearchString(":test\nt"))
        assertEquals("/48", InputLogic.getInlineEmojiSearchString("2606:127.0.0.1::/48")) // do we want this?
    }

    @Test fun moveCursorHorizontally() {
        chainInput("hello")
        assertEquals(5, cursor)
        latinIME.mKeyboardActionListener.onSpaceSwipe(helium314.keyboard.agent.SpaceGesture.SWIPE_LEFT, -2)
        assertEquals(3, cursor)
        latinIME.mKeyboardActionListener.onSpaceSwipe(helium314.keyboard.agent.SpaceGesture.SWIPE_LEFT, -5)
        assertEquals(0, cursor)
        latinIME.mKeyboardActionListener.onSpaceSwipe(helium314.keyboard.agent.SpaceGesture.SWIPE_LEFT, -1)
        assertEquals(0, cursor)
        latinIME.mKeyboardActionListener.onSpaceSwipe(helium314.keyboard.agent.SpaceGesture.SWIPE_RIGHT, 3)
        assertEquals(3, cursor)
        latinIME.mKeyboardActionListener.onSpaceSwipe(helium314.keyboard.agent.SpaceGesture.SWIPE_RIGHT, 3)
        assertEquals(5, cursor)
        latinIME.mKeyboardActionListener.onSpaceSwipe(helium314.keyboard.agent.SpaceGesture.SWIPE_RIGHT, 1)
        assertEquals(5, cursor)
    }

    // ------- helper functions ---------

    @Test fun generatedSelectionReplacementPreservesSurroundingTextAndSingleDelete() {
        setText("prefix Hallo suffix")
        setCursorPosition(7, 12)
        assertEquals(dev.agentkeyboard.core.ApplyOutcome.SUBMITTED,
            inputLogic.applyGeneratedText("Hallo", ".com", 7, 12))
        assertEquals("prefix .com suffix", text)
        checkConnectionConsistency()
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("prefix .co suffix", text)
    }

    @Test fun generatedInsertionAtCursorPreservesSurroundingTextAndSingleDelete() {
        setText("prefix  suffix")
        setCursorPosition(7)
        assertEquals(dev.agentkeyboard.core.ApplyOutcome.SUBMITTED, inputLogic.insertGeneratedText("Hallo", 7))
        assertEquals("prefix Hallo suffix", text)
        checkConnectionConsistency()
        assertTrue(inputLogic.isAgentHistorySuppressed)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("prefix Hall suffix", text)
    }

    @Test fun typedCommandIsReplacedInOneCommitWithoutStoppingLearning() {
        chainInput("hi @ma")
        assertEquals("hi @ma", text)
        assertEquals(dev.agentkeyboard.core.ApplyOutcome.SUBMITTED, inputLogic.replaceCommandText("@ma", "@math ", 6))
        assertEquals("hi @math ", text)
        assertEquals("", composingText)
        checkConnectionConsistency()
        assertFalse(inputLogic.isAgentHistorySuppressed) // the user typed everything around the command
        // an empty replacement deletes the command
        assertEquals(dev.agentkeyboard.core.ApplyOutcome.SUBMITTED, inputLogic.replaceCommandText("@math ", "", 9))
        assertEquals("hi ", text)
        checkConnectionConsistency()
    }

    @Test fun typedCommandIsKeptWhenTheFieldOrCursorDiffers() {
        chainInput("hi @ma")
        assertEquals(dev.agentkeyboard.core.ApplyOutcome.REJECTED, inputLogic.replaceCommandText("@mo", "@math ", 6))
        assertEquals("hi @ma", text)
        assertEquals(dev.agentkeyboard.core.ApplyOutcome.REJECTED, inputLogic.replaceCommandText("@ma", "@math ", 5))
        assertEquals(dev.agentkeyboard.core.ApplyOutcome.REJECTED, inputLogic.replaceCommandText("", "x", 6))
        assertEquals("hi @ma", text)
        checkConnectionConsistency()
        setText("hi @ma")
        setCursorPosition(3, 6) // a selection: never replaced
        assertEquals(dev.agentkeyboard.core.ApplyOutcome.REJECTED, inputLogic.replaceCommandText("@ma", "@math ", 6))
        assertEquals("hi @ma", text)
    }

    @Test fun typedCommandNameIsNeitherLearnedNorGuardedAfterwards() {
        helium314.keyboard.agent.KeyboardCommands.load(latinIME)
        lastAddedWord = ""
        chainInput("@math ")
        assertEquals("@math ", text)
        assertNotEquals("math", lastAddedWord)
        assertTrue(helium314.keyboard.agent.KeyboardCommands.guardsTextBefore("hi @math "))
        assertTrue(helium314.keyboard.agent.KeyboardCommands.guardsTextBefore("hi @ma"))
        assertFalse(helium314.keyboard.agent.KeyboardCommands.guardsTextBefore("hi @math 2*3")) // @math does not protect its argument
        assertFalse(helium314.keyboard.agent.KeyboardCommands.guardsTextBefore("mail@math"))
        assertFalse(helium314.keyboard.agent.KeyboardCommands.guardsTextBefore("hi @lewin"))
    }

    @Test fun typedCommandCalculatorChipOpensTheCalculator() {
        helium314.keyboard.agent.KeyboardCommands.load(latinIME)
        val root = android.widget.FrameLayout(latinIME)
        val calculator = helium314.keyboard.agent.CalculatorView(latinIME).apply { id = R.id.agent_calculator }
        root.addView(calculator)
        latinIME.agentEditorController.attach(root)
        chainInput("x @math")
        val strip = android.widget.FrameLayout(latinIME)
        val chips = helium314.keyboard.agent.KeyboardCommands.createChipsView(latinIME, strip)
        assertNotNull(chips)
        val row = (chips as android.view.ViewGroup).getChildAt(0) as android.view.ViewGroup
        val open = (0 until row.childCount).map { row.getChildAt(it) as android.widget.TextView }
            .single { it.text.toString() == latinIME.getString(R.string.agent_command_math_open) }
        open.performClick()
        // the field stays as it is while the calculator is open (an app may restart input otherwise)
        assertEquals("x @math", text)
        assertEquals(android.view.View.VISIBLE, calculator.visibility)
        listOf("2", "+", "3").forEach { label ->
            buttons(calculator).first { it.text.toString() == label }.performClick()
        }
        calculator.findViewById<android.widget.Button>(R.id.agent_calculator_insert).performClick()
        assertEquals("x 2 + 3 = 5", text) // the command is replaced in one commit
        assertEquals(android.view.View.GONE, calculator.visibility)
        checkConnectionConsistency()
    }

    @Test fun typedCommandContactChipShowsFieldsAndInsertsOne() {
        helium314.keyboard.agent.KeyboardCommands.load(latinIME)
        org.robolectric.Shadows.shadowOf(latinIME.application).grantPermissions(android.Manifest.permission.READ_CONTACTS)
        val contact = helium314.keyboard.agent.ContactCommand
        contact.source = object : helium314.keyboard.agent.ContactCommand.Source {
            override fun find(context: android.content.Context, query: String, limit: Int) =
                listOf(helium314.keyboard.agent.ContactCommand.Contact(7, "Anna Muster"))
            override fun fields(context: android.content.Context, contactId: Long, limit: Int) =
                listOf(helium314.keyboard.agent.ContactCommand.Field(helium314.keyboard.agent.ContactCommand.Kind.PHONE, "0170 123", "Mobile"))
        }
        // the address book answers at once here; ContactCommandTest covers the background queries
        contact.worker = object : helium314.keyboard.agent.ContactCommand.Worker {
            override fun schedule(delayMs: Long, task: () -> Unit): () -> Unit { task(); return {} }
            override fun post(task: () -> Unit) = task()
        }
        try {
            chainInput("x @contact Ann")
            assertEquals("x @contact Ann", text) // the typed name is not corrected
            val strip = android.widget.FrameLayout(latinIME)
            val chips = helium314.keyboard.agent.KeyboardCommands.createChipsView(latinIME, strip) as android.view.ViewGroup
            fun labels() = (chips.getChildAt(0) as android.view.ViewGroup).let { row ->
                (0 until row.childCount).map { row.getChildAt(it) as android.widget.TextView }
            }
            assertEquals(listOf("Anna Muster"), labels().map { it.text.toString() })
            labels().single().performClick()
            // the same strip now shows the fields; the field is unchanged
            assertEquals(listOf("←", "Anna Muster", "0170 123"), labels().map { it.text.toString() })
            assertEquals("x @contact Ann", text)
            labels().last().performClick()
            assertEquals("x 0170 123", text) // the command is replaced in one commit
            checkConnectionConsistency()
        } finally {
            contact.source = helium314.keyboard.agent.ContactCommand.PhoneContacts
            contact.reset()
            contact.worker = helium314.keyboard.agent.ContactCommand.BackgroundWorker
        }
    }

    @Test fun typedCommandCalculatorInsertsAtTheCursorWhenTheCommandIsGone() {
        helium314.keyboard.agent.KeyboardCommands.load(latinIME)
        val root = android.widget.FrameLayout(latinIME)
        val calculator = helium314.keyboard.agent.CalculatorView(latinIME).apply { id = R.id.agent_calculator }
        root.addView(calculator)
        val controller = latinIME.agentEditorController
        controller.attach(root)
        setText("x ")
        setCursorPosition(2)
        controller.startEditor(EditorInfo().apply { inputType = currentInputType; initialSelStart = 2; initialSelEnd = 2 })
        controller.selectionChanged(2, 2, -1, -1)
        assertTrue(controller.openCalculator("4*2", "@math 4*2", 11)) // the command is no longer there
        calculator.findViewById<android.widget.Button>(R.id.agent_calculator_insert).performClick()
        assertEquals("x 4 × 2 = 8", text)
        assertEquals(android.view.View.GONE, calculator.visibility)
    }

    private fun buttons(group: android.view.View): List<android.widget.Button> = when (group) {
        is android.widget.Button -> listOf(group)
        is android.view.ViewGroup -> (0 until group.childCount).flatMap { buttons(group.getChildAt(it)) }
        else -> emptyList()
    }

    @Test fun typedCommandChipFromBeforeTheLastKeystrokeLeavesTheWordAlone() {
        chainInput("hi @ma")
        input('t') // the chips were built for "@ma" at 6
        assertEquals(dev.agentkeyboard.core.ApplyOutcome.REJECTED, inputLogic.replaceCommandText("@ma", "@math ", 6))
        assertEquals("hi @mat", text)
        assertEquals("mat", composingText) // still composing, suggestions keep working
        assertTrue(composer.isComposingWord)
    }

    @Test fun typedCommandLanguageChipRemovesTheCommandThenSwitches() {
        val german = SubtypeSettings.getResourceSubtypesForLocale("de".constructLocale()).first()
        val spanish = SubtypeSettings.getResourceSubtypesForLocale("es".constructLocale()).first()
        val language = helium314.keyboard.agent.commands.LanguageCommand
        language.subtypes = { listOf(german, spanish) }
        try {
            latinIME.switchToSubtype(german)
            helium314.keyboard.agent.KeyboardCommands.load(latinIME)
            chainInput("hola @spnaish")
            assertEquals("hola @spnaish", text) // the typed word is not corrected
            val strip = android.widget.FrameLayout(latinIME)
            val chips = helium314.keyboard.agent.KeyboardCommands.createChipsView(latinIME, strip) as android.view.ViewGroup
            val row = chips.getChildAt(0) as android.view.ViewGroup
            assertEquals(1, row.childCount)
            assertEquals(german, RichInputMethodManager.getInstance().currentSubtype.rawSubtype) // nothing before the tap
            row.getChildAt(0).performClick()
            assertEquals("hola ", text) // the command went in one commit, in the field it was typed in
            assertEquals(spanish, RichInputMethodManager.getInstance().currentSubtype.rawSubtype)
            checkConnectionConsistency()
        } finally {
            language.subtypes = { SubtypeSettings.getEnabledSubtypes(true) }
            helium314.keyboard.agent.KeyboardCommands.load(latinIME)
        }
    }

    @Test fun typedCommandIsKeptWhenTheEditorCursorMovedUnseen() {
        setText("x @ma")
        setCursorPosition(5)
        // the app put text in front without the keyboard knowing yet: the relative text still matches
        ShadowInputMethodService.text = "yyy x @ma"
        ShadowInputMethodService.selectionStart = 9
        ShadowInputMethodService.selectionEnd = 9
        assertEquals(dev.agentkeyboard.core.ApplyOutcome.REJECTED, inputLogic.replaceCommandText("@ma", "@math ", 5))
        assertEquals("yyy x @ma", text)
    }

    @Test fun generatedInsertionIsRefusedWhileAWordIsBeingComposed() {
        chainInput("hel")
        assertEquals("hel", composingText)
        val suppressedBefore = inputLogic.isAgentHistorySuppressed
        assertEquals(dev.agentkeyboard.core.ApplyOutcome.REJECTED, inputLogic.insertGeneratedText("Hallo", 3))
        assertEquals("hel", text)
        assertEquals(suppressedBefore, inputLogic.isAgentHistorySuppressed)
        checkConnectionConsistency()
    }

    @Test fun generatedInsertionAfterPickedSuggestionGetsTheSameAutomaticSpaceAsTyping() {
        pickSuggestion("this")
        assertEquals("this", text)
        assertEquals(dev.agentkeyboard.core.ApplyOutcome.SUBMITTED, inputLogic.insertGeneratedText("works", 4))
        assertEquals("this works", text) // one commit, not "thisworks"
        checkConnectionConsistency()
    }

    @Test fun generatedInsertionFollowsTheTypedTextRuleForTheAutomaticSpace() {
        // Whatever typing a letter does after a picked suggestion in a field, insertion does the same.
        for (type in listOf(InputType.TYPE_CLASS_TEXT, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)) {
            reset()
            setInputType(type)
            chainInput("exam")
            pickSuggestion("example")
            input('c')
            val typed = text
            reset()
            setInputType(type)
            chainInput("exam")
            pickSuggestion("example")
            assertEquals(dev.agentkeyboard.core.ApplyOutcome.SUBMITTED, inputLogic.insertGeneratedText("c", 7))
            assertEquals(typed, text)
        }
    }

    @Test fun generatedTextSuppressesLearningUntilFreshFrameworkSession() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, true) }
        setText("prefix Hallo suffix")
        setCursorPosition(7, 12)
        assertEquals(dev.agentkeyboard.core.ApplyOutcome.SUBMITTED,
            inputLogic.applyGeneratedText("Hallo", "GENERATED", 7, 12))
        assertTrue(inputLogic.isAgentHistorySuppressed)
        chainInput(" hello ")
        assertEquals("", lastAddedWord)
        val info = EditorInfo().apply { inputType = currentInputType }
        latinIME.onStartInput(info, true)
        assertTrue(inputLogic.isAgentHistorySuppressed)
        latinIME.onStartInput(info, false)
        assertFalse(inputLogic.isAgentHistorySuppressed)
    }

    @Test fun generatedUncertainEditorBlocksAllPublicInputRoutesUntilFreshCoordinates() {
        setText("hello")
        setCursorPosition(0, 5)
        assertEquals(dev.agentkeyboard.core.ApplyOutcome.SUBMITTED,
            inputLogic.applyGeneratedText("hello", "hello", 0, 5))
        org.robolectric.util.ReflectionHelpers.setField(latinIME.agentEditorController, "needsCursorRefresh", true)
        latinIME.onTextInput("paste")
        latinIME.onEvent(Event.createEventForCodePointFromUnknownSource('x'.code))
        latinIME.mKeyboardActionListener.onCodeInput(KeyCode.DELETE, -1, -1, false)
        latinIME.mKeyboardActionListener.onMoveDeletePointer(2)
        latinIME.mKeyboardActionListener.onUpWithDeletePointerActive()
        latinIME.mKeyboardActionListener.onSpaceSwipe(helium314.keyboard.agent.SpaceGesture.SWIPE_LEFT, -1)
        latinIME.onStartBatchInput()
        latinIME.onUpdateBatchInput(helium314.keyboard.latin.common.InputPointers(1))
        latinIME.onEndBatchInput(helium314.keyboard.latin.common.InputPointers(1))
        assertEquals("hello", text)
        assertEquals(5, cursor)
        assertFalse(inputLogic.hasActiveComposition())
        latinIME.agentEditorController.cursorPositionUpdated(5, 5)
        assertFalse(latinIME.agentEditorController.isAwaitingCursorRefresh)
        input('!')
        assertEquals("hello!", text)
    }

    @Test fun generatedWheelPreparesLocalPreviewAndOnlyExplicitApplyWrites() {
        setText("prefix Hallo suffix")
        setCursorPosition(7, 12)
        val controller = latinIME.agentEditorController
        controller.startEditor(EditorInfo().apply {
            inputType = currentInputType; initialSelStart = 7; initialSelEnd = 12
        })
        val root = android.widget.FrameLayout(latinIME)
        val panel = helium314.keyboard.agent.AgentPanelView(latinIME).apply { id = R.id.agent_panel }
        val wheel = helium314.keyboard.agent.AgentWheelView(latinIME).apply { id = R.id.agent_wheel }
        root.addView(panel); root.addView(wheel)
        controller.attach(root)
        assertTrue(controller.openWheel())
        assertEquals("prefix Hallo suffix", text)
        assertTrue(controller.isBlockingInput)
        wheel.findViewById<android.widget.Button>(R.id.agent_wheel_uppercase).performClick()
        assertEquals("prefix Hallo suffix", text)
        assertEquals("HALLO", panel.findViewById<android.widget.TextView>(R.id.agent_result).text.toString())
        panel.findViewById<android.widget.Button>(R.id.agent_apply).performClick()
        assertEquals("prefix HALLO suffix", text)
        assertFalse(controller.isBlockingInput)
        assertEquals(android.view.View.GONE, panel.visibility)
    }

    private fun setSpaceGesture(gesture: SpaceGesture, action: SpaceAction) =
        latinIME.prefs().edit { putString(gesture.key, action.name) }

    /** A real tap on the space key as PointerTracker sends it: press, code (no coordinates off the letters), release. */
    private fun spaceTap() {
        val listener = latinIME.mKeyboardActionListener
        listener.onPressKey(Constants.CODE_SPACE, 0, 1, helium314.keyboard.event.HapticEvent.KEY_PRESS)
        listener.onCodeInput(Constants.CODE_SPACE, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
        listener.onReleaseKey(Constants.CODE_SPACE, false)
        handleMessages()
    }

    private fun attachWheel(): helium314.keyboard.agent.AgentWheelView {
        val root = android.widget.FrameLayout(latinIME)
        val wheel = helium314.keyboard.agent.AgentWheelView(latinIME).apply { id = R.id.agent_wheel }
        root.addView(wheel)
        latinIME.agentEditorController.attach(root)
        return wheel
    }

    @Test fun generatedSpaceDoubleTapKeepsTheDoubleSpacePeriodByDefault() {
        chainInput("hello")
        spaceTap(); spaceTap()
        assertEquals("hello. ", text)
        assertTrue(settingsValues.mUseDoubleSpacePeriod)
    }

    @Test fun generatedSpaceDoubleTapRunsItsActionInsteadOfThePeriod() {
        setSpaceGesture(SpaceGesture.DOUBLE_TAP, SpaceAction.COMMAND_LIST)
        assertFalse(settingsValues.mUseDoubleSpacePeriod)
        chainInput("hello")
        spaceTap()
        assertEquals("hello ", text)
        spaceTap()
        assertEquals("hello @", text)
        // the action's own key codes do not count as taps: the next tap is a plain space again
        spaceTap()
        assertEquals("hello @ ", text)
    }

    @Test fun generatedSpaceDoubleTapNeedsTheSameCursorWithinTheTimeout() {
        setSpaceGesture(SpaceGesture.DOUBLE_TAP, SpaceAction.COMMAND_LIST)
        chainInput("hello")
        spaceTap()
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(settingsValues.mDoubleSpacePeriodTimeout + 100))
        spaceTap()
        assertEquals("hello  ", text) // two spaces: no period, no action

        setText("hello")
        spaceTap()
        setCursorPosition(2)
        spaceTap()
        assertFalse(text.contains('@'))

        // a space code without a key press (strip, popup key, an action) is no tap and resets
        setText("hello")
        spaceTap()
        latinIME.mKeyboardActionListener.onCodeInput(Constants.CODE_SPACE, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
        handleMessages()
        spaceTap()
        assertFalse(text.contains('@'))
        latinIME.mKeyboardActionListener.onCodeInput(Constants.CODE_SPACE, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
        handleMessages()
        assertFalse(text.contains('@'))
    }

    @Test fun generatedSpaceGestureActionsUseTheExistingKeyCodes() {
        val spy = Mockito.spy(latinIME.mKeyboardActionListener as helium314.keyboard.keyboard.KeyboardActionListenerImpl)
        Mockito.doNothing().`when`(spy).onCodeInput(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean())
        Mockito.doNothing().`when`(spy).toggleLayout(Mockito.any(LayoutDirective.Utility::class.java) ?: LayoutDirective.Utility.NUMPAD, Mockito.anyInt(), Mockito.any())
        val codes = mapOf(
            SpaceAction.SWITCH_LANGUAGE to KeyCode.LANGUAGE_SWITCH,
            SpaceAction.SWITCH_INPUT_METHOD to KeyCode.SYSTEM_INPUT_METHOD_PICKER,
            SpaceAction.DICTATION to KeyCode.AGENT_DICTATE,
            SpaceAction.CLIPBOARD to KeyCode.CLIPBOARD,
            SpaceAction.EMOJI to KeyCode.EMOJI,
            SpaceAction.UNDO to KeyCode.UNDO,
            SpaceAction.REDO to KeyCode.REDO,
            SpaceAction.SELECT_ALL to KeyCode.CLIPBOARD_SELECT_ALL,
        )
        val layouts = mapOf(
            SpaceAction.TOGGLE_NUMPAD to LayoutDirective.Utility.NUMPAD,
            SpaceAction.TOGGLE_SYMBOLS to LayoutDirective.Utility.SYMBOLS,
            SpaceAction.TOGGLE_DPAD to LayoutDirective.Utility.DPAD,
        )
        val request = helium314.keyboard.keyboard.KeyboardActionListener.CustomAction.SPACE_HOLD
        for (gesture in listOf(SpaceGesture.SWIPE_LEFT, SpaceGesture.SWIPE_DOWN, SpaceGesture.HOLD)) {
            fun run(): Boolean = if (gesture == SpaceGesture.HOLD) spy.onCustomRequest(request) else spy.onSpaceSwipe(gesture, -1)
            codes.forEach { (action, code) ->
                // a swipe switches languages by distance; the input method picker on hold stays with PointerTracker
                if (gesture.isSwipe && action == SpaceAction.SWITCH_LANGUAGE) return@forEach
                setSpaceGesture(gesture, action)
                Mockito.clearInvocations(spy)
                val consumed = run()
                if (gesture == SpaceGesture.HOLD && action == SpaceAction.SWITCH_INPUT_METHOD) {
                    assertFalse(consumed)
                    Mockito.verify(spy, Mockito.never()).onCodeInput(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean())
                } else {
                    assertTrue(consumed, "$gesture $action")
                    Mockito.verify(spy).onCodeInput(code, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
                }
            }
            layouts.forEach { (action, layout) ->
                setSpaceGesture(gesture, action)
                Mockito.clearInvocations(spy)
                assertTrue(run())
                Mockito.verify(spy).toggleLayout(Mockito.eq(layout) ?: layout, Mockito.anyInt(), Mockito.any())
            }
            setSpaceGesture(gesture, SpaceAction.NONE)
            Mockito.clearInvocations(spy)
            assertFalse(run())
            Mockito.verify(spy, Mockito.never()).onCodeInput(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean())
        }
    }

    @Test fun generatedSpaceCommandListTypesThePrefixOnlyWhereCommandsWork() {
        setSpaceGesture(SpaceGesture.SWIPE_UP, SpaceAction.COMMAND_LIST)
        val listener = latinIME.mKeyboardActionListener
        assertTrue(listener.onSpaceSwipe(SpaceGesture.SWIPE_UP, -1)); handleMessages()
        assertEquals("@", text)
        setText("hi")
        assertTrue(listener.onSpaceSwipe(SpaceGesture.SWIPE_UP, -1)); handleMessages()
        assertEquals("hi @", text)
        setText("(")
        assertTrue(listener.onSpaceSwipe(SpaceGesture.SWIPE_UP, -1)); handleMessages()
        assertEquals("(@", text)

        latinIME.prefs().edit { putBoolean(AgentPreferences.COMMANDS, false) }
        setText("hi")
        assertFalse(listener.onSpaceSwipe(SpaceGesture.SWIPE_UP, -1)); handleMessages()
        assertEquals("hi", text)
        latinIME.prefs().edit { putBoolean(AgentPreferences.COMMANDS, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        assertFalse(listener.onSpaceSwipe(SpaceGesture.SWIPE_UP, -1)); handleMessages()
        assertEquals("hi", text)
    }

    @Test fun generatedSpaceHoldFollowsItsOwnSetting() {
        val request = helium314.keyboard.keyboard.KeyboardActionListener.CustomAction.SPACE_HOLD
        val listener = latinIME.mKeyboardActionListener
        val wheel = attachWheel()
        assertTrue(settingsValues.mSpaceForLangChange) // default: HeliBoard's picker
        // an own value wins over the older opt-in and picker settings
        latinIME.prefs().edit { putBoolean(AgentPreferences.SPACE_LONG_PRESS, true) }
        setSpaceGesture(SpaceGesture.HOLD, SpaceAction.NONE)
        assertFalse(listener.onCustomRequest(request))
        assertFalse(settingsValues.mSpaceForLangChange)
        assertEquals(android.view.View.GONE, wheel.visibility)
        latinIME.prefs().edit { putBoolean(Settings.PREF_SPACE_TO_CHANGE_LANG, false) }
        setSpaceGesture(SpaceGesture.HOLD, SpaceAction.SWITCH_INPUT_METHOD)
        assertFalse(listener.onCustomRequest(request)) // PointerTracker shows the picker
        assertTrue(settingsValues.mSpaceForLangChange)
        latinIME.prefs().edit { putBoolean(AgentPreferences.SPACE_LONG_PRESS, false) }
        setSpaceGesture(SpaceGesture.HOLD, SpaceAction.ACTION_WHEEL)
        assertFalse(settingsValues.mSpaceForLangChange)
        assertTrue(listener.onCustomRequest(request))
        assertEquals(android.view.View.VISIBLE, wheel.visibility)
        latinIME.agentEditorController.close()
    }

    @Test fun generatedSpaceSwipeOpensTheWheelOnceAndBlocksFurtherSteps() {
        setSpaceGesture(SpaceGesture.SWIPE_RIGHT, SpaceAction.ACTION_WHEEL)
        chainInput("hello")
        val wheel = attachWheel()
        val listener = latinIME.mKeyboardActionListener
        assertTrue(listener.onSpaceSwipe(SpaceGesture.SWIPE_RIGHT, 1))
        assertEquals(android.view.View.VISIBLE, wheel.visibility)
        val position = cursor
        assertTrue(listener.onSpaceSwipe(SpaceGesture.SWIPE_LEFT, -2)) // consumed while the wheel blocks input
        assertEquals(position, cursor)
        latinIME.agentEditorController.close()
        listener.onEndSpaceSwipe()
        assertTrue(listener.onSpaceSwipe(SpaceGesture.SWIPE_LEFT, -2)) // left still moves the cursor
        assertEquals(position - 2, cursor)
    }

    @Test fun generatedSpaceLongPressRequiresOptInAndAnAvailableSurface() {
        val request = helium314.keyboard.keyboard.KeyboardActionListener.CustomAction.SPACE_HOLD
        val listener = latinIME.mKeyboardActionListener
        assertFalse(listener.onCustomRequest(request))
        latinIME.prefs().edit().putBoolean(helium314.keyboard.agent.AgentPreferences.SPACE_LONG_PRESS, true).commit()
        assertFalse(listener.onCustomRequest(request))
        val root = android.widget.FrameLayout(latinIME)
        val wheel = helium314.keyboard.agent.AgentWheelView(latinIME).apply { id = R.id.agent_wheel }
        root.addView(wheel)
        latinIME.agentEditorController.attach(root)
        assertTrue(listener.onCustomRequest(request))
        assertEquals(android.view.View.VISIBLE, wheel.visibility)
        latinIME.agentEditorController.close()
    }

    @Test fun generatedPreviewCopyRechecksIncognitoEvenBeforeTheUiInvalidationArrives() {
        generatedProtectedPreviewCannotBeExported(true)
    }

    @Test fun generatedStaleCancelErasesBothPreviewsWithoutChangingTheClipboard() {
        generatedProtectedPreviewCannotBeExported(false)
    }

    private fun generatedProtectedPreviewCannotBeExported(copy: Boolean) {
        setText("prefix Hallo suffix")
        setCursorPosition(7, 12)
        val controller = latinIME.agentEditorController
        controller.startEditor(EditorInfo().apply { inputType = currentInputType; initialSelStart = 7; initialSelEnd = 12 })
        val root = android.widget.FrameLayout(latinIME)
        val panel = helium314.keyboard.agent.AgentPanelView(latinIME).apply { id = R.id.agent_panel }
        root.addView(panel); controller.attach(root); controller.open()
        panel.findViewById<android.widget.Button>(R.id.agent_uppercase).performClick()
        assertTrue(panel.findViewById<android.widget.Button>(R.id.agent_copy).isEnabled)
        val clipboard = latinIME.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("test", "keep this"))
        latinIME.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, true).commit()
        // Deliberately bypass the stale enabled/visible state of the view.
        if (copy) panel.onCopy?.invoke() else panel.onCancelCloud?.invoke()
        assertEquals("keep this", clipboard.primaryClip!!.getItemAt(0).text.toString())
        assertEquals("", panel.findViewById<android.widget.TextView>(R.id.agent_source).text.toString())
        assertEquals("", panel.findViewById<android.widget.TextView>(R.id.agent_result).text.toString())
        assertFalse(panel.findViewById<android.widget.Button>(R.id.agent_copy).isEnabled)
        assertEquals("prefix Hallo suffix", text)
    }

    @Test fun generatedCurrentPreviewCopyIsExplicitSensitiveAndNeverWritesTheEditor() {
        setText("prefix Hallo suffix")
        setCursorPosition(7, 12)
        val controller = latinIME.agentEditorController
        controller.startEditor(EditorInfo().apply { inputType = currentInputType; initialSelStart = 7; initialSelEnd = 12 })
        val root = android.widget.FrameLayout(latinIME)
        val panel = helium314.keyboard.agent.AgentPanelView(latinIME).apply { id = R.id.agent_panel }
        root.addView(panel); controller.attach(root); controller.open()
        panel.findViewById<android.widget.Button>(R.id.agent_uppercase).performClick()
        panel.findViewById<android.widget.Button>(R.id.agent_copy).performClick()
        val clipboard = latinIME.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        assertEquals("HALLO", clipboard.primaryClip!!.getItemAt(0).text.toString())
        assertTrue(clipboard.primaryClip!!.description.extras!!.getBoolean("android.content.extra.IS_SENSITIVE"))
        assertEquals("prefix Hallo suffix", text)
    }

    @Test fun generatedOrdinaryTypingDoesNotInitializeCloudTransport() {
        assertFalse(helium314.keyboard.agent.providers.CloudHttpClient.isInitialized)
        input('h'); input('i'); input(' ')
        assertEquals("hi ", text)
        assertFalse(helium314.keyboard.agent.providers.CloudHttpClient.isInitialized)
    }

    @Test fun generatedLocalWorkflowNeedsApprovalThenSeparateApplyAndNeverInitializesAProvider() {
        val workflow = dev.agentkeyboard.core.WorkflowDefinition(java.util.UUID.randomUUID().toString(), 1, "Local proof", listOf(
            dev.agentkeyboard.core.WorkflowStep.Local("upper", dev.agentkeyboard.core.WorkflowInput.Selection, dev.agentkeyboard.core.LocalTextAction.UPPERCASE),
            dev.agentkeyboard.core.WorkflowStep.Local("clean", dev.agentkeyboard.core.WorkflowInput.StepResult("upper"), dev.agentkeyboard.core.LocalTextAction.CLEAN_SPACES)))
        val (panel, journal) = generatedWorkflowSurface(workflow) { error("local workflow must not construct a provider") }
        assertEquals("prefix Hallo suffix", text)
        assertTrue(panel.findViewById<android.widget.Button>(R.id.agent_cloud_send).isEnabled)
        panel.findViewById<android.widget.Button>(R.id.agent_cloud_send).performClick()
        assertEquals("HALLO", panel.findViewById<android.widget.TextView>(R.id.agent_result).text.toString())
        assertEquals("prefix Hallo suffix", text)
        assertTrue(journal.modelStates.isEmpty())
        assertFalse(helium314.keyboard.agent.providers.CloudHttpClient.isInitialized)
        panel.onCancelCloud?.invoke() // A queued cancel can arrive just after a fast local completion.
        panel.onApply?.invoke()
        assertEquals("prefix Hallo suffix", text)
        assertEquals(latinIME.getString(R.string.agent_panel_empty), panel.findViewById<android.widget.TextView>(R.id.agent_result).text.toString())
        panel.onWorkflow?.invoke(workflow)
        panel.findViewById<android.widget.Button>(R.id.agent_cloud_send).performClick()
        panel.findViewById<android.widget.Button>(R.id.agent_apply).performClick()
        assertEquals("prefix HALLO suffix", text)
    }

    @Test fun generatedNonAdjacentWorkflowInputIsDescribedAndExecutedExactly() {
        val workflow = dev.agentkeyboard.core.WorkflowDefinition(java.util.UUID.randomUUID().toString(), 1, "Earlier result", listOf(
            dev.agentkeyboard.core.WorkflowStep.Local("upper", dev.agentkeyboard.core.WorkflowInput.Selection, dev.agentkeyboard.core.LocalTextAction.UPPERCASE),
            dev.agentkeyboard.core.WorkflowStep.Local("lower", dev.agentkeyboard.core.WorkflowInput.StepResult("upper"), dev.agentkeyboard.core.LocalTextAction.LOWERCASE),
            dev.agentkeyboard.core.WorkflowStep.Local("again", dev.agentkeyboard.core.WorkflowInput.StepResult("upper"), dev.agentkeyboard.core.LocalTextAction.CLEAN_SPACES)))
        val (panel, _) = generatedWorkflowSurface(workflow) { error("must stay offline") }
        val expected = latinIME.getString(R.string.agent_workflow_step_description, 3,
            latinIME.getString(R.string.agent_action_spaces), latinIME.getString(R.string.agent_workflow_input_step, 1))
        assertTrue(panel.findViewById<android.widget.TextView>(R.id.agent_cloud_confirmation).text.contains(expected))
        panel.findViewById<android.widget.Button>(R.id.agent_cloud_send).performClick()
        assertEquals("HALLO", panel.findViewById<android.widget.TextView>(R.id.agent_result).text.toString())
        assertEquals("prefix Hallo suffix", text)
    }

    @Test fun generatedCloudWorkflowFreezesTheApprovedDefinitionAndConsumesSendExactlyOnce() {
        val workflow = dev.agentkeyboard.core.WorkflowDefinition(java.util.UUID.randomUUID().toString(), 1, "Cloud proof", listOf(
            dev.agentkeyboard.core.WorkflowStep.Local("upper", dev.agentkeyboard.core.WorkflowInput.Selection, dev.agentkeyboard.core.LocalTextAction.UPPERCASE),
            dev.agentkeyboard.core.WorkflowStep.Ai("translate", dev.agentkeyboard.core.WorkflowInput.StepResult("upper"),
                dev.agentkeyboard.core.AiTextAction.TRANSLATE, dev.agentkeyboard.core.WorkflowProvider.OPENAI,
                "approved-model", dev.agentkeyboard.core.TranslationLanguage.GERMAN)))
        val response = kotlinx.coroutines.CompletableDeferred<String>()
        var calls = 0
        val provider = object : dev.agentkeyboard.core.ModelProvider {
            override val id = "test"; override val capabilities = dev.agentkeyboard.core.ProviderCapabilities()
            override suspend fun generate(request: dev.agentkeyboard.core.ModelRequest, onTextDelta: (String) -> Unit): dev.agentkeyboard.core.ModelResponse {
                calls++
                assertEquals("HALLO", request.input)
                assertEquals("approved-model", request.model)
                assertTrue(request.instruction.contains("German"))
                return dev.agentkeyboard.core.ModelResponse(response.await())
            }
        }
        val (panel, journal) = generatedWorkflowSurface(workflow) { provider }
        assertEquals(0, calls)
        assertTrue(panel.findViewById<android.widget.TextView>(R.id.agent_cloud_confirmation).text.contains("approved-model"))
        val changed = dev.agentkeyboard.core.WorkflowDefinition(workflow.id, 1, "Changed", listOf(
            dev.agentkeyboard.core.WorkflowStep.Ai("first", dev.agentkeyboard.core.WorkflowInput.Selection,
                dev.agentkeyboard.core.AiTextAction.CORRECT, dev.agentkeyboard.core.WorkflowProvider.OPENAI, "changed-model")))
        val store = helium314.keyboard.agent.WorkflowStore(latinIME) { true }
        store.replaceAll(listOf(changed), store.snapshot().generation)
        panel.findViewById<android.widget.Button>(R.id.agent_cloud_send).performClick()
        panel.onSendCloud?.invoke() // Bypass the hidden button to test consumed approval.
        assertEquals(1, calls)
        assertEquals("prefix Hallo suffix", text)
        response.complete("ERGEBNIS")
        awaitAgentUi { panel.findViewById<android.widget.TextView>(R.id.agent_result).text.toString() == "ERGEBNIS" }
        assertEquals(helium314.keyboard.agent.ActionRunState.SUCCEEDED, journal.modelStates.values.single())
        assertEquals("prefix Hallo suffix", text)
        panel.findViewById<android.widget.Button>(R.id.agent_apply).performClick()
        assertEquals("prefix ERGEBNIS suffix", text)
    }

    @Test fun generatedFalseConditionSkipsTheModelAndReportsThatFactInThePreview() {
        val workflow = dev.agentkeyboard.core.WorkflowDefinition(java.util.UUID.randomUUID().toString(), 1, "Conditional proof", listOf(
            dev.agentkeyboard.core.WorkflowStep.Ai("first", dev.agentkeyboard.core.WorkflowInput.Selection,
                dev.agentkeyboard.core.AiTextAction.ANALYZE, dev.agentkeyboard.core.WorkflowProvider.OPENAI, "model",
                condition = dev.agentkeyboard.core.TextCondition(dev.agentkeyboard.core.TextPredicate.CONTAINS_LITERAL, "absent"))))
        val (panel, journal) = generatedWorkflowSurface(workflow) { error("skipped model must not initialize") }
        assertTrue(panel.findViewById<android.widget.TextView>(R.id.agent_cloud_confirmation).text.contains("absent"))
        panel.findViewById<android.widget.Button>(R.id.agent_cloud_send).performClick()
        assertEquals("Hallo", panel.findViewById<android.widget.TextView>(R.id.agent_result).text.toString())
        assertTrue(panel.findViewById<android.widget.TextView>(R.id.agent_status).text.startsWith(latinIME.getString(R.string.agent_workflow_finished, 0, 1)))
        assertTrue(journal.modelStates.isEmpty())
        assertEquals("prefix Hallo suffix", text)
    }

    @Test fun generatedStaleWorkflowApprovalCannotStartAProviderEvenThroughADirectCallback() {
        val workflow = dev.agentkeyboard.core.WorkflowDefinition(java.util.UUID.randomUUID().toString(), 1, "Stale proof", listOf(
            dev.agentkeyboard.core.WorkflowStep.Ai("first", dev.agentkeyboard.core.WorkflowInput.Selection,
                dev.agentkeyboard.core.AiTextAction.CORRECT, dev.agentkeyboard.core.WorkflowProvider.OPENAI, "model")))
        var constructed = 0
        val (panel, journal) = generatedWorkflowSurface(workflow) { constructed++; error("must not construct") }
        latinIME.agentEditorController.invalidate()
        panel.onSendCloud?.invoke()
        assertEquals(0, constructed)
        assertTrue(journal.modelStates.isEmpty())
        assertEquals("", panel.findViewById<android.widget.TextView>(R.id.agent_source).text.toString())
        assertEquals("prefix Hallo suffix", text)
    }

    private fun generatedWorkflowSurface(workflow: dev.agentkeyboard.core.WorkflowDefinition,
                                         provider: () -> dev.agentkeyboard.core.ModelProvider): Pair<helium314.keyboard.agent.AgentPanelView, helium314.keyboard.agent.TestRunJournal> {
        setText("prefix Hallo suffix"); setCursorPosition(7, 12)
        val store = helium314.keyboard.agent.WorkflowStore(latinIME) { true }
        store.replaceAll(listOf(workflow), store.snapshot().generation)
        latinIME.agentEditorController.destroy()
        val journal = helium314.keyboard.agent.TestRunJournal()
        val controller = helium314.keyboard.agent.AgentEditorController(latinIME, inputLogic, { journal }, provider)
        org.robolectric.util.ReflectionHelpers.setField(latinIME, "mAgentEditorController", controller)
        controller.startEditor(EditorInfo().apply { inputType = currentInputType; initialSelStart = 7; initialSelEnd = 12 })
        val root = android.widget.FrameLayout(latinIME)
        val panel = helium314.keyboard.agent.AgentPanelView(latinIME).apply { id = R.id.agent_panel }
        root.addView(panel); controller.attach(root); controller.open()
        panel.findViewById<android.widget.Button>(R.id.agent_workflows).performClick()
        awaitAgentUi { panel.findViewWithTag<android.widget.Button>(workflow.id) != null }
        panel.findViewWithTag<android.widget.Button>(workflow.id).performClick()
        return panel to journal
    }

    @Test fun generatedCloudCorrectionNeedsSendAndThenSeparateEditorApply() {
        generatedCloudActionFlow(dev.agentkeyboard.core.AiTextAction.CORRECT)
    }

    @Test fun generatedCloudTranslationUsesTheLanguageApprovedBeforeSend() {
        helium314.keyboard.agent.ModelSettings(latinIME).setTranslationLanguage(dev.agentkeyboard.core.TranslationLanguage.GERMAN)
        generatedCloudActionFlow(dev.agentkeyboard.core.AiTextAction.TRANSLATE)
    }

    @Test fun generatedCloudRephrasingUsesTheToneApprovedBeforeSend() {
        helium314.keyboard.agent.ModelSettings(latinIME).setWritingTone(dev.agentkeyboard.core.WritingTone.PROFESSIONAL)
        generatedCloudActionFlow(dev.agentkeyboard.core.AiTextAction.REPHRASE)
    }

    @Test fun generatedCloudSummaryUsesTheGuardedSendAndApplyFlow() {
        generatedCloudActionFlow(dev.agentkeyboard.core.AiTextAction.SUMMARIZE)
    }

    @Test fun generatedCloudReplyIsOnlyADraftUntilExplicitEditorApply() {
        helium314.keyboard.agent.ModelSettings(latinIME).setWritingTone(dev.agentkeyboard.core.WritingTone.PROFESSIONAL)
        generatedCloudActionFlow(dev.agentkeyboard.core.AiTextAction.REPLY)
    }

    @Test fun generatedContextAnalysisCannotReplaceTheSourceSelection() {
        generatedCloudActionFlow(dev.agentkeyboard.core.AiTextAction.ANALYZE)
    }

    @Test fun generatedNativeSkillKeyPreparesLocalResultWithoutWriting() {
        val panel = prepareSkillEditor()
        helium314.keyboard.agent.SkillBindingStore(latinIME) { true }
            .set(dev.agentkeyboard.core.SkillBinding(1, "Case", dev.agentkeyboard.core.BuiltinAction.UPPERCASE))
        latinIME.mKeyboardActionListener.onCodeInput(KeyCode.SKILL_1, -1, -1, false)
        awaitAgentUi { panel.findViewById<android.widget.Button>(R.id.agent_apply).isEnabled }
        assertEquals("prefix Hallo suffix", text)
        assertEquals("HALLO", panel.findViewById<android.widget.TextView>(R.id.agent_result).text.toString())
        panel.findViewById<android.widget.Button>(R.id.agent_apply).performClick()
        assertEquals("prefix HALLO suffix", text)
    }

    @Test fun generatedNativeAiSkillKeyRequiresItsOwnSendApproval() {
        val panel = prepareSkillEditor()
        helium314.keyboard.agent.SkillBindingStore(latinIME) { true }
            .set(dev.agentkeyboard.core.SkillBinding(2, "Translate", dev.agentkeyboard.core.BuiltinAction.TRANSLATE))
        latinIME.mKeyboardActionListener.onCodeInput(KeyCode.SKILL_2, -1, -1, false)
        awaitAgentUi { panel.findViewById<android.widget.Button>(R.id.agent_cloud_send).visibility == android.view.View.VISIBLE }
        assertFalse(helium314.keyboard.agent.providers.CloudHttpClient.isInitialized)
        assertFalse(panel.findViewById<android.widget.Button>(R.id.agent_apply).isEnabled)
        assertEquals("prefix Hallo suffix", text)
        latinIME.agentEditorController.close()
    }

    @Test fun generatedNativeWorkflowSkillPreparesApprovalAndStillNeedsSeparateApply() {
        val panel = prepareSkillEditor()
        val workflow = nativeSkillWorkflow(false)
        saveNativeSkillWorkflow(workflow)
        helium314.keyboard.agent.SkillBindingStore(latinIME) { true }
            .set(dev.agentkeyboard.core.SkillBinding(1, "Workflow", dev.agentkeyboard.core.BuiltinAction.WORKFLOW, workflow.id))
        latinIME.mKeyboardActionListener.onCodeInput(KeyCode.SKILL_1, -1, -1, false)
        awaitAgentUi { panel.findViewById<android.widget.Button>(R.id.agent_cloud_send).visibility == android.view.View.VISIBLE }
        assertFalse(panel.findViewById<android.widget.Button>(R.id.agent_apply).isEnabled)
        assertEquals("prefix Hallo suffix", text)
        panel.findViewById<android.widget.Button>(R.id.agent_cloud_send).performClick()
        assertEquals("HALLO", panel.findViewById<android.widget.TextView>(R.id.agent_result).text.toString())
        assertFalse(helium314.keyboard.agent.providers.CloudHttpClient.isInitialized)
        assertEquals("prefix Hallo suffix", text)
        panel.findViewById<android.widget.Button>(R.id.agent_apply).performClick()
        assertEquals("prefix HALLO suffix", text)
    }

    @Test fun generatedNativeCloudWorkflowSkillShowsActualModelInsteadOfTrustingItsLabel() {
        val panel = prepareSkillEditor()
        val workflow = nativeSkillWorkflow(true)
        saveNativeSkillWorkflow(workflow)
        helium314.keyboard.agent.SkillBindingStore(latinIME) { true }
            .set(dev.agentkeyboard.core.SkillBinding(2, "LOCAL ONLY", dev.agentkeyboard.core.BuiltinAction.WORKFLOW, workflow.id))
        latinIME.mKeyboardActionListener.onCodeInput(KeyCode.SKILL_2, -1, -1, false)
        awaitAgentUi { panel.findViewById<android.widget.Button>(R.id.agent_cloud_send).visibility == android.view.View.VISIBLE }
        val confirmation = panel.findViewById<android.widget.TextView>(R.id.agent_cloud_confirmation).text.toString()
        assertTrue(confirmation.contains("skill-model"))
        assertTrue(confirmation.contains("openai"))
        assertFalse(confirmation.contains("LOCAL ONLY"))
        assertFalse(helium314.keyboard.agent.providers.CloudHttpClient.isInitialized)
        assertEquals("prefix Hallo suffix", text)
    }

    @Test fun generatedMissingWorkflowSkillNeverUsesAnotherSavedWorkflowAsAFallback() {
        val panel = prepareSkillEditor()
        saveNativeSkillWorkflow(nativeSkillWorkflow(false))
        helium314.keyboard.agent.SkillBindingStore(latinIME) { true }
            .set(dev.agentkeyboard.core.SkillBinding(3, "Missing", dev.agentkeyboard.core.BuiltinAction.WORKFLOW, java.util.UUID.randomUUID().toString()))
        latinIME.mKeyboardActionListener.onCodeInput(KeyCode.SKILL_3, -1, -1, false)
        awaitAgentUi { panel.findViewById<android.widget.TextView>(R.id.agent_status).text.toString() == latinIME.getString(R.string.agent_skill_workflow_missing) }
        assertEquals(android.view.View.GONE, panel.findViewById<android.widget.Button>(R.id.agent_cloud_send).visibility)
        panel.onSendCloud?.invoke()
        assertFalse(helium314.keyboard.agent.providers.CloudHttpClient.isInitialized)
        assertEquals("prefix Hallo suffix", text)
    }

    @Test fun generatedWorkflowSkillMetadataCannotPublishAfterItsSelectionExpires() {
        val panel = prepareSkillEditor()
        val workflow = nativeSkillWorkflow(true)
        saveNativeSkillWorkflow(workflow)
        helium314.keyboard.agent.SkillBindingStore(latinIME) { true }
            .set(dev.agentkeyboard.core.SkillBinding(1, "Workflow", dev.agentkeyboard.core.BuiltinAction.WORKFLOW, workflow.id))
        latinIME.mKeyboardActionListener.onCodeInput(KeyCode.SKILL_1, -1, -1, false)
        latinIME.agentEditorController.invalidate()
        awaitAgentUi { panel.findViewById<android.widget.TextView>(R.id.agent_status).text.toString() == latinIME.getString(R.string.agent_panel_stale) }
        assertEquals(android.view.View.GONE, panel.findViewById<android.widget.Button>(R.id.agent_cloud_send).visibility)
        assertEquals("", panel.findViewById<android.widget.TextView>(R.id.agent_source).text.toString())
        assertFalse(helium314.keyboard.agent.providers.CloudHttpClient.isInitialized)
    }

    @Test fun typedWorkflowCommandPreparesTheSavedWorkflowLikeASkillKeyAndNeedsItsApproval() {
        val panel = prepareSkillEditor()
        val workflow = nativeSkillWorkflow(true)
        saveNativeSkillWorkflow(workflow)
        assertTrue(latinIME.agentEditorController.openWorkflow(workflow.id))
        awaitAgentUi { panel.findViewById<android.widget.Button>(R.id.agent_cloud_send).visibility == android.view.View.VISIBLE }
        assertEquals(latinIME.getString(R.string.agent_workflow_run_cloud), panel.findViewById<android.widget.Button>(R.id.agent_cloud_send).text.toString())
        assertTrue(panel.findViewById<android.widget.TextView>(R.id.agent_cloud_confirmation).text.contains("skill-model"))
        // nothing ran and nothing changed in the field before the approval
        assertFalse(helium314.keyboard.agent.providers.CloudHttpClient.isInitialized)
        assertEquals("prefix Hallo suffix", text)

        latinIME.agentEditorController.close()
        assertTrue(latinIME.agentEditorController.openWorkflow(java.util.UUID.randomUUID().toString()))
        awaitAgentUi { panel.findViewById<android.widget.TextView>(R.id.agent_status).text.toString() == latinIME.getString(R.string.agent_skill_workflow_missing) }
        assertEquals(android.view.View.GONE, panel.findViewById<android.widget.Button>(R.id.agent_cloud_send).visibility)
    }

    @Test fun typedWorkflowCommandWithoutSelectionShowsThePanelMessageAndPreparesNothing() {
        val panel = prepareSkillEditor()
        val workflow = nativeSkillWorkflow(false)
        saveNativeSkillWorkflow(workflow)
        setCursorPosition(19)
        latinIME.agentEditorController.startEditor(EditorInfo().apply { inputType = currentInputType; initialSelStart = 19; initialSelEnd = 19 })
        assertTrue(latinIME.agentEditorController.openWorkflow(workflow.id))
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        // nothing selected: the panel says the workflow waits for a selection
        assertEquals(latinIME.getString(R.string.agent_command_wf_pending), panelText(panel, R.id.agent_status))
        assertEquals(android.view.View.GONE, panel.findViewById<android.widget.Button>(R.id.agent_cloud_send).visibility)
        assertEquals(android.view.View.VISIBLE, panel.visibility)
        assertEquals("prefix Hallo suffix", text)
    }

    /** Reports the editor's current selection and composition to [controller], as the app does after each change. */
    private fun reportEditorState(controller: helium314.keyboard.agent.AgentEditorController) =
        controller.selectionChanged(selectionStart, selectionEnd, composingStart, composingEnd)

    /** Taps the chip labelled [label] in a fresh chip row for the text before the cursor. */
    private fun tapCommandChip(label: String) {
        val chips = helium314.keyboard.agent.KeyboardCommands.createChipsView(latinIME, android.widget.FrameLayout(latinIME))
        assertNotNull(chips)
        val row = (chips as android.view.ViewGroup).getChildAt(0) as android.view.ViewGroup
        (0 until row.childCount).map { row.getChildAt(it) as android.widget.TextView }
            .single { it.text.toString() == label }
            .performClick()
    }

    @Test fun typedWorkflowCommandIsPreparedWhenThePanelOpensAgainWithSelectedText() {
        val panel = prepareSkillEditor()
        val controller = latinIME.agentEditorController
        val workflow = nativeSkillWorkflow(true)
        saveNativeSkillWorkflow(workflow)
        setText("")
        helium314.keyboard.agent.KeyboardCommands.load(latinIME)
        chainInput("x @wf Saved")
        controller.selectionChanged(text.length, text.length, composingStart, composingEnd)
        tapCommandChip(latinIME.getString(R.string.agent_command_wf_label, "Saved workflow"))
        // the chip changes nothing in the field; the panel asks for the text
        assertEquals("x @wf Saved", text)
        assertEquals(android.view.View.VISIBLE, panel.visibility)
        assertEquals(latinIME.getString(R.string.agent_command_wf_pending), panelText(panel, R.id.agent_status))
        assertEquals(android.view.View.GONE, panel.findViewById<android.widget.Button>(R.id.agent_cloud_send).visibility)

        // the user closes the panel, selects text and opens the text actions again (pen key)
        panel.onClose?.invoke()
        setCursorPosition(0, 1)
        reportEditorState(controller) // the editor reports the ended composition, as an app does
        controller.open()
        awaitAgentUi { panel.findViewById<android.widget.Button>(R.id.agent_cloud_send).visibility == android.view.View.VISIBLE }
        assertEquals("x", panelText(panel, R.id.agent_source))
        assertTrue(panelText(panel, R.id.agent_cloud_confirmation).contains("skill-model"))
        // nothing ran and nothing changed before the approval
        assertFalse(helium314.keyboard.agent.providers.CloudHttpClient.isInitialized)
        assertEquals("x @wf Saved", text)

        // offered once: the next panel is the plain one
        controller.close()
        controller.open()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(android.view.View.GONE, panel.findViewById<android.widget.Button>(R.id.agent_cloud_send).visibility)
        assertEquals("x", panelText(panel, R.id.agent_source))
        assertFalse(helium314.keyboard.agent.providers.CloudHttpClient.isInitialized)
    }

    @Test fun typedWorkflowChoiceIsDroppedWhenTheEditorChanges() {
        val panel = prepareSkillEditor()
        val controller = latinIME.agentEditorController
        val workflow = nativeSkillWorkflow(true)
        saveNativeSkillWorkflow(workflow)
        setText("")
        helium314.keyboard.agent.KeyboardCommands.load(latinIME)
        chainInput("x @wf Saved")
        controller.selectionChanged(text.length, text.length, composingStart, composingEnd)
        tapCommandChip(latinIME.getString(R.string.agent_command_wf_label, "Saved workflow"))
        assertEquals(latinIME.getString(R.string.agent_command_wf_pending), panelText(panel, R.id.agent_status))
        panel.onClose?.invoke()
        // another field: the choice is gone
        setText("prefix Hallo suffix")
        setCursorPosition(7, 12)
        reportEditorState(controller)
        controller.open()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals("Hallo", panelText(panel, R.id.agent_source))
        assertEquals(android.view.View.GONE, panel.findViewById<android.widget.Button>(R.id.agent_cloud_send).visibility)
        assertFalse(helium314.keyboard.agent.providers.CloudHttpClient.isInitialized)
    }

    @Test fun typedWorkflowChoiceIsDroppedInIncognito() {
        val panel = prepareSkillEditor()
        val controller = latinIME.agentEditorController
        val workflow = nativeSkillWorkflow(true)
        saveNativeSkillWorkflow(workflow)
        setCursorPosition(19)
        controller.startEditor(EditorInfo().apply { inputType = currentInputType; initialSelStart = 19; initialSelEnd = 19 })
        assertTrue(controller.openWorkflow(workflow.id))
        assertEquals(latinIME.getString(R.string.agent_command_wf_pending), panelText(panel, R.id.agent_status))
        panel.onClose?.invoke()
        latinIME.prefs().edit().putBoolean(helium314.keyboard.latin.settings.Settings.PREF_ALWAYS_INCOGNITO_MODE, true).commit()
        try {
            setCursorPosition(7, 12)
            reportEditorState(controller)
            controller.open() // denied in incognito: the choice is dropped
            assertNotEquals(latinIME.getString(R.string.agent_command_wf_pending), panelText(panel, R.id.agent_status))
        } finally {
            latinIME.prefs().edit().putBoolean(helium314.keyboard.latin.settings.Settings.PREF_ALWAYS_INCOGNITO_MODE, false).commit()
        }
        controller.close()
        controller.open()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals("Hallo", panelText(panel, R.id.agent_source))
        assertEquals(android.view.View.GONE, panel.findViewById<android.widget.Button>(R.id.agent_cloud_send).visibility)
    }

    private fun nativeSkillWorkflow(cloud: Boolean) = dev.agentkeyboard.core.WorkflowDefinition(
        java.util.UUID.randomUUID().toString(), 1, "Saved workflow", if (cloud) listOf(
            dev.agentkeyboard.core.WorkflowStep.Ai("first", dev.agentkeyboard.core.WorkflowInput.Selection,
                dev.agentkeyboard.core.AiTextAction.CORRECT, dev.agentkeyboard.core.WorkflowProvider.OPENAI, "skill-model"))
        else listOf(dev.agentkeyboard.core.WorkflowStep.Local("first", dev.agentkeyboard.core.WorkflowInput.Selection,
            dev.agentkeyboard.core.LocalTextAction.UPPERCASE)))

    private fun saveNativeSkillWorkflow(workflow: dev.agentkeyboard.core.WorkflowDefinition) {
        val store = helium314.keyboard.agent.WorkflowStore(latinIME) { true }
        store.replaceAll(listOf(workflow), store.snapshot().generation)
    }

    private fun prepareSkillEditor(): helium314.keyboard.agent.AgentPanelView {
        setText("prefix Hallo suffix")
        setCursorPosition(7, 12)
        latinIME.agentEditorController.destroy()
        val controller = helium314.keyboard.agent.AgentEditorController(latinIME, inputLogic,
            { helium314.keyboard.agent.TestRunJournal() })
        org.robolectric.util.ReflectionHelpers.setField(latinIME, "mAgentEditorController", controller)
        controller.startEditor(EditorInfo().apply { inputType = currentInputType; initialSelStart = 7; initialSelEnd = 12 })
        val root = android.widget.FrameLayout(latinIME)
        val panel = helium314.keyboard.agent.AgentPanelView(latinIME).apply { id = R.id.agent_panel }
        root.addView(panel)
        controller.attach(root)
        return panel
    }

    private fun awaitAgentUi(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 2_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            // Legacy fixture captures Handler messages globally. Dispatch actual
            // Runnable callbacks as Handler.dispatchMessage does, rather than
            // passing coroutine continuations to LatinIME's integer-message handler.
            for (queue in listOf(messages, delayedMessages)) {
                val callbacks = synchronized(queue) {
                    queue.filter { it.callback != null }.also { queue.removeAll(it.toSet()) }
                }
                callbacks.forEach { it.callback?.run() }
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if (!condition()) Thread.sleep(5)
        }
        assertTrue(condition(), "Agent UI did not finish within the test deadline")
    }

    private fun generatedCloudActionFlow(action: dev.agentkeyboard.core.AiTextAction) {
        setText("prefix Hallo suffix")
        setCursorPosition(7, 12)
        val response = kotlinx.coroutines.CompletableDeferred<String>()
        var calls = 0
        val provider = object : dev.agentkeyboard.core.ModelProvider {
            override val id = "offline-test"
            override val capabilities = dev.agentkeyboard.core.ProviderCapabilities()
            override suspend fun generate(request: dev.agentkeyboard.core.ModelRequest, onTextDelta: (String) -> Unit): dev.agentkeyboard.core.ModelResponse {
                calls++
                assertEquals("Hallo", request.input)
                if (action == dev.agentkeyboard.core.AiTextAction.TRANSLATE) {
                    assertTrue(request.instruction.contains("German"))
                    assertFalse(request.instruction.contains("French"))
                }
                if (action == dev.agentkeyboard.core.AiTextAction.REPHRASE || action == dev.agentkeyboard.core.AiTextAction.REPLY) {
                    assertTrue(request.instruction.contains("professional"))
                    assertFalse(request.instruction.contains("friendly"))
                }
                return dev.agentkeyboard.core.ModelResponse(response.await())
            }
        }
        latinIME.agentEditorController.destroy()
        val journal = helium314.keyboard.agent.TestRunJournal()
        val controller = helium314.keyboard.agent.AgentEditorController(latinIME, inputLogic, { journal }) { provider }
        org.robolectric.util.ReflectionHelpers.setField(latinIME, "mAgentEditorController", controller)
        controller.startEditor(EditorInfo().apply { inputType = currentInputType; initialSelStart = 7; initialSelEnd = 12 })
        val root = android.widget.FrameLayout(latinIME)
        val panel = helium314.keyboard.agent.AgentPanelView(latinIME).apply { id = R.id.agent_panel }
        root.addView(panel)
        controller.attach(root)
        controller.open()
        panel.findViewById<android.widget.Button>(when (action) {
            dev.agentkeyboard.core.AiTextAction.CORRECT -> R.id.agent_cloud_correct
            dev.agentkeyboard.core.AiTextAction.TRANSLATE -> R.id.agent_cloud_translate
            dev.agentkeyboard.core.AiTextAction.REPHRASE -> R.id.agent_cloud_rephrase
            dev.agentkeyboard.core.AiTextAction.SUMMARIZE -> R.id.agent_cloud_summarize
            dev.agentkeyboard.core.AiTextAction.REPLY -> R.id.agent_cloud_reply
            dev.agentkeyboard.core.AiTextAction.ANALYZE -> R.id.agent_cloud_analyze
            dev.agentkeyboard.core.AiTextAction.ASK -> error("a question has no panel button, only the typed command")
            dev.agentkeyboard.core.AiTextAction.TRANSLATE_STYLED -> error("a styled translation has no panel button, only the typed command")
        }).performClick()
        if (action == dev.agentkeyboard.core.AiTextAction.TRANSLATE) {
            // Configuration changes after the displayed approval cannot silently
            // change the task that the user is about to send.
            helium314.keyboard.agent.ModelSettings(latinIME).setTranslationLanguage(dev.agentkeyboard.core.TranslationLanguage.FRENCH)
        }
        if (action == dev.agentkeyboard.core.AiTextAction.REPHRASE || action == dev.agentkeyboard.core.AiTextAction.REPLY) {
            helium314.keyboard.agent.ModelSettings(latinIME).setWritingTone(dev.agentkeyboard.core.WritingTone.FRIENDLY)
        }
        assertEquals(0, calls)
        panel.findViewById<android.widget.Button>(R.id.agent_cloud_send).performClick()
        assertEquals(1, calls)
        assertEquals("prefix Hallo suffix", text)
        response.complete("HALLO")
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(helium314.keyboard.agent.ActionRunState.SUCCEEDED, journal.modelStates.values.single())
        assertEquals("prefix Hallo suffix", text)
        assertEquals("HALLO", panel.findViewById<android.widget.TextView>(R.id.agent_result).text.toString())
        if (action == dev.agentkeyboard.core.AiTextAction.ANALYZE) {
            assertFalse(panel.findViewById<android.widget.Button>(R.id.agent_apply).isEnabled)
            assertTrue(panel.findViewById<android.widget.Button>(R.id.agent_copy).isEnabled)
            panel.findViewById<android.widget.Button>(R.id.agent_apply).performClick()
            panel.onApply?.invoke() // Controller must also reject bypassing the disabled view.
            assertEquals("prefix Hallo suffix", text)
            controller.destroy()
            return
        }
        panel.findViewById<android.widget.Button>(R.id.agent_apply).performClick()
        assertEquals("prefix HALLO suffix", text)
        controller.destroy()
    }

    /** A test provider that records every request and answers with [response]. */
    private class QuestionProvider(val response: kotlinx.coroutines.CompletableDeferred<String> = kotlinx.coroutines.CompletableDeferred()) :
        dev.agentkeyboard.core.ModelProvider {
        val requests = mutableListOf<dev.agentkeyboard.core.ModelRequest>()
        override val id = "offline-test"
        override val capabilities = dev.agentkeyboard.core.ProviderCapabilities()
        override suspend fun generate(request: dev.agentkeyboard.core.ModelRequest, onTextDelta: (String) -> Unit): dev.agentkeyboard.core.ModelResponse {
            requests += request
            return dev.agentkeyboard.core.ModelResponse(response.await())
        }
    }

    /** A controller with the test provider and journal, attached to a panel; the field is started empty. */
    private fun questionSurface(provider: QuestionProvider, journal: helium314.keyboard.agent.TestRunJournal):
            Pair<helium314.keyboard.agent.AgentEditorController, helium314.keyboard.agent.AgentPanelView> {
        latinIME.agentEditorController.destroy()
        val controller = helium314.keyboard.agent.AgentEditorController(latinIME, inputLogic, { journal }) { provider }
        org.robolectric.util.ReflectionHelpers.setField(latinIME, "mAgentEditorController", controller)
        val root = android.widget.FrameLayout(latinIME)
        val panel = helium314.keyboard.agent.AgentPanelView(latinIME).apply { id = R.id.agent_panel }
        root.addView(panel)
        controller.attach(root)
        setText("")
        controller.startEditor(EditorInfo().apply { inputType = currentInputType; initialSelStart = 0; initialSelEnd = 0 })
        helium314.keyboard.agent.KeyboardCommands.load(latinIME)
        return controller to panel
    }

    /**
     * Types [typed], reports the cursor like the editor would and opens the panel's send preview of
     * [request] for the typed command: the details view a failed request in the strip opens.
     */
    private fun openQuestionPanel(controller: helium314.keyboard.agent.AgentEditorController, typed: String, request: String,
                                  action: dev.agentkeyboard.core.AiTextAction = dev.agentkeyboard.core.AiTextAction.ASK) {
        chainInput(typed)
        controller.selectionChanged(text.length, text.length, composingStart, composingEnd)
        assertTrue(controller.openQuestion(request, text.substring(text.indexOf('@')), text.length, action))
    }

    private fun panelText(panel: android.view.View, id: Int) = panel.findViewById<android.widget.TextView>(id).text.toString()

    @Test fun typedQuestionPanelIsSentOnlyAfterSendAndTheAnswerReplacesTheCommandOnlyAfterInsert() {
        val provider = QuestionProvider()
        val journal = helium314.keyboard.agent.TestRunJournal()
        val (controller, panel) = questionSurface(provider, journal)
        openQuestionPanel(controller, "x @ki wie hoch ist der Mount Everest", "wie hoch ist der Mount Everest")
        // the field is unchanged and the panel shows exactly the text that would be sent
        assertEquals("x @ki wie hoch ist der Mount Everest", text)
        assertEquals(android.view.View.VISIBLE, panel.visibility)
        assertEquals("wie hoch ist der Mount Everest", panelText(panel, R.id.agent_source))
        val confirmation = panelText(panel, R.id.agent_cloud_confirmation)
        assertTrue(confirmation.contains(helium314.keyboard.agent.ModelSettings.DEFAULT_MODEL))
        assertTrue(confirmation.contains("OpenAI"))
        assertTrue(confirmation.contains("30")) // characters of the question
        assertEquals(android.view.View.VISIBLE, panel.findViewById<android.view.View>(R.id.agent_cloud_send).visibility)
        // the selection actions stay off: there is no selection
        assertFalse(panel.findViewById<android.widget.Button>(R.id.agent_cloud_correct).isEnabled)
        assertFalse(panel.findViewById<android.widget.Button>(R.id.agent_uppercase).isEnabled)
        panel.onPrepareCloud?.invoke(dev.agentkeyboard.core.AiTextAction.CORRECT) // bypassing the disabled view changes nothing
        assertEquals(android.view.View.VISIBLE, panel.findViewById<android.view.View>(R.id.agent_cloud_send).visibility)
        assertTrue(provider.requests.isEmpty())
        assertTrue(journal.states.isEmpty())

        panel.findViewById<android.widget.Button>(R.id.agent_cloud_send).performClick()
        panel.onSendCloud?.invoke() // the approval is consumed: a second send starts nothing
        assertEquals(1, provider.requests.size)
        val request = provider.requests.single()
        assertEquals("wie hoch ist der Mount Everest", request.input) // exactly the shown question, nothing else of the field
        assertEquals(dev.agentkeyboard.core.AiTextAction.ASK.instruction(), request.instruction)
        assertEquals(helium314.keyboard.agent.ModelSettings.DEFAULT_MODEL, request.model)
        assertEquals("x @ki wie hoch ist der Mount Everest", text)

        provider.response.complete("8849 m")
        awaitAgentUi { panelText(panel, R.id.agent_result) == "8849 m" }
        assertEquals(helium314.keyboard.agent.ActionRunState.SUCCEEDED, journal.modelStates.values.single())
        assertEquals(helium314.keyboard.agent.RunSubject.TextAction(dev.agentkeyboard.core.AiTextAction.ASK), journal.subjects.values.single())
        assertEquals("x @ki wie hoch ist der Mount Everest", text) // nothing is replaced automatically
        val insert = panel.findViewById<android.widget.Button>(R.id.agent_apply)
        assertTrue(insert.isEnabled)
        assertEquals(latinIME.getString(R.string.agent_command_ki_insert), insert.text.toString())
        assertTrue(panel.findViewById<android.widget.Button>(R.id.agent_copy).isEnabled)

        insert.performClick()
        assertEquals("x 8849 m", text) // exactly the command is replaced, in one commit
        assertEquals(android.view.View.GONE, panel.visibility)
        assertTrue(inputLogic.isAgentHistorySuppressed) // generated text is not learned from
        checkConnectionConsistency()
        panel.onApply?.invoke() // a second tap has nothing left to insert
        assertEquals("x 8849 m", text)
        controller.destroy()
    }

    @Test fun typedQuestionPanelWithoutAKeyShowsTheWayToTheSettingsAndSendsNothing() {
        val provider = QuestionProvider()
        val journal = helium314.keyboard.agent.TestRunJournal()
        val (controller, panel) = questionSurface(provider, journal)
        controller.credentialCheck = { false }
        openQuestionPanel(controller, "x @ki hallo", "hallo")
        assertEquals(latinIME.getString(R.string.agent_command_ki_credentials), panelText(panel, R.id.agent_status))
        assertEquals(android.view.View.GONE, panel.findViewById<android.view.View>(R.id.agent_cloud_send).visibility)
        assertTrue(panel.findViewById<android.view.View>(R.id.agent_cloud_settings).isEnabled) // the way to the AI settings
        panel.onSendCloud?.invoke() // even a direct send has no approval to use
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(provider.requests.isEmpty())
        assertTrue(journal.states.isEmpty())
        assertEquals("x @ki hallo", text)
        controller.destroy()
    }

    @Test fun cancelledQuestionPanelShowsNoAnswerAndChangesNothing() {
        val provider = QuestionProvider()
        val journal = helium314.keyboard.agent.TestRunJournal()
        val (controller, panel) = questionSurface(provider, journal)
        openQuestionPanel(controller, "x @ki hallo", "hallo")
        panel.findViewById<android.widget.Button>(R.id.agent_cloud_cancel).performClick() // before sending
        assertTrue(provider.requests.isEmpty())
        assertEquals("hallo", panelText(panel, R.id.agent_source))
        assertEquals(latinIME.getString(R.string.agent_cloud_cancelled), panelText(panel, R.id.agent_status))
        panel.onSendCloud?.invoke() // the cancelled approval is gone
        assertTrue(provider.requests.isEmpty())

        openQuestionPanel(controller, "", "hallo") // again: a fresh preview
        panel.findViewById<android.widget.Button>(R.id.agent_cloud_send).performClick()
        assertEquals(1, provider.requests.size)
        panel.findViewById<android.widget.Button>(R.id.agent_cloud_cancel).performClick() // while waiting
        provider.response.complete("zu spät")
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(helium314.keyboard.agent.ActionRunState.CANCELLED, journal.modelStates.values.single())
        assertEquals(latinIME.getString(R.string.agent_panel_empty), panelText(panel, R.id.agent_result)) // no answer shown
        assertFalse(panel.findViewById<android.widget.Button>(R.id.agent_apply).isEnabled)
        panel.onApply?.invoke()
        assertEquals("x @ki hallo", text)
        controller.destroy()
    }

    @Test fun answerIsInsertedAtTheCursorWhenTheCommandIsGone() {
        val provider = QuestionProvider()
        val journal = helium314.keyboard.agent.TestRunJournal()
        val (controller, panel) = questionSurface(provider, journal)
        setText("x ")
        setCursorPosition(2)
        controller.startEditor(EditorInfo().apply { inputType = currentInputType; initialSelStart = 2; initialSelEnd = 2 })
        controller.selectionChanged(2, 2, -1, -1)
        assertTrue(controller.openQuestion("frage", "@ki frage", 11)) // the command is no longer there
        panel.findViewById<android.widget.Button>(R.id.agent_cloud_send).performClick()
        assertEquals("frage", provider.requests.single().input)
        provider.response.complete("Antwort")
        awaitAgentUi { panelText(panel, R.id.agent_result) == "Antwort" }
        assertEquals("x ", text)
        panel.findViewById<android.widget.Button>(R.id.agent_apply).performClick()
        assertEquals("x Antwort", text)
        assertEquals(android.view.View.GONE, panel.visibility)
        controller.destroy()
    }

    @Test fun questionIsNotPreparedInProtectedFields() {
        val provider = QuestionProvider()
        val journal = helium314.keyboard.agent.TestRunJournal()
        val (controller, panel) = questionSurface(provider, journal)
        controller.startEditor(EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; initialSelStart = 0; initialSelEnd = 0 })
        // even a direct call cannot prepare a request from a password field
        assertTrue(controller.openQuestion("hallo", "@ki hallo", 0))
        assertEquals(latinIME.getString(R.string.agent_panel_protected), panelText(panel, R.id.agent_status))
        assertEquals(android.view.View.GONE, panel.findViewById<android.view.View>(R.id.agent_cloud_send).visibility)
        panel.onSendCloud?.invoke()
        assertTrue(provider.requests.isEmpty())
        controller.destroy()
    }

    /** Types [typed], reports the cursor like the editor would and taps the chip labelled [label]. */
    private fun tapCommandChip(controller: helium314.keyboard.agent.AgentEditorController, typed: String, label: String) {
        chainInput(typed)
        controller.selectionChanged(text.length, text.length, composingStart, composingEnd)
        val chips = helium314.keyboard.agent.KeyboardCommands.createChipsView(latinIME, android.widget.FrameLayout(latinIME))
        assertNotNull(chips)
        val row = (chips as android.view.ViewGroup).getChildAt(0) as android.view.ViewGroup
        (0 until row.childCount).map { row.getChildAt(it) as android.widget.TextView }
            .single { it.text.toString() == label }
            .performClick()
    }

    private val translationRequest =
        "Translation style:\nnatürliches slang spanisch\n\nText to translate:\nwann treffen wir uns morgen du geile sau"

    @Test fun typedTranslationPanelIsSentOnlyAfterSendAndReplacesTheCommandOnlyAfterInsert() {
        val provider = QuestionProvider()
        val journal = helium314.keyboard.agent.TestRunJournal()
        val (controller, panel) = questionSurface(provider, journal)
        val typed = "x @translate \"wann treffen wir uns morgen du geile sau\" natürliches slang spanisch"
        openQuestionPanel(controller, typed, translationRequest, dev.agentkeyboard.core.AiTextAction.TRANSLATE_STYLED)
        // the field is unchanged and the panel shows exactly the request: style and text, labelled
        assertEquals(typed, text)
        assertEquals(android.view.View.VISIBLE, panel.visibility)
        assertEquals(translationRequest, panelText(panel, R.id.agent_source))
        assertEquals(Int.MAX_VALUE, panel.findViewById<android.widget.TextView>(R.id.agent_source).maxLines) // never cut off
        val confirmation = panelText(panel, R.id.agent_cloud_confirmation)
        assertEquals(latinIME.getString(R.string.agent_command_translate_confirm, helium314.keyboard.agent.ModelSettings.DEFAULT_MODEL,
            translationRequest.length, "OpenAI"), confirmation)
        assertEquals(latinIME.getString(R.string.agent_command_translate_send, "OpenAI"), panelText(panel, R.id.agent_cloud_send))
        // the selection actions stay off, and bypassing them changes nothing
        assertFalse(panel.findViewById<android.widget.Button>(R.id.agent_cloud_translate).isEnabled)
        panel.onPrepareCloud?.invoke(dev.agentkeyboard.core.AiTextAction.TRANSLATE_STYLED)
        panel.onPrepareCloud?.invoke(dev.agentkeyboard.core.AiTextAction.CORRECT)
        assertEquals(confirmation, panelText(panel, R.id.agent_cloud_confirmation))
        assertTrue(provider.requests.isEmpty())
        assertTrue(journal.states.isEmpty())

        panel.findViewById<android.widget.Button>(R.id.agent_cloud_send).performClick()
        panel.onSendCloud?.invoke() // the approval is consumed: a second send starts nothing
        assertEquals(1, provider.requests.size)
        val request = provider.requests.single()
        assertEquals(translationRequest, request.input) // exactly the shown request, nothing else of the field
        assertEquals(dev.agentkeyboard.core.AiTextAction.TRANSLATE_STYLED.instruction(), request.instruction)
        assertEquals(helium314.keyboard.agent.ModelSettings.DEFAULT_MODEL, request.model)
        assertEquals(typed, text)

        provider.response.complete("¿cuándo nos vemos mañana, cabrón?")
        awaitAgentUi { panelText(panel, R.id.agent_result) == "¿cuándo nos vemos mañana, cabrón?" }
        assertEquals(helium314.keyboard.agent.ActionRunState.SUCCEEDED, journal.modelStates.values.single())
        assertEquals(helium314.keyboard.agent.RunSubject.TextAction(dev.agentkeyboard.core.AiTextAction.TRANSLATE_STYLED),
            journal.subjects.values.single())
        assertEquals(latinIME.getString(R.string.agent_command_translate_ready), panelText(panel, R.id.agent_status))
        assertEquals(typed, text) // nothing is replaced automatically
        val insert = panel.findViewById<android.widget.Button>(R.id.agent_apply)
        assertTrue(insert.isEnabled)
        assertTrue(panel.findViewById<android.widget.Button>(R.id.agent_copy).isEnabled)

        insert.performClick()
        assertEquals("x ¿cuándo nos vemos mañana, cabrón?", text) // exactly the command is replaced, in one commit
        assertEquals(android.view.View.GONE, panel.visibility)
        assertTrue(inputLogic.isAgentHistorySuppressed) // generated text is not learned from
        checkConnectionConsistency()
        panel.onApply?.invoke() // a second tap has nothing left to insert
        assertEquals("x ¿cuándo nos vemos mañana, cabrón?", text)
        controller.destroy()
    }

    @Test fun typedTranslationPanelWithoutAKeyShowsTheWayToTheSettingsAndSendsNothing() {
        val provider = QuestionProvider()
        val journal = helium314.keyboard.agent.TestRunJournal()
        val (controller, panel) = questionSurface(provider, journal)
        controller.credentialCheck = { false }
        openQuestionPanel(controller, "x @translate \"hallo\"", "Translation style:\nnatural English\n\nText to translate:\nhallo",
            dev.agentkeyboard.core.AiTextAction.TRANSLATE_STYLED)
        assertEquals(latinIME.getString(R.string.agent_command_translate_credentials), panelText(panel, R.id.agent_status))
        assertEquals(android.view.View.GONE, panel.findViewById<android.view.View>(R.id.agent_cloud_send).visibility)
        assertTrue(panel.findViewById<android.view.View>(R.id.agent_cloud_settings).isEnabled) // the way to the AI settings
        panel.onSendCloud?.invoke() // even a direct send has no approval to use
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(provider.requests.isEmpty())
        assertTrue(journal.states.isEmpty())
        assertEquals("x @translate \"hallo\"", text)
        controller.destroy()
    }

    @Test fun typedTranslationWithoutQuotesOffersOnlyAHintAndOpensNothing() {
        val provider = QuestionProvider()
        val journal = helium314.keyboard.agent.TestRunJournal()
        val (controller, panel) = questionSurface(provider, journal)
        tapCommandChip(controller, "x @translate hallo spanisch", latinIME.getString(R.string.agent_command_translate_hint))
        assertNotEquals(android.view.View.VISIBLE, panel.visibility)
        panel.onSendCloud?.invoke()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(provider.requests.isEmpty())
        assertEquals("x @translate hallo spanisch", text)
        controller.destroy()
    }

    @Test fun typedTranslationPanelCancelledShowsNothingAndChangesNothing() {
        val provider = QuestionProvider()
        val journal = helium314.keyboard.agent.TestRunJournal()
        val (controller, panel) = questionSurface(provider, journal)
        val request = "Translation style:\nspanisch\n\nText to translate:\nhallo"
        openQuestionPanel(controller, "x @translate \"hallo\" spanisch", request, dev.agentkeyboard.core.AiTextAction.TRANSLATE_STYLED)
        panel.findViewById<android.widget.Button>(R.id.agent_cloud_cancel).performClick() // before sending
        assertTrue(provider.requests.isEmpty())
        assertEquals(request, panelText(panel, R.id.agent_source))
        assertEquals(latinIME.getString(R.string.agent_cloud_cancelled), panelText(panel, R.id.agent_status))
        panel.onSendCloud?.invoke() // the cancelled approval is gone
        assertTrue(provider.requests.isEmpty())

        openQuestionPanel(controller, "", request, dev.agentkeyboard.core.AiTextAction.TRANSLATE_STYLED) // again: a fresh preview
        panel.findViewById<android.widget.Button>(R.id.agent_cloud_send).performClick()
        assertEquals(request, provider.requests.single().input)
        panel.findViewById<android.widget.Button>(R.id.agent_cloud_cancel).performClick() // while waiting
        provider.response.complete("hola")
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(helium314.keyboard.agent.ActionRunState.CANCELLED, journal.modelStates.values.single())
        assertEquals(latinIME.getString(R.string.agent_panel_empty), panelText(panel, R.id.agent_result)) // no translation shown
        assertFalse(panel.findViewById<android.widget.Button>(R.id.agent_apply).isEnabled)
        panel.onApply?.invoke()
        assertEquals("x @translate \"hallo\" spanisch", text)
        controller.destroy()
    }

    @Test fun typedTranslationIsInsertedAtTheCursorWhenTheCommandIsGone() {
        val provider = QuestionProvider()
        val journal = helium314.keyboard.agent.TestRunJournal()
        val (controller, panel) = questionSurface(provider, journal)
        setText("x ")
        setCursorPosition(2)
        controller.startEditor(EditorInfo().apply { inputType = currentInputType; initialSelStart = 2; initialSelEnd = 2 })
        controller.selectionChanged(2, 2, -1, -1)
        val request = "Translation style:\nspanisch\n\nText to translate:\nhallo"
        // the command is no longer there
        assertTrue(controller.openQuestion(request, "@translate \"hallo\" spanisch", 29, dev.agentkeyboard.core.AiTextAction.TRANSLATE_STYLED))
        panel.findViewById<android.widget.Button>(R.id.agent_cloud_send).performClick()
        assertEquals(request, provider.requests.single().input)
        provider.response.complete("hola")
        awaitAgentUi { panelText(panel, R.id.agent_result) == "hola" }
        assertEquals("x ", text)
        panel.findViewById<android.widget.Button>(R.id.agent_apply).performClick()
        assertEquals("x hola", text)
        assertEquals(android.view.View.GONE, panel.visibility)
        controller.destroy()
    }

    @Test fun typedTranslationIsTheOnlyOtherActionATypedRequestCanOpen() {
        val provider = QuestionProvider()
        val journal = helium314.keyboard.agent.TestRunJournal()
        val (controller, panel) = questionSurface(provider, journal)
        for (action in dev.agentkeyboard.core.AiTextAction.entries.filter {
                it != dev.agentkeyboard.core.AiTextAction.ASK && it != dev.agentkeyboard.core.AiTextAction.TRANSLATE_STYLED })
            assertFalse(controller.openQuestion("hallo", "@ki hallo", 9, action), action.name)
        assertNotEquals(android.view.View.VISIBLE, panel.visibility)
        panel.onSendCloud?.invoke()
        assertTrue(provider.requests.isEmpty())
        controller.destroy()
    }

    // Variante A: `@ki` and `@translate` in two taps from the strip, without the panel

    /** Types [typed] and reports the cursor like the editor would. */
    private fun typeCommand(controller: helium314.keyboard.agent.AgentEditorController, typed: String) {
        chainInput(typed)
        controller.selectionChanged(text.length, text.length, composingStart, composingEnd)
    }

    /** The chips the strip shows now for the text before the cursor (a normal strip update). */
    private fun commandStrip(): android.view.ViewGroup {
        val chips = helium314.keyboard.agent.KeyboardCommands.createChipsView(latinIME, android.widget.FrameLayout(latinIME))
        assertNotNull(chips)
        return chips as android.view.ViewGroup
    }

    private fun chipViews(strip: android.view.ViewGroup): List<android.view.View> =
        (strip.getChildAt(0) as android.view.ViewGroup).let { row -> (0 until row.childCount).map { row.getChildAt(it) } }

    private fun chipLabel(chip: android.view.View): String = when (chip) {
        is android.widget.TextView -> chip.text.toString()
        is android.view.ViewGroup -> (0 until chip.childCount).map { chip.getChildAt(it) }
            .filterIsInstance<android.widget.TextView>().single().text.toString()
        else -> ""
    }

    private fun chipLabels(strip: android.view.ViewGroup) = chipViews(strip).map(::chipLabel)

    private fun tapStripChip(strip: android.view.ViewGroup, label: String) {
        assertTrue(chipViews(strip).single { chipLabel(it) == label }.performClick())
    }

    private val askingLabel get() = latinIME.getString(R.string.agent_command_ai_asking)
    private val cancelLabel get() = latinIME.getString(R.string.agent_command_ai_cancel)

    @Test fun typedQuestionIsSentWithOneTapAndOnlyTheAnswerChipReplacesTheCommand() {
        val provider = QuestionProvider()
        val journal = helium314.keyboard.agent.TestRunJournal()
        val (controller, panel) = questionSurface(provider, journal)
        typeCommand(controller, "x @ki wie hoch ist der Mount Everest")
        val strip = commandStrip()
        val send = latinIME.getString(R.string.agent_command_ki_chip, "OpenAI", "wie hoch ist der Mount Everest")
        assertEquals(listOf(send), chipLabels(strip)) // names the provider it sends to
        assertTrue(provider.requests.isEmpty()) // nothing is sent before the tap
        assertTrue(journal.states.isEmpty())

        tapStripChip(strip, send)
        assertEquals(1, provider.requests.size)
        val request = provider.requests.single()
        assertEquals("wie hoch ist der Mount Everest", request.input) // exactly the typed question, nothing else of the field
        assertEquals(dev.agentkeyboard.core.AiTextAction.ASK.instruction(), request.instruction)
        assertEquals(helium314.keyboard.agent.ModelSettings.DEFAULT_MODEL, request.model)
        assertNotEquals(android.view.View.VISIBLE, panel.visibility) // no panel in between
        assertEquals("x @ki wie hoch ist der Mount Everest", text)

        // waiting: moving dots before the text, and a way to cancel
        assertEquals(listOf(askingLabel, cancelLabel), chipLabels(strip))
        val loading = chipViews(strip).first() as android.view.ViewGroup
        assertTrue(loading.getChildAt(0) is helium314.keyboard.agent.LoadingDotsView)
        assertFalse(loading.isClickable)
        // a normal strip update while waiting shows the same chips and sends nothing new
        val rebuilt = commandStrip()
        assertEquals(listOf(askingLabel, cancelLabel), chipLabels(rebuilt))
        assertEquals(1, provider.requests.size)

        provider.response.complete("8849 m")
        awaitAgentUi { chipLabels(rebuilt).firstOrNull() == "8849 m" }
        assertEquals(listOf("8849 m", "↻", "✕"), chipLabels(rebuilt))
        assertEquals(helium314.keyboard.agent.ActionRunState.SUCCEEDED, journal.modelStates.values.single())
        assertEquals(helium314.keyboard.agent.RunSubject.TextAction(dev.agentkeyboard.core.AiTextAction.ASK), journal.subjects.values.single())
        assertEquals("x @ki wie hoch ist der Mount Everest", text) // nothing is replaced automatically
        assertEquals(listOf("8849 m", "↻", "✕"), chipLabels(commandStrip())) // and the answer survives strip updates

        tapStripChip(rebuilt, "8849 m")
        assertEquals("x 8849 m", text) // exactly the command is replaced, in one commit
        assertTrue(inputLogic.isAgentHistorySuppressed) // generated text is not learned from
        checkConnectionConsistency()
        assertFalse(controller.insertCommandAnswer("@ki wie hoch ist der Mount Everest", 36)) // nothing left to insert
        assertEquals("x 8849 m", text)
        assertEquals(1, provider.requests.size)
        controller.destroy()
    }

    @Test fun typedTranslationIsSentWithOneTapAndOnlyTheAnswerChipReplacesTheCommand() {
        val provider = QuestionProvider()
        val journal = helium314.keyboard.agent.TestRunJournal()
        val (controller, _) = questionSurface(provider, journal)
        val typed = "x @translate \"wann treffen wir uns morgen du geile sau\" natürliches slang spanisch"
        typeCommand(controller, typed)
        val strip = commandStrip()
        val send = latinIME.getString(R.string.agent_command_translate_chip, "OpenAI", "natürliches slang spanisch")
        assertEquals(listOf(send), chipLabels(strip))
        assertTrue(provider.requests.isEmpty())
        tapStripChip(strip, send)
        assertEquals(translationRequest, provider.requests.single().input) // the same request as the panel sends
        assertEquals(dev.agentkeyboard.core.AiTextAction.TRANSLATE_STYLED.instruction(), provider.requests.single().instruction)
        assertEquals(listOf(latinIME.getString(R.string.agent_command_ai_translating), cancelLabel), chipLabels(strip))
        provider.response.complete("¿cuándo nos vemos mañana, cabrón?")
        awaitAgentUi { chipLabels(strip).firstOrNull() == "¿cuándo nos vemos mañana, cabrón?" }
        assertEquals(helium314.keyboard.agent.RunSubject.TextAction(dev.agentkeyboard.core.AiTextAction.TRANSLATE_STYLED),
            journal.subjects.values.single())
        assertEquals(typed, text)
        tapStripChip(strip, "¿cuándo nos vemos mañana, cabrón?")
        assertEquals("x ¿cuándo nos vemos mañana, cabrón?", text)
        assertTrue(inputLogic.isAgentHistorySuppressed)
        checkConnectionConsistency()
        controller.destroy()
    }

    @Test fun typedQuestionCancelChipStopsTheRunAndNoAnswerShows() {
        val provider = QuestionProvider()
        val journal = helium314.keyboard.agent.TestRunJournal()
        val (controller, _) = questionSurface(provider, journal)
        typeCommand(controller, "x @ki hallo")
        val strip = commandStrip()
        val send = latinIME.getString(R.string.agent_command_ki_chip, "OpenAI", "hallo")
        tapStripChip(strip, send)
        tapStripChip(strip, cancelLabel)
        assertEquals(listOf(send), chipLabels(strip)) // the send chip again
        awaitAgentUi { journal.modelStates.values.singleOrNull() == helium314.keyboard.agent.ActionRunState.CANCELLED }
        provider.response.complete("zu spät")
        awaitAgentUi { true }
        assertEquals(listOf(send), chipLabels(strip))
        assertEquals(listOf(send), chipLabels(commandStrip()))
        assertEquals("x @ki hallo", text)
        assertEquals(1, provider.requests.size)
        controller.destroy()
    }

    @Test fun typedQuestionIsCancelledByTypingAndNoStaleAnswerShows() {
        val provider = QuestionProvider()
        val journal = helium314.keyboard.agent.TestRunJournal()
        val (controller, _) = questionSurface(provider, journal)
        typeCommand(controller, "x @ki hallo")
        tapStripChip(commandStrip(), latinIME.getString(R.string.agent_command_ki_chip, "OpenAI", "hallo"))
        typeCommand(controller, " du") // the editor reports the new cursor
        awaitAgentUi { journal.modelStates.values.singleOrNull() == helium314.keyboard.agent.ActionRunState.CANCELLED }
        provider.response.complete("Antwort")
        awaitAgentUi { true }
        assertEquals(listOf(latinIME.getString(R.string.agent_command_ki_chip, "OpenAI", "hallo du")), chipLabels(commandStrip()))
        // deleting back to the sent text does not bring the old answer back
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE)
        controller.selectionChanged(text.length, text.length, composingStart, composingEnd)
        assertEquals("x @ki hallo", text)
        assertEquals(listOf(latinIME.getString(R.string.agent_command_ki_chip, "OpenAI", "hallo")), chipLabels(commandStrip()))
        assertEquals(1, provider.requests.size)
        assertEquals("x @ki hallo", text)
        controller.destroy()
    }

    @Test fun typedQuestionIsCancelledWhenTheStripIsBuiltForChangedText() {
        val provider = QuestionProvider()
        val journal = helium314.keyboard.agent.TestRunJournal()
        val (controller, _) = questionSurface(provider, journal)
        typeCommand(controller, "x @ki hallo")
        tapStripChip(commandStrip(), latinIME.getString(R.string.agent_command_ki_chip, "OpenAI", "hallo"))
        chainInput(" du") // no cursor report yet: the strip for the new text ends the request
        val strip = commandStrip()
        assertEquals(listOf(latinIME.getString(R.string.agent_command_ki_chip, "OpenAI", "hallo du")), chipLabels(strip))
        awaitAgentUi { journal.modelStates.values.singleOrNull() == helium314.keyboard.agent.ActionRunState.CANCELLED }
        provider.response.complete("Antwort")
        awaitAgentUi { true }
        assertFalse("Antwort" in chipLabels(strip))
        assertEquals("x @ki hallo du", text)
        controller.destroy()
    }

    @Test fun typedQuestionIsCancelledWhenTheFieldTurnsProtected() {
        val provider = QuestionProvider()
        val journal = helium314.keyboard.agent.TestRunJournal()
        val (controller, _) = questionSurface(provider, journal)
        typeCommand(controller, "x @ki hallo")
        tapStripChip(commandStrip(), latinIME.getString(R.string.agent_command_ki_chip, "OpenAI", "hallo"))
        latinIME.prefs().edit { putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, true) }
        val strip = helium314.keyboard.agent.KeyboardCommands.createChipsView(latinIME, android.widget.FrameLayout(latinIME))
        awaitAgentUi { journal.modelStates.values.singleOrNull() == helium314.keyboard.agent.ActionRunState.CANCELLED }
        provider.response.complete("Antwort")
        awaitAgentUi { true }
        assertTrue(strip == null || "Antwort" !in chipLabels(strip as android.view.ViewGroup))
        assertEquals("x @ki hallo", text)
        controller.destroy()
    }

    @Test fun typedQuestionRetryNeedsANewTapAndDiscardDropsTheAnswer() {
        val provider = QuestionProvider()
        val journal = helium314.keyboard.agent.TestRunJournal()
        val (controller, _) = questionSurface(provider, journal)
        typeCommand(controller, "x @ki hallo")
        val strip = commandStrip()
        val send = latinIME.getString(R.string.agent_command_ki_chip, "OpenAI", "hallo")
        tapStripChip(strip, send)
        provider.response.complete("Antwort")
        awaitAgentUi { chipLabels(strip).firstOrNull() == "Antwort" }
        awaitAgentUi { true }
        assertEquals(1, provider.requests.size) // an answer never sends again by itself
        tapStripChip(strip, "↻")
        assertEquals(2, provider.requests.size)
        assertEquals(provider.requests[0].input, provider.requests[1].input)
        awaitAgentUi { chipLabels(strip).firstOrNull() == "Antwort" }
        assertEquals(2, journal.modelStates.size) // two taps, two journaled runs
        tapStripChip(strip, "✕")
        assertEquals(listOf(send), chipLabels(strip))
        assertEquals("x @ki hallo", text)
        assertEquals(2, provider.requests.size)
        controller.destroy()
    }

    @Test fun typedQuestionWithoutAKeySendsNothingAndItsChipOpensTheAiSettings() {
        val provider = QuestionProvider()
        val journal = helium314.keyboard.agent.TestRunJournal()
        val (controller, panel) = questionSurface(provider, journal)
        controller.credentialCheck = { false }
        typeCommand(controller, "x @ki hallo")
        val strip = commandStrip()
        tapStripChip(strip, latinIME.getString(R.string.agent_command_ki_chip, "OpenAI", "hallo"))
        val noKey = latinIME.getString(R.string.agent_command_ai_no_key)
        assertEquals(listOf(noKey), chipLabels(strip))
        awaitAgentUi { true }
        assertTrue(provider.requests.isEmpty())
        assertTrue(journal.states.isEmpty())
        tapStripChip(strip, noKey)
        val started = org.robolectric.Shadows.shadowOf(org.robolectric.RuntimeEnvironment.getApplication()).nextStartedActivity
        assertNotNull(started)
        assertTrue(started.getBooleanExtra(helium314.keyboard.agent.AgentPreferences.OPEN_AI_SETTINGS, false))
        assertNotEquals(android.view.View.VISIBLE, panel.visibility)
        assertTrue(provider.requests.isEmpty())
        assertEquals("x @ki hallo", text)
        controller.destroy()
    }

    @Test fun typedQuestionFailureIsOneChipThatOpensThePanelWithTheReason() {
        val provider = QuestionProvider()
        val journal = helium314.keyboard.agent.TestRunJournal()
        val (controller, panel) = questionSurface(provider, journal)
        typeCommand(controller, "x @ki hallo")
        val strip = commandStrip()
        tapStripChip(strip, latinIME.getString(R.string.agent_command_ki_chip, "OpenAI", "hallo"))
        provider.response.completeExceptionally(helium314.keyboard.agent.providers.CloudFailure(
            helium314.keyboard.agent.providers.CloudError.REFUSED))
        val refused = latinIME.getString(R.string.agent_command_ai_refused)
        awaitAgentUi { chipLabels(strip) == listOf(refused) }
        assertEquals(1, provider.requests.size) // no retry by itself
        tapStripChip(strip, refused)
        // the panel is the details view: the same request, ready for a new explicit Send, and why it failed
        assertEquals(android.view.View.VISIBLE, panel.visibility)
        assertEquals("hallo", panelText(panel, R.id.agent_source))
        assertEquals(android.view.View.VISIBLE, panel.findViewById<android.view.View>(R.id.agent_cloud_send).visibility)
        assertEquals(latinIME.getString(R.string.agent_cloud_refused), panelText(panel, R.id.agent_status))
        assertEquals(1, provider.requests.size)
        assertEquals("x @ki hallo", text)
        controller.destroy()
    }

    @Test fun typedQuestionAnswerIsInsertedAtTheCursorOnlyWhenTheCommandIsGone() {
        val provider = QuestionProvider()
        val journal = helium314.keyboard.agent.TestRunJournal()
        val (controller, _) = questionSurface(provider, journal)
        setText("x ")
        setCursorPosition(2)
        controller.startEditor(EditorInfo().apply { inputType = currentInputType; initialSelStart = 2; initialSelEnd = 2 })
        controller.selectionChanged(2, 2, -1, -1)
        var changes = 0
        // the command is no longer before the cursor
        val sent = controller.sendCommandRequest("frage", "@ki frage", 2, dev.agentkeyboard.core.AiTextAction.ASK) { changes++ }
        assertNotNull(sent)
        assertEquals("frage", provider.requests.single().input)
        provider.response.complete("Antwort")
        awaitAgentUi { changes == 1 }
        assertEquals("x ", text)
        assertTrue(controller.insertCommandAnswer("@ki frage", 2))
        assertEquals("x Antwort", text)
        controller.destroy()
    }

    @Test fun typedRequestFromTheStripTakesOnlyQuestionsAndTranslations() {
        val provider = QuestionProvider()
        val journal = helium314.keyboard.agent.TestRunJournal()
        val (controller, _) = questionSurface(provider, journal)
        for (action in dev.agentkeyboard.core.AiTextAction.entries.filter {
                it != dev.agentkeyboard.core.AiTextAction.ASK && it != dev.agentkeyboard.core.AiTextAction.TRANSLATE_STYLED })
            kotlin.test.assertNull(controller.sendCommandRequest("hallo", "@ki hallo", 9, action) {}, action.name)
        awaitAgentUi { true }
        assertTrue(provider.requests.isEmpty())
        controller.destroy()
    }

    // stage 23g: a fresh screenshot is offered between words, before next-word suggestions

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun screenshotChipShowsBetweenWordsAndSuggestionsWhileAWordIsTyped() {
        val strip = screenshotStrip()
        assertTrue(screenshotShown(strip)) // empty field: the chip right away
        typeAndUpdate("Hal")
        assertTrue(composer.isComposingWord)
        assertFalse(screenshotShown(strip)) // a word is typed: its suggestions
        typeAndUpdate(' ')
        assertTrue(screenshotShown(strip)) // after the space
        typeAndUpdate("du")
        assertFalse(screenshotShown(strip)) // the first letter brings the suggestions back
        typeAndUpdate('.')
        assertTrue(screenshotShown(strip)) // after punctuation
        typeAndUpdate("Ja")
        assertFalse(screenshotShown(strip))
        typeAndUpdate('\n')
        assertTrue(screenshotShown(strip)) // after a new line
        typeAndUpdate('x')
        assertFalse(screenshotShown(strip))
        typeAndUpdate(' ')
        assertTrue(screenshotShown(strip)) // and again, until it is used, closed or too old
        assertEquals("Hal du.Ja\nx ", text) // the chip never inserts anything by itself
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun screenshotChipOnlyChangesTheStripNotTheSuggestionsForAutocorrection() {
        val strip = screenshotStrip()
        val words = SuggestedWords(arrayListOf(SuggestedWordInfo("hallo", "", 100, SuggestedWordInfo.KIND_CORRECTION, null, 0, 0)),
            null, null, false, false, false, SuggestedWords.INPUT_STYLE_PREDICTION, SuggestedWords.NOT_A_SEQUENCE_NUMBER)
        latinIME.setSuggestions(words)
        handleMessages()
        assertTrue(screenshotShown(strip))
        assertTrue(inputLogic.mSuggestedWords === words) // InputLogic still gets them
        // the chip goes on closing, and the normal strip comes back at once
        closeScreenshotChip(strip)
        assertFalse(screenshotShown(strip))
        latinIME.setSuggestions(words)
        handleMessages()
        assertFalse(screenshotShown(strip))
        assertTrue(stripWords(strip).contains("hallo"))
        typeAndUpdate("ok ")
        assertFalse(screenshotShown(strip)) // a closed screenshot does not come back
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun screenshotChipGivesWayToTypedCommandChips() {
        helium314.keyboard.agent.KeyboardCommands.load(latinIME)
        val strip = screenshotStrip()
        assertTrue(screenshotShown(strip))
        typeAndUpdate("x @math 12*3 ")
        assertFalse(composer.isComposingWord)
        assertFalse(screenshotShown(strip))
        assertNotNull(strip.findViewById<android.view.View>(R.id.agent_command_chips))
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun screenshotChipForANewScreenshotAppearsAtOnceBetweenWordsAndAtTheNextBoundaryWhileTyping() {
        var shot: helium314.keyboard.agent.ScreenshotChip.Shot? = null
        val strip = screenshotStrip { shot }
        assertFalse(screenshotShown(strip))
        val looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        var changes = 0
        val manager = helium314.keyboard.agent.ScreenshotChip.onChange
        helium314.keyboard.agent.ScreenshotChip.onChange = { changes++; manager?.invoke() }
        val debounce = helium314.keyboard.agent.ScreenshotChip.DEBOUNCE_MILLIS

        // the media store reports one screenshot several times: one refresh, without a new tap into the field
        shot = screenshot(id = 11)
        repeat(3) { helium314.keyboard.agent.ScreenshotChip.mediaChanged() }
        looper.idleFor(java.time.Duration.ofMillis(debounce / 2))
        assertEquals(0, changes)
        looper.idleFor(java.time.Duration.ofMillis(debounce))
        handleMessages()
        assertEquals(1, changes)
        assertTrue(screenshotShown(strip))

        // while a word is typed the next screenshot waits for the word boundary
        typeAndUpdate("Hal")
        shot = screenshot(id = 12)
        helium314.keyboard.agent.ScreenshotChip.mediaChanged()
        looper.idleFor(java.time.Duration.ofMillis(2 * debounce))
        handleMessages()
        assertEquals(2, changes)
        assertFalse(screenshotShown(strip))
        typeAndUpdate(' ')
        assertTrue(screenshotShown(strip))

        // when it gets too old, the normal strip comes back by itself
        // (the age is checked against the wall clock, which the paused looper does not move)
        shot = screenshot(id = 13, age = helium314.keyboard.agent.ScreenshotChip.RECENT_MILLIS - 1_500)
        helium314.keyboard.agent.ScreenshotChip.mediaChanged()
        looper.idleFor(java.time.Duration.ofMillis(2 * debounce))
        handleMessages()
        assertTrue(screenshotShown(strip))
        Thread.sleep(1_700)
        looper.idleFor(java.time.Duration.ofMillis(2_000))
        handleMessages()
        assertFalse(screenshotShown(strip))
        typeAndUpdate("ok ")
        assertFalse(screenshotShown(strip))
    }

    private lateinit var screenshotImage: java.io.File

    /**
     * Types and then delivers what the delayed strip update would (skipped by [handleMessages], and
     * the suggestion thread does not run here): the typed word while composing, else the neutral strip.
     */
    private fun typeAndUpdate(typed: String) {
        chainInput(typed)
        if (composer.isComposingWord) latinIME.setSuggestions(SuggestedWords(arrayListOf(SuggestedWordInfo(composer.typedWord, "",
            SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_TYPED, null, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)),
            null, null, true, false, false, SuggestedWords.INPUT_STYLE_TYPING, SuggestedWords.NOT_A_SEQUENCE_NUMBER))
        else latinIME.setNeutralSuggestionStrip()
        handleMessages()
    }

    private fun typeAndUpdate(typed: Char) = typeAndUpdate(typed.toString())

    private fun screenshot(id: Long = 5, age: Long = 1_000) = helium314.keyboard.agent.ScreenshotChip.Shot(
        id, android.net.Uri.fromFile(screenshotImage), "image/png", System.currentTimeMillis() - age)

    /** A strip like the keyboard's, an image field, the screenshot switch with permission and [shot] as the newest one. */
    private fun screenshotStrip(shot: (() -> helium314.keyboard.agent.ScreenshotChip.Shot?)? = null): helium314.keyboard.latin.suggestions.SuggestionStripView {
        screenshotImage = java.io.File(latinIME.cacheDir, "Screenshot_test.png")
        val bitmap = android.graphics.Bitmap.createBitmap(60, 120, android.graphics.Bitmap.Config.ARGB_8888)
            .apply { eraseColor(android.graphics.Color.BLUE) }
        screenshotImage.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        ShadowInputMethodService.acceptedMimeTypes = arrayOf("image/*")
        currentInputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        ShadowInputMethodService.currentImeOptions = EditorInfo.IME_ACTION_NONE // enter types a new line
        latinIME.prefs().edit { putBoolean(AgentPreferences.SCREENSHOT_CHIP, true) }
        org.robolectric.Shadows.shadowOf(latinIME.application).grantPermissions(helium314.keyboard.agent.ScreenshotChip.permission())
        val fixed = screenshot()
        helium314.keyboard.agent.ScreenshotChip.finder = { _, _ -> if (shot == null) fixed else shot() }
        helium314.keyboard.keyboard.internal.KeyboardIconsSet.instance.loadIcons(latinIME)
        val themed = android.view.ContextThemeWrapper(latinIME, R.style.KeyboardTheme_Flat)
        val root = android.widget.FrameLayout(themed)
        val container = android.view.LayoutInflater.from(themed).inflate(R.layout.strip_container, root, false) as android.view.ViewGroup
        root.addView(container)
        root.addView(MainKeyboardView(themed, null).apply { id = R.id.keyboard_view })
        latinIME.updateSuggestionStripView(root)
        setText("")
        return container.findViewById(R.id.suggestion_strip_view)
    }

    private fun screenshotChipText(strip: android.view.View): android.widget.TextView? =
        strip.findViewById<android.widget.TextView>(R.id.clipboard_suggestion_text)
            ?.takeIf { it.text.toString() == latinIME.getString(R.string.agent_screenshot_chip) }

    /** The chip is in the strip and nothing between them is hidden. */
    private fun screenshotShown(strip: android.view.View): Boolean {
        var view: android.view.View = screenshotChipText(strip) ?: return false
        while (view !== strip) {
            if (view.visibility != android.view.View.VISIBLE) return false
            view = view.parent as? android.view.View ?: return false
        }
        return true
    }

    private fun stripWords(strip: android.view.View): List<String> {
        val words = mutableListOf<String>()
        fun collect(view: android.view.View) {
            if (view is android.widget.TextView) words += view.text.toString()
            if (view is android.view.ViewGroup) (0 until view.childCount).forEach { collect(view.getChildAt(it)) }
        }
        collect(strip.findViewById(R.id.suggestions_strip))
        return words
    }

    private fun closeScreenshotChip(strip: android.view.View) {
        val chip = screenshotChipText(strip)!!.parent as android.view.View
        assertTrue(chip.findViewById<android.view.View>(R.id.clipboard_suggestion_close).performClick())
        handleMessages()
    }

    // should be called before every test, so the same state is guaranteed
    @BeforeTest
    fun reset() {
        // reset input connection & facilitator
        currentScript = ScriptUtils.SCRIPT_LATIN
        ShadowInputMethodService.reset()
        helium314.keyboard.agent.ScreenshotChip.resetForTest()
        lastAddedWord = ""

        // reset settings
        latinIME.prefs().edit { clear() }

        setText("") // (re)sets selection and composing word
    }

    private fun chainInput(text: String) = text.forEach { input(it.code) }

    private fun input(char: Char) = input(char.code)

    private fun input(codePoint: Int) {
        require(codePoint > 0) { "not a codePoint: $codePoint" }
        val oldBefore = textBeforeCursor
        val oldAfter = textAfterCursor
        val insert = StringUtils.newSingleCodePointString(codePoint)
        val phantomSpaceToInsert = if (spaceState == SpaceState.PHANTOM) " " else ""
        val oldIsAtEnd = !composer.isCursorFrontOrMiddleOfComposingWord

        latinIME.onEvent(Event.createEventForCodePointFromUnknownSource(codePoint))
        handleMessages()

        if (!latinIME.prefs().getString(Settings.PREF_SELECTED_SUBTYPE, "")!!.contains("CombiningRules") // check fails if combiner merges symbols
            && !(codePoint == Constants.CODE_SPACE && oldBefore.lastOrNull() == ' ') // check fails when 2 spaces are converted into a period
            && !latinIME.mInputLogic.mSuggestedWords.mWillAutoCorrect // autocorrect obviously creates inconsistencies
            ) {
            if (phantomSpaceToInsert.isEmpty())
                assertEquals(oldBefore + insert, textBeforeCursor)
            else // in some cases autospace might be suppressed
                assert(oldBefore + phantomSpaceToInsert + insert == textBeforeCursor || oldBefore + insert == textBeforeCursor)
        }
        assertEquals(oldAfter, textAfterCursor)
        assertEquals(textBeforeCursor + textAfterCursor, getTextFromConnection())
        if (composer.isComposingWord) // if we're not composing any more cursor is always at the end
            assertEquals(oldIsAtEnd, !composer.isCursorFrontOrMiddleOfComposingWord)
        checkConnectionConsistency()
    }

    private fun functionalKeyPress(keyCode: Int) {
        require(keyCode < 0) { "not a functional key code: $keyCode" }
        latinIME.onEvent(Event.createSoftwareKeypressEvent(Event.NOT_A_CODE_POINT, keyCode, 0, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false))
        handleMessages()
        checkConnectionConsistency()
    }

    // almost the same as codePoint input, but calls different latinIME function
    private fun input(insert: String) {
        val oldBefore = textBeforeCursor
        val oldAfter = textAfterCursor
        val phantomSpaceToInsert = if (spaceState == SpaceState.PHANTOM) " " else ""

        latinIME.onTextInput(insert)
        handleMessages()

        if (phantomSpaceToInsert.isEmpty())
            assertEquals(oldBefore + insert, textBeforeCursor)
        else // in some cases autospace might be suppressed
            assert(oldBefore + phantomSpaceToInsert + insert == textBeforeCursor || oldBefore + insert == textBeforeCursor)
        assert(oldBefore + insert == textBeforeCursor || "$oldBefore $insert" == textBeforeCursor)
        assertEquals(oldAfter, textAfterCursor)
        assertEquals(textBeforeCursor + textAfterCursor, getTextFromConnection())
        checkConnectionConsistency()
    }

    private fun getWordAtCursor() = connection.getWordRangeAtCursor(settingsValues.mSpacingAndPunctuations, currentScript)?.mWord

    private fun setCursorPosition(start: Int, end: Int = start, weirdTextField: Boolean = false) {
        val ei = EditorInfo()
        ei.inputType = currentInputType
        ei.initialSelStart = start
        ei.initialSelEnd = end
        // imeOptions should not matter

        // adjust text in inputConnection first, otherwise fixLyingCursorPosition will move cursor
        // to the end of the text
        val fullText = textBeforeCursor + selectedText + textAfterCursor
        assertEquals(fullText, getTextFromConnection())

        // need to update ic before, otherwise when reloading text cache from ic, ric will load wrong text before cursor
        val oldStart = selectionStart
        val oldEnd = selectionEnd
        selectionStart = start
        selectionEnd = end
        assertEquals(fullText, textBeforeCursor + selectedText + textAfterCursor)

        latinIME.onUpdateSelection(oldStart, oldEnd, start, end, composingStart, composingEnd)
        handleMessages()

        if (weirdTextField) {
            latinIME.mHandler.onStartInput(ei, true) // essentially does nothing
            latinIME.mHandler.onStartInputView(ei, true) // does the thing
            handleMessages()
        }

        assertEquals(fullText, getTextFromConnection())
        assertEquals(start, selectionStart)
        assertEquals(end, selectionEnd)
        checkConnectionConsistency()
    }

    // assumes we have nothing selected
    private fun getCursorPosition(): Int {
        assertEquals(cursor, connection.expectedSelectionStart)
        assertEquals(cursor, connection.expectedSelectionEnd)
        return cursor
    }

    // just sets the text and starts input so connection it set up correctly
    private fun setText(newText: String) {
        ShadowInputMethodService.text = newText
        selectionStart = newText.length
        selectionEnd = selectionStart
        composingStart = -1
        composingStart = -1

        // we need to start input to notify that something changed
        // restarting is false, so this is seen as a new text field
        val ei = EditorInfo()
        ei.inputType = currentInputType
        latinIME.mHandler.onStartInput(ei, false)
        latinIME.mHandler.onStartInputView(ei, false)
        handleMessages() // this is important so the composing span is set correctly
        checkConnectionConsistency()
    }

    // like selecting a suggestion from strip
    private fun pickSuggestion(suggestion: String) {
        val info = SuggestedWordInfo(suggestion, "", 0, 0, null, 0, 0)
        latinIME.pickSuggestionManually(info)
        checkConnectionConsistency()
    }

    // only works when autocorrect is on, separator after word is required
    private fun getAutocorrectedWithSpaceAfter(suggestion: String, typedWord: String?) {
        val info = SuggestedWordInfo(suggestion, "", 0, 0, null, 0, 0)
        val typedInfo = SuggestedWordInfo(typedWord, "", 0, 0, null, 0, 0)
        val sw = SuggestedWords(ArrayList(listOf(typedInfo, info)), null, typedInfo, false, true, false, 0, 0)
        latinIME.mInputLogic.setSuggestedWords(sw) // this prepares for autocorrect
        input(' ')
        checkConnectionConsistency()
    }

    private fun glideTypingInput(word: String) {
        val info = SuggestedWordInfo(word, "", 0, 0, null, 0, 0)
        val sw = SuggestedWords(ArrayList(listOf(info)), null, info, true, false, false, 0, 0)
        latinIME.mInputLogic.onUpdateTailBatchInputCompleted(settingsValues, sw, KeyboardSwitcher.getInstance())
    }

    private fun checkConnectionConsistency() {
        // RichInputConnection only has composing text up to cursor, but InputConnection has full composing text
        val expectedConnectionComposingText = if (composingStart == -1 || composingEnd == -1) ""
        else text.substring(composingStart, min(composingEnd, selectionEnd))
        assert(composingText.startsWith(expectedConnectionComposingText))
        // RichInputConnection only returns text up to cursor
        val textBeforeComposingText = if (composingStart == -1) textBeforeCursor else text.substring(0, composingStart)

        println("consistency: $selectionStart, ${connection.expectedSelectionStart}, $selectionEnd, ${connection.expectedSelectionEnd}, $textBeforeComposingText, " +
                "$connectionTextBeforeComposingText, $composingText, $connectionComposingText, $textBeforeCursor, ${connection.getTextBeforeCursor(textBeforeCursor.length, 0)}" +
                ", $textAfterCursor, ${connection.getTextAfterCursor(textAfterCursor.length, 0)}")
        assertEquals(selectionStart, connection.expectedSelectionStart)
        assertEquals(selectionEnd, connection.expectedSelectionEnd)
        assertEquals(textBeforeComposingText, connectionTextBeforeComposingText)
        assertEquals(expectedConnectionComposingText, connectionComposingText)
        assertEquals(textBeforeCursor, connection.getTextBeforeCursor(textBeforeCursor.length, 0).toString())
        assertEquals(textAfterCursor, connection.getTextAfterCursor(textAfterCursor.length, 0).toString())
    }

    private fun getTextFromConnection() =
        connection.getTextBeforeCursor(100, 0).toString() + (connection.getSelectedText(0) ?: "") + connection.getTextAfterCursor(100, 0)

    private fun setInputType(inputType: Int) {
        // set text to actually apply input type
        currentInputType = inputType
        setText(text)
    }

    // always need to handle messages for proper simulation
    private fun handleMessages() {
        while (messages.isNotEmpty()) {
            latinIME.mHandler.handleMessage(messages.first())
            messages.removeAt(0)
        }
        while (delayedMessages.isNotEmpty()) {
            val msg = delayedMessages.first()
            if (msg.what != 2) // MSG_UPDATE_SUGGESTION_STRIP, we want to ignore it because it's irrelevant and has a 500 ms timeout
                latinIME.mHandler.handleMessage(delayedMessages.first())
            delayedMessages.removeAt(0)
            // delayed messages may post further messages, handle before next delayed message
            while (messages.isNotEmpty()) {
                latinIME.mHandler.handleMessage(messages.first())
                messages.removeAt(0)
            }
        }
        assertEquals(0, messages.size)
        assertEquals(0, delayedMessages.size)
    }

}

private var currentScript = ScriptUtils.SCRIPT_LATIN
private val messages = java.util.Collections.synchronizedList(mutableListOf<Message>()) // for latinIME / ShadowInputMethodService
private val delayedMessages = java.util.Collections.synchronizedList(mutableListOf<Message>()) // for latinIME / ShadowInputMethodService

// Shadows are handled by Robolectric. @Implementation overrides built-in functionality.
// This is used for avoiding crashes (LocaleManagerCompat, InputMethodManager, KeyboardSwitcher)
// and for simulating system stuff (InputMethodService for controlling the InputConnection, which
// more or less is the contents of the text field), and for setting the current script in
// KeyboardSwitcher without having to care about InputMethodSubtypes

@Implements(Handler::class)
class ShadowHandler {
    @Implementation
    fun sendMessage(message: Message): Boolean {
        messages.add(message)
        return true
    }
    @Implementation
    fun sendMessageDelayed(message: Message, delay: Long): Boolean {
        delayedMessages.add(message)
        return true
    }
}

@Implements(KeyboardSwitcher::class)
class ShadowKeyboardSwitcher {
    @Implementation
    // basically only needed for null check
    fun getMainKeyboardView(): MainKeyboardView = Mockito.mock(MainKeyboardView::class.java)
    @Implementation
    // only affects view
    fun setKeyboard(keyboardId: Int, toggleState: KeyboardSwitcher.KeyboardSwitchState) = Unit
    @Implementation
    // only affects view
    fun setOneHandedModeEnabled(enabled: Boolean, force: Boolean) = Unit
    @Implementation
    fun getCurrentKeyboardScript() = currentScript
}

@Implements(DictionaryFacilitatorImpl::class)
class ShadowFacilitator2 {
    @Implementation
    fun addToUserHistory(suggestion: String, wasAutoCapitalized: Boolean,
                         ngramContext: NgramContext, timeStampInSeconds: Long,
                         blockPotentiallyOffensive: Boolean) {
        lastAddedWord = suggestion
    }
    companion object {
        var lastAddedWord = ""
    }
}
