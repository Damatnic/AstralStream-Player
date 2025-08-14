package com.astralstream.player.ai.demo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astralstream.player.ai.AstralStreamAICoordinator
import com.astralstream.player.ai.integration.AIEnhancedExoPlayerManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AI Features Demonstration Activity
 * Showcases all AI capabilities of AstralStream
 */
@AndroidEntryPoint
class AIDemoActivity : ComponentActivity() {
    
    @Inject
    lateinit var aiCoordinator: AstralStreamAICoordinator
    
    @Inject
    lateinit var aiPlayerManager: AIEnhancedExoPlayerManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            AIDemoScreen(
                aiCoordinator = aiCoordinator,
                aiPlayerManager = aiPlayerManager,
                onNavigateBack = { finish() }
            )
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        aiPlayerManager.cleanup()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIDemoScreen(
    aiCoordinator: AstralStreamAICoordinator,
    aiPlayerManager: AIEnhancedExoPlayerManager,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isInitialized by remember { mutableStateOf(false) }
    var initializationStatus by remember { mutableStateOf("Not initialized") }
    var selectedMediaFile by remember { mutableStateOf<String?>(null) }
    
    // AI feature states
    val aiEnhancementState by aiPlayerManager.aiEnhancementState.collectAsState()
    
    // AI processing results
    var contentAnalysis by remember { mutableStateOf<Map<String, Any>>(emptyMap()) }
    var smartPlaylists by remember { mutableStateOf<List<String>>(emptyList()) }
    var autoChapters by remember { mutableStateOf<List<String>>(emptyList()) }
    var performanceMetrics by remember { mutableStateOf<Map<String, Any>>(emptyMap()) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AstralStream AI Demo") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // Initialization Section
            item {
                AIInitializationCard(
                    isInitialized = isInitialized,
                    status = initializationStatus,
                    onInitialize = {
                        scope.launch {
                            initializationStatus = "Initializing AI..."
                            val config = AstralStreamAICoordinator.AIConfiguration(
                                enableVideoEnhancement = true,
                                enableAudioEnhancement = true,
                                enableSubtitleGeneration = true,
                                enableContentIntelligence = true,
                                enableSmartOrganization = true,
                                enablePerformanceOptimization = true,
                                offlineMode = true
                            )
                            
                            val result = aiCoordinator.initialize(config)
                            isInitialized = result.success
                            initializationStatus = if (result.success) {
                                "Initialized ${result.initializedFeatures.size}/${result.initializedFeatures.size + result.failedFeatures.size} features"
                            } else {
                                "Failed: ${result.failedFeatures.firstOrNull()?.second ?: "Unknown error"}"
                            }
                        }
                    }
                )
            }
            
            // Media File Selection
            item {
                MediaSelectionCard(
                    selectedFile = selectedMediaFile,
                    onFileSelected = { filePath ->
                        selectedMediaFile = filePath
                        scope.launch {
                            // Load media with AI analysis
                            val uri = Uri.parse(filePath)
                            aiPlayerManager.setMediaItemWithAIAnalysis(uri, "Demo Video")
                            
                            // Get analysis results
                            contentAnalysis = aiPlayerManager.getContentAnalysis()
                            smartPlaylists = aiPlayerManager.getSmartPlaylists()
                            autoChapters = aiPlayerManager.getAutoChapters()
                            performanceMetrics = aiPlayerManager.getAIPerformanceMetrics()
                        }
                    }
                )
            }
            
            // AI Features Status
            item {
                AIFeaturesStatusCard(
                    enhancementState = aiEnhancementState,
                    onFeatureToggle = { feature, enabled ->
                        aiPlayerManager.setAIFeatureEnabled(feature, enabled)
                    }
                )
            }
            
            // Content Analysis Results
            if (contentAnalysis.isNotEmpty()) {
                item {
                    ContentAnalysisCard(analysis = contentAnalysis)
                }
            }
            
            // Smart Playlists
            if (smartPlaylists.isNotEmpty()) {
                item {
                    SmartPlaylistsCard(playlists = smartPlaylists)
                }
            }
            
            // Auto Chapters
            if (autoChapters.isNotEmpty()) {
                item {
                    AutoChaptersCard(chapters = autoChapters)
                }
            }
            
            // Performance Metrics
            if (performanceMetrics.isNotEmpty()) {
                item {
                    PerformanceMetricsCard(metrics = performanceMetrics)
                }
            }
            
            // AI Actions
            if (selectedMediaFile != null) {
                item {
                    AIActionsCard(
                        mediaFile = selectedMediaFile!!,
                        onExportSubtitles = { format ->
                            scope.launch {
                                val success = aiPlayerManager.exportSubtitles(
                                    "/storage/emulated/0/Download/subtitles.$format",
                                    format
                                )
                                // Handle result
                            }
                        },
                        onGenerateChapters = {
                            scope.launch {
                                autoChapters = aiPlayerManager.getAutoChapters()
                            }
                        },
                        onRunAnalysis = {
                            scope.launch {
                                contentAnalysis = aiPlayerManager.getContentAnalysis()
                                performanceMetrics = aiPlayerManager.getAIPerformanceMetrics()
                            }
                        }
                    )
                }
            }
            
            // Real-time Processing Demo
            item {
                RealTimeProcessingCard(
                    isEnabled = aiEnhancementState.realTimeProcessing,
                    onToggle = { enabled ->
                        scope.launch {
                            if (enabled) {
                                // Initialize player with real-time processing
                                aiPlayerManager.initializeAIEnhancedPlayer(enableRealTimeProcessing = true)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AIInitializationCard(
    isInitialized: Boolean,
    status: String,
    onInitialize: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AI System",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isInitialized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                
                if (!isInitialized) {
                    Button(
                        onClick = onInitialize
                    ) {
                        Text("Initialize AI")
                    }
                } else {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Initialized",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun MediaSelectionCard(
    selectedFile: String?,
    onFileSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Media File Selection",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (selectedFile != null) {
                Text(
                    text = "Selected: ${selectedFile.substringAfterLast('/')}",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    text = "No file selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Demo buttons for different file types
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onFileSelected("/storage/emulated/0/Movies/demo_video.mp4") }
                ) {
                    Text("Demo Video")
                }
                
                Button(
                    onClick = { onFileSelected("/storage/emulated/0/Movies/action_movie.mp4") }
                ) {
                    Text("Action Movie")
                }
            }
        }
    }
}

@Composable
fun AIFeaturesStatusCard(
    enhancementState: AIEnhancedExoPlayerManager.AIEnhancementState,
    onFeatureToggle: (AstralStreamAICoordinator.AIFeature, Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "AI Features Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Video Enhancement
            FeatureToggleRow(
                icon = Icons.Default.VideoCameraBack,
                title = "Video Enhancement",
                description = "Real-time upscaling & HDR tone mapping",
                enabled = enhancementState.videoEnhancementEnabled,
                onToggle = { onFeatureToggle(AstralStreamAICoordinator.AIFeature.VIDEO_ENHANCEMENT, it) }
            )
            
            // Audio Enhancement
            FeatureToggleRow(
                icon = Icons.Default.VolumeUp,
                title = "Audio Enhancement",
                description = "Voice isolation & noise reduction",
                enabled = enhancementState.audioEnhancementEnabled,
                onToggle = { onFeatureToggle(AstralStreamAICoordinator.AIFeature.AUDIO_ENHANCEMENT, it) }
            )
            
            // Subtitle Generation
            FeatureToggleRow(
                icon = Icons.Default.Subtitles,
                title = "AI Subtitles",
                description = "Speech recognition & translation",
                enabled = enhancementState.subtitleGenerationEnabled,
                onToggle = { onFeatureToggle(AstralStreamAICoordinator.AIFeature.SUBTITLE_GENERATION, it) }
            )
            
            // Performance Optimization
            FeatureToggleRow(
                icon = Icons.Default.FlashOn,
                title = "Performance AI",
                description = "Adaptive streaming & battery optimization",
                enabled = enhancementState.performanceOptimizationEnabled,
                onToggle = { onFeatureToggle(AstralStreamAICoordinator.AIFeature.PERFORMANCE_OPTIMIZATION, it) }
            )
        }
    }
}

@Composable
fun FeatureToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Switch(
            checked = enabled,
            onCheckedChange = onToggle
        )
    }
}

@Composable
fun ContentAnalysisCard(analysis: Map<String, Any>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Content Analysis",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            analysis.forEach { (key, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = key.replace("_", " ").replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = value.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun SmartPlaylistsCard(playlists: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Smart Playlists (${playlists.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            playlists.forEach { playlist ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PlaylistPlay,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = playlist,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun AutoChaptersCard(chapters: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Auto-Generated Chapters (${chapters.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            chapters.forEachIndexed { index, chapter ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = chapter,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun PerformanceMetricsCard(metrics: Map<String, Any>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Performance Metrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            metrics.forEach { (key, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = key.replace("_", " ").replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = when (value) {
                            is Boolean -> if (value) "Yes" else "No"
                            is Float -> String.format("%.2f", value)
                            else -> value.toString()
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (key.contains("optimized") && value == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun AIActionsCard(
    mediaFile: String,
    onExportSubtitles: (String) -> Unit,
    onGenerateChapters: () -> Unit,
    onRunAnalysis: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "AI Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onExportSubtitles("srt") }
                ) {
                    Icon(Icons.Default.Subtitles, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export SRT")
                }
                
                OutlinedButton(
                    onClick = onGenerateChapters
                ) {
                    Icon(Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Chapters")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = onRunAnalysis,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Run Full AI Analysis")
            }
        }
    }
}

@Composable
fun RealTimeProcessingCard(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Real-Time Processing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isEnabled) "AI processing during playback" else "Process on-demand only",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle
                )
            }
            
            if (isEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "⚠️ Real-time processing increases battery usage",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}