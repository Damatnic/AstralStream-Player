package com.astralstream.player.audio

import android.content.Context
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Advanced audio enhancement processor with volume normalization,
 * dynamic range compression, and advanced audio processing
 */
@Singleton
class AudioEnhancementProcessor @Inject constructor(
    private val context: Context
) {
    
    private val _audioEnhancementState = MutableStateFlow(AudioEnhancementState())
    val audioEnhancementState: StateFlow<AudioEnhancementState> = _audioEnhancementState.asStateFlow()
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var currentAudioSessionId: Int = 0
    
    // Audio analysis buffers
    private var volumeHistory = mutableListOf<Float>()
    private var peakLevels = mutableListOf<Float>()
    private val maxHistorySize = 100
    
    companion object {
        private const val TAG = "AudioEnhancementProcessor"
        
        // Volume normalization constants
        private const val TARGET_LUFS = -16f  // Target loudness (LUFS)
        private const val MAX_GAIN_DB = 20f   // Maximum gain adjustment
        private const val MIN_GAIN_DB = -20f  // Minimum gain adjustment
        
        // Dynamic range processing
        private const val COMPRESSOR_THRESHOLD_DB = -12f
        private const val COMPRESSOR_RATIO = 4f
        private const val LIMITER_THRESHOLD_DB = -1f
        
        // Audio enhancement presets
        val ENHANCEMENT_PRESETS = mapOf(
            "Off" to AudioEnhancementPreset(
                volumeNormalization = false,
                dynamicRangeCompression = false,
                nightMode = false,
                dialogEnhancement = false,
                bassEnhancement = 0f,
                trebleEnhancement = 0f
            ),
            "Music" to AudioEnhancementPreset(
                volumeNormalization = true,
                dynamicRangeCompression = false,
                nightMode = false,
                dialogEnhancement = false,
                bassEnhancement = 0.3f,
                trebleEnhancement = 0.2f
            ),
            "Movie" to AudioEnhancementPreset(
                volumeNormalization = true,
                dynamicRangeCompression = true,
                nightMode = false,
                dialogEnhancement = true,
                bassEnhancement = 0.2f,
                trebleEnhancement = 0.1f
            ),
            "Night Mode" to AudioEnhancementPreset(
                volumeNormalization = true,
                dynamicRangeCompression = true,
                nightMode = true,
                dialogEnhancement = true,
                bassEnhancement = -0.2f,
                trebleEnhancement = 0.4f
            ),
            "Voice" to AudioEnhancementPreset(
                volumeNormalization = true,
                dynamicRangeCompression = true,
                nightMode = false,
                dialogEnhancement = true,
                bassEnhancement = -0.4f,
                trebleEnhancement = 0.6f
            )
        )
    }
    
    fun initializeAudioEnhancement(audioSessionId: Int) {
        if (currentAudioSessionId == audioSessionId && dynamicsProcessing != null) {
            return
        }
        
        try {
            releaseAudioEnhancement()
            currentAudioSessionId = audioSessionId
            
            // Initialize DynamicsProcessing (Android 9+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val config = createDynamicsProcessingConfig()
                    dynamicsProcessing = DynamicsProcessing(0, audioSessionId, config).apply {
                        enabled = false
                    }
                    Log.d(TAG, "DynamicsProcessing initialized")
                } catch (e: Exception) {
                    Log.w(TAG, "DynamicsProcessing not available", e)
                }
            }
            
            _audioEnhancementState.value = _audioEnhancementState.value.copy(
                isInitialized = true,
                isDynamicsProcessingAvailable = dynamicsProcessing != null,
                error = null
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize audio enhancement", e)
            _audioEnhancementState.value = _audioEnhancementState.value.copy(
                error = "Failed to initialize audio enhancement: ${e.message}"
            )
        }
    }
    
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
    private fun createDynamicsProcessingConfig(): DynamicsProcessing.Config {
        return DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2, // stereo
            true, // preEqInUse
            1, // preEqBandCount
            true, // mbcInUse
            1, // mbcBandCount
            true, // postEqInUse
            1, // postEqBandCount
            true  // limiterInUse
        ).build()
    }
    
    // Volume normalization
    fun setVolumeNormalizationEnabled(enabled: Boolean) {
        _audioEnhancementState.value = _audioEnhancementState.value.copy(
            isVolumeNormalizationEnabled = enabled
        )
        
        if (enabled) {
            startVolumeAnalysis()
        } else {
            stopVolumeAnalysis()
        }
        
        Log.d(TAG, "Volume normalization ${if (enabled) "enabled" else "disabled"}")
    }
    
    private fun startVolumeAnalysis() {
        coroutineScope.launch {
            // Simulate volume analysis - in real implementation, this would analyze audio samples
            Log.d(TAG, "Started volume analysis")
        }
    }
    
    private fun stopVolumeAnalysis() {
        volumeHistory.clear()
        peakLevels.clear()
        _audioEnhancementState.value = _audioEnhancementState.value.copy(
            currentVolumeLevel = 0f,
            averageVolumeLevel = 0f,
            recommendedGainAdjustment = 0f
        )
    }
    
    fun analyzeAudioSample(samples: FloatArray, sampleRate: Int) {
        if (!_audioEnhancementState.value.isVolumeNormalizationEnabled) return
        
        try {
            // Calculate RMS (Root Mean Square) for volume level
            val rms = calculateRMS(samples)
            val volumeDb = 20 * log10(rms + Float.MIN_VALUE)
            
            // Calculate peak level
            val peak = samples.maxOrNull()?.absoluteValue ?: 0f
            val peakDb = 20 * log10(peak + Float.MIN_VALUE)
            
            // Update history
            volumeHistory.add(volumeDb)
            peakLevels.add(peakDb)
            
            if (volumeHistory.size > maxHistorySize) {
                volumeHistory.removeAt(0)
                peakLevels.removeAt(0)
            }
            
            // Calculate average volume
            val averageVolume = volumeHistory.average().toFloat()
            
            // Calculate recommended gain adjustment
            val targetVolume = -20f // Target RMS level in dB
            val gainAdjustment = (targetVolume - averageVolume).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
            
            _audioEnhancementState.value = _audioEnhancementState.value.copy(
                currentVolumeLevel = volumeDb,
                averageVolumeLevel = averageVolume,
                currentPeakLevel = peakDb,
                recommendedGainAdjustment = gainAdjustment
            )
            
        } catch (e: Exception) {
            Log.w(TAG, "Audio analysis failed", e)
        }
    }
    
    private fun calculateRMS(samples: FloatArray): Float {
        var sum = 0f
        for (sample in samples) {
            sum += sample * sample
        }
        return sqrt(sum / samples.size)
    }
    
    // Dynamic range compression
    fun setDynamicRangeCompressionEnabled(enabled: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dynamicsProcessing?.enabled = enabled
            }
            
            _audioEnhancementState.value = _audioEnhancementState.value.copy(
                isDynamicRangeCompressionEnabled = enabled
            )
            
            Log.d(TAG, "Dynamic range compression ${if (enabled) "enabled" else "disabled"}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set dynamic range compression", e)
        }
    }
    
    fun setCompressionRatio(ratio: Float) {
        val clampedRatio = ratio.coerceIn(1f, 20f)
        _audioEnhancementState.value = _audioEnhancementState.value.copy(
            compressionRatio = clampedRatio
        )
        
        // Apply compression ratio through dynamics processing
        // This would require more detailed implementation with DynamicsProcessing API
        Log.d(TAG, "Compression ratio set to: $clampedRatio")
    }
    
    fun setCompressionThreshold(thresholdDb: Float) {
        val clampedThreshold = thresholdDb.coerceIn(-60f, 0f)
        _audioEnhancementState.value = _audioEnhancementState.value.copy(
            compressionThreshold = clampedThreshold
        )
        
        Log.d(TAG, "Compression threshold set to: $clampedThreshold dB")
    }
    
    // Night mode
    fun setNightModeEnabled(enabled: Boolean) {
        _audioEnhancementState.value = _audioEnhancementState.value.copy(
            isNightModeEnabled = enabled
        )
        
        if (enabled) {
            // Apply night mode settings: compress dynamic range, enhance dialog
            setDynamicRangeCompressionEnabled(true)
            setCompressionRatio(6f)
            setCompressionThreshold(-18f)
            setDialogEnhancementEnabled(true)
        }
        
        Log.d(TAG, "Night mode ${if (enabled) "enabled" else "disabled"}")
    }
    
    // Dialog enhancement
    fun setDialogEnhancementEnabled(enabled: Boolean) {
        _audioEnhancementState.value = _audioEnhancementState.value.copy(
            isDialogEnhancementEnabled = enabled
        )
        
        // Dialog enhancement typically boosts mid frequencies (1-4 kHz)
        // This would be implemented through the equalizer or dynamics processing
        Log.d(TAG, "Dialog enhancement ${if (enabled) "enabled" else "disabled"}")
    }
    
    fun setDialogEnhancementLevel(level: Float) {
        val clampedLevel = level.coerceIn(0f, 1f)
        _audioEnhancementState.value = _audioEnhancementState.value.copy(
            dialogEnhancementLevel = clampedLevel
        )
    }
    
    // Bass and treble enhancement
    fun setBassEnhancement(level: Float) {
        val clampedLevel = level.coerceIn(-1f, 1f)
        _audioEnhancementState.value = _audioEnhancementState.value.copy(
            bassEnhancement = clampedLevel
        )
        
        Log.d(TAG, "Bass enhancement set to: $clampedLevel")
    }
    
    fun setTrebleEnhancement(level: Float) {
        val clampedLevel = level.coerceIn(-1f, 1f)
        _audioEnhancementState.value = _audioEnhancementState.value.copy(
            trebleEnhancement = clampedLevel
        )
        
        Log.d(TAG, "Treble enhancement set to: $clampedLevel")
    }
    
    // Limiter
    fun setLimiterEnabled(enabled: Boolean) {
        _audioEnhancementState.value = _audioEnhancementState.value.copy(
            isLimiterEnabled = enabled
        )
        
        Log.d(TAG, "Limiter ${if (enabled) "enabled" else "disabled"}")
    }
    
    fun setLimiterThreshold(thresholdDb: Float) {
        val clampedThreshold = thresholdDb.coerceIn(-10f, 0f)
        _audioEnhancementState.value = _audioEnhancementState.value.copy(
            limiterThreshold = clampedThreshold
        )
    }
    
    // Presets
    fun applyEnhancementPreset(presetName: String) {
        val preset = ENHANCEMENT_PRESETS[presetName] ?: return
        
        setVolumeNormalizationEnabled(preset.volumeNormalization)
        setDynamicRangeCompressionEnabled(preset.dynamicRangeCompression)
        setNightModeEnabled(preset.nightMode)
        setDialogEnhancementEnabled(preset.dialogEnhancement)
        setBassEnhancement(preset.bassEnhancement)
        setTrebleEnhancement(preset.trebleEnhancement)
        
        _audioEnhancementState.value = _audioEnhancementState.value.copy(
            currentPreset = presetName
        )
        
        Log.d(TAG, "Applied enhancement preset: $presetName")
    }
    
    fun getAvailablePresets(): List<String> {
        return ENHANCEMENT_PRESETS.keys.toList()
    }
    
    // Auto-leveling
    fun setAutoLevelingEnabled(enabled: Boolean) {
        _audioEnhancementState.value = _audioEnhancementState.value.copy(
            isAutoLevelingEnabled = enabled
        )
        
        if (enabled) {
            coroutineScope.launch {
                performAutoLeveling()
            }
        }
    }
    
    private suspend fun performAutoLeveling() {
        // Simulate auto-leveling process
        Log.d(TAG, "Performing auto-leveling analysis")
        
        // In real implementation, this would:
        // 1. Analyze the entire audio track or a sample
        // 2. Calculate optimal gain adjustment
        // 3. Apply the adjustment gradually
        
        val recommendedGain = _audioEnhancementState.value.recommendedGainAdjustment
        _audioEnhancementState.value = _audioEnhancementState.value.copy(
            autoLevelingGain = recommendedGain
        )
    }
    
    // Channel balance
    fun setChannelBalance(balance: Float) {
        val clampedBalance = balance.coerceIn(-1f, 1f) // -1 = full left, 1 = full right
        _audioEnhancementState.value = _audioEnhancementState.value.copy(
            channelBalance = clampedBalance
        )
        
        Log.d(TAG, "Channel balance set to: $clampedBalance")
    }
    
    // Audio delay compensation
    fun setAudioDelayMs(delayMs: Long) {
        val clampedDelay = delayMs.coerceIn(-500L, 500L)
        _audioEnhancementState.value = _audioEnhancementState.value.copy(
            audioDelayMs = clampedDelay
        )
    }
    
    // Reset all enhancements
    fun resetAllEnhancements() {
        applyEnhancementPreset("Off")
        setChannelBalance(0f)
        setAudioDelayMs(0L)
        setAutoLevelingEnabled(false)
        
        volumeHistory.clear()
        peakLevels.clear()
        
        _audioEnhancementState.value = _audioEnhancementState.value.copy(
            currentVolumeLevel = 0f,
            averageVolumeLevel = 0f,
            recommendedGainAdjustment = 0f,
            autoLevelingGain = 0f
        )
        
        Log.d(TAG, "Reset all audio enhancements")
    }
    
    fun releaseAudioEnhancement() {
        try {
            dynamicsProcessing?.release()
            dynamicsProcessing = null
            
            volumeHistory.clear()
            peakLevels.clear()
            
            _audioEnhancementState.value = AudioEnhancementState()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio enhancement", e)
        }
    }
}

