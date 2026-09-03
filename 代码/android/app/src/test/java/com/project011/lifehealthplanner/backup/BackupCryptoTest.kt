package com.project011.lifehealthplanner.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCryptoTest {
    @Test
    fun encryptedBackupRoundTripsWithoutPlaintext() {
        val plaintext = "{\"health\":\"private-test-value\"}"
        val envelope = BackupCrypto.encrypt(plaintext, "correct horse battery".toCharArray())
        assertNotEquals(plaintext, envelope.ciphertext)
        assertEquals(plaintext, BackupCrypto.decrypt(envelope, "correct horse battery".toCharArray()))
    }

    @Test
    fun tamperedBackupIsRejected() {
        val envelope = BackupCrypto.encrypt("private", "correct horse battery".toCharArray())
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.decrypt(envelope.copy(ciphertext = envelope.ciphertext + "A"), "correct horse battery".toCharArray())
        }
    }
}
