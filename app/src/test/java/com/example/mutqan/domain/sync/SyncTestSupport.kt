package com.example.mutqan.domain.sync

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** A device's data: all that [SyncLocal] needs is the text, and a way to fail like a database can. */
internal class FakeLocal(var text: String) : SyncLocal {
    var applied = 0
    var cleared = 0
    var failNextApply = false

    /** The copies kept before a merge, and whether keeping one fails. */
    val copies = mutableListOf<String>()
    var failCopy = false
    var appliedBeforeCopy = false

    override suspend fun snapshot() = text

    override suspend fun keepCopyBeforeMerge(text: String) {
        if (failCopy) throw SafetyCopyFailedException(IOException("no space"))
        appliedBeforeCopy = appliedBeforeCopy || applied > 0
        copies += text
    }

    /** Like the real one: the data goes, the settings stay. */
    override suspend fun clear() {
        cleared++
        val settings = JSONObject(text).optJSONObject("settings") ?: JSONObject()
        text = JSONObject(emptyData()).put("settings", settings).toString()
    }
    override suspend fun apply(text: String) {
        // The real one is a single transaction: when it fails nothing changes.
        if (failNextApply) { failNextApply = false; throw IOException("disk full") }
        applied++
        this.text = text
    }
}

/** The cloud copy, with the failures a network has. Versions only go up, as the real ones do. */
internal class FakeRemote(var cloud: RemoteSnapshot? = null) : SyncRemote {
    var pushes = 0
    var fetches = 0

    /** Pushes to refuse with a version conflict before one is accepted. */
    var conflicts = 0
    var offline = false
    var failNextPush = false

    /** Another device has taken the account over. */
    var displaced = false
    private var highest = cloud?.version ?: 0L

    override suspend fun fetch(): RemoteSnapshot? {
        if (offline) throw IOException("offline")
        if (displaced) throw SignedInElsewhereException()
        fetches++
        return cloud
    }

    override suspend fun peek(): RemoteSnapshot? {
        if (offline) throw IOException("offline")
        return cloud
    }

    override suspend fun push(text: String, expectedVersion: Long): Long? {
        if (offline) throw IOException("offline")
        if (displaced) throw SignedInElsewhereException()
        if (failNextPush) { failNextPush = false; throw IOException("connection lost") }
        if (conflicts > 0) { conflicts--; return null }
        if ((cloud?.version ?: 0L) != expectedVersion) return null
        pushes++
        highest = maxOf(highest, expectedVersion) + 1
        return highest.also { cloud = RemoteSnapshot(it, text) }
    }

    /** What "delete cloud copy" does. */
    fun delete() { cloud = null }

    /** Another device's push, without going through an engine. */
    fun put(text: String) { highest = maxOf(highest, cloud?.version ?: 0L) + 1; cloud = RemoteSnapshot(highest, text) }
}

internal class FakeMemory(var hash: String? = null, var version: Long = 0, var snapshot: String? = null) : SyncMemory {
    override suspend fun lastLocalHash() = hash
    override suspend fun lastRemoteVersion() = version
    override suspend fun lastSnapshot() = snapshot
    override suspend fun remember(localHash: String, remoteVersion: Long, snapshot: String) {
        hash = localHash; version = remoteVersion; this.snapshot = snapshot
    }
}

/**
 * One installation of the app syncing with [remote]. A [linked] device has already been set up by the user, as one that
 * joined an account with nothing in it; an unlinked one has not, and joins with [joinFromCloud] or [joinWithDevice].
 */
internal class Device(val remote: FakeRemote, text: String = emptyData(), linked: Boolean = true) {
    val local = FakeLocal(text)
    val memory = if (linked) FakeMemory(SyncEngine.hash(emptyData()), 0, emptyData()) else FakeMemory()

    private val engine get() = SyncEngine(local, remote, memory)

    fun sync(): SyncOutcome = runBlocking { engine.sync() }

    /** The user chose the cloud copy: it is looked at once, then it replaces this device's data. */
    fun joinFromCloud(): SyncOutcome = runBlocking { engine.replaceLocal(remote.peek()) }

    /** The user chose this device's data: it replaces the cloud copy that was looked at. */
    fun joinWithDevice(): SyncOutcome = runBlocking { engine.replaceCloud(remote.peek()) }

    var text: String
        get() = local.text
        set(value) { local.text = value }

    val logs: Set<Long> get() = logsOf(local.text)
}

// ── Snapshots ───────────────────────────────────────────────────────────────

/** A revision log at [ts]; the outcome is part of what makes it the same log. */
internal class L(val ts: Long, val outcome: Int = 2)

internal fun snapshot(
    logs: List<L> = emptyList(),
    plans: Map<String, Int> = emptyMap(),
    coverage: List<IntRange> = emptyList(),
    settings: Map<String, Any> = emptyMap()
): String = JSONObject()
    .put("type", "full_backup")
    .put("version", 5)
    .put("revision_logs", JSONArray(logs.map {
        JSONObject().put("timestamp", it.ts).put("start_ayah_id", 1).put("end_ayah_id", 2).put("outcome", it.outcome).put("mistake_tags", 0)
    }))
    .put("hifz_coverage", JSONArray(coverage.map { JSONObject().put("start_ayah_id", it.first).put("end_ayah_id", it.last) }))
    .put("revision_plans", JSONArray(plans.map { (name, amount) ->
        JSONObject().put("name", name).put("unit", 0).put("amount_per_day", amount)
    }))
    .put("settings", JSONObject().also { o -> settings.forEach { (k, v) -> o.put(k, v) } })
    .toString()

/** A device with nothing on it. */
internal fun emptyData() = snapshot()

/** Logs at the given times, nothing else. */
internal fun data(vararg timestamps: Long) = snapshot(logs = timestamps.map { L(it) })

internal fun logsOf(text: String): Set<Long> = JSONObject(text).getJSONArray("revision_logs").let { a ->
    (0 until a.length()).map { a.getJSONObject(it).getLong("timestamp") }.toSet()
}

internal fun outcomesOf(text: String): Map<Long, Int> = JSONObject(text).getJSONArray("revision_logs").let { a ->
    (0 until a.length()).associate { a.getJSONObject(it).getLong("timestamp") to a.getJSONObject(it).getInt("outcome") }
}

internal fun plansOf(text: String): Map<String, Int> = JSONObject(text).getJSONArray("revision_plans").let { a ->
    (0 until a.length()).associate { a.getJSONObject(it).getString("name") to a.getJSONObject(it).getInt("amount_per_day") }
}

/** Every verse the memorised ranges cover. */
internal fun coverageOf(text: String): Set<Int> = JSONObject(text).getJSONArray("hifz_coverage").let { a ->
    (0 until a.length()).flatMap { a.getJSONObject(it).let { r -> r.getInt("start_ayah_id")..r.getInt("end_ayah_id") } }.toSet()
}

internal fun settingsOf(text: String): Map<String, String> = JSONObject(text).getJSONObject("settings").let { o ->
    o.keys().asSequence().associateWith { o.get(it).toString() }
}
