package com.example.mutqan.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudRecordTest {
    private val fp = Fingerprint(10, "aa")

    /** The record a push of [plan] leaves behind, read back the way the next push and every reader read it. */
    private fun stored(plan: PushPlan): CloudRecord = CloudRecord.parse(plan.fields)!!

    @Test
    fun fingerprintCountsUtf8BytesAndHashesTheText() {
        val f = SnapshotCodec.fingerprint("éa")
        assertEquals(3L, f.bytes)
        assertEquals(SyncEngine.hash("éa"), f.sha256)
        assertTrue(SnapshotCodec.matches("éa", f))
        assertFalse(SnapshotCodec.matches("éb", f))
        assertFalse(SnapshotCodec.matches("ea", f))
    }

    @Test
    fun aRecordWithoutASchemaIsTheOldLayout() {
        val r = CloudRecord.parse(mapOf("version" to 5L, "chunks" to 2L, "device" to "x"))!!
        assertTrue(r.isLegacy)
        assertEquals(5L, r.version)
        assertEquals(2, r.pieceCount)
        assertNull(r.current)
        assertNull(r.previous)
    }

    @Test
    fun aRecordWithNoVersionHasNoCopy() {
        assertNull(CloudRecord.parse(mapOf("active" to "x")))
        assertNull(CloudRecord.parse(null))
    }

    @Test
    fun firstPushGoesToCopyA() {
        val plan = CloudLayout.plan(null, 2, fp, 100L)
        assertEquals("copyA", plan.slot)
        assertEquals(listOf("0000", "0001"), plan.ids)
        assertTrue(plan.staleInSlot.isEmpty())
        assertTrue(plan.staleLegacy.isEmpty())
        assertFalse(plan.fields.containsKey("prev"))
        assertEquals(2L, plan.fields["schema"])
        assertEquals(100L, plan.fields["version"])
        assertEquals(2L, plan.fields["chunks"])
    }

    @Test
    fun pushesTakeTurnsAndKeepTheCopyBefore() {
        val first = CloudLayout.plan(null, 1, Fingerprint(1, "a"), 100L)
        val r1 = stored(first)
        assertEquals("copyA", r1.current!!.slot)
        assertNull(r1.previous)

        val second = CloudLayout.plan(r1, 1, Fingerprint(2, "b"), 200L)
        assertEquals("copyB", second.slot)
        val r2 = stored(second)
        assertEquals("copyB", r2.current!!.slot)
        // The copy the second push replaces stays whole, and is described exactly as it was.
        assertEquals("copyA", r2.previous!!.slot)
        assertEquals(100L, r2.previous!!.version)
        assertEquals(Fingerprint(1, "a"), r2.previous!!.fingerprint)

        val third = CloudLayout.plan(r2, 1, Fingerprint(3, "c"), 300L)
        assertEquals("copyA", third.slot)
        val r3 = stored(third)
        assertEquals("copyB", r3.previous!!.slot)
        assertEquals(200L, r3.previous!!.version)
        assertEquals(Fingerprint(3, "c"), r3.current!!.fingerprint)
    }

    @Test
    fun aPushNeverWritesTheSlotOfTheCurrentCopy() {
        var record: CloudRecord? = null
        repeat(8) { n ->
            val plan = CloudLayout.plan(record, 1, fp, 100L + n)
            assertTrue(plan.slot != record?.current?.slot)
            if (record?.previous != null) assertEquals(record!!.previous!!.slot, plan.slot)
            record = stored(plan)
        }
    }

    @Test
    fun piecesLeftInTheSlotByAnOlderBiggerCopyAreRemoved() {
        val big = CloudLayout.plan(null, 3, fp, 100L)               // copyA: 0000..0002
        val r1 = stored(big)
        val mid = CloudLayout.plan(r1, 3, fp, 200L)                 // copyB: 0000..0002
        val r2 = stored(mid)
        val small = CloudLayout.plan(r2, 1, fp, 300L)               // copyA again: only 0000 now
        assertEquals("copyA", small.slot)
        assertEquals(listOf("0001", "0002"), small.staleInSlot)
        // Writing a bigger copy over a smaller one leaves nothing to remove.
        val r3 = stored(small)
        val grow = CloudLayout.plan(r3, 4, fp, 400L)                // copyB had 0000..0002
        assertEquals("copyB", grow.slot)
        assertTrue(grow.staleInSlot.isEmpty())
    }

    @Test
    fun theFirstPushOverTheOldLayoutRemovesItAndKeepsNoPreviousCopy() {
        val old = CloudRecord.parse(mapOf("version" to 7L, "chunks" to 2L))!!
        val plan = CloudLayout.plan(old, 1, fp, 8L)
        assertEquals("copyA", plan.slot)
        assertFalse(plan.fields.containsKey("prev"))
        // Both spellings of every old piece go, so no device still looking for them reads stale data.
        assertTrue(plan.staleLegacy.containsAll(listOf("0000", "0001")))
        assertTrue(plan.staleLegacy.containsAll(ChunkIds.legacy(0) + ChunkIds.legacy(1)))
        assertEquals(6, plan.staleLegacy.size)
        // Once in the new layout nothing of the old one is touched again.
        assertTrue(CloudLayout.plan(stored(plan), 1, fp, 9L).staleLegacy.isEmpty())
    }

    @Test
    fun aRecordThatContradictsItselfStillGetsAWritableSlot() {
        val info = mapOf(
            "version" to 3L, "chunks" to 1L, "schema" to 2L, "slot" to "copyA", "ids" to listOf("0000"), "bytes" to 1L, "sha256" to "a",
            "prev" to mapOf("slot" to "copyA", "ids" to listOf("0000"), "version" to 2L, "bytes" to 1L, "sha256" to "b")
        )
        val plan = CloudLayout.plan(CloudRecord.parse(info), 1, fp, 4L)
        assertEquals("copyB", plan.slot)
    }

    @Test
    fun aCopyThatCannotBeDescribedIsNotAcceptedAsOne() {
        val good = mapOf("slot" to "copyA", "ids" to listOf("0000"), "version" to 1L, "bytes" to 1L, "sha256" to "a")
        assertNotNull(CopyInfo.parse(good))
        assertNull(CopyInfo.parse(good + ("slot" to "elsewhere")))
        assertNull(CopyInfo.parse(good + ("ids" to listOf("0000", 1))))
        assertNull(CopyInfo.parse(good - "sha256"))
        assertNull(CopyInfo.parse(good - "bytes"))
        assertNull(CopyInfo.parse(null))
        // A new-layout record that lost its fields has no current copy, which a reader reports as damage.
        val broken = CloudRecord.parse(mapOf("version" to 3L, "chunks" to 1L, "schema" to 2L))!!
        assertFalse(broken.isLegacy)
        assertNull(broken.current)
    }
}
