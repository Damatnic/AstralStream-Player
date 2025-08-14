package com.astralstream.player.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.astralstream.player.thumbnail.ThumbnailGenerator
import com.astralstream.player.thumbnail.ThumbnailInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke

@Composable
fun VideoSeekPreview(
    videoUri: Uri,
    duration: Long,
    seekPosition: Long,
    isVisible: Boolean,
    thumbnailGenerator: ThumbnailGenerator,
    previewWidth: Dp = 160.dp,
    previewHeight: Dp = 90.dp,
    xOffset: Int = 0,
    yOffset: Int = -200,
    modifier: Modifier = Modifier
) {
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var thumbnailJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    
    // Load thumbnail when seek position changes
    LaunchedEffect(seekPosition) {
        thumbnailJob?.cancel()
        thumbnailJob = scope.launch {
            // Add small delay to avoid loading too many thumbnails during fast seeking
            delay(50)
            
            // Try to get cached thumbnail first
            val cachedThumbnail = thumbnailGenerator.getCachedThumbnail(
                videoUri,
                (seekPosition / 10000).toInt() // Assuming 10-second intervals
            )
            
            if (cachedThumbnail != null) {
                previewBitmap = cachedThumbnail
            } else {
                // Generate thumbnail on the fly
                val thumbnail = thumbnailGenerator.getThumbnailAtTime(
                    videoUri,
                    seekPosition
                )
                previewBitmap = thumbnail
            }
        }
    }
    
    AnimatedVisibility(
        visible = isVisible && previewBitmap != null,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        Popup(
            alignment = Alignment.TopCenter,
            offset = IntOffset(xOffset, yOffset)
        ) {
            SeekPreviewCard(
                bitmap = previewBitmap,
                timestamp = formatTimestamp(seekPosition),
                previewWidth = previewWidth,
                previewHeight = previewHeight
            )
        }
    }
}

@Composable
fun SeekPreviewCard(
    bitmap: Bitmap?,
    timestamp: String,
    previewWidth: Dp,
    previewHeight: Dp
) {
    Card(
        modifier = Modifier
            .width(previewWidth)
            .shadow(8.dp, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Thumbnail image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewHeight)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            ) {
                bitmap?.let {
                    Image(
                        painter = BitmapPainter(it.asImageBitmap()),
                        contentDescription = "Seek preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } ?: Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.DarkGray)
                )
                
                // Optional: Add loading indicator or placeholder
            }
            
            // Timestamp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = timestamp,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
fun EnhancedVideoSeekBar(
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long,
    videoUri: Uri,
    thumbnailGenerator: ThumbnailGenerator,
    onSeek: (Long) -> Unit,
    onSeekStart: () -> Unit = {},
    onSeekEnd: () -> Unit = {},
    showChapterMarkers: Boolean = true,
    chapters: List<ChapterMarker> = emptyList(),
    bookmarks: List<BookmarkMarker> = emptyList(),
    modifier: Modifier = Modifier
) {
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableStateOf(currentPosition) }
    var previewOffset by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    
    Column(modifier = modifier) {
        // Preview popup
        VideoSeekPreview(
            videoUri = videoUri,
            duration = duration,
            seekPosition = seekPosition,
            isVisible = isSeeking,
            thumbnailGenerator = thumbnailGenerator,
            xOffset = previewOffset,
            yOffset = with(density) { -120.dp.toPx().toInt() }
        )
        
        // Seek bar with markers
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            // Background track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.Center)
                    .background(Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
            )
            
            // Buffered progress
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = if (duration > 0) bufferedPosition.toFloat() / duration else 0f)
                    .height(4.dp)
                    .align(Alignment.CenterStart)
                    .background(Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
            )
            
            // Current progress
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = if (duration > 0) currentPosition.toFloat() / duration else 0f)
                    .height(4.dp)
                    .align(Alignment.CenterStart)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            )
            
            // Chapter markers
            if (showChapterMarkers) {
                chapters.forEach { chapter ->
                    ChapterMarkerView(
                        chapter = chapter,
                        duration = duration,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                }
            }
            
            // Bookmark markers
            bookmarks.forEach { bookmark ->
                BookmarkMarkerView(
                    bookmark = bookmark,
                    duration = duration,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }
            
        }
        
        // Time labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTimestamp(if (isSeeking) seekPosition else currentPosition),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = formatTimestamp(duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun ChapterMarkerView(
    chapter: ChapterMarker,
    duration: Long,
    modifier: Modifier = Modifier
) {
    val position = if (duration > 0) chapter.startTime.toFloat() / duration else 0f
    
    Box(
        modifier = modifier
            .offset(x = LocalDensity.current.run { (position * 100).dp })
            .size(12.dp, 16.dp)
    ) {
        // Chapter marker visual
        Box(
            modifier = Modifier
                .size(2.dp, 16.dp)
                .background(Color.Yellow)
                .align(Alignment.Center)
        )
    }
}

@Composable
fun BookmarkMarkerView(
    bookmark: BookmarkMarker,
    duration: Long,
    modifier: Modifier = Modifier
) {
    val position = if (duration > 0) bookmark.position.toFloat() / duration else 0f
    
    Box(
        modifier = modifier
            .offset(x = LocalDensity.current.run { (position * 100).dp })
            .size(8.dp, 12.dp)
    ) {
        // Bookmark marker visual
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    color = Color.Red,
                    shape = androidx.compose.foundation.shape.CircleShape
                )
                .align(Alignment.Center)
        )
    }
}

@Composable
fun ThumbnailStripPreview(
    videoUri: Uri,
    duration: Long,
    currentPosition: Long,
    thumbnails: List<ThumbnailInfo>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var hoveredIndex by remember { mutableStateOf(-1) }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .horizontalScroll(rememberScrollState())
    ) {
        thumbnails.forEachIndexed { index, thumbnail ->
            ThumbnailPreviewItem(
                thumbnail = thumbnail,
                isHovered = index == hoveredIndex,
                isCurrent = kotlin.math.abs(thumbnail.timestamp - currentPosition) < 5000,
                onClick = {
                    onSeek(thumbnail.timestamp)
                },
                onHover = {
                    hoveredIndex = if (it) index else -1
                },
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

@Composable
fun ThumbnailPreviewItem(
    thumbnail: ThumbnailInfo,
    isHovered: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onHover: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = when {
            isHovered -> 1.2f
            isCurrent -> 1.1f
            else -> 1f
        }
    )
    
    Card(
        modifier = modifier
            .size(80.dp * scale, 45.dp * scale)
            .clickable { onClick() }
            .onHover { onHover(it) },
        shape = RoundedCornerShape(4.dp),
        border = if (isCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box {
            thumbnail.bitmap?.let { bitmap ->
                Image(
                    painter = BitmapPainter(bitmap.asImageBitmap()),
                    contentDescription = "Thumbnail at ${formatTimestamp(thumbnail.timestamp)}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } ?: Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.DarkGray)
            )
            
            // Timestamp overlay
            if (isHovered) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatTimestamp(thumbnail.timestamp),
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

// Helper functions and data classes

private fun formatTimestamp(positionMs: Long): String {
    val totalSeconds = positionMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun updateSeekPosition(x: Float, duration: Long) {
    // Calculate seek position based on x coordinate
    // Implementation depends on actual seek bar width
}

data class ChapterMarker(
    val startTime: Long,
    val title: String
)

data class BookmarkMarker(
    val position: Long,
    val title: String
)

// Extension for hover detection
@Composable
fun Modifier.onHover(onHover: (Boolean) -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    LaunchedEffect(isHovered) {
        onHover(isHovered)
    }
    
    return this.clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = {}
    )
}