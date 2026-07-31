package com.tanmay.notificationmirror.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val PREFS_NAME = "mirror_secure"
private const val KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "mirror_config_key"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val IV_LENGTH = 12
private const val TAG_BITS = 128

/**
 * Small string store encrypted with an AES-GCM key held in the Android Keystore.
 * Hand-rolled because androidx.security:security-crypto is deprecated in favour
 * of using the Keystore directly.
 */
class SecureStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun put(key: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + ciphertext
        prefs.edit().putString(key, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun get(key: String): String? {
        val packed = prefs.getString(key, null) ?: return null
        return try {
            val bytes = Base64.decode(packed, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_LENGTH))
            }
            String(cipher.doFinal(bytes, IV_LENGTH, bytes.size - IV_LENGTH), Charsets.UTF_8)
        } catch (_: Exception) {
            // A key rotation or restore to a new device makes old data undecryptable.
            remove(key)
            null
        }
    }

    fun remove(key: String) = prefs.edit().remove(key).apply()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}
