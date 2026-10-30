package com.example.mutqan.domain

import android.util.Base64
import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** The backup file is encrypted and needs its password. */
class PasswordRequiredException : Exception()

/** The password does not open the backup file (or the file was changed). */
class WrongPasswordException : Exception()

/**
 * Password protection for backup files: AES-256-GCM with a key from PBKDF2-HMAC-SHA256. The
 * file is a small JSON envelope, so an unprotected backup and a protected one are told apart
 * by [isEncrypted]. Nothing is stored: without the password the file cannot be opened.
 */
object BackupCrypto {

    private const val FORMAT = "mutqan-encrypted-backup"
    private const val VERSION = 1
    private const val ITERATIONS = 200_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BYTES = 32
    private const val TAG_BITS = 128

    const val MIN_PASSWORD_LENGTH = 6

    fun isEncrypted(text: String): Boolean =
        try {
            JSONObject(text).optString("format") == FORMAT
        } catch (e: Exception) {
            false
        }

    fun encrypt(plain: String, password: String): String {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(deriveKey(password, salt), "AES"), GCMParameterSpec(TAG_BITS, iv))
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("iterations", ITERATIONS)
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(data, Base64.NO_WRAP))
            .toString()
    }

    /** @throws WrongPasswordException when [password] does not open [envelope]. */
    fun decrypt(envelope: String, password: String): String {
        val o = JSONObject(envelope)
        val salt = Base64.decode(o.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(o.getString("iv"), Base64.NO_WRAP)
        val data = Base64.decode(o.getString("data"), Base64.NO_WRAP)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val key = deriveKey(password, salt, o.optInt("iterations", ITERATIONS))
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            throw WrongPasswordException()
        }
    }

    /** PBKDF2-HMAC-SHA256, one 32-byte block (written out because the platform class needs API 26). */
    internal fun deriveKey(password: String, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        var u = mac.doFinal(salt + byteArrayOf(0, 0, 0, 1))
        val t = u.copyOf()
        repeat(iterations - 1) {
            u = mac.doFinal(u)
            for (i in t.indices) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
        }
        return t.copyOf(KEY_BYTES)
    }
}
