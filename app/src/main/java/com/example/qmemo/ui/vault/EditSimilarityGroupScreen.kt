package com.example.qmemo.ui.vault

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.qmemo.R
import com.example.qmemo.data.SurahData
import com.example.qmemo.data.local.dao.MemberVerseRef
import com.example.qmemo.ui.components.localizedLabel
import com.example.qmemo.ui.theme.DifficultyCritical
import com.example.qmemo.ui.theme.DifficultySmooth
import com.example.qmemo.ui.theme.DifficultyStruggled

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSimilarityGroupScreen(
    groupId: Int?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: EditGroupViewModel = viewModel(factory = EditGroupViewModelFactory(context))

    LaunchedEffect(groupId) {
        if (groupId != null) viewModel.loadGroup(groupId)
    }

    val uiState by viewModel.uiState.collectAsState()
    val members by viewModel.members.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }

    val addResult = uiState.addVerseResult
    LaunchedEffect(addResult) {
        val msg = when (addResult) {
            is AddVerseResult.InvalidRef    -> context.getString(R.string.snackbar_no_verse)
            is AddVerseResult.AlreadyMember -> context.getString(R.string.snackbar_already_member)
            is AddVerseResult.GroupNotSaved -> context.getString(R.string.snackbar_save_first)
            is AddVerseResult.Added         -> null
            else -> null
        }
        if (msg != null) {
            snackbarHost.showSnackbar(msg)
            viewModel.clearAddResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                title = {
                    Text(
                        text          = if (groupId == null) stringResource(R.string.new_group_title)
                                        else stringResource(R.string.edit_group_title),
                        style         = MaterialTheme.typography.titleMedium,
                        fontWeight    = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color         = MaterialTheme.colorScheme.onBackground
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost   = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            item {
                SectionLabel(stringResource(R.string.section_group_desc))
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value         = uiState.description,
                    onValueChange = viewModel::onDescriptionChange,
                    modifier      = Modifier.fillMaxWidth(),
                    placeholder   = { Text(stringResource(R.string.group_desc_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine    = false,
                    maxLines      = 3,
                    shape         = RoundedCornerShape(8.dp),
                    colors        = fieldColors()
                )
            }

            item {
                SectionLabel(stringResource(R.string.section_mastery))
                Spacer(Modifier.height(6.dp))
                StrengthSegmentedControl(
                    selected = uiState.strength,
                    onSelect = viewModel::onStrengthChange
                )
            }

            item {
                Button(
                    onClick  = viewModel::saveGroup,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape    = RoundedCornerShape(8.dp),
                    enabled  = uiState.description.isNotBlank(),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor   = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor   = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    if (uiState.isSaved) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text          = if (uiState.isSaved) stringResource(R.string.btn_saved)
                                        else stringResource(R.string.btn_save_group),
                        style         = MaterialTheme.typography.labelLarge,
                        fontWeight    = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                }
            }

            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(4.dp))
                SectionLabel(stringResource(R.string.section_link_verse))
                Spacer(Modifier.height(6.dp))
            }

            item {
                VerseAddRow(
                    surahInput    = uiState.surahInput,
                    ayahInput     = uiState.ayahInput,
                    isError       = uiState.addVerseResult is AddVerseResult.InvalidRef,
                    onSurahChange = viewModel::onSurahInputChange,
                    onAyahChange  = viewModel::onAyahInputChange,
                    onAdd         = viewModel::addVerse
                )
            }

            if (members.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    SectionLabel(stringResource(R.string.linked_verses_header, members.size))
                    Spacer(Modifier.height(6.dp))
                }
                items(members, key = { it.verseId }) { member ->
                    MemberRow(member = member, onRemove = { viewModel.removeMember(member) })
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

// ── Verse add row ─────────────────────────────────────────────────────────────

@Composable
private fun VerseAddRow(
    surahInput: String,
    ayahInput: String,
    isError: Boolean,
    onSurahChange: (String) -> Unit,
    onAyahChange: (String) -> Unit,
    onAdd: () -> Unit
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment     = Alignment.Top
    ) {
        OutlinedTextField(
            value           = surahInput,
            onValueChange   = onSurahChange,
            modifier        = Modifier.weight(1f),
            label           = { Text(stringResource(R.string.label_surah)) },
            placeholder     = { Text(stringResource(R.string.placeholder_surah_range), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            isError         = isError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine      = true,
            shape           = RoundedCornerShape(8.dp),
            colors          = fieldColors()
        )
        OutlinedTextField(
            value           = ayahInput,
            onValueChange   = onAyahChange,
            modifier        = Modifier.weight(1f),
            label           = { Text(stringResource(R.string.label_ayah)) },
            placeholder     = { Text(stringResource(R.string.placeholder_ayah_range), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            isError         = isError,
            supportingText  = if (isError) {
                { Text(stringResource(R.string.error_invalid_ref), color = DifficultyCritical, style = MaterialTheme.typography.labelSmall) }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine      = true,
            shape           = RoundedCornerShape(8.dp),
            colors          = fieldColors()
        )
        Button(
            onClick  = onAdd,
            modifier = Modifier
                .height(56.dp)
                .width(72.dp),
            shape    = RoundedCornerShape(8.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor   = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(stringResource(R.string.btn_add), fontWeight = FontWeight.Black, letterSpacing = 1.sp, fontSize = 12.sp)
        }
    }
}

// ── Member row ────────────────────────────────────────────────────────────────

@Composable
private fun MemberRow(member: MemberVerseRef, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                )
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(
                text       = "${member.surahId}. ${SurahData.nameOf(member.surahId)}  ·  ${stringResource(R.string.ayah_n, member.ayahNumber)}",
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text  = stringResource(R.string.label_page, member.pageNumber),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.cd_remove_verse),
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.size(16.dp)
            )
        }
    }
}

// ── Strength segmented control ────────────────────────────────────────────────

@Composable
private fun StrengthSegmentedControl(
    selected: MasterStrength,
    onSelect: (MasterStrength) -> Unit
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MasterStrength.entries.forEach { s ->
            val isSelected = s == selected
            val color      = strengthColor(s)
            OutlinedButton(
                onClick  = { onSelect(s) },
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(8.dp),
                colors   = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isSelected) color.copy(alpha = 0.15f) else Color.Transparent,
                    contentColor   = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) color else MaterialTheme.colorScheme.outline
                )
            ) {
                Text(
                    text       = s.localizedLabel(),
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text          = text,
        style         = MaterialTheme.typography.labelSmall,
        color         = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight    = FontWeight.Bold,
        letterSpacing = 1.5.sp
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor    = MaterialTheme.colorScheme.outline,
    focusedLabelColor       = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor     = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor             = MaterialTheme.colorScheme.primary,
    focusedTextColor        = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor      = MaterialTheme.colorScheme.onSurface,
    errorBorderColor        = DifficultyCritical,
    errorLabelColor         = DifficultyCritical,
    errorTextColor          = MaterialTheme.colorScheme.onSurface,
    focusedContainerColor   = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    errorContainerColor     = MaterialTheme.colorScheme.surface
)

private fun strengthColor(s: MasterStrength): Color = when (s) {
    MasterStrength.WEAK   -> DifficultyCritical
    MasterStrength.STABLE -> DifficultyStruggled
    MasterStrength.SOLID  -> DifficultySmooth
}
