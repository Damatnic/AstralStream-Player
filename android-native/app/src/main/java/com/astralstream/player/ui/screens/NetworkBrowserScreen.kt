package com.astralstream.player.ui.screens

import android.net.Uri
import android.text.format.Formatter
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.astralstream.player.network.SmbDataSource
import com.astralstream.player.viewmodel.NetworkBrowserViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkBrowserScreen(
    onBackPressed: () -> Unit,
    onPlayVideo: (Uri) -> Unit,
    viewModel: NetworkBrowserViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var showAddServerDialog by remember { mutableStateOf(false) }
    var showCredentialsDialog by remember { mutableStateOf(false) }
    var selectedServer by remember { mutableStateOf<SmbDataSource.SmbServer?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Network Browser")
                        if (uiState.currentPath.isNotEmpty()) {
                            Text(
                                text = uiState.currentPath.substringAfterLast('/'),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.navigationHistory.size > 1) {
                            viewModel.navigateBack()
                        } else {
                            onBackPressed()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddServerDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Server")
                    }
                    IconButton(onClick = { viewModel.discoverServers() }) {
                        Icon(Icons.Default.Search, contentDescription = "Discover")
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.currentServer != null && uiState.currentPath.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.navigateUp() },
                    icon = { Icon(Icons.Default.ArrowUpward, contentDescription = "Up") },
                    text = { Text("Parent") }
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
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Discovering network shares...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                
                uiState.error != null -> {
                    ErrorState(
                        message = uiState.error ?: "An error occurred",
                        onRetry = { viewModel.refresh() }
                    )
                }
                
                uiState.currentServer == null -> {
                    // Server list view
                    if (uiState.servers.isEmpty()) {
                        EmptyState(
                            message = "No network shares found",
                            icon = Icons.Default.Wifi,
                            actionLabel = "Add Server Manually",
                            onAction = { showAddServerDialog = true }
                        )
                    } else {
                        ServerListView(
                            servers = uiState.servers,
                            savedServers = uiState.savedServers,
                            onServerClick = { server ->
                                selectedServer = server
                                if (server.credentials == null) {
                                    showCredentialsDialog = true
                                } else {
                                    viewModel.connectToServer(server)
                                }
                            },
                            onServerLongClick = { server ->
                                viewModel.toggleServerSaved(server)
                            }
                        )
                    }
                }
                
                else -> {
                    // File browser view
                    if (uiState.files.isEmpty()) {
                        EmptyState(
                            message = "No files found",
                            icon = Icons.Default.FolderOpen
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            // Breadcrumb
                            item {
                                uiState.currentServer?.let { server ->
                                    NetworkBreadcrumb(
                                        server = server,
                                        path = uiState.currentPath,
                                        onNavigate = { path ->
                                            viewModel.navigateToPath(path)
                                        }
                                    )
                                }
                            }
                            
                            // File list
                            items(uiState.files) { file ->
                                NetworkFileItem(
                                    file = file,
                                    onClick = {
                                        if (file.isDirectory) {
                                            viewModel.navigateToPath(file.path)
                                        } else if (isVideoFile(file.name)) {
                                            val uri = Uri.parse(
                                                viewModel.getStreamingUrl(file.path)
                                            )
                                            onPlayVideo(uri)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Add server dialog
    if (showAddServerDialog) {
        AddServerDialog(
            onDismiss = { showAddServerDialog = false },
            onAdd = { address, credentials ->
                val server = SmbDataSource.SmbServer(
                    name = address.substringBefore('/'),
                    address = address,
                    credentials = credentials
                )
                viewModel.addServer(server)
                showAddServerDialog = false
            }
        )
    }
    
    // Credentials dialog
    if (showCredentialsDialog && selectedServer != null) {
        CredentialsDialog(
            serverName = selectedServer!!.name,
            onDismiss = { 
                showCredentialsDialog = false
                selectedServer = null
            },
            onConnect = { credentials ->
                val server = selectedServer!!.copy(credentials = credentials)
                viewModel.connectToServer(server)
                showCredentialsDialog = false
                selectedServer = null
            }
        )
    }
}

@Composable
private fun ServerListView(
    servers: List<SmbDataSource.SmbServer>,
    savedServers: List<SmbDataSource.SmbServer>,
    onServerClick: (SmbDataSource.SmbServer) -> Unit,
    onServerLongClick: (SmbDataSource.SmbServer) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (savedServers.isNotEmpty()) {
            item {
                Text(
                    "Saved Servers",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            items(savedServers) { server ->
                ServerCard(
                    server = server,
                    isSaved = true,
                    onClick = { onServerClick(server) },
                    onLongClick = { onServerLongClick(server) }
                )
            }
        }
        
        if (servers.isNotEmpty()) {
            item {
                Text(
                    "Discovered Servers",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            items(servers) { server ->
                ServerCard(
                    server = server,
                    isSaved = false,
                    onClick = { onServerClick(server) },
                    onLongClick = { onServerLongClick(server) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ServerCard(
    server: SmbDataSource.SmbServer,
    isSaved: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSaved) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Computer,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (isSaved) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = server.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (server.credentials != null) {
                    Text(
                        text = "Authenticated as: ${server.credentials.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (isSaved) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "Saved",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun NetworkFileItem(
    file: SmbDataSource.SmbFileInfo,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (file.isDirectory) {
                    Icons.Default.Folder
                } else if (isVideoFile(file.name)) {
                    Icons.Default.VideoFile
                } else {
                    Icons.Default.InsertDriveFile
                },
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = when {
                    file.isDirectory -> MaterialTheme.colorScheme.primary
                    isVideoFile(file.name) -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row {
                    if (!file.isDirectory) {
                        Text(
                            text = Formatter.formatFileSize(context, file.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    if (file.isHidden) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Hidden",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    if (!file.canWrite) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Read-only",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkBreadcrumb(
    server: SmbDataSource.SmbServer,
    path: String,
    onNavigate: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = server.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onNavigate("") }
            )
            
            if (path.isNotEmpty()) {
                val parts = path.split('/').filter { it.isNotEmpty() }
                parts.forEachIndexed { index, part ->
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(16.dp)
                    )
                    
                    Text(
                        text = part,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable {
                            val targetPath = parts.take(index + 1).joinToString("/")
                            onNavigate(targetPath)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddServerDialog(
    onDismiss: () -> Unit,
    onAdd: (String, SmbDataSource.SmbCredentials?) -> Unit
) {
    var address by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var useAuth by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Network Server") },
        text = {
            Column {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Server Address") },
                    placeholder = { Text("192.168.1.100 or computer-name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = useAuth,
                        onCheckedChange = { useAuth = it }
                    )
                    Text("Requires authentication")
                }
                
                AnimatedVisibility(visible = useAuth) {
                    Column {
                        OutlinedTextField(
                            value = domain,
                            onValueChange = { domain = it },
                            label = { Text("Domain (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (address.isNotEmpty()) {
                        val credentials = if (useAuth && username.isNotEmpty()) {
                            SmbDataSource.SmbCredentials(domain, username, password)
                        } else null
                        onAdd(address, credentials)
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CredentialsDialog(
    serverName: String,
    onDismiss: () -> Unit,
    onConnect: (SmbDataSource.SmbCredentials?) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var asGuest by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect to $serverName") },
        text = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = asGuest,
                        onCheckedChange = { asGuest = it }
                    )
                    Text("Connect as Guest")
                }
                
                AnimatedVisibility(visible = !asGuest) {
                    Column {
                        OutlinedTextField(
                            value = domain,
                            onValueChange = { domain = it },
                            label = { Text("Domain (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (asGuest) {
                        onConnect(null)
                    } else if (username.isNotEmpty()) {
                        onConnect(SmbDataSource.SmbCredentials(domain, username, password))
                    }
                }
            ) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EmptyState(
    message: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.FolderOpen,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
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
            
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
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
                Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

private fun isVideoFile(name: String): Boolean {
    val extension = name.substringAfterLast('.', "").lowercase()
    return extension in setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm",
        "m4v", "mpg", "mpeg", "3gp", "3g2", "m2ts", "mts",
        "ts", "vob", "ogv", "rm", "rmvb", "asf", "divx", "f4v"
    )
}