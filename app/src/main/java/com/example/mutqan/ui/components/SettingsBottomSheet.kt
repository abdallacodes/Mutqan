package com.example.mutqan.ui.components

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import com.example.mutqan.domain.BackupCrypto
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.foundation.text.KeyboardOptions
import android.net.Uri
import android.app.KeyguardManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.mutqan.ui.theme.ThemeViewModel
import com.example.mutqan.ui.theme.ThemeViewModelFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.data.ThemeKey
import com.example.mutqan.ui.theme.DifficultySmooth
import com.example.mutqan.quran.DownloadState

// ── Theme choices: follow the system, or Paper in its light or dark version ──────

private val themeChoices = listOf(
    ThemeKey.AUTO to R.string.theme_auto,
    ThemeKey.LIGHT to R.string.theme_light,
    ThemeKey.DARK to R.string.theme_dark
)

// ── Bottom sheet ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    currentTheme: ThemeKey,
    onThemeChange: (ThemeKey) -> Unit,
    onDismiss: () -> Unit,
    onOpenCoverage: () -> Unit = {},
    onOpenSaved: () -> Unit = {},
    onOpenHowItWorks: () -> Unit = {},
    onOpenAudioStorage: () -> Unit = {},
    onOpenFeedback: () -> Unit = {},
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(context))

    var showExportDialog by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf<String?>(null) }
    var importPasswordUri by remember { mutableStateOf<Uri?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val backupGate = rememberBackupGate()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportData(it, exportPassword) }
        exportPassword = null
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importData(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is BackupUiEvent.Success -> Toast.makeText(context, event.message.asString(context), Toast.LENGTH_SHORT).show()
                is BackupUiEvent.Error -> Toast.makeText(context, event.message.asString(context), Toast.LENGTH_LONG).show()
                is BackupUiEvent.NeedsPassword -> importPasswordUri = event.uri
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest  = onDismiss,
        sheetState        = sheetState,
        containerColor    = MaterialTheme.colorScheme.surface,
        shape             = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            val handleLabel = stringResource(R.string.cd_drag_handle)
            Box(
                modifier         = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .fillMaxWidth()
                    .semantics { contentDescription = handleLabel },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
        ) {

            // ── Sheet title ──────────────────────────────────────────────────
            Text(
                text       = stringResource(R.string.settings_title),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color      = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(20.dp))

            // ── Theme section ────────────────────────────────────────────────
            SectionLabel(stringResource(R.string.settings_theme))
            Spacer(Modifier.height(12.dp))

            Row(
                modifier              = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                themeChoices.forEach { (key, labelRes) ->
                    ThemeChoice(
                        label      = stringResource(labelRes),
                        isSelected = key == currentTheme,
                        onClick    = {
                            onThemeChange(key)
                            onDismiss()
                        },
                        modifier   = Modifier.weight(1f)
                    )
                }
            }

            // Colours: the choice recolours this sheet at once, so it stays open.
            val themeViewModel: ThemeViewModel = viewModel(factory = ThemeViewModelFactory(context))
            val palette by themeViewModel.palette.collectAsState()
            val darkNow = when (currentTheme) {
                ThemeKey.AUTO -> isSystemInDarkTheme()
                ThemeKey.LIGHT -> false
                ThemeKey.DARK -> true
            }
            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.settings_palette))
            Spacer(Modifier.height(12.dp))
            PalettePicker(selected = palette, dark = darkNow, onSelect = themeViewModel::setPalette)

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(
                color     = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp
            )
            Spacer(Modifier.height(20.dp))

            // ── Language section ─────────────────────────────────────────────
            SectionLabel(stringResource(R.string.settings_language))
            Spacer(Modifier.height(8.dp))

            val currentLang = LocalConfiguration.current.locales[0].language
            LanguageSettingsRow(
                displayName = stringResource(R.string.lang_english),
                tag         = "en",
                isSelected  = currentLang == "en",
                onDismiss   = onDismiss
            )
            LanguageSettingsRow(
                displayName = stringResource(R.string.lang_arabic),
                tag         = "ar",
                isSelected  = currentLang == "ar" || currentLang.isEmpty(),
                onDismiss   = onDismiss
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(
                color     = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp
            )
            Spacer(Modifier.height(20.dp))

            // ── Account ────────────────────────────────────────────────────
            if (remember { com.example.mutqan.domain.sync.CloudSync.get(context).isConfigured }) {
                AccountSection()
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp
                )
            }

            // ── Backup section ─────────────────────────────────────────────
            SectionLabel(stringResource(R.string.settings_data_management))
            Spacer(Modifier.height(8.dp))

            val downloadState by viewModel.downloadState.collectAsState()

            DataActionRow(
                label = stringResource(R.string.coverage_title),
                icon = Icons.Default.TaskAlt,
                onClick = onOpenCoverage
            )

            DataActionRow(
                label = stringResource(R.string.saved_title),
                icon = Icons.Default.Bookmarks,
                onClick = onOpenSaved
            )

            DownloadActionRow(
                state = downloadState,
                onClick = { viewModel.startMushafDownload() }
            )

            DataActionRow(
                label = stringResource(R.string.btn_backup),
                icon = Icons.Default.Backup,
                onClick = { showExportDialog = true }
            )

            DataActionRow(
                label = stringResource(R.string.btn_restore),
                icon = Icons.Default.Restore,
                // Restoring replaces everything on the device, so the current data is saved first.
                onClick = {
                    backupGate.require { importLauncher.launch(arrayOf("application/json")) }
                }
            )

            // ── Privacy ────────────────────────────────────────────────────
            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.settings_privacy))
            Spacer(Modifier.height(8.dp))

            val appLock by viewModel.appLockEnabled.collectAsState()
            val noScreenLock = stringResource(R.string.lock_needs_screen_lock)
            val setAppLock: (Boolean) -> Unit = { on ->
                val secure = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
                if (on && !secure) Toast.makeText(context, noScreenLock, Toast.LENGTH_LONG).show()
                else viewModel.setAppLock(on)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = appLock, role = Role.Switch, onValueChange = setAppLock)
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_app_lock), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.settings_app_lock_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                PaperSwitch(checked = appLock, onCheckedChange = null)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDeleteDialog = true }
                    .padding(vertical = 14.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.delete_all_row),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // ── About ──────────────────────────────────────────────────────
            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.settings_about))
            Spacer(Modifier.height(8.dp))
            val version = remember(context) {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
            }
            Text(
                text = stringResource(R.string.settings_about_version, stringResource(R.string.app_name), version),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
            Text(
                text = stringResource(R.string.how_link),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().clickable { onOpenHowItWorks() }.heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 10.dp)
            )
            Text(
                text = stringResource(R.string.feedback_link),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().clickable { onOpenFeedback() }.heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 10.dp)
            )
            Text(
                text = stringResource(R.string.audio_storage_link),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().clickable { onOpenAudioStorage() }.heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 10.dp)
            )
            Text(
                text = stringResource(R.string.settings_about_sources),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showExportDialog) {
        PasswordDialog(
            title = stringResource(R.string.backup_password_title),
            body = stringResource(R.string.backup_password_body),
            confirmText = stringResource(R.string.backup_btn_encrypt),
            dismissText = stringResource(R.string.backup_btn_plain),
            requireLength = BackupCrypto.MIN_PASSWORD_LENGTH,
            onConfirm = { password ->
                showExportDialog = false
                exportPassword = password
                exportLauncher.launch("mutqan_backup_${System.currentTimeMillis()}.json")
            },
            onDismiss = {
                showExportDialog = false
                exportPassword = null
                exportLauncher.launch("mutqan_backup_${System.currentTimeMillis()}.json")
            }
        )
    }

    importPasswordUri?.let { uri ->
        PasswordDialog(
            title = stringResource(R.string.restore_password_title),
            body = stringResource(R.string.restore_password_body),
            confirmText = stringResource(R.string.btn_restore),
            dismissText = stringResource(R.string.btn_cancel),
            requireLength = 1,
            onConfirm = { password ->
                importPasswordUri = null
                viewModel.importData(uri, password)
            },
            onDismiss = { importPasswordUri = null }
        )
    }

    if (showDeleteDialog) {
        PaperDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_all_title)) },
            text = { Text(stringResource(R.string.delete_all_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    backupGate.require { viewModel.deleteAllData() }
                }) { Text(stringResource(R.string.delete_all_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }
}

/** Asks for a password; [onConfirm] is enabled once it has [requireLength] characters. */
@Composable
private fun PasswordDialog(
    title: String,
    body: String,
    confirmText: String,
    dismissText: String,
    requireLength: Int,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    PaperDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(body, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.backup_password_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(password) }, enabled = password.length >= requireLength) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText) } }
    )
}

