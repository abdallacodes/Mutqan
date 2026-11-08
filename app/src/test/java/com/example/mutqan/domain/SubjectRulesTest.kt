package com.example.mutqan.domain

import com.example.mutqan.data.local.entity.UserSubjectEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubjectRulesTest {
    private fun subject(id: Int, ayah: Int, text: String = "s$id") =
        UserSubjectEntity(id = id, subjectText = text, startAyahId = ayah, orderIndex = 0)

    @Test
    fun `a new subject at a free verse is inserted`() {
        val plan = SubjectRules.plan("Topic", 92, atTarget = null, replacing = null)
        assertNull(plan.delete)
        assertEquals(0, plan.write.id)
        assertEquals(92, plan.write.startAyahId)
        assertEquals("Topic", plan.write.subjectText)
    }

    @Test
    fun `a new subject at a taken verse renames the one there`() {
        val plan = SubjectRules.plan("New", 92, atTarget = subject(5, 92, "Old"), replacing = null)
        assertNull(plan.delete)
        assertEquals(5, plan.write.id)
        assertEquals("New", plan.write.subjectText)
        assertEquals(92, plan.write.startAyahId)
    }

    @Test
    fun `editing the text keeps the verse and the row`() {
        val edited = subject(5, 92, "Old")
        val plan = SubjectRules.plan("New", 92, atTarget = edited, replacing = edited)
        assertNull(plan.delete)
        assertEquals(edited.copy(subjectText = "New"), plan.write)
    }

    @Test
    fun `moving to a free verse updates the row`() {
        val edited = subject(5, 92)
        val plan = SubjectRules.plan("Same", 100, atTarget = null, replacing = edited)
        assertNull(plan.delete)
        assertEquals(5, plan.write.id)
        assertEquals(100, plan.write.startAyahId)
    }

    @Test
    fun `moving onto a taken verse removes the subject that was there`() {
        val edited = subject(5, 92)
        val other = subject(8, 100)
        val plan = SubjectRules.plan("Moved", 100, atTarget = other, replacing = edited)
        assertEquals(other, plan.delete)
        assertEquals(5, plan.write.id)
        assertEquals(100, plan.write.startAyahId)
    }

    @Test
    fun `a file with two subjects at one verse keeps the first`() {
        val list = listOf(subject(1, 7, "a"), subject(2, 9, "b"), subject(3, 7, "c"))
        assertEquals(listOf("a", "b"), SubjectRules.onePerVerse(list).map { it.subjectText })
    }
}
