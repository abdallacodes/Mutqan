package com.example.mutqan.ui.mushaf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.domain.AyahGroup
import com.example.mutqan.quran.toCleanQuranicText
import com.example.mutqan.ui.components.localizedLabel
import com.example.mutqan.ui.theme.AmiriFontFamily
import com.example.mutqan.ui.vault.MasterStrength

/** The mutashabihat groups an ayah belongs to, each with the look-alike ayahs it is confused with. */
@Composable
fun ColumnScope.AyahGroupsSection(groups: List<AyahGroup>) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    groups.forEach { entry ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = entry.group.description,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = MasterStrength.fromQuality(entry.group.masterQuality).localizedLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (entry.group.memorizationNotes.isNotBlank()) {
                Text(
                    text = entry.group.memorizationNotes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (entry.others.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.peek_group_also_in),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                entry.others.forEach { other ->
                    Text(
                        text = stringResource(R.string.peek_group_ayah_ref, SurahData.nameOf(other.surah), other.ayah),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = other.text.toCleanQuranicText(),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            textDirection = TextDirection.Rtl,
                            fontFamily = AmiriFontFamily,
                            fontSize = 20.sp,
                            lineHeight = 40.sp
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
