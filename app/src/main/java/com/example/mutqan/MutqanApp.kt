package com.example.mutqan

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
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
     *
     * Android 12 and older: done here through AppCompat. Android 13 and newer: done by [MainActivity.applyArabicDefault],
     * because the system only applies a language change to an app that has finished starting; set from here, while
     * the process is still starting, it was stored but the first screen stayed in the device's language.
     */
    private fun applyDefaultLanguage() {
        if (Build.VERSION.SDK_INT >= 33) {
            markAppCompatLocaleMigrationDone()
        } else if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"))
        }
    }

    /**
     * On Android 13+ AppCompat runs a one-time migration of the locales it used to keep itself when the first activity
     * starts, and on a fresh install it resets the system list to empty, wiping a language set before it ran. This app
     * never kept locales that way, so the migration is marked as done up front (what AppCompat itself does after it runs).
     */
    private fun markAppCompatLocaleMigrationDone() {
        val holder = ComponentName(this, "androidx.appcompat.app.AppLocalesMetadataHolderService")
        if (packageManager.getComponentEnabledSetting(holder) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            packageManager.setComponentEnabledSetting(holder, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        }
    }

}
