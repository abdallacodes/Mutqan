package com.example.mutqan.ui.heatmap.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mutqan.R
import com.example.mutqan.data.local.entity.UserSubjectEntity
import com.example.mutqan.ui.heatmap.AnchorCardData
import com.example.mutqan.ui.heatmap.SubjectWithText
import com.example.mutqan.ui.theme.*
import com.example.mutqan.ui.components.AyahText

@Composable
fun TimelineSpine(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant
) {
    Canvas(modifier = modifier.width(2.dp).fillMaxHeight()) {
        drawLine(
            color = color,
            start = center.copy(y = 0f),
            end = center.copy(y = size.height),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        )
    }
}

@Composable
fun AnchorCard(
    data: AnchorCardData,
    onAddSubject: () -> Unit,
    onSubjectClick: (UserSubjectEntity) -> Unit,
    onPageClick: (Int) -> Unit,
    onEditSubject: (UserSubjectEntity) -> Unit = {},
    onDeleteSubject: (UserSubjectEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val emerald = Color(0xFF5E8C6E)
    val teal    = Color(0xFF4F8A86)
    val indigo  = Color(0xFF6577A8)
    val amber   = Color(0xFFC9A24B)
    
    val palette = listOf(emerald, teal, indigo, amber)
    val accentColor = palette[(data.unit.hizb - 1) % palette.size]

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Accent bar (Right side in RTL)
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )

            // Main Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                // 1. First Ayah Header
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AyahText(
                        ayahId = data.unit.startAyahId,
                        size = 22.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 2. Thematic Subjects (Rows)
                Text(
                    text = stringResource(R.string.label_groups_header),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    data.subjects.forEach { subjectWithText ->
                        SubjectRow(
                            subjectWithText = subjectWithText,
                            onClickAyah = { onSubjectClick(subjectWithText.entity) },
                            onEdit = { onEditSubject(subjectWithText.entity) },
                            onDelete = { onDeleteSubject(subjectWithText.entity) }
                        )
                    }
                    
                    TextButton(
                        onClick = onAddSubject,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.add_subject), style = MaterialTheme.typography.labelMedium)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 3. Physical Pages
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    data.pages.forEach { pageNum ->
                        PageChip(
                            pageNumber = pageNum,
                            onClick = { onPageClick(pageNum) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 4. Last Ayah Footer
                AyahText(
                    ayahId = data.unit.endAyahId,
                    size = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun SubjectRow(
    subjectWithText: SubjectWithText,
    onClickAyah: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = subjectWithText.entity.subjectText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.cd_more_options),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.btn_edit)) },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
            
            Icon(
                imageVector = if (expanded) {
                    if (isRtl) Icons.Default.ChevronRight else Icons.Default.ChevronLeft
                } else {
                    if (isRtl) Icons.Default.ChevronLeft else Icons.Default.ChevronRight
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
        
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                Surface(
                    onClick = onClickAyah,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        AyahText(ayahId = subjectWithText.entity.startAyahId, size = 20.sp, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.ayah_n, subjectWithText.ayahNumber),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PageChip(
    pageNumber: Int,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(width = 40.dp, height = 32.dp)
            .clickable { onClick() },
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = pageNumber.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
