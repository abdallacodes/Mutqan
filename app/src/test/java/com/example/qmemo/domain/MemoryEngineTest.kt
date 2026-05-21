package com.example.qmemo.domain

import com.example.qmemo.data.local.entity.RevisionLogEntity
import org.junit.Assert.*
import org.junit.Test

class MemoryEngineTest {

    private val MS_PER_DAY = 86_400_000L

    @Test
    fun `initial stability follows cubic law`() {
        val now = System.currentTimeMillis()
        val logs = listOf(
            RevisionLogEntity(startPage = 1, endPage = 1, timestamp = now, manualStability = 1.0f)
        )
        val state = MemoryEngine.computeCurrentState(logs, emptyMap())
        
        // 50 * 1.0^3 = 50.0
        assertEquals(50.0f, state.stability[0], 0.1f)
    }

    @Test
    fun `stability accumulates over multiple smooth revisions`() {
        val now = System.currentTimeMillis()
        val log1 = RevisionLogEntity(startPage = 1, endPage = 1, timestamp = now, manualStability = 1.0f)
        val log2 = RevisionLogEntity(startPage = 1, endPage = 1, timestamp = now + 7 * MS_PER_DAY, manualStability = 1.0f)
        
        val state = MemoryEngine.computeCurrentState(listOf(log1, log2), emptyMap())
        
        // Initial was 50. after 7 days, R is high, next stability should be > 50
        assertTrue("Stability should grow, expected > 50, got ${state.stability[0]}", state.stability[0] > 50f)
    }

    @Test
    fun `stability drops on critical failure`() {
        val now = System.currentTimeMillis()
        val log1 = RevisionLogEntity(startPage = 1, endPage = 1, timestamp = now, manualStability = 1.0f)
        val log2 = RevisionLogEntity(startPage = 1, endPage = 1, timestamp = now + 7 * MS_PER_DAY, manualStability = 0.1f)
        
        val state = MemoryEngine.computeCurrentState(listOf(log1, log2), emptyMap())
        
        assertTrue("Stability should drop, expected < 50, got ${state.stability[0]}", state.stability[0] < 50f)
    }

    @Test
    fun `semantic interference penalizes similar pages`() {
        val now = System.currentTimeMillis()
        val links = mapOf(1 to listOf(2))
        
        // Setup page 2 with some stability
        val log1 = RevisionLogEntity(startPage = 2, endPage = 2, timestamp = now, manualStability = 1.0f)
        // Fail page 1
        val log2 = RevisionLogEntity(startPage = 1, endPage = 1, timestamp = now + 1000, manualStability = 0.3f)
        
        val state = MemoryEngine.computeCurrentState(listOf(log1, log2), links)
        
        // Page 2 should have less than 50 stability
        assertTrue("Page 2 stability should be penalized, expected < 50, got ${state.stability[1]}", state.stability[1] < 50f)
    }

    @Test
    fun `retrievability at log time matches manual quality`() {
        val now = System.currentTimeMillis()
        val quality = 0.7f
        val log = RevisionLogEntity(startPage = 1, endPage = 1, timestamp = now, manualStability = quality)
        
        val state = MemoryEngine.computeCurrentState(listOf(log), emptyMap())
        val rs = MemoryEngine.projectRetrievability(state, 0, now)
        
        assertEquals(quality, rs[0], 0.05f)
    }
}
