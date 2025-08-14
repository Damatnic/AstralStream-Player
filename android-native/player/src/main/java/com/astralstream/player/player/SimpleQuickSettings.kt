package com.astralstream.player.player

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.astralstream.player.player.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

@Composable
fun SimpleQuickSettings(
    viewModel: PlayerViewModel,
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    if (isVisible) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.8f)
                    .clip(RoundedCornerShape(20.dp)),
                color = Color(0xFF1A1A1A)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Simple Header
                    SimpleHeader(onDismiss)
                    
                    // Tab Layout
                    var selectedTab by remember { mutableStateOf(0) }
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Playback") },
                            icon = { Icon(Icons.Default.PlayCircle, null, Modifier.size(20.dp)) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Display") },
                            icon = { Icon(Icons.Default.Tv, null, Modifier.size(20.dp)) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("Gestures") },
                            icon = { Icon(Icons.Default.TouchApp, null, Modifier.size(20.dp)) }
                        )
                    }
                    
                    // Content based on selected tab
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        when (selectedTab) {
                            0 -> PlaybackTab(viewModel)
                            1 -> DisplayTab(viewModel)
                            2 -> GesturesTab(viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleHeader(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.align(Alignment.Center)
        )
        
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun PlaybackTab(viewModel: PlayerViewModel) {
    val playerState by viewModel.playerState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Playback Speed with Visual Slider
        SpeedControl(
            currentSpeed = playerState.playbackSpeed,
            onSpeedChange = { viewModel.setPlaybackSpeed(it) }
        )
        
        // Sleep Timer
        SleepTimerControl(viewModel)
        
        // Audio Delay
        AudioDelayControl(viewModel)
        
        // Loop & Shuffle
        PlaybackOptionsRow(viewModel)
    }
}

@Composable
private fun DisplayTab(viewModel: PlayerViewModel) {
    val playerState by viewModel.playerState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Aspect Ratio Grid
        AspectRatioGrid(viewModel)
        
        // Brightness Control
        BrightnessControl(
            brightness = playerState.brightness,
            onBrightnessChange = { viewModel.setBrightness(it) }
        )
        
        // Video Filters (simplified)
        VideoFiltersRow()
        
        // Subtitle Settings
        SubtitleSettings(viewModel)
    }
}

