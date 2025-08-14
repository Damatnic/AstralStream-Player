package com.astralstream.player.performance

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Performance manager with multi-core decoding, hardware acceleration detection,
 * adaptive streaming, and caching/buffering optimization
 */
@UnstableApi
@Singleton
class PerformanceManager @Inject constructor(
    private val context: Context
) {
    
    private val _performanceState = MutableStateFlow(PerformanceState())
    val performanceState: StateFlow<PerformanceState> = _performanceState.asStateFlow()
    
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Thread pools for multi-core processing
    private var decodingExecutor = Executors.newFixedThreadPool(getOptimalThreadCount())
    private var networkExecutor = Executors.newFixedThreadPool(2)
    
    companion object {
        private const val TAG = "PerformanceManager"
        
        // Cache settings
        private const val MAX_CACHE_SIZE = 500L * 1024 * 1024 // 500MB
        private const val MIN_CACHE_SIZE = 50L * 1024 * 1024  // 50MB
        
        // Buffer settings (in milliseconds)
        private const val MIN_BUFFER_MS = 2500
        private const val MAX_BUFFER_MS = 30000
        private const val BUFFER_FOR_PLAYBACK_MS = 1500
        private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 2500
        
        // Performance monitoring intervals
        private const val PERFORMANCE_MONITOR_INTERVAL_MS = 1000L
        private const val THERMAL_MONITOR_INTERVAL_MS = 5000L
    }
    
    init {
        initializePerformanceMonitoring()
        detectHardwareCapabilities()
        optimizeCacheSettings()
    }
    
    private fun initializePerformanceMonitoring() {
        coroutineScope.launch {
            while (true) {
                updatePerformanceMetrics()
                delay(PERFORMANCE_MONITOR_INTERVAL_MS)
            }
        }
        
        coroutineScope.launch {
            while (true) {
                monitorThermalState()
                delay(THERMAL_MONITOR_INTERVAL_MS)
            }
        }
    }
    
    private fun detectHardwareCapabilities() {
        val hardwareInfo = HardwareInfo(
            cpuCores = Runtime.getRuntime().availableProcessors(),
            totalMemoryMb = getTotalMemoryMb(),
            availableMemoryMb = getAvailableMemoryMb(),
            hardwareAcceleration = detectHardwareAcceleration(),
            gpuInfo = detectGpuInfo(),
            thermalSupport = isThermalSupported(),
            lowRamDevice = isLowRamDevice()
        )
        
        _performanceState.value = _performanceState.value.copy(
            hardwareInfo = hardwareInfo
        )
        
        Log.d(TAG, "Hardware capabilities detected: $hardwareInfo")
    }
    
    private fun getTotalMemoryMb(): Long {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            memInfo.totalMem / (1024 * 1024)
        } catch (e: Exception) {
            0L
        }
    }
    
    private fun getAvailableMemoryMb(): Long {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            memInfo.availMem / (1024 * 1024)
        } catch (e: Exception) {
            0L
        }
    }
    
    private fun detectHardwareAcceleration(): HardwareAcceleration {
        return try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    // Modern hardware acceleration detection
                    HardwareAcceleration.FULL_HARDWARE
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    HardwareAcceleration.PARTIAL_HARDWARE
                }
                else -> HardwareAcceleration.SOFTWARE_ONLY
            }
        } catch (e: Exception) {
            HardwareAcceleration.UNKNOWN
        }
    }
    
    private fun detectGpuInfo(): GpuInfo {
        return try {
            // Basic GPU detection - in real implementation, this would use more sophisticated methods
            GpuInfo(
                vendor = "Unknown",
                model = "Unknown",
                driverVersion = "Unknown",
                supportsHardwareDecoding = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
            )
        } catch (e: Exception) {
            GpuInfo()
        }
    }
    
    private fun isThermalSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }
    
    private fun isLowRamDevice(): Boolean {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                activityManager.isLowRamDevice
            } else {
                getTotalMemoryMb() < 1024 // Less than 1GB
            }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun getOptimalThreadCount(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return when {
            cores >= 8 -> 4 // High-end devices
            cores >= 4 -> 2 // Mid-range devices
            else -> 1       // Low-end devices
        }
    }
    
    // Multi-core decoding
    fun enableMultiCoreDecoding(enabled: Boolean) {
        _performanceState.value = _performanceState.value.copy(
            isMultiCoreDecodingEnabled = enabled
        )
        
        if (enabled) {
            // Reinitialize thread pool with optimal thread count
            decodingExecutor.shutdown()
            decodingExecutor = Executors.newFixedThreadPool(getOptimalThreadCount())
        } else {
            // Use single-threaded decoding
            decodingExecutor.shutdown()
            decodingExecutor = Executors.newSingleThreadExecutor()
        }
        
        Log.d(TAG, "Multi-core decoding ${if (enabled) "enabled" else "disabled"}")
    }
    
    fun setDecodingThreads(threadCount: Int) {
        val clampedThreads = threadCount.coerceIn(1, _performanceState.value.hardwareInfo.cpuCores)
        
        decodingExecutor.shutdown()
        decodingExecutor = Executors.newFixedThreadPool(clampedThreads)
        
        _performanceState.value = _performanceState.value.copy(
            decodingThreadCount = clampedThreads
        )
        
        Log.d(TAG, "Decoding threads set to: $clampedThreads")
    }
    
    // Hardware acceleration
    fun setHardwareAccelerationMode(mode: HardwareAcceleration) {
        _performanceState.value = _performanceState.value.copy(
            currentHardwareMode = mode
        )
        
        Log.d(TAG, "Hardware acceleration mode set to: $mode")
    }
    
    fun isHardwareDecodingSupported(mimeType: String): Boolean {
        // Check if hardware decoding is supported for specific codec
        // This would integrate with MediaCodecList in real implementation
        return when (mimeType.lowercase()) {
            "video/avc", "video/h264" -> true
            "video/hevc", "video/h265" -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
            "video/vp9" -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            "video/av01" -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            else -> false
        }
    }
    
    // Adaptive streaming
    fun enableAdaptiveStreaming(enabled: Boolean) {
        _performanceState.value = _performanceState.value.copy(
            isAdaptiveStreamingEnabled = enabled
        )
        
        Log.d(TAG, "Adaptive streaming ${if (enabled) "enabled" else "disabled"}")
    }
    
    fun setAdaptiveStreamingParameters(
        maxBitrate: Int,
        maxResolution: Pair<Int, Int>,
        bufferOptimization: BufferOptimization
    ) {
        val params = AdaptiveStreamingParams(
            maxBitrate = maxBitrate,
            maxWidth = maxResolution.first,
            maxHeight = maxResolution.second,
            bufferOptimization = bufferOptimization
        )
        
        _performanceState.value = _performanceState.value.copy(
            adaptiveStreamingParams = params
        )
        
        Log.d(TAG, "Adaptive streaming parameters updated: $params")
    }
    
    fun createOptimizedLoadControl(): LoadControl {
        val state = _performanceState.value
        val bufferOptimization = state.adaptiveStreamingParams.bufferOptimization
        
        // Adjust buffer sizes based on hardware capabilities and thermal state
        val minBufferMs = when {
            state.thermalState == ThermalState.CRITICAL -> MIN_BUFFER_MS / 2
            state.hardwareInfo.lowRamDevice -> MIN_BUFFER_MS
            else -> MIN_BUFFER_MS * 2
        }
        
        val maxBufferMs = when {
            state.thermalState == ThermalState.CRITICAL -> MAX_BUFFER_MS / 4
            state.hardwareInfo.lowRamDevice -> MAX_BUFFER_MS / 2
            bufferOptimization == BufferOptimization.MEMORY_OPTIMIZED -> MAX_BUFFER_MS / 2
            bufferOptimization == BufferOptimization.PERFORMANCE_OPTIMIZED -> MAX_BUFFER_MS * 2
            else -> MAX_BUFFER_MS
        }
        
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBufferMs,
                maxBufferMs,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .setTargetBufferBytes(
                when (bufferOptimization) {
                    BufferOptimization.MEMORY_OPTIMIZED -> 5 * 1024 * 1024  // 5MB
                    BufferOptimization.PERFORMANCE_OPTIMIZED -> 50 * 1024 * 1024 // 50MB
                    BufferOptimization.BALANCED -> 20 * 1024 * 1024 // 20MB
                }
            )
            .setPrioritizeTimeOverSizeThresholds(
                bufferOptimization == BufferOptimization.PERFORMANCE_OPTIMIZED
            )
            .setBackBuffer(
                when (bufferOptimization) {
                    BufferOptimization.MEMORY_OPTIMIZED -> 10000  // 10 seconds
                    BufferOptimization.PERFORMANCE_OPTIMIZED -> 60000 // 60 seconds
                    BufferOptimization.BALANCED -> 30000 // 30 seconds
                },
                true
            )
            .build()
    }
    
    // Caching optimization
    private fun optimizeCacheSettings() {
        val availableStorage = getAvailableStorageSpace()
        val optimalCacheSize = calculateOptimalCacheSize(availableStorage)
        
        _performanceState.value = _performanceState.value.copy(
            cacheSettings = CacheSettings(
                maxSize = optimalCacheSize,
                currentSize = getCurrentCacheSize(),
                availableStorage = availableStorage,
                isEnabled = true
            )
        )
        
        Log.d(TAG, "Cache settings optimized: ${optimalCacheSize / (1024 * 1024)}MB")
    }
    
    private fun getAvailableStorageSpace(): Long {
        return try {
            val cacheDir = context.cacheDir
            cacheDir.freeSpace
        } catch (e: Exception) {
            0L
        }
    }
    
    private fun getCurrentCacheSize(): Long {
        return try {
            val cacheDir = File(context.cacheDir, "media_cache")
            if (cacheDir.exists()) {
                cacheDir.walkTopDown().sumOf { it.length() }
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }
    
    private fun calculateOptimalCacheSize(availableStorage: Long): Long {
        val hardwareInfo = _performanceState.value.hardwareInfo
        
        return when {
            hardwareInfo.lowRamDevice -> MIN_CACHE_SIZE
            availableStorage < 1024 * 1024 * 1024 -> MIN_CACHE_SIZE // Less than 1GB available
            availableStorage < 5L * 1024 * 1024 * 1024 -> MAX_CACHE_SIZE / 2 // Less than 5GB
            else -> MAX_CACHE_SIZE
        }.coerceAtMost(availableStorage / 10) // Never use more than 10% of available storage
    }
    
    fun setCacheSize(sizeBytes: Long) {
        val clampedSize = sizeBytes.coerceIn(MIN_CACHE_SIZE, MAX_CACHE_SIZE)
        
        _performanceState.value = _performanceState.value.copy(
            cacheSettings = _performanceState.value.cacheSettings.copy(
                maxSize = clampedSize
            )
        )
        
        Log.d(TAG, "Cache size set to: ${clampedSize / (1024 * 1024)}MB")
    }
    
    fun clearCache() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = File(context.cacheDir, "media_cache")
                if (cacheDir.exists()) {
                    cacheDir.deleteRecursively()
                    cacheDir.mkdirs()
                }
                
                _performanceState.value = _performanceState.value.copy(
                    cacheSettings = _performanceState.value.cacheSettings.copy(
                        currentSize = 0L
                    )
                )
                
                Log.d(TAG, "Cache cleared")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear cache", e)
            }
        }
    }
    
    // Performance monitoring
    private suspend fun updatePerformanceMetrics() {
        try {
            val runtime = Runtime.getRuntime()
            val usedMemoryMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            val maxMemoryMb = runtime.maxMemory() / (1024 * 1024)
            val cpuUsage = getCurrentCpuUsage()
            
            val metrics = PerformanceMetrics(
                usedMemoryMb = usedMemoryMb,
                maxMemoryMb = maxMemoryMb,
                memoryUsagePercent = (usedMemoryMb.toFloat() / maxMemoryMb * 100),
                cpuUsagePercent = cpuUsage,
                frameDrops = 0, // Would be populated from video renderer
                bufferHealth = calculateBufferHealth(),
                networkThroughputMbps = 0.0 // Would be measured from actual network usage
            )
            
            _performanceState.value = _performanceState.value.copy(
                performanceMetrics = metrics
            )
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update performance metrics", e)
        }
    }
    
    private fun getCurrentCpuUsage(): Float {
        // Simplified CPU usage - in real implementation, this would read /proc/stat
        return 0f
    }
    
    private fun calculateBufferHealth(): Float {
        // Calculate buffer health based on current buffer state
        // This would integrate with ExoPlayer's buffer metrics
        return 100f
    }
    
    private suspend fun monitorThermalState() {
        if (!_performanceState.value.hardwareInfo.thermalSupport) return
        
        try {
            val thermalState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                when (powerManager.currentThermalStatus) {
                    android.os.PowerManager.THERMAL_STATUS_NONE -> ThermalState.NORMAL
                    android.os.PowerManager.THERMAL_STATUS_LIGHT -> ThermalState.LIGHT
                    android.os.PowerManager.THERMAL_STATUS_MODERATE -> ThermalState.MODERATE
                    android.os.PowerManager.THERMAL_STATUS_SEVERE -> ThermalState.SEVERE
                    android.os.PowerManager.THERMAL_STATUS_CRITICAL -> ThermalState.CRITICAL
                    android.os.PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalState.EMERGENCY
                    android.os.PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalState.SHUTDOWN
                    else -> ThermalState.UNKNOWN
                }
            } else {
                ThermalState.UNKNOWN
            }
            
            _performanceState.value = _performanceState.value.copy(
                thermalState = thermalState
            )
            
            // Apply thermal throttling if necessary
            if (thermalState >= ThermalState.SEVERE) {
                applyThermalThrottling()
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to monitor thermal state", e)
        }
    }
    
    private fun applyThermalThrottling() {
        Log.d(TAG, "Applying thermal throttling")
        
        // Reduce buffer sizes
        _performanceState.value = _performanceState.value.copy(
            adaptiveStreamingParams = _performanceState.value.adaptiveStreamingParams.copy(
                bufferOptimization = BufferOptimization.MEMORY_OPTIMIZED
            )
        )
        
        // Reduce decoding threads
        if (_performanceState.value.decodingThreadCount > 1) {
            setDecodingThreads(1)
        }
        
        // Switch to software decoding if thermal state is critical
        if (_performanceState.value.thermalState == ThermalState.CRITICAL) {
            setHardwareAccelerationMode(HardwareAcceleration.SOFTWARE_ONLY)
        }
    }
    
    // Optimization presets
    fun applyPerformancePreset(preset: PerformancePreset) {
        when (preset) {
            PerformancePreset.BATTERY_SAVER -> {
                enableMultiCoreDecoding(false)
                setHardwareAccelerationMode(HardwareAcceleration.SOFTWARE_ONLY)
                setAdaptiveStreamingParameters(
                    maxBitrate = 2_000_000, // 2 Mbps
                    maxResolution = Pair(1280, 720), // 720p
                    bufferOptimization = BufferOptimization.MEMORY_OPTIMIZED
                )
                setCacheSize(MIN_CACHE_SIZE)
            }
            PerformancePreset.BALANCED -> {
                enableMultiCoreDecoding(true)
                setHardwareAccelerationMode(HardwareAcceleration.PARTIAL_HARDWARE)
                setAdaptiveStreamingParameters(
                    maxBitrate = 8_000_000, // 8 Mbps
                    maxResolution = Pair(1920, 1080), // 1080p
                    bufferOptimization = BufferOptimization.BALANCED
                )
                setCacheSize(MAX_CACHE_SIZE / 2)
            }
            PerformancePreset.PERFORMANCE -> {
                enableMultiCoreDecoding(true)
                setHardwareAccelerationMode(HardwareAcceleration.FULL_HARDWARE)
                setAdaptiveStreamingParameters(
                    maxBitrate = 25_000_000, // 25 Mbps
                    maxResolution = Pair(3840, 2160), // 4K
                    bufferOptimization = BufferOptimization.PERFORMANCE_OPTIMIZED
                )
                setCacheSize(MAX_CACHE_SIZE)
            }
            PerformancePreset.AUTO -> {
                applyAutoOptimization()
            }
        }
        
        _performanceState.value = _performanceState.value.copy(
            currentPreset = preset
        )
        
        Log.d(TAG, "Applied performance preset: $preset")
    }
    
    private fun applyAutoOptimization() {
        val hardwareInfo = _performanceState.value.hardwareInfo
        val thermalState = _performanceState.value.thermalState
        val metrics = _performanceState.value.performanceMetrics
        
        when {
            // High-end device with good thermal state
            hardwareInfo.cpuCores >= 8 && 
            hardwareInfo.totalMemoryMb >= 6144 && 
            thermalState <= ThermalState.LIGHT -> {
                applyPerformancePreset(PerformancePreset.PERFORMANCE)
            }
            // Low-end device or thermal throttling
            hardwareInfo.lowRamDevice || 
            thermalState >= ThermalState.SEVERE ||
            metrics.memoryUsagePercent > 85 -> {
                applyPerformancePreset(PerformancePreset.BATTERY_SAVER)
            }
            // Everything else
            else -> {
                applyPerformancePreset(PerformancePreset.BALANCED)
            }
        }
    }
    
    fun getPerformanceRecommendations(): List<PerformanceRecommendation> {
        val recommendations = mutableListOf<PerformanceRecommendation>()
        val state = _performanceState.value
        
        // Memory recommendations
        if (state.performanceMetrics.memoryUsagePercent > 85) {
            recommendations.add(
                PerformanceRecommendation(
                    type = RecommendationType.MEMORY,
                    severity = RecommendationSeverity.HIGH,
                    title = "High Memory Usage",
                    description = "Consider reducing buffer size or clearing cache",
                    action = "Reduce buffer settings"
                )
            )
        }
        
        // Thermal recommendations
        if (state.thermalState >= ThermalState.SEVERE) {
            recommendations.add(
                PerformanceRecommendation(
                    type = RecommendationType.THERMAL,
                    severity = RecommendationSeverity.CRITICAL,
                    title = "Thermal Throttling",
                    description = "Device is overheating, performance will be reduced",
                    action = "Switch to battery saver mode"
                )
            )
        }
        
        // Storage recommendations
        if (state.cacheSettings.availableStorage < 1024 * 1024 * 1024) { // Less than 1GB
            recommendations.add(
                PerformanceRecommendation(
                    type = RecommendationType.STORAGE,
                    severity = RecommendationSeverity.MEDIUM,
                    title = "Low Storage Space",
                    description = "Consider clearing cache or reducing cache size",
                    action = "Clear cache"
                )
            )
        }
        
        return recommendations
    }
    
    fun release() {
        decodingExecutor.shutdown()
        networkExecutor.shutdown()
        coroutineScope.cancel()
        
        _performanceState.value = PerformanceState()
    }
}

