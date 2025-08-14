package com.astralstream.player.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

/**
 * Speed control dialog for video playback
 * Provides preset speeds and custom input
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedControlDialog(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    showCustomInput: Boolean = true
) {
    val hapticFeedback = LocalHapticFeedback.current
    var selectedSpeed by remember { mutableStateOf(currentSpeed) }
    var showCustomSpeedInput by remember { mutableStateOf(false) }
    var customSpeedText by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    
    // Preset speed options
    val presetSpeeds = listOf(
        0.25f to "0.25x",
        0.5f to "0.5x",
        0.75f to "0.75x",
        1.0f to "Normal",
        1.25f to "1.25x",
        1.5f to "1.5x",
        1.75f to "1.75x",
        2.0f to "2x",
        2.5f to "2.5x",
        3.0f to "3x",
        4.0f to "4x"
    )
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .animateContentSize(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Playback Speed",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Current speed display
                CurrentSpeedDisplay(selectedSpeed)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Speed slider
                SpeedSlider(
                    currentSpeed = selectedSpeed,
                    onSpeedChange = { speed ->
                        selectedSpeed = speed
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Preset speed chips
                PresetSpeedChips(
                    presetSpeeds = presetSpeeds,
                    selectedSpeed = selectedSpeed,
                    onSpeedSelect = { speed ->
                        selectedSpeed = speed
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                )
                
                // Custom speed input
                if (showCustomInput) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    AnimatedVisibility(
                        visible = showCustomSpeedInput,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        CustomSpeedInput(
                            customSpeedText = customSpeedText,
                            onTextChange = { customSpeedText = it },
                            onApply = {
                                val speed = customSpeedText.toFloatOrNull()
                                if (speed != null && speed in 0.1f..5.0f) {
                                    selectedSpeed = speed
                                    showCustomSpeedInput = false
                                    customSpeedText = ""
                                    focusManager.clearFocus()
                                }
                            }
                        )
                    }
                    
                    if (!showCustomSpeedInput) {
                        TextButton(
                            onClick = { showCustomSpeedInput = true }
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Custom Speed")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            selectedSpeed = 1.0f
                            onSpeedChange(1.0f)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset")
                    }
                    
                    Button(
                        onClick = {
                            onSpeedChange(selectedSpeed)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentSpeedDisplay(speed: Float) {
    val animatedSpeed by animateFloatAsState(
        targetValue = speed,
        animationSpec = tween(300),
        label = "speed_animation"
    )
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = String.format("%.2fx", animatedSpeed),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            Text(
                text = when {
                    speed < 0.5f -> "Very Slow"
                    speed < 1.0f -> "Slow"
                    speed == 1.0f -> "Normal Speed"
                    speed < 1.5f -> "Fast"
                    speed < 2.0f -> "Faster"
                    else -> "Very Fast"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun SpeedSlider(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Slider(
            value = currentSpeed,
            onValueChange = onSpeedChange,
            valueRange = 0.25f..4.0f,
            steps = 30,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "0.25x",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "4x",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetSpeedChips(
    presetSpeeds: List<Pair<Float, String>>,
    selectedSpeed: Float,
    onSpeedSelect: (Float) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presetSpeeds.forEach { (speed, label) ->
            val isSelected = (selectedSpeed - speed).let { kotlin.math.abs(it) } < 0.01f
            
            FilterChip(
                selected = isSelected,
                onClick = { onSpeedSelect(speed) },
                label = { 
                    Text(
                        text = label,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                leadingIcon = if (speed == 1.0f) {
                    { Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
private fun CustomSpeedInput(
    customSpeedText: String,
    onTextChange: (String) -> Unit,
    onApply: () -> Unit
) {
    OutlinedTextField(
        value = customSpeedText,
        onValueChange = { text ->
            // Allow only valid number input
            if (text.isEmpty() || text.matches(Regex("^\\d*\\.?\\d*$"))) {
                onTextChange(text)
            }
        },
        label = { Text("Enter custom speed (0.1 - 5.0)") },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = { onApply() }
        ),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(onClick = onApply) {
                Icon(Icons.Default.Check, contentDescription = "Apply")
            }
        },
        isError = customSpeedText.toFloatOrNull()?.let { it !in 0.1f..5.0f } ?: false
    )
}

/**
 * Compact speed control button for inline usage
 */
@Composable
fun SpeedControlButton(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    
    Button(
        onClick = { showDialog = true },
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            Icons.Default.Speed,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (currentSpeed == 1.0f) "1x" else String.format("%.2fx", currentSpeed),
            fontWeight = FontWeight.Medium
        )
    }
    
    if (showDialog) {
        SpeedControlDialog(
            currentSpeed = currentSpeed,
            onSpeedChange = onSpeedChange,
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * Speed indicator overlay for video player
 */
@Composable
fun SpeedIndicatorOverlay(
    currentSpeed: Float,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = currentSpeed != 1.0f,
        enter = fadeIn() + slideInHorizontally(),
        exit = fadeOut() + slideOutHorizontally(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Speed,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = String.format("%.2fx", currentSpeed),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Quick speed selector for minimal UI
 */
@Composable
fun QuickSpeedSelector(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    val currentIndex = speeds.indexOfFirst { 
        kotlin.math.abs(it - currentSpeed) < 0.01f 
    }.takeIf { it >= 0 } ?: 2
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        speeds.forEachIndexed { index, speed ->
            val isSelected = index == currentIndex
            
            Surface(
                onClick = { onSpeedChange(speed) },
                shape = CircleShape,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(32.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = if (speed == 1.0f) "1" else speed.toString().removeSuffix(".0"),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}