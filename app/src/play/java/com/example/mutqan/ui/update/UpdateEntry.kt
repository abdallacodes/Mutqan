package com.example.mutqan.ui.update

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/*
 * The Google Play build has no updater: Play does not let an app update itself any way but through Play, so none of
 * the code that downloads or installs an APK is in this build. These two stand in for the direct build's pieces.
 */

/** Never an offer here; see the direct build's file of the same name. */
@Composable
fun rememberUpdateOffer(): (@Composable () -> Unit)? = null

/** The "Check for updates" line of Settings: not in the Google Play build. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun UpdateSettingsRow(modifier: Modifier = Modifier) = Unit
