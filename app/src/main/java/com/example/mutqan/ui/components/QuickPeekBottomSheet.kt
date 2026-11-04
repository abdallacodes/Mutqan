package com.example.mutqan.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.quran.QuranRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Carries the minimal data needed to open the Quick Peek sheet for a verse.
 */
data class QuickPeekTarget(
    val verseId: Int,
    val surahId: Int,
    val ayahNumber: Int,
    val pageNumber: Int
)

/** One thing to do with the verse: an icon with a short word under it. [active] marks a switch that is on. */
data class PeekAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val active: Boolean = false
)

/**
 * The sheet that opens on a verse. The verse's name with copy and share sit at the top, the things to do with it
 * are one row of icons right under that (always in view), and the verse and what is known about it scroll below.
 * [actions] are the screen's own; opening the Mushaf is added when [onOpenMushaf] is given.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPeekBottomSheet(
    target: QuickPeekTarget,
    onDismiss: () -> Unit,
    onOpenMushaf: ((Int) -> Unit)? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    actions: List<PeekAction> = emptyList(),
    /** Extra content shown under the verse text. */
    extraContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    val context = LocalContext.current
    val quran = remember { QuranRepository.getInstance(context) }

    var arabicText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(target.verseId) {
        arabicText = withContext(Dispatchers.IO) { quran.ayahById(target.verseId)?.text }
    }

    val surahName = SurahData.nameOf(target.surahId)
    val verseLabel = stringResource(R.string.verse_label_format, surahName, target.ayahNumber)
    val resolvedText = arabicText ?: ""

    val mushafAction = onOpenMushaf?.let {
        PeekAction(
            icon = Icons.Default.MenuBook,
            label = stringResource(R.string.peek_act_mushaf),
            onClick = { it(target.pageNumber); onDismiss() }
        )
    }
    val all = actions + listOfNotNull(mushafAction)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
        dragHandle = {
            Box(
                modifier = Modifier
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
        // Fixed: who it is, and what can be done with it.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = PaperSpace.Gutter, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = verseLabel,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.dialog_page_title, target.pageNumber),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { copyToClipboard(context, surahName, target.ayahNumber, resolvedText) },
                enabled = resolvedText.isNotBlank()
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.btn_copy), modifier = Modifier.size(22.dp))
            }
            IconButton(
                onClick = { shareVerse(context, surahName, target.ayahNumber, resolvedText) },
                enabled = resolvedText.isNotBlank()
            ) {
                Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.btn_share), modifier = Modifier.size(22.dp))
            }
        }

        if (all.isNotEmpty()) {
            Hairline(Modifier.fillMaxWidth().padding(top = 8.dp, start = PaperSpace.Gutter, end = PaperSpace.Gutter))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                all.forEach { PeekActionItem(it, Modifier.weight(1f)) }
            }
        }
        Hairline(Modifier.fillMaxWidth().padding(start = PaperSpace.Gutter, end = PaperSpace.Gutter))

        // Scrolls: the verse itself and what is known about it.
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PaperSpace.Gutter)
                .padding(top = 20.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (arabicText == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            } else if (resolvedText.isBlank()) {
                Text(
                    text = stringResource(R.string.arabic_text_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                AyahText(
                    ayahId = target.verseId,
                    color = MaterialTheme.colorScheme.onSurface,
                    size = 24.sp,
                    textAlign = TextAlign.Center
                )
            }

            if (extraContent != null) {
                Spacer(Modifier.height(8.dp))
                Column(modifier = Modifier.fillMaxWidth()) { extraContent() }
            }
        }
    }
}

/** An icon over a short word; the icon and word take the action colour when the action is on. */
@Composable
private fun PeekActionItem(action: PeekAction, modifier: Modifier = Modifier) {
    val tint = if (action.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clickable(role = Role.Button, onClick = action.onClick)
            .padding(horizontal = 2.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(action.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(
            text = action.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (action.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
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
