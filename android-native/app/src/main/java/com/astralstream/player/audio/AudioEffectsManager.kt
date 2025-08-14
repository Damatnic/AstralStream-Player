package com.astralstream.player.audio

import android.content.Context
import android.media.audiofx.*
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comprehensive audio effects manager with 5-band equalizer,
 * bass boost, virtualizer, and volume normalization
 */
@Singleton
class AudioEffectsManager @Inject constructor(
    private val context: Context
) {
    
    private val _audioState = MutableStateFlow(AudioEffectsState())
    val audioState: StateFlow<AudioEffectsState> = _audioState.asStateFlow()
    
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var presetReverb: PresetReverb? = null
    private var environmentalReverb: EnvironmentalReverb? = null
    
    private var currentAudioSessionId: Int = 0
    
    companion object {
        private const val TAG = "AudioEffectsManager"
        
        // Equalizer presets
        val EQUALIZER_PRESETS = mapOf(
            "Normal" to floatArrayOf(0f, 0f, 0f, 0f, 0f),
            "Rock" to floatArrayOf(0.8f, 0.4f, -0.6f, -0.8f, -0.3f),
            "Pop" to floatArrayOf(-0.2f, 0.5f, 0.7f, 0.8f, -0.2f),
            "Jazz" to floatArrayOf(0.4f, 0.2f, -0.2f, 0.2f, 0.5f),
            "Classical" to floatArrayOf(0.5f, 0.3f, -0.2f, 0.4f, 0.9f),
            "Electronic" to floatArrayOf(0.6f, 0.8f, 0f, -0.5f, -0.5f),
            "Hip-Hop" to floatArrayOf(0.7f, 0.9f, 0.1f, 0.3f, -0.3f),
            "Vocal" to floatArrayOf(-0.2f, 0.4f, 0.9f, 0.7f, 0.2f),
            "Bass Boost" to floatArrayOf(1.2f, 0.8f, 0.4f, 0f, 0f),
            "Treble Boost" to floatArrayOf(0f, 0f, 0.4f, 0.8f, 1.2f)
        )
    }
    
    fun initializeAudioEffects(audioSessionId: Int) {
        if (currentAudioSessionId == audioSessionId && equalizer != null) {
            return // Already initialized for this session
        }
        
        try {
            releaseAudioEffects()
            currentAudioSessionId = audioSessionId
            
            // Initialize Equalizer
            try {
                equalizer = Equalizer(0, audioSessionId).apply {
                    enabled = false
                }
                Log.d(TAG, "Equalizer initialized with ${equalizer!!.numberOfBands} bands")
            } catch (e: Exception) {
                Log.w(TAG, "Equalizer not available", e)
            }
            
            // Initialize Bass Boost
            try {
                bassBoost = BassBoost(0, audioSessionId).apply {
                    enabled = false
                }
                Log.d(TAG, "Bass Boost initialized")
            } catch (e: Exception) {
                Log.w(TAG, "Bass Boost not available", e)
            }
            
            // Initialize Virtualizer
            try {
                virtualizer = Virtualizer(0, audioSessionId).apply {
                    enabled = false
                }
                Log.d(TAG, "Virtualizer initialized")
            } catch (e: Exception) {
                Log.w(TAG, "Virtualizer not available", e)
            }
            
            // Initialize Loudness Enhancer
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                try {
                    loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                        enabled = false
                    }
                    Log.d(TAG, "Loudness Enhancer initialized")
                } catch (e: Exception) {
                    Log.w(TAG, "Loudness Enhancer not available", e)
                }
            }
            
            // Initialize Preset Reverb
            try {
                presetReverb = PresetReverb(0, audioSessionId).apply {
                    enabled = false
                }
                Log.d(TAG, "Preset Reverb initialized")
            } catch (e: Exception) {
                Log.w(TAG, "Preset Reverb not available", e)
            }
            
            updateAudioState()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize audio effects", e)
            _audioState.value = _audioState.value.copy(
                error = "Failed to initialize audio effects: ${e.message}"
            )
        }
    }
    
    private fun updateAudioState() {
        val equalizerBands = mutableListOf<EqualizerBand>()
        
        equalizer?.let { eq ->
            for (i in 0 until eq.numberOfBands.toInt()) {
                val band = EqualizerBand(
                    frequency = eq.getCenterFreq(i.toShort()),
                    gain = eq.getBandLevel(i.toShort()),
                    minGain = eq.bandLevelRange[0],
                    maxGain = eq.bandLevelRange[1]
                )
                equalizerBands.add(band)
            }
        }
        
        _audioState.value = _audioState.value.copy(
            isInitialized = true,
            equalizerBands = equalizerBands,
            isEqualizerAvailable = equalizer != null,
            isBassBoostAvailable = bassBoost != null,
            isVirtualizerAvailable = virtualizer != null,
            isLoudnessEnhancerAvailable = loudnessEnhancer != null,
            isPresetReverbAvailable = presetReverb != null,
            error = null
        )
    }
    
    fun setEqualizerEnabled(enabled: Boolean) {
        try {
            equalizer?.enabled = enabled
            _audioState.value = _audioState.value.copy(isEqualizerEnabled = enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set equalizer enabled state", e)
        }
    }
    
    fun setBandLevel(bandIndex: Int, level: Short) {
        try {
            equalizer?.setBandLevel(bandIndex.toShort(), level)
            
            // Update the state
            val updatedBands = _audioState.value.equalizerBands.toMutableList()
            if (bandIndex < updatedBands.size) {
                updatedBands[bandIndex] = updatedBands[bandIndex].copy(gain = level)
                _audioState.value = _audioState.value.copy(equalizerBands = updatedBands)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set band level", e)
        }
    }
    
    fun applyEqualizerPreset(presetName: String) {
        val preset = EQUALIZER_PRESETS[presetName] ?: return
        
        try {
            equalizer?.let { eq ->
                val bandLevelRange = eq.bandLevelRange
                val maxLevel = bandLevelRange[1]
                
                for (i in 0 until minOf(preset.size, eq.numberOfBands.toInt())) {
                    val level = (preset[i] * maxLevel).toInt().toShort()
                    eq.setBandLevel(i.toShort(), level)
                }
                
                _audioState.value = _audioState.value.copy(
                    currentPreset = presetName
                )
                
                updateAudioState()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply equalizer preset: $presetName", e)
        }
    }
    
    fun setBassBoostEnabled(enabled: Boolean) {
        try {
            bassBoost?.enabled = enabled
            _audioState.value = _audioState.value.copy(isBassBoostEnabled = enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set bass boost enabled state", e)
        }
    }
    
    fun setBassBoostStrength(strength: Short) {
        try {
            bassBoost?.setStrength(strength)
            _audioState.value = _audioState.value.copy(bassBoostStrength = strength)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set bass boost strength", e)
        }
    }
    
    fun setVirtualizerEnabled(enabled: Boolean) {
        try {
            virtualizer?.enabled = enabled
            _audioState.value = _audioState.value.copy(isVirtualizerEnabled = enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set virtualizer enabled state", e)
        }
    }
    
    fun setVirtualizerStrength(strength: Short) {
        try {
            virtualizer?.setStrength(strength)
            _audioState.value = _audioState.value.copy(virtualizerStrength = strength)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set virtualizer strength", e)
        }
    }
    
    fun setLoudnessEnhancerEnabled(enabled: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            try {
                loudnessEnhancer?.enabled = enabled
                _audioState.value = _audioState.value.copy(isLoudnessEnhancerEnabled = enabled)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set loudness enhancer enabled state", e)
            }
        }
    }
    
    fun setLoudnessEnhancerGain(gainDb: Float) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            try {
                loudnessEnhancer?.setTargetGain((gainDb * 100).toInt())
                _audioState.value = _audioState.value.copy(loudnessEnhancerGain = gainDb)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set loudness enhancer gain", e)
            }
        }
    }
    
    fun setPresetReverbEnabled(enabled: Boolean) {
        try {
            presetReverb?.enabled = enabled
            _audioState.value = _audioState.value.copy(isPresetReverbEnabled = enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set preset reverb enabled state", e)
        }
    }
    
    fun setPresetReverbType(preset: Short) {
        try {
            presetReverb?.preset = preset
            
            val presetName = when (preset) {
                PresetReverb.PRESET_NONE -> "None"
                PresetReverb.PRESET_SMALLROOM -> "Small Room"
                PresetReverb.PRESET_MEDIUMROOM -> "Medium Room"
                PresetReverb.PRESET_LARGEROOM -> "Large Room"
                PresetReverb.PRESET_MEDIUMHALL -> "Medium Hall"
                PresetReverb.PRESET_LARGEHALL -> "Large Hall"
                PresetReverb.PRESET_PLATE -> "Plate"
                else -> "Unknown"
            }
            
            _audioState.value = _audioState.value.copy(
                presetReverbType = preset,
                presetReverbName = presetName
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set preset reverb type", e)
        }
    }
    
    fun resetAllEffects() {
        try {
            setEqualizerEnabled(false)
            setBassBoostEnabled(false)
            setVirtualizerEnabled(false)
            setLoudnessEnhancerEnabled(false)
            setPresetReverbEnabled(false)
            
            // Reset equalizer to flat
            applyEqualizerPreset("Normal")
            
            _audioState.value = _audioState.value.copy(
                currentPreset = "Normal",
                bassBoostStrength = 0,
                virtualizerStrength = 0,
                loudnessEnhancerGain = 0f,
                presetReverbType = PresetReverb.PRESET_NONE
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset audio effects", e)
        }
    }
    
    fun getEqualizerPresets(): List<String> {
        return EQUALIZER_PRESETS.keys.toList()
    }
    
    fun getReverbPresets(): List<ReverbPreset> {
        return listOf(
            ReverbPreset(PresetReverb.PRESET_NONE, "None"),
            ReverbPreset(PresetReverb.PRESET_SMALLROOM, "Small Room"),
            ReverbPreset(PresetReverb.PRESET_MEDIUMROOM, "Medium Room"),
            ReverbPreset(PresetReverb.PRESET_LARGEROOM, "Large Room"),
            ReverbPreset(PresetReverb.PRESET_MEDIUMHALL, "Medium Hall"),
            ReverbPreset(PresetReverb.PRESET_LARGEHALL, "Large Hall"),
            ReverbPreset(PresetReverb.PRESET_PLATE, "Plate")
        )
    }
    
    fun savePreset(name: String): Boolean {
        return try {
            val levels = _audioState.value.equalizerBands.map { it.gain.toFloat() / it.maxGain }.toFloatArray()
            // In a real implementation, this would save to SharedPreferences or a database
            Log.d(TAG, "Saved preset: $name with levels: ${levels.contentToString()}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save preset: $name", e)
            false
        }
    }
    
    fun releaseAudioEffects() {
        try {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
            loudnessEnhancer?.release()
            presetReverb?.release()
            environmentalReverb?.release()
            
            equalizer = null
            bassBoost = null
            virtualizer = null
            loudnessEnhancer = null
            presetReverb = null
            environmentalReverb = null
            
            _audioState.value = AudioEffectsState()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio effects", e)
        }
    }
}

data class AudioEffectsState(
    val isInitialized: Boolean = false,
    
    // Equalizer
    val isEqualizerAvailable: Boolean = false,
    val isEqualizerEnabled: Boolean = false,
    val equalizerBands: List<EqualizerBand> = emptyList(),
    val currentPreset: String = "Normal",
    
    // Bass Boost
    val isBassBoostAvailable: Boolean = false,
    val isBassBoostEnabled: Boolean = false,
    val bassBoostStrength: Short = 0,
    
    // Virtualizer
    val isVirtualizerAvailable: Boolean = false,
    val isVirtualizerEnabled: Boolean = false,
    val virtualizerStrength: Short = 0,
    
    // Loudness Enhancer
    val isLoudnessEnhancerAvailable: Boolean = false,
    val isLoudnessEnhancerEnabled: Boolean = false,
    val loudnessEnhancerGain: Float = 0f,
    
    // Preset Reverb
    val isPresetReverbAvailable: Boolean = false,
    val isPresetReverbEnabled: Boolean = false,
    val presetReverbType: Short = PresetReverb.PRESET_NONE,
    val presetReverbName: String = "None",
    
    // Error state
    val error: String? = null
)

data class EqualizerBand(
    val frequency: Int,
    val gain: Short,
    val minGain: Short,
    val maxGain: Short
)

data class ReverbPreset(
    val value: Short,
    val name: String
)