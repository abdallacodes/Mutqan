package com.example.qmemo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Single DataStore instance shared across all preference repositories.
 * Declared here once to prevent the "multiple DataStore for same file" crash.
 */
internal val Context.dataStore: DataStore<Preferences>
    by preferencesDataStore(name = "qmemo_prefs")
