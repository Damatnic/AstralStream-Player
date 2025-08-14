package com.astralstream.player.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.astralstream.player.viewmodel.FileExplorerViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimplifiedFileExplorerScreen(
    viewModel: FileExplorerViewModel = hiltViewModel(),
    onVideoClick: (String) -> Unit,
    onNavigateToSearch: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            MinimalTopBar(
                currentPath = uiState.currentPath,
                onNavigateUp = { viewModel.navigateUp() },
                onNavigateBack = { viewModel.navigateBack() },
                canGoBack = uiState.canGoBack,
                onSearchClick = onNavigateToSearch,
                onSortClick = { showSortMenu = true },
                onFilterClick = { showFilterMenu = true }
            )
        },
        containerColor = Color(0xFF0D0D0D)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                LoadingView()
            } else if (uiState.error != null) {
                ErrorView(
                    error = uiState.error ?: "Unknown error",
                    onRetry = { viewModel.refreshCurrentDirectory() }
                )
            } else {
                FileList(
                    files = uiState.files,
                    onFileClick = { file ->
                        if (file.isDirectory) {
                            viewModel.navigateToPath(file.path)
                        } else {
                            onVideoClick(file.path)
                        }
                    }
                )
            }
            
            // Sort dropdown
            SortDropdown(
                visible = showSortMenu,
                currentOrder = uiState.sortOrder,
                onDismiss = { showSortMenu = false },
                onSortOrderChange = { 
                    viewModel.setSortOrder(it)
                    showSortMenu = false
                }
            )
            
            // Filter dropdown
            FilterDropdown(
                visible = showFilterMenu,
                currentFilter = uiState.fileTypeFilter,
                onDismiss = { showFilterMenu = false },
                onFilterChange = {
                    viewModel.setFileTypeFilter(it)
                    showFilterMenu = false
                }
            )
        }
    }
}

@Composable
private fun MinimalTopBar(
    currentPath: String,
    onNavigateUp: () -> Unit,
    onNavigateBack: () -> Unit,
    canGoBack: Boolean,
    onSearchClick: () -> Unit,
    onSortClick: () -> Unit,
    onFilterClick: () -> Unit
) {
    Surface(
        color = Color(0xFF1A1A1A),
        shadowElevation = 4.dp
    ) {
        Column {
            // Action bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                IconButton(
                    onClick = onNavigateBack,
                    enabled = canGoBack
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (canGoBack) Color.White else Color.Gray
                    )
                }
                
                // Path display
                Text(
                    text = currentPath.substringAfterLast('/'),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Action buttons
                IconButton(onClick = onSearchClick) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.White
                    )
                }
                
                IconButton(onClick = onSortClick) {
                    Icon(
                        Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Sort",
                        tint = Color.White
                    )
                }
                
                IconButton(onClick = onFilterClick) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = "Filter",
                        tint = Color.White
                    )
                }
            }
            
            // Quick access bar (optional)
            QuickAccessBar()
        }
    }
}

@Composable
private fun QuickAccessBar() {
    val quickAccessItems = listOf(
        "Internal" to Icons.Default.Storage,
        "Downloads" to Icons.Default.Download,
        "Movies" to Icons.Default.Movie,
        "Camera" to Icons.Default.PhotoCamera
    )
    
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F0F0F))
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(quickAccessItems) { item ->
            val (label, icon) = item
            QuickAccessChip(
                label = label,
                icon = icon,
                onClick = { /* Navigate to folder */ }
            )
        }
    }
}

