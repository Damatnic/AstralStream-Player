package com.astralstream.player.gestures

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class GestureCustomization @Inject constructor(
    private val context: Context
) {
    
    companion object {
        private const val PREFS_NAME = "gesture_prefs"
        private const val KEY_GESTURE_CONFIG = "gesture_config"
        
        // Default gesture zones (as percentage of screen)
        private const val ZONE_LEFT = 0.33f
        private const val ZONE_RIGHT = 0.67f
        private const val ZONE_TOP = 0.33f
        private const val ZONE_BOTTOM = 0.67f
        
        // Gesture thresholds
        private const val SWIPE_THRESHOLD = 100
        private const val VELOCITY_THRESHOLD = 100
        private const val LONG_PRESS_DURATION = 500L
        private const val DOUBLE_TAP_TIMEOUT = 300L
    }
    
    private val _gestureConfig = MutableStateFlow(loadCustomGestureConfig())
    val gestureConfig: StateFlow<CustomGestureConfig> = _gestureConfig.asStateFlow()
    
    private val _gestureHistory = MutableStateFlow<List<GestureEvent>>(emptyList())
    val gestureHistory: StateFlow<List<GestureEvent>> = _gestureHistory.asStateFlow()
    
    // Gesture Configuration
    
    fun updateGestureMapping(gesture: GestureType, zone: ScreenZone, action: PlayerAction) {
        val config = _gestureConfig.value
        val updatedMappings = config.gestureMappings.toMutableMap()
        updatedMappings[GestureKey(gesture, zone)] = action
        
        val newConfig = config.copy(gestureMappings = updatedMappings)
        _gestureConfig.value = newConfig
        saveCustomGestureConfig(newConfig)
    }
    
    fun removeGestureMapping(gesture: GestureType, zone: ScreenZone) {
        val config = _gestureConfig.value
        val updatedMappings = config.gestureMappings.toMutableMap()
        updatedMappings.remove(GestureKey(gesture, zone))
        
        val newConfig = config.copy(gestureMappings = updatedMappings)
        _gestureConfig.value = newConfig
        saveCustomGestureConfig(newConfig)
    }
    
    fun setSensitivity(sensitivity: GestureSensitivity) {
        val config = _gestureConfig.value.copy(sensitivity = sensitivity)
        _gestureConfig.value = config
        saveCustomGestureConfig(config)
    }
    
    fun setZoneBoundaries(
        leftBoundary: Float,
        rightBoundary: Float,
        topBoundary: Float,
        bottomBoundary: Float
    ) {
        val config = _gestureConfig.value.copy(
            zoneBoundaries = ZoneBoundaries(
                left = leftBoundary.coerceIn(0f, 0.5f),
                right = rightBoundary.coerceIn(0.5f, 1f),
                top = topBoundary.coerceIn(0f, 0.5f),
                bottom = bottomBoundary.coerceIn(0.5f, 1f)
            )
        )
        _gestureConfig.value = config
        saveCustomGestureConfig(config)
    }
    
    fun enableGesture(gesture: GestureType, enabled: Boolean) {
        val config = _gestureConfig.value
        val updatedEnabled = config.enabledGestures.toMutableSet()
        
        if (enabled) {
            updatedEnabled.add(gesture)
        } else {
            updatedEnabled.remove(gesture)
        }
        
        val newConfig = config.copy(enabledGestures = updatedEnabled)
        _gestureConfig.value = newConfig
        saveCustomGestureConfig(newConfig)
    }
    
    fun resetToDefaults() {
        val defaultConfig = getDefaultCustomGestureConfig()
        _gestureConfig.value = defaultConfig
        saveCustomGestureConfig(defaultConfig)
    }
    
    fun loadPreset(preset: GesturePreset) {
        val config = when (preset) {
            GesturePreset.DEFAULT -> getDefaultCustomGestureConfig()
            GesturePreset.MINIMAL -> getMinimalCustomGestureConfig()
            GesturePreset.ADVANCED -> getAdvancedCustomGestureConfig()
            GesturePreset.CUSTOM -> _gestureConfig.value // Keep current
        }
        
        _gestureConfig.value = config
        saveCustomGestureConfig(config)
    }
    
    // Gesture Detection
    
    fun createGestureDetector(
        onGestureDetected: (GestureEvent) -> Unit
    ): CustomGestureDetector {
        return CustomGestureDetector(
            context = context,
            config = _gestureConfig.value,
            onGestureDetected = { event ->
                recordGesture(event)
                onGestureDetected(event)
            }
        )
    }
    
    private fun recordGesture(event: GestureEvent) {
        val history = _gestureHistory.value.toMutableList()
        history.add(0, event)
        if (history.size > 50) {
            history.removeLast()
        }
        _gestureHistory.value = history
    }
    
    fun getActionForGesture(gesture: GestureType, zone: ScreenZone): PlayerAction? {
        val config = _gestureConfig.value
        
        // Check if gesture is enabled
        if (!config.enabledGestures.contains(gesture)) {
            return null
        }
        
        // Get action for gesture and zone
        return config.gestureMappings[GestureKey(gesture, zone)]
    }
    
    fun getActionForGesture(gesture: GestureType, x: Float, y: Float, screenWidth: Int, screenHeight: Int): PlayerAction? {
        val zone = getScreenZone(x, y, screenWidth, screenHeight)
        return getActionForGesture(gesture, zone)
    }
    
    private fun getScreenZone(x: Float, y: Float, width: Int, height: Int): ScreenZone {
        val boundaries = _gestureConfig.value.zoneBoundaries
        val xPercent = x / width
        val yPercent = y / height
        
        return when {
            xPercent < boundaries.left && yPercent < boundaries.top -> ScreenZone.TOP_LEFT
            xPercent > boundaries.right && yPercent < boundaries.top -> ScreenZone.TOP_RIGHT
            xPercent < boundaries.left && yPercent > boundaries.bottom -> ScreenZone.BOTTOM_LEFT
            xPercent > boundaries.right && yPercent > boundaries.bottom -> ScreenZone.BOTTOM_RIGHT
            xPercent < boundaries.left -> ScreenZone.LEFT
            xPercent > boundaries.right -> ScreenZone.RIGHT
            yPercent < boundaries.top -> ScreenZone.TOP
            yPercent > boundaries.bottom -> ScreenZone.BOTTOM
            else -> ScreenZone.CENTER
        }
    }
    
    // Preset Configurations
    
    private fun getDefaultCustomGestureConfig() = CustomGestureConfig(
        gestureMappings = mapOf(
            // Single tap
            GestureKey(GestureType.SINGLE_TAP, ScreenZone.CENTER) to PlayerAction.PLAY_PAUSE,
            
            // Double tap
            GestureKey(GestureType.DOUBLE_TAP, ScreenZone.LEFT) to PlayerAction.SEEK_BACKWARD,
            GestureKey(GestureType.DOUBLE_TAP, ScreenZone.RIGHT) to PlayerAction.SEEK_FORWARD,
            GestureKey(GestureType.DOUBLE_TAP, ScreenZone.CENTER) to PlayerAction.TOGGLE_FULLSCREEN,
            
            // Swipe horizontal
            GestureKey(GestureType.SWIPE_LEFT, ScreenZone.CENTER) to PlayerAction.NEXT_VIDEO,
            GestureKey(GestureType.SWIPE_RIGHT, ScreenZone.CENTER) to PlayerAction.PREVIOUS_VIDEO,
            
            // Swipe vertical
            GestureKey(GestureType.SWIPE_UP, ScreenZone.LEFT) to PlayerAction.VOLUME_UP,
            GestureKey(GestureType.SWIPE_DOWN, ScreenZone.LEFT) to PlayerAction.VOLUME_DOWN,
            GestureKey(GestureType.SWIPE_UP, ScreenZone.RIGHT) to PlayerAction.BRIGHTNESS_UP,
            GestureKey(GestureType.SWIPE_DOWN, ScreenZone.RIGHT) to PlayerAction.BRIGHTNESS_DOWN,
            
            // Long press
            GestureKey(GestureType.LONG_PRESS, ScreenZone.CENTER) to PlayerAction.SHOW_INFO,
            
            // Pinch
            GestureKey(GestureType.PINCH_IN, ScreenZone.CENTER) to PlayerAction.ZOOM_OUT,
            GestureKey(GestureType.PINCH_OUT, ScreenZone.CENTER) to PlayerAction.ZOOM_IN
        ),
        enabledGestures = GestureType.values().toSet(),
        sensitivity = GestureSensitivity.NORMAL,
        zoneBoundaries = ZoneBoundaries()
    )
    
    private fun getMinimalCustomGestureConfig() = CustomGestureConfig(
        gestureMappings = mapOf(
            GestureKey(GestureType.SINGLE_TAP, ScreenZone.CENTER) to PlayerAction.PLAY_PAUSE,
            GestureKey(GestureType.DOUBLE_TAP, ScreenZone.LEFT) to PlayerAction.SEEK_BACKWARD,
            GestureKey(GestureType.DOUBLE_TAP, ScreenZone.RIGHT) to PlayerAction.SEEK_FORWARD
        ),
        enabledGestures = setOf(
            GestureType.SINGLE_TAP,
            GestureType.DOUBLE_TAP
        ),
        sensitivity = GestureSensitivity.LOW,
        zoneBoundaries = ZoneBoundaries()
    )
    
    private fun getAdvancedCustomGestureConfig() = CustomGestureConfig(
        gestureMappings = getDefaultCustomGestureConfig().gestureMappings + mapOf(
            // Additional advanced gestures
            GestureKey(GestureType.TRIPLE_TAP, ScreenZone.CENTER) to PlayerAction.TOGGLE_PIP,
            GestureKey(GestureType.TWO_FINGER_TAP, ScreenZone.CENTER) to PlayerAction.SCREENSHOT,
            GestureKey(GestureType.TWO_FINGER_SWIPE_UP, ScreenZone.CENTER) to PlayerAction.SPEED_UP,
            GestureKey(GestureType.TWO_FINGER_SWIPE_DOWN, ScreenZone.CENTER) to PlayerAction.SPEED_DOWN,
            GestureKey(GestureType.ROTATE, ScreenZone.CENTER) to PlayerAction.ROTATE_SCREEN
        ),
        enabledGestures = GestureType.values().toSet(),
        sensitivity = GestureSensitivity.HIGH,
        zoneBoundaries = ZoneBoundaries()
    )
    
    // Persistence
    
    private fun loadCustomGestureConfig(): CustomGestureConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val configJson = prefs.getString(KEY_GESTURE_CONFIG, null)
        
        return if (configJson != null) {
            try {
                CustomGestureConfig.fromJson(JSONObject(configJson))
            } catch (e: Exception) {
                getDefaultCustomGestureConfig()
            }
        } else {
            getDefaultCustomGestureConfig()
        }
    }
    
    private fun saveCustomGestureConfig(config: CustomGestureConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_GESTURE_CONFIG, config.toJson().toString())
            .apply()
    }
    
    // Export/Import
    
    fun exportConfiguration(): String {
        return _gestureConfig.value.toJson().toString(2)
    }
    
    fun importConfiguration(json: String): Boolean {
        return try {
            val config = CustomGestureConfig.fromJson(JSONObject(json))
            _gestureConfig.value = config
            saveCustomGestureConfig(config)
            true
        } catch (e: Exception) {
            false
        }
    }
}

