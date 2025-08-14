package com.astralstream.player.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comprehensive video features manager with aspect ratios, filters,
 * frame navigation, A-B repeat, and screenshot capture
 */
@Singleton
class VideoFeaturesManager @Inject constructor(
    private val context: Context
) {
    
    private val _videoState = MutableStateFlow(VideoFeaturesState())
    val videoState: StateFlow<VideoFeaturesState> = _videoState.asStateFlow()
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    companion object {
        private const val TAG = "VideoFeaturesManager"
        
        // Predefined aspect ratios
        val ASPECT_RATIOS = listOf(
            AspectRatio("Original", 0f, AspectRatioMode.ORIGINAL),
            AspectRatio("4:3", 4f / 3f, AspectRatioMode.FIXED),
            AspectRatio("16:9", 16f / 9f, AspectRatioMode.FIXED),
            AspectRatio("16:10", 16f / 10f, AspectRatioMode.FIXED),
            AspectRatio("21:9", 21f / 9f, AspectRatioMode.FIXED),
            AspectRatio("1:1", 1f, AspectRatioMode.FIXED),
            AspectRatio("Fill Screen", 0f, AspectRatioMode.FILL),
            AspectRatio("Fit Screen", 0f, AspectRatioMode.FIT),
            AspectRatio("Stretch", 0f, AspectRatioMode.STRETCH)
        )
        
        // Screen modes
        val SCREEN_MODES = listOf(
            ScreenMode("Normal", ScaleType.FIT_CENTER),
            ScreenMode("Fill", ScaleType.CENTER_CROP),
            ScreenMode("Stretch", ScaleType.FILL_XY),
            ScreenMode("Center", ScaleType.CENTER),
            ScreenMode("Zoom", ScaleType.CENTER_CROP_ZOOM)
        )
    }
    
    fun setAspectRatio(aspectRatio: AspectRatio) {
        _videoState.value = _videoState.value.copy(
            currentAspectRatio = aspectRatio
        )
        Log.d(TAG, "Set aspect ratio: ${aspectRatio.name}")
    }
    
    fun setScreenMode(screenMode: ScreenMode) {
        _videoState.value = _videoState.value.copy(
            currentScreenMode = screenMode
        )
        Log.d(TAG, "Set screen mode: ${screenMode.name}")
    }
    
    fun setVideoFilters(filters: VideoFilters) {
        _videoState.value = _videoState.value.copy(
            videoFilters = filters
        )
        Log.d(TAG, "Updated video filters: brightness=${filters.brightness}, contrast=${filters.contrast}")
    }
    
    fun setBrightness(brightness: Float) {
        val filters = _videoState.value.videoFilters.copy(
            brightness = brightness.coerceIn(-100f, 100f)
        )
        setVideoFilters(filters)
    }
    
    fun setContrast(contrast: Float) {
        val filters = _videoState.value.videoFilters.copy(
            contrast = contrast.coerceIn(0f, 200f)
        )
        setVideoFilters(filters)
    }
    
    fun setSaturation(saturation: Float) {
        val filters = _videoState.value.videoFilters.copy(
            saturation = saturation.coerceIn(0f, 200f)
        )
        setVideoFilters(filters)
    }
    
    fun setHue(hue: Float) {
        val filters = _videoState.value.videoFilters.copy(
            hue = hue.coerceIn(-180f, 180f)
        )
        setVideoFilters(filters)
    }
    
    fun setGamma(gamma: Float) {
        val filters = _videoState.value.videoFilters.copy(
            gamma = gamma.coerceIn(0.1f, 3.0f)
        )
        setVideoFilters(filters)
    }
    
    fun resetVideoFilters() {
        setVideoFilters(VideoFilters())
    }
    
    fun createColorMatrixFilter(): ColorMatrixColorFilter {
        val filters = _videoState.value.videoFilters
        val colorMatrix = ColorMatrix()
        
        // Apply brightness
        if (filters.brightness != 0f) {
            val brightnessMatrix = ColorMatrix()
            val brightnessValue = filters.brightness / 100f * 255f
            brightnessMatrix.set(floatArrayOf(
                1f, 0f, 0f, 0f, brightnessValue,
                0f, 1f, 0f, 0f, brightnessValue,
                0f, 0f, 1f, 0f, brightnessValue,
                0f, 0f, 0f, 1f, 0f
            ))
            colorMatrix.postConcat(brightnessMatrix)
        }
        
        // Apply contrast
        if (filters.contrast != 100f) {
            val contrastMatrix = ColorMatrix()
            val contrastValue = filters.contrast / 100f
            val translation = (1f - contrastValue) / 2f * 255f
            contrastMatrix.set(floatArrayOf(
                contrastValue, 0f, 0f, 0f, translation,
                0f, contrastValue, 0f, 0f, translation,
                0f, 0f, contrastValue, 0f, translation,
                0f, 0f, 0f, 1f, 0f
            ))
            colorMatrix.postConcat(contrastMatrix)
        }
        
        // Apply saturation
        if (filters.saturation != 100f) {
            val saturationMatrix = ColorMatrix()
            val saturationValue = filters.saturation / 100f
            saturationMatrix.setSaturation(saturationValue)
            colorMatrix.postConcat(saturationMatrix)
        }
        
        // Apply hue rotation
        if (filters.hue != 0f) {
            val hueMatrix = ColorMatrix()
            val hueValue = filters.hue
            hueMatrix.setRotate(0, hueValue) // Red
            hueMatrix.setRotate(1, hueValue) // Green
            hueMatrix.setRotate(2, hueValue) // Blue
            colorMatrix.postConcat(hueMatrix)
        }
        
        return ColorMatrixColorFilter(colorMatrix)
    }
    
    // Frame navigation
    fun setFrameNavigationMode(enabled: Boolean) {
        _videoState.value = _videoState.value.copy(
            isFrameNavigationEnabled = enabled
        )
    }
    
    fun stepFrame(forward: Boolean, frameRate: Float = 30f) {
        val frameTimeMs = (1000f / frameRate).toLong()
        val currentPosition = _videoState.value.currentPosition
        val newPosition = if (forward) {
            currentPosition + frameTimeMs
        } else {
            (currentPosition - frameTimeMs).coerceAtLeast(0)
        }
        
        _videoState.value = _videoState.value.copy(
            currentPosition = newPosition,
            isFrameStepping = true
        )
        
        Log.d(TAG, "Frame step ${if (forward) "forward" else "backward"} to position: $newPosition")
    }
    
    fun setFrameSteppingComplete() {
        _videoState.value = _videoState.value.copy(isFrameStepping = false)
    }
    
    // A-B Repeat functionality
    fun setRepeatAPoint(position: Long) {
        _videoState.value = _videoState.value.copy(
            repeatAPosition = position,
            isABRepeatSet = _videoState.value.repeatBPosition > position
        )
        Log.d(TAG, "Set A-B repeat point A: $position")
    }
    
    fun setRepeatBPoint(position: Long) {
        val aPosition = _videoState.value.repeatAPosition
        if (position > aPosition) {
            _videoState.value = _videoState.value.copy(
                repeatBPosition = position,
                isABRepeatSet = true
            )
            Log.d(TAG, "Set A-B repeat point B: $position")
        }
    }
    
    fun clearABRepeat() {
        _videoState.value = _videoState.value.copy(
            repeatAPosition = -1L,
            repeatBPosition = -1L,
            isABRepeatSet = false
        )
        Log.d(TAG, "Cleared A-B repeat")
    }
    
    fun isPositionInABRepeatRange(position: Long): Boolean {
        val state = _videoState.value
        return state.isABRepeatSet && 
               position >= state.repeatAPosition && 
               position <= state.repeatBPosition
    }
    
    // Screenshot functionality
    fun captureScreenshot(videoBitmap: Bitmap?, callback: (Boolean, String?) -> Unit) {
        if (videoBitmap == null) {
            callback(false, "No video frame available")
            return
        }
        
        coroutineScope.launch {
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "AstralStream_Screenshot_$timestamp.png"
                
                val result = withContext(Dispatchers.IO) {
                    saveScreenshotToFile(videoBitmap, fileName)
                }
                
                if (result.success) {
                    _videoState.value = _videoState.value.copy(
                        lastScreenshotPath = result.filePath
                    )
                    callback(true, result.filePath)
                    Log.d(TAG, "Screenshot saved: ${result.filePath}")
                } else {
                    callback(false, result.error)
                    Log.e(TAG, "Screenshot failed: ${result.error}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Screenshot capture failed", e)
                callback(false, "Screenshot capture failed: ${e.message}")
            }
        }
    }
    
    private suspend fun saveScreenshotToFile(bitmap: Bitmap, fileName: String): ScreenshotResult {
        return withContext(Dispatchers.IO) {
            try {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val appDir = File(picturesDir, "AstralStream")
                if (!appDir.exists()) {
                    appDir.mkdirs()
                }
                
                val file = File(appDir, fileName)
                val fileOutputStream = FileOutputStream(file)
                
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
                fileOutputStream.flush()
                fileOutputStream.close()
                
                ScreenshotResult(true, file.absolutePath, null)
                
            } catch (e: Exception) {
                ScreenshotResult(false, null, e.message)
            }
        }
    }
    
    // Zoom functionality
    fun setZoomLevel(zoomLevel: Float) {
        val clampedZoom = zoomLevel.coerceIn(0.5f, 4.0f)
        _videoState.value = _videoState.value.copy(zoomLevel = clampedZoom)
    }
    
    fun setZoomCenter(x: Float, y: Float) {
        _videoState.value = _videoState.value.copy(
            zoomCenterX = x,
            zoomCenterY = y
        )
    }
    
    fun resetZoom() {
        _videoState.value = _videoState.value.copy(
            zoomLevel = 1.0f,
            zoomCenterX = 0.5f,
            zoomCenterY = 0.5f
        )
    }
    
    // Rotation
    fun setRotation(degrees: Float) {
        val normalizedRotation = degrees % 360f
        _videoState.value = _videoState.value.copy(rotation = normalizedRotation)
    }
    
    fun rotateVideo(clockwise: Boolean = true) {
        val currentRotation = _videoState.value.rotation
        val newRotation = if (clockwise) {
            currentRotation + 90f
        } else {
            currentRotation - 90f
        }
        setRotation(newRotation)
    }
    
    fun resetRotation() {
        setRotation(0f)
    }
    
    // Video information
    fun updateVideoInfo(width: Int, height: Int, frameRate: Float, bitrate: Long) {
        _videoState.value = _videoState.value.copy(
            videoInfo = VideoInfo(
                width = width,
                height = height,
                aspectRatio = if (height != 0) width.toFloat() / height.toFloat() else 16f / 9f,
                frameRate = frameRate,
                bitrate = bitrate
            )
        )
    }
    
    fun setCurrentPosition(position: Long) {
        _videoState.value = _videoState.value.copy(currentPosition = position)
    }
    
    fun getAvailableAspectRatios(): List<AspectRatio> = ASPECT_RATIOS
    fun getAvailableScreenModes(): List<ScreenMode> = SCREEN_MODES
    
    // Presets
    fun applyVideoPreset(preset: VideoPreset) {
        when (preset) {
            VideoPreset.CINEMA -> {
                setVideoFilters(VideoFilters(
                    brightness = 10f,
                    contrast = 110f,
                    saturation = 105f,
                    hue = 0f,
                    gamma = 1.1f
                ))
            }
            VideoPreset.VIVID -> {
                setVideoFilters(VideoFilters(
                    brightness = 5f,
                    contrast = 120f,
                    saturation = 130f,
                    hue = 0f,
                    gamma = 1.0f
                ))
            }
            VideoPreset.SOFT -> {
                setVideoFilters(VideoFilters(
                    brightness = -5f,
                    contrast = 90f,
                    saturation = 85f,
                    hue = 0f,
                    gamma = 0.9f
                ))
            }
            VideoPreset.WARM -> {
                setVideoFilters(VideoFilters(
                    brightness = 0f,
                    contrast = 100f,
                    saturation = 100f,
                    hue = -10f,
                    gamma = 1.0f
                ))
            }
            VideoPreset.COOL -> {
                setVideoFilters(VideoFilters(
                    brightness = 0f,
                    contrast = 100f,
                    saturation = 100f,
                    hue = 10f,
                    gamma = 1.0f
                ))
            }
            VideoPreset.NORMAL -> {
                resetVideoFilters()
            }
        }
        
        _videoState.value = _videoState.value.copy(currentPreset = preset)
    }
}

