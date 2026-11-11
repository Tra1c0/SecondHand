// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SecretCipher {
    fun seal(plaintext: ByteArray, associatedData: ByteArray): ByteArray
    fun open(sealed: ByteArray, associatedData: ByteArray): ByteArray
}

/** The byte format is version + 12-byte random IV + authenticated ciphertext. */
class AesGcmSecretCipher(private val key: (create: Boolean) -> SecretKey) : SecretCipher {
    override fun seal(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(true))
        cipher.updateAAD(associatedData)
        check(cipher.iv.size == 12)
        return byteArrayOf(1) + cipher.iv + cipher.doFinal(plaintext)
    }

    override fun open(sealed: ByteArray, associatedData: ByteArray): ByteArray {
        require(sealed.size >= 29 && sealed[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, sealed.copyOfRange(1, 13)))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(sealed, 13, sealed.size - 13)
    }
}

/** Keystore stores the AES key; bearer credentials are encrypted by it, never used as aliases. */
internal fun androidSecretCipher(alias: String = "agent.credentials.v1", mayCreateKey: () -> Boolean): SecretCipher = AesGcmSecretCipher { create ->
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) throw IllegalStateException("UNSUPPORTED")
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    if (store.containsAlias(alias)) {
        store.getKey(alias, null) as SecretKey // Failed/invalid keys are never silently regenerated.
    } else {
        check(create && mayCreateKey())
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
            generateKey()
        }
    }
}