// Custom Gesture Detector

class CustomGestureDetector(
    private val context: Context,
    private val config: CustomGestureConfig,
    private val onGestureDetected: (GestureEvent) -> Unit
) {
    
    private val gestureDetector: GestureDetector
    private var lastTapTime = 0L
    private var tapCount = 0
    private var startX = 0f
    private var startY = 0f
    private var startTime = 0L
    private var fingerCount = 0
    
    init {
        gestureDetector = GestureDetector(context, GestureListener())
    }
    
    fun onTouchEvent(event: MotionEvent): Boolean {
        fingerCount = event.pointerCount
        
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                startTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Multi-finger gesture started
            }
        }
        
        return gestureDetector.onTouchEvent(event)
    }
    
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onGestureDetected(GestureEvent(
                type = GestureType.SINGLE_TAP,
                x = e.x,
                y = e.y,
                timestamp = System.currentTimeMillis()
            ))
            return true
        }
        
        override fun onDoubleTap(e: MotionEvent): Boolean {
            onGestureDetected(GestureEvent(
                type = GestureType.DOUBLE_TAP,
                x = e.x,
                y = e.y,
                timestamp = System.currentTimeMillis()
            ))
            return true
        }
        
        override fun onLongPress(e: MotionEvent) {
            onGestureDetected(GestureEvent(
                type = GestureType.LONG_PRESS,
                x = e.x,
                y = e.y,
                timestamp = System.currentTimeMillis()
            ))
        }
        
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false
            
            val deltaX = e2.x - e1.x
            val deltaY = e2.y - e1.y
            
            val type = when {
                abs(deltaX) > abs(deltaY) -> {
                    if (deltaX > 0) {
                        if (fingerCount == 2) GestureType.TWO_FINGER_SWIPE_RIGHT
                        else GestureType.SWIPE_RIGHT
                    } else {
                        if (fingerCount == 2) GestureType.TWO_FINGER_SWIPE_LEFT
                        else GestureType.SWIPE_LEFT
                    }
                }
                else -> {
                    if (deltaY > 0) {
                        if (fingerCount == 2) GestureType.TWO_FINGER_SWIPE_DOWN
                        else GestureType.SWIPE_DOWN
                    } else {
                        if (fingerCount == 2) GestureType.TWO_FINGER_SWIPE_UP
                        else GestureType.SWIPE_UP
                    }
                }
            }
            
            onGestureDetected(GestureEvent(
                type = type,
                x = e2.x,
                y = e2.y,
                deltaX = deltaX,
                deltaY = deltaY,
                velocity = velocityX,
                timestamp = System.currentTimeMillis()
            ))
            
            return true
        }
    }
}

