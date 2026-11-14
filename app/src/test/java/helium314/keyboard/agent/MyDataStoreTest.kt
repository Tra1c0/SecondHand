// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import dev.agentkeyboard.core.suggest.SuggestKind
import helium314.keyboard.agent.security.AesGcmSecretCipher
import helium314.keyboard.agent.security.CredentialError
import helium314.keyboard.agent.security.CredentialFailure
import helium314.keyboard.agent.security.credentialContext
import helium314.keyboard.latin.utils.prefs
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.KeyGenerator
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stage 45 "Meine Daten": encrypted like the AI keys, nothing before unlocking, cache only while unlocked. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MyDataStoreTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val aesKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private var locked = false
    private var cryptCalls = 0
    private val cipher = AesGcmSecretCipher { cryptCalls++; aesKey }
    private val store get() = MyDataStore(app, cipher) { !locked }
    private val directory get() = File(credentialContext(app).noBackupFilesDir, MyDataStore.DIRECTORY)
    private val data = MyData(name = "Erika Musterfrau", emails = listOf("erika@example.org", " erika@example.org "),
        phones = listOf("+49 30 1234567"), addresses = listOf("Hauptstraße 5\n10115 Berlin"), ibans = listOf("DE89 3704 0044 0532 0130 00"))
    private val defaultStore = MyDataCache.store

    @Before fun clear() {
        directory.listFiles()?.forEach { assertTrue(it.delete()) }
        app.prefs().edit().clear().commit()
        MyDataCache.drop()
        MyDataCache.store = { store }
    }

    @After fun tearDown() {
        MyDataCache.store = defaultStore
        MyDataCache.drop()
    }

    @Test fun startsEmptyAndRoundTripsEncryptedInCredentialProtectedNoBackupStorage() {
        assertTrue(store.read().isEmpty)
        assertTrue(directory.listFiles().isNullOrEmpty())
        store.save(data)
        val file = directory.listFiles()!!.single()
        assertEquals(MyDataStore.FILE, file.name)
        val raw = file.readBytes().toString(Charsets.ISO_8859_1)
        for (value in listOf("Erika", "example.org", "1234567", "Hauptstr", "DE89")) assertFalse(raw.contains(value), value)
        assertFalse(credentialContext(app).isDeviceProtectedStorage)
        assertFalse(directory.toPath().startsWith(app.filesDir.toPath()))
        assertFalse(app.prefs().all.values.any { it.toString().contains("Erika") })
        val read = store.read()
        assertEquals("Erika Musterfrau", read.name)
        // cleaned: one line, no repeats
        assertEquals(listOf("erika@example.org"), read.emails)
        assertEquals(listOf("Hauptstraße 5 10115 Berlin"), read.addresses)
        assertEquals(listOf("DE89 3704 0044 0532 0130 00"), read.entries(SuggestKind.IBAN))
        assertEquals(emptyList(), read.entries(SuggestKind.OTP))
        assertEquals(emptyList(), read.entries(SuggestKind.LINK))
        assertEquals("MyData(set)", read.toString())
    }

    @Test fun lockedMeansNoFileNoKeyAndNoCache() {
        store.save(data)
        assertEquals("Erika Musterfrau", MyDataCache.get(app, locked = false)!!.name)
        locked = true
        val callsBefore = cryptCalls
        for (operation in listOf<() -> Any?>({ store.read() }, { store.save(data) }, { store.delete() }))
            assertEquals(CredentialError.LOCKED, assertFailsWith<CredentialFailure> { operation() }.error)
        assertEquals(callsBefore, cryptCalls)
        // asked while locked: the cache is dropped, nothing is returned
        assertNull(MyDataCache.get(app, locked = true))
        // and it is not read again while the store refuses
        assertNull(MyDataCache.get(app, locked = false))
        locked = false
        assertEquals("Erika Musterfrau", MyDataCache.get(app, locked = false)!!.name)
    }

    @Test fun saveAndDeleteDropTheCache() {
        store.save(data)
        assertEquals("Erika Musterfrau", MyDataCache.get(app, false)!!.name)
        store.save(MyData(name = "Max"))
        assertEquals("Max", MyDataCache.get(app, false)!!.name)
        store.delete()
        assertTrue(MyDataCache.get(app, false)!!.isEmpty)
        assertTrue(directory.listFiles().isNullOrEmpty())
        // saving empty data removes the file
        store.save(data)
        store.save(MyData())
        assertTrue(directory.listFiles().isNullOrEmpty())
    }

    @Test fun tamperedFilesFailClosedWithoutDeletingThem() {
        store.save(data)
        val file = directory.listFiles()!!.single()
        val modified = file.readBytes().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        file.writeBytes(modified)
        assertEquals(CredentialError.UNAVAILABLE, assertFailsWith<CredentialFailure> { store.read() }.error)
        assertContentEquals(modified, file.readBytes())
        assertNull(MyDataCache.get(app, false))
        file.writeBytes(ByteArray(20000))
        assertFailsWith<CredentialFailure> { store.read() }
    }

    @Test fun entriesAreBoundedAndNeverShownByToString() {
        val many = MyData(name = "x".repeat(500), emails = (1..10).map { "a$it@example.org" }).cleaned()
        assertEquals(MyData.MAX_LENGTH, many.name.length)
        assertEquals(MyData.MAX_ENTRIES, many.emails.size)
        assertFalse(many.toString().contains("example"))
        assertEquals("MyData(empty)", MyData().toString())
    }
}
