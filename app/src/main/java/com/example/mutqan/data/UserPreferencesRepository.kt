package com.example.mutqan.data

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.mutqan.domain.PracticeScope
import com.example.mutqan.domain.sync.SyncMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
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
        private val KEY_TAFSIR_SOURCE     = intPreferencesKey("tafsir_source")
        private val KEY_MUSHAF_THEME      = stringPreferencesKey("mushaf_theme")
        private val KEY_MUSHAF_ROTATE     = booleanPreferencesKey("mushaf_rotate")
        private val KEY_RECITER           = stringPreferencesKey("recitation_reciter")
        private val KEY_RECITE_REPEAT     = intPreferencesKey("recitation_repeat")
        private val KEY_RECITE_LOOP       = booleanPreferencesKey("recitation_loop")
        private val KEY_RECITE_SURAH      = booleanPreferencesKey("recitation_to_surah_end")
        private val KEY_RECITE_SPEED      = intPreferencesKey("recitation_speed_percent")
        private val KEY_RECITE_RUN        = intPreferencesKey("recitation_run_repeat")
        private val KEY_RECITE_AFTER      = intPreferencesKey("recitation_say_after_percent")
        private val KEY_RECITE_LAST       = intPreferencesKey("recitation_last_ayah")
        private val KEY_PRACTICE_TIERS    = intPreferencesKey("practice_tier_mask")
        private val KEY_PRACTICE_LAST     = intPreferencesKey("practice_last_pages")
        private val KEY_PRACTICE_UNIT     = intPreferencesKey("practice_last_unit")
        private val KEY_SYNC_UID          = stringPreferencesKey("sync_uid")
        private val KEY_SYNC_HASH         = stringPreferencesKey("sync_local_hash")
        private val KEY_SYNC_VERSION      = longPreferencesKey("sync_remote_version")
        private val KEY_SYNC_TIME         = longPreferencesKey("sync_last_time")
        private val KEY_DEVICE_ID         = stringPreferencesKey("sync_device_id")
        private val KEY_DISPLACED         = booleanPreferencesKey("sync_session_displaced")

        private val SYNCED_INTS = listOf(
            KEY_RECITE_REPEAT, KEY_RECITE_SPEED, KEY_RECITE_RUN, KEY_RECITE_AFTER,
            KEY_PRACTICE_TIERS, KEY_PRACTICE_LAST, KEY_PRACTICE_UNIT, KEY_TAFSIR_SOURCE
        )
        private val SYNCED_BOOLEANS = listOf(KEY_RECITE_LOOP, KEY_RECITE_SURAH)
        private val SYNCED_STRINGS = listOf(KEY_RECITER, KEY_MUSHAF_THEME)
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
    /** How many times the whole passage plays through before the recitation ends (1 = once). */
    val recitationRunRepeat: Flow<Int> = context.dataStore.data.map { prefs -> prefs[KEY_RECITE_RUN] ?: 1 }

    suspend fun setRecitationRunRepeat(times: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_RECITE_RUN] = times.coerceIn(1, 20) }
    }

    /**
     * Recite-after-me: after each verse the recitation waits for this share of the verse's length (in percent) for
     * the reader to say it; 0 means it does not wait.
     */
    val recitationSayAfterPercent: Flow<Int> = context.dataStore.data.map { prefs -> prefs[KEY_RECITE_AFTER] ?: 0 }

    suspend fun setRecitationSayAfterPercent(percent: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_RECITE_AFTER] = percent.coerceIn(0, 300) }
    }

    /** Which pages the practice questions are about (see PracticeScope); nothing chosen means every memorised page. */
    val practiceScope: Flow<PracticeScope> = context.dataStore.data.map { prefs ->
        PracticeScope.fromStored(prefs[KEY_PRACTICE_TIERS] ?: 0, prefs[KEY_PRACTICE_LAST] ?: 0, prefs[KEY_PRACTICE_UNIT] ?: 0)
    }

    suspend fun setPracticeScope(scope: PracticeScope) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PRACTICE_TIERS] = scope.tierMask
            prefs[KEY_PRACTICE_LAST] = scope.recentAmount
            prefs[KEY_PRACTICE_UNIT] = scope.recentUnit.code
        }
    }

    /** The verse (global id) the last recitation had reached; 0 when there has been none. */
    val lastRecitationAyah: Flow<Int> = context.dataStore.data.map { prefs -> prefs[KEY_RECITE_LAST] ?: 0 }

    suspend fun setLastRecitationAyah(id: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_RECITE_LAST] = id }
    }

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

    /** True once another device has taken the account over; syncing here is paused until the user decides. */
    val sessionDisplaced: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_DISPLACED] ?: false }

    suspend fun isSessionDisplaced(): Boolean = context.dataStore.data.first()[KEY_DISPLACED] ?: false

    suspend fun setSessionDisplaced(on: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_DISPLACED] = on }
    }

    /**
     * The account this device is set up to sync with, or null: set only once the user has chosen how this device
     * joins the account and that has worked, cleared by signing out and by anything that ends the link.
     */
    suspend fun syncOwner(): String? = context.dataStore.data.first()[KEY_SYNC_UID]?.takeIf { it.isNotEmpty() }

    val syncOwnerFlow: Flow<String?> = context.dataStore.data.map { prefs -> prefs[KEY_SYNC_UID]?.takeIf { it.isNotEmpty() } }

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

        override suspend fun lastSnapshot(): String? {
            if (context.dataStore.data.first()[KEY_SYNC_UID] != uid) return null
            return withContext(Dispatchers.IO) { baseFile().takeIf { it.exists() }?.readText() }
        }

        override suspend fun remember(localHash: String, remoteVersion: Long, snapshot: String) {
            // The copy first: if the app dies in between, the older hash just makes the next sync look again.
            withContext(Dispatchers.IO) {
                val temp = File(baseFile().path + ".tmp")
                temp.writeText(snapshot)
                if (!temp.renameTo(baseFile())) { baseFile().delete(); temp.renameTo(baseFile()) }
            }
            context.dataStore.edit { prefs ->
                prefs[KEY_SYNC_UID] = uid
                prefs[KEY_SYNC_HASH] = localHash
                prefs[KEY_SYNC_VERSION] = remoteVersion
            }
        }
    }

    /** The data as the last sync left it (see SyncMemory). */
    private fun baseFile() = File(context.filesDir, "sync_base.json")

    /**
     * The settings that follow the reader to every device: reciter and playback, practice choices, tafsir and Mushaf
     * theme. What belongs to one device (app lock, screen rotation, where the reader was) is left out.
     */
    suspend fun syncedSettings(): JSONObject {
        val prefs = context.dataStore.data.first()
        return JSONObject().also { out ->
            for (key in SYNCED_INTS) prefs[key]?.let { out.put(key.name, it) }
            for (key in SYNCED_BOOLEANS) prefs[key]?.let { out.put(key.name, it) }
            for (key in SYNCED_STRINGS) prefs[key]?.let { out.put(key.name, it) }
        }
    }

    /** Sets the settings [syncedSettings] wrote; any that are missing from [json] stay as they are. */
    suspend fun applySyncedSettings(json: JSONObject) {
        context.dataStore.edit { prefs ->
            for (key in SYNCED_INTS) if (json.has(key.name)) prefs[key] = json.optInt(key.name)
            for (key in SYNCED_BOOLEANS) if (json.has(key.name)) prefs[key] = json.optBoolean(key.name)
            for (key in SYNCED_STRINGS) if (json.has(key.name)) prefs[key] = json.optString(key.name)
        }
    }

    /** Ends the link with the account: nothing syncs until the user chooses again how this device joins one. */
    suspend fun forgetSyncHistory() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_SYNC_UID)
            prefs.remove(KEY_SYNC_HASH)
            prefs.remove(KEY_SYNC_VERSION)
            prefs.remove(KEY_SYNC_TIME)
        }
        withContext(Dispatchers.IO) { baseFile().delete() }
    }

    /**
     * Puts everything stored here back to how a first run finds it: settings, onboarding, the sync link, the lot.
     * Only the installation id stays, so the account does not take this phone for a second device.
     */
    suspend fun resetToFreshInstall() {
        context.dataStore.edit { prefs ->
            val id = prefs[KEY_DEVICE_ID]
            prefs.clear()
            if (id != null) prefs[KEY_DEVICE_ID] = id
        }
        withContext(Dispatchers.IO) { baseFile().delete() }
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
