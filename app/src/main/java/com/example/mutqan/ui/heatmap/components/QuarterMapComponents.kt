package com.example.mutqan.ui.heatmap.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mutqan.R
import com.example.mutqan.data.local.entity.UserSubjectEntity
import com.example.mutqan.ui.components.AyahText
import com.example.mutqan.ui.components.Folio
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.components.PaperOutlinedButton
import com.example.mutqan.ui.components.SectionLabel
import com.example.mutqan.ui.heatmap.PageWithSurahs
import com.example.mutqan.ui.heatmap.QuarterCardData
import com.example.mutqan.ui.heatmap.adaptiveTextColor
import com.example.mutqan.ui.heatmap.healthColor
import com.example.mutqan.ui.heatmap.pageColor
import com.example.mutqan.ui.theme.FolioTheme
import kotlin.math.roundToInt

// The juz map: eight segments coloured by how well each hizb quarter is held, and below them one compact card per
// quarter. The weakest quarter is the one thing drawn larger and ringed, so the eye lands on where to work first.

/** The eight quarters of the juz as one strip; [weakest] is ringed and taller. Tapping a segment calls [onSelect] with its index. */
@Composable
fun QuarterStrip(
    quarters: List<QuarterCardData>,
    weakest: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        quarters.forEachIndexed { index, q ->
            val average = q.health.average
            val number = q.card.unit.quarterInJuz
            val fill = if (average == null) MaterialTheme.colorScheme.surfaceVariant else healthColor(average)
            val ink = if (average == null) MaterialTheme.colorScheme.onSurfaceVariant else adaptiveTextColor(fill)
            val focal = index == weakest
            val shape = RoundedCornerShape(10.dp)
            val description = if (average == null) stringResource(R.string.map_cd_untracked, number)
            else stringResource(R.string.map_cd_tracked, number, (average * 100).roundToInt())
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(if (focal) 60.dp else 46.dp)
                    .clip(shape)
                    .background(fill, shape)
                    .then(if (focal) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, shape) else Modifier)
                    .clickable { onSelect(index) }
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$number",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = ink
                    )
                    Text(
                        text = if (average == null) stringResource(R.string.untracked_dash)
                        else stringResource(R.string.percent_value, (average * 100).roundToInt()),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = ink.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

/**
 * One hizb quarter as a card. Collapsed it shows the quarter, its pages, how well it is held, the titles of its subjects
 * and one line of its opening ayah; opened it adds the pages (tap to log), the subjects to edit, how the quarter ends and
 * the joint test. [onTestJoint] is null after the last quarter of the Quran, which has no next one.
 */
@Composable
fun QuarterCard(
    data: QuarterCardData,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAddSubject: () -> Unit,
    onEditSubject: (UserSubjectEntity) -> Unit,
    onDeleteSubject: (UserSubjectEntity) -> Unit,
    onSubjectClick: (UserSubjectEntity) -> Unit,
    onPageClick: (PageWithSurahs) -> Unit,
    onTestJoint: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val unit = data.card.unit
    val average = data.health.average
    val accent = if (average == null) FolioTheme.colors.edge else healthColor(average)
    val titles = data.card.subjects.joinToString(" · ") { it.entity.subjectText }

    Folio(
        modifier = modifier.fillMaxWidth(),
        onClick = onToggle,
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(accent))
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.quarter_n, unit.quarterInJuz),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (unit.startPage == unit.endPage) stringResource(R.string.label_page, unit.startPage)
                            else stringResource(R.string.quarter_pages, unit.startPage, unit.endPage),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (average == null) stringResource(R.string.quarter_untracked)
                        else stringResource(R.string.percent_value, (average * 100).roundToInt()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = if (average == null) MaterialTheme.colorScheme.onSurfaceVariant else accent
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(if (expanded) R.string.cd_collapse_card else R.string.cd_expand_card),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp).size(22.dp)
                    )
                }

                // How each page of the quarter is doing, as a row of small squares.
                if (data.pages.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        data.pages.forEach { page ->
                            Box(
                                modifier = Modifier
                                    .size(width = 22.dp, height = 8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (page.stability.isTracked) pageColor(page.stability)
                                        else MaterialTheme.colorScheme.outlineVariant
                                    )
                            )
                        }
                    }
                }

                Text(
                    text = titles.ifEmpty { stringResource(R.string.quarter_no_subjects) },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (titles.isEmpty()) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (titles.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = if (expanded) 3 else 1,
                    overflow = TextOverflow.Ellipsis
                )
                AyahText(
                    ayahId = unit.startAyahId,
                    size = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    maxLines = 1
                )

                AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Hairline()

                        SectionLabel(stringResource(R.string.quarter_pages_header))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            data.pages.forEach { page -> PageTileSmall(page, onClick = { onPageClick(page) }) }
                        }

                        SectionLabel(stringResource(R.string.quarter_subjects_header))
                        data.card.subjects.forEach { subject ->
                            SubjectRow(
                                subjectWithText = subject,
                                onClickAyah = { onSubjectClick(subject.entity) },
                                onEdit = { onEditSubject(subject.entity) },
                                onDelete = { onDeleteSubject(subject.entity) }
                            )
                        }
                        PaperLink(
                            text = stringResource(R.string.add_subject),
                            onClick = onAddSubject
                        )

                        Hairline()
                        SectionLabel(stringResource(R.string.quarter_ends_with))
                        AyahText(
                            ayahId = unit.endAyahId,
                            size = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            maxLines = 2,
                            textAlign = TextAlign.Start
                        )
                        if (onTestJoint != null) {
                            PaperOutlinedButton(
                                text = stringResource(R.string.joint_test_btn),
                                onClick = onTestJoint,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A page of the quarter: its number and strength on its own colour; a tap logs a revision of it. */
@Composable
private fun PageTileSmall(page: PageWithSurahs, onClick: () -> Unit) {
    val ps = page.stability
    val fill = if (ps.isTracked) pageColor(ps) else MaterialTheme.colorScheme.surfaceVariant
    val ink = if (ps.isTracked) adaptiveTextColor(fill) else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = fill,
        modifier = Modifier.size(width = 56.dp, height = 52.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${ps.page}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = ink
            )
            Text(
                text = if (ps.isTracked) stringResource(R.string.percent_value, (ps.score * 100).roundToInt())
                else stringResource(R.string.untracked_dash),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = ink.copy(alpha = 0.8f)
            )
        }
    }
}
