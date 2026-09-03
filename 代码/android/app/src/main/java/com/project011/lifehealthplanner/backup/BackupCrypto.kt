package com.project011.lifehealthplanner.backup

import com.project011.lifehealthplanner.data.remote.BackupEnvelopeDto
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCrypto {
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256

    fun encrypt(plaintext: String, password: CharArray): BackupEnvelopeDto {
        require(password.size >= 10) { "恢复密码至少需要 10 个字符" }
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        val ciphertext = cipher.doFinal(gzip(plaintext.toByteArray(Charsets.UTF_8)))
        val encoded = encode(ciphertext)
        password.fill('\u0000')
        return BackupEnvelopeDto(
            createdAt = Instant.now().toString(),
            salt = encode(salt),
            nonce = encode(nonce),
            ciphertext = encoded,
            sha256 = sha256(encoded),
        )
    }

    fun decrypt(envelope: BackupEnvelopeDto, password: CharArray): String {
        require(sha256(envelope.ciphertext) == envelope.sha256) { "备份完整性校验失败" }
        val key = deriveKey(password, decode(envelope.salt))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, decode(envelope.nonce)))
        val plaintext = gunzip(cipher.doFinal(decode(envelope.ciphertext)))
        password.fill('\u0000')
        return plaintext.toString(Charsets.UTF_8)
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    private fun gzip(input: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
        GZIPOutputStream(output).use { it.write(input) }
        output.toByteArray()
    }

    private fun gunzip(input: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(input)).use { it.readBytes() }

    private fun encode(value: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value)

    private fun decode(value: String): ByteArray = Base64.getUrlDecoder().decode(value)

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
