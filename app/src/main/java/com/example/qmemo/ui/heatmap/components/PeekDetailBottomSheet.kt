package com.example.qmemo.ui.heatmap.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.qmemo.R
import com.example.qmemo.ui.theme.AmiriFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeekDetailBottomSheet(
    pageNumber: Int,
    startSnippet: String,
    endSnippet: String,
    onOpenInMushaf: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.dialog_page_title, pageNumber),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            // Start Snippet
            SnippetCard(
                text = "$startSnippet...",
                isStart = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Open in Mushaf Button
            Button(
                onClick = onOpenInMushaf,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.open_in_mushaf),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // End Snippet
            SnippetCard(
                text = "...$endSnippet",
                isStart = false
            )
        }
    }
}

@Composable
private fun SnippetCard(
    text: String,
    isStart: Boolean
) {
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp),
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = AmiriFontFamily,
                    fontSize = 22.sp,
                    lineHeight = 42.sp
                ),
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
            
            // Fading Edge Overlay
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = if (isStart) {
                                // Fade at the left (end of RTL text)
                                listOf(backgroundColor, Color.Transparent, Color.Transparent)
                            } else {
                                // Fade at the right (start of RTL text)
                                listOf(Color.Transparent, Color.Transparent, backgroundColor)
                            },
                            startX = 0f,
                            endX = Float.POSITIVE_INFINITY
                        )
                    )
            )
        }
    }
}
