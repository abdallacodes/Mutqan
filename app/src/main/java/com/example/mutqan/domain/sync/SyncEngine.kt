package com.example.mutqan.domain.sync

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** The copy of the user's data kept in the cloud: [text] is a snapshot, [version] counts the pushes. */
class RemoteSnapshot(val version: Long, val text: String)

/** This device's data as snapshot text, and the way to replace it. */
interface SyncLocal {
    suspend fun snapshot(): String
    suspend fun apply(text: String)
}

/** The cloud side. [push] succeeds only if the copy is still at [expectedVersion] and returns the new version, else null. */
interface SyncRemote {
    suspend fun fetch(): RemoteSnapshot?
    suspend fun push(text: String, expectedVersion: Long): Long?
}

/**
 * What the last successful sync left behind: the hash of this device's data, the cloud version it matched, and the
 * data itself, which tells a later merge what each side added, changed or deleted since.
 */
interface SyncMemory {
    suspend fun lastLocalHash(): String?
    suspend fun lastRemoteVersion(): Long
    suspend fun lastSnapshot(): String?
    suspend fun remember(localHash: String, remoteVersion: Long, snapshot: String)
}

enum class SyncOutcome { NOTHING_TO_DO, PUSHED, PULLED, MERGED }

class SyncConflictException : Exception("The cloud copy kept changing")

/** The account is in use on another device now, so this one must not read or change its cloud copy. */
class SignedInElsewhereException : Exception("The account is in use on another device")

/**
 * Brings this device and the cloud copy to the same state. It remembers what the two looked like after the last sync,
 * so it can tell which side changed: neither (nothing to do), only this device (push), only the cloud (pull), or both
 * (merge, then push).
 */
class SyncEngine(private val local: SyncLocal, private val remote: SyncRemote, private val memory: SyncMemory) {

    /**
     * [recreateCloud] false (the automatic syncs): a cloud copy that this device had synced with and that is now gone
     * was deleted on purpose, from here or from another device, so it is not uploaded again without being asked.
     */
    suspend fun sync(recreateCloud: Boolean = true): SyncOutcome {
        repeat(ATTEMPTS) {
            attempt(recreateCloud)?.let { return it }
        }
        throw SyncConflictException()
    }

    /** Makes this device's data the cloud copy's, discarding what is on the device; with no cloud copy it is an ordinary sync. */
    suspend fun replaceLocal(): SyncOutcome {
        val cloud = remote.fetch() ?: return sync()
        pull(cloud)
        return SyncOutcome.PULLED
    }

    private suspend fun pull(cloud: RemoteSnapshot) {
        local.apply(cloud.text)
        val now = local.snapshot()
        memory.remember(hash(now), cloud.version, now)
    }

    /** One pass; null when the cloud copy changed under us and the pass must be repeated. */
    private suspend fun attempt(recreateCloud: Boolean): SyncOutcome? {
        val cloud = remote.fetch()
        val text = local.snapshot()

        if (cloud == null) {
            if (!recreateCloud && memory.lastRemoteVersion() > 0) return SyncOutcome.NOTHING_TO_DO
            // Nothing to keep in the cloud yet.
            if (isEmpty(text)) return SyncOutcome.NOTHING_TO_DO
            val version = remote.push(text, 0) ?: return null
            memory.remember(hash(text), version, text)
            return SyncOutcome.PUSHED
        }

        val localChanged = hash(text) != memory.lastLocalHash()
        val cloudChanged = cloud.version != memory.lastRemoteVersion()
        return when {
            !localChanged && !cloudChanged -> SyncOutcome.NOTHING_TO_DO
            // A device that holds nothing never overwrites a cloud copy that holds something: wiping the device
            // (or a fresh install) is not a request to wipe the account; "delete cloud copy" is for that.
            localChanged && isEmpty(text) && !isEmpty(cloud.text) -> {
                pull(cloud)
                SyncOutcome.PULLED
            }
            localChanged && !cloudChanged -> {
                val version = remote.push(text, cloud.version) ?: return null
                memory.remember(hash(text), version, text)
                SyncOutcome.PUSHED
            }
            !localChanged -> {
                pull(cloud)
                SyncOutcome.PULLED
            }
            else -> {
                // The last synced data tells a deletion from something the other side never had.
                val base = memory.lastSnapshot()?.let { runCatching { JSONObject(it) }.getOrNull() }
                local.apply(SnapshotMerge.merge(JSONObject(text), JSONObject(cloud.text), base).toString())
                val merged = local.snapshot()
                val version = remote.push(merged, cloud.version) ?: return null
                memory.remember(hash(merged), version, merged)
                SyncOutcome.MERGED
            }
        }
    }

    companion object {
        private const val ATTEMPTS = 3

        /** True when the snapshot holds no entries at all: every list in it is empty. */
        fun isEmpty(text: String): Boolean {
            val root = JSONObject(text)
            return root.keys().asSequence().all { ((root.opt(it) as? JSONArray)?.length() ?: 0) == 0 }
        }

        fun hash(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
