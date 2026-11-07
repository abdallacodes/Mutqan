package com.example.mutqan.domain.sync

/** What a snapshot's text must add up to once its pieces are put together: its length in UTF-8 bytes and its SHA-256. */
data class Fingerprint(val bytes: Long, val sha256: String)

/**
 * One copy of the snapshot in the cloud: the collection under `users/{uid}` that holds its pieces, their ids in order,
 * the version of the push that wrote it and what its text must add up to.
 */
data class CopyInfo(val slot: String, val ids: List<String>, val version: Long, val fingerprint: Fingerprint) {
    fun toMap(): Map<String, Any> =
        mapOf("slot" to slot, "ids" to ids, "version" to version, "bytes" to fingerprint.bytes, "sha256" to fingerprint.sha256)

    companion object {
        /** Null when anything is missing or of the wrong kind: a copy that cannot be described cannot be read. */
        fun parse(map: Map<*, *>?): CopyInfo? {
            map ?: return null
            val slot = (map["slot"] as? String)?.takeIf { it in CloudLayout.SLOTS } ?: return null
            val ids = (map["ids"] as? List<*>)?.map { it as? String ?: return null } ?: return null
            val version = (map["version"] as? Number)?.toLong() ?: return null
            val bytes = (map["bytes"] as? Number)?.toLong() ?: return null
            val sha256 = map["sha256"] as? String ?: return null
            return CopyInfo(slot, ids, version, Fingerprint(bytes, sha256))
        }
    }
}

/**
 * The version record `users/{uid}`, as far as the copies go. A [schema] below [CloudLayout.SCHEMA] is the layout of
 * v6.4.1 and before: [pieceCount] pieces in the collection `chunks`, nothing to check them against, and no [previous].
 */
class CloudRecord(
    val version: Long,
    val pieceCount: Int,
    val schema: Int,
    val current: CopyInfo?,
    val previous: CopyInfo?
) {
    val isLegacy get() = schema < CloudLayout.SCHEMA

    companion object {
        /** Null when the record has no version (an account that only claimed the device, with nothing saved). */
        fun parse(data: Map<String, Any?>?): CloudRecord? {
            data ?: return null
            val version = (data["version"] as? Number)?.toLong() ?: return null
            val count = (data["chunks"] as? Number)?.toInt() ?: 0
            val schema = (data["schema"] as? Number)?.toInt() ?: 0
            if (schema < CloudLayout.SCHEMA) return CloudRecord(version, count, schema, null, null)
            // The current copy is described by the record's own fields; the push that wrote it is the record's version.
            val current = CopyInfo.parse(data + ("version" to version))
            return CloudRecord(version, count, schema, current, CopyInfo.parse(data["prev"] as? Map<*, *>))
        }
    }
}

/** What one push writes, worked out from the record it finds. */
class PushPlan(
    /** The collection the new pieces go to. */
    val slot: String,
    val ids: List<String>,
    /** Pieces left in [slot] by the copy that was there, which the new one does not overwrite. */
    val staleInSlot: List<String>,
    /** Pieces of a copy in the old layout (collection `chunks`), which the first push in the new layout removes. */
    val staleLegacy: List<String>,
    /** The fields of the version record that describe the copy; the device fields are added by the caller. */
    val fields: Map<String, Any>
)

/**
 * Where the copies live. Two collections, [SLOTS], take turns: a push writes the one that does not hold the current copy
 * and, in the same transaction, makes it the current one. The copy it replaces stays untouched as the previous one, so
 * there is always one older copy to go back to when the newest cannot be read.
 */
object CloudLayout {
    /** The layout with a fingerprint and a previous copy. */
    const val SCHEMA = 2

    /** The collection of the old layout. */
    const val LEGACY = "chunks"

    val SLOTS = listOf("copyA", "copyB")

    fun plan(old: CloudRecord?, pieceCount: Int, fingerprint: Fingerprint, version: Long): PushPlan {
        val ids = (0 until pieceCount).map { ChunkIds.of(it) }
        val current = old?.current
        // The previous copy's slot is the one to write, unless the record is inconsistent and says it is the current one too.
        val slot = old?.previous?.slot?.takeIf { it != current?.slot } ?: SLOTS.first { it != current?.slot }
        val staleInSlot = old?.previous?.takeIf { it.slot == slot }?.ids.orEmpty() - ids.toSet()
        // Deleting the old layout also removes what an older build may have saved under other digits (see ChunkIds), so a
        // device that still looks for those never reads them as the current copy.
        val staleLegacy = if (old != null && old.isLegacy) {
            (0 until old.pieceCount).flatMap { listOf(ChunkIds.of(it)) + ChunkIds.legacy(it) }
        } else {
            emptyList()
        }
        val fields = buildMap<String, Any> {
            put("schema", SCHEMA.toLong())
            put("version", version)
            put("chunks", pieceCount.toLong())
            put("slot", slot)
            put("ids", ids)
            put("bytes", fingerprint.bytes)
            put("sha256", fingerprint.sha256)
            if (current != null) put("prev", current.toMap())
        }
        return PushPlan(slot, ids, staleInSlot, staleLegacy, fields)
    }
}
