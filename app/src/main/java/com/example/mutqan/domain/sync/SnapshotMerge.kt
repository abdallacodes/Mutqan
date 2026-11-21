package com.example.mutqan.domain.sync

import com.example.mutqan.domain.ListPassage
import com.example.mutqan.domain.VerseLists
import org.json.JSONArray
import org.json.JSONObject

/**
 * Combines two snapshots (the JSON of `BackupManager.buildSnapshot`) when the same account was changed on two
 * devices. [base] is the data as the last sync left it; with it a missing entry can be told from one the other side
 * never had: an entry that is in [base] but gone from one side was deleted there, and stays deleted unless the other
 * side changed it meanwhile. Without a [base] (the first sync) the two are united.
 *
 * Every table is merged entry by entry, so an entry edited on one side is not copied twice and a table is never lost
 * whole. Entries are told apart by their content, not by row ids, because each device numbers its own rows.
 */
object SnapshotMerge {

    private const val SEP = '\u001F'

    fun merge(local: JSONObject, remote: JSONObject, base: JSONObject? = null): JSONObject {
        val out = JSONObject()
        for (key in local.keys()) out.put(key, local.get(key))
        for (key in remote.keys()) if (!out.has(key)) out.put(key, remote.get(key))

        keyed(local, remote, base, "revision_logs", ::logKey)?.let { out.put("revision_logs", it) }
        keyed(local, remote, base, "exercise_results", ::exerciseKey)?.let { out.put("exercise_results", it) }
        keyed(local, remote, base, "revision_plans") { "${it.optString("name")}|${it.optInt("unit")}" }?.let { out.put("revision_plans", it) }
        keyed(local, remote, base, "saved_passages") {
            listOf(it.optInt("kind"), it.optInt("start_ayah_id"), it.optInt("end_ayah_id"), it.optInt("word_id")).joinToString("|")
        }?.let { out.put("saved_passages", it) }
        // A verse starts one subject, so the verse is what identifies it and the title is its value: a title changed on
        // one side is taken, changed on both keeps this side's, and two titles can never end up on one verse.
        keyed(local, remote, base, "user_subjects") { it.optInt("start_ayah_id").toString() }?.let { out.put("user_subjects", it) }
        keyed(local, remote, base, "page_strength") { it.optInt("page").toString() }?.let { out.put("page_strength", it) }

        coverage(local, remote, base)?.let { out.put("hifz_coverage", it) }
        vault(local, remote, base)?.forEach { (name, array) -> out.put(name, array) }
        lists(local, remote, base)?.let { out.put("verse_lists", it) }
        settings(local, remote, base)?.let { out.put("settings", it) }
        return out
    }

    // ── What a merge changed ────────────────────────────────────────────────

    /** How many entries a merge brought in or took out of this device's data. */
    data class Report(val added: Int, val removed: Int) {
        val isEmpty get() = added == 0 && removed == 0
    }

    private val countedTables = listOf("revision_logs", "exercise_results", "revision_plans", "saved_passages", "user_subjects", "page_strength")

    /**
     * What [after] has that [before] did not, and the other way round, over the tables whose entries stand on their own
     * (not the vault, whose rows are renumbered by every merge, nor the memorised ranges). An entry whose value changed
     * counts once as removed and once as added.
     */
    fun report(before: JSONObject, after: JSONObject): Report {
        var added = 0
        var removed = 0
        for (name in countedTables) {
            val b = canons(before, name)
            val a = canons(after, name)
            added += a.count { it !in b }
            removed += b.count { it !in a }
        }
        val bl = listCanons(before)
        val al = listCanons(after)
        added += al.count { it !in bl }
        removed += bl.count { it !in al }
        return Report(added, removed)
    }

    private fun canons(side: JSONObject, name: String): Set<String> {
        val array = side.optJSONArray(name) ?: return emptySet()
        return (0 until array.length()).mapTo(HashSet()) { canon(array.getJSONObject(it)) }
    }

    // ── Entries ─────────────────────────────────────────────────────────────

    private fun entries(side: JSONObject?, name: String, key: (JSONObject) -> String): Map<String, JSONObject>? {
        val array = side?.optJSONArray(name) ?: return null
        val map = LinkedHashMap<String, JSONObject>()
        for (i in 0 until array.length()) {
            val entry = array.getJSONObject(i)
            map.putIfAbsent(key(entry), entry)
        }
        return map
    }

    private fun keyed(local: JSONObject, remote: JSONObject, base: JSONObject?, name: String, key: (JSONObject) -> String): JSONArray? {
        val l = entries(local, name, key)
        val r = entries(remote, name, key)
        if (l == null && r == null) return null
        // A side without the table at all (written by an older version) has not deleted anything.
        val was = if (l == null || r == null) null else entries(base, name, key)
        return JSONArray().also { out ->
            for (entry in pick(l.orEmpty(), r.orEmpty(), was)) out.put(withoutId(entry))
        }
    }

