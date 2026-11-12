package com.unipoint.core.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores an approved PC's pairing PIN encrypted with a non-exportable
 * AndroidKeyStore AES key. PINs never go into saved-device JSON or logs.
 * Existing Host session PINs may rotate when Host is restarted.
 */
object TrustedPcPairing {
    private const val ALIAS = "duopad_pc_pairing_aes_v1"
    private const val PREF = "duopad_pc_pairings_v1"

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun id(endpoint: String): String {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(endpoint.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP or Base64.URL_SAFE)
    }

    fun save(context: Context, endpoint: String, pin: String): Boolean = runCatching {
        require(endpoint.isNotBlank() && pin.length >= 8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(pin.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + encrypted
        val value = Base64.encodeToString(payload, Base64.NO_WRAP)
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(id(endpoint), value).apply()
        true
    }.getOrDefault(false)

    fun read(context: Context, endpoint: String): String? = runCatching {
        val text = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(id(endpoint), null) ?: return null
        val bytes = Base64.decode(text, Base64.NO_WRAP)
        require(bytes.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }.getOrNull()

    fun forget(context: Context, endpoint: String) {
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().remove(id(endpoint)).apply()
    }

    fun forgetAll(context: Context) {
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
