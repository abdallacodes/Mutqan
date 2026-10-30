package com.example.mutqan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCryptoTest {

    private val plain = "{\"version\":5,\"note\":\"سجل المراجعة\"}"

    @Test
    fun encryptedBackupOpensWithItsPassword() {
        val envelope = BackupCrypto.encrypt(plain, "correct horse")
        assertTrue(BackupCrypto.isEncrypted(envelope))
        assertFalse(envelope.contains("المراجعة"))
        assertEquals(plain, BackupCrypto.decrypt(envelope, "correct horse"))
    }

    @Test
    fun aWrongPasswordIsRejected() {
        val envelope = BackupCrypto.encrypt(plain, "correct horse")
        try {
            BackupCrypto.decrypt(envelope, "battery staple")
            fail("expected WrongPasswordException")
        } catch (expected: WrongPasswordException) {
        }
    }

    @Test
    fun everyEncryptionIsDifferent() {
        assertFalse(BackupCrypto.encrypt(plain, "pw-123456") == BackupCrypto.encrypt(plain, "pw-123456"))
    }
}
