package com.example.mutqan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BackupCryptoKeyTest {

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    @Test
    fun `key derivation matches the PBKDF2-HMAC-SHA256 test vectors`() {
        // RFC 7914 section 11, first 32 of the 64 bytes
        assertEquals(
            "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc",
            hex(BackupCrypto.deriveKey("passwd", "salt".toByteArray(), iterations = 1))
        )
        assertEquals(
            "4ddcd8f60b98be21830cee5ef22701f9641a4418d04c0414aeff08876b34ab56",
            hex(BackupCrypto.deriveKey("Password", "NaCl".toByteArray(), iterations = 80_000))
        )
    }

    @Test
    fun `a plain backup is not mistaken for an encrypted one`() {
        assertFalse(BackupCrypto.isEncrypted("{\"version\":5,\"revision_logs\":[]}"))
        assertFalse(BackupCrypto.isEncrypted("not json"))
    }
}
