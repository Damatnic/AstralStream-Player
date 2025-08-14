package com.astralstream.player.player

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * MX Player style gesture overlay for volume, brightness and seek feedback
 */
@Composable
fun GestureOverlay(
    gestureType: GestureType?,
    value: Float,
    seekInfo: SeekInfo? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = gestureType != null,
        enter = fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.8f),
        exit = fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 0.8f),
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
        ) {
            when (gestureType) {
                GestureType.VOLUME -> VolumeOverlay(value)
                GestureType.BRIGHTNESS -> BrightnessOverlay(value)
                GestureType.SEEK -> seekInfo?.let { SeekOverlay(it) }
                null -> {}
            }
        }
    }
}

@Composable
private fun VolumeOverlay(volume: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(24.dp)
    ) {
        Icon(
            imageVector = when {
                volume == 0f -> Icons.Default.VolumeOff
                volume < 0.5f -> Icons.Default.VolumeDown
                else -> Icons.Default.VolumeUp
            },
            contentDescription = "Volume",
            tint = Color.White,
            modifier = Modifier.size(48.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Volume bar
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(volume)
                    .background(Color.White)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "${(volume * 100).toInt()}%",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun BrightnessOverlay(brightness: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(24.dp)
    ) {
        Icon(
            imageVector = when {
                brightness < 0.3f -> Icons.Default.BrightnessLow
                brightness < 0.7f -> Icons.Default.BrightnessMedium
                else -> Icons.Default.BrightnessHigh
            },
            contentDescription = "Brightness",
            tint = Color.White,
            modifier = Modifier.size(48.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Brightness bar
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(brightness)
                    .background(Color.White)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "${(brightness * 100).toInt()}%",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SeekOverlay(seekInfo: SeekInfo) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        // Seek direction icon and amount
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (seekInfo.isForward) {
                    Icons.Default.FastForward
                } else {
                    Icons.Default.FastRewind
                },
                contentDescription = "Seek",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = if (seekInfo.isForward) {
                    "+${seekInfo.seekSeconds}s"
                } else {
                    "-${seekInfo.seekSeconds}s"
                },
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Current and target time
        Text(
            text = "${formatTime(seekInfo.currentPosition)} / ${formatTime(seekInfo.duration)}",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Progress bar
        Box(
            modifier = Modifier
                .width(250.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(Color.White.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(seekInfo.progress)
                    .background(Color.Red)
            )
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

enum class GestureType {
    VOLUME,
    BRIGHTNESS,
    SEEK
}

data class SeekInfo(
    val isForward: Boolean,
    val seekSeconds: Int,
    val currentPosition: Long,
    val duration: Long,
    val progress: Float
)