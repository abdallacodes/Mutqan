package com.example.mutqan.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.example.mutqan.ui.theme.FolioTheme

/**
 * Call this inside a full-screen Dialog. A Dialog is a window of its own, so the clock and icons in the system bars
 * keep the system's colours (white on a light page) unless they are set again here, as MutqanTheme does for the activity.
 */
@Composable
fun DialogSystemBars() {
    val view = LocalView.current
    val dark = FolioTheme.colors.dark
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }
}
