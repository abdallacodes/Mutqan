package com.example.mutqan.domain.sync

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.room.InvalidationTracker
import com.example.mutqan.data.UserPreferencesRepository
import com.example.mutqan.data.local.AppDatabase
import com.example.mutqan.domain.BackupManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object Syncing : SyncStatus()
    data class Done(val outcome: SyncOutcome) : SyncStatus()
    data class Failed(val message: String?) : SyncStatus()
}

/**
 * Signing in found an account that already has data in the cloud while this device has data of its own, so the
 * user has to say what happens to the two. [otherAccount]: the data on this device was last synced with a different account.
 */
data class FirstSync(val cloudEntries: Int, val deviceEntries: Int, val otherAccount: Boolean)

/** Account and sync for the whole app: one instance, so sign-in state and progress are shared by every screen. */
class CloudSync private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private val prefs = UserPreferencesRepository(appContext)
    private val backup by lazy { BackupManager(appContext) }
    private var pending: Job? = null

    val accounts = AccountManager(appContext)
    val isConfigured: Boolean get() = accounts.isConfigured

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status

    /** When the last sync finished, in millis, or null. */
    val lastSyncTime = prefs.lastSyncTime

    /**
     * Keeps the cloud copy current without being asked: a sync when the app comes to the front and when it goes away,
     * and one shortly after the data changes. Only for an account this device has already synced with; a new
     * account first gets its choice from [firstSyncChoice].
     */
    fun startAutoSync(app: Application) {
        if (!isConfigured) return
        autoSync()
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(activity: Activity) { if (started++ == 0) autoSync() }
            override fun onActivityStopped(activity: Activity) { if (--started == 0) autoSync() }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        scope.launch {
            val tables = arrayOf(
                "revision_logs", "exercise_results", "hifz_coverage", "revision_plans", "saved_passages",
                "page_strength", "similarity_groups", "similarity_members", "user_subjects", "vault_folders"
            )
            AppDatabase.getInstance(appContext).invalidationTracker.addObserver(object : InvalidationTracker.Observer(tables) {
                override fun onInvalidated(tables: Set<String>) {
                    if (_status.value != SyncStatus.Syncing) autoSyncSoon()
                }
            })
        }
    }

    /** Syncs now if someone is signed in whose data this device has synced before. */
    private fun autoSync() {
        scope.launch {
            val account = accounts.account.value ?: return@launch
            if (prefs.syncOwner() == account.uid) syncNow()
        }
    }

    /** Waits for the changes to stop for a moment, so a run of edits is one sync. */
    private fun autoSyncSoon() {
        pending?.cancel()
        pending = scope.launch {
            delay(CHANGE_DELAY_MS)
            autoSync()
        }
    }

    /**
     * Asks what to do when this account is new to this device but already has data in the cloud and the device has data
     * too; null when there is nothing to decide (no cloud copy, nothing on the device, or this account was synced before).
     */
    suspend fun firstSyncChoice(): FirstSync? {
        val account = accounts.account.value ?: return null
        val owner = prefs.syncOwner()
        if (owner == account.uid) return null
        return lock.withLock {
            val cloud = FirestoreRemote(account.uid, prefs.deviceId()).fetch() ?: return@withLock null
            val device = backup.buildSnapshot()
            val deviceEntries = device.optJSONArray("revision_logs")?.length() ?: 0
            val deviceCoverage = device.optJSONArray("hifz_coverage")?.length() ?: 0
            if (deviceEntries == 0 && deviceCoverage == 0) return@withLock null
            FirstSync(
                cloudEntries = JSONObject(cloud.text).optJSONArray("revision_logs")?.length() ?: 0,
                deviceEntries = deviceEntries,
                otherAccount = owner != null
            )
        }
    }

    /** Syncs; with [replaceDevice] this device's data is replaced by the cloud copy instead of being merged with it. */
    suspend fun syncNow(replaceDevice: Boolean = false) {
        val account = accounts.account.value ?: return
        lock.withLock {
            _status.value = SyncStatus.Syncing
            _status.value = try {
                val engine = SyncEngine(
                    local = object : SyncLocal {
                        override suspend fun snapshot() = backup.buildSnapshot().toString()
                        override suspend fun apply(text: String) = backup.restoreSnapshot(JSONObject(text))
                    },
                    remote = FirestoreRemote(account.uid, prefs.deviceId()),
                    memory = prefs.syncMemoryFor(account.uid)
                )
                SyncStatus.Done(if (replaceDevice) engine.replaceLocal() else engine.sync())
                    .also { prefs.setLastSyncTime(System.currentTimeMillis()) }
            } catch (e: Exception) {
                SyncStatus.Failed(e.message)
            }
        }
    }

    /** Removes this account's data from the cloud; the data on this device stays. */
    suspend fun deleteCloudCopy() {
        val account = accounts.account.value ?: return
        lock.withLock {
            _status.value = try {
                FirestoreRemote(account.uid, prefs.deviceId()).deleteAll()
                prefs.syncMemoryFor(account.uid).remember("", 0)
                SyncStatus.Idle
            } catch (e: Exception) {
                SyncStatus.Failed(e.message)
            }
        }
    }

    companion object {
        private const val CHANGE_DELAY_MS = 20_000L

        @Volatile
        private var instance: CloudSync? = null

        fun get(context: Context): CloudSync =
            instance ?: synchronized(this) { instance ?: CloudSync(context).also { instance = it } }
    }
}
