package com.astralstream.player.player

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Simplified player controls similar to MX Player and VLC
 */
@Composable
fun SimplifiedPlayerControls(
    title: String,
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentTime: Long,
    duration: Long,
    onPlayPauseClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipBackward: () -> Unit,
    onSkipForward: () -> Unit,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLockClick: () -> Unit,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Gradient overlays for better visibility
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.7f),
                                Color.Transparent
                            )
                        )
                    )
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            )
            
            // Top bar - minimal
            MinimalTopBar(
                title = title,
                onBackClick = onBackClick,
                onSettingsClick = onSettingsClick,
                modifier = Modifier.align(Alignment.TopCenter)
            )
            
            // Center controls - clean and simple
            CenterControls(
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                onPlayPauseClick = onPlayPauseClick,
                onSkipBackward = onSkipBackward,
                onSkipForward = onSkipForward,
                modifier = Modifier.align(Alignment.Center)
            )
            
            // Bottom bar - essential controls only
            MinimalBottomBar(
                currentTime = currentTime,
                duration = duration,
                onSeek = onSeek,
                onLockClick = onLockClick,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun MinimalTopBar(
    title: String,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
        }
        
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "More",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun CenterControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPlayPauseClick: () -> Unit,
    onSkipBackward: () -> Unit,
    onSkipForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (isBuffering) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(56.dp)
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Skip backward - smaller, cleaner
                SkipButton(
                    icon = Icons.Default.Replay10,
                    onClick = onSkipBackward,
                    size = 40.dp
                )
                
                // Play/Pause - prominent but clean
                PlayPauseButton(
                    isPlaying = isPlaying,
                    onClick = onPlayPauseClick
                )
                
                // Skip forward
                SkipButton(
                    icon = Icons.Default.Forward10,
                    onClick = onSkipForward,
                    size = 40.dp
                )
            }
        }
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.15f),
        modifier = Modifier.size(64.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
private fun SkipButton(
    icon: ImageVector,
    onClick: () -> Unit,
    size: Dp
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(size)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.fillMaxSize(0.8f)
        )
    }
}

@Composable
private fun MinimalBottomBar(
    currentTime: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    onLockClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Progress bar with time
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTime(currentTime),
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.width(45.dp),
                textAlign = TextAlign.Center
            )
            
            MinimalSeekBar(
                currentTime = currentTime,
                duration = duration,
                onSeek = onSeek,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            )
            
            Text(
                text = formatTime(duration),
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.width(45.dp),
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Quick actions bar
        QuickActionsBar(
            onLockClick = onLockClick,
            onSettingsClick = { /* Settings action */ }
        )
    }
}

@Composable
private fun MinimalSeekBar(
    currentTime: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableStateOf(0f) }
    
    val progress = if (isDragging) {
        dragPosition
    } else {
        if (duration > 0) currentTime.toFloat() / duration.toFloat() else 0f
    }
    
    Box(
        modifier = modifier.height(32.dp),
        contentAlignment = Alignment.Center
    ) {
        // Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(Color.White.copy(alpha = 0.3f))
        ) {
            // Progress
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        
        // Interactive overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                            dragPosition = newProgress
                            isDragging = true
                            tryAwaitRelease()
                            isDragging = false
                            onSeek((newProgress * duration).toLong())
                        }
                    )
                }
        )
        
        // Thumb (visible when dragging)
        AnimatedVisibility(
            visible = isDragging,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}

@Composable
private fun QuickActionsBar(
    onLockClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Playback speed
        QuickActionChip(
            icon = Icons.Default.Speed,
            label = "Speed",
            onClick = onSettingsClick
        )
        
        // Subtitles
        QuickActionChip(
            icon = Icons.Default.Subtitles,
            label = "CC",
            onClick = onSettingsClick
        )
        
        // Audio track
        QuickActionChip(
            icon = Icons.Default.Audiotrack,
            label = "Audio",
            onClick = onSettingsClick
        )
        
        // Aspect ratio
        QuickActionChip(
            icon = Icons.Default.AspectRatio,
            label = "Ratio",
            onClick = onSettingsClick
        )
        
        // Lock
        QuickActionChip(
            icon = Icons.Default.Lock,
            label = "Lock",
            onClick = onLockClick
        )
    }
}

@Composable
private fun QuickActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.1f),
        modifier = Modifier
            .height(32.dp)
            .widthIn(min = 64.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                label,
                color = Color.White,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Lock screen overlay - minimal and clean
 */
@Composable
fun SimpleLockOverlay(
    isLocked: Boolean,
    onUnlock: () -> Unit
) {
    AnimatedVisibility(
        visible = isLocked,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .pointerInput(Unit) {
                    detectTapGestures { onUnlock() }
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier.padding(32.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Touch to unlock",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

/**
 * Minimal gesture indicator
 */
@Composable
fun MinimalGestureIndicator(
    gestureType: GestureType?,
    value: Float,
    seekInfo: SeekInfo?
) {
    AnimatedVisibility(
        visible = gestureType != null || seekInfo != null,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.8f),
                modifier = Modifier.padding(32.dp)
            ) {
                when {
                    seekInfo != null -> {
                        SeekIndicator(seekInfo)
                    }
                    gestureType != null -> {
                        GestureIndicator(gestureType, value)
                    }
                }
            }
        }
    }
}

@Composable
private fun SeekIndicator(seekInfo: SeekInfo) {
    Column(
        modifier = Modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                if (seekInfo.isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Text(
                "${if (seekInfo.isForward) "+" else "-"}${seekInfo.seekSeconds}s",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            formatTime(seekInfo.currentPosition),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun GestureIndicator(type: GestureType, value: Float) {
    Row(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            when (type) {
                GestureType.VOLUME -> Icons.AutoMirrored.Filled.VolumeUp
                GestureType.BRIGHTNESS -> Icons.Default.BrightnessHigh
                else -> Icons.Default.Settings
            },
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        
        LinearProgressIndicator(
            progress = value,
            modifier = Modifier
                .width(120.dp)
                .height(3.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.White.copy(alpha = 0.3f)
        )
        
        Text(
            "${(value * 100).toInt()}%",
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.End
        )
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}