// ── Section label ─────────────────────────────────────────────────────────────

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text          = text,
        style         = MaterialTheme.typography.labelSmall,
        fontWeight    = FontWeight.Bold,
        color         = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ── Data Action Row ──────────────────────────────────────────────────────────

@Composable
private fun DownloadActionRow(
    state: DownloadState,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = state is DownloadState.Idle || state is DownloadState.Failed, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CloudDownload,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.btn_download_mushaf),
                style = MaterialTheme.typography.bodyMedium,
                color = if (state is DownloadState.Completed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.onSurface
            )
            when (state) {
                is DownloadState.Idle -> {
                    if (state.current > 0) {
                        Text(
                            text = stringResource(R.string.mushaf_download_partial, state.current, state.total),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is DownloadState.Downloading -> {
                    Text(
                        text = stringResource(
                            R.string.downloading_mushaf,
                            state.current,
                            state.total,
                            (state.progress * 100).toInt()
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is DownloadState.Completed -> {
                    Text(
                        text = stringResource(R.string.mushaf_download_complete),
                        style = MaterialTheme.typography.labelSmall,
                        color = DifficultySmooth
                    )
                }
                is DownloadState.Failed -> {
                    Text(
                        text = stringResource(R.string.mushaf_download_failed, state.firstPage, state.lastPage),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        if (state is DownloadState.Downloading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else if (state is DownloadState.Completed) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = DifficultySmooth,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun DataActionRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

// ── Theme preview card ────────────────────────────────────────────────────────

@Composable
private fun ThemeChoice(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val border = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(4.dp))
            .border(if (isSelected) 2.dp else 1.dp, border, RoundedCornerShape(4.dp))
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = label,
            style      = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color      = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

// ── Language row (re-implemented inline — same UX as before) ─────────────────

@Composable
private fun LanguageSettingsRow(
    displayName: String,
    tag: String,
    isSelected: Boolean,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, role = Role.RadioButton) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                onDismiss()
            }
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Colored dot indicator
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    shape = CircleShape
                )
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text       = displayName,
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color      = if (isSelected) MaterialTheme.colorScheme.onSurface
                         else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier   = Modifier.weight(1f)
        )
        if (isSelected) {
            Icon(
                imageVector        = Icons.Default.Check,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(18.dp)
            )
        }
    }
}
