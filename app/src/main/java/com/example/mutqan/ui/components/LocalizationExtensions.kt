package com.example.mutqan.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.mutqan.R
import com.example.mutqan.ui.vault.MasterStrength

/** Locale-aware display label for a group mastery strength. */
@Composable
fun MasterStrength.localizedLabel(): String = when (this) {
    MasterStrength.WEAK   -> stringResource(R.string.strength_weak)
    MasterStrength.STABLE -> stringResource(R.string.strength_stable)
    MasterStrength.SOLID  -> stringResource(R.string.strength_solid)
}
