package helium314.keyboard.agent

import android.app.KeyguardManager
import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.agent.commands.EmojiMixer
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
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The gates of the command system and of the emoji mixer ask the live lock state of the device, not only the flag
 * that was set when the field started: a device locked while the field stays open blocks them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class LiveLockGateTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val keyguard get() = latinIME.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    private val textField = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; packageName = "app"; fieldId = 1 }
    private var now = 1_000L

    @Before fun setUp() {
        LockState.resetForTest()
        LockState.clock = { now }
        shadowOf(keyguard).setIsDeviceLocked(false)
        latinIME.prefs().edit().clear().commit()
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(textField, false, latinIME.packageName))
        KeyboardCommands.load(latinIME)
        EmojiMixer.resetForTest()
    }

    @After fun tearDown() {
        shadowOf(keyguard).setIsDeviceLocked(false)
        LockState.resetForTest()
        EmojiMixer.resetForTest()
        latinIME.prefs().edit().clear().commit()
    }

    private fun lockAfterFieldStart() {
        shadowOf(keyguard).setIsDeviceLocked(true)
        now += LockState.UNLOCKED_MILLIS + 1 // the short-lived "unlocked" answer has run out
    }

    @Test fun commandGateBlocksWhenTheDeviceLocksAfterTheFieldStarted() {
        assertFalse(KeyboardCommands.isBlocked(Settings.getValues(), textField))
        assertFalse(Settings.getValues().mIsLocked)
        lockAfterFieldStart()
        assertFalse(Settings.getValues().mIsLocked) // the field-start flag is stale
        assertTrue(KeyboardCommands.isBlocked(Settings.getValues(), textField))
    }

    @Test fun emojiMixerGateBlocksWhenTheDeviceLocksAfterTheFieldStarted() {
        assertFalse(EmojiMixer.blocked(textField))
        lockAfterFieldStart()
        assertTrue(EmojiMixer.blocked(textField))
    }
}
