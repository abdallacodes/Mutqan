package com.example.mutqan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecallEvidenceTest {

    private val day = 86_400_000L

    private fun log(page: Int, outcome: RecallOutcome, onDay: Int, tags: Set<MistakeTag> = emptySet()) = RevisionLog(
        startPage = page,
        endPage = page,
        timestamp = onDay * day,
        manualStability = outcome.quality,
        dateMillis = onDay * day,
        outcome = outcome,
        mistakeTags = tags
    )

    private fun stateOfPage1(vararg logs: RevisionLog) = PassageStates.compute(logs.toList())[0]

    // ── Outcomes and tags ─────────────────────────────────────────────────────

    @Test
    fun `legacy quality maps to the nearest outcome`() {
        assertEquals(RecallOutcome.INDEPENDENT, RecallOutcome.fromQuality(1.0f))
        assertEquals(RecallOutcome.INDEPENDENT, RecallOutcome.fromQuality(0.9f))
        assertEquals(RecallOutcome.SELF_CORRECTED, RecallOutcome.fromQuality(0.8f))
        assertEquals(RecallOutcome.HINTED, RecallOutcome.fromQuality(0.5f))
        assertEquals(RecallOutcome.UNABLE, RecallOutcome.fromQuality(0.1f))
    }

    @Test
    fun `a slider value stored as a float still lands in its band`() {
        // 0.9f is 0.89999998... once it is a double in the database.
        assertEquals(RecallOutcome.INDEPENDENT, RecallOutcome.fromQuality(0.9f.toDouble().toFloat() - 1e-7f))
        assertEquals(RecallOutcome.SELF_CORRECTED, RecallOutcome.fromQuality(0.7f - 1e-7f))
        assertEquals(RecallOutcome.HINTED, RecallOutcome.fromQuality(0.4f - 1e-7f))
    }

    @Test
    fun `stored codes are stable and unknown codes fall back to independent`() {
        assertEquals(listOf(0, 1, 2, 3, 4), RecallOutcome.entries.map { it.code })
        assertEquals(RecallOutcome.INDEPENDENT, RecallOutcome.fromCode(99))
    }

    @Test
    fun `mistake tags survive the bit mask round trip`() {
        val tags = setOf(MistakeTag.OMISSION, MistakeTag.HESITATION, MistakeTag.SIMILAR_AYAH)
        assertEquals(tags, MistakeTag.fromMask(MistakeTag.toMask(tags)))
        assertEquals(emptySet<MistakeTag>(), MistakeTag.fromMask(0))
    }

    @Test
    fun `only independent and self corrected count as success`() {
        assertTrue(RecallOutcome.INDEPENDENT.isSuccess)
        assertTrue(RecallOutcome.SELF_CORRECTED.isSuccess)
        assertFalse(RecallOutcome.HINTED.isSuccess)
        assertFalse(RecallOutcome.UNABLE.isSuccess)
        assertFalse(RecallOutcome.PRACTICE_ONLY.isRecallAttempt)
    }

    // ── Passage states ────────────────────────────────────────────────────────

    @Test
    fun `of two attempts on the same day the one recorded last decides`() {
        val midnight = 20_000 * day
        fun same(outcome: RecallOutcome, recordedAt: Long) = RevisionLog(
            startPage = 1, endPage = 1, timestamp = recordedAt, manualStability = outcome.quality,
            dateMillis = midnight, outcome = outcome
        )
        val independentFirst = same(RecallOutcome.INDEPENDENT, midnight + 1_000)
        val unableAfter = same(RecallOutcome.UNABLE, midnight + 2_000)
        // The database lists newest first, whatever the order the dates tie in.
        assertEquals(PassageState.NEEDS_ATTENTION, stateOfPage1(unableAfter, independentFirst))
        assertEquals(PassageState.NEEDS_ATTENTION, stateOfPage1(independentFirst, unableAfter))
        // And the other way round the page has recovered.
        val unableFirst = same(RecallOutcome.UNABLE, midnight + 1_000)
        val independentAfter = same(RecallOutcome.INDEPENDENT, midnight + 2_000)
        assertEquals(PassageState.DEVELOPING, stateOfPage1(independentAfter, unableFirst))
    }

    @Test
    fun `a page nobody has tried to recall is not assessed`() {
        assertEquals(PassageState.NOT_ASSESSED, stateOfPage1())
        assertEquals(PassageState.NOT_ASSESSED, stateOfPage1(log(1, RecallOutcome.PRACTICE_ONLY, 1)))
    }

    @Test
    fun `one success is only developing`() {
        assertEquals(PassageState.DEVELOPING, stateOfPage1(log(1, RecallOutcome.INDEPENDENT, 1)))
    }

    @Test
    fun `two successes on the same day are not yet reliable`() {
        val state = stateOfPage1(log(1, RecallOutcome.INDEPENDENT, 1), log(1, RecallOutcome.INDEPENDENT, 1))
        assertEquals(PassageState.DEVELOPING, state)
    }

    @Test
    fun `successes on two days are reliable`() {
        val state = stateOfPage1(log(1, RecallOutcome.INDEPENDENT, 1), log(1, RecallOutcome.SELF_CORRECTED, 3))
        assertEquals(PassageState.RELIABLE, state)
    }

    @Test
    fun `successes spread over a month are stable over time`() {
        val state = stateOfPage1(
            log(1, RecallOutcome.INDEPENDENT, 1),
            log(1, RecallOutcome.INDEPENDENT, 15),
            log(1, RecallOutcome.INDEPENDENT, 40)
        )
        assertEquals(PassageState.STABLE_OVER_TIME, state)
    }

    @Test
    fun `a failed latest attempt needs attention`() {
        val state = stateOfPage1(
            log(1, RecallOutcome.INDEPENDENT, 1),
            log(1, RecallOutcome.INDEPENDENT, 20),
            log(1, RecallOutcome.UNABLE, 40)
        )
        assertEquals(PassageState.NEEDS_ATTENTION, state)
    }

    @Test
    fun `two helped attempts in the last three need attention`() {
        val state = stateOfPage1(
            log(1, RecallOutcome.INDEPENDENT, 1),
            log(1, RecallOutcome.HINTED, 2),
            log(1, RecallOutcome.HINTED, 3)
        )
        assertEquals(PassageState.NEEDS_ATTENTION, state)
    }

    @Test
    fun `reliable recall interrupted by one hint drops back to developing`() {
        val state = stateOfPage1(
            log(1, RecallOutcome.INDEPENDENT, 1),
            log(1, RecallOutcome.INDEPENDENT, 3),
            log(1, RecallOutcome.HINTED, 5)
        )
        assertEquals(PassageState.DEVELOPING, state)
    }

    @Test
    fun `practice logs never change a state`() {
        val state = stateOfPage1(
            log(1, RecallOutcome.INDEPENDENT, 1),
            log(1, RecallOutcome.PRACTICE_ONLY, 5),
            log(1, RecallOutcome.PRACTICE_ONLY, 6)
        )
        assertEquals(PassageState.DEVELOPING, state)
    }

    @Test
    fun `a log marks every page it covers`() {
        val states = PassageStates.compute(
            listOf(RevisionLog(startPage = 2, endPage = 4, timestamp = day, manualStability = 0.95f))
        )
        assertEquals(PassageState.NOT_ASSESSED, states[0])
        assertEquals(PassageState.DEVELOPING, states[1])
        assertEquals(PassageState.DEVELOPING, states[3])
        assertEquals(PassageState.NOT_ASSESSED, states[4])
    }

    // ── Memory engine ─────────────────────────────────────────────────────────

    @Test
    fun `practice only logs are not tracked by the memory engine`() {
        val state = MemoryEngine.computeCurrentState(
            listOf(log(1, RecallOutcome.PRACTICE_ONLY, 1)), emptyMap()
        )
        assertEquals(0f, state.stability[0], 0f)
    }

    @Test
    fun `pages outside the coverage are not tracked`() {
        val covered = BooleanArray(MemoryEngine.TOTAL_PAGES).also { it[0] = true }
        val logs = listOf(
            RevisionLog(startPage = 1, endPage = 2, timestamp = day, manualStability = 1f)
        )
        val state = MemoryEngine.computeCurrentState(logs, emptyMap(), covered)
        assertTrue(state.stability[0] > 0f)
        assertEquals(0f, state.stability[1], 0f)
    }
}
