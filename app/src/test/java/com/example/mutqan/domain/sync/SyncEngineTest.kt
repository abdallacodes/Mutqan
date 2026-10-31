package com.example.mutqan.domain.sync

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class SyncEngineTest {

    private class Local(var text: String) : SyncLocal {
        override suspend fun snapshot() = text
        override suspend fun apply(text: String) { this.text = text }
    }

    private class Remote(var cloud: RemoteSnapshot? = null) : SyncRemote {
        var pushes = 0

        /** Pushes to refuse with a version conflict before one is accepted. */
        var conflicts = 0
        override suspend fun fetch() = cloud
        override suspend fun push(text: String, expectedVersion: Long): Long? {
            if (conflicts > 0) { conflicts--; return null }
            if ((cloud?.version ?: 0L) != expectedVersion) return null
            pushes++
            return (expectedVersion + 1).also { cloud = RemoteSnapshot(it, text) }
        }
    }

    private class Memory(var hash: String? = null, var version: Long = 0) : SyncMemory {
        override suspend fun lastLocalHash() = hash
        override suspend fun lastRemoteVersion() = version
        override suspend fun remember(localHash: String, remoteVersion: Long) { hash = localHash; version = remoteVersion }
    }

    private fun data(vararg timestamps: Long) = JSONObject()
        .put("revision_logs", JSONArray(timestamps.map {
            JSONObject().put("timestamp", it).put("start_ayah_id", 1).put("end_ayah_id", 2).put("outcome", 2).put("mistake_tags", 0)
        })).toString()

    private fun sync(local: Local, remote: Remote, memory: Memory) = runBlocking { SyncEngine(local, remote, memory).sync() }

    @Test
    fun `replacing takes the cloud copy and drops what the device had`() {
        val local = Local(data(1)); val remote = Remote(RemoteSnapshot(4, data(7, 8))); val memory = Memory()
        assertEquals(SyncOutcome.PULLED, runBlocking { SyncEngine(local, remote, memory).replaceLocal() })
        assertEquals(data(7, 8), local.text)
        assertEquals(4L, memory.version)
        assertEquals(0, remote.pushes)
    }

    @Test
    fun `replacing with no cloud copy pushes like a normal sync`() {
        val local = Local(data(1)); val remote = Remote(); val memory = Memory()
        assertEquals(SyncOutcome.PUSHED, runBlocking { SyncEngine(local, remote, memory).replaceLocal() })
        assertEquals(1L, remote.cloud?.version)
    }

    @Test
    fun `the first sync with an empty cloud pushes`() {
        val local = Local(data(1)); val remote = Remote(); val memory = Memory()
        assertEquals(SyncOutcome.PUSHED, sync(local, remote, memory))
        assertEquals(1L, remote.cloud?.version)
        assertEquals(1L, memory.version)
    }

    @Test
    fun `nothing changed means nothing is sent`() {
        val local = Local(data(1)); val remote = Remote(); val memory = Memory()
        sync(local, remote, memory)
        assertEquals(SyncOutcome.NOTHING_TO_DO, sync(local, remote, memory))
        assertEquals(1, remote.pushes)
    }

    @Test
    fun `a change on this device is pushed`() {
        val local = Local(data(1)); val remote = Remote(); val memory = Memory()
        sync(local, remote, memory)
        local.text = data(1, 2)
        assertEquals(SyncOutcome.PUSHED, sync(local, remote, memory))
        assertEquals(local.text, remote.cloud?.text)
        assertEquals(2L, memory.version)
    }

    @Test
    fun `a change from another device is pulled`() {
        val local = Local(data(1)); val remote = Remote(); val memory = Memory()
        sync(local, remote, memory)
        remote.cloud = RemoteSnapshot(2, data(1, 5))
        assertEquals(SyncOutcome.PULLED, sync(local, remote, memory))
        assertEquals(data(1, 5), local.text)
        assertEquals(2L, memory.version)
        assertEquals(SyncOutcome.NOTHING_TO_DO, sync(local, remote, memory))
    }

    @Test
    fun `changes on both sides are merged and pushed`() {
        val local = Local(data(1)); val remote = Remote(); val memory = Memory()
        sync(local, remote, memory)
        local.text = data(1, 2)
        remote.cloud = RemoteSnapshot(2, data(1, 3))
        assertEquals(SyncOutcome.MERGED, sync(local, remote, memory))
        val logs = JSONObject(local.text).getJSONArray("revision_logs")
        assertEquals(setOf(1L, 2L, 3L), (0 until logs.length()).map { logs.getJSONObject(it).getLong("timestamp") }.toSet())
        assertEquals(local.text, remote.cloud?.text)
        assertEquals(3L, remote.cloud?.version)
    }

    @Test
    fun `a device that is new to an account merges with what is there`() {
        val local = Local(data(10)); val remote = Remote(RemoteSnapshot(4, data(1, 2))); val memory = Memory()
        assertEquals(SyncOutcome.MERGED, sync(local, remote, memory))
        assertEquals(3, JSONObject(local.text).getJSONArray("revision_logs").length())
    }

    @Test
    fun `a conflicting push is retried`() {
        val local = Local(data(1)); val remote = Remote(); val memory = Memory()
        remote.conflicts = 1
        assertEquals(SyncOutcome.PUSHED, sync(local, remote, memory))
        assertNotNull(remote.cloud)
    }

    @Test
    fun `endless conflicts give up`() {
        val local = Local(data(1)); val remote = Remote(); val memory = Memory()
        remote.conflicts = 10
        try {
            sync(local, remote, memory)
            fail("expected a conflict")
        } catch (e: SyncConflictException) {
            assertNull(remote.cloud)
        }
    }
}
