package com.example.mutqan.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.mutqan.R
import com.example.mutqan.domain.MistakeTag
import com.example.mutqan.domain.PassageState
import com.example.mutqan.domain.RecallOutcome
import com.example.mutqan.ui.vault.MasterStrength

/** Locale-aware display label for a group mastery strength. */
@Composable
fun MasterStrength.localizedLabel(): String = when (this) {
    MasterStrength.WEAK   -> stringResource(R.string.strength_weak)
    MasterStrength.STABLE -> stringResource(R.string.strength_stable)
    MasterStrength.SOLID  -> stringResource(R.string.strength_solid)
}

/** Locale-aware label for what a revision session demonstrated. */
@Composable
fun RecallOutcome.localizedLabel(): String = when (this) {
    RecallOutcome.PRACTICE_ONLY  -> stringResource(R.string.outcome_practice_only)
    RecallOutcome.INDEPENDENT    -> stringResource(R.string.outcome_independent)
    RecallOutcome.SELF_CORRECTED -> stringResource(R.string.outcome_self_corrected)
    RecallOutcome.HINTED         -> stringResource(R.string.outcome_hinted)
    RecallOutcome.UNABLE         -> stringResource(R.string.outcome_unable)
}

@Composable
fun MistakeTag.localizedLabel(): String = when (this) {
    MistakeTag.OMISSION      -> stringResource(R.string.tag_omission)
    MistakeTag.ADDITION      -> stringResource(R.string.tag_addition)
    MistakeTag.SUBSTITUTION  -> stringResource(R.string.tag_substitution)
    MistakeTag.ORDER         -> stringResource(R.string.tag_order)
    MistakeTag.HESITATION    -> stringResource(R.string.tag_hesitation)
    MistakeTag.REPETITION    -> stringResource(R.string.tag_repetition)
    MistakeTag.PROMPT_NEEDED -> stringResource(R.string.tag_prompt_needed)
    MistakeTag.SIMILAR_AYAH  -> stringResource(R.string.tag_similar_ayah)
}

@Composable
fun PassageState.localizedLabel(): String = when (this) {
    PassageState.NOT_ASSESSED     -> stringResource(R.string.state_not_assessed)
    PassageState.NEEDS_ATTENTION  -> stringResource(R.string.state_needs_attention)
    PassageState.DEVELOPING       -> stringResource(R.string.state_developing)
    PassageState.RELIABLE         -> stringResource(R.string.state_reliable)
    PassageState.STABLE_OVER_TIME -> stringResource(R.string.state_stable)
}
