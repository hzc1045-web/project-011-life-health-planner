package com.project011.lifehealthplanner.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class CompanionCredentials(
    val serverUrl: String,
    val deviceId: String,
    val token: String,
)

class SecurePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("secure_companion", Context.MODE_PRIVATE)
    private val alias = "life_health_companion_credentials"

    fun getOrCreateDeviceId(): String {
        preferences.getString("device_id", null)?.let { return it }
        val value = "android-${UUID.randomUUID()}"
        preferences.edit().putString("device_id", value).apply()
        return value
    }

    fun saveCompanion(serverUrl: String, token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(), SecureRandom())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString("server_url", serverUrl.trimEnd('/'))
            .putString("token", encode(encrypted))
            .putString("token_iv", encode(cipher.iv))
            .apply()
    }

    fun credentials(): CompanionCredentials? {
        val server = preferences.getString("server_url", null) ?: return null
        val encrypted = preferences.getString("token", null) ?: return null
        val iv = preferences.getString("token_iv", null) ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, decode(iv)))
        val token = cipher.doFinal(decode(encrypted)).toString(Charsets.UTF_8)
        return CompanionCredentials(server, getOrCreateDeviceId(), token)
    }

    fun clearCompanion() {
        preferences.edit().remove("server_url").remove("token").remove("token_iv").apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encode(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)
}
