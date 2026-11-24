package com.example.mutqan.ui.vault

import com.example.mutqan.ui.components.SectionHead
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.components.IconMedallion
import com.example.mutqan.ui.components.Folio
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MoveUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.EmptyStateCard
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.data.local.dao.GroupWithCount
import com.example.mutqan.data.local.entity.VaultFolderEntity
import com.example.mutqan.ui.components.HelpDialog
import com.example.mutqan.ui.components.TopBarOverflowMenu
import com.example.mutqan.ui.components.localizedLabel
import com.example.mutqan.ui.theme.DifficultyCritical
import com.example.mutqan.ui.theme.DifficultySmooth
import com.example.mutqan.ui.theme.DifficultyStruggled
import kotlin.math.roundToInt
import com.example.mutqan.ui.components.PaperDialog
import com.example.mutqan.ui.theme.FolioTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MutashabihatListScreen(
    onAddGroup: (folderId: Int?) -> Unit,
    onViewGroup: (groupId: Int, currentSurahId: Int) -> Unit,
    onEditGroup: (Int) -> Unit,
    onStartTest: (folderId: Int?, groupId: Int?) -> Unit,
    onOpenReadyList: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val viewModel: VaultViewModel = viewModel(factory = VaultViewModelFactory(context))
    
    val currentFolderId by viewModel.currentFolderId.collectAsState()
    val currentFolderName by viewModel.currentFolderName.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val groups by viewModel.groups.collectAsState()

    var showAddFolderDialog by remember { mutableStateOf(false) }
    var showRenameFolderDialog by remember { mutableStateOf<VaultFolderEntity?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    
    // Move state
    var movingFolder by remember { mutableStateOf<VaultFolderEntity?>(null) }
    var movingGroup by remember { mutableStateOf<GroupWithCount?>(null) }

    // A delete asks first.
    var deletingFolder by remember { mutableStateOf<VaultFolderEntity?>(null) }
    var deletingGroup by remember { mutableStateOf<GroupWithCount?>(null) }
    
    var showHelpDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportVault(it, currentFolderId) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { 
            pendingImportUri = it
            showImportDialog = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is VaultUiEvent.Success -> Toast.makeText(context, event.message.asString(context), Toast.LENGTH_SHORT).show()
                is VaultUiEvent.Error -> Toast.makeText(context, event.message.asString(context), Toast.LENGTH_LONG).show()
            }
        }
    }

    BackHandler(enabled = currentFolderId != null) {
        viewModel.navigateBack()
    }

    Scaffold(
        topBar = {
            PaperHeader(
                title = currentFolderName ?: stringResource(R.string.vault_title),
                subtitle = if (currentFolderName == null) stringResource(R.string.vault_subtitle) else null,
                onBack = if (currentFolderId != null) { { viewModel.navigateBack() } } else onBack,
                actions = {
                    if (currentFolderId == null) {
                        IconButton(onClick = onOpenReadyList) {
                            Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = stringResource(R.string.cd_ready_list))
                        }
                    }
                    IconButton(onClick = { showAddFolderDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = stringResource(R.string.cd_add_folder))
                    }
                    TopBarOverflowMenu(
                        onHelpClick = { showHelpDialog = true },
                        onSettingsClick = onSettingsClick,
                        extraItems = { dismissMenu ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.cd_import_vault)) },
                                onClick = {
                                    dismissMenu()
                                    importLauncher.launch(arrayOf("application/json"))
                                },
                                leadingIcon = { Icon(Icons.Default.FileDownload, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.btn_export)) },
                                onClick = {
                                    dismissMenu()
                                    exportLauncher.launch("vault_${currentFolderName ?: "all"}_${System.currentTimeMillis()}.json")
                                },
                                leadingIcon = { Icon(Icons.Default.FileUpload, null) }
                            )
                        }
                    )
                }
            )
        },
        floatingActionButton = {
            PaperButton(
                text = stringResource(R.string.cd_add_group),
                onClick = { onAddGroup(currentFolderId) },
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->

        Box(modifier = Modifier.fillMaxSize()) {
            if (folders.isEmpty() && groups.isEmpty()) {
                EmptyStateCard(
                    icon        = Icons.Default.Inbox,
                    title       = stringResource(R.string.empty_vault_title),
                    description = stringResource(R.string.empty_vault_body),
                    modifier    = Modifier.padding(innerPadding)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    if (folders.isNotEmpty()) {
                        item(key = "folders-head") { SectionHead(title = stringResource(R.string.label_folders), first = true) }
                        items(folders, key = { "f_${it.id}" }) { folder ->
                            FolderCard(
                                folder = folder,
                                onClick = { viewModel.navigateToFolder(folder) },
                                onRename = { showRenameFolderDialog = folder },
                                onDelete = { deletingFolder = folder },
                                onMove = { movingFolder = folder },
                                onTest = { onStartTest(folder.id, null) }
                            )
                        }
                    }

                    if (groups.isNotEmpty()) {
                        item(key = "groups-head") {
                            SectionHead(
                                // At the top with folders beside them, the groups in none of them.
                                title = stringResource(if (currentFolderId == null && folders.isNotEmpty()) R.string.label_uncategorized else R.string.label_groups_header),
                                first = folders.isEmpty()
                            )
                        }
                        items(groups, key = { "g_${it.group.id}" }) { item ->
                            val currentSurahId = item.surahIds.firstOrNull() ?: 1
                            GroupCard(
                                item     = item,
                                onView   = { onViewGroup(item.group.id, currentSurahId) },
                                onEdit   = { onEditGroup(item.group.id) },
                                onDelete = { deletingGroup = item },
                                onMove   = { movingGroup = item },
                                onTest   = { onStartTest(null, item.group.id) }
                            )
                        }
                    }
                }
            }

            if (showHelpDialog) {
                com.example.mutqan.ui.components.HelpDialog(
                    title = stringResource(R.string.help_vault_title),
                    description = stringResource(R.string.help_vault_desc),
                    onDismiss = { showHelpDialog = false }
                )
            }
        }
    }

    if (showAddFolderDialog) {
        FolderNameDialog(
            title = stringResource(R.string.dialog_new_folder_title),
            onDismiss = { showAddFolderDialog = false },
            onConfirm = { 
                viewModel.addFolder(it)
                showAddFolderDialog = false
            }
        )
    }

    if (showRenameFolderDialog != null) {
        FolderNameDialog(
            title = stringResource(R.string.dialog_rename_folder_title),
            initialName = showRenameFolderDialog!!.name,
            onDismiss = { showRenameFolderDialog = null },
            onConfirm = { 
                viewModel.renameFolder(showRenameFolderDialog!!, it)
                showRenameFolderDialog = null
            }
        )
    }

    if (showImportDialog) {
        FolderNameDialog(
            title = stringResource(R.string.dialog_import_vault_title),
            onDismiss = { 
                showImportDialog = false
                pendingImportUri = null
            },
            onConfirm = { 
                pendingImportUri?.let { uri -> viewModel.importVault(uri, it) }
                showImportDialog = false
                pendingImportUri = null
            }
        )
    }

    deletingFolder?.let { folder ->
        PaperDialog(
            onDismissRequest = { deletingFolder = null },
            title = { Text(stringResource(R.string.vault_delete_folder_title)) },
            text = { Text(stringResource(R.string.vault_delete_folder_body, folder.name)) },
            confirmButton = {
                PaperLink(text = stringResource(R.string.btn_delete), onClick = {
                    deletingFolder = null
                    viewModel.deleteFolder(folder)
                })
            },
            dismissButton = { PaperLink(text = stringResource(R.string.btn_cancel), onClick = { deletingFolder = null }, soft = true) }
        )
    }

    deletingGroup?.let { item ->
        PaperDialog(
            onDismissRequest = { deletingGroup = null },
            title = { Text(stringResource(R.string.vault_delete_group_title)) },
            text = { Text(stringResource(R.string.vault_delete_group_body, item.group.description)) },
            confirmButton = {
                PaperLink(text = stringResource(R.string.btn_delete), onClick = {
                    deletingGroup = null
                    viewModel.deleteGroup(item)
                })
            },
            dismissButton = { PaperLink(text = stringResource(R.string.btn_cancel), onClick = { deletingGroup = null }, soft = true) }
        )
    }

    // Move Dialogs
    movingFolder?.let { folder ->
        MoveItemDialog(
            viewModel = viewModel,
            onDismiss = { movingFolder = null },
            onConfirm = { targetId ->
                viewModel.moveFolder(folder, targetId)
                movingFolder = null
            },
            excludeId = folder.id
        )
    }

    movingGroup?.let { item ->
        MoveItemDialog(
            viewModel = viewModel,
            onDismiss = { movingGroup = null },
            onConfirm = { targetId ->
                viewModel.moveGroup(item.group, targetId)
                movingGroup = null
            }
        )
    }
}

