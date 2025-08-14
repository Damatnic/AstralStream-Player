package com.astralstream.player.gestures

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.absoluteValue

/**
 * Comprehensive gesture manager for video player controls
 * Supports swipe gestures for volume/brightness, pinch to zoom, double-tap seek
 */
class PlayerGestureManager(private val context: Context) {
    
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    
    companion object {
        const val TAG = "PlayerGestureManager"
        const val DOUBLE_TAP_TIMEOUT = 300L
        const val MIN_SWIPE_DISTANCE = 50f
        const val MIN_ZOOM_SCALE = 0.5f
        const val MAX_ZOOM_SCALE = 3.0f
        const val SEEK_SENSITIVITY = 0.001f
        const val VOLUME_BRIGHTNESS_SENSITIVITY = 0.002f
    }
    
    fun vibrate(duration: Long = 50L) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(duration)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed", e)
        }
    }
    
    interface GestureListener {
        fun onSingleTap()
        fun onDoubleTap(x: Float, y: Float)
        fun onLongPress(x: Float, y: Float)
        
        fun onVolumeGesture(deltaY: Float, screenHeight: Int)
        fun onBrightnessGesture(deltaY: Float, screenHeight: Int)
        fun onSeekGesture(deltaX: Float, screenWidth: Int)
        
        fun onZoomGesture(scaleFactor: Float, focusX: Float, focusY: Float)
        fun onZoomEnd()
        
        fun onSwipeUp()
        fun onSwipeDown()
        fun onSwipeLeft()
        fun onSwipeRight()
    }
    
}

/**
 * Composable modifier for handling player gestures
 */
@Composable
fun Modifier.playerGestures(
    onSingleTap: () -> Unit = {},
    onDoubleTap: (Offset) -> Unit = {},
    onLongPress: (Offset) -> Unit = {},
    onVolumeGesture: (Float, Int) -> Unit = { _, _ -> },
    onBrightnessGesture: (Float, Int) -> Unit = { _, _ -> },
    onSeekGesture: (Float, Int) -> Unit = { _, _ -> },
    onZoomGesture: (Float, Offset) -> Unit = { _, _ -> },
    onZoomEnd: () -> Unit = {},
    onSwipeUp: () -> Unit = {},
    onSwipeDown: () -> Unit = {},
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {}
): Modifier {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx().toInt() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx().toInt() }
    
    val gestureManager = remember { PlayerGestureManager(context) }
    
    return this
        .pointerInput(Unit) {
            // Handle tap gestures
            detectTapGestures(
                onTap = { offset ->
                    onSingleTap()
                },
                onDoubleTap = { offset ->
                    onDoubleTap(offset)
                    gestureManager.vibrate(30L)
                },
                onLongPress = { offset ->
                    onLongPress(offset)
                    gestureManager.vibrate(100L)
                }
            )
        }
        .pointerInput(Unit) {
            // Handle drag gestures for volume/brightness/seek
            detectDragGestures(
                onDragStart = { offset ->
                    // Determine gesture type based on start position
                },
                onDragEnd = {
                    // Handle drag end
                },
                onDrag = { change, dragAmount ->
                    val startX = change.position.x - dragAmount.x
                    val startY = change.position.y - dragAmount.y
                    
                    val isLeftHalf = startX < screenWidth * 0.5f
                    val isRightHalf = startX >= screenWidth * 0.5f
                    
                    when {
                        // Vertical swipes for brightness and volume (MX Player style)
                        abs(dragAmount.y) > abs(dragAmount.x) * 1.5 -> {
                            when {
                                isLeftHalf -> {
                                    // Left half - brightness control (swipe up = brighter, down = darker)
                                    onBrightnessGesture(-dragAmount.y, screenHeight)
                                }
                                isRightHalf -> {
                                    // Right half - volume control (swipe up = louder, down = quieter)
                                    onVolumeGesture(-dragAmount.y, screenHeight)
                                }
                            }
                        }
                        // Horizontal swipes for seeking (MX Player style)
                        abs(dragAmount.x) > abs(dragAmount.y) -> {
                            // Seek anywhere on screen with horizontal swipe
                            val seekAmount = (dragAmount.x / screenWidth) * 30000 // Scale to 30 seconds max
                            onSeekGesture(seekAmount, screenWidth)
                        }
                    }
                }
            )
        }
        .pointerInput(Unit) {
            // Handle pinch-to-zoom gestures
            detectTransformGestures(
                onGesture = { _, pan, zoom, _ ->
                    if (zoom != 1f) {
                        onZoomGesture(zoom, pan)
                    }
                }
            )
        }
}

/**
 * Legacy View-based gesture detector for compatibility
 */
