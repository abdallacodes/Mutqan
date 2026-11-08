package com.example.mutqan.domain.sync

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

    /** A copy of the data could not be saved on the phone first, so nothing was replaced or deleted. */
    data object CopyFailed : SyncStatus()

    /** The cloud copy this device synced with is gone, so syncing from this device is off. */
    data object CloudGone : SyncStatus()

    /** The cloud copy cannot be read (see [CloudCopyDamagedException]); this device changed nothing, in either direction. */
    data object CloudDamaged : SyncStatus()
}

/**
 * Signing in found data on this device, so the user has to say which side wins before anything moves:
 * [cloudExists] false means the account has nothing in the cloud (no copy, or an empty one).
 */
/** [cloudDamaged]: the account has a cloud copy whose pieces are missing, so it cannot be read; only replacing it works. */
data class FirstSync(val cloudEntries: Int, val deviceEntries: Int, val cloudExists: Boolean, val cloudDamaged: Boolean = false)

/** How this device joins the account: the cloud copy replaces what is on the device, or the device replaces the cloud copy. */
enum class Choice { CLOUD_WINS, DEVICE_WINS }

enum class NoticeKind { SYNC_ON_CLOUD, SYNC_ON_DEVICE, CLOUD_DELETED, DEVICE_RESET, MERGED }

/**
 * What the user is told once an override or a deletion is complete. [copy] is the file saved on the phone before
 * anything changed, or null when there was nothing to keep; [cloudExisted] says whether the account had data (only
 * [NoticeKind.SYNC_ON_CLOUD] needs it).
 */
data class SyncNotice(val kind: NoticeKind, val copy: SavedCopy?, val cloudExisted: Boolean = true, val merge: SnapshotMerge.Report? = null)

/** Another device the account is in use on; [lastActive] is when it last synced (millis), if known. */
data class OtherDevice(val lastActive: Long?)