data class AudioEnhancementState(
    val isInitialized: Boolean = false,
    val isDynamicsProcessingAvailable: Boolean = false,
    
    // Volume normalization
    val isVolumeNormalizationEnabled: Boolean = false,
    val currentVolumeLevel: Float = 0f,  // in dB
    val averageVolumeLevel: Float = 0f,  // in dB
    val currentPeakLevel: Float = 0f,    // in dB
    val recommendedGainAdjustment: Float = 0f,  // in dB
    
    // Dynamic range compression
    val isDynamicRangeCompressionEnabled: Boolean = false,
    val compressionRatio: Float = 4f,
    val compressionThreshold: Float = -12f,  // in dB
    
    // Night mode
    val isNightModeEnabled: Boolean = false,
    
    // Dialog enhancement
    val isDialogEnhancementEnabled: Boolean = false,
    val dialogEnhancementLevel: Float = 0.5f,
    
    // Bass and treble
    val bassEnhancement: Float = 0f,     // -1 to 1
    val trebleEnhancement: Float = 0f,   // -1 to 1
    
    // Limiter
    val isLimiterEnabled: Boolean = false,
    val limiterThreshold: Float = -1f,   // in dB
    
    // Auto-leveling
    val isAutoLevelingEnabled: Boolean = false,
    val autoLevelingGain: Float = 0f,    // in dB
    
    // Channel balance and delay
    val channelBalance: Float = 0f,      // -1 to 1
    val audioDelayMs: Long = 0L,         // in milliseconds
    
    // Current preset
    val currentPreset: String = "Off",
    
    // Error state
    val error: String? = null
)

data class AudioEnhancementPreset(
    val volumeNormalization: Boolean,
    val dynamicRangeCompression: Boolean,
    val nightMode: Boolean,
    val dialogEnhancement: Boolean,
    val bassEnhancement: Float,
    val trebleEnhancement: Float
)