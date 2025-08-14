package com.astralstream.player.controls

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

@UnstableApi
@Singleton
class AdvancedPlayerControls @Inject constructor(
    private val context: Context
) {
    
    private val _frameNavigationState = MutableStateFlow(FrameNavigationState())
    val frameNavigationState: StateFlow<FrameNavigationState> = _frameNavigationState.asStateFlow()
    
    private val _zoomPanState = MutableStateFlow(ZoomPanState())
    val zoomPanState: StateFlow<ZoomPanState> = _zoomPanState.asStateFlow()
    
    private val _playbackControlState = MutableStateFlow(PlaybackControlState())
    val playbackControlState: StateFlow<PlaybackControlState> = _playbackControlState.asStateFlow()
    
    private var exoPlayer: ExoPlayer? = null
    private var frameSeekJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    
    private var videoWidth = 0
    private var videoHeight = 0
    private var viewWidth = 0
    private var viewHeight = 0
    
    private val transformMatrix = Matrix()
    private var currentScale = 1f
    private var currentTranslateX = 0f
    private var currentTranslateY = 0f
    
    companion object {
        private const val MIN_ZOOM = 1f
        private const val MAX_ZOOM = 5f
        private const val ZOOM_ANIMATION_DURATION = 300L
        private const val FRAME_SEEK_DELAY_MS = 50L
        private const val DEFAULT_FRAME_RATE = 30f
        private const val LONG_PRESS_THRESHOLD = 500L
    }
    
    fun initialize(player: ExoPlayer) {
        exoPlayer = player
        
        player.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                videoWidth = videoSize.width
                videoHeight = videoSize.height
                updateFrameRate(player)
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                _playbackControlState.value = _playbackControlState.value.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    isEnded = playbackState == Player.STATE_ENDED
                )
            }
            
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackControlState.value = _playbackControlState.value.copy(
                    isPlaying = isPlaying
                )
            }
        })
    }
    
    private fun updateFrameRate(player: ExoPlayer) {
        val format = player.videoFormat
        val frameRate = format?.frameRate ?: DEFAULT_FRAME_RATE
        val frameDuration = (1000f / frameRate).roundToLong()
        
        _frameNavigationState.value = _frameNavigationState.value.copy(
            frameRate = frameRate,
            frameDurationMs = frameDuration
        )
    }
    
    // Frame-by-Frame Navigation
    
    fun seekToNextFrame() {
        exoPlayer?.let { player ->
            if (_frameNavigationState.value.isFrameByFrameMode) {
                val currentPosition = player.currentPosition
                val frameDuration = _frameNavigationState.value.frameDurationMs
                val nextFramePosition = currentPosition + frameDuration
                
                player.seekTo(nextFramePosition)
                updateFrameInfo(nextFramePosition)
            }
        }
    }
    
    fun seekToPreviousFrame() {
        exoPlayer?.let { player ->
            if (_frameNavigationState.value.isFrameByFrameMode) {
                val currentPosition = player.currentPosition
                val frameDuration = _frameNavigationState.value.frameDurationMs
                val prevFramePosition = (currentPosition - frameDuration).coerceAtLeast(0)
                
                player.seekTo(prevFramePosition)
                updateFrameInfo(prevFramePosition)
            }
        }
    }
    
    fun seekFrames(frameCount: Int) {
        exoPlayer?.let { player ->
            val currentPosition = player.currentPosition
            val frameDuration = _frameNavigationState.value.frameDurationMs
            val targetPosition = (currentPosition + (frameCount * frameDuration))
                .coerceIn(0, player.duration)
            
            player.seekTo(targetPosition)
            updateFrameInfo(targetPosition)
        }
    }
    
    fun enterFrameByFrameMode() {
        exoPlayer?.let { player ->
            // Pause playback
            player.pause()
            
            _frameNavigationState.value = _frameNavigationState.value.copy(
                isFrameByFrameMode = true,
                currentFrame = calculateCurrentFrame(player.currentPosition)
            )
        }
    }
    
    fun exitFrameByFrameMode() {
        _frameNavigationState.value = _frameNavigationState.value.copy(
            isFrameByFrameMode = false
        )
    }
    
    fun startContinuousFrameSeek(forward: Boolean) {
        frameSeekJob?.cancel()
        frameSeekJob = coroutineScope.launch {
            while (true) {
                if (forward) {
                    seekToNextFrame()
                } else {
                    seekToPreviousFrame()
                }
                delay(FRAME_SEEK_DELAY_MS)
            }
        }
    }
    
    fun stopContinuousFrameSeek() {
        frameSeekJob?.cancel()
        frameSeekJob = null
    }
    
    private fun calculateCurrentFrame(positionMs: Long): Long {
        val frameDuration = _frameNavigationState.value.frameDurationMs
        return if (frameDuration > 0) positionMs / frameDuration else 0
    }
    
    private fun updateFrameInfo(positionMs: Long) {
        val currentFrame = calculateCurrentFrame(positionMs)
        val totalFrames = exoPlayer?.duration?.let { calculateCurrentFrame(it) } ?: 0
        
        _frameNavigationState.value = _frameNavigationState.value.copy(
            currentFrame = currentFrame,
            totalFrames = totalFrames
        )
    }
    
    // Video Zoom and Pan Controls
    
    fun createZoomPanGestureListener(videoView: View): GestureListener {
        viewWidth = videoView.width
        viewHeight = videoView.height
        
        return GestureListener(
            context = context,
            onZoom = { scaleFactor, focusX, focusY ->
                handleZoom(scaleFactor, focusX, focusY)
            },
            onPan = { dx, dy ->
                handlePan(dx, dy)
            },
            onDoubleTap = { x, y ->
                handleDoubleTap(x, y)
            },
            onLongPress = {
                enterFrameByFrameMode()
            }
        )
    }
    
    private fun handleZoom(scaleFactor: Float, focusX: Float, focusY: Float) {
        val newScale = (currentScale * scaleFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        
        if (newScale != currentScale) {
            val scaleDelta = newScale / currentScale
            
            // Adjust translation to zoom towards focus point
            currentTranslateX = focusX - (focusX - currentTranslateX) * scaleDelta
            currentTranslateY = focusY - (focusY - currentTranslateY) * scaleDelta
            
            currentScale = newScale
            
            // Constrain pan within bounds
            constrainPan()
            updateTransform()
        }
    }
    
    private fun handlePan(dx: Float, dy: Float) {
        if (currentScale > MIN_ZOOM) {
            currentTranslateX += dx
            currentTranslateY += dy
            
            constrainPan()
            updateTransform()
        }
    }
    
    private fun handleDoubleTap(x: Float, y: Float) {
        if (currentScale > MIN_ZOOM) {
            // Reset zoom
            animateZoomTo(MIN_ZOOM, viewWidth / 2f, viewHeight / 2f)
        } else {
            // Zoom in to 2x
            animateZoomTo(2f, x, y)
        }
    }
    
    private fun constrainPan() {
        val scaledWidth = viewWidth * currentScale
        val scaledHeight = viewHeight * currentScale
        
        val maxTranslateX = (scaledWidth - viewWidth) / 2f
        val maxTranslateY = (scaledHeight - viewHeight) / 2f
        
        currentTranslateX = currentTranslateX.coerceIn(-maxTranslateX, maxTranslateX)
        currentTranslateY = currentTranslateY.coerceIn(-maxTranslateY, maxTranslateY)
    }
    
    private fun updateTransform() {
        transformMatrix.reset()
        transformMatrix.postScale(currentScale, currentScale, viewWidth / 2f, viewHeight / 2f)
        transformMatrix.postTranslate(currentTranslateX, currentTranslateY)
        
        _zoomPanState.value = ZoomPanState(
            scale = currentScale,
            translateX = currentTranslateX,
            translateY = currentTranslateY,
            matrix = Matrix(transformMatrix),
            isZoomed = currentScale > MIN_ZOOM
        )
    }
    
    private fun animateZoomTo(targetScale: Float, focusX: Float, focusY: Float) {
        val animator = ValueAnimator.ofFloat(currentScale, targetScale)
        animator.duration = ZOOM_ANIMATION_DURATION
        
        val startTranslateX = currentTranslateX
        val startTranslateY = currentTranslateY
        
        animator.addUpdateListener { animation ->
            val scale = animation.animatedValue as Float
            val progress = animation.animatedFraction
            
            currentScale = scale
            
            if (targetScale == MIN_ZOOM) {
                // Animate back to center
                currentTranslateX = startTranslateX * (1f - progress)
                currentTranslateY = startTranslateY * (1f - progress)
            } else {
                // Zoom towards focus point
                val scaleDelta = scale / targetScale
                currentTranslateX = focusX - (focusX - startTranslateX) * scaleDelta
                currentTranslateY = focusY - (focusY - startTranslateY) * scaleDelta
            }
            
            constrainPan()
            updateTransform()
        }
        
        animator.start()
    }
    
    fun resetZoomPan() {
        currentScale = MIN_ZOOM
        currentTranslateX = 0f
        currentTranslateY = 0f
        updateTransform()
    }
    
    fun setZoom(scale: Float) {
        currentScale = scale.coerceIn(MIN_ZOOM, MAX_ZOOM)
        constrainPan()
        updateTransform()
    }
    
    fun getZoomLevel(): Float = currentScale
    
    fun isZoomed(): Boolean = currentScale > MIN_ZOOM
    
    // Advanced Playback Controls
    
    fun skipForward(seconds: Int = 10) {
        exoPlayer?.let { player ->
            val newPosition = (player.currentPosition + seconds * 1000).coerceAtMost(player.duration)
            player.seekTo(newPosition)
        }
    }
    
    fun skipBackward(seconds: Int = 10) {
        exoPlayer?.let { player ->
            val newPosition = (player.currentPosition - seconds * 1000).coerceAtLeast(0)
            player.seekTo(newPosition)
        }
    }
    
    fun seekToPercentage(percentage: Float) {
        exoPlayer?.let { player ->
            val position = (player.duration * percentage).roundToLong()
            player.seekTo(position)
        }
    }
    
    fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }
    
    fun setLoopMode(mode: LoopMode) {
        exoPlayer?.let { player ->
            player.repeatMode = when (mode) {
                LoopMode.OFF -> Player.REPEAT_MODE_OFF
                LoopMode.ONE -> Player.REPEAT_MODE_ONE
                LoopMode.ALL -> Player.REPEAT_MODE_ALL
            }
            
            _playbackControlState.value = _playbackControlState.value.copy(
                loopMode = mode
            )
        }
    }
    
    fun markLoopStart() {
        exoPlayer?.let { player ->
            _playbackControlState.value = _playbackControlState.value.copy(
                abLoopStart = player.currentPosition
            )
        }
    }
    
    fun markLoopEnd() {
        exoPlayer?.let { player ->
            val loopStart = _playbackControlState.value.abLoopStart
            if (loopStart != null && player.currentPosition > loopStart) {
                _playbackControlState.value = _playbackControlState.value.copy(
                    abLoopEnd = player.currentPosition,
                    isABLoopEnabled = true
                )
                startABLoop()
            }
        }
    }
    
    fun clearABLoop() {
        _playbackControlState.value = _playbackControlState.value.copy(
            abLoopStart = null,
            abLoopEnd = null,
            isABLoopEnabled = false
        )
    }
    
    private fun startABLoop() {
        val state = _playbackControlState.value
        if (state.isABLoopEnabled && state.abLoopStart != null && state.abLoopEnd != null) {
            exoPlayer?.let { player ->
                player.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        if (events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                            events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                            val playbackPosition = player.currentPosition
                            if (playbackPosition >= state.abLoopEnd) {
                                player.seekTo(state.abLoopStart)
                            }
                        }
                    }
                })
            }
        }
    }
    
    fun captureSnapshot(): Snapshot? {
        exoPlayer?.let { player ->
            return Snapshot(
                position = player.currentPosition,
                frame = calculateCurrentFrame(player.currentPosition),
                timestamp = System.currentTimeMillis()
            )
        }
        return null
    }
    
    fun restoreSnapshot(snapshot: Snapshot) {
        exoPlayer?.seekTo(snapshot.position)
    }
    
    fun release() {
        frameSeekJob?.cancel()
        exoPlayer = null
    }
}

