package com.example.mutqan.domain.sync

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

/** What the last successful sync left behind: the hash of this device's data and the cloud version it matched. */
interface SyncMemory {
    suspend fun lastLocalHash(): String?
    suspend fun lastRemoteVersion(): Long
    suspend fun remember(localHash: String, remoteVersion: Long)
}

enum class SyncOutcome { NOTHING_TO_DO, PUSHED, PULLED, MERGED }

class SyncConflictException : Exception("The cloud copy kept changing")

/**
 * Brings this device and the cloud copy to the same state. It remembers what the two looked like after the last sync,
 * so it can tell which side changed: neither (nothing to do), only this device (push), only the cloud (pull), or both
 * (merge, then push).
 */
class SyncEngine(private val local: SyncLocal, private val remote: SyncRemote, private val memory: SyncMemory) {

    suspend fun sync(): SyncOutcome {
        repeat(ATTEMPTS) {
            attempt()?.let { return it }
        }
        throw SyncConflictException()
    }

    /** Makes this device's data the cloud copy's, discarding what is on the device; with no cloud copy it is an ordinary sync. */
    suspend fun replaceLocal(): SyncOutcome {
        val cloud = remote.fetch() ?: return sync()
        local.apply(cloud.text)
        memory.remember(hash(local.snapshot()), cloud.version)
        return SyncOutcome.PULLED
    }

    /** One pass; null when the cloud copy changed under us and the pass must be repeated. */
    private suspend fun attempt(): SyncOutcome? {
        val cloud = remote.fetch()
        val text = local.snapshot()

        if (cloud == null) {
            val version = remote.push(text, 0) ?: return null
            memory.remember(hash(text), version)
            return SyncOutcome.PUSHED
        }

        val localChanged = hash(text) != memory.lastLocalHash()
        val cloudChanged = cloud.version != memory.lastRemoteVersion()
        return when {
            !localChanged && !cloudChanged -> SyncOutcome.NOTHING_TO_DO
            localChanged && !cloudChanged -> {
                val version = remote.push(text, cloud.version) ?: return null
                memory.remember(hash(text), version)
                SyncOutcome.PUSHED
            }
            !localChanged -> {
                local.apply(cloud.text)
                memory.remember(hash(local.snapshot()), cloud.version)
                SyncOutcome.PULLED
            }
            else -> {
                local.apply(SnapshotMerge.merge(JSONObject(text), JSONObject(cloud.text)).toString())
                val merged = local.snapshot()
                val version = remote.push(merged, cloud.version) ?: return null
                memory.remember(hash(merged), version)
                SyncOutcome.MERGED
            }
        }
    }

    companion object {
        private const val ATTEMPTS = 3

        fun hash(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
