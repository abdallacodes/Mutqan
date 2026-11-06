package com.example.mutqan.domain.sync

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class SyncEngineTest {

    private fun logCount(text: String) = JSONObject(text).getJSONArray("revision_logs").length()

    private inline fun <reified T : Throwable> failsWith(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return
            throw e
        }
        fail("expected ${T::class.java.simpleName}")
    }

    private fun runBlockingPeek(remote: FakeRemote) = runBlocking { remote.peek() }

    /** Two devices that have both synced [shared] with one account. */
    private fun synced(shared: String): Triple<FakeRemote, Device, Device> {
        val remote = FakeRemote()
        val a = Device(remote, shared)
        val b = Device(remote, snapshot())
        a.sync()
        b.sync()
        assertEquals(shared, b.text)
        return Triple(remote, a, b)
    }

    // ── The basics ──────────────────────────────────────────────────────────

    // ── Joining an account: the user chooses, and only then does anything sync ──

    @Test
    fun `a device nobody has set up refuses to sync, whatever is on either side`() {
        val cases = listOf(
            FakeRemote(RemoteSnapshot(4, data(7, 8))) to data(1),   // data on both sides
            FakeRemote() to data(1),                                // data on the device only
            FakeRemote(RemoteSnapshot(4, data(7, 8))) to data(),    // data in the cloud only
            FakeRemote(RemoteSnapshot(4, data(1))) to data(1)       // the very same data
        )
        for ((remote, text) in cases) {
            val d = Device(remote, text, linked = false)
            val before = remote.cloud
            failsWith<NotLinkedException> { d.sync() }
            assertEquals(text, d.text)
            assertEquals(before, remote.cloud)
            assertEquals(0, remote.pushes)
            assertEquals(0, remote.fetches)
            assertNull(d.memory.hash)
        }
    }

    @Test
    fun `choosing the cloud copy takes it and drops what the device had`() {
        val remote = FakeRemote(RemoteSnapshot(4, data(7, 8))); val d = Device(remote, data(1), linked = false)
        assertEquals(SyncOutcome.PULLED, d.joinFromCloud())
        assertEquals(data(7, 8), d.text)
        assertEquals(4L, d.memory.version)
        assertEquals(data(7, 8), d.memory.snapshot)
        assertEquals(0, remote.pushes)
        assertEquals(SyncOutcome.NOTHING_TO_DO, d.sync())
    }

    @Test
    fun `choosing the cloud copy when the account has none empties the device and sets it up`() {
        val remote = FakeRemote(); val d = Device(remote, data(1), linked = false)
        assertEquals(SyncOutcome.PULLED, d.joinFromCloud())
        assertTrue(SyncEngine.isEmpty(d.text))
        assertEquals(1, d.local.cleared)
        assertEquals(0, remote.pushes)
        assertNull(remote.cloud)
        assertNotNull(d.memory.hash)
        assertEquals(SyncOutcome.NOTHING_TO_DO, d.sync())
        d.text = data(5)
        assertEquals(SyncOutcome.PUSHED, d.sync())
        assertEquals(setOf(5L), logsOf(remote.cloud!!.text))
    }

    @Test
    fun `choosing the cloud copy keeps the device's settings when the account has none`() {
        val remote = FakeRemote()
        val d = Device(remote, snapshot(logs = listOf(L(1)), settings = mapOf("recitation_reciter" to "husary")), linked = false)
        d.joinFromCloud()
        assertEquals("husary", settingsOf(d.text)["recitation_reciter"])
    }

    @Test
    fun `choosing this device replaces the cloud copy and does not merge with it`() {
        val remote = FakeRemote(RemoteSnapshot(4, data(7, 8))); val d = Device(remote, data(1), linked = false)
        assertEquals(SyncOutcome.PUSHED, d.joinWithDevice())
        assertEquals(setOf(1L), logsOf(remote.cloud!!.text))
        assertEquals(data(1), d.text)
        assertTrue(remote.cloud!!.version > 4)
        assertEquals(remote.cloud!!.version, d.memory.version)
        assertEquals(SyncOutcome.NOTHING_TO_DO, d.sync())
    }

    @Test
    fun `choosing this device when the account has nothing makes the first copy`() {
        val remote = FakeRemote(); val d = Device(remote, data(1), linked = false)
        assertEquals(SyncOutcome.PUSHED, d.joinWithDevice())
        assertEquals(data(1), remote.cloud?.text)
        assertEquals(1L, d.memory.version)
    }

    @Test
    fun `choosing this device refuses when the cloud copy changed after it was looked at`() {
        val remote = FakeRemote(RemoteSnapshot(4, data(7)))
        val d = Device(remote, data(1), linked = false)
        val seen = runBlockingPeek(remote)
        remote.put(data(7, 9)) // another device pushed in between
        failsWith<SyncConflictException> { runBlocking { SyncEngine(d.local, remote, d.memory).replaceCloud(seen) } }
        assertEquals(setOf(7L, 9L), logsOf(remote.cloud!!.text))
        assertNull(d.memory.hash)
    }

    @Test
    fun `a replacement that cannot be written does not set the device up`() {
        val remote = FakeRemote(RemoteSnapshot(4, data(7))); val d = Device(remote, data(1), linked = false)
        d.local.failNextApply = true
        failsWith<IOException> { d.joinFromCloud() }
        assertEquals(data(1), d.text)
        assertNull(d.memory.hash)
        failsWith<NotLinkedException> { d.sync() }
    }

    @Test
    fun `a push that fails does not set the device up`() {
        val remote = FakeRemote(RemoteSnapshot(4, data(7))); val d = Device(remote, data(1), linked = false)
        remote.failNextPush = true
        failsWith<IOException> { d.joinWithDevice() }
        assertEquals(setOf(7L), logsOf(remote.cloud!!.text))
        assertNull(d.memory.hash)
        failsWith<NotLinkedException> { d.sync() }
    }

    @Test
    fun `an account held by another device cannot be replaced from here`() {
        val remote = FakeRemote(RemoteSnapshot(4, data(7))); val d = Device(remote, data(1), linked = false)
        remote.displaced = true
        failsWith<SignedInElsewhereException> { d.joinWithDevice() }
        assertEquals(setOf(7L), logsOf(remote.cloud!!.text))
        assertNull(d.memory.hash)
    }

    // ── The basics ──────────────────────────────────────────────────────────

    @Test
    fun `the first sync with an empty cloud pushes`() {
        val remote = FakeRemote(); val d = Device(remote, data(1))
        assertEquals(SyncOutcome.PUSHED, d.sync())
        assertEquals(1L, remote.cloud?.version)
        assertEquals(1L, d.memory.version)
    }

    @Test
    fun `nothing changed means nothing is sent`() {
        val remote = FakeRemote(); val d = Device(remote, data(1))
        d.sync()
        assertEquals(SyncOutcome.NOTHING_TO_DO, d.sync())
        assertEquals(1, remote.pushes)
    }

    @Test
    fun `a change on this device is pushed`() {
        val remote = FakeRemote(); val d = Device(remote, data(1))
        d.sync()
        d.text = data(1, 2)
        assertEquals(SyncOutcome.PUSHED, d.sync())
        assertEquals(d.text, remote.cloud?.text)
        assertEquals(2L, d.memory.version)
    }

    @Test
    fun `a change from another device is pulled`() {
        val remote = FakeRemote(); val d = Device(remote, data(1))
        d.sync()
        remote.put(data(1, 5))
        assertEquals(SyncOutcome.PULLED, d.sync())
        assertEquals(data(1, 5), d.text)
        assertEquals(2L, d.memory.version)
        assertEquals(SyncOutcome.NOTHING_TO_DO, d.sync())
    }

    @Test
    fun `changes on both sides are merged and pushed`() {
        val remote = FakeRemote(); val d = Device(remote, data(1))
        d.sync()
        d.text = data(1, 2)
        remote.put(data(1, 3))
        assertEquals(SyncOutcome.MERGED, d.sync())
        assertEquals(setOf(1L, 2L, 3L), d.logs)
        assertEquals(d.text, remote.cloud?.text)
        assertEquals(3L, remote.cloud?.version)
    }

    @Test
    fun `an empty device that is set up takes the account's data without sending anything`() {
        val remote = FakeRemote(RemoteSnapshot(4, data(1, 2))); val d = Device(remote)
        assertEquals(SyncOutcome.PULLED, d.sync())
        assertEquals(setOf(1L, 2L), d.logs)
        assertEquals(0, remote.pushes)
    }

    @Test
    fun `nothing at all on either side sends nothing`() {
        val remote = FakeRemote(); val d = Device(remote)
        assertEquals(SyncOutcome.NOTHING_TO_DO, d.sync())
        assertNull(remote.cloud)
        assertEquals(0, remote.pushes)
    }

    // ── Conflicts and failures ──────────────────────────────────────────────

    @Test
    fun `a conflicting push is retried`() {
        val remote = FakeRemote(); val d = Device(remote, data(1))
        remote.conflicts = 1
        assertEquals(SyncOutcome.PUSHED, d.sync())
        assertNotNull(remote.cloud)
    }

    @Test
    fun `two conflicts are survived, three are not`() {
        val remote = FakeRemote(); val d = Device(remote, data(1))
        remote.conflicts = 2
        assertEquals(SyncOutcome.PUSHED, d.sync())
        val other = FakeRemote(); val e = Device(other, data(1))
        other.conflicts = 3
        failsWith<SyncConflictException> { e.sync() }
        assertNull(other.cloud)
    }

    @Test
    fun `endless conflicts give up`() {
        val remote = FakeRemote(); val d = Device(remote, data(1))
        remote.conflicts = 10
        failsWith<SyncConflictException> { d.sync() }
        assertNull(remote.cloud)
    }

    @Test
    fun `a conflict while merging leaves the same result after the retry`() {
        val (remote, a, b) = synced(data(1))
        a.text = data(1, 2); a.sync()
        b.text = data(1, 3)
        remote.conflicts = 1
        assertEquals(SyncOutcome.MERGED, b.sync())
        assertEquals(setOf(1L, 2L, 3L), b.logs)
        assertEquals(b.text, remote.cloud?.text)
    }

    @Test
    fun `no network changes nothing on the device or in the memory`() {
        val (remote, a, _) = synced(data(1))
        a.text = data(1, 2)
        val before = a.memory.hash
        remote.offline = true
        failsWith<IOException> { a.sync() }
        assertEquals(data(1, 2), a.text)
        assertEquals(before, a.memory.hash)
        remote.offline = false
        assertEquals(SyncOutcome.PUSHED, a.sync())
    }

    @Test
    fun `a push that fails after a merge keeps the merged data and finishes later`() {
        val (remote, a, b) = synced(data(1, 2))
        a.text = data(1, 2, 3); a.sync()
        b.text = data(1, 4) // deleted 2, added 4
        remote.failNextPush = true
        failsWith<IOException> { b.sync() }
        assertEquals(setOf(1L, 3L, 4L), b.logs)
        assertEquals(setOf(1L, 2L, 3L), logsOf(remote.cloud!!.text))
        assertEquals(SyncOutcome.MERGED, b.sync())
        assertEquals(setOf(1L, 3L, 4L), logsOf(remote.cloud!!.text))
        assertEquals(setOf(1L, 3L, 4L), b.logs)
    }

    @Test
    fun `a pull that cannot be written leaves the device and its memory alone`() {
        val (_, a, b) = synced(data(1))
        a.text = data(1, 2); a.sync()
        b.local.failNextApply = true
        failsWith<IOException> { b.sync() }
        assertEquals(data(1), b.text)
        assertEquals(1L, b.memory.version)
        assertEquals(SyncOutcome.PULLED, b.sync())
        assertEquals(setOf(1L, 2L), b.logs)
    }

    @Test
    fun `a merge that cannot be written pushes nothing`() {
        val (remote, a, b) = synced(data(1))
        a.text = data(1, 2); a.sync()
        b.text = data(1, 3)
        b.local.failNextApply = true
        val pushes = remote.pushes
        failsWith<IOException> { b.sync() }
        assertEquals(pushes, remote.pushes)
        assertEquals(data(1, 3), b.text)
    }

    // ── Wiping, deleting, recreating ────────────────────────────────────────

    @Test
    fun `an emptied device does not wipe the cloud copy`() {
        val remote = FakeRemote(); val d = Device(remote, data(1, 2))
        d.sync()
        d.text = data()
        assertEquals(SyncOutcome.PULLED, d.sync())
        assertEquals(2, logCount(d.text))
        assertEquals(1, remote.pushes)
    }

    @Test
    fun `an emptied device does not wipe a cloud copy that also changed`() {
        val (remote, a, b) = synced(data(1, 2))
        a.text = data(1, 2, 3); a.sync()
        b.text = data()
        assertEquals(SyncOutcome.PULLED, b.sync())
        assertEquals(setOf(1L, 2L, 3L), b.logs)
        assertEquals(setOf(1L, 2L, 3L), logsOf(remote.cloud!!.text))
    }

    @Test
    fun `a device with a few entries still pushes over a larger cloud copy`() {
        val remote = FakeRemote(); val d = Device(remote, data(1, 2, 3))
        d.sync()
        d.text = data(1)
        assertEquals(SyncOutcome.PUSHED, d.sync())
        assertEquals(setOf(1L), logsOf(remote.cloud!!.text))
    }

    @Test
    fun `an empty snapshot is told apart`() {
        assertTrue(SyncEngine.isEmpty(data()))
        assertFalse(SyncEngine.isEmpty(data(1)))
        assertFalse(SyncEngine.isEmpty(snapshot(coverage = listOf(1..2))))
        assertFalse(SyncEngine.isEmpty(snapshot(plans = mapOf("p" to 1))))
    }

    @Test
    fun `settings alone do not count as data`() {
        assertTrue(SyncEngine.isEmpty(snapshot(settings = mapOf("recitation_reciter" to "husary"))))
    }

    @Test
    fun `a deleted cloud copy is not made again, by any sync`() {
        val remote = FakeRemote(); val d = Device(remote, data(1))
        d.sync()
        remote.delete()
        failsWith<CloudCopyGoneException> { d.sync() }
        failsWith<CloudCopyGoneException> { d.sync() }
        assertNull(remote.cloud)
        assertEquals(1, remote.pushes)
        assertEquals(data(1), d.text)
    }

    @Test
    fun `a device set up against an empty account makes the first cloud copy`() {
        val remote = FakeRemote(); val d = Device(remote, data(1))
        assertEquals(SyncOutcome.PUSHED, d.sync())
    }

    @Test
    fun `after a deletion the user can start the cloud copy again by choosing this device`() {
        val remote = FakeRemote(); val d = Device(remote, data(1))
        d.sync()
        remote.delete()
        assertEquals(SyncOutcome.PUSHED, d.joinWithDevice())
        assertEquals(setOf(1L), logsOf(remote.cloud!!.text))
    }

    @Test
    fun `a deleted cloud copy that another device started again is pulled`() {
        val (remote, a, b) = synced(data(1, 2))
        remote.delete()
        a.text = data(1, 2, 9)
        assertEquals(SyncOutcome.PUSHED, a.joinWithDevice())
        assertTrue(remote.cloud!!.version > b.memory.version)
        assertEquals(SyncOutcome.PULLED, b.sync())
        assertEquals(setOf(1L, 2L, 9L), b.logs)
    }

    @Test
    fun `a deleted cloud copy started again while this device changed is merged`() {
        val (remote, a, b) = synced(data(1, 2))
        remote.delete()
        a.text = data(1, 2, 9); a.joinWithDevice()
        b.text = data(1, 2, 7)
        assertEquals(SyncOutcome.MERGED, b.sync())
        assertEquals(setOf(1L, 2L, 7L, 9L), b.logs)
    }

    @Test
    fun `huge version numbers are fine`() {
        val remote = FakeRemote(RemoteSnapshot(1_760_000_000_000L, data(1))); val d = Device(remote)
        assertEquals(SyncOutcome.PULLED, d.sync())
        d.text = data(1, 2)
        assertEquals(SyncOutcome.PUSHED, d.sync())
        assertTrue(remote.cloud!!.version > 1_760_000_000_000L)
    }

    // ── An account on one device at a time ──────────────────────────────────

    @Test
    fun `a device the account was taken from reads and sends nothing`() {
        val (remote, a, b) = synced(data(1))
        b.text = data(1, 2)
        remote.displaced = true
        val pushes = remote.pushes
        failsWith<SignedInElsewhereException> { b.sync() }
        assertEquals(data(1, 2), b.text)
        assertEquals(pushes, remote.pushes)
        assertEquals(1L, b.memory.version)
        assertEquals(data(1), a.memory.snapshot)
    }

    @Test
    fun `a device that takes the account back merges what it did meanwhile`() {
        val (remote, a, b) = synced(data(1, 2, 3))
        // b is displaced while it keeps changing things; a carries on.
        b.text = data(1, 2, 3, 40) // added 40
        a.text = data(1, 3, 5)     // deleted 2, added 5
        a.sync()
        remote.displaced = true
        failsWith<SignedInElsewhereException> { b.sync() }
        remote.displaced = false
        assertEquals(SyncOutcome.MERGED, b.sync())
        assertEquals(setOf(1L, 3L, 5L, 40L), b.logs)
        assertEquals(b.logs, logsOf(remote.cloud!!.text))
    }

    @Test
    fun `being displaced is not a conflict and is not retried`() {
        val remote = FakeRemote(); val d = Device(remote, data(1))
        d.sync()
        d.text = data(1, 2)
        remote.displaced = true
        failsWith<SignedInElsewhereException> { d.sync() }
        assertEquals(0, remote.conflicts)
    }

    // ── Two and three devices ───────────────────────────────────────────────

    @Test
    fun `two devices that both changed end up with the same data`() {
        val (_, a, b) = synced(data(1))
        a.text = data(1, 2)
        b.text = data(1, 3)
        a.sync()
        assertEquals(SyncOutcome.MERGED, b.sync())
        assertEquals(SyncOutcome.PULLED, a.sync())
        assertEquals(setOf(1L, 2L, 3L), a.logs)
        assertEquals(a.logs, b.logs)
        assertEquals(SyncOutcome.NOTHING_TO_DO, a.sync())
        assertEquals(SyncOutcome.NOTHING_TO_DO, b.sync())
    }

    @Test
    fun `three devices that all changed converge`() {
        val remote = FakeRemote()
        val a = Device(remote, data(1)); a.sync()
        val b = Device(remote); b.sync()
        val c = Device(remote); c.sync()
        a.text = data(1, 10)
        b.text = data(1, 20)
        c.text = data(1, 30)
        repeat(2) { listOf(a, b, c).forEach { it.sync() } }
        assertEquals(setOf(1L, 10L, 20L, 30L), a.logs)
        assertEquals(a.logs, b.logs)
        assertEquals(a.logs, c.logs)
    }

    @Test
    fun `syncing again after a pull or a merge changes nothing`() {
        val (_, a, b) = synced(data(1))
        a.text = data(1, 2); a.sync()
        b.text = data(1, 3); b.sync()
        a.sync()
        val pushes = a.remote.pushes
        listOf(a, b).forEach { assertEquals(SyncOutcome.NOTHING_TO_DO, it.sync()) }
        assertEquals(pushes, a.remote.pushes)
    }

    // ── Deletions and edits ─────────────────────────────────────────────────

    @Test
    fun `a deletion on one device reaches another that changed something else`() {
        val (_, a, b) = synced(data(1, 2, 3))
        a.text = data(1, 3); a.sync()
        b.text = data(1, 2, 3, 4)
        assertEquals(SyncOutcome.MERGED, b.sync())
        assertEquals(setOf(1L, 3L, 4L), b.logs)
        a.sync()
        assertEquals(setOf(1L, 3L, 4L), a.logs)
    }

    @Test
    fun `a deletion on the device that merges is kept too`() {
        val (_, a, b) = synced(data(1, 2, 3))
        a.text = data(1, 2, 3, 4); a.sync()
        b.text = data(1, 3)
        assertEquals(SyncOutcome.MERGED, b.sync())
        assertEquals(setOf(1L, 3L, 4L), b.logs)
    }

    @Test
    fun `the same entry deleted on both sides stays deleted`() {
        val (_, a, b) = synced(data(1, 2, 3))
        a.text = data(1, 3); a.sync()
        b.text = data(1, 3, 5)
        b.sync()
        assertEquals(setOf(1L, 3L, 5L), b.logs)
    }

    @Test
    fun `the same entry added on both sides is kept once`() {
        val (_, a, b) = synced(data(1))
        a.text = data(1, 5); a.sync()
        b.text = data(1, 5)
        b.sync()
        assertEquals(2, logCount(b.text))
        assertEquals(2, logCount(a.remote.cloud!!.text))
    }

    @Test
    fun `an edited entry replaces the old one instead of sitting beside it`() {
        val (_, a, b) = synced(snapshot(logs = listOf(L(1, outcome = 2))))
        a.text = snapshot(logs = listOf(L(1, outcome = 0))); a.sync()
        b.text = snapshot(logs = listOf(L(1, outcome = 2), L(5)))
        b.sync()
        assertEquals(mapOf(1L to 0, 5L to 2), outcomesOf(b.text))
    }

    @Test
    fun `an edit made on the merging device also replaces the old one`() {
        val (_, a, b) = synced(snapshot(logs = listOf(L(1, outcome = 2))))
        a.text = snapshot(logs = listOf(L(1, outcome = 2), L(5))); a.sync()
        b.text = snapshot(logs = listOf(L(1, outcome = 0)))
        b.sync()
        assertEquals(mapOf(1L to 0, 5L to 2), outcomesOf(b.text))
    }

    @Test
    fun `without the remembered copy the two sides are united and nothing is lost`() {
        val (remote, a, b) = synced(data(1, 2, 3))
        a.text = data(1, 3); a.sync()
        b.text = data(1, 2, 3, 4)
        b.memory.snapshot = null
        assertEquals(SyncOutcome.MERGED, b.sync())
        assertEquals(setOf(1L, 2L, 3L, 4L), b.logs)
        assertEquals(b.text, remote.cloud?.text)
    }

    @Test
    fun `a damaged remembered copy is ignored`() {
        val (_, a, b) = synced(data(1, 2))
        a.text = data(1, 2, 3); a.sync()
        b.text = data(1, 2, 4)
        b.memory.snapshot = "{not json"
        assertEquals(SyncOutcome.MERGED, b.sync())
        assertEquals(setOf(1L, 2L, 3L, 4L), b.logs)
    }

    @Test
    fun `what a sync leaves behind is the data on the device`() {
        val remote = FakeRemote(); val a = Device(remote, data(1))
        a.sync()
        assertEquals(a.text, a.memory.snapshot)
        val b = Device(remote); b.sync()
        assertEquals(b.text, b.memory.snapshot)
        a.text = data(1, 2); b.text = data(1, 3)
        a.sync(); b.sync()
        assertEquals(b.text, b.memory.snapshot)
        assertEquals(SyncEngine.hash(b.text), b.memory.hash)
    }

    @Test
    fun `a replace keeps the new copy as what was last synced`() {
        val remote = FakeRemote(RemoteSnapshot(3, data(7))); val d = Device(remote, data(1), linked = false)
        d.joinFromCloud()
        assertEquals(data(7), d.memory.snapshot)
    }

    // ── The other tables ────────────────────────────────────────────────────

    @Test
    fun `memorised ranges one device removed are removed everywhere`() {
        val (_, a, b) = synced(snapshot(coverage = listOf(1..10)))
        a.text = snapshot(coverage = listOf(4..10)); a.sync()
        b.text = snapshot(coverage = listOf(1..10, 20..25))
        b.sync()
        assertEquals((4..10).toSet() + (20..25).toSet(), coverageOf(b.text))
    }

    @Test
    fun `memorised ranges both devices added are united`() {
        val (_, a, b) = synced(snapshot(coverage = listOf(1..10)))
        a.text = snapshot(coverage = listOf(1..10, 30..40)); a.sync()
        b.text = snapshot(coverage = listOf(1..10, 20..25))
        b.sync()
        assertEquals((1..10).toSet() + (20..25).toSet() + (30..40).toSet(), coverageOf(b.text))
    }

    @Test
    fun `a plan edited on another device is adopted`() {
        val (_, a, b) = synced(snapshot(plans = mapOf("p" to 3)))
        a.text = snapshot(plans = mapOf("p" to 5)); a.sync()
        b.text = snapshot(plans = mapOf("p" to 3), logs = listOf(L(1)))
        b.sync()
        assertEquals(mapOf("p" to 5), plansOf(b.text))
    }

    @Test
    fun `a plan edited on both devices keeps the merging device's edit`() {
        val (_, a, b) = synced(snapshot(plans = mapOf("p" to 3)))
        a.text = snapshot(plans = mapOf("p" to 5)); a.sync()
        b.text = snapshot(plans = mapOf("p" to 7))
        b.sync()
        assertEquals(mapOf("p" to 7), plansOf(b.text))
    }

    @Test
    fun `a plan deleted on one device and edited on the other survives`() {
        val (_, a, b) = synced(snapshot(plans = mapOf("p" to 3, "q" to 1)))
        a.text = snapshot(plans = mapOf("q" to 1)); a.sync()
        b.text = snapshot(plans = mapOf("p" to 7, "q" to 1))
        b.sync()
        assertEquals(mapOf("p" to 7, "q" to 1), plansOf(b.text))
    }

    @Test
    fun `a plan deleted on one device and untouched on the other is gone`() {
        val (_, a, b) = synced(snapshot(plans = mapOf("p" to 3, "q" to 1)))
        a.text = snapshot(plans = mapOf("q" to 1)); a.sync()
        b.text = snapshot(plans = mapOf("p" to 3, "q" to 1), logs = listOf(L(1)))
        b.sync()
        assertEquals(mapOf("q" to 1), plansOf(b.text))
    }

    @Test
    fun `a plan added on each device is kept on both`() {
        val (_, a, b) = synced(snapshot(plans = mapOf("p" to 3)))
        a.text = snapshot(plans = mapOf("p" to 3, "mine" to 1)); a.sync()
        b.text = snapshot(plans = mapOf("p" to 3, "theirs" to 2))
        b.sync()
        assertEquals(mapOf("p" to 3, "mine" to 1, "theirs" to 2), plansOf(b.text))
    }

    // ── Settings ────────────────────────────────────────────────────────────

    @Test
    fun `a setting changed on one device reaches the other`() {
        val (_, a, b) = synced(snapshot(logs = listOf(L(1)), settings = mapOf("recitation_reciter" to "husary")))
        b.text = snapshot(logs = listOf(L(1)), settings = mapOf("recitation_reciter" to "minshawi"))
        assertEquals(SyncOutcome.PUSHED, b.sync())
        assertEquals(SyncOutcome.PULLED, a.sync())
        assertEquals("minshawi", settingsOf(a.text)["recitation_reciter"])
    }

    @Test
    fun `settings changed on both devices keep each device's own changes`() {
        val (_, a, b) = synced(snapshot(logs = listOf(L(1)), settings = mapOf("recitation_reciter" to "husary", "recitation_repeat" to 1)))
        a.text = snapshot(logs = listOf(L(1)), settings = mapOf("recitation_reciter" to "minshawi", "recitation_repeat" to 1)); a.sync()
        b.text = snapshot(logs = listOf(L(1)), settings = mapOf("recitation_reciter" to "husary", "recitation_repeat" to 3))
        b.sync()
        assertEquals(mapOf("recitation_reciter" to "minshawi", "recitation_repeat" to "3"), settingsOf(b.text))
    }

    @Test
    fun `the same setting changed on both devices keeps the merging device's`() {
        val (_, a, b) = synced(snapshot(logs = listOf(L(1)), settings = mapOf("recitation_reciter" to "husary")))
        a.text = snapshot(logs = listOf(L(1)), settings = mapOf("recitation_reciter" to "minshawi")); a.sync()
        b.text = snapshot(logs = listOf(L(1)), settings = mapOf("recitation_reciter" to "shatri"))
        b.sync()
        assertEquals("shatri", settingsOf(b.text)["recitation_reciter"])
        a.sync()
        assertEquals("shatri", settingsOf(a.text)["recitation_reciter"])
    }

    @Test
    fun `a new device takes the account's settings`() {
        val remote = FakeRemote()
        val a = Device(remote, snapshot(logs = listOf(L(1)), settings = mapOf("recitation_reciter" to "husary")))
        a.sync()
        val b = Device(remote, snapshot(logs = listOf(L(2))))
        b.sync()
        assertEquals("husary", settingsOf(b.text)["recitation_reciter"])
        assertEquals(setOf(1L, 2L), b.logs)
    }

    // ── Size ────────────────────────────────────────────────────────────────

    @Test
    fun `thousands of entries on both sides merge`() {
        val (_, a, b) = synced(snapshot(logs = (1L..5000L).map { L(it) }))
        a.text = snapshot(logs = (1L..5000L).map { L(it) } + (6000L..6100L).map { L(it) }); a.sync()
        b.text = snapshot(logs = (3L..5000L).map { L(it) } + (7000L..7050L).map { L(it) })
        b.sync()
        val expected = (3L..5000L).toSet() + (6000L..6100L).toSet() + (7000L..7050L).toSet()
        assertEquals(expected, b.logs)
        assertEquals(expected.size, logCount(b.text))
    }
}