class GestureListener(
    context: Context,
    private val onZoom: (scaleFactor: Float, focusX: Float, focusY: Float) -> Unit,
    private val onPan: (dx: Float, dy: Float) -> Unit,
    private val onDoubleTap: (x: Float, y: Float) -> Unit,
    private val onLongPress: () -> Unit
) {
    
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())
    
    private var lastX = 0f
    private var lastY = 0f
    private var isScaling = false
    
    fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        
        if (!isScaling) {
            gestureDetector.onTouchEvent(event)
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x
                    lastY = event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!scaleDetector.isInProgress) {
                        val dx = event.x - lastX
                        val dy = event.y - lastY
                        
                        if (abs(dx) > 5 || abs(dy) > 5) {
                            onPan(dx, dy)
                            lastX = event.x
                            lastY = event.y
                        }
                    }
                }
            }
        }
        
        return true
    }
    
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            return true
        }
        
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            onZoom(detector.scaleFactor, detector.focusX, detector.focusY)
            return true
        }
        
        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
        }
    }
    
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            onDoubleTap(e.x, e.y)
            return true
        }
        
        override fun onLongPress(e: MotionEvent) {
            onLongPress()
        }
    }
}

data class FrameNavigationState(
    val isFrameByFrameMode: Boolean = false,
    val currentFrame: Long = 0,
    val totalFrames: Long = 0,
    val frameRate: Float = 30f,
    val frameDurationMs: Long = 33
)

data class ZoomPanState(
    val scale: Float = 1f,
    val translateX: Float = 0f,
    val translateY: Float = 0f,
    val matrix: Matrix = Matrix(),
    val isZoomed: Boolean = false
)

data class PlaybackControlState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isEnded: Boolean = false,
    val loopMode: LoopMode = LoopMode.OFF,
    val abLoopStart: Long? = null,
    val abLoopEnd: Long? = null,
    val isABLoopEnabled: Boolean = false
)

enum class LoopMode {
    OFF, ONE, ALL
}

data class Snapshot(
    val position: Long,
    val frame: Long,
    val timestamp: Long
)