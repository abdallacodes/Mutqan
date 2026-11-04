package com.example.mutqan.domain.sync

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotMergeTest {

    private fun log(ts: Long, id: Int, outcome: Int = 2) = JSONObject()
        .put("id", id).put("timestamp", ts).put("start_ayah_id", 1).put("end_ayah_id", 7)
        .put("outcome", outcome).put("mistake_tags", 0)

    private fun snapshot(logs: List<JSONObject> = emptyList(), plans: List<String> = emptyList()) = JSONObject()
        .put("revision_logs", JSONArray(logs))
        .put("revision_plans", JSONArray(plans.map { JSONObject().put("name", it) }))

    private fun names(array: JSONArray, field: String) = (0 until array.length()).map { array.getJSONObject(it).getString(field) }
    private fun stamps(array: JSONArray) = (0 until array.length()).map { array.getJSONObject(it).getLong("timestamp") }

    // ── Revision logs ───────────────────────────────────────────────────────

    @Test
    fun `logs from both devices are kept once, without ids`() {
        val local = snapshot(listOf(log(100, 1), log(200, 2)))
        val remote = snapshot(listOf(log(200, 9), log(300, 4)))
        val logs = SnapshotMerge.merge(local, remote).getJSONArray("revision_logs")
        assertEquals(listOf(100L, 200L, 300L), stamps(logs))
        assertFalse((0 until logs.length()).any { logs.getJSONObject(it).has("id") })
    }

    @Test
    fun `the same moment with a different outcome is a different log`() {
        val merged = SnapshotMerge.merge(snapshot(listOf(log(100, 1, outcome = 2))), snapshot(listOf(log(100, 1, outcome = 0))))
        assertEquals(2, merged.getJSONArray("revision_logs").length())
    }

    @Test
    fun `a log the other side deleted is gone when this side left it alone`() {
        val base = snapshot(listOf(log(1, 1), log(2, 2), log(3, 3)))
        val local = snapshot(listOf(log(1, 1), log(2, 2), log(3, 3), log(4, 4)))
        val remote = snapshot(listOf(log(1, 1), log(3, 3)))
        assertEquals(listOf(1L, 3L, 4L), stamps(SnapshotMerge.merge(local, remote, base).getJSONArray("revision_logs")))
    }

    @Test
    fun `a log this side deleted stays deleted`() {
        val base = snapshot(listOf(log(1, 1), log(2, 2)))
        val local = snapshot(listOf(log(1, 1)))
        val remote = snapshot(listOf(log(1, 1), log(2, 2), log(3, 3)))
        assertEquals(listOf(1L, 3L), stamps(SnapshotMerge.merge(local, remote, base).getJSONArray("revision_logs")))
    }

    @Test
    fun `a log new on one side is kept even with a base`() {
        val base = snapshot(listOf(log(1, 1)))
        val merged = SnapshotMerge.merge(snapshot(listOf(log(1, 1), log(2, 2))), snapshot(listOf(log(1, 1), log(3, 3))), base)
        assertEquals(listOf(1L, 2L, 3L), stamps(merged.getJSONArray("revision_logs")))
    }

    @Test
    fun `without a base nothing counts as deleted`() {
        val merged = SnapshotMerge.merge(snapshot(listOf(log(1, 1))), snapshot(listOf(log(2, 2))))
        assertEquals(listOf(1L, 2L), stamps(merged.getJSONArray("revision_logs")))
    }

    @Test
    fun `a side without the table is not taken as having deleted everything`() {
        val base = snapshot(listOf(log(1, 1), log(2, 2)))
        val remote = JSONObject().put("type", "full_backup")
        val merged = SnapshotMerge.merge(snapshot(listOf(log(1, 1), log(2, 2))), remote, base)
        assertEquals(listOf(1L, 2L), stamps(merged.getJSONArray("revision_logs")))
        val other = SnapshotMerge.merge(remote, snapshot(listOf(log(1, 1), log(2, 2))), base)
        assertEquals(listOf(1L, 2L), stamps(other.getJSONArray("revision_logs")))
    }

    @Test
    fun `identical logs inside one list collapse to one`() {
        val merged = SnapshotMerge.merge(snapshot(listOf(log(1, 1), log(1, 2))), snapshot())
        assertEquals(1, merged.getJSONArray("revision_logs").length())
    }

    @Test
    fun `merging twice gives the same result`() {
        val base = snapshot(listOf(log(1, 1), log(2, 2)))
        val local = snapshot(listOf(log(1, 1), log(4, 4)))
        val remote = snapshot(listOf(log(1, 1), log(2, 2), log(3, 3)))
        val once = SnapshotMerge.merge(local, remote, base)
        val twice = SnapshotMerge.merge(once, remote, base)
        assertEquals(stamps(once.getJSONArray("revision_logs")).toSet(), stamps(twice.getJSONArray("revision_logs")).toSet())
    }

    @Test
    fun `exercise results are kept like logs`() {
        fun results(vararg ts: Long) = JSONObject().put("exercise_results", JSONArray(ts.map {
            JSONObject().put("timestamp", it).put("type", 1).put("start_page", 1).put("end_page", 1).put("outcome", 1)
        }))
        val merged = SnapshotMerge.merge(results(1, 2), results(2, 3), results(1, 2))
        assertEquals(listOf(2L, 3L), stamps(merged.getJSONArray("exercise_results")))
    }

    @Test
    fun `keys the merge does not know are kept, this side's first`() {
        val local = snapshot().put("type", "full_backup").put("extra", 1)
        val remote = snapshot().put("version", 5).put("extra", 2)
        val merged = SnapshotMerge.merge(local, remote)
        assertEquals("full_backup", merged.getString("type"))
        assertEquals(5, merged.getInt("version"))
        assertEquals(1, merged.getInt("extra"))
    }

    // ── Plans, saved passages, subjects, page levels ────────────────────────

    @Test
    fun `plans of both sides are kept, this device's first, and one with the same name once`() {
        val both = SnapshotMerge.merge(snapshot(plans = listOf("mine", "same")), snapshot(plans = listOf("theirs", "same")))
            .getJSONArray("revision_plans")
        assertEquals(listOf("mine", "same", "theirs"), names(both, "name"))
        assertEquals(
            "theirs",
            SnapshotMerge.merge(snapshot(), snapshot(plans = listOf("theirs"))).getJSONArray("revision_plans").getJSONObject(0).getString("name")
        )
    }

    @Test
    fun `a plan edited only on the other side is taken from there`() {
        fun plan(amount: Int) = JSONObject().put("revision_plans", JSONArray().put(JSONObject().put("name", "p").put("unit", 0).put("amount_per_day", amount)))
        val merged = SnapshotMerge.merge(plan(3), plan(5), plan(3))
        assertEquals(5, merged.getJSONArray("revision_plans").getJSONObject(0).getInt("amount_per_day"))
    }

    @Test
    fun `a plan edited on both sides keeps this side's`() {
        fun plan(amount: Int) = JSONObject().put("revision_plans", JSONArray().put(JSONObject().put("name", "p").put("unit", 0).put("amount_per_day", amount)))
        val merged = SnapshotMerge.merge(plan(7), plan(5), plan(3))
        assertEquals(7, merged.getJSONArray("revision_plans").getJSONObject(0).getInt("amount_per_day"))
    }

    @Test
    fun `saved passages subjects and page levels of both sides are kept`() {
        fun side(passage: Int, subject: String, page: Int, level: Int) = JSONObject()
            .put("saved_passages", JSONArray().put(JSONObject().put("kind", 1).put("start_ayah_id", passage).put("end_ayah_id", passage).put("id", 5)))
            .put("user_subjects", JSONArray().put(JSONObject().put("subject_text", subject).put("start_ayah_id", 1)))
            .put("page_strength", JSONArray().put(JSONObject().put("page", page).put("level", level)))
        val merged = SnapshotMerge.merge(side(10, "a", 3, 1), side(20, "b", 3, 2))
        assertEquals(2, merged.getJSONArray("saved_passages").length())
        assertEquals(2, merged.getJSONArray("user_subjects").length())
        assertEquals(1, merged.getJSONArray("page_strength").length())
        assertEquals(1, merged.getJSONArray("page_strength").getJSONObject(0).getInt("level"))
        assertFalse(merged.getJSONArray("saved_passages").getJSONObject(0).has("id"))
    }

    @Test
    fun `a saved passage the other side resolved is taken as resolved`() {
        fun passage(resolvedAt: Long) = JSONObject().put(
            "saved_passages",
            JSONArray().put(JSONObject().put("kind", 1).put("start_ayah_id", 4).put("end_ayah_id", 6).put("word_id", 0).put("resolved_at", resolvedAt))
        )
        val merged = SnapshotMerge.merge(passage(0), passage(99), passage(0))
        assertEquals(99L, merged.getJSONArray("saved_passages").getJSONObject(0).getLong("resolved_at"))
    }

    @Test
    fun `a page level the other side changed is taken when this side left it`() {
        fun level(n: Int) = JSONObject().put("page_strength", JSONArray().put(JSONObject().put("page", 3).put("level", n)))
        assertEquals(2, SnapshotMerge.merge(level(1), level(2), level(1)).getJSONArray("page_strength").getJSONObject(0).getInt("level"))
        assertEquals(3, SnapshotMerge.merge(level(3), level(2), level(1)).getJSONArray("page_strength").getJSONObject(0).getInt("level"))
    }

    @Test
    fun `a subject the other side removed is removed`() {
        fun subjects(vararg texts: String) = JSONObject().put("user_subjects", JSONArray(texts.map { JSONObject().put("subject_text", it).put("start_ayah_id", 1) }))
        val merged = SnapshotMerge.merge(subjects("a", "b"), subjects("a"), subjects("a", "b"))
        assertEquals(listOf("a"), names(merged.getJSONArray("user_subjects"), "subject_text"))
    }

    // ── Memorised ranges ────────────────────────────────────────────────────

    private fun coverage(vararg ranges: IntRange) = JSONObject().put("hifz_coverage", JSONArray(ranges.map {
        JSONObject().put("start_ayah_id", it.first).put("end_ayah_id", it.last)
    }))

    private fun verses(merged: JSONObject): Set<Int> = merged.getJSONArray("hifz_coverage").let { a ->
        (0 until a.length()).flatMap { a.getJSONObject(it).let { r -> r.getInt("start_ayah_id")..r.getInt("end_ayah_id") } }.toSet()
    }

    @Test
    fun `memorised ranges are united`() {
        val merged = SnapshotMerge.merge(coverage(1..10), coverage(5..20))
        assertEquals((1..20).toSet(), verses(merged))
        assertEquals(1, merged.getJSONArray("hifz_coverage").length())
    }

    @Test
    fun `ranges stay apart when there is a gap`() {
        assertEquals(2, SnapshotMerge.merge(coverage(1..10), coverage(12..20)).getJSONArray("hifz_coverage").length())
    }

    @Test
    fun `verses the other side stopped memorising are removed`() {
        val merged = SnapshotMerge.merge(coverage(1..20), coverage(1..5, 11..20), coverage(1..20))
        assertEquals((1..5).toSet() + (11..20).toSet(), verses(merged))
    }

    @Test
    fun `verses this side stopped memorising stay removed`() {
        val merged = SnapshotMerge.merge(coverage(1..5), coverage(1..20, 30..35), coverage(1..20))
        assertEquals((1..5).toSet() + (30..35).toSet(), verses(merged))
    }

    @Test
    fun `verses removed on both sides stay removed`() {
        val merged = SnapshotMerge.merge(coverage(1..5), coverage(1..5), coverage(1..10))
        assertEquals((1..5).toSet(), verses(merged))
    }

    @Test
    fun `a range cut in the middle on one side and extended on the other`() {
        val merged = SnapshotMerge.merge(coverage(1..4, 7..10), coverage(1..14), coverage(1..10))
        assertEquals((1..4).toSet() + (7..14).toSet(), verses(merged))
    }

    @Test
    fun `nothing memorised anywhere gives no ranges`() {
        assertEquals(0, SnapshotMerge.merge(coverage(), coverage(), coverage(1..3)).getJSONArray("hifz_coverage").length())
    }

    @Test
    fun `a side without ranges loses nothing`() {
        val merged = SnapshotMerge.merge(coverage(1..5), JSONObject(), coverage(1..5))
        assertEquals((1..5).toSet(), verses(merged))
    }

    // ── The vault ───────────────────────────────────────────────────────────

    private fun folder(id: Int, name: String, parent: Int? = null) =
        JSONObject().put("id", id).put("name", name).put("timestamp", 1L).also { if (parent != null) it.put("parent_id", parent) }

    private fun group(id: Int, description: String, folder: Int? = null) =
        JSONObject().put("id", id).put("description", description).put("master_quality", 0.5).put("memorization_notes", "")
            .also { if (folder != null) it.put("folder_id", folder) }

    private fun member(group: Int, verse: Int) = JSONObject().put("group_id", group).put("verse_id", verse)

    private fun vault(folders: List<JSONObject> = emptyList(), groups: List<JSONObject> = emptyList(), members: List<JSONObject> = emptyList()) =
        JSONObject()
            .put("vault_folders", JSONArray(folders))
            .put("similarity_groups", JSONArray(groups))
            .put("similarity_members", JSONArray(members))

    /** "folder/path:description[verses]" for each group, whatever the row ids are. */
    private fun view(snapshot: JSONObject): Set<String> {
        val folders = snapshot.getJSONArray("vault_folders")
        val byId = (0 until folders.length()).associate { folders.getJSONObject(it).getInt("id") to folders.getJSONObject(it) }
        fun path(id: Int): String {
            val f = byId.getValue(id)
            return (if (f.has("parent_id")) path(f.getInt("parent_id")) + "/" else "") + f.getString("name")
        }
        val members = snapshot.getJSONArray("similarity_members")
        val groups = snapshot.getJSONArray("similarity_groups")
        return (0 until groups.length()).map {
            val g = groups.getJSONObject(it)
            val verses = (0 until members.length()).map { m -> members.getJSONObject(m) }
                .filter { m -> m.getInt("group_id") == g.getInt("id") }.map { m -> m.getInt("verse_id") }.sorted()
            (if (g.has("folder_id")) path(g.getInt("folder_id")) else "") + ":" + g.getString("description") + verses
        }.toSet()
    }

    /** Every folder and group a row points at exists, and no id is used twice. */
    private fun assertConsistent(snapshot: JSONObject) {
        val folders = snapshot.getJSONArray("vault_folders")
        val groups = snapshot.getJSONArray("similarity_groups")
        val members = snapshot.getJSONArray("similarity_members")
        val folderIds = (0 until folders.length()).map { folders.getJSONObject(it).getInt("id") }
        val groupIds = (0 until groups.length()).map { groups.getJSONObject(it).getInt("id") }
        assertEquals(folderIds.size, folderIds.toSet().size)
        assertEquals(groupIds.size, groupIds.toSet().size)
        for (i in 0 until folders.length()) folders.getJSONObject(i).let { if (it.has("parent_id")) assertTrue(it.getInt("parent_id") in folderIds) }
        for (i in 0 until groups.length()) groups.getJSONObject(i).let { if (it.has("folder_id")) assertTrue(it.getInt("folder_id") in folderIds) }
        for (i in 0 until members.length()) assertTrue(members.getJSONObject(i).getInt("group_id") in groupIds)
    }

    @Test
    fun `the vault of both sides is united even when both have one`() {
        val local = vault(listOf(folder(1, "Hard")), listOf(group(1, "A", 1)), listOf(member(1, 10), member(1, 11)))
        val remote = vault(listOf(folder(7, "Easy")), listOf(group(3, "B", 7)), listOf(member(3, 20), member(3, 21)))
        val merged = SnapshotMerge.merge(local, remote)
        assertEquals(setOf("Hard:A[10, 11]", "Easy:B[20, 21]"), view(merged))
        assertConsistent(merged)
    }

    @Test
    fun `the vault is taken whole from the side that has one`() {
        val remote = vault(listOf(folder(1, "x")), listOf(group(4, "g", 1)), listOf(member(4, 9)))
        val merged = SnapshotMerge.merge(vault(), remote)
        assertEquals(1, merged.getJSONArray("vault_folders").length())
        assertEquals(1, merged.getJSONArray("similarity_members").length())
        assertEquals(setOf("x:g[9]"), view(merged))
    }

    @Test
    fun `the same group with different row ids on each side is one group`() {
        val local = vault(listOf(folder(1, "F")), listOf(group(1, "A", 1)), listOf(member(1, 5)))
        val remote = vault(listOf(folder(9, "F")), listOf(group(8, "A", 9)), listOf(member(8, 5)))
        val merged = SnapshotMerge.merge(local, remote)
        assertEquals(setOf("F:A[5]"), view(merged))
        assertEquals(1, merged.getJSONArray("vault_folders").length())
        assertConsistent(merged)
    }

    @Test
    fun `nested folders keep their place`() {
        val local = vault(listOf(folder(1, "A"), folder(2, "B", 1)), listOf(group(1, "g", 2)), listOf(member(1, 3)))
        val merged = SnapshotMerge.merge(local, vault())
        assertEquals(setOf("A/B:g[3]"), view(merged))
        assertEquals(2, merged.getJSONArray("vault_folders").length())
        assertConsistent(merged)
    }

    @Test
    fun `a group on the top level has no folder`() {
        val merged = SnapshotMerge.merge(vault(groups = listOf(group(1, "g")), members = listOf(member(1, 3))), vault())
        assertEquals(setOf(":g[3]"), view(merged))
        assertFalse(merged.getJSONArray("similarity_groups").getJSONObject(0).has("folder_id"))
    }

    @Test
    fun `a group the other side deleted is gone`() {
        val base = vault(listOf(folder(1, "F")), listOf(group(1, "A", 1), group(2, "B", 1)), listOf(member(1, 5), member(2, 6)))
        val local = base
        val remote = vault(listOf(folder(1, "F")), listOf(group(1, "A", 1)), listOf(member(1, 5)))
        val merged = SnapshotMerge.merge(local, remote, base)
        assertEquals(setOf("F:A[5]"), view(merged))
        assertConsistent(merged)
    }

    @Test
    fun `a group added on one side and one deleted on the other both count`() {
        val base = vault(listOf(folder(1, "F")), listOf(group(1, "A", 1), group(2, "B", 1)), listOf(member(1, 5), member(2, 6)))
        val local = vault(listOf(folder(1, "F")), listOf(group(1, "A", 1), group(2, "B", 1), group(3, "C", 1)), listOf(member(1, 5), member(2, 6), member(3, 7)))
        val remote = vault(listOf(folder(1, "F")), listOf(group(2, "B", 1)), listOf(member(2, 6)))
        val merged = SnapshotMerge.merge(local, remote, base)
        assertEquals(setOf("F:B[6]", "F:C[7]"), view(merged))
        assertConsistent(merged)
    }

    @Test
    fun `a group with a verse added on one side replaces its old self`() {
        val base = vault(listOf(), listOf(group(1, "A")), listOf(member(1, 5)))
        val local = vault(listOf(), listOf(group(1, "A")), listOf(member(1, 5)))
        val remote = vault(listOf(), listOf(group(1, "A")), listOf(member(1, 5), member(1, 6)))
        assertEquals(setOf(":A[5, 6]"), view(SnapshotMerge.merge(local, remote, base)))
    }

    @Test
    fun `a folder deleted on one side is brought back when a group still sits in it`() {
        val base = vault(listOf(folder(1, "F")), listOf(group(1, "A", 1)), listOf(member(1, 5)))
        val local = vault(listOf(folder(1, "F")), listOf(group(1, "A", 1), group(2, "B", 1)), listOf(member(1, 5), member(2, 6)))
        val remote = vault(listOf(), listOf(), listOf())
        val merged = SnapshotMerge.merge(local, remote, base)
        assertEquals(setOf("F:B[6]"), view(merged))
        assertConsistent(merged)
    }

    @Test
    fun `an empty folder survives when nobody deleted it`() {
        val merged = SnapshotMerge.merge(vault(folders = listOf(folder(1, "Empty"))), vault())
        assertEquals(1, merged.getJSONArray("vault_folders").length())
    }

    @Test
    fun `folders with a missing parent do not break the merge`() {
        val local = vault(listOf(folder(2, "Orphan", 99)), listOf(group(1, "g", 2)), listOf(member(1, 3)))
        val merged = SnapshotMerge.merge(local, vault())
        assertConsistent(merged)
        assertEquals(1, merged.getJSONArray("similarity_groups").length())
    }

    @Test
    fun `a loop of parents does not hang`() {
        val local = vault(listOf(folder(1, "A", 2), folder(2, "B", 1)), listOf(group(1, "g", 1)), listOf(member(1, 3)))
        val merged = SnapshotMerge.merge(local, vault())
        assertConsistent(merged)
    }

    @Test
    fun `members of a group are not repeated`() {
        val local = vault(groups = listOf(group(1, "g")), members = listOf(member(1, 3), member(1, 3), member(1, 4)))
        assertEquals(setOf(":g[3, 4]"), view(SnapshotMerge.merge(local, vault())))
    }

    @Test
    fun `merging the vault twice gives the same groups`() {
        val base = vault(listOf(folder(1, "F")), listOf(group(1, "A", 1)), listOf(member(1, 5)))
        val local = vault(listOf(folder(1, "F")), listOf(group(1, "A", 1), group(2, "B", 1)), listOf(member(1, 5), member(2, 6)))
        val remote = vault(listOf(folder(1, "F"), folder(2, "G")), listOf(group(1, "A", 1), group(2, "C", 2)), listOf(member(1, 5), member(2, 7)))
        val once = SnapshotMerge.merge(local, remote, base)
        val twice = SnapshotMerge.merge(once, remote, base)
        assertEquals(view(once), view(twice))
        assertEquals(setOf("F:A[5]", "F:B[6]", "G:C[7]"), view(once))
        assertConsistent(twice)
    }

    @Test
    fun `no vault anywhere adds none`() {
        val merged = SnapshotMerge.merge(snapshot(), snapshot())
        assertFalse(merged.has("vault_folders"))
    }

    // ── Settings ────────────────────────────────────────────────────────────

    private fun settings(vararg pairs: Pair<String, Any>) = JSONObject().put("settings", JSONObject().also { o -> pairs.forEach { (k, v) -> o.put(k, v) } })

    @Test
    fun `a setting only one side has is kept`() {
        val merged = SnapshotMerge.merge(settings("a" to 1), settings("b" to "x")).getJSONObject("settings")
        assertEquals(1, merged.getInt("a"))
        assertEquals("x", merged.getString("b"))
    }

    @Test
    fun `a setting the other side changed is taken when this side left it`() {
        val merged = SnapshotMerge.merge(settings("a" to 1), settings("a" to 2), settings("a" to 1)).getJSONObject("settings")
        assertEquals(2, merged.getInt("a"))
    }

    @Test
    fun `a setting this side changed is kept`() {
        val merged = SnapshotMerge.merge(settings("a" to 3), settings("a" to 1), settings("a" to 1)).getJSONObject("settings")
        assertEquals(3, merged.getInt("a"))
    }

    @Test
    fun `a setting changed on both sides keeps this side's`() {
        val merged = SnapshotMerge.merge(settings("a" to 3), settings("a" to 2), settings("a" to 1)).getJSONObject("settings")
        assertEquals(3, merged.getInt("a"))
    }

    @Test
    fun `yes and no settings merge like numbers`() {
        val merged = SnapshotMerge.merge(settings("loop" to false), settings("loop" to true), settings("loop" to false)).getJSONObject("settings")
        assertTrue(merged.getBoolean("loop"))
    }

    @Test
    fun `no settings anywhere adds none`() {
        assertFalse(SnapshotMerge.merge(snapshot(), snapshot()).has("settings"))
    }

    @Test
    fun `settings from only the other side are taken`() {
        val merged = SnapshotMerge.merge(snapshot(), settings("a" to 1))
        assertEquals(1, merged.getJSONObject("settings").getInt("a"))
    }
}
