package com.example.mutqan.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persists the user's chosen [ThemeKey] via Jetpack DataStore.
 * All reads are returned as a [Flow]; writes are suspend functions safe to call
 * from any coroutine scope (e.g. ViewModel's [viewModelScope]).
 */
class ThemePreferences(private val context: Context) {

    companion object {
        private val KEY_THEME = stringPreferencesKey("selected_theme")
        private val KEY_PALETTE = stringPreferencesKey("selected_palette")
    }

    val themeKey: Flow<ThemeKey> = context.dataStore.data.map { prefs ->
        ThemeKey.fromString(prefs[KEY_THEME] ?: ThemeKey.AUTO.name)
    }

    val palette: Flow<Palette> = context.dataStore.data.map { prefs -> Palette.fromString(prefs[KEY_PALETTE]) }

    suspend fun setPalette(palette: Palette) {
        context.dataStore.edit { prefs -> prefs[KEY_PALETTE] = palette.name }
    }

    suspend fun setTheme(key: ThemeKey) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME] = key.name
        }
    }
}
