package com.example.mutqan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import com.example.mutqan.ui.theme.AmiriFontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mutqan.ui.theme.FolioTheme

/**
 * Reusable full-area empty state: the ayah-end mark, a title in display type and a short
 * description. [icon] is kept so callers do not change; the page itself carries the tone.
 * Drop it into a Box/Scaffold content area that already has the correct padding.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier         = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier            = Modifier.padding(horizontal = 40.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp.withFontScale())
                    .clip(EightPointStarShape)
                    .background(FolioTheme.colors.tonal),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text  = "\u06DD",
                    style = TextStyle(fontFamily = AmiriFontFamily, fontSize = 36.sp, lineHeight = 44.sp),
                    color = FolioTheme.colors.onTonal
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text      = title,
                style     = MaterialTheme.typography.headlineSmall,
                color     = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text      = description,
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
