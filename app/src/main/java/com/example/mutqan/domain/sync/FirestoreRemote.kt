package com.example.mutqan.domain.sync

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/** The device an account is in use on, and when it last synced (millis), if known. */
class ActiveDevice(val id: String, val atMillis: Long?)

/**
 * The cloud copy in Firestore: `users/{uid}` holds the version, and `users/{uid}/chunks/{n}` the snapshot in pieces
 * (see [SnapshotCodec]). A push is one transaction, so a reader never sees a half-written copy.
 */
class FirestoreRemote(
    private val uid: String,
    private val deviceId: String,
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : SyncRemote {

    private val meta get() = db.collection("users").document(uid)
    private val chunks get() = meta.collection("chunks")

    override suspend fun fetch(): RemoteSnapshot? {
        val doc = meta.get().await()
        if (!doc.exists()) return null
        checkActive(doc)
        val version = doc.getLong("version") ?: return null
        val count = (doc.getLong("chunks") ?: 0L).toInt()
        if (count == 0) return null
        val pieces = (0 until count).map { i ->
            chunks.document(chunkId(i)).get().await().getString("d") ?: throw IllegalStateException("Missing piece $i")
        }
        // A push may have landed while the pieces were read.
        val after = meta.get().await().getLong("version")
        if (after != version) throw IllegalStateException("The cloud copy changed while it was read")
        return RemoteSnapshot(version, SnapshotCodec.decode(pieces))
    }

    override suspend fun push(text: String, expectedVersion: Long): Long? {
        val pieces = SnapshotCodec.encode(text)
        val result = db.runTransaction { tr ->
            val current = tr.get(meta)
            val active = if (current.exists()) current.getString("active") else null
            if (active != null && active != deviceId) return@runTransaction DISPLACED
            val version = if (current.exists()) current.getLong("version") ?: 0L else 0L
            if (version != expectedVersion) return@runTransaction -1L
            val oldCount = if (current.exists()) (current.getLong("chunks") ?: 0L).toInt() else 0
            pieces.forEachIndexed { i, piece -> tr.set(chunks.document(chunkId(i)), mapOf("d" to piece)) }
            for (i in pieces.size until oldCount) tr.delete(chunks.document(chunkId(i)))
            // Never back to a number a deleted copy already used: another device that remembers "version 1" of the
            // old copy would take a brand-new version 1 for the same, unchanged data.
            val next = maxOf(version + 1, System.currentTimeMillis())
            tr.set(
                meta,
                mapOf(
                    "version" to next,
                    "chunks" to pieces.size.toLong(),
                    "device" to deviceId,
                    "active" to deviceId,
                    "activeAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            next
        }.await()
        if (result == DISPLACED) throw SignedInElsewhereException()
        return result.takeIf { it >= 0 }
    }

    /** The device the account is in use on, or null if none has claimed it. */
    suspend fun activeDevice(): ActiveDevice? {
        val doc = meta.get().await()
        if (!doc.exists()) return null
        val id = doc.getString("active") ?: return null
        return ActiveDevice(id, doc.getTimestamp("activeAt")?.toDate()?.time)
    }

    /** Makes this device the one the account is in use on; any other device finds out at its next sync. */
    suspend fun claim() {
        meta.set(mapOf("active" to deviceId, "activeAt" to FieldValue.serverTimestamp()), SetOptions.merge()).await()
    }

    private fun checkActive(doc: DocumentSnapshot) {
        val active = doc.getString("active")
        if (active != null && active != deviceId) throw SignedInElsewhereException()
    }

    /** Removes everything this account has in the cloud. */
    suspend fun deleteAll() {
        val doc = meta.get().await()
        if (doc.exists()) checkActive(doc)
        val count = (doc.getLong("chunks") ?: 0L).toInt()
        val batch = db.batch()
        for (i in 0 until count) batch.delete(chunks.document(chunkId(i)))
        batch.delete(meta)
        batch.commit().await()
    }

    private fun chunkId(i: Int) = "%04d".format(i)

    private companion object {
        /** What the push transaction returns when another device holds the account. */
        const val DISPLACED = -2L
    }
}
