package com.example.mutqan

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.mutqan.domain.sync.CloudSync

class MutqanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        applyDefaultLanguage()
        CloudSync.get(this).startAutoSync(this)
    }

    /**
     * Arabic is the app's default language whatever the device language is. An empty per-app language list means
     * the user has not chosen one (a choice, even English, is stored), so Arabic is applied; this is checked on every
     * start because some Android versions can lose the stored list.
     */
    private fun applyDefaultLanguage() {
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"))
        }
    }

}
