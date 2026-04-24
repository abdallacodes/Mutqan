package com.example.qmemo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "qmemo_prefs")

/**
 * Persists the user's chosen [ThemeKey] via Jetpack DataStore.
 * All reads are returned as a [Flow]; writes are suspend functions safe to call
 * from any coroutine scope (e.g. ViewModel's [viewModelScope]).
 */
class ThemePreferences(private val context: Context) {

    companion object {
        private val KEY_THEME = stringPreferencesKey("selected_theme")
    }

    val themeKey: Flow<ThemeKey> = context.dataStore.data.map { prefs ->
        ThemeKey.fromString(prefs[KEY_THEME] ?: ThemeKey.AUTO.name)
    }

    suspend fun setTheme(key: ThemeKey) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME] = key.name
        }
    }
}
