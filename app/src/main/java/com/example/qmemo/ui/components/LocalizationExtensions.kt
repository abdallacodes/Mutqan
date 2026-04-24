package com.example.qmemo.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.qmemo.R
import com.example.qmemo.ui.revision.Difficulty
import com.example.qmemo.ui.vault.MasterStrength

/** Locale-aware display label for a revision difficulty level. */
@Composable
fun Difficulty.localizedLabel(): String = when (this) {
    Difficulty.SMOOTH    -> stringResource(R.string.difficulty_smooth)
    Difficulty.STRUGGLED -> stringResource(R.string.difficulty_struggled)
    Difficulty.CRITICAL  -> stringResource(R.string.difficulty_critical)
}

/** Locale-aware display label for a group mastery strength. */
@Composable
fun MasterStrength.localizedLabel(): String = when (this) {
    MasterStrength.WEAK   -> stringResource(R.string.strength_weak)
    MasterStrength.STABLE -> stringResource(R.string.strength_stable)
    MasterStrength.SOLID  -> stringResource(R.string.strength_solid)
}
