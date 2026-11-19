// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.view.View
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowInputMethodService
import helium314.keyboard.accessibility.MainKeyboardAccessibilityDelegate
import helium314.keyboard.keyboard.KeyDetector
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.LatinIME
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Commit 681fa4e: the node of a key is placed where the keyboard view is on the screen NOW, not where it was when
 * the root node was built. The view is a spy of the real main keyboard view whose place on the screen can be changed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowInputMethodService::class])
class KeyNodePositionTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val switcher = KeyboardSwitcher.getInstance()

    @Before fun setUp() {
        ShadowInputMethodService.reset()
        latinIME.setInputView(switcher.onCreateInputView(latinIME, true))
        switcher.reloadMainKeyboard()
    }

    @Test fun aKeyNodeFollowsTheViewsCurrentPlaceOnTheScreen() {
        val real = switcher.mainKeyboardView
        val keyboard = assertNotNull(switcher.keyboard)
        var x = 10
        var y = 20
        val view = Mockito.spy(real)
        Mockito.doAnswer { it.getArgument<IntArray>(0).let { out -> out[0] = x; out[1] = y }; null }
            .`when`(view).getLocationOnScreen(Mockito.any(IntArray::class.java))
        val delegate = MainKeyboardAccessibilityDelegate(view, KeyDetector(0f, 0f))
        delegate.keyboard = keyboard
        val provider = delegate.getAccessibilityNodeProvider(view)

        assertNotNull(provider.createAccessibilityNodeInfo(View.NO_ID), "the root node")
        x = 300; y = 700 // the keyboard window got its final place after the root was built
        val index = keyboard.sortedKeys.indexOfFirst { !it.isSpacer && it.hitBox.width() > 0 }
        val key = keyboard.sortedKeys[index]
        val info = assertNotNull(provider.createAccessibilityNodeInfo(index))
        val bounds = android.graphics.Rect().also { info.getBoundsInScreen(it) }
        val expected = android.graphics.Rect(key.hitBox).apply { offset(300, 700) }
        assertEquals(expected, bounds)
    }
}