// Data classes and enums
data class PerformanceState(
    val hardwareInfo: HardwareInfo = HardwareInfo(),
    val isMultiCoreDecodingEnabled: Boolean = true,
    val decodingThreadCount: Int = 2,
    val currentHardwareMode: HardwareAcceleration = HardwareAcceleration.PARTIAL_HARDWARE,
    val isAdaptiveStreamingEnabled: Boolean = true,
    val adaptiveStreamingParams: AdaptiveStreamingParams = AdaptiveStreamingParams(),
    val cacheSettings: CacheSettings = CacheSettings(),
    val performanceMetrics: PerformanceMetrics = PerformanceMetrics(),
    val thermalState: ThermalState = ThermalState.NORMAL,
    val currentPreset: PerformancePreset = PerformancePreset.AUTO
)

data class HardwareInfo(
    val cpuCores: Int = 0,
    val totalMemoryMb: Long = 0L,
    val availableMemoryMb: Long = 0L,
    val hardwareAcceleration: HardwareAcceleration = HardwareAcceleration.UNKNOWN,
    val gpuInfo: GpuInfo = GpuInfo(),
    val thermalSupport: Boolean = false,
    val lowRamDevice: Boolean = false
)

data class GpuInfo(
    val vendor: String = "Unknown",
    val model: String = "Unknown",
    val driverVersion: String = "Unknown",
    val supportsHardwareDecoding: Boolean = false
)

