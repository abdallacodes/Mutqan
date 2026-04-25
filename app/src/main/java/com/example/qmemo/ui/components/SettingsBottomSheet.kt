package com.example.qmemo.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Restore
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
import com.example.qmemo.R
import com.example.qmemo.data.ThemeKey
import com.example.qmemo.ui.theme.MidnightBg
import com.example.qmemo.ui.theme.MidnightPrimary
import com.example.qmemo.ui.theme.ModernDarkBg
import com.example.qmemo.ui.theme.ModernDarkPrimary
import com.example.qmemo.ui.theme.MushafBg
import com.example.qmemo.ui.theme.MushafPrimary
import com.example.qmemo.ui.theme.OLEDBg
import com.example.qmemo.ui.theme.OLEDPrimary

// ── Metadata for each theme preview card ──────────────────────────────────────

private data class ThemeOption(
    val key: ThemeKey,
    val labelResId: Int,
    val bgColor: Color,
    val accentColor: Color,
    val sampleColor: Color   // second swatch dot — secondary visual
)

private val themeOptions = listOf(
    ThemeOption(ThemeKey.AUTO,          R.string.theme_auto,          Color(0xFF1A1A2E), Color(0xFF3DA882), Color(0xFF00C853)),
    ThemeOption(ThemeKey.MODERN_DARK,   R.string.theme_modern_dark,   ModernDarkBg,     ModernDarkPrimary, Color(0xFFE6EDF3)),
    ThemeOption(ThemeKey.OLED_BLACK,    R.string.theme_oled_black,    OLEDBg,           OLEDPrimary,       Color(0xFFF2F2F2)),
    ThemeOption(ThemeKey.MUSHAF_CREAM,  R.string.theme_mushaf_cream,  MushafBg,         MushafPrimary,     Color(0xFF5C4A3A)),
    ThemeOption(ThemeKey.MIDNIGHT_BLUE, R.string.theme_midnight_blue, MidnightBg,       MidnightPrimary,   Color(0xFF7A8FA8)),
)

// ── Bottom sheet ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    currentTheme: ThemeKey,
    onThemeChange: (ThemeKey) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(context))

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportData(it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importData(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is BackupUiEvent.Success -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is BackupUiEvent.Error -> Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest  = onDismiss,
        sheetState        = sheetState,
        containerColor    = MaterialTheme.colorScheme.surface,
        shape             = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                modifier         = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .fillMaxWidth(),
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
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
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
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                themeOptions.forEach { option ->
                    ThemeCard(
                        option    = option,
                        label     = stringResource(option.labelResId),
                        isSelected = option.key == currentTheme,
                        onClick   = {
                            onThemeChange(option.key)
                            onDismiss()
                        },
                        modifier  = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(
                color     = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp
            )
            Spacer(Modifier.height(20.dp))

            // ── Language section ─────────────────────────────────────────────
            SectionLabel(stringResource(R.string.settings_language))
            Spacer(Modifier.height(8.dp))

            val currentLang = AppCompatDelegate.getApplicationLocales()[0]?.language ?: "en"
            LanguageSettingsRow(
                displayName = stringResource(R.string.lang_english),
                tag         = "en",
                isSelected  = currentLang == "en" || currentLang.isEmpty(),
                onDismiss   = onDismiss
            )
            LanguageSettingsRow(
                displayName = stringResource(R.string.lang_arabic),
                tag         = "ar",
                isSelected  = currentLang == "ar",
                onDismiss   = onDismiss
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(
                color     = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp
            )
            Spacer(Modifier.height(20.dp))

            // ── Backup section ─────────────────────────────────────────────
            SectionLabel(stringResource(R.string.settings_data_management))
            Spacer(Modifier.height(8.dp))

            DataActionRow(
                label = stringResource(R.string.btn_backup),
                icon = Icons.Default.Backup,
                onClick = {
                    exportLauncher.launch("qmemo_backup_${System.currentTimeMillis()}.json")
                }
            )

            DataActionRow(
                label = stringResource(R.string.btn_restore),
                icon = Icons.Default.Restore,
                onClick = {
                    importLauncher.launch(arrayOf("application/json"))
                }
            )
        }
    }
}

// ── Section label ─────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text          = text,
        style         = MaterialTheme.typography.labelSmall,
        fontWeight    = FontWeight.Bold,
        color         = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.5.sp
    )
}

// ── Data Action Row ──────────────────────────────────────────────────────────

@Composable
private fun DataActionRow(
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
private fun ThemeCard(
    option: ThemeOption,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier            = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(option.bgColor)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                // Selected indicator — checkmark in the primary accent color
                Icon(
                    imageVector        = Icons.Default.Check,
                    contentDescription = null,
                    tint               = option.accentColor,
                    modifier           = Modifier.size(20.dp)
                )
            } else {
                // Preview: stacked accent + text color dots
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(option.accentColor, CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(option.sampleColor.copy(alpha = 0.6f), CircleShape)
                    )
                }
            }
        }

        Spacer(Modifier.height(5.dp))

        Text(
            text      = label,
            style     = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color      = if (isSelected)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines  = 2,
            lineHeight = 12.sp,
            modifier  = Modifier.fillMaxWidth()
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
            .clickable {
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
