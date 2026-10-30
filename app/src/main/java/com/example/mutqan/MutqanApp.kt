package com.example.mutqan

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class MutqanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        applyDefaultLanguage()
    }

    /**
     * Arabic is the app's default language whatever the device language is. Runs once; after that
     * the language is whatever the user picked (kept by AppCompat's per-app locale store).
     */
    private fun applyDefaultLanguage() {
        val prefs = getSharedPreferences("locale_defaults", MODE_PRIVATE)
        if (prefs.getBoolean(KEY_APPLIED, false)) return
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"))
        }
        prefs.edit().putBoolean(KEY_APPLIED, true).apply()
    }

    private companion object {
        const val KEY_APPLIED = "arabic_default_applied"
    }
}