// Data Classes

// Custom Gesture Configuration
data class CustomGestureConfig(
    val gestureMappings: Map<GestureKey, PlayerAction>,
    val enabledGestures: Set<GestureType>,
    val sensitivity: GestureSensitivity,
    val zoneBoundaries: ZoneBoundaries
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("mappings", JSONObject().apply {
            gestureMappings.forEach { (key, action) ->
                put(key.toString(), action.name)
            }
        })
        put("enabled", enabledGestures.map { it.name })
        put("sensitivity", sensitivity.name)
        put("boundaries", zoneBoundaries.toJson())
    }
    
    companion object {
        fun fromJson(json: JSONObject): CustomGestureConfig {
            val mappings = mutableMapOf<GestureKey, PlayerAction>()
            val mappingsJson = json.getJSONObject("mappings")
            mappingsJson.keys().forEach { key ->
                val parts = key.split("_")
                if (parts.size == 2) {
                    val gestureType = GestureType.valueOf(parts[0])
                    val zone = ScreenZone.valueOf(parts[1])
                    val action = PlayerAction.valueOf(mappingsJson.getString(key))
                    mappings[GestureKey(gestureType, zone)] = action
                }
            }
            
            val enabledArray = json.getJSONArray("enabled")
            val enabled = mutableSetOf<GestureType>()
            for (i in 0 until enabledArray.length()) {
                enabled.add(GestureType.valueOf(enabledArray.getString(i)))
            }
            
            return CustomGestureConfig(
                gestureMappings = mappings,
                enabledGestures = enabled,
                sensitivity = GestureSensitivity.valueOf(json.getString("sensitivity")),
                zoneBoundaries = ZoneBoundaries.fromJson(json.getJSONObject("boundaries"))
            )
        }
    }
}

