package com.example.qmemo.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.qmemo.R
import com.example.qmemo.data.SurahData
import com.example.qmemo.data.local.AppDatabase
import com.example.qmemo.ui.theme.AmiriFontFamily

/**
 * Carries the minimal data needed to open the Quick Peek sheet for a verse.
 */
data class QuickPeekTarget(
    val verseId: Int,
    val surahId: Int,
    val ayahNumber: Int,
    val pageNumber: Int
)

/**
 * Modal bottom sheet showing the full Arabic text of a single verse.
 * Arabic text is lazy-loaded on open via a single SELECT.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPeekBottomSheet(
    target: QuickPeekTarget,
    onDismiss: () -> Unit,
    onOpenMushaf: ((Int) -> Unit)? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getInstance(context).quranDao() }

    var arabicText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(target.verseId) {
        arabicText = dao.getArabicText(target.verseId)
    }

    val surahName  = SurahData.nameOf(target.surahId)
    val verseLabel = stringResource(R.string.verse_label_format, surahName, target.ayahNumber)

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
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {

            Text(
            text       = verseLabel,
            style      = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.primary,
                textAlign  = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text  = stringResource(R.string.quick_peek_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(28.dp))

            if (arabicText == null) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(28.dp),
                    color       = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            } else {
                val text = arabicText!!
                if (text.isBlank()) {
                    Text(
                        text  = stringResource(R.string.arabic_text_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text     = text,
                        color    = MaterialTheme.colorScheme.onSurface,
                        style    = MaterialTheme.typography.bodyLarge.copy(
                            textDirection = TextDirection.Rtl,
                            fontFamily    = AmiriFontFamily,
                            fontSize      = 26.sp,
                            lineHeight    = 46.sp,
                            textAlign     = TextAlign.Center
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            val resolvedText = arabicText ?: ""
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick  = { copyToClipboard(context, surahName, target.ayahNumber, resolvedText) },
                    modifier = Modifier.weight(1f),
                    enabled  = resolvedText.isNotBlank()
                ) {
                    Icon(
                        imageVector        = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.btn_copy),
                        modifier           = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.btn_copy))
                }

                FilledTonalButton(
                    onClick  = { shareVerse(context, surahName, target.ayahNumber, resolvedText) },
                    modifier = Modifier.weight(1f),
                    enabled  = resolvedText.isNotBlank()
                ) {
                    Icon(
                        imageVector        = Icons.Default.Share,
                        contentDescription = stringResource(R.string.btn_share),
                        modifier           = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.btn_share))
                }
            }

            if (onOpenMushaf != null) {
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = {
                        onOpenMushaf(target.pageNumber)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = stringResource(R.string.open_in_mushaf),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.open_in_mushaf))
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun copyToClipboard(
    context: Context,
    surahName: String,
    ayahNumber: Int,
    text: String
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val label     = context.getString(R.string.verse_label_format, surahName, ayahNumber)
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun shareVerse(
    context: Context,
    surahName: String,
    ayahNumber: Int,
    text: String
) {
    val shareText = context.getString(R.string.share_verse_body, text, surahName, ayahNumber)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type    = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_verse_title)))
}