    /**
     * The entries both sides agree should exist, this device's order first. Where both have one, this device's copy wins
     * unless only the other side changed it since [base].
     */
    private fun pick(l: Map<String, JSONObject>, r: Map<String, JSONObject>, b: Map<String, JSONObject>?): List<JSONObject> {
        val result = ArrayList<JSONObject>()
        for (k in LinkedHashSet<String>().apply { addAll(l.keys); addAll(r.keys) }) {
            val mine = l[k]
            val theirs = r[k]
            val was = b?.get(k)
            val chosen = when {
                mine != null && theirs != null -> if (was != null && same(mine, was)) theirs else mine
                // Gone from one side: deleted there if the other side still has it as it was, else it was edited there.
                mine != null -> if (was != null && same(mine, was)) null else mine
                theirs != null -> if (was != null && same(theirs, was)) null else theirs
                else -> null
            }
            if (chosen != null) result.add(chosen)
        }
        return result
    }

    private fun Map<String, JSONObject>?.orEmpty(): Map<String, JSONObject> = this ?: emptyMap()

    private fun withoutId(entry: JSONObject) = JSONObject(entry.toString()).also { it.remove("id") }

    private fun same(a: JSONObject, b: JSONObject) = canon(a) == canon(b)

    private fun canon(o: JSONObject): String =
        o.keys().asSequence().filter { it != "id" }.sorted().joinToString(",") { "$it=${o.opt(it)}" }

    private fun logKey(o: JSONObject): String =
        listOf(
            o.optLong("timestamp"), o.optInt("start_ayah_id"), o.optInt("end_ayah_id"),
            o.optInt("outcome"), o.optInt("mistake_tags")
        ).joinToString("|")

    private fun exerciseKey(o: JSONObject): String =
        listOf(o.optLong("timestamp"), o.optInt("type"), o.optInt("start_page"), o.optInt("end_page"), o.optInt("outcome"))
            .joinToString("|")

    // ── Memorised ranges ────────────────────────────────────────────────────

    /** The memorised verses are a set, so each verse is kept if a side has it and did not just lose it. */
    private fun coverage(local: JSONObject, remote: JSONObject, base: JSONObject?): JSONArray? {
        val sides = listOf(local, remote, base).map { it?.optJSONArray("hifz_coverage") }
        if (sides[0] == null && sides[1] == null) return null
        val size = sides.maxOf { a -> (0 until (a?.length() ?: 0)).maxOfOrNull { a!!.getJSONObject(it).optInt("end_ayah_id") } ?: 0 } + 2
        fun marks(a: JSONArray?): BooleanArray? {
            a ?: return null
            val m = BooleanArray(size)
            for (i in 0 until a.length()) {
                val e = a.getJSONObject(i)
                for (id in e.optInt("start_ayah_id").coerceAtLeast(0)..e.optInt("end_ayah_id").coerceAtMost(size - 1)) m[id] = true
            }
            return m
        }
        val l = marks(sides[0]); val r = marks(sides[1])
        val b = if (l == null || r == null) null else marks(sides[2])
        val keep = BooleanArray(size) { id ->
            val inL = l?.get(id) == true
            val inR = r?.get(id) == true
            val inB = b?.get(id) == true
            (inL && inR) || (inL && !inR && !inB) || (!inL && inR && !inB)
        }
        return JSONArray().also { out ->
            var id = 0
            while (id < size) {
                if (!keep[id]) { id++; continue }
                val start = id
                while (id < size && keep[id]) id++
                out.put(JSONObject().put("start_ayah_id", start).put("end_ayah_id", id - 1))
            }
        }
    }

    // ── Vault ───────────────────────────────────────────────────────────────

