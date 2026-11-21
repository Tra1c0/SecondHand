// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.net.Uri
import android.view.inputmethod.InputContentInfo
import androidx.core.view.inputmethod.InputContentInfoCompat
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Stage 88: the redo offer ends at the common places where text comes into the field (LatinIME and the listener), one test per way. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w369dp-h821dp-520dpi", shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class UndoOfferHooksTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)

    @Before @After fun clean() { UndoOffer.onChange = null; UndoOffer.reset() }

    private fun offered() { UndoOffer.onCode(KeyCode.UNDO); assertTrue(UndoOffer.active) }

    @Test fun textInputEndsTheOffer() { offered(); latinIME.onTextInput("🙂"); assertFalse(UndoOffer.active) }

    @Test fun textInputThroughTheKeyboardListenerEndsTheOffer() { offered(); latinIME.mKeyboardActionListener.onTextInput("ab"); assertFalse(UndoOffer.active) }

    @Test fun privateTextInputEndsTheOffer() { offered(); latinIME.onPrivateTextInput("code"); assertFalse(UndoOffer.active) }

    @Test fun everyChosenSuggestionEndsTheOffer() {
        // MoreSuggestionsView, the strip and the chips all end here
        offered()
        latinIME.pickSuggestionManually(SuggestedWordInfo("hallo", "", 100, SuggestedWordInfo.KIND_TYPED, null, 0, 0))
        assertFalse(UndoOffer.active)
    }

    @Test fun aGestureEndsTheOffer() { offered(); latinIME.mKeyboardActionListener.onStartBatchInput(); assertFalse(UndoOffer.active) }

    @Test fun aStickerOrGifEndsTheOffer() {
        offered()
        val content = InputContentInfoCompat(Uri.parse("content://x/y.gif"), android.content.ClipDescription("gif", arrayOf("image/gif")), null)
        runCatching { latinIME.mKeyboardActionListener.onContent(content) } // the field's connection is not there; the hook runs first
        assertFalse(UndoOffer.active)
    }
}