@Composable
private fun QuickAccessChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF2A2A2A),
        modifier = Modifier.height(32.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color(0xFF888888),
                modifier = Modifier.size(16.dp)
            )
            Text(
                label,
                color = Color.White,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun FileList(
    files: List<FileExplorerViewModel.FileItem>,
    onFileClick: (FileExplorerViewModel.FileItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(files) { file ->
            FileListItem(
                file = file,
                onClick = { onFileClick(file) }
            )
        }
    }
}

@Composable
private fun FileListItem(
    file: FileExplorerViewModel.FileItem,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail or icon
            ThumbnailView(file)
            
            // File info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = file.name,
                    color = Color.White,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!file.isDirectory) {
                        // Duration
                        if (file.duration > 0) {
                            Text(
                                text = formatDuration(file.duration),
                                color = Color(0xFF888888),
                                fontSize = 13.sp
                            )
                        }
                        
                        // Size
                        Text(
                            text = formatFileSize(file.size),
                            color = Color(0xFF888888),
                            fontSize = 13.sp
                        )
                        
                        // Resolution
                        file.videoInfo?.let { info ->
                            if (info.width > 0 && info.height > 0) {
                                Text(
                                    text = "${info.width}×${info.height}",
                                    color = Color(0xFF888888),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Folder",
                            color = Color(0xFF888888),
                            fontSize = 13.sp
                        )
                    }
                }
            }
            
            // More options
            if (!file.isDirectory) {
                IconButton(
                    onClick = { /* Show options */ },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = Color(0xFF888888),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ThumbnailView(file: FileExplorerViewModel.FileItem) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF2A2A2A)),
        contentAlignment = Alignment.Center
    ) {
        when {
            file.isDirectory -> {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(32.dp)
                )
            }
            file.thumbnailBitmap != null -> {
                Image(
                    bitmap = file.thumbnailBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            else -> {
                Icon(
                    Icons.Default.VideoFile,
                    contentDescription = null,
                    tint = Color(0xFF888888),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ErrorView(
    error: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = Color.Red,
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = error,
            color = Color.White,
            fontSize = 16.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Retry")
        }
    }
}

@Composable
private fun SortDropdown(
    visible: Boolean,
    currentOrder: FileExplorerViewModel.SortOrder,
    onDismiss: () -> Unit,
    onSortOrderChange: (FileExplorerViewModel.SortOrder) -> Unit
) {
    DropdownMenu(
        expanded = visible,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(Color(0xFF2A2A2A))
    ) {
        FileExplorerViewModel.SortOrder.values().forEach { order ->
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (currentOrder == order) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.size(20.dp))
                        }
                        Text(
                            formatSortOrder(order),
                            color = Color.White
                        )
                    }
                },
                onClick = { onSortOrderChange(order) }
            )
        }
    }
}

@Composable
private fun FilterDropdown(
    visible: Boolean,
    currentFilter: FileExplorerViewModel.FileTypeFilter,
    onDismiss: () -> Unit,
    onFilterChange: (FileExplorerViewModel.FileTypeFilter) -> Unit
) {
    DropdownMenu(
        expanded = visible,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(Color(0xFF2A2A2A))
    ) {
        FileExplorerViewModel.FileTypeFilter.values().forEach { filter ->
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (currentFilter == filter) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.size(20.dp))
                        }
                        Text(
                            formatFilter(filter),
                            color = Color.White
                        )
                    }
                },
                onClick = { onFilterChange(filter) }
            )
        }
    }
}

// Helper functions
private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

private fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    
    return when {
        gb >= 1 -> String.format("%.1f GB", gb)
        mb >= 1 -> String.format("%.1f MB", mb)
        kb >= 1 -> String.format("%.0f KB", kb)
        else -> "$bytes B"
    }
}

private fun formatSortOrder(order: FileExplorerViewModel.SortOrder): String {
    return when (order) {
        FileExplorerViewModel.SortOrder.NAME_ASC -> "Name (A-Z)"
        FileExplorerViewModel.SortOrder.NAME_DESC -> "Name (Z-A)"
        FileExplorerViewModel.SortOrder.SIZE_ASC -> "Size (Small first)"
        FileExplorerViewModel.SortOrder.SIZE_DESC -> "Size (Large first)"
        FileExplorerViewModel.SortOrder.DATE_ASC -> "Date (Old first)"
        FileExplorerViewModel.SortOrder.DATE_DESC -> "Date (New first)"
        FileExplorerViewModel.SortOrder.TYPE_ASC -> "Type (A-Z)"
        FileExplorerViewModel.SortOrder.TYPE_DESC -> "Type (Z-A)"
    }
}

private fun formatFilter(filter: FileExplorerViewModel.FileTypeFilter): String {
    return when (filter) {
        FileExplorerViewModel.FileTypeFilter.ALL -> "All Files"
        FileExplorerViewModel.FileTypeFilter.VIDEOS_ONLY -> "Videos Only"
        FileExplorerViewModel.FileTypeFilter.FOLDERS_ONLY -> "Folders Only"
    }
}