    /**
     * Folders, similarity groups and their members point at each other by row id, so they are compared by what they
     * are: a folder by its path of names, a group by its description and verses (with the folder it sits in as a path),
     * then numbered afresh.
     */
    private fun vault(local: JSONObject, remote: JSONObject, base: JSONObject?): Map<String, JSONArray>? {
        val names = listOf("vault_folders", "similarity_groups", "similarity_members")
        if (listOf(local, remote).all { side -> names.all { side.optJSONArray(it) == null } }) return null

        val l = VaultSide.of(local); val r = VaultSide.of(remote)
        val bothHave = listOf(local, remote).all { side -> names.any { side.optJSONArray(it) != null } }
        val b = base?.takeIf { bothHave && names.any { name -> it.optJSONArray(name) != null } }?.let { VaultSide.of(it) }

        val folders = pick(l.folders, r.folders, b?.folders)
        val groups = pick(l.groups, r.groups, b?.groups)

        // A folder a kept group sits in must exist, with the folders above it.
        val paths = sortedSetOf(compareBy<String>({ it.count { c -> c == SEP } }, { it }))
        val timestamps = HashMap<String, Long>()
        fun add(path: String) {
            var p = path
            while (p.isNotEmpty()) {
                paths.add(p)
                p = p.substringBeforeLast(SEP, "")
            }
        }
        for (f in folders) { add(f.getString("path")); timestamps[f.getString("path")] = f.optLong("timestamp") }
        for (g in groups) add(g.getString("folder"))

        val folderIds = HashMap<String, Int>()
        val folderArray = JSONArray()
        for (p in paths) {
            val id = folderIds.size + 1
            folderIds[p] = id
            val parent = p.substringBeforeLast(SEP, "")
            folderArray.put(JSONObject().apply {
                put("id", id)
                put("name", p.substringAfterLast(SEP))
                if (parent.isNotEmpty()) put("parent_id", folderIds.getValue(parent))
                put("timestamp", timestamps[p] ?: 0L)
            })
        }

        val groupArray = JSONArray()
        val memberArray = JSONArray()
        groups.forEachIndexed { i, g ->
            val id = i + 1
            groupArray.put(JSONObject().apply {
                put("id", id)
                put("description", g.optString("description"))
                put("master_quality", g.opt("master_quality") ?: 0.5)
                put("memorization_notes", g.optString("memorization_notes"))
                g.optString("folder").takeIf { it.isNotEmpty() }?.let { put("folder_id", folderIds.getValue(it)) }
            })
            val verses = g.getJSONArray("verses")
            for (v in 0 until verses.length()) memberArray.put(JSONObject().put("group_id", id).put("verse_id", verses.getInt(v)))
        }
        return mapOf("vault_folders" to folderArray, "similarity_groups" to groupArray, "similarity_members" to memberArray)
    }

    private class VaultSide(val folders: Map<String, JSONObject>, val groups: Map<String, JSONObject>) {
        companion object {
            fun of(snapshot: JSONObject): VaultSide {
                val rows = snapshot.optJSONArray("vault_folders")
                val byId = HashMap<Int, JSONObject>()
                for (i in 0 until (rows?.length() ?: 0)) rows!!.getJSONObject(i).let { byId[it.optInt("id")] = it }

                val pathOf = HashMap<Int, String>()
                fun path(id: Int): String {
                    pathOf[id]?.let { return it }
                    val names = ArrayList<String>()
                    var at: Int? = id
                    val seen = HashSet<Int>()
                    while (at != null && seen.add(at)) {
                        val row = byId[at] ?: break
                        names.add(0, row.optString("name"))
                        at = if (row.isNull("parent_id") || !row.has("parent_id")) null else row.optInt("parent_id")
                    }
                    return names.joinToString(SEP.toString()).also { pathOf[id] = it }
                }

                val folders = LinkedHashMap<String, JSONObject>()
                for (id in byId.keys.sorted()) {
                    val p = path(id)
                    folders.putIfAbsent(p, JSONObject().put("path", p).put("timestamp", byId.getValue(id).optLong("timestamp")))
                }

                val verses = HashMap<Int, MutableSet<Int>>()
                snapshot.optJSONArray("similarity_members")?.let { a ->
                    for (i in 0 until a.length()) a.getJSONObject(i).let { verses.getOrPut(it.optInt("group_id")) { sortedSetOf<Int>() }.add(it.optInt("verse_id")) }
                }
                val groups = LinkedHashMap<String, JSONObject>()
                snapshot.optJSONArray("similarity_groups")?.let { a ->
                    for (i in 0 until a.length()) {
                        val g = a.getJSONObject(i)
                        val members = verses[g.optInt("id")].orEmpty().sorted()
                        val folder = if (g.isNull("folder_id") || !g.has("folder_id")) "" else path(g.optInt("folder_id"))
                        val item = JSONObject()
                            .put("description", g.optString("description"))
                            .put("master_quality", g.opt("master_quality") ?: 0.5)
                            .put("memorization_notes", g.optString("memorization_notes"))
                            .put("folder", folder)
                            .put("verses", JSONArray(members))
                        groups.putIfAbsent(g.optString("description") + SEP + members.joinToString(","), item)
                    }
                }
                return VaultSide(folders, groups)
            }
        }
    }

    // ── Lists of verses ─────────────────────────────────────────────────────

