package com.example.mutqan.domain.sync

import org.json.JSONArray
import org.json.JSONObject

/**
 * Combines two snapshots (the JSON of `BackupManager.buildSnapshot`) when the same account was changed on two
 * devices. What only grows is united: revision logs, exercise results and the memorised ranges. Everything else
 * (plans, saved passages and the vault) is taken from this device as a whole, because those tables point at each
 * other by id; the other side's copy is used only where this device has none.
 */
object SnapshotMerge {

    private val VAULT = listOf("vault_folders", "similarity_groups", "similarity_members")

    fun merge(local: JSONObject, remote: JSONObject): JSONObject {
        val out = JSONObject()
        for (key in local.keys()) out.put(key, local.get(key))
        for (key in remote.keys()) if (!out.has(key)) out.put(key, remote.get(key))

        out.put("revision_logs", union(local, remote, "revision_logs", ::logKey))
        out.put("exercise_results", union(local, remote, "exercise_results", ::exerciseKey))
        // Ranges are merged into the smallest set when restored, so listing both is enough.
        out.put("hifz_coverage", JSONArray().also { all ->
            for (o in sequenceOf(local, remote)) o.optJSONArray("hifz_coverage")?.let { a -> for (i in 0 until a.length()) all.put(a.get(i)) }
        })

        if (VAULT.all { local.optJSONArray(it)?.length().orEmpty() == 0 }) {
            for (k in VAULT) remote.optJSONArray(k)?.let { out.put(k, it) }
        }
        for (k in listOf("revision_plans", "saved_passages", "user_subjects", "page_strength")) {
            if (local.optJSONArray(k)?.length().orEmpty() == 0) remote.optJSONArray(k)?.let { out.put(k, it) }
        }
        return out
    }

    private fun Int?.orEmpty(): Int = this ?: 0

    /** Entries of both sides once each, local first; ids are dropped because each device numbers its own rows. */
    private fun union(local: JSONObject, remote: JSONObject, name: String, key: (JSONObject) -> String): JSONArray {
        val seen = HashSet<String>()
        val result = JSONArray()
        for (side in sequenceOf(local, remote)) {
            val array = side.optJSONArray(name) ?: continue
            for (i in 0 until array.length()) {
                val entry = array.getJSONObject(i)
                if (!seen.add(key(entry))) continue
                result.put(JSONObject(entry.toString()).also { it.remove("id") })
            }
        }
        return result
    }

    private fun logKey(o: JSONObject): String =
        listOf(
            o.optLong("timestamp"), o.optInt("start_ayah_id"), o.optInt("end_ayah_id"),
            o.optInt("outcome"), o.optInt("mistake_tags")
        ).joinToString("|")

    private fun exerciseKey(o: JSONObject): String =
        listOf(o.optLong("timestamp"), o.optInt("type"), o.optInt("start_page"), o.optInt("end_page"), o.optInt("outcome"))
            .joinToString("|")
}
