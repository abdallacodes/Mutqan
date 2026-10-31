package com.example.mutqan.domain.sync

import android.content.Context
import com.example.mutqan.data.UserPreferencesRepository
import com.example.mutqan.domain.BackupManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

/** Account and sync for the whole app: one instance, so sign-in state and progress are shared by every screen. */
class CloudSync private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private val prefs = UserPreferencesRepository(appContext)
    private val backup by lazy { BackupManager(appContext) }

    val accounts = AccountManager(appContext)
    val isConfigured: Boolean get() = accounts.isConfigured

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status

    /** When the last sync finished, in millis, or null. */
    val lastSyncTime = prefs.lastSyncTime

    /** Syncs in the background if someone is signed in; used when the app starts. */
    fun syncInBackground() {
        if (accounts.account.value != null) scope.launch { syncNow() }
    }

    suspend fun syncNow() {
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
                SyncStatus.Done(engine.sync()).also { prefs.setLastSyncTime(System.currentTimeMillis()) }
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
        @Volatile
        private var instance: CloudSync? = null

        fun get(context: Context): CloudSync =
            instance ?: synchronized(this) { instance ?: CloudSync(context).also { instance = it } }
    }
}
