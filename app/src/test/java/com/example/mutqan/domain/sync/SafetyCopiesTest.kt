package com.example.mutqan.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyCopiesTest {

    @Test
    fun `a copy's name says what happened to the data and when`() {
        val name = SafetyCopies.fileName(CopyReason.CLOUD_DELETED, 1_794_000_000_000L)
        assertTrue(name, Regex("mutqan-cloud-deleted-\\d{8}-\\d{6}\\.json").matches(name))
    }

    @Test
    fun `every reason has its own name`() {
        val names = CopyReason.entries.map { SafetyCopies.fileName(it, 1_794_000_000_000L).substringBeforeLast('-').substringBeforeLast('-') }
        assertEquals(CopyReason.entries.size, names.toSet().size)
    }

    @Test
    fun `two copies a second apart do not share a name`() {
        assertNotEquals(
            SafetyCopies.fileName(CopyReason.DEVICE_DELETED, 1_794_000_000_000L),
            SafetyCopies.fileName(CopyReason.DEVICE_DELETED, 1_794_000_001_000L)
        )
    }
}