    /**
     * The user's lists carry their passages inside them. A list is told apart by its name ([VerseLists.nameKey]) and
     * a passage by its list and verses, so a passage added on each device stays. A list deleted on one side while
     * the other added to it stays, with what was added. Passages that now overlap become one.
     */
    private fun lists(local: JSONObject, remote: JSONObject, base: JSONObject?): JSONArray? {
        val l = ListSide.of(local)
        val r = ListSide.of(remote)
        if (l == null && r == null) return null
        // A side without lists at all (written by an older version) has not deleted any.
        val b = if (l == null || r == null) null else ListSide.of(base)

        val lists = pick(l?.lists.orEmpty(), r?.lists.orEmpty(), b?.lists)
        val items = pick(l?.items.orEmpty(), r?.items.orEmpty(), b?.items)
        val itemsOf = items.groupBy { it.getString("list") }

        val keys = LinkedHashSet<String>()
        val chosen = HashMap<String, JSONObject>()
        for (list in lists) {
            val key = VerseLists.nameKey(list.getString("name"))
            keys.add(key)
            chosen[key] = list
        }
        keys.addAll(itemsOf.keys)

        return JSONArray().also { out ->
            for (key in keys) {
                val list = chosen[key] ?: l?.lists?.get(key) ?: r?.lists?.get(key) ?: continue
                val passages = VerseLists.tidy(itemsOf[key].orEmpty().map {
                    ListPassage(it.getInt("start_ayah_id"), it.getInt("end_ayah_id"), it.optString("note"), it.optLong("created_at"))
                })
                out.put(JSONObject().apply {
                    put("name", list.getString("name"))
                    put("note", list.optString("note"))
                    put("created_at", list.optLong("created_at"))
                    put("updated_at", maxOf(l?.updated?.get(key) ?: 0L, r?.updated?.get(key) ?: 0L))
                    put("items", JSONArray().apply {
                        for (p in passages) {
                            put(JSONObject().put("start_ayah_id", p.start).put("end_ayah_id", p.end).put("note", p.note).put("created_at", p.createdAt))
                        }
                    })
                })
            }
        }
    }

    /** The lists and passages of a snapshot as entries of their own, for [report]. */
    private fun listCanons(side: JSONObject): Set<String> {
        val s = ListSide.of(side) ?: return emptySet()
        return s.lists.values.mapTo(HashSet()) { "list" + SEP + canon(it) }.apply { s.items.values.forEach { add("item" + SEP + canon(it)) } }
    }

    /**
     * One side's lists by name, and their passages by list name and verses. The time a list was last changed is kept
     * apart: it moves with every passage, so comparing it would make one passage added look like a changed list.
     */
    private class ListSide(val lists: Map<String, JSONObject>, val items: Map<String, JSONObject>, val updated: Map<String, Long>) {
        companion object {
            fun of(snapshot: JSONObject?): ListSide? {
                val array = snapshot?.optJSONArray("verse_lists") ?: return null
                val lists = LinkedHashMap<String, JSONObject>()
                val items = LinkedHashMap<String, JSONObject>()
                val updated = HashMap<String, Long>()
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    val key = VerseLists.nameKey(o.optString("name"))
                    if (key.isEmpty()) continue
                    lists.putIfAbsent(
                        key,
                        JSONObject().put("name", o.optString("name")).put("note", o.optString("note")).put("created_at", o.optLong("created_at"))
                    )
                    updated[key] = maxOf(updated[key] ?: 0L, o.optLong("updated_at"))
                    val passages = o.optJSONArray("items") ?: continue
                    for (j in 0 until passages.length()) {
                        val p = passages.getJSONObject(j)
                        val start = p.optInt("start_ayah_id")
                        val end = p.optInt("end_ayah_id")
                        items.putIfAbsent(
                            key + SEP + start + SEP + end,
                            JSONObject().put("list", key).put("start_ayah_id", start).put("end_ayah_id", end)
                                .put("note", p.optString("note")).put("created_at", p.optLong("created_at"))
                        )
                    }
                }
                return ListSide(lists, items, updated)
            }
        }
    }

    // ── Settings ────────────────────────────────────────────────────────────

    /** One value at a time: the side that changed it since [base] wins; if both did, this device. */
    private fun settings(local: JSONObject, remote: JSONObject, base: JSONObject?): JSONObject? {
        val l = local.optJSONObject("settings")
        val r = remote.optJSONObject("settings")
        if (l == null && r == null) return null
        val b = base?.optJSONObject("settings")
        val out = JSONObject()
        val names = LinkedHashSet<String>().apply { l?.keys()?.forEach { add(it) }; r?.keys()?.forEach { add(it) } }
        for (name in names) {
            val mine = l?.opt(name)
            val theirs = r?.opt(name)
            val was = b?.opt(name)
            out.put(
                name,
                when {
                    mine == null -> theirs
                    theirs == null -> mine
                    mine.toString() == theirs.toString() -> mine
                    was != null && mine.toString() == was.toString() -> theirs
                    else -> mine
                }
            )
        }
        return out
    }
}
