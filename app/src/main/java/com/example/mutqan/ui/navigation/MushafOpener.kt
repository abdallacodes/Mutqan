package com.example.mutqan.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Opens the Mushaf at a page, with [ayahId] (a global ayah id) tinted when given. Wherever an ayah is shown it can offer
 * "open in the Mushaf" through this, without every screen passing a callback down. Null where there is nothing to open,
 * such as inside the Mushaf itself.
 */
val LocalMushafOpener = staticCompositionLocalOf<((page: Int, ayahId: Int?) -> Unit)?> { null }
