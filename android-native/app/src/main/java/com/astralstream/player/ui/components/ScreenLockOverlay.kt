package com.astralstream.player.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Screen lock overlay for video player
 * Prevents accidental touches during playback
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ScreenLockOverlay(
    isLocked: Boolean,
    onToggleLock: () -> Unit,
    modifier: Modifier = Modifier,
    showToast: (String) -> Unit = {}
) {
    val hapticFeedback = LocalHapticFeedback.current
    var showUnlockHint by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Animation states
    val lockIconRotation by animateFloatAsState(
        targetValue = if (isLocked) 0f else 30f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "lock_rotation"
    )
    
    val lockScale by animateFloatAsState(
        targetValue = if (isLocked) 1f else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "lock_scale"
    )
    
    // Show unlock hint animation when locked
    LaunchedEffect(isLocked) {
        if (isLocked) {
            delay(2000)
            showUnlockHint = true
            delay(3000)
            showUnlockHint = false
        }
    }
    
    Box(modifier = modifier) {
        // Invisible touch interceptor when locked
        if (isLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        // Show unlock hint on tap
                        showUnlockHint = true
                        scope.launch {
                            delay(2000)
                            showUnlockHint = false
                        }
                    }
            )
        }
        
        // Lock button with animations
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            LockButton(
                isLocked = isLocked,
                rotation = lockIconRotation,
                scale = lockScale,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleLock()
                    showToast(if (!isLocked) "Screen locked" else "Screen unlocked")
                }
            )
        }
        
        // Unlock hint overlay
        AnimatedVisibility(
            visible = showUnlockHint && isLocked,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            UnlockHintOverlay()
        }
        
        // Lock status indicator
        AnimatedVisibility(
            visible = isLocked,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            LockStatusIndicator()
        }
    }
}

@Composable
private fun LockButton(
    isLocked: Boolean,
    rotation: Float,
    scale: Float,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "lock_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .rotate(rotation),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(
            alpha = if (isLocked) pulseAlpha else 0.9f
        ),
        shadowElevation = 8.dp
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = if (isLocked) "Unlock screen" else "Lock screen",
                tint = if (isLocked) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun UnlockHintOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "hint_animation")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hint_scale"
    )
    
    Card(
        modifier = Modifier
            .scale(scale)
            .padding(32.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.TouchApp,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Screen is Locked",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Tap the lock icon to unlock",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LockStatusIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "status_blink")
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink_alpha"
    )
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .alpha(blinkAlpha)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                    )
                )
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "LOCKED",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
            letterSpacing = 1.sp
        )
    }
}

/**
 * Screen lock state manager
 */
@Composable
fun rememberScreenLockState(): ScreenLockState {
    return remember { ScreenLockState() }
}

class ScreenLockState {
    var isLocked by mutableStateOf(false)
        private set
    
    fun toggleLock() {
        isLocked = !isLocked
    }
    
    fun lock() {
        isLocked = true
    }
    
    fun unlock() {
        isLocked = false
    }
}

/**
 * Helper function for easy integration with screen lock
 */
@Composable
fun BoxWithScreenLock(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val screenLockState = rememberScreenLockState()
    
    Box(modifier = modifier.fillMaxSize()) {
        content()
        
        ScreenLockOverlay(
            isLocked = screenLockState.isLocked,
            onToggleLock = { screenLockState.toggleLock() },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Screen lock button for custom placement
 */
@Composable
fun ScreenLockButton(
    screenLockState: ScreenLockState,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    
    IconButton(
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            screenLockState.toggleLock()
        },
        modifier = modifier
    ) {
        Icon(
            imageVector = if (screenLockState.isLocked) {
                Icons.Default.Lock
            } else {
                Icons.Default.LockOpen
            },
            contentDescription = if (screenLockState.isLocked) {
                "Unlock screen"
            } else {
                "Lock screen"
            },
            tint = if (screenLockState.isLocked) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
    }
}

/**
 * Simplified screen lock for basic usage
 */
@Composable
fun SimpleScreenLock(
    modifier: Modifier = Modifier,
    onLockStateChanged: (Boolean) -> Unit = {}
) {
    var isLocked by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    
    if (isLocked) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    // Consume all touch events
                }
        )
    }
    
    IconButton(
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            isLocked = !isLocked
            onLockStateChanged(isLocked)
        },
        modifier = Modifier.padding(16.dp)
    ) {
        Icon(
            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
            contentDescription = if (isLocked) "Unlock" else "Lock"
        )
    }
}