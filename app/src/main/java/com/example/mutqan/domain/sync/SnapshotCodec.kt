package com.example.mutqan.domain.sync

import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Snapshot text <-> a few base64 pieces small enough for one Firestore document each. */
object SnapshotCodec {
    const val CHUNK_CHARS = 400_000

    fun encode(text: String): List<String> {
        val bytes = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) } }.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP).chunked(CHUNK_CHARS)
    }

    /** What the text must add up to when it is read back from the cloud. */
    fun fingerprint(text: String): Fingerprint = Fingerprint(text.toByteArray(Charsets.UTF_8).size.toLong(), SyncEngine.hash(text))

    fun matches(text: String, expected: Fingerprint): Boolean = fingerprint(text) == expected

    fun decode(chunks: List<String>): String {
        val bytes = Base64.decode(chunks.joinToString(""), Base64.NO_WRAP)
        return GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }.decodeToString()
    }
}