data class GestureKey(
    val gesture: GestureType,
    val zone: ScreenZone
) {
    override fun toString(): String = "${gesture.name}_${zone.name}"
}

data class ZoneBoundaries(
    val left: Float = 0.33f,
    val right: Float = 0.67f,
    val top: Float = 0.33f,
    val bottom: Float = 0.67f
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("left", left)
        put("right", right)
        put("top", top)
        put("bottom", bottom)
    }
    
    companion object {
        fun fromJson(json: JSONObject): ZoneBoundaries = ZoneBoundaries(
            left = json.getDouble("left").toFloat(),
            right = json.getDouble("right").toFloat(),
            top = json.getDouble("top").toFloat(),
            bottom = json.getDouble("bottom").toFloat()
        )
    }
}

data class GestureEvent(
    val type: GestureType,
    val x: Float,
    val y: Float,
    val deltaX: Float = 0f,
    val deltaY: Float = 0f,
    val velocity: Float = 0f,
    val timestamp: Long
)

enum class GestureType {
    SINGLE_TAP,
    DOUBLE_TAP,
    TRIPLE_TAP,
    LONG_PRESS,
    SWIPE_UP,
    SWIPE_DOWN,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    PINCH_IN,
    PINCH_OUT,
    TWO_FINGER_TAP,
    TWO_FINGER_SWIPE_UP,
    TWO_FINGER_SWIPE_DOWN,
    TWO_FINGER_SWIPE_LEFT,
    TWO_FINGER_SWIPE_RIGHT,
    ROTATE
}

enum class ScreenZone {
    TOP_LEFT, TOP, TOP_RIGHT,
    LEFT, CENTER, RIGHT,
    BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT
}

enum class PlayerAction {
    PLAY_PAUSE,
    SEEK_FORWARD,
    SEEK_BACKWARD,
    NEXT_VIDEO,
    PREVIOUS_VIDEO,
    VOLUME_UP,
    VOLUME_DOWN,
    BRIGHTNESS_UP,
    BRIGHTNESS_DOWN,
    TOGGLE_FULLSCREEN,
    TOGGLE_PIP,
    SHOW_INFO,
    SHOW_MENU,
    TOGGLE_SUBTITLE,
    NEXT_SUBTITLE,
    NEXT_AUDIO_TRACK,
    SPEED_UP,
    SPEED_DOWN,
    ZOOM_IN,
    ZOOM_OUT,
    SCREENSHOT,
    ROTATE_SCREEN,
    TOGGLE_LOCK
}

enum class GestureSensitivity {
    LOW, NORMAL, HIGH, CUSTOM
}

enum class GesturePreset {
    DEFAULT, MINIMAL, ADVANCED, CUSTOM
}

