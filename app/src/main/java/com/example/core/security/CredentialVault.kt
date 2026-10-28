package com.example.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore AES-256-GCM encrypted credential vault.
 * Securely persists the user's Gemini API key on the device.
 * Never logs or exposes raw secret keys.
 */
class CredentialVault(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_vault_secure", Context.MODE_PRIVATE)
    private val keyAlias = "jarvis_gemini_master_key"

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(keyAlias)) {
            val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        val keyGenSpec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)
            .build()

        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }

    @Synchronized
    fun saveApiKey(apiKey: String, model: String = "gemini-2.5-flash") {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(apiKey.trim().toByteArray(Charsets.UTF_8))

        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val cipherB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        val last4 = if (apiKey.length >= 4) apiKey.takeLast(4) else apiKey

        prefs.edit()
            .putString("key_cipher", cipherB64)
            .putString("key_iv", ivB64)
            .putString("key_last4", last4)
            .putString("model", model)
            .putString("status", "valid")
            .putLong("verified_at", System.currentTimeMillis())
            .apply()
    }

    @Synchronized
    fun getApiKey(): String? {
        val cipherB64 = prefs.getString("key_cipher", null) ?: return null
        val ivB64 = prefs.getString("key_iv", null) ?: return null

        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)
            val ciphertext = Base64.decode(cipherB64, Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun hasKey(): Boolean = prefs.contains("key_cipher") && !getApiKey().isNullOrEmpty()

    fun getLast4(): String = prefs.getString("key_last4", "----") ?: "----"

    fun getModel(): String = prefs.getString("model", "gemini-2.5-flash") ?: "gemini-2.5-flash"

    fun setModel(model: String) {
        prefs.edit().putString("model", model).apply()
    }

    fun getStatus(): String = prefs.getString("status", "valid") ?: "valid"

    fun setStatus(status: String) {
        prefs.edit().putString("status", status).apply()
    }

    @Synchronized
    fun disconnect() {
        prefs.edit().clear().apply()
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias)
            }
        } catch (_: Exception) {}
    }
}
