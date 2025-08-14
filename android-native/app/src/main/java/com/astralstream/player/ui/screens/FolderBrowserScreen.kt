package com.astralstream.player.ui.screens

import android.content.Context
import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.astralstream.player.viewmodel.FileExplorerViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderBrowserScreen(
    onBackPressed: () -> Unit,
    onPlayVideo: (Uri) -> Unit,
    onNavigateToSettings: () -> Unit = {},
    viewModel: FileExplorerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp)
            ) {
                Text(
                    text = "Quick Access",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
                HorizontalDivider()
                
                uiState.quickAccessPaths.forEach { quickAccess ->
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                imageVector = getQuickAccessIcon(quickAccess.icon),
                                contentDescription = null
                            )
                        },
                        label = { Text(quickAccess.name) },
                        selected = uiState.currentPath == quickAccess.path,
                        onClick = {
                            viewModel.navigateToPath(quickAccess.path)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                HorizontalDivider()
                
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                    selected = false,
                    onClick = { 
                        scope.launch { drawerState.close() }
                        onNavigateToSettings()
                    },
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                if (searchActive) {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = { 
                            searchQuery = it
                            viewModel.searchFiles(it)
                        },
                        onSearch = { viewModel.searchFiles(it) },
                        active = true,
                        onActiveChange = { 
                            searchActive = it
                            if (!it) {
                                searchQuery = ""
                                viewModel.searchFiles("")
                            }
                        },
                        placeholder = { Text("Search files...") },
                        leadingIcon = {
                            IconButton(onClick = { 
                                searchActive = false
                                searchQuery = ""
                                viewModel.searchFiles("")
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {}
                } else {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = "File Browser",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = uiState.currentPath.substringAfterLast('/'),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            if (uiState.canGoBack) {
                                IconButton(onClick = { viewModel.navigateBack() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            } else {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = { searchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                            
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                                }
                                SortDropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false },
                                    currentSortOrder = uiState.sortOrder,
                                    onSortOrderChange = { 
                                        viewModel.setSortOrder(it)
                                        showSortMenu = false
                                    }
                                )
                            }
                            
                            Box {
                                IconButton(onClick = { showFilterMenu = true }) {
                                    Icon(Icons.Default.FilterList, contentDescription = "Filter")
                                }
                                FilterDropdownMenu(
                                    expanded = showFilterMenu,
                                    onDismissRequest = { showFilterMenu = false },
                                    currentFilter = uiState.fileTypeFilter,
                                    showHiddenFiles = uiState.showHiddenFiles,
                                    onFilterChange = { 
                                        viewModel.setFileTypeFilter(it)
                                        showFilterMenu = false
                                    },
                                    onToggleHiddenFiles = {
                                        viewModel.toggleHiddenFiles()
                                    }
                                )
                            }
                            
                            IconButton(onClick = { viewModel.refreshCurrentDirectory() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                        }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    
                    uiState.error != null -> {
                        ErrorState(
                            message = uiState.error ?: "An error occurred",
                            onRetry = { viewModel.refreshCurrentDirectory() }
                        )
                    }
                    
                    uiState.files.isEmpty() -> {
                        EmptyState(
                            message = if (searchQuery.isNotEmpty()) {
                                "No files found for \"$searchQuery\""
                            } else {
                                "No files found in this directory"
                            }
                        )
                    }
                    
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            // Breadcrumb navigation
                            item {
                                BreadcrumbNavigation(
                                    currentPath = uiState.currentPath,
                                    onNavigate = { path -> viewModel.navigateToPath(path) }
                                )
                            }
                            
                            // File list
                            items(uiState.files, key = { it.path }) { file ->
                                FileListItem(
                                    file = file,
                                    onClick = {
                                        if (file.isDirectory) {
                                            viewModel.navigateToPath(file.path)
                                        } else {
                                            viewModel.playVideo(file)
                                            onPlayVideo(File(file.path).toUri())
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                
                // Floating Action Button for parent directory
                if (!uiState.currentPath.equals("/storage/emulated/0", ignoreCase = true)) {
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.navigateUp() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Go Up")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Parent Directory")
                    }
                }
            }
        }
    }
}

@Composable
private fun BreadcrumbNavigation(
    currentPath: String,
    onNavigate: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val pathParts = currentPath.split("/").filter { it.isNotEmpty() }
        pathParts.forEachIndexed { index, part ->
            item {
                TextButton(
                    onClick = {
                        val targetPath = "/" + pathParts.take(index + 1).joinToString("/")
                        onNavigate(targetPath)
                    }
                ) {
                    Text(
                        text = if (part == "emulated") "Internal" else part,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                if (index < pathParts.size - 1) {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListItem(
    file: FileExplorerViewModel.FileItem,
    onClick: () -> Unit,
    viewModel: FileExplorerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showMoreOptionsMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf(file.name) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showOptionsMenu = true }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail or icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (file.isDirectory) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else if (file.thumbnailBitmap != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(file.thumbnailBitmap)
                            .crossfade(true)
                            .build(),
                        contentDescription = file.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (file.thumbnailUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(file.thumbnailUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = file.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.VideoFile,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // File info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!file.isDirectory) {
                        // File size
                        Text(
                            text = Formatter.formatFileSize(context, file.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        // Duration for videos
                        if (file.duration > 0) {
                            Text(
                                text = "• ${formatDuration(file.duration)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // Video resolution
                        file.videoInfo?.let { info ->
                            Text(
                                text = "• ${info.width}x${info.height}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // Last modified
                    Text(
                        text = DateUtils.getRelativeTimeSpanString(
                            file.lastModified,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS
                        ).toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // More options
            Box {
                IconButton(onClick = { showMoreOptionsMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More options",
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                FileOptionsMenu(
                    expanded = showMoreOptionsMenu,
                    onDismissRequest = { showMoreOptionsMenu = false },
                    file = file,
                    onPlayVideo = { onClick() },
                    onDelete = { 
                        showDeleteDialog = true
                        showMoreOptionsMenu = false
                    },
                    onRename = {
                        showRenameDialog = true
                        showMoreOptionsMenu = false
                    },
                    onShare = {
                        shareFile(context, file)
                        showMoreOptionsMenu = false
                    },
                    onDetails = {
                        // Show file details
                        showMoreOptionsMenu = false
                    }
                )
            }
        }
    }
    
    // Show options dialog on long click
    if (showOptionsMenu) {
        AlertDialog(
            onDismissRequest = { showOptionsMenu = false },
            title = { Text(file.name) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            onClick()
                            showOptionsMenu = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Play")
                        }
                    }
                    TextButton(
                        onClick = {
                            showRenameDialog = true
                            showOptionsMenu = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Rename")
                        }
                    }
                    TextButton(
                        onClick = {
                            showDeleteDialog = true
                            showOptionsMenu = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete")
                        }
                    }
                    TextButton(
                        onClick = {
                            shareFile(context, file)
                            showOptionsMenu = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOptionsMenu = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename File") },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = { Text("New name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFileName.isNotBlank() && newFileName != file.name) {
                            scope.launch {
                                val success = renameFile(file, newFileName)
                                if (success) {
                                    viewModel.refreshCurrentDirectory()
                                }
                            }
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showRenameDialog = false
                    newFileName = file.name
                }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete File") },
            text = { Text("Are you sure you want to delete \"${file.name}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val success = deleteFile(file)
                            if (success) {
                                viewModel.refreshCurrentDirectory()
                            }
                        }
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun FileOptionsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    file: FileExplorerViewModel.FileItem,
    onPlayVideo: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDetails: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        if (!file.isDirectory) {
            DropdownMenuItem(
                text = { Text("Play") },
                onClick = onPlayVideo,
                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
            )
            HorizontalDivider()
        }
        
        DropdownMenuItem(
            text = { Text("Rename") },
            onClick = onRename,
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
        )
        
        DropdownMenuItem(
            text = { Text("Delete") },
            onClick = onDelete,
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
        )
        
        DropdownMenuItem(
            text = { Text("Share") },
            onClick = onShare,
            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
        )
        
        DropdownMenuItem(
            text = { Text("Details") },
            onClick = onDetails,
            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
        )
    }
}

@Composable
private fun SortDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    currentSortOrder: FileExplorerViewModel.SortOrder,
    onSortOrderChange: (FileExplorerViewModel.SortOrder) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        Text(
            "Sort by",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
        HorizontalDivider()
        
        FileExplorerViewModel.SortOrder.values().forEach { sortOrder ->
            DropdownMenuItem(
                text = { Text(getSortOrderLabel(sortOrder)) },
                onClick = { onSortOrderChange(sortOrder) },
                leadingIcon = {
                    if (currentSortOrder == sortOrder) {
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                }
            )
        }
    }
}

@Composable
private fun FilterDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    currentFilter: FileExplorerViewModel.FileTypeFilter,
    showHiddenFiles: Boolean,
    onFilterChange: (FileExplorerViewModel.FileTypeFilter) -> Unit,
    onToggleHiddenFiles: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        Text(
            "Filter",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
        HorizontalDivider()
        
        FileExplorerViewModel.FileTypeFilter.values().forEach { filter ->
            DropdownMenuItem(
                text = { Text(getFilterLabel(filter)) },
                onClick = { onFilterChange(filter) },
                leadingIcon = {
                    if (currentFilter == filter) {
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                }
            )
        }
        
        HorizontalDivider()
        
        DropdownMenuItem(
            text = { Text("Show hidden files") },
            onClick = onToggleHiddenFiles,
            leadingIcon = {
                Checkbox(
                    checked = showHiddenFiles,
                    onCheckedChange = null
                )
            }
        )
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%02d:%02d", minutes, secs)
    }
}

private fun getSortOrderLabel(sortOrder: FileExplorerViewModel.SortOrder): String {
    return when (sortOrder) {
        FileExplorerViewModel.SortOrder.NAME_ASC -> "Name (A-Z)"
        FileExplorerViewModel.SortOrder.NAME_DESC -> "Name (Z-A)"
        FileExplorerViewModel.SortOrder.SIZE_ASC -> "Size (Smallest)"
        FileExplorerViewModel.SortOrder.SIZE_DESC -> "Size (Largest)"
        FileExplorerViewModel.SortOrder.DATE_ASC -> "Date (Oldest)"
        FileExplorerViewModel.SortOrder.DATE_DESC -> "Date (Newest)"
        FileExplorerViewModel.SortOrder.TYPE_ASC -> "Type (A-Z)"
        FileExplorerViewModel.SortOrder.TYPE_DESC -> "Type (Z-A)"
    }
}

private fun getFilterLabel(filter: FileExplorerViewModel.FileTypeFilter): String {
    return when (filter) {
        FileExplorerViewModel.FileTypeFilter.ALL -> "All Files"
        FileExplorerViewModel.FileTypeFilter.VIDEOS_ONLY -> "Videos Only"
        FileExplorerViewModel.FileTypeFilter.FOLDERS_ONLY -> "Folders Only"
    }
}

private fun getQuickAccessIcon(iconName: String): ImageVector {
    return when (iconName) {
        "storage" -> Icons.Default.Storage
        "download" -> Icons.Default.Download
        "movie" -> Icons.Default.Movie
        "camera" -> Icons.Default.CameraAlt
        "document" -> Icons.Default.Description
        else -> Icons.Default.Folder
    }
}

// File operation helper functions
private suspend fun renameFile(file: FileExplorerViewModel.FileItem, newName: String): Boolean {
    return try {
        val sourceFile = File(file.path)
        val destFile = File(sourceFile.parentFile, newName)
        sourceFile.renameTo(destFile)
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

private suspend fun deleteFile(file: FileExplorerViewModel.FileItem): Boolean {
    return try {
        val fileToDelete = File(file.path)
        if (fileToDelete.isDirectory) {
            fileToDelete.deleteRecursively()
        } else {
            fileToDelete.delete()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

private fun shareFile(context: Context, file: FileExplorerViewModel.FileItem) {
    try {
        val fileUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            File(file.path)
        )
        
        val shareIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            type = getMimeType(file.name)
            putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(
            android.content.Intent.createChooser(shareIntent, "Share ${file.name}")
        )
    } catch (e: Exception) {
        e.printStackTrace()
        android.widget.Toast.makeText(context, "Failed to share file", android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun getMimeType(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "mp4", "avi", "mkv", "mov", "wmv" -> "video/*"
        "mp3", "wav", "flac", "aac" -> "audio/*"
        "jpg", "jpeg", "png", "gif", "bmp" -> "image/*"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        else -> "*/*"
    }
}