private var pendingImportUri: android.net.Uri? = null

/**
 * A folder or a group in the vault, in the look of the app's hub rows: a raised card with an icon, a name and a line
 * under it, an optional [note], then the test button and a menu of what else can be done with it.
 */
@Composable
private fun VaultRow(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    onClick: () -> Unit,
    onTest: () -> Unit,
    note: (@Composable () -> Unit)? = null,
    menu: @Composable ColumnScope.(close: () -> Unit) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Folio(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter, vertical = 5.dp),
        onClick = onClick,
        contentPadding = PaddingValues(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconMedallion(icon)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            note?.invoke()
            Row {
                IconButton(onClick = onTest) {
                    Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.cd_start_test), tint = MaterialTheme.colorScheme.primary)
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more_options), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) { menu { showMenu = false } }
                }
            }
        }
    }
}

@Composable
private fun FolderCard(
    folder: VaultFolderEntity,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onTest: () -> Unit
) {
    VaultRow(
        title = folder.name,
        subtitle = null,
        icon = Icons.Default.Folder,
        onClick = onClick,
        onTest = onTest
    ) { close ->
        DropdownMenuItem(
            text = { Text(stringResource(R.string.btn_move)) },
            onClick = { close(); onMove() },
            leadingIcon = { Icon(Icons.Default.MoveUp, null) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.btn_rename)) },
            onClick = { close(); onRename() },
            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error) },
            onClick = { close(); onDelete() },
            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
        )
    }
}

