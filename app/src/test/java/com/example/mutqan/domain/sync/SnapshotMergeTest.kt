package com.example.mutqan.domain.sync

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SnapshotMergeTest {

    private fun log(ts: Long, id: Int, outcome: Int = 2) = JSONObject()
        .put("id", id).put("timestamp", ts).put("start_ayah_id", 1).put("end_ayah_id", 7)
        .put("outcome", outcome).put("mistake_tags", 0)

    private fun snapshot(logs: List<JSONObject> = emptyList(), plans: List<String> = emptyList()) = JSONObject()
        .put("revision_logs", JSONArray(logs))
        .put("revision_plans", JSONArray(plans.map { JSONObject().put("name", it) }))

    @Test
    fun `logs from both devices are kept once, without ids`() {
        val local = snapshot(listOf(log(100, 1), log(200, 2)))
        val remote = snapshot(listOf(log(200, 9), log(300, 4)))
        val logs = SnapshotMerge.merge(local, remote).getJSONArray("revision_logs")
        assertEquals(listOf(100L, 200L, 300L), (0 until logs.length()).map { logs.getJSONObject(it).getLong("timestamp") })
        assertFalse((0 until logs.length()).any { logs.getJSONObject(it).has("id") })
    }

    @Test
    fun `the same moment with a different outcome is a different log`() {
        val merged = SnapshotMerge.merge(snapshot(listOf(log(100, 1, outcome = 2))), snapshot(listOf(log(100, 1, outcome = 0))))
        assertEquals(2, merged.getJSONArray("revision_logs").length())
    }

    @Test
    fun `plans come from this device, or from the cloud when this device has none`() {
        assertEquals(
            "mine",
            SnapshotMerge.merge(snapshot(plans = listOf("mine")), snapshot(plans = listOf("theirs")))
                .getJSONArray("revision_plans").getJSONObject(0).getString("name")
        )
        assertEquals(
            "theirs",
            SnapshotMerge.merge(snapshot(), snapshot(plans = listOf("theirs")))
                .getJSONArray("revision_plans").getJSONObject(0).getString("name")
        )
    }

    @Test
    fun `memorised ranges are united`() {
        val local = JSONObject().put("hifz_coverage", JSONArray().put(JSONObject().put("start_ayah_id", 1).put("end_ayah_id", 10)))
        val remote = JSONObject().put("hifz_coverage", JSONArray().put(JSONObject().put("start_ayah_id", 5).put("end_ayah_id", 20)))
        assertEquals(2, SnapshotMerge.merge(local, remote).getJSONArray("hifz_coverage").length())
    }

    @Test
    fun `the vault is taken whole from one side`() {
        val local = JSONObject().put("vault_folders", JSONArray()).put("similarity_groups", JSONArray()).put("similarity_members", JSONArray())
        val remote = JSONObject()
            .put("vault_folders", JSONArray().put(JSONObject().put("id", 1)))
            .put("similarity_groups", JSONArray().put(JSONObject().put("id", 4)))
            .put("similarity_members", JSONArray().put(JSONObject().put("group_id", 4)))
        val merged = SnapshotMerge.merge(local, remote)
        assertEquals(1, merged.getJSONArray("vault_folders").length())
        assertEquals(1, merged.getJSONArray("similarity_members").length())
    }
}
