package com.example.mutqan.domain.sync

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.room.InvalidationTracker
import com.example.mutqan.R
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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
data class FirstSync(val cloudEntries: Int, val deviceEntries: Int, val otherAccount: Boolean, val cloudExists: Boolean = true)

/** Another device the account is in use on; [lastActive] is when it last synced (millis), if known. */
data class OtherDevice(val lastActive: Long?)

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

    /** True when another device has taken the account over: syncing here is paused until the user decides. */
    val displaced = prefs.sessionDisplaced

    /** The other device this account is in use on, or null when it is free or already in use here. */
    suspend fun otherDeviceInUse(): OtherDevice? {
        val account = accounts.account.value ?: return null
        val deviceId = prefs.deviceId()
        val active = withTimeout(TIMEOUT_MS) { FirestoreRemote(account.uid, deviceId).activeDevice() } ?: return null
        return if (active.id == deviceId) null else OtherDevice(active.atMillis)
    }

    /** Makes this device the one the account is used on; the other device's sync stops at its next attempt. */
    suspend fun takeOver() {
        val account = accounts.account.value ?: return
        withTimeout(TIMEOUT_MS) { FirestoreRemote(account.uid, prefs.deviceId()).claim() }
        prefs.setSessionDisplaced(false)
    }

    suspend fun signOut() {
        accounts.signOut()
        prefs.setSessionDisplaced(false)
    }

    /** Another device took the account over: syncing here stops until the user decides, and the data here is kept. */
    private suspend fun displacedHere(announce: Boolean) {
        prefs.setSessionDisplaced(true)
        if (announce) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(appContext, appContext.getString(R.string.session_displaced), Toast.LENGTH_LONG).show()
            }
        }
    }

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
            if (prefs.syncOwner() == account.uid) syncNow(auto = true)
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
            val cloud = try {
                withTimeout(TIMEOUT_MS) { FirestoreRemote(account.uid, prefs.deviceId()).fetch() }
            } catch (e: SignedInElsewhereException) {
                displacedHere(announce = false)
                return@withLock null
            }
            val device = backup.buildSnapshot()
            if (SyncEngine.isEmpty(device.toString())) return@withLock null
            // Data from another account would otherwise go into this account without a word.
            if (cloud == null && owner == null) return@withLock null
            FirstSync(
                cloudEntries = cloud?.let { JSONObject(it.text).optJSONArray("revision_logs")?.length() } ?: 0,
                deviceEntries = device.optJSONArray("revision_logs")?.length() ?: 0,
                otherAccount = owner != null,
                cloudExists = cloud != null
            )
        }
    }

    /**
     * Syncs; with [replaceDevice] this device's data is replaced by the cloud copy instead of being merged with it.
     * An [auto] sync does not bring back a cloud copy that was deleted.
     */
    suspend fun syncNow(replaceDevice: Boolean = false, auto: Boolean = false) {
        val account = accounts.account.value ?: return
        if (prefs.isSessionDisplaced()) {
            if (!auto) _status.value = SyncStatus.Failed(appContext.getString(R.string.session_displaced))
            return
        }
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
                val outcome = withTimeout(TIMEOUT_MS) { if (replaceDevice) engine.replaceLocal() else engine.sync(recreateCloud = !auto) }
                SyncStatus.Done(outcome).also { prefs.setLastSyncTime(System.currentTimeMillis()) }
            } catch (e: TimeoutCancellationException) {
                SyncStatus.Failed(appContext.getString(R.string.sync_timeout))
            } catch (e: SignedInElsewhereException) {
                displacedHere(announce = auto)
                SyncStatus.Failed(appContext.getString(R.string.session_displaced))
            } catch (e: Exception) {
                SyncStatus.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Erases the data on this device, then syncs: this account starts from what is in its cloud copy, or from nothing. */
    suspend fun startFresh() {
        lock.withLock {
            withContext(Dispatchers.IO) { AppDatabase.getInstance(appContext).clearAllTables() }
            prefs.forgetSyncHistory()
        }
        syncNow()
    }

    /** Removes this account's data from the cloud; the data on this device stays. */
    suspend fun deleteCloudCopy() {
        val account = accounts.account.value ?: return
        lock.withLock {
            _status.value = try {
                FirestoreRemote(account.uid, prefs.deviceId()).deleteAll()
                // Automatic syncing stops until the next "Sync now", which starts a fresh copy.
                prefs.forgetSyncHistory()
                SyncStatus.Idle
            } catch (e: SignedInElsewhereException) {
                displacedHere(announce = false)
                SyncStatus.Failed(appContext.getString(R.string.session_displaced))
            } catch (e: Exception) {
                SyncStatus.Failed(e.message)
            }
        }
    }

    companion object {
        private const val CHANGE_DELAY_MS = 20_000L
        private const val TIMEOUT_MS = 60_000L

        @Volatile
        private var instance: CloudSync? = null

        fun get(context: Context): CloudSync =
            instance ?: synchronized(this) { instance ?: CloudSync(context).also { instance = it } }
    }
}