/** Account and sync for the whole app: one instance, so sign-in state and progress are shared by every screen. */
class CloudSync private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private val prefs = UserPreferencesRepository(appContext)
    private val backup by lazy { BackupManager(appContext) }
    private val copies by lazy { SafetyCopies(appContext) }
    private var pending: Job? = null

    /** The copy a merge kept of this device's data, for the notice that follows it. */
    @Volatile
    private var keptCopy: SavedCopy? = null

    val accounts = AccountManager(appContext)
    val isConfigured: Boolean get() = accounts.isConfigured

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status

    /** When the last sync finished, in millis, or null. */
    val lastSyncTime = prefs.lastSyncTime

    /** True when another device has taken the account over: syncing here is paused until the user decides. */
    val displaced = prefs.sessionDisplaced

    /**
     * True while this device is set up to sync with the signed-in account. It becomes true only when the user has
     * chosen how the device joins the account ([enableSync]) and that has worked; until then nothing is synced.
     */
    val linked: Flow<Boolean> = combine(accounts.account, prefs.syncOwnerFlow) { account, owner -> account != null && owner == account.uid }

    private val _notice = MutableStateFlow<SyncNotice?>(null)

    /** The message for the user that follows an override or a deletion, until it is dismissed. */
    val notice: StateFlow<SyncNotice?> = _notice

    fun dismissNotice() {
        _notice.value = null
    }

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

    /**
     * Signs in. The link with any account is ended first, so that nothing syncs between the sign-in and the user's
     * choice about the data (see [enableSync]).
     */
    suspend fun signIn(activity: Activity) {
        lock.withLock { endLink() }
        accounts.signIn(activity)
    }

    suspend fun signOut() {
        lock.withLock {
            endLink()
            accounts.signOut()
        }
    }

    /** Syncing stops until the user chooses again how this device joins an account; the data stays as it is. */
    private suspend fun endLink() {
        pending?.cancel()
        prefs.forgetSyncHistory()
        prefs.setSessionDisplaced(false)
        _status.value = SyncStatus.Idle
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
     * and one shortly after the data changes. Only while the device is linked to the signed-in account; a newly
     * signed-in account first gets the user's choice ([setupChoice], [enableSync]).
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
     * What the user has to decide when this device joins the signed-in account: null when the device holds nothing to
     * lose, so the account's data is simply brought here ([enableSync] with [Choice.CLOUD_WINS]). It only looks;
     * nothing is sent, received or changed.
     */
    suspend fun setupChoice(): FirstSync? {
        val account = accounts.account.value ?: return null
        return lock.withLock {
            val cloud = try {
                withTimeout(TIMEOUT_MS) { FirestoreRemote(account.uid, prefs.deviceId()).peek() }
            } catch (e: CloudCopyDamagedException) {
                android.util.Log.w("CloudSync", "The cloud copy is incomplete", e)
                val here = backup.buildSnapshot()
                return@withLock FirstSync(0, here.optJSONArray("revision_logs")?.length() ?: 0, cloudExists = false, cloudDamaged = true)
            }
            val device = backup.buildSnapshot()
            if (SyncEngine.isEmpty(device.toString())) return@withLock null
            val cloudHasData = cloud != null && !SyncEngine.isEmpty(cloud.text)
            FirstSync(
                cloudEntries = if (cloudHasData) JSONObject(cloud!!.text).optJSONArray("revision_logs")?.length() ?: 0 else 0,
                deviceEntries = device.optJSONArray("revision_logs")?.length() ?: 0,
                cloudExists = cloudHasData
            )
        }
    }

    /**
     * Joins the signed-in account as the user chose, and only then turns syncing on. Before anything is replaced, a
     * copy of what is about to be lost (this device's data, or the cloud copy) is saved on the phone and read back; if
     * that fails nothing is changed. The device is linked to the account only after the replacement has fully worked,
     * so a failure at any point leaves syncing off. When it is done, [notice] says what was kept and where.
     */
    suspend fun enableSync(choice: Choice) {
        val account = accounts.account.value ?: return
        lock.withLock {
            _status.value = SyncStatus.Syncing
            _status.value = try {
                val remote = FirestoreRemote(account.uid, prefs.deviceId())
                val engine = engineFor(account.uid, remote)
                val outcome = withTimeout(TIMEOUT_MS) {
                    val cloud = try {
                        remote.peek()
                    } catch (e: CloudCopyDamagedException) {
                        // Nothing in a damaged copy can be read, so replacing it loses nothing; only replacing the cloud is offered for it.
                        if (choice != Choice.DEVICE_WINS) throw e
                        remote.deleteAll()
                        null
                    }
                    val cloudHasData = cloud != null && !SyncEngine.isEmpty(cloud.text)
                    when (choice) {
                        Choice.CLOUD_WINS -> {
                            val saved = copies.save(CopyReason.DEVICE_REPLACED, backup.buildSnapshot().toString())
                            remote.claim()
                            engine.replaceLocal(cloud).also { _notice.value = SyncNotice(NoticeKind.SYNC_ON_CLOUD, saved, cloudHasData) }
                        }
                        Choice.DEVICE_WINS -> {
                            val saved = cloud?.let { copies.save(CopyReason.CLOUD_REPLACED, it.text) }
                            remote.claim()
                            engine.replaceCloud(cloud).also { _notice.value = SyncNotice(NoticeKind.SYNC_ON_DEVICE, saved) }
                        }
                    }
                }
                SyncStatus.Done(outcome).also { prefs.setLastSyncTime(System.currentTimeMillis()) }
            } catch (e: SafetyCopyFailedException) {
                SyncStatus.CopyFailed
            } catch (e: TimeoutCancellationException) {
                SyncStatus.Failed(appContext.getString(R.string.sync_timeout))
            } catch (e: SignedInElsewhereException) {
                displacedHere(announce = false)
                SyncStatus.Failed(appContext.getString(R.string.session_displaced))
            } catch (e: Exception) {
                android.util.Log.w("CloudSync", "Sync did not finish", e)
                SyncStatus.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /**
     * Syncs a device that is linked to the signed-in account; for any other it does nothing. If the cloud copy has been
     * deleted the link ends and the copy is not made again; the user decides what happens next.
     */
    suspend fun syncNow(auto: Boolean = false) {
        val account = accounts.account.value ?: return
        if (prefs.isSessionDisplaced()) {
            if (!auto) _status.value = SyncStatus.Failed(appContext.getString(R.string.session_displaced))
            return
        }
        lock.withLock {
            // Checked again inside the lock: signing out or deleting data may have ended the link while this waited.
            if (accounts.account.value?.uid != account.uid || prefs.syncOwner() != account.uid) return
            _status.value = SyncStatus.Syncing
            _status.value = try {
                keptCopy = null
                val engine = engineFor(account.uid, FirestoreRemote(account.uid, prefs.deviceId()))
                val outcome = withTimeout(TIMEOUT_MS) { engine.sync() }
                // Two devices changed the account: say what the merge did and where the copy of this device's data is.
                val merge = engine.lastMerge
                if (outcome == SyncOutcome.MERGED && merge != null && !merge.isEmpty) {
                    _notice.value = SyncNotice(NoticeKind.MERGED, keptCopy, merge = merge)
                }
                SyncStatus.Done(outcome).also { prefs.setLastSyncTime(System.currentTimeMillis()) }
            } catch (e: SafetyCopyFailedException) {
                SyncStatus.CopyFailed
            } catch (e: CloudCopyGoneException) {
                prefs.forgetSyncHistory()
                SyncStatus.CloudGone
            } catch (e: CloudCopyDamagedException) {
                android.util.Log.w("CloudSync", "The cloud copy is damaged", e)
                SyncStatus.CloudDamaged
            } catch (e: NotLinkedException) {
                SyncStatus.Idle
            } catch (e: TimeoutCancellationException) {
                SyncStatus.Failed(appContext.getString(R.string.sync_timeout))
            } catch (e: SignedInElsewhereException) {
                displacedHere(announce = auto)
                SyncStatus.Failed(appContext.getString(R.string.session_displaced))
            } catch (e: Exception) {
                android.util.Log.w("CloudSync", "Sync did not finish", e)
                SyncStatus.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /**
     * Deletes everything this account has in the cloud, so that nothing is left there, after saving a copy of it on
     * the phone. The data on this device stays; syncing is off until the user sets it up again. When it is done,
     * [notice] says what was deleted and where the copy is.
     */
    suspend fun deleteCloudCopy() {
        val account = accounts.account.value ?: return
        lock.withLock {
            _status.value = SyncStatus.Syncing
            _status.value = try {
                val remote = FirestoreRemote(account.uid, prefs.deviceId())
                withTimeout(TIMEOUT_MS) {
                    // A damaged copy cannot be read, so there is nothing of it to keep; it can still be deleted.
                    val readable = try { remote.peek() } catch (e: CloudCopyDamagedException) { null }
                    val saved = readable?.let { copies.save(CopyReason.CLOUD_DELETED, it.text) }
                    remote.deleteAll()
                    prefs.forgetSyncHistory()
                    _notice.value = SyncNotice(NoticeKind.CLOUD_DELETED, saved)
                }
                SyncStatus.Idle
            } catch (e: SafetyCopyFailedException) {
                SyncStatus.CopyFailed
            } catch (e: TimeoutCancellationException) {
                SyncStatus.Failed(appContext.getString(R.string.sync_timeout))
            } catch (e: SignedInElsewhereException) {
                displacedHere(announce = false)
                SyncStatus.Failed(appContext.getString(R.string.session_displaced))
            } catch (e: Exception) {
                android.util.Log.w("CloudSync", "Sync did not finish", e)
                SyncStatus.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /**
     * Puts this device back to how it was when the app was first installed: the data and settings are gone, the account
     * is signed out and nothing is linked, so the app asks nothing of the cloud. A copy of the data is saved on the
     * phone first (a [SafetyCopyFailedException] means nothing was deleted). The cloud copy is not touched. When it is
     * done, [notice] says what was deleted and where the copy is.
     */
    suspend fun resetDevice() {
        lock.withLock {
            val saved = copies.save(CopyReason.DEVICE_DELETED, backup.buildSnapshot().toString())
            endLink()
            accounts.signOut()
            withContext(Dispatchers.IO) {
                val db = AppDatabase.getInstance(appContext)
                db.clearAllTables()
                // A fresh install numbers its rows from 1.
                runCatching { db.openHelper.writableDatabase.execSQL("DELETE FROM sqlite_sequence") }
            }
            prefs.resetToFreshInstall()
            _notice.value = SyncNotice(NoticeKind.DEVICE_RESET, saved)
        }
        // Arabic is where a first run starts, whatever language the user had chosen.
        withContext(Dispatchers.Main) { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar")) }
    }

    private fun engineFor(uid: String, remote: SyncRemote) = SyncEngine(
        local = object : SyncLocal {
            override suspend fun snapshot() = backup.buildSnapshot().toString()
            override suspend fun apply(text: String) = backup.restoreSnapshot(JSONObject(text))
            override suspend fun clear() = backup.clearUserData()
            override suspend fun keepCopyBeforeMerge(text: String) {
                keptCopy = copies.save(CopyReason.BEFORE_MERGE, text)
            }
        },
        remote = remote,
        memory = prefs.syncMemoryFor(uid)
    )

    companion object {
        private const val CHANGE_DELAY_MS = 20_000L
        private const val TIMEOUT_MS = 60_000L

        @Volatile
        private var instance: CloudSync? = null

        fun get(context: Context): CloudSync =
            instance ?: synchronized(this) { instance ?: CloudSync(context).also { instance = it } }
    }
}
