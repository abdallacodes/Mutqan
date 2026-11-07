package com.example.mutqan.domain.sync

import android.util.Log
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

/** The device an account is in use on, and when it last synced (millis), if known. */
class ActiveDevice(val id: String, val atMillis: Long?)

/**
 * The cloud copy in Firestore. `users/{uid}` is the version record: the version, the device, and what the copy must add
 * up to (length, SHA-256, the ids of its pieces). The snapshot itself is in pieces (see [SnapshotCodec]) in one of two
 * collections, `copyA` and `copyB`, that take turns (see [CloudLayout]); the record says which holds the current copy and
 * describes the one before it. A push is one transaction that writes the other collection and flips the record, so a
 * reader never sees a half-written copy and the copy it replaces stays untouched.
 *
 * Copies saved by v6.4.1 and before are in the collection `chunks` and carry no checksum; they are still read, and the
 * first push in the new layout replaces them.
 */
class FirestoreRemote(
    private val uid: String,
    private val deviceId: String,
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : SyncRemote {

    private val meta get() = db.collection("users").document(uid)
    private val chunks get() = meta.collection(CloudLayout.LEGACY)

    override suspend fun fetch(): RemoteSnapshot? = read(forThisDevice = true)

    override suspend fun peek(): RemoteSnapshot? = read(forThisDevice = false)

    /**
     * [forThisDevice] is the read of a sync: a copy that cannot be read is an error, because merging an older copy into
     * this device's data could delete entries the older copy never had. A look at the account ([peek]) falls back to the
     * previous copy instead, so the user can still join it, or have it saved before it is deleted.
     */
    private suspend fun read(forThisDevice: Boolean): RemoteSnapshot? {
        val doc = meta.get().await()
        if (!doc.exists()) return null
        if (forThisDevice) checkActive(doc)
        val record = CloudRecord.parse(doc.data) ?: return null
        if (record.pieceCount == 0) return null
        val snapshot = try {
            if (record.isLegacy) RemoteSnapshot(record.version, readLegacy(record.pieceCount)) else readCurrent(record, fallBack = !forThisDevice)
        } catch (e: CloudCopyDamagedException) {
            // A push that landed while the pieces were read can look like damage: say so instead.
            ensureUnchanged(record.version)
            throw e
        }
        // A push may have landed while the pieces were read.
        ensureUnchanged(record.version)
        return snapshot
    }

    private suspend fun ensureUnchanged(version: Long) {
        if (meta.get().await().getLong("version") != version) throw IllegalStateException("The cloud copy changed while it was read")
    }

    private suspend fun readCurrent(record: CloudRecord, fallBack: Boolean): RemoteSnapshot {
        val current = record.current
            ?: throw CloudCopyDamagedException(record.pieceCount, emptyList(), "the version record does not say where the pieces are")
        try {
            return RemoteSnapshot(record.version, readCopy(current))
        } catch (e: CloudCopyDamagedException) {
            val previous = record.previous
            if (!fallBack || previous == null) throw e
            Log.w("FirestoreRemote", "The newest cloud copy cannot be read, using the one before it", e)
            val text = try {
                readCopy(previous)
            } catch (ignored: CloudCopyDamagedException) {
                throw e
            }
            return RemoteSnapshot(record.version, text, fromPrevious = true)
        }
    }

    /** The text of one copy, checked against its length and SHA-256; anything that does not add up is damage. */
    private suspend fun readCopy(copy: CopyInfo): String {
        val slot = meta.collection(copy.slot)
        val pieces = copy.ids.map { slot.document(it).get().await().getString("d") }
        if (pieces.any { it == null }) {
            throw CloudCopyDamagedException(copy.ids.size, idsIn(slot), "pieces are missing")
        }
        val text = try {
            SnapshotCodec.decode(pieces.map { it!! })
        } catch (e: Exception) {
            throw CloudCopyDamagedException(copy.ids.size, copy.ids, "the pieces do not decode: ${e.javaClass.simpleName}")
        }
        if (!SnapshotCodec.matches(text, copy.fingerprint)) {
            throw CloudCopyDamagedException(copy.ids.size, copy.ids, "the text does not match its length and checksum")
        }
        return text
    }

    /**
     * The copy of v6.4.1 and before. Normally each piece is fetched by its id; if one is not there the whole collection is
     * listed, because that build made the ids with the phone's numerals (see [ChunkIds]).
     */
    private suspend fun readLegacy(count: Int): String {
        val direct = (0 until count).map { chunks.document(ChunkIds.of(it)).get().await().getString("d") }
        val pieces = if (direct.none { it == null }) {
            direct.map { it!! }
        } else {
            val docs = chunks.get().await().documents
            val found = docs.mapNotNull { d -> d.getString("d")?.let { d.id to it } }.toMap()
            ChunkIds.assemble(count, found) ?: throw CloudCopyDamagedException(count, docs.map { it.id })
        }
        return try {
            SnapshotCodec.decode(pieces)
        } catch (e: Exception) {
            throw CloudCopyDamagedException(count, emptyList(), "the pieces do not decode: ${e.javaClass.simpleName}")
        }
    }

    private suspend fun idsIn(collection: CollectionReference) = collection.get().await().documents.map { it.id }

    override suspend fun push(text: String, expectedVersion: Long): Long? {
        val pieces = SnapshotCodec.encode(text)
        val fingerprint = SnapshotCodec.fingerprint(text)
        // Checked before anything is sent: a copy that would not read back to the same text is never uploaded.
        check(SnapshotCodec.matches(SnapshotCodec.decode(pieces), fingerprint)) { "The snapshot did not survive being packed" }
        val result = db.runTransaction { tr ->
            val current = tr.get(meta)
            val active = if (current.exists()) current.getString("active") else null
            if (active != null && active != deviceId) return@runTransaction DISPLACED
            val version = if (current.exists()) current.getLong("version") ?: 0L else 0L
            if (version != expectedVersion) return@runTransaction -1L
            // Never back to a number a deleted copy already used: another device that remembers "version 1" of the
            // old copy would take a brand-new version 1 for the same, unchanged data.
            val next = maxOf(version + 1, System.currentTimeMillis())
            val plan = CloudLayout.plan(if (current.exists()) CloudRecord.parse(current.data) else null, pieces.size, fingerprint, next)
            val slot = meta.collection(plan.slot)
            pieces.forEachIndexed { i, piece -> tr.set(slot.document(plan.ids[i]), mapOf("d" to piece)) }
            plan.staleInSlot.forEach { tr.delete(slot.document(it)) }
            plan.staleLegacy.forEach { tr.delete(chunks.document(it)) }
            tr.set(
                meta,
                plan.fields + mapOf(
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

    /**
     * Removes everything this account has in the cloud and checks, asking the server, that nothing is left. It deletes
     * every piece found in every collection, not just the ones the version record lists: a push that died half-way, an
     * older build or the previous copy may have left others.
     */
    suspend fun deleteAll() {
        val doc = meta.get().await()
        if (doc.exists()) checkActive(doc)
        val collections = listOf(chunks) + CloudLayout.SLOTS.map { meta.collection(it) }
        val pieces = collections.flatMap { c -> c.get().await().documents.map { it.reference } }
        // A batch holds 500 writes; the version document goes with the last, so one batch is the usual case.
        val groups = pieces.chunked(BATCH_SIZE).ifEmpty { listOf(emptyList()) }
        groups.forEachIndexed { i, group ->
            val batch = db.batch()
            group.forEach { batch.delete(it) }
            if (i == groups.lastIndex) batch.delete(meta)
            batch.commit().await()
        }
        check(!meta.get(Source.SERVER).await().exists() && collections.all { it.limit(1).get(Source.SERVER).await().isEmpty }) {
            "Part of the cloud copy is still there"
        }
    }

    private companion object {
        /** What the push transaction returns when another device holds the account. */
        const val DISPLACED = -2L

        /** Deletes per batch, under Firestore's limit of 500. */
        const val BATCH_SIZE = 400
    }
}