@Composable
private fun GesturesTab(viewModel: PlayerViewModel) {
    var gesturesEnabled by remember { mutableStateOf(true) }
    // MX Player default sensitivity values (medium level)
    var seekSensitivity by remember { mutableStateOf(1.2f) } // Slightly higher for better responsiveness
    var volumeSensitivity by remember { mutableStateOf(1.0f) } // Default 1.0 matches MX Player
    var brightnessSensitivity by remember { mutableStateOf(1.0f) } // Default 1.0 matches MX Player
    var doubleTapSeek by remember { mutableStateOf(10) }
    var longPressSeekSpeed by remember { mutableStateOf(2.0f) } // 2x speed for long press seek
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Master Toggle
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Enable Gestures",
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Control playback with swipe gestures",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = gesturesEnabled,
                    onCheckedChange = { gesturesEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
        
        AnimatedVisibility(visible = gesturesEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Gesture Zones Visual Guide
                GestureZonesGuide()
                
                // Seek Sensitivity
                SensitivitySlider(
                    label = "Horizontal Swipe Sensitivity",
                    description = "Adjust seeking speed",
                    value = seekSensitivity,
                    onValueChange = { seekSensitivity = it },
                    icon = Icons.Default.Speed
                )
                
                // Volume Sensitivity
                SensitivitySlider(
                    label = "Volume Gesture Sensitivity",
                    description = "Right side vertical swipe",
                    value = volumeSensitivity,
                    onValueChange = { volumeSensitivity = it },
                    icon = Icons.Default.VolumeUp
                )
                
                // Brightness Sensitivity
                SensitivitySlider(
                    label = "Brightness Gesture Sensitivity",
                    description = "Left side vertical swipe",
                    value = brightnessSensitivity,
                    onValueChange = { brightnessSensitivity = it },
                    icon = Icons.Default.BrightnessHigh
                )
                
                // Double Tap Seek Duration
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.DoubleArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Double Tap to Seek",
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(5, 10, 15, 30).forEach { seconds ->
                                FilterChip(
                                    selected = doubleTapSeek == seconds,
                                    onClick = { doubleTapSeek = seconds },
                                    label = { Text("${seconds}s") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                
                // Long Press Seek Speed (MX Player style)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.FastForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Long Press Seek Speed",
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "Hold to seek, release to play",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                "${longPressSeekSpeed}x",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Slider(
                            value = longPressSeekSpeed,
                            onValueChange = { 
                                longPressSeekSpeed = it
                                viewModel.setLongPressSeekSpeed(it)
                            },
                            valueRange = 1.0f..4.0f,
                            steps = 5,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(1.0f to "1x", 2.0f to "2x", 3.0f to "3x", 4.0f to "4x").forEach { (speed, label) ->
                                OutlinedButton(
                                    onClick = { 
                                        longPressSeekSpeed = speed
                                        viewModel.setLongPressSeekSpeed(speed)
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (longPressSeekSpeed == speed) 
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) 
                                        else Color.Transparent
                                    ),
                                    contentPadding = PaddingValues(vertical = 6.dp)
                                ) {
                                    Text(
                                        label,
                                        fontSize = 12.sp,
                                        color = if (longPressSeekSpeed == speed) 
                                            MaterialTheme.colorScheme.primary 
                                        else Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedControl(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Playback Speed",
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "${currentSpeed}x",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Slider(
                value = currentSpeed,
                onValueChange = onSpeedChange,
                valueRange = 0.25f..2.0f,
                steps = 7,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
            
            // Quick preset buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(0.5f to "0.5x", 1.0f to "Normal", 1.5f to "1.5x", 2.0f to "2x").forEach { (speed, label) ->
                    OutlinedButton(
                        onClick = { onSpeedChange(speed) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (currentSpeed == speed) 
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) 
                            else Color.Transparent
                        ),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text(
                            label,
                            fontSize = 12.sp,
                            color = if (currentSpeed == speed) 
                                MaterialTheme.colorScheme.primary 
                            else Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AspectRatioGrid(viewModel: PlayerViewModel) {
    val aspectRatio = viewModel.getAspectRatio()
    val aspectRatios = listOf(
        "Fit" to Icons.Default.FitScreen,
        "Fill" to Icons.Default.Fullscreen,
        "16:9" to Icons.Default.Tv,
        "4:3" to Icons.Default.TabletMac,
        "1:1" to Icons.Default.CropSquare,
        "21:9" to Icons.Default.DesktopMac
    )
    
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Aspect Ratio",
                color = Color.White,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(160.dp)
            ) {
                items(aspectRatios.size) { index ->
                    val (label, icon) = aspectRatios[index]
                    val isSelected = aspectRatio.label == label
                    
                    Card(
                        onClick = { viewModel.setAspectRatio(index) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) 
                                MaterialTheme.colorScheme.primary 
                            else Color(0xFF3A3A3A)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                icon,
                                contentDescription = label,
                                tint = if (isSelected) Color.Black else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                label,
                                fontSize = 11.sp,
                                color = if (isSelected) Color.Black else Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GestureZonesGuide() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Gesture Zones",
                color = Color.White,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Visual representation of gesture zones
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1A1A1A))
                    .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
            ) {
                // Left zone
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.33f)
                        .background(Color.Blue.copy(alpha = 0.1f))
                        .border(0.5.dp, Color.Blue.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.BrightnessHigh,
                            contentDescription = null,
                            tint = Color.Blue,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Brightness",
                            fontSize = 10.sp,
                            color = Color.Blue
                        )
                    }
                }
                
                // Center zone
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.67f)
                        .align(Alignment.Center)
                        .background(Color.Green.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.FastForward,
                            contentDescription = null,
                            tint = Color.Green,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Seek",
                            fontSize = 10.sp,
                            color = Color.Green
                        )
                    }
                }
                
                // Right zone
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.33f)
                        .align(Alignment.CenterEnd)
                        .background(Color.Red.copy(alpha = 0.1f))
                        .border(0.5.dp, Color.Red.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = Color.Red,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Volume",
                            fontSize = 10.sp,
                            color = Color.Red
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SensitivitySlider(
    label: String,
    description: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    icon: ImageVector
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        label,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        description,
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Low", fontSize = 11.sp, color = Color.Gray)
                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = 0.5f..2.0f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                Text("High", fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

// Additional helper composables
@Composable
private fun SleepTimerControl(viewModel: PlayerViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Sleep Timer",
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("Off", "15m", "30m", "1h", "End").forEach { label ->
                    OutlinedButton(
                        onClick = { /* Handle timer */ },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text(label, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioDelayControl(viewModel: PlayerViewModel) {
    var audioDelay by remember { mutableStateOf(0f) }
    
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Audio Sync",
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "${audioDelay.toInt()}ms",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { audioDelay = (audioDelay - 50).coerceAtLeast(-1000f) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Remove, null, tint = Color.White)
                }
                
                Slider(
                    value = audioDelay,
                    onValueChange = { audioDelay = it },
                    valueRange = -1000f..1000f,
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(
                    onClick = { audioDelay = (audioDelay + 50).coerceAtMost(1000f) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Add, null, tint = Color.White)
                }
            }
            
            TextButton(
                onClick = { audioDelay = 0f },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Reset", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PlaybackOptionsRow(viewModel: PlayerViewModel) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Loop, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Loop", color = Color.White, fontSize = 14.sp)
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = false,
                    onCheckedChange = { },
                    modifier = Modifier.scale(0.8f)
                )
            }
        }
        
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Shuffle, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Shuffle", color = Color.White, fontSize = 14.sp)
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = false,
                    onCheckedChange = { },
                    modifier = Modifier.scale(0.8f)
                )
            }
        }
    }
}

@Composable
private fun BrightnessControl(
    brightness: Float,
    onBrightnessChange: (Float) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.BrightnessHigh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Screen Brightness",
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "${(brightness * 100).toInt()}%",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Slider(
                value = brightness,
                onValueChange = onBrightnessChange,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun VideoFiltersRow() {
    val filters = listOf(
        "Original" to Icons.Default.PhotoCamera,
        "Vivid" to Icons.Default.Palette,
        "Dark" to Icons.Default.DarkMode,
        "Warm" to Icons.Default.WbSunny
    )
    
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Video Filters",
                color = Color.White,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filters) { (label, icon) ->
                    FilterChip(
                        selected = label == "Original",
                        onClick = { },
                        label = { 
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(icon, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(label, fontSize = 12.sp)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SubtitleSettings(viewModel: PlayerViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Subtitles,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Subtitles",
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = false,
                    onCheckedChange = { }
                )
            }
        }
    }
}

// Extension function for LazyGridScope
private fun androidx.compose.foundation.lazy.grid.LazyGridScope.items(
    count: Int,
    itemContent: @Composable androidx.compose.foundation.lazy.grid.LazyGridItemScope.(Int) -> Unit
) {
    items(count = count, itemContent = itemContent)
}