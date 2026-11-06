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

    /** Removes everything the user made on this device; the settings stay. */
    suspend fun clear()
}

/** The cloud side. [push] succeeds only if the copy is still at [expectedVersion] and returns the new version, else null. */
interface SyncRemote {
    /** The cloud copy; throws [SignedInElsewhereException] when another device holds the account. */
    suspend fun fetch(): RemoteSnapshot?

    /** The same read for looking only: it works whoever holds the account and changes nothing. */
    suspend fun peek(): RemoteSnapshot?
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

/** [SyncEngine.sync] was asked for before the user chose how this device joins the account. */
class NotLinkedException : Exception("This device has not been set up to sync with the account")

/** The cloud copy this device synced with is gone: it was deleted on purpose, here or on another device. */
class CloudCopyGoneException : Exception("The cloud copy was deleted")

/** The account is in use on another device now, so this one must not read or change its cloud copy. */
class SignedInElsewhereException : Exception("The account is in use on another device")

/**
 * Keeps this device and the cloud copy at the same state, once the user has chosen how this device joins the account:
 * either [replaceLocal] (the cloud copy wins) or [replaceCloud] (this device wins). Only those two leave the memory
 * that makes a device "set up"; until then [sync] refuses and nothing moves in either direction.
 *
 * After that it remembers what the two looked like after the last sync, so it can tell which side changed: neither
 * (nothing to do), only this device (push), only the cloud (pull), or both (merge, then push).
 */
class SyncEngine(private val local: SyncLocal, private val remote: SyncRemote, private val memory: SyncMemory) {

    /**
     * A device that is set up and whose cloud copy has gone gets [CloudCopyGoneException]: the copy was deleted on
     * purpose, from here or from another device, so it is not uploaded again without being asked.
     */
    suspend fun sync(): SyncOutcome {
        if (memory.lastLocalHash() == null) throw NotLinkedException()
        repeat(ATTEMPTS) {
            attempt()?.let { return it }
        }
        throw SyncConflictException()
    }

    /**
     * The user chose the cloud copy: this device's data is replaced by [cloud] (what the caller looked at and saved a
     * copy of), or emptied when there is none. Only when that has worked is the device set up to sync.
     */
    suspend fun replaceLocal(cloud: RemoteSnapshot?): SyncOutcome {
        if (cloud == null) {
            local.clear()
            val now = local.snapshot()
            memory.remember(hash(now), 0, now)
        } else {
            pull(cloud)
        }
        return SyncOutcome.PULLED
    }

    /**
     * The user chose this device's data: the cloud copy is replaced by it. [seen] is the cloud copy the caller looked
     * at and saved a copy of; if it changed since, nothing is replaced. Only when the push has worked is the device
     * set up to sync.
     */
    suspend fun replaceCloud(seen: RemoteSnapshot?): SyncOutcome {
        val text = local.snapshot()
        val version = remote.push(text, seen?.version ?: 0) ?: throw SyncConflictException()
        memory.remember(hash(text), version, text)
        return SyncOutcome.PUSHED
    }

    private suspend fun pull(cloud: RemoteSnapshot) {
        local.apply(cloud.text)
        val now = local.snapshot()
        memory.remember(hash(now), cloud.version, now)
    }

    /** One pass; null when the cloud copy changed under us and the pass must be repeated. */
    private suspend fun attempt(): SyncOutcome? {
        val cloud = remote.fetch()
        val text = local.snapshot()

        if (cloud == null) {
            if (memory.lastRemoteVersion() > 0) throw CloudCopyGoneException()
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
