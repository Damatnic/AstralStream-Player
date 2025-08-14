package com.astralstream.player.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.astralstream.player.player.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Main player screen component
 */
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onFullscreenToggle: (Boolean) -> Unit,
    onBackPressed: () -> Unit,
    onPictureInPicture: () -> Unit
) {
    val playerState by viewModel.playerState.collectAsState()
    val scope = rememberCoroutineScope()
    var showControls by remember { mutableStateOf(true) }
    var showQuickSettings by remember { mutableStateOf(false) }
    var currentTime by remember { mutableStateOf(0L) }
    
    // Gesture overlay states for MX Player style UI
    var gestureType by remember { mutableStateOf<GestureType?>(null) }
    var gestureValue by remember { mutableStateOf(0f) }
    var seekInfo by remember { mutableStateOf<SeekInfo?>(null) }
    var isLocked by remember { mutableStateOf(false) }
    var useSimplifiedUI by remember { mutableStateOf(true) } // Toggle for UI style
    var isLongPressSeeking by remember { mutableStateOf(false) }
    
    // Auto-hide controls after 3 seconds
    LaunchedEffect(showControls) {
        if (showControls && playerState.isPlaying) {
            delay(3000)
            showControls = false
        }
    }
    
    // Update current time periodically
    LaunchedEffect(playerState.isPlaying) {
        while (playerState.isPlaying) {
            currentTime = viewModel.getCurrentPosition()
            delay(1000)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isLocked) {
                if (!isLocked) {
                    detectTapGestures(
                        onTap = {
                            showControls = !showControls
                        },
                        onDoubleTap = { offset ->
                            val screenWidth = size.width
                            if (offset.x < screenWidth / 2) {
                                viewModel.seekBackward(10)
                            } else {
                                viewModel.seekForward(10)
                            }
                        }
                    )
                }
            }
            .pointerInput(isLocked, playerState.longPressSeekSpeed) {
                if (!isLocked) {
                    // MX Player style long press seek with release detection
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val screenWidth = size.width
                        val isForward = down.position.x > screenWidth / 2
                        
                        // Start timer for long press detection
                        val longPressJob = scope.launch {
                            delay(500) // 500ms for long press threshold
                            isLongPressSeeking = true
                            viewModel.startLongPressSeek(isForward)
                        }
                        
                        // Wait for release or cancellation
                        val up = waitForUpOrCancellation()
                        longPressJob.cancel()
                        
                        if (isLongPressSeeking) {
                            viewModel.stopLongPressSeek()
                            isLongPressSeeking = false
                        }
                    }
                }
            }
            .pointerInput(isLocked) {
                if (isLocked) return@pointerInput
                // MX Player style gesture detection with improved sensitivity
                var initialVolume = 0f
                var initialBrightness = 0f
                var initialSeekPosition = 0L
                var totalDragY = 0f
                var totalDragX = 0f
                var gestureStartX = 0f
                var gestureStartY = 0f
                
                detectDragGestures(
                    onDragStart = { offset ->
                        // Store initial values
                        initialVolume = viewModel.getVolume()
                        initialBrightness = viewModel.getBrightness()
                        initialSeekPosition = viewModel.getCurrentPosition()
                        gestureStartX = offset.x
                        gestureStartY = offset.y
                        totalDragY = 0f
                        totalDragX = 0f
                    },
                    onDragEnd = {
                        // Hide gesture overlay after delay
                        scope.launch {
                            delay(800)
                            gestureType = null
                            seekInfo = null
                        }
                    },
                    onDrag = { change, dragAmount ->
                        val screenWidth = size.width.toFloat()
                        val screenHeight = size.height.toFloat()
                        
                        // Accumulate drag amounts for smoother control
                        totalDragY += dragAmount.y
                        totalDragX += dragAmount.x
                        
                        // Determine gesture type based on accumulated drag distance
                        val isVerticalGesture = kotlin.math.abs(totalDragY) > kotlin.math.abs(totalDragX) * 1.2f
                        val isHorizontalGesture = kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY) * 1.2f
                        
                        when {
                            // Vertical swipe - volume or brightness (MX Player style)
                            isVerticalGesture && kotlin.math.abs(totalDragY) > 10 -> {
                                val isLeftHalf = gestureStartX < screenWidth / 2
                                
                                if (isLeftHalf) {
                                    // Left half - brightness control
                                    val sensitivity = 1.5f  // Increase sensitivity
                                    val delta = -totalDragY / screenHeight * sensitivity
                                    val newBrightness = (initialBrightness + delta).coerceIn(0f, 1f)
                                    viewModel.setBrightness(newBrightness)
                                    gestureType = GestureType.BRIGHTNESS
                                    gestureValue = newBrightness
                                } else {
                                    // Right half - volume control
                                    val sensitivity = 1.5f  // Increase sensitivity
                                    val delta = -totalDragY / screenHeight * sensitivity
                                    val newVolume = (initialVolume + delta).coerceIn(0f, 1f)
                                    viewModel.setVolume(newVolume)
                                    gestureType = GestureType.VOLUME
                                    gestureValue = newVolume
                                }
                            }
                            // Horizontal swipe - seek (works anywhere on screen)
                            isHorizontalGesture && kotlin.math.abs(totalDragX) > 10 -> {
                                // More responsive seeking with variable speed based on screen position
                                val seekSensitivity = if (gestureStartY < screenHeight * 0.3f || gestureStartY > screenHeight * 0.7f) {
                                    90000  // 90 seconds max for edges (faster seeking)
                                } else {
                                    45000  // 45 seconds max for center (normal seeking)
                                }
                                
                                val seekDelta = (totalDragX / screenWidth) * seekSensitivity
                                val newPosition = (initialSeekPosition + seekDelta.toLong()).coerceIn(0, playerState.duration)
                                viewModel.seekTo(newPosition)
                                gestureType = GestureType.SEEK
                                
                                val actualSeekDelta = newPosition - initialSeekPosition
                                seekInfo = SeekInfo(
                                    isForward = actualSeekDelta > 0,
                                    seekSeconds = kotlin.math.abs(actualSeekDelta / 1000).toInt(),
                                    currentPosition = newPosition,
                                    duration = playerState.duration,
                                    progress = if (playerState.duration > 0) newPosition.toFloat() / playerState.duration.toFloat() else 0f
                                )
                            }
                        }
                    }
                )
            }
    ) {
        // ExoPlayer Video View
        val context = LocalContext.current
        var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
        var playerView by remember { mutableStateOf<PlayerView?>(null) }
        
        DisposableEffect(Unit) {
            val player = ExoPlayer.Builder(context).build()
            exoPlayer = player
            viewModel.setExoPlayer(player)
            
            onDispose {
                player.release()
            }
        }
        
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false // We'll use our own controls
                    playerView = this
                }
            },
            update = { view ->
                // Update the player when exoPlayer changes
                view.player = exoPlayer
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Player Controls - Use simplified or classic UI
        if (useSimplifiedUI) {
            SimplifiedPlayerControls(
                title = playerState.currentMedia?.substringAfterLast('/') ?: "Video",
                isPlaying = playerState.isPlaying,
                isBuffering = playerState.isBuffering,
                currentTime = currentTime,
                duration = playerState.duration,
                onPlayPauseClick = {
                    if (playerState.isPlaying) {
                        viewModel.pausePlayback()
                    } else {
                        viewModel.resumePlayback()
                    }
                },
                onSeek = { viewModel.seekTo(it) },
                onSkipBackward = { viewModel.seekBackward(10) },
                onSkipForward = { viewModel.seekForward(10) },
                onBackClick = onBackPressed,
                onSettingsClick = { showQuickSettings = true },
                onLockClick = { isLocked = !isLocked },
                isVisible = showControls
            )
        } else {
            // Classic controls (existing implementation)
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                ) {
                    // Top Controls
                    TopPlayerControls(
                        title = playerState.currentMedia?.substringAfterLast('/') ?: "Video Player",
                        onBackClick = onBackPressed,
                        onSettingsClick = { showQuickSettings = true },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                    
                    // Center Controls
                    CenterPlayerControls(
                        isPlaying = playerState.isPlaying,
                        isBuffering = playerState.isBuffering,
                        onPlayPauseClick = {
                            if (playerState.isPlaying) {
                                viewModel.pausePlayback()
                            } else {
                                viewModel.resumePlayback()
                            }
                        },
                        onSkipBackward = { viewModel.seekBackward(10) },
                        onSkipForward = { viewModel.seekForward(10) },
                        modifier = Modifier.align(Alignment.Center)
                    )
                    
                    // Bottom Controls
                    BottomPlayerControls(
                        currentTime = currentTime,
                        duration = playerState.duration,
                        onSeek = { viewModel.seekTo(it) },
                        onFullscreenClick = { viewModel.toggleFullscreen() },
                        onPipClick = onPictureInPicture,
                        onLockClick = { isLocked = !isLocked },
                        isLocked = isLocked,
                        playbackSpeed = playerState.playbackSpeed,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
        
        // Quick Settings Menu - Using new simplified version
        SimpleQuickSettings(
            viewModel = viewModel,
            isVisible = showQuickSettings,
            onDismiss = { showQuickSettings = false }
        )
        
        // Gesture and lock overlays - use simplified versions
        if (useSimplifiedUI) {
            MinimalGestureIndicator(
                gestureType = gestureType,
                value = gestureValue,
                seekInfo = seekInfo
            )
            
            SimpleLockOverlay(
                isLocked = isLocked,
                onUnlock = { isLocked = false }
            )
        } else {
            // Classic overlays
            GestureOverlay(
                gestureType = gestureType,
                value = gestureValue,
                seekInfo = seekInfo
            )
            
            // Lock screen overlay
            if (isLocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { isLocked = false },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.padding(16.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.8f)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Screen Locked",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tap to unlock",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopPlayerControls(
    title: String,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = { /* Cast */ }) {
                    Icon(
                        imageVector = Icons.Default.Cast,
                        contentDescription = "Cast",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun CenterPlayerControls(
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
                modifier = Modifier.size(60.dp)
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Skip backward
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { onSkipBackward() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "Skip 10s backward",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                
                // Play/Pause
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .clickable { onPlayPauseClick() }
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }
                
                // Skip forward
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { onSkipForward() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "Skip 10s forward",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomPlayerControls(
    currentTime: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    onFullscreenClick: () -> Unit,
    onPipClick: () -> Unit,
    onLockClick: () -> Unit,
    isLocked: Boolean,
    playbackSpeed: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Time and progress bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(currentTime),
                    color = Color.White,
                    fontSize = 14.sp
                )
                
                Slider(
                    value = if (duration > 0) currentTime.toFloat() else 0f,
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.Gray
                    )
                )
                
                Text(
                    text = formatTime(duration),
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
            
            // Bottom control buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speed indicator
                Text(
                    text = if (playbackSpeed != 1.0f) "${playbackSpeed}x" else "",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    modifier = Modifier.width(40.dp),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Control buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onLockClick) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock",
                            tint = Color.White
                        )
                    }
                    
                    IconButton(onClick = onPipClick) {
                        Icon(
                            imageVector = Icons.Default.PictureInPicture,
                            contentDescription = "Picture in Picture",
                            tint = Color.White
                        )
                    }
                    
                    IconButton(onClick = onFullscreenClick) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen",
                            tint = Color.White
                        )
                    }
                }
            }
        }
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