@Composable
private fun FolderNameDialog(
    title: String,
    initialName: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    PaperDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(stringResource(R.string.placeholder_folder_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            PaperLink(text = stringResource(R.string.btn_save), onClick = { if (name.isNotBlank()) onConfirm(name.trim()) })
        },
        dismissButton = {
            PaperLink(text = stringResource(R.string.btn_cancel), onClick = onDismiss, soft = true)
        }
    )
}

@Composable
private fun MoveItemDialog(
    viewModel: VaultViewModel,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit,
    excludeId: Int? = null
) {
    // For simplicity, we'll show a flat list of all folders (excluding the current item)
    // plus a "Vault (Root)" option.
    val dbFolders by viewModel.folders.collectAsState()
    
    PaperDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_move_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().height(300.dp)
            ) {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        MoveTargetRow(
                            name = stringResource(R.string.label_root_folder),
                            isSelected = false,
                            onClick = { onConfirm(null) }
                        )
                    }
                    items(dbFolders.filter { it.id != excludeId }) { folder ->
                        MoveTargetRow(
                            name = folder.name,
                            isSelected = false,
                            onClick = { onConfirm(folder.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            PaperLink(text = stringResource(R.string.btn_cancel), onClick = onDismiss, soft = true)
        }
    )
}

@Composable
private fun MoveTargetRow(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = name, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun GroupCard(
    item: GroupWithCount,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onTest: () -> Unit
) {
    val quality = item.group.masterQuality
    val color = strengthColor(quality)
    val verses = pluralStringResource(R.plurals.count_ayahs, item.memberCount, item.memberCount)
    val surahs = item.surahIds.joinToString(stringResource(R.string.scope_list_separator)) { SurahData.nameOf(it) }
    VaultRow(
        title = item.group.description,
        subtitle = if (surahs.isEmpty()) verses else "$verses · $surahs",
        icon = Icons.Outlined.Layers,
        onClick = onView,
        onTest = onTest,
        note = {
            Text(
                text = stringResource(R.string.percent_value, (quality * 100).roundToInt()),
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    ) { close ->
        DropdownMenuItem(
            text = { Text(stringResource(R.string.cd_view_group)) },
            onClick = { close(); onView() },
            leadingIcon = { Icon(Icons.Default.Visibility, null) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.cd_edit_group)) },
            onClick = { close(); onEdit() },
            leadingIcon = { Icon(Icons.Default.Edit, null) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.btn_move)) },
            onClick = { close(); onMove() },
            leadingIcon = { Icon(Icons.Default.MoveUp, null) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error) },
            onClick = { close(); onDelete() },
            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
        )
    }
}


@Composable
private fun strengthColor(quality: Float): Color = when {
    quality >= 0.70f -> DifficultySmooth
    quality >= 0.40f -> DifficultyStruggled
    else          -> DifficultyCritical
}
