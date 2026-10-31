package com.example.mutqan.domain.sync

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

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
            val version = if (current.exists()) current.getLong("version") ?: 0L else 0L
            if (version != expectedVersion) return@runTransaction -1L
            val oldCount = if (current.exists()) (current.getLong("chunks") ?: 0L).toInt() else 0
            pieces.forEachIndexed { i, piece -> tr.set(chunks.document(chunkId(i)), mapOf("d" to piece)) }
            for (i in pieces.size until oldCount) tr.delete(chunks.document(chunkId(i)))
            tr.set(
                meta,
                mapOf(
                    "version" to version + 1,
                    "chunks" to pieces.size.toLong(),
                    "device" to deviceId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            version + 1
        }.await()
        return result.takeIf { it >= 0 }
    }

    /** Removes everything this account has in the cloud. */
    suspend fun deleteAll() {
        val doc = meta.get().await()
        val count = (doc.getLong("chunks") ?: 0L).toInt()
        val batch = db.batch()
        for (i in 0 until count) batch.delete(chunks.document(chunkId(i)))
        batch.delete(meta)
        batch.commit().await()
    }

    private fun chunkId(i: Int) = "%04d".format(i)
}
