package com.astralstream.player.player

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.astralstream.player.player.viewmodel.PlayerViewModel

@Composable
fun QuickSettingsMenu(
    viewModel: PlayerViewModel,
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    if (isVisible) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(16.dp)),
                color = Color.Black.copy(alpha = 0.95f)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Quick Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Playback Speed
                    PlaybackSpeedSection(viewModel)
                    
                    Divider(modifier = Modifier.padding(vertical = 12.dp))
                    
                    // Audio & Subtitle Tracks
                    TrackSelectionSection(viewModel)
                    
                    Divider(modifier = Modifier.padding(vertical = 12.dp))
                    
                    // Video Quality
                    VideoQualitySection(viewModel)
                    
                    Divider(modifier = Modifier.padding(vertical = 12.dp))
                    
                    // Display Settings
                    DisplaySettingsSection(viewModel)
                    
                    Divider(modifier = Modifier.padding(vertical = 12.dp))
                    
                    // Advanced Settings
                    AdvancedSettingsSection(viewModel)
                }
            }
        }
    }
}

@Composable
private fun PlaybackSpeedSection(viewModel: PlayerViewModel) {
    val playerState by viewModel.playerState.collectAsState()
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    
    Column {
        Text(
            text = "Playback Speed",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            speeds.forEach { speed ->
                val isSelected = playerState.playbackSpeed == speed
                
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.setPlaybackSpeed(speed) },
                    label = {
                        Text(
                            text = if (speed == 1.0f) "Normal" else "${speed}x",
                            color = if (isSelected) Color.Black else Color.White
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.Black,
                        containerColor = Color.Gray.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
}

@Composable
private fun TrackSelectionSection(viewModel: PlayerViewModel) {
    var expandedAudio by remember { mutableStateOf(false) }
    var expandedSubtitle by remember { mutableStateOf(false) }
    
    Column {
        Text(
            text = "Audio & Subtitles",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Audio Track
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expandedAudio = !expandedAudio },
            colors = CardDefaults.cardColors(
                containerColor = Color.Gray.copy(alpha = 0.2f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.VolumeUp,
                        contentDescription = "Audio",
                        tint = Color.White
                    )
                    Column {
                        Text(
                            text = "Audio Track",
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "English (Default)",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
                Icon(
                    if (expandedAudio) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }
        
        AnimatedVisibility(visible = expandedAudio) {
            Column(
                modifier = Modifier.padding(start = 16.dp, top = 8.dp)
            ) {
                AudioTrackOption("English (Default)", true) {}
                AudioTrackOption("Spanish", false) {}
                AudioTrackOption("French", false) {}
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Subtitle Track
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expandedSubtitle = !expandedSubtitle },
            colors = CardDefaults.cardColors(
                containerColor = Color.Gray.copy(alpha = 0.2f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Subtitles,
                        contentDescription = "Subtitles",
                        tint = Color.White
                    )
                    Column {
                        Text(
                            text = "Subtitles",
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Off",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
                Icon(
                    if (expandedSubtitle) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }
        
        AnimatedVisibility(visible = expandedSubtitle) {
            Column(
                modifier = Modifier.padding(start = 16.dp, top = 8.dp)
            ) {
                SubtitleOption("Off", true) {}
                SubtitleOption("English", false) {}
                SubtitleOption("Spanish", false) {}
                SubtitleOption("Load from file...", false) {}
            }
        }
    }
}

@Composable
private fun VideoQualitySection(viewModel: PlayerViewModel) {
    val qualities = listOf("Auto", "2160p", "1080p", "720p", "480p", "360p")
    var selectedQuality by remember { mutableStateOf("Auto") }
    
    Column {
        Text(
            text = "Video Quality",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            qualities.forEach { quality ->
                val isSelected = selectedQuality == quality
                
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedQuality = quality },
                    label = {
                        Text(
                            text = quality,
                            color = if (isSelected) Color.Black else Color.White
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.Black,
                        containerColor = Color.Gray.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
}

@Composable
private fun DisplaySettingsSection(viewModel: PlayerViewModel) {
    var aspectRatio by remember { mutableStateOf("Fit") }
    val aspectRatios = listOf("Fit", "Fill", "16:9", "4:3", "Stretch")
    
    Column {
        Text(
            text = "Display",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Aspect Ratio
        Text(
            text = "Aspect Ratio",
            color = Color.Gray,
            fontSize = 14.sp
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(top = 4.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            aspectRatios.forEach { ratio ->
                val isSelected = aspectRatio == ratio
                
                FilterChip(
                    selected = isSelected,
                    onClick = { aspectRatio = ratio },
                    label = {
                        Text(
                            text = ratio,
                            color = if (isSelected) Color.Black else Color.White,
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier.height(32.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.Black,
                        containerColor = Color.Gray.copy(alpha = 0.3f)
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Screen Rotation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Screen Rotation",
                color = Color.White
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { /* Rotate left */ },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.RotateLeft,
                        contentDescription = "Rotate Left",
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick = { /* Auto rotate */ },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.ScreenRotation,
                        contentDescription = "Auto Rotate",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = { /* Rotate right */ },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.RotateRight,
                        contentDescription = "Rotate Right",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvancedSettingsSection(viewModel: PlayerViewModel) {
    var loopEnabled by remember { mutableStateOf(false) }
    var sleepTimerEnabled by remember { mutableStateOf(false) }
    
    Column {
        Text(
            text = "Advanced",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Loop Video
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { loopEnabled = !loopEnabled }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Loop,
                    contentDescription = "Loop",
                    tint = if (loopEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                )
                Text(
                    text = "Loop Video",
                    color = Color.White
                )
            }
            Switch(
                checked = loopEnabled,
                onCheckedChange = { loopEnabled = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            )
        }
        
        // Sleep Timer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { sleepTimerEnabled = !sleepTimerEnabled }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = "Sleep Timer",
                    tint = if (sleepTimerEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                )
                Column {
                    Text(
                        text = "Sleep Timer",
                        color = Color.White
                    )
                    if (sleepTimerEnabled) {
                        Text(
                            text = "30 minutes",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Switch(
                checked = sleepTimerEnabled,
                onCheckedChange = { sleepTimerEnabled = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            )
        }
        
        // Equalizer
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clickable { /* Open equalizer */ },
            colors = CardDefaults.cardColors(
                containerColor = Color.Gray.copy(alpha = 0.2f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Equalizer,
                        contentDescription = "Equalizer",
                        tint = Color.White
                    )
                    Text(
                        text = "Audio Equalizer",
                        color = Color.White
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.Gray
                )
            }
        }
        
        // Video Filters
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clickable { /* Open filters */ },
            colors = CardDefaults.cardColors(
                containerColor = Color.Gray.copy(alpha = 0.2f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "Filters",
                        tint = Color.White
                    )
                    Text(
                        text = "Video Filters",
                        color = Color.White
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun AudioTrackOption(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = Color.Gray
            )
        )
        Text(
            text = name,
            color = if (isSelected) Color.White else Color.Gray
        )
    }
}

@Composable
private fun SubtitleOption(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = Color.Gray
            )
        )
        Text(
            text = name,
            color = if (isSelected) Color.White else Color.Gray
        )
    }
}

@Composable
private fun Divider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        color = Color.Gray.copy(alpha = 0.3f)
    )
}