// Data classes
data class VideoFeaturesState(
    val currentAspectRatio: AspectRatio = VideoFeaturesManager.ASPECT_RATIOS.first(),
    val currentScreenMode: ScreenMode = VideoFeaturesManager.SCREEN_MODES.first(),
    val videoFilters: VideoFilters = VideoFilters(),
    val currentPreset: VideoPreset = VideoPreset.NORMAL,
    
    // Frame navigation
    val isFrameNavigationEnabled: Boolean = false,
    val isFrameStepping: Boolean = false,
    val currentPosition: Long = 0L,
    
    // A-B Repeat
    val repeatAPosition: Long = -1L,
    val repeatBPosition: Long = -1L,
    val isABRepeatSet: Boolean = false,
    
    // Screenshot
    val lastScreenshotPath: String? = null,
    
    // Zoom and rotation
    val zoomLevel: Float = 1.0f,
    val zoomCenterX: Float = 0.5f,
    val zoomCenterY: Float = 0.5f,
    val rotation: Float = 0f,
    
    // Video information
    val videoInfo: VideoInfo? = null
)

data class AspectRatio(
    val name: String,
    val ratio: Float, // 0 for special modes
    val mode: AspectRatioMode
)

enum class AspectRatioMode {
    ORIGINAL, FIXED, FILL, FIT, STRETCH
}

data class ScreenMode(
    val name: String,
    val scaleType: ScaleType
)

enum class ScaleType {
    FIT_CENTER, CENTER_CROP, FILL_XY, CENTER, CENTER_CROP_ZOOM
}

data class VideoFilters(
    val brightness: Float = 0f,    // -100 to 100
    val contrast: Float = 100f,    // 0 to 200
    val saturation: Float = 100f,  // 0 to 200
    val hue: Float = 0f,          // -180 to 180
    val gamma: Float = 1.0f       // 0.1 to 3.0
)

enum class VideoPreset {
    NORMAL, CINEMA, VIVID, SOFT, WARM, COOL
}

data class VideoInfo(
    val width: Int,
    val height: Int,
    val aspectRatio: Float,
    val frameRate: Float,
    val bitrate: Long
)

private data class ScreenshotResult(
    val success: Boolean,
    val filePath: String?,
    val error: String?
)