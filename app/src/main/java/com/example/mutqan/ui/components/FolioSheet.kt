package com.example.mutqan.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable

/**
 * What a bottom sheet pads its content by: the navigation bar at its full size even while it is hidden, and the keyboard.
 *
 * The sheet's own default follows the bars as they come and go. In the Mushaf the bars are hidden and a swipe near the
 * bottom edge brings them back for a moment, which would change the padding of a sheet while it is being dragged. A
 * report for Material3 1.4.0 also has a big sheet bouncing after an upward fling with the default insets, cured by
 * changing them. Padding by the size the bar has anyway keeps the content still. Not reproduced on a device yet.
 */
@OptIn(ExperimentalLayoutApi::class)
val FolioSheetInsets: @Composable () -> WindowInsets = {
    WindowInsets.navigationBarsIgnoringVisibility.union(WindowInsets.ime)
}
