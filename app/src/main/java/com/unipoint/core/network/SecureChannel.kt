package com.unipoint.core.network

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Lightweight AES-256-GCM encryption layer for Network mode.
 *
 * Design:
 * - PIN (or pre-shared key) → PBKDF2 → 256-bit AES key
 * - Every packet payload is encrypted with a random 12-byte IV
 * - Wire format becomes:  [type][len][IV (12)][ciphertext+tag]
 *
 * This is intentionally simple and auditable. For production you can
 * upgrade to Noise Protocol or TLS with certificate pinning.
 */
object SecureChannel {

    private const val ITERATIONS = 100_000
    private const val KEY_LEN = 256
    private const val GCM_TAG_BITS = 128
    private const val IV_LEN = 12

    fun deriveKey(pin: String, salt: ByteArray = "UniPoint-v1".toByteArray()): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LEN)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plain: ByteArray, key: SecretKeySpec): ByteArray {
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plain)
        return iv + ciphertext
    }

    fun decrypt(data: ByteArray, key: SecretKeySpec): ByteArray {
        require(data.size > IV_LEN) { "Ciphertext too short" }
        val iv = data.copyOfRange(0, IV_LEN)
        val ciphertext = data.copyOfRange(IV_LEN, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    /** Quick fingerprint of a key for UI display */
    fun keyFingerprint(key: SecretKeySpec): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.encoded)
        return digest.take(4).joinToString("") { "%02x".format(it) }
    }
}
