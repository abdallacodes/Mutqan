package com.example.qmemo.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persists the user's preference for Structural Mode vs Memory Health mode.
 */
class StructuralPrefsRepository(private val context: Context) {

    private object PreferencesKeys {
        val IS_STRUCTURAL_MODE = booleanPreferencesKey("is_structural_mode")
    }

    val isStructuralMode: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.IS_STRUCTURAL_MODE] ?: false
        }

    suspend fun setStructuralMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_STRUCTURAL_MODE] = enabled
        }
    }
}
