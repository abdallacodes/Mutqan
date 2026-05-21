package com.example.qmemo.ui.heatmap.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.qmemo.R
import com.example.qmemo.data.local.entity.VerseEntity
import com.example.qmemo.ui.theme.AmiriFontFamily
import com.example.qmemo.util.toCleanQuranicText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AyahPreviewBottomSheet(
    verse: VerseEntity,
    onOpenInMushaf: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.ayah_n, verse.ayahNumber),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = verse.textArabic.toCleanQuranicText(),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = AmiriFontFamily,
                    fontSize = 24.sp,
                    lineHeight = 48.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onOpenInMushaf,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.open_in_mushaf))
                }
                
                OutlinedIconButton(
                    onClick = onShare,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Share, contentDescription = stringResource(R.string.btn_share))
                }
            }
        }
    }
}
