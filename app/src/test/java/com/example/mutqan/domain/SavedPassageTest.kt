package com.example.mutqan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedPassageTest {

    @Test
    fun `weak tags survive the bit mask round trip`() {
        val tags = setOf(WeakTag.FORGETS_BEGINNING, WeakTag.UNCERTAIN_WORDING)
        assertEquals(tags, WeakTag.fromMask(WeakTag.toMask(tags)))
        assertEquals(emptySet<WeakTag>(), WeakTag.fromMask(0))
    }

    @Test
    fun `stored codes are stable`() {
        assertEquals(listOf(0, 1), SavedKind.entries.map { it.code })
        assertEquals(SavedKind.BOOKMARK, SavedKind.fromCode(42))
    }

    @Test
    fun `a weak passage round trips through its entity`() {
        val passage = SavedPassage(
            id = 7,
            kind = SavedKind.WEAK,
            startAyahId = 262,
            endAyahId = 264,
            wordId = 4101,
            tags = setOf(WeakTag.WEAK_TRANSITION, WeakTag.SIMILAR_AYAH),
            priority = SavedPassage.PRIORITY_HIGH,
            note = "  watch the opening  ",
            createdAt = 1000L,
            resolvedAt = 5000L
        )
        val back = SavedPassage.fromEntity(passage.toEntity())
        assertEquals(passage.copy(note = "watch the opening"), back)
        assertTrue(back.isResolved)
    }

    @Test
    fun `a bookmark keeps no tags and is never resolved`() {
        val entity = SavedPassage(
            kind = SavedKind.BOOKMARK,
            startAyahId = 1,
            tags = setOf(WeakTag.OMISSION),
            resolvedAt = 99L
        ).toEntity()
        assertEquals(0, entity.tags)
        assertEquals(0L, entity.resolvedAt)
        assertFalse(SavedPassage.fromEntity(entity).isResolved)
    }

    @Test
    fun `priority is kept within range`() {
        assertEquals(SavedPassage.PRIORITY_HIGH, SavedPassage(kind = SavedKind.WEAK, startAyahId = 1, priority = 9).toEntity().priority)
        assertEquals(SavedPassage.PRIORITY_LOW, SavedPassage(kind = SavedKind.WEAK, startAyahId = 1, priority = -3).toEntity().priority)
    }
}
