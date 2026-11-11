// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.os.Build
import helium314.keyboard.agent.security.*
import helium314.keyboard.latin.utils.prefs
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Locale
import javax.crypto.KeyGenerator
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EncryptedCredentialStoreTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val aesKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private var locked = false
    private var cryptCalls = 0
    private val cipher = AesGcmSecretCipher { cryptCalls++; aesKey }
    private val store get() = EncryptedCredentialStore(app, cipher) { !locked }
    private val directory get() = File(credentialContext(app).noBackupFilesDir, "agent_credentials")
    private val slot = CredentialSlot.OPENAI
    private val example = "example-credential-for-offline-tests-only"

    @Before fun clear() {
        directory.listFiles()?.forEach { assertTrue(it.delete()) }
        app.prefs().edit().clear().commit()
    }

    @Test fun encryptedRoundTripUsesCredentialProtectedNoBackupStorageAndNoKeyboardPreferences() {
        store.save(slot, example.toCharArray())
        assertTrue(store.contains(slot))
        val file = directory.listFiles()!!.single()
        assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains(example))
        assertFalse(credentialContext(app).isDeviceProtectedStorage)
        assertTrue(app.isDeviceProtectedStorage)
        assertFalse(directory.toPath().startsWith(app.filesDir.toPath()))
        assertFalse(app.prefs().all.values.any { it.toString().contains(example) })
        store.read(slot)!!.use {
            assertEquals(example, it.value())
            assertEquals("Secret(redacted)", it.toString())
        }
        store.delete(slot)
        assertNull(store.read(slot))
    }

    @Test fun lockBlocksReadWriteAndMetadataBeforeCrypto() {
        locked = true
        val callsBefore = cryptCalls
        for (operation in listOf<() -> Any?>({ store.contains(slot) }, { store.read(slot) },
            { store.save(slot, example.toCharArray()) }, { store.delete(slot) })) {
            assertEquals(CredentialError.LOCKED, assertFailsWith<CredentialFailure> { operation() }.error)
        }
        assertEquals(callsBefore, cryptCalls)
        assertTrue(directory.listFiles().isNullOrEmpty())
    }

    @Test fun tamperingAndOversizedFilesFailClosedWithoutDeletingOriginal() {
        store.save(slot, example.toCharArray())
        val file = directory.listFiles()!!.single()
        val valid = file.readBytes()
        val modified = valid.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        file.writeBytes(modified)
        val failure = assertFailsWith<CredentialFailure> { store.read(slot) }
        assertEquals(CredentialError.UNAVAILABLE, failure.error)
        assertEquals("UNAVAILABLE", failure.message)
        assertNull(failure.cause)
        assertContentEquals(modified, file.readBytes())
        file.writeBytes(ByteArray(8193))
        assertFailsWith<CredentialFailure> { store.read(slot) }
        assertEquals(8193L, file.length())
    }

    @Test fun invalidKeysCannotOverwriteSavedCredential() {
        store.save(slot, example.toCharArray())
        for (invalid in listOf("", "with space", "line\nbreak", "ä", "x".repeat(4097))) {
            assertEquals(CredentialError.INVALID_KEY,
                assertFailsWith<CredentialFailure> { store.save(slot, invalid.toCharArray()) }.error)
            store.read(slot)!!.use { assertEquals(example, it.value()) }
        }
    }

    @Test fun authenticatedEncryptionUsesFreshIvAndBindsTheSlot() {
        val plain = example.toByteArray()
        val first = cipher.seal(plain, "first-slot".toByteArray())
        val second = cipher.seal(plain, "first-slot".toByteArray())
        assertFalse(first.contentEquals(second))
        assertContentEquals(plain, cipher.open(first, "first-slot".toByteArray()))
        assertFails { cipher.open(first, "other-slot".toByteArray()) }
        assertFails { cipher.open(first.copyOf().apply { this[0] = 99 }, "first-slot".toByteArray()) }
    }

    @Test fun unexpectedLockServiceFailureDoesNotReachCrypto() {
        val unavailable = EncryptedCredentialStore(app, cipher) { error("system service unavailable") }
        assertEquals(CredentialError.LOCKED,
            assertFailsWith<CredentialFailure> { unavailable.read(slot) }.error)
        assertEquals(0, cryptCalls)
    }

    @Test fun closingSecretErasesMutableCopyAndPreventsReuse() {
        val bytes = example.toByteArray()
        val secret = Secret(bytes)
        assertEquals(example, secret.value())
        secret.close()
        assertTrue(bytes.all { it == 0.toByte() })
        assertFails { secret.value() }
        secret.close()
    }

    @Test fun localeChangesDoNotChangeTheCredentialSlot() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            store.save(slot, example.toCharArray())
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            store.read(slot)!!.use { assertEquals(example, it.value()) }
        } finally { Locale.setDefault(original) }
    }

    @Test fun boundedReadRejectsDataBeyondLimitAndModelIdentifiersCannotBeUrls() {
        assertContentEquals(byteArrayOf(1, 2), ByteArrayInputStream(byteArrayOf(1, 2)).readBounded(2))
        assertFails { ByteArrayInputStream(byteArrayOf(1, 2, 3)).readBounded(2) }
        assertTrue(ModelSettings.validModel("gpt-6-luna"))
        assertFalse(ModelSettings.validModel("https://example.invalid/credential"))
        assertFalse(ModelSettings.validModel("model\r\nheader: injected"))
    }

    @Test fun credentialCapabilityExcludesAndroidBelowMarshmallow() {
        assertFalse(EncryptedCredentialStore.supportsSdk(21))
        assertFalse(EncryptedCredentialStore.supportsSdk(22))
        assertTrue(EncryptedCredentialStore.supportsSdk(23))
        assertEquals(0, cryptCalls)
    }

    @Test fun sharedSettingsAndCredentialGuardDeniesUnknownServicesAndLockStates() {
        val context = org.mockito.Mockito.mock(android.content.Context::class.java)
        val keyguard = org.mockito.Mockito.mock(android.app.KeyguardManager::class.java)
        val users = org.mockito.Mockito.mock(android.os.UserManager::class.java)
        assertFalse(credentialAccessAllowed(context))
        org.mockito.Mockito.`when`(context.getSystemService(android.content.Context.KEYGUARD_SERVICE)).thenReturn(keyguard)
        org.mockito.Mockito.`when`(context.getSystemService(android.content.Context.USER_SERVICE)).thenReturn(users)
        assertFalse(credentialAccessAllowed(context)) // User not unlocked yet.
        org.mockito.Mockito.`when`(users.isUserUnlocked).thenReturn(true)
        assertTrue(credentialAccessAllowed(context))
        org.mockito.Mockito.`when`(keyguard.isKeyguardLocked).thenReturn(true)
        assertFalse(credentialAccessAllowed(context))
        org.mockito.Mockito.`when`(keyguard.isKeyguardLocked).thenReturn(false)
        org.mockito.Mockito.`when`(keyguard.isDeviceLocked).thenReturn(true)
        assertFalse(credentialAccessAllowed(context))
        org.mockito.Mockito.`when`(context.getSystemService(android.content.Context.KEYGUARD_SERVICE)).thenThrow(IllegalStateException("unavailable"))
        assertFalse(credentialAccessAllowed(context))
    }
}