enum class HardwareAcceleration {
    SOFTWARE_ONLY, PARTIAL_HARDWARE, FULL_HARDWARE, UNKNOWN
}

data class AdaptiveStreamingParams(
    val maxBitrate: Int = 10_000_000, // 10 Mbps default
    val maxWidth: Int = 1920,
    val maxHeight: Int = 1080,
    val bufferOptimization: BufferOptimization = BufferOptimization.BALANCED
)

enum class BufferOptimization {
    MEMORY_OPTIMIZED, BALANCED, PERFORMANCE_OPTIMIZED
}

data class CacheSettings(
    val maxSize: Long = 100L * 1024 * 1024, // 100MB default
    val currentSize: Long = 0L,
    val availableStorage: Long = 0L,
    val isEnabled: Boolean = true
)

data class PerformanceMetrics(
    val usedMemoryMb: Long = 0L,
    val maxMemoryMb: Long = 0L,
    val memoryUsagePercent: Float = 0f,
    val cpuUsagePercent: Float = 0f,
    val frameDrops: Int = 0,
    val bufferHealth: Float = 100f,
    val networkThroughputMbps: Double = 0.0
)

enum class ThermalState {
    NORMAL, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN, UNKNOWN
}

enum class PerformancePreset {
    BATTERY_SAVER, BALANCED, PERFORMANCE, AUTO
}

data class PerformanceRecommendation(
    val type: RecommendationType,
    val severity: RecommendationSeverity,
    val title: String,
    val description: String,
    val action: String
)

enum class RecommendationType {
    MEMORY, THERMAL, STORAGE, NETWORK, DECODING
}

enum class RecommendationSeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}