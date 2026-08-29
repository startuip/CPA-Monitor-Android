package io.cpamonitor.android.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

interface SecretCipher {
    fun encrypt(plaintext: String): String
    fun decrypt(blob: String): String
    fun deleteKey()
}

@Singleton
class KeystoreCipher @Inject constructor() : SecretCipher {
    private val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    override fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return "v1.${encode(cipher.iv)}.${encode(ciphertext)}"
    }

    override fun decrypt(blob: String): String {
        val parts = blob.split('.')
        require(parts.size == 3 && parts[0] == "v1") { "Unsupported credential format" }
        val key = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            ?: error("Credential encryption key is missing")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, decode(parts[1])))
        return cipher.doFinal(decode(parts[2])).toString(Charsets.UTF_8)
    }

    override fun deleteKey() {
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(value: String) = Base64.decode(value, Base64.NO_WRAP)

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "cpa_monitor_admin_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

