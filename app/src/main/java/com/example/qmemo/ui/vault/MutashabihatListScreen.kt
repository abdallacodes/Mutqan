package com.example.qmemo.ui.vault

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MoveUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.qmemo.ui.components.EmptyStateCard
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.qmemo.R
import com.example.qmemo.data.SurahData
import com.example.qmemo.data.local.dao.GroupWithCount
import com.example.qmemo.data.local.entity.VaultFolderEntity
import com.example.qmemo.ui.components.localizedLabel
import com.example.qmemo.ui.theme.DifficultyCritical
import com.example.qmemo.ui.theme.DifficultySmooth
import com.example.qmemo.ui.theme.DifficultyStruggled
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MutashabihatListScreen(
    onAddGroup: (folderId: Int?) -> Unit,
    onViewGroup: (groupId: Int, currentSurahId: Int) -> Unit,
    onEditGroup: (Int) -> Unit,
    onSettingsClick: () -> Unit = {}
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
                is VaultUiEvent.Success -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is VaultUiEvent.Error -> Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    BackHandler(enabled = currentFolderId != null) {
        viewModel.navigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text          = currentFolderName ?: stringResource(R.string.vault_title),
                            style         = MaterialTheme.typography.titleLarge,
                            fontWeight    = FontWeight.Black,
                            letterSpacing = if (currentFolderName == null) 3.sp else 1.sp,
                            color         = MaterialTheme.colorScheme.onBackground,
                            maxLines      = 1,
                            overflow      = TextOverflow.Ellipsis
                        )
                        if (currentFolderName == null) {
                            Text(
                                text          = stringResource(R.string.vault_subtitle),
                                style         = MaterialTheme.typography.labelSmall,
                                color         = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (currentFolderId != null) {
                        IconButton(onClick = { viewModel.navigateBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                },
                actions = {
                    var showMainOverflow by remember { mutableStateOf(false) }

                    IconButton(onClick = { showAddFolderDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = stringResource(R.string.cd_add_folder))
                    }

                    Box {
                        IconButton(onClick = { showMainOverflow = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showMainOverflow,
                            onDismissRequest = { showMainOverflow = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.cd_import_vault)) },
                                onClick = {
                                    showMainOverflow = false
                                    importLauncher.launch(arrayOf("application/json"))
                                },
                                leadingIcon = { Icon(Icons.Default.FileDownload, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.btn_export)) },
                                onClick = {
                                    showMainOverflow = false
                                    exportLauncher.launch("vault_${currentFolderName ?: "all"}_${System.currentTimeMillis()}.json")
                                },
                                leadingIcon = { Icon(Icons.Default.FileUpload, null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Help") },
                                onClick = {
                                    showMainOverflow = false
                                    showHelpDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Info, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings)) },
                                onClick = {
                                    showMainOverflow = false
                                    onSettingsClick()
                                },
                                leadingIcon = { Icon(Icons.Default.Settings, null) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick        = { onAddGroup(currentFolderId) },
                shape          = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor   = MaterialTheme.colorScheme.onPrimary,
                elevation      = FloatingActionButtonDefaults.elevation(0.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_group))
            }
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
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    
                    if (folders.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.label_folders),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        items(folders, key = { "f_${it.id}" }) { folder ->
                            FolderCard(
                                folder = folder,
                                onClick = { viewModel.navigateToFolder(folder) },
                                onRename = { showRenameFolderDialog = folder },
                                onDelete = { viewModel.deleteFolder(folder) },
                                onMove = { movingFolder = folder }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }

                    if (groups.isNotEmpty()) {
                        item {
                            Text(
                                text = if (currentFolderId == null) stringResource(R.string.label_uncategorized) else "GROUPS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        items(groups, key = { "g_${it.group.id}" }) { item ->
                            val currentSurahId = item.surahIds.firstOrNull() ?: 1
                            GroupCard(
                                item     = item,
                                onView   = { onViewGroup(item.group.id, currentSurahId) },
                                onEdit   = { onEditGroup(item.group.id) },
                                onDelete = { viewModel.deleteGroup(item) },
                                onMove   = { movingGroup = item }
                            )
                        }
                    }
                    
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }

            if (showHelpDialog) {
                com.example.qmemo.ui.components.HelpDialog(
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

@Composable
private fun FolderCard(
    folder: VaultFolderEntity,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        onClick  = onClick,
        shape    = RoundedCornerShape(8.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.btn_move)) },
                        onClick = { onMove(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.MoveUp, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.btn_rename)) },
                        onClick = { onRename(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.btn_delete)) },
                        onClick = { onDelete(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Delete, null) }
                    )
                }
            }
        }
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
    AlertDialog(
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
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
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
    
    AlertDialog(
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
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
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
    onMove: () -> Unit
) {
    val quality       = item.group.masterQuality
    val strengthColor = when {
        quality >= 0.70f -> Color(0xFF4CAF50)
        quality >= 0.40f -> Color(0xFFFFC107)
        else          -> Color(0xFFF44336)
    }
    val versesLabel   = if (item.memberCount == 1)
        stringResource(R.string.verses_singular, item.memberCount)
    else
        stringResource(R.string.verses_plural, item.memberCount)

    var showMenu by remember { mutableStateOf(false) }

    Surface(
        onClick  = onView,
        shape    = RoundedCornerShape(8.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant,
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(strengthColor, CircleShape)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = item.group.description,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis
                )
                val surahIds = item.surahIds
                if (surahIds.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = surahIds.joinToString(" · ") { id ->
                            "${id}. ${SurahData.nameOf(id)}"
                        },
                        style    = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape  = RoundedCornerShape(4.dp),
                        color  = MaterialTheme.colorScheme.background,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            text     = versesLabel,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape  = RoundedCornerShape(4.dp),
                        color  = strengthColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, strengthColor.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text      = "${(quality * 100).roundToInt()}%",
                            modifier  = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style     = MaterialTheme.typography.labelSmall,
                            color     = strengthColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cd_view_group)) },
                        onClick = { onView(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Visibility, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cd_edit_group)) },
                        onClick = { onEdit(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.btn_move)) },
                        onClick = { onMove(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.MoveUp, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.btn_delete)) },
                        onClick = { onDelete(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Delete, null) }
                    )
                }
            }
        }
    }
}


private fun strengthColor(quality: Float): Color = when {
    quality >= 0.70f -> DifficultySmooth
    quality >= 0.40f -> DifficultyStruggled
    else          -> DifficultyCritical
}