class LegacyPlayerGestureDetector(
    context: Context,
    private val listener: PlayerGestureManager.GestureListener
) {
    
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            listener.onSingleTap()
            return true
        }
        
        override fun onDoubleTap(e: MotionEvent): Boolean {
            listener.onDoubleTap(e.x, e.y)
            return true
        }
        
        override fun onLongPress(e: MotionEvent) {
            listener.onLongPress(e.x, e.y)
        }
        
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            e1?.let { startEvent ->
                handleScrollGesture(startEvent, e2, distanceX, distanceY)
            }
            return true
        }
        
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            e1?.let { startEvent ->
                handleFlingGesture(startEvent, e2, velocityX, velocityY)
            }
            return true
        }
    })
    
    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            listener.onZoomGesture(
                detector.scaleFactor,
                detector.focusX,
                detector.focusY
            )
            return true
        }
        
        override fun onScaleEnd(detector: ScaleGestureDetector) {
            listener.onZoomEnd()
        }
    })
    
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private var screenWidth = 0
    private var screenHeight = 0
    
    fun setScreenSize(width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
    }
    
    fun onTouchEvent(event: MotionEvent): Boolean {
        var handled = false
        
        // Handle scale gestures first
        handled = scaleGestureDetector.onTouchEvent(event) || handled
        
        // Handle other gestures if not scaling
        if (!scaleGestureDetector.isInProgress) {
            handled = gestureDetector.onTouchEvent(event) || handled
        }
        
        return handled
    }
    
    private fun handleScrollGesture(
        startEvent: MotionEvent,
        currentEvent: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ) {
        val startX = startEvent.x
        val deltaX = currentEvent.x - startX
        val deltaY = currentEvent.y - startEvent.y
        
        val isLeftSide = startX < screenWidth * 0.3f
        val isRightSide = startX > screenWidth * 0.7f
        val isCenterArea = !isLeftSide && !isRightSide
        
        when {
            // Vertical gestures
            abs(deltaY) > abs(deltaX) && abs(deltaY) > PlayerGestureManager.MIN_SWIPE_DISTANCE -> {
                when {
                    isLeftSide -> {
                        listener.onBrightnessGesture(-distanceY, screenHeight)
                        vibrateLight()
                    }
                    isRightSide -> {
                        listener.onVolumeGesture(-distanceY, screenHeight)
                        vibrateLight()
                    }
                }
            }
            // Horizontal gestures
            abs(deltaX) > abs(deltaY) && abs(deltaX) > PlayerGestureManager.MIN_SWIPE_DISTANCE -> {
                if (isCenterArea) {
                    listener.onSeekGesture(-distanceX, screenWidth)
                    vibrateLight()
                }
            }
        }
    }
    
    private fun handleFlingGesture(
        startEvent: MotionEvent,
        endEvent: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ) {
        val deltaX = endEvent.x - startEvent.x
        val deltaY = endEvent.y - startEvent.y
        
        val isHorizontalFling = abs(velocityX) > abs(velocityY)
        val isVerticalFling = abs(velocityY) > abs(velocityX)
        
        when {
            isHorizontalFling && abs(deltaX) > PlayerGestureManager.MIN_SWIPE_DISTANCE -> {
                if (deltaX > 0) {
                    listener.onSwipeRight()
                } else {
                    listener.onSwipeLeft()
                }
                vibrateMedium()
            }
            isVerticalFling && abs(deltaY) > PlayerGestureManager.MIN_SWIPE_DISTANCE -> {
                if (deltaY > 0) {
                    listener.onSwipeDown()
                } else {
                    listener.onSwipeUp()
                }
                vibrateMedium()
            }
        }
    }
    
    private fun vibrateLight() {
        vibrate(25L)
    }
    
    private fun vibrateMedium() {
        vibrate(50L)
    }
    
    fun vibrate(duration: Long) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(duration)
            }
        } catch (e: Exception) {
            Log.w(PlayerGestureManager.TAG, "Vibration failed", e)
        }
    }
}

/**
 * Gesture configuration data class
 */
data class GestureConfig(
    val enableVolumeGesture: Boolean = true,
    val enableBrightnessGesture: Boolean = true,
    val enableSeekGesture: Boolean = true,
    val enableZoomGesture: Boolean = true,
    val enableSwipeGestures: Boolean = true,
    val enableDoubleTapSeek: Boolean = true,
    val enableVibration: Boolean = true,
    val seekSensitivity: Float = 1.0f,
    val volumeSensitivity: Float = 1.0f,
    val brightnessSensitivity: Float = 1.0f,
    val zoomSensitivity: Float = 1.0f,
    val doubleTapSeekSeconds: Int = 10
)

/**
 * Gesture state for tracking ongoing gestures
 */
data class GestureState(
    val isZooming: Boolean = false,
    val isScrolling: Boolean = false,
    val currentZoomScale: Float = 1.0f,
    val zoomFocusX: Float = 0f,
    val zoomFocusY: Float = 0f,
    val lastTapTime: Long = 0L,
    val lastTapX: Float = 0f,
    val lastTapY: Float = 0f
)