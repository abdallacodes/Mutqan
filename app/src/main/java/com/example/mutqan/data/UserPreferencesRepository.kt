package com.example.mutqan.data

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.mutqan.domain.sync.SyncMemory
import kotlinx.coroutines.flow.first
import java.util.UUID
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserPreferencesRepository(private val context: Context) {

    companion object {
        private val KEY_IS_FIRST_RUN      = booleanPreferencesKey("is_first_run")
        private val KEY_HEATMAP_COACHMARK = booleanPreferencesKey("heatmap_coachmark_seen")
        private val KEY_APP_LOCK          = booleanPreferencesKey("app_lock_enabled")
        private val KEY_LAST_MUSHAF_PAGE  = intPreferencesKey("last_mushaf_page")
        private val KEY_TOPICS_SHOW_ALL   = booleanPreferencesKey("topics_show_all")
        private val KEY_TAFSIR_SOURCE     = intPreferencesKey("tafsir_source")
        private val KEY_MUSHAF_THEME      = stringPreferencesKey("mushaf_theme")
        private val KEY_MUSHAF_ROTATE     = booleanPreferencesKey("mushaf_rotate")
        private val KEY_RECITER           = stringPreferencesKey("recitation_reciter")
        private val KEY_RECITE_REPEAT     = intPreferencesKey("recitation_repeat")
        private val KEY_RECITE_LOOP       = booleanPreferencesKey("recitation_loop")
        private val KEY_RECITE_SURAH      = booleanPreferencesKey("recitation_to_surah_end")
        private val KEY_RECITE_SPEED      = intPreferencesKey("recitation_speed_percent")
        private val KEY_SYNC_UID          = stringPreferencesKey("sync_uid")
        private val KEY_SYNC_HASH         = stringPreferencesKey("sync_local_hash")
        private val KEY_SYNC_VERSION      = longPreferencesKey("sync_remote_version")
        private val KEY_SYNC_TIME         = longPreferencesKey("sync_last_time")
        private val KEY_DEVICE_ID         = stringPreferencesKey("sync_device_id")
    }

    /** `true` on fresh installs; `false` after onboarding is completed. */
    val isFirstRun: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_IS_FIRST_RUN] ?: true
    }

    /** `false` until the user dismisses the heatmap coachmark for the first time. */
    val hasSeenHeatmapCoachmark: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_HEATMAP_COACHMARK] ?: false
    }

    /** Whether the app asks for the device PIN, pattern or fingerprint when it is opened. */
    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_APP_LOCK] ?: false }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_APP_LOCK] = enabled }
    }

    /** The Mushaf page the user was last reading, or null before they have read any. */
    val lastMushafPage: Flow<Int?> = context.dataStore.data.map { prefs -> prefs[KEY_LAST_MUSHAF_PAGE] }

    suspend fun setLastMushafPage(page: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_LAST_MUSHAF_PAGE] = page }
    }

    /** Whether the Arabic interface also lists topics that QUL names only in English. */
    val topicsShowAll: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_TOPICS_SHOW_ALL] ?: false }

    suspend fun setTopicsShowAll(on: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_TOPICS_SHOW_ALL] = on }
    }

    /** The name of the Mushaf reading theme (see MushafTheme); empty until the reader chooses. */
    val mushafTheme: Flow<String> = context.dataStore.data.map { prefs -> prefs[KEY_MUSHAF_THEME].orEmpty() }

    suspend fun setMushafTheme(name: String) {
        context.dataStore.edit { prefs -> prefs[KEY_MUSHAF_THEME] = name }
    }

    /** Whether the Mushaf turns with the device instead of staying portrait (one page) or landscape (two). */
    val mushafRotate: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_MUSHAF_ROTATE] ?: false }

    suspend fun setMushafRotate(on: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_MUSHAF_ROTATE] = on }
    }

    /** The reciter the reader chose (see Reciter); empty until they choose. */
    val reciterId: Flow<String> = context.dataStore.data.map { prefs -> prefs[KEY_RECITER] ?: "" }

    suspend fun setReciterId(id: String) {
        context.dataStore.edit { prefs -> prefs[KEY_RECITER] = id }
    }

    /** How many times each verse is played before the next. */
    val recitationRepeat: Flow<Int> = context.dataStore.data.map { prefs -> prefs[KEY_RECITE_REPEAT] ?: 1 }

    suspend fun setRecitationRepeat(times: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_RECITE_REPEAT] = times.coerceIn(1, 20) }
    }

    /** Whether the range starts over when it ends. */
    val recitationLoop: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_RECITE_LOOP] ?: false }

    suspend fun setRecitationLoop(on: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_RECITE_LOOP] = on }
    }

    /** Whether playing goes on to the end of the surah instead of stopping at the end of the page. */
    val recitationToSurahEnd: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_RECITE_SURAH] ?: false }

    suspend fun setRecitationToSurahEnd(on: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_RECITE_SURAH] = on }
    }

    /** Playback speed in percent (100 = normal). */
    val recitationSpeedPercent: Flow<Int> = context.dataStore.data.map { prefs -> prefs[KEY_RECITE_SPEED] ?: 100 }

    suspend fun setRecitationSpeedPercent(percent: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_RECITE_SPEED] = percent.coerceIn(50, 200) }
    }

    /** The id of the tafsir the reader chose last (see TafsirSource); Saadi until they choose. */
    val tafsirSource: Flow<Int> = context.dataStore.data.map { prefs -> prefs[KEY_TAFSIR_SOURCE] ?: 1 }

    suspend fun setTafsirSource(id: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_TAFSIR_SOURCE] = id }
    }

    /** When the last cloud sync finished, in millis, or null if there has been none. */
    val lastSyncTime: Flow<Long?> = context.dataStore.data.map { prefs -> prefs[KEY_SYNC_TIME] }

    suspend fun setLastSyncTime(millis: Long) {
        context.dataStore.edit { prefs -> prefs[KEY_SYNC_TIME] = millis }
    }

    /** A random id for this installation, written with each cloud push. */
    suspend fun deviceId(): String {
        context.dataStore.data.first()[KEY_DEVICE_ID]?.let { return it }
        val id = UUID.randomUUID().toString()
        context.dataStore.edit { prefs -> prefs[KEY_DEVICE_ID] = id }
        return id
    }

    /** The account this device's data was last synced with, or null if it never was. */
    suspend fun syncOwner(): String? = context.dataStore.data.first()[KEY_SYNC_UID]?.takeIf { it.isNotEmpty() }

    /**
     * What the last sync with [uid]'s cloud copy left behind. Another account has no shared history, so for it
     * everything counts as new.
     */
    fun syncMemoryFor(uid: String): SyncMemory = object : SyncMemory {
        override suspend fun lastLocalHash(): String? {
            val prefs = context.dataStore.data.first()
            return if (prefs[KEY_SYNC_UID] == uid) prefs[KEY_SYNC_HASH]?.takeIf { it.isNotEmpty() } else null
        }

        override suspend fun lastRemoteVersion(): Long {
            val prefs = context.dataStore.data.first()
            return if (prefs[KEY_SYNC_UID] == uid) prefs[KEY_SYNC_VERSION] ?: 0L else 0L
        }

        override suspend fun remember(localHash: String, remoteVersion: Long) {
            context.dataStore.edit { prefs ->
                prefs[KEY_SYNC_UID] = uid
                prefs[KEY_SYNC_HASH] = localHash
                prefs[KEY_SYNC_VERSION] = remoteVersion
            }
        }
    }

    suspend fun completeOnboarding() {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_FIRST_RUN] = false
        }
    }

    suspend fun markHeatmapCoachmarkSeen() {
        context.dataStore.edit { prefs ->
            prefs[KEY_HEATMAP_COACHMARK] = true
        }
    }
}
