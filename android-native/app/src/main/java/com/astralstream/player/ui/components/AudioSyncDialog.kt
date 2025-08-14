package com.astralstream.player.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * Audio sync adjustment dialog for correcting audio/video synchronization
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSyncDialog(
    currentDelay: Long, // in milliseconds
    onDelayChange: (Long) -> Unit,
    onDismiss: () -> Unit,
    minDelay: Long = -5000L, // -5 seconds
    maxDelay: Long = 5000L,   // +5 seconds
    stepSize: Long = 50L       // 50ms steps
) {
    val hapticFeedback = LocalHapticFeedback.current
    var selectedDelay by remember { mutableStateOf(currentDelay) }
    var customDelayText by remember { mutableStateOf("") }
    var showCustomInput by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    
    // Preset delay options
    val presetDelays = listOf(
        -2000L to "Audio First (-2s)",
        -1000L to "Audio First (-1s)",
        -500L to "Audio First (-0.5s)",
        -250L to "Audio First (-0.25s)",
        0L to "No Delay",
        250L to "Video First (+0.25s)",
        500L to "Video First (+0.5s)",
        1000L to "Video First (+1s)",
        2000L to "Video First (+2s)"
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
                    Column {
                        Text(
                            text = "Audio Sync",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Adjust audio/video synchronization",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Current delay display
                CurrentDelayDisplay(selectedDelay)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Visual sync indicator
                SyncVisualization(selectedDelay)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Fine adjustment controls
                FineAdjustmentControls(
                    currentDelay = selectedDelay,
                    stepSize = stepSize,
                    onDelayChange = { delay ->
                        selectedDelay = delay.coerceIn(minDelay, maxDelay)
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Delay slider
                DelaySlider(
                    currentDelay = selectedDelay,
                    minDelay = minDelay,
                    maxDelay = maxDelay,
                    onDelayChange = { delay ->
                        selectedDelay = delay
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Preset delay chips
                PresetDelayChips(
                    presetDelays = presetDelays,
                    selectedDelay = selectedDelay,
                    onDelaySelect = { delay ->
                        selectedDelay = delay
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                )
                
                // Custom delay input
                Spacer(modifier = Modifier.height(16.dp))
                
                AnimatedVisibility(
                    visible = showCustomInput,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    CustomDelayInput(
                        customDelayText = customDelayText,
                        onTextChange = { customDelayText = it },
                        onApply = {
                            val delay = customDelayText.toLongOrNull()
                            if (delay != null && delay in minDelay..maxDelay) {
                                selectedDelay = delay
                                showCustomInput = false
                                customDelayText = ""
                                focusManager.clearFocus()
                            }
                        }
                    )
                }
                
                if (!showCustomInput) {
                    TextButton(
                        onClick = { showCustomInput = true }
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Custom Delay (ms)")
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
                            selectedDelay = 0L
                            onDelayChange(0L)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset")
                    }
                    
                    Button(
                        onClick = {
                            onDelayChange(selectedDelay)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Apply")
                    }
                }
                
                // Help text
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tip: Use negative values if audio comes before video, positive if video comes before audio",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun CurrentDelayDisplay(delay: Long) {
    val animatedDelay by animateIntAsState(
        targetValue = delay.toInt(),
        animationSpec = tween(300),
        label = "delay_animation"
    )
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                delay == 0L -> MaterialTheme.colorScheme.primaryContainer
                delay < 0 -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.tertiaryContainer
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = when {
                        delay < 0 -> Icons.Default.FastRewind
                        delay > 0 -> Icons.Default.FastForward
                        else -> Icons.Default.Sync
                    },
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = when {
                        delay == 0L -> MaterialTheme.colorScheme.onPrimaryContainer
                        delay < 0 -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onTertiaryContainer
                    }
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = formatDelay(animatedDelay.toLong()),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        delay == 0L -> MaterialTheme.colorScheme.onPrimaryContainer
                        delay < 0 -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onTertiaryContainer
                    }
                )
            }
            
            Text(
                text = when {
                    delay < 0 -> "Audio plays first"
                    delay > 0 -> "Video plays first"
                    else -> "Perfectly synced"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    delay == 0L -> MaterialTheme.colorScheme.onPrimaryContainer
                    delay < 0 -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onTertiaryContainer
                }.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun SyncVisualization(delay: Long) {
    val infiniteTransition = rememberInfiniteTransition(label = "sync_animation")
    val audioOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "audio_offset"
    )
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Video track
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.VideoLibrary,
                    contentDescription = "Video",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                ),
                                startX = audioOffset * 1000f,
                                endX = (audioOffset * 1000f) + 200f
                            )
                        )
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Audio track with delay offset
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AudioFile,
                    contentDescription = "Audio",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .offset(x = (delay / 50).toInt().dp) // Visual offset based on delay
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.secondary,
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                                ),
                                startX = audioOffset * 1000f,
                                endX = (audioOffset * 1000f) + 200f
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun FineAdjustmentControls(
    currentDelay: Long,
    stepSize: Long,
    onDelayChange: (Long) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Large decrease
        IconButton(
            onClick = { onDelayChange(currentDelay - stepSize * 10) }
        ) {
            Icon(
                Icons.Default.SkipPrevious,
                contentDescription = "Decrease 500ms",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        // Small decrease
        OutlinedIconButton(
            onClick = { onDelayChange(currentDelay - stepSize) }
        ) {
            Icon(
                Icons.Default.Remove,
                contentDescription = "Decrease 50ms"
            )
        }
        
        // Current value
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = "${currentDelay}ms",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
        
        // Small increase
        OutlinedIconButton(
            onClick = { onDelayChange(currentDelay + stepSize) }
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Increase 50ms"
            )
        }
        
        // Large increase
        IconButton(
            onClick = { onDelayChange(currentDelay + stepSize * 10) }
        ) {
            Icon(
                Icons.Default.SkipNext,
                contentDescription = "Increase 500ms",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun DelaySlider(
    currentDelay: Long,
    minDelay: Long,
    maxDelay: Long,
    onDelayChange: (Long) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Slider(
            value = currentDelay.toFloat(),
            onValueChange = { onDelayChange(it.roundToInt().toLong()) },
            valueRange = minDelay.toFloat()..maxDelay.toFloat(),
            steps = ((maxDelay - minDelay) / 50).toInt(),
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = when {
                    currentDelay == 0L -> MaterialTheme.colorScheme.primary
                    currentDelay < 0 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.tertiary
                },
                activeTrackColor = when {
                    currentDelay == 0L -> MaterialTheme.colorScheme.primary
                    currentDelay < 0 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.tertiary
                }
            )
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${minDelay}ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "0ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "+${maxDelay}ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetDelayChips(
    presetDelays: List<Pair<Long, String>>,
    selectedDelay: Long,
    onDelaySelect: (Long) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presetDelays.forEach { (delay, label) ->
            val isSelected = selectedDelay == delay
            
            FilterChip(
                selected = isSelected,
                onClick = { onDelaySelect(delay) },
                label = { 
                    Text(
                        text = label,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                leadingIcon = if (delay == 0L) {
                    { Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = when {
                        delay == 0L -> MaterialTheme.colorScheme.primary
                        delay < 0 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.tertiary
                    },
                    selectedLabelColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    }
}

@Composable
private fun CustomDelayInput(
    customDelayText: String,
    onTextChange: (String) -> Unit,
    onApply: () -> Unit
) {
    OutlinedTextField(
        value = customDelayText,
        onValueChange = { text ->
            // Allow only valid number input including negative
            if (text.isEmpty() || text == "-" || text.matches(Regex("^-?\\d*$"))) {
                onTextChange(text)
            }
        },
        label = { Text("Enter delay in milliseconds (-5000 to 5000)") },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
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
        isError = customDelayText.toLongOrNull()?.let { it !in -5000L..5000L } ?: false
    )
}

private fun formatDelay(delay: Long): String {
    val absoluteDelay = delay.absoluteValue
    val seconds = absoluteDelay / 1000
    val milliseconds = absoluteDelay % 1000
    
    return when {
        delay == 0L -> "0ms"
        delay < 0 -> "-${seconds}s ${milliseconds}ms"
        else -> "+${seconds}s ${milliseconds}ms"
    }
}

/**
 * Compact audio sync button for inline usage
 */
@Composable
fun AudioSyncButton(
    currentDelay: Long,
    onDelayChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    
    Button(
        onClick = { showDialog = true },
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            Icons.Default.Sync,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (currentDelay == 0L) "Sync" else "${currentDelay}ms",
            fontWeight = FontWeight.Medium
        )
    }
    
    if (showDialog) {
        AudioSyncDialog(
            currentDelay = currentDelay,
            onDelayChange = onDelayChange,
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * Audio sync indicator overlay for video player
 */
@Composable
fun AudioSyncIndicatorOverlay(
    currentDelay: Long,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = currentDelay != 0L,
        enter = fadeIn() + slideInHorizontally(),
        exit = fadeOut() + slideOutHorizontally(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = when {
                currentDelay < 0 -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.tertiaryContainer
            }.copy(alpha = 0.9f),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        currentDelay < 0 -> Icons.Default.FastRewind
                        else -> Icons.Default.FastForward
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = when {
                        currentDelay < 0 -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onTertiaryContainer
                    }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${currentDelay}ms",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        currentDelay < 0 -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onTertiaryContainer
                    }
                )
            }
        }
    }
}