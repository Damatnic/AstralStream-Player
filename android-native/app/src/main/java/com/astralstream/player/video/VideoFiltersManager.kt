package com.astralstream.player.video

import android.content.Context
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.Matrix
import androidx.media3.common.VideoFrameProcessor
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class VideoFiltersManager @Inject constructor(
    private val context: Context,
    private val videoEffectProcessor: VideoEffectProcessor = VideoEffectProcessor(context)
) {
    
    private val _activeFilters = MutableStateFlow<List<VideoFilter>>(emptyList())
    val activeFilters: StateFlow<List<VideoFilter>> = _activeFilters.asStateFlow()
    
    private val _filterSettings = MutableStateFlow(FilterSettings())
    val filterSettings: StateFlow<FilterSettings> = _filterSettings.asStateFlow()
    
    fun applyBrightnessFilter(brightness: Float) {
        updateFilterSetting { it.copy(brightness = brightness.coerceIn(-1f, 1f)) }
        updateActiveFilter(VideoFilter.BRIGHTNESS)
    }
    
    fun applyContrastFilter(contrast: Float) {
        updateFilterSetting { it.copy(contrast = contrast.coerceIn(0f, 2f)) }
        updateActiveFilter(VideoFilter.CONTRAST)
    }
    
    fun applySaturationFilter(saturation: Float) {
        updateFilterSetting { it.copy(saturation = saturation.coerceIn(0f, 2f)) }
        updateActiveFilter(VideoFilter.SATURATION)
    }
    
    fun applySharpnessFilter(sharpness: Float) {
        updateFilterSetting { it.copy(sharpness = sharpness.coerceIn(0f, 2f)) }
        updateActiveFilter(VideoFilter.SHARPNESS)
    }
    
    fun applyGammaFilter(gamma: Float) {
        updateFilterSetting { it.copy(gamma = gamma.coerceIn(0.5f, 2f)) }
        updateActiveFilter(VideoFilter.GAMMA)
    }
    
    fun applyHueFilter(hue: Float) {
        updateFilterSetting { it.copy(hue = hue.coerceIn(-180f, 180f)) }
        updateActiveFilter(VideoFilter.HUE)
    }
    
    fun applyTemperatureFilter(temperature: Float) {
        updateFilterSetting { it.copy(temperature = temperature.coerceIn(-1f, 1f)) }
        updateActiveFilter(VideoFilter.TEMPERATURE)
    }
    
    fun applyTintFilter(tint: Float) {
        updateFilterSetting { it.copy(tint = tint.coerceIn(-1f, 1f)) }
        updateActiveFilter(VideoFilter.TINT)
    }
    
    fun applyGrayscaleFilter(enabled: Boolean) {
        updateFilterSetting { it.copy(grayscale = enabled) }
        if (enabled) {
            updateActiveFilter(VideoFilter.GRAYSCALE)
        } else {
            removeActiveFilter(VideoFilter.GRAYSCALE)
        }
    }
    
    fun applySepiaFilter(enabled: Boolean) {
        updateFilterSetting { it.copy(sepia = enabled) }
        if (enabled) {
            updateActiveFilter(VideoFilter.SEPIA)
        } else {
            removeActiveFilter(VideoFilter.SEPIA)
        }
    }
    
    fun applyNegativeFilter(enabled: Boolean) {
        updateFilterSetting { it.copy(negative = enabled) }
        if (enabled) {
            updateActiveFilter(VideoFilter.NEGATIVE)
        } else {
            removeActiveFilter(VideoFilter.NEGATIVE)
        }
    }
    
    fun applyVignetteFilter(intensity: Float) {
        updateFilterSetting { it.copy(vignette = intensity.coerceIn(0f, 1f)) }
        if (intensity > 0) {
            updateActiveFilter(VideoFilter.VIGNETTE)
        } else {
            removeActiveFilter(VideoFilter.VIGNETTE)
        }
    }
    
    fun applyBlurFilter(radius: Float) {
        updateFilterSetting { it.copy(blur = radius.coerceIn(0f, 25f)) }
        if (radius > 0) {
            updateActiveFilter(VideoFilter.BLUR)
        } else {
            removeActiveFilter(VideoFilter.BLUR)
        }
    }
    
    fun applyNoiseReduction(strength: Float) {
        updateFilterSetting { it.copy(noiseReduction = strength.coerceIn(0f, 1f)) }
        if (strength > 0) {
            updateActiveFilter(VideoFilter.NOISE_REDUCTION)
        } else {
            removeActiveFilter(VideoFilter.NOISE_REDUCTION)
        }
    }
    
    fun applyEdgeEnhancement(strength: Float) {
        updateFilterSetting { it.copy(edgeEnhancement = strength.coerceIn(0f, 1f)) }
        if (strength > 0) {
            updateActiveFilter(VideoFilter.EDGE_ENHANCEMENT)
        } else {
            removeActiveFilter(VideoFilter.EDGE_ENHANCEMENT)
        }
    }
    
    fun apply3DEffect(depth: Float) {
        updateFilterSetting { it.copy(depth3D = depth.coerceIn(0f, 1f)) }
        if (depth > 0) {
            updateActiveFilter(VideoFilter.STEREOSCOPIC_3D)
        } else {
            removeActiveFilter(VideoFilter.STEREOSCOPIC_3D)
        }
    }
    
    fun applyPreset(preset: FilterPreset) {
        val settings = when (preset) {
            FilterPreset.VIVID -> FilterSettings(
                brightness = 0.1f,
                contrast = 1.2f,
                saturation = 1.3f,
                sharpness = 0.3f
            )
            FilterPreset.CINEMA -> FilterSettings(
                brightness = -0.05f,
                contrast = 1.1f,
                saturation = 0.9f,
                temperature = 0.1f,
                vignette = 0.3f
            )
            FilterPreset.WARM -> FilterSettings(
                temperature = 0.3f,
                tint = 0.1f,
                saturation = 1.1f
            )
            FilterPreset.COOL -> FilterSettings(
                temperature = -0.3f,
                tint = -0.1f,
                saturation = 0.95f
            )
            FilterPreset.VINTAGE -> FilterSettings(
                sepia = true,
                vignette = 0.4f,
                contrast = 0.9f
            )
            FilterPreset.BLACK_WHITE -> FilterSettings(
                grayscale = true,
                contrast = 1.2f
            )
            FilterPreset.HDR_ENHANCE -> FilterSettings(
                brightness = 0.05f,
                contrast = 1.3f,
                saturation = 1.2f,
                gamma = 1.1f,
                sharpness = 0.4f
            )
            FilterPreset.NIGHT_MODE -> FilterSettings(
                brightness = -0.2f,
                temperature = 0.4f,
                contrast = 0.8f
            )
            FilterPreset.DAYLIGHT -> FilterSettings(
                brightness = 0.15f,
                contrast = 1.1f,
                temperature = -0.1f
            )
            FilterPreset.NONE -> FilterSettings()
        }
        
        _filterSettings.value = settings
        updateActiveFiltersFromSettings()
    }
    
    private fun updateFilterSetting(update: (FilterSettings) -> FilterSettings) {
        _filterSettings.value = update(_filterSettings.value)
    }
    
    private fun updateActiveFilter(filter: VideoFilter) {
        val current = _activeFilters.value.toMutableList()
        if (!current.contains(filter)) {
            current.add(filter)
            _activeFilters.value = current
        }
    }
    
    private fun removeActiveFilter(filter: VideoFilter) {
        _activeFilters.value = _activeFilters.value.filter { it != filter }
    }
    
    private fun updateActiveFiltersFromSettings() {
        val filters = mutableListOf<VideoFilter>()
        val settings = _filterSettings.value
        
        if (settings.brightness != 0f) filters.add(VideoFilter.BRIGHTNESS)
        if (settings.contrast != 1f) filters.add(VideoFilter.CONTRAST)
        if (settings.saturation != 1f) filters.add(VideoFilter.SATURATION)
        if (settings.sharpness != 0f) filters.add(VideoFilter.SHARPNESS)
        if (settings.gamma != 1f) filters.add(VideoFilter.GAMMA)
        if (settings.hue != 0f) filters.add(VideoFilter.HUE)
        if (settings.temperature != 0f) filters.add(VideoFilter.TEMPERATURE)
        if (settings.tint != 0f) filters.add(VideoFilter.TINT)
        if (settings.grayscale) filters.add(VideoFilter.GRAYSCALE)
        if (settings.sepia) filters.add(VideoFilter.SEPIA)
        if (settings.negative) filters.add(VideoFilter.NEGATIVE)
        if (settings.vignette > 0f) filters.add(VideoFilter.VIGNETTE)
        if (settings.blur > 0f) filters.add(VideoFilter.BLUR)
        if (settings.noiseReduction > 0f) filters.add(VideoFilter.NOISE_REDUCTION)
        if (settings.edgeEnhancement > 0f) filters.add(VideoFilter.EDGE_ENHANCEMENT)
        if (settings.depth3D > 0f) filters.add(VideoFilter.STEREOSCOPIC_3D)
        
        _activeFilters.value = filters
    }
    
    fun createGlEffect(): GlEffect? {
        val settings = _filterSettings.value
        val effects = mutableListOf<GlEffect>()
        
        // Add active effects based on settings
        if (settings.brightness != 0f) {
            effects.add(videoEffectProcessor.createBrightnessEffect(settings.brightness))
        }
        if (settings.contrast != 1f) {
            effects.add(videoEffectProcessor.createContrastEffect(settings.contrast))
        }
        if (settings.saturation != 1f) {
            effects.add(videoEffectProcessor.createSaturationEffect(settings.saturation))
        }
        if (settings.hue != 0f) {
            effects.add(videoEffectProcessor.createHueEffect(settings.hue))
        }
        if (settings.temperature != 0f) {
            effects.add(videoEffectProcessor.createTemperatureEffect(settings.temperature))
        }
        if (settings.sharpness > 0f) {
            effects.add(videoEffectProcessor.createSharpnessEffect(settings.sharpness))
        }
        if (settings.grayscale) {
            effects.add(videoEffectProcessor.createGrayscaleEffect())
        }
        if (settings.sepia) {
            effects.add(videoEffectProcessor.createSepiaEffect())
        }
        if (settings.negative) {
            effects.add(videoEffectProcessor.createNegativeEffect())
        }
        if (settings.vignette > 0f) {
            effects.add(videoEffectProcessor.createVignetteEffect(settings.vignette))
        }
        if (settings.blur > 0f) {
            effects.add(videoEffectProcessor.createBlurEffect(settings.blur))
        }
        
        return when (effects.size) {
            0 -> null
            1 -> effects.first()
            else -> videoEffectProcessor.combineEffects(effects)
        }
    }
    
    fun resetFilters() {
        _filterSettings.value = FilterSettings()
        _activeFilters.value = emptyList()
    }
    
    fun saveFilterPreset(name: String): CustomFilterPreset {
        return CustomFilterPreset(
            name = name,
            settings = _filterSettings.value.copy()
        )
    }
    
    fun loadFilterPreset(preset: CustomFilterPreset) {
        _filterSettings.value = preset.settings
        updateActiveFiltersFromSettings()
    }
    
    fun getFilterIntensity(filter: VideoFilter): Float {
        val settings = _filterSettings.value
        return when (filter) {
            VideoFilter.BRIGHTNESS -> settings.brightness
            VideoFilter.CONTRAST -> settings.contrast
            VideoFilter.SATURATION -> settings.saturation
            VideoFilter.SHARPNESS -> settings.sharpness
            VideoFilter.GAMMA -> settings.gamma
            VideoFilter.HUE -> settings.hue
            VideoFilter.TEMPERATURE -> settings.temperature
            VideoFilter.TINT -> settings.tint
            VideoFilter.GRAYSCALE -> if (settings.grayscale) 1f else 0f
            VideoFilter.SEPIA -> if (settings.sepia) 1f else 0f
            VideoFilter.NEGATIVE -> if (settings.negative) 1f else 0f
            VideoFilter.VIGNETTE -> settings.vignette
            VideoFilter.BLUR -> settings.blur
            VideoFilter.NOISE_REDUCTION -> settings.noiseReduction
            VideoFilter.EDGE_ENHANCEMENT -> settings.edgeEnhancement
            VideoFilter.STEREOSCOPIC_3D -> settings.depth3D
        }
    }
}

// Note: Media3's effect API has changed significantly. 
// Video filters are temporarily disabled until proper implementation with new API
// The ColorMatrixEffect class has been commented out as the GlShaderProgram interface 
// has too many complex requirements that keep changing between Media3 versions

/*
@UnstableApi
class ColorMatrixEffect(private val settings: FilterSettings) : GlEffect {
    
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        // This would need to be properly implemented with the new Media3 effect API
        // For now, commenting out to avoid compilation errors
        throw UnsupportedOperationException("Video filters not yet implemented with Media3")
    }
    
    private fun createColorMatrix(settings: FilterSettings): FloatArray {
        val matrix = FloatArray(16)
        Matrix.setIdentityM(matrix, 0)
        
        // Apply brightness
        val brightness = settings.brightness
        
        // Apply contrast
        val contrast = settings.contrast
        val translate = (1f - contrast) / 2f
        
        // Apply saturation
        val saturation = settings.saturation
        val invSat = 1f - saturation
        val R = 0.213f * invSat
        val G = 0.715f * invSat
        val B = 0.072f * invSat
        
        // Build combined matrix
        matrix[0] = R + saturation * contrast
        matrix[1] = G
        matrix[2] = B
        matrix[3] = 0f
        
        matrix[4] = R
        matrix[5] = G + saturation * contrast
        matrix[6] = B
        matrix[7] = 0f
        
        matrix[8] = R
        matrix[9] = G
        matrix[10] = B + saturation * contrast
        matrix[11] = 0f
        
        matrix[12] = 0f
        matrix[13] = 0f
        matrix[14] = 0f
        matrix[15] = 1f
        
        // Apply grayscale
        if (settings.grayscale) {
            for (i in 0..2) {
                matrix[i * 4] = 0.299f
                matrix[i * 4 + 1] = 0.587f
                matrix[i * 4 + 2] = 0.114f
            }
        }
        
        // Apply sepia
        if (settings.sepia) {
            val sepiaMatrix = floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f,
                0.349f, 0.686f, 0.168f, 0f,
                0.272f, 0.534f, 0.131f, 0f,
                0f, 0f, 0f, 1f
            )
            multiplyMatrices(matrix, sepiaMatrix)
        }
        
        // Apply negative
        if (settings.negative) {
            for (i in 0..2) {
                for (j in 0..2) {
                    matrix[i * 4 + j] *= -1f
                }
            }
        }
        
        return matrix
    }
    
    private fun createColorOffset(settings: FilterSettings): FloatArray {
        val offset = FloatArray(4)
        
        // Brightness offset
        val brightness = settings.brightness
        offset[0] = brightness
        offset[1] = brightness
        offset[2] = brightness
        offset[3] = 0f
        
        // Contrast offset
        val contrast = settings.contrast
        val translate = (1f - contrast) / 2f
        offset[0] += translate
        offset[1] += translate
        offset[2] += translate
        
        // Temperature adjustment
        val temp = settings.temperature
        offset[0] += temp * 0.1f  // Add red for warm
        offset[2] -= temp * 0.1f  // Remove blue for warm
        
        // Tint adjustment
        val tint = settings.tint
        offset[1] += tint * 0.1f  // Adjust green channel
        
        // Negative offset
        if (settings.negative) {
            offset[0] += 1f
            offset[1] += 1f
            offset[2] += 1f
        }
        
        return offset
    }
    
    private fun multiplyMatrices(a: FloatArray, b: FloatArray) {
        val result = FloatArray(16)
        Matrix.multiplyMM(result, 0, a, 0, b, 0)
        System.arraycopy(result, 0, a, 0, 16)
    }
}
*/

enum class VideoFilter {
    BRIGHTNESS,
    CONTRAST,
    SATURATION,
    SHARPNESS,
    GAMMA,
    HUE,
    TEMPERATURE,
    TINT,
    GRAYSCALE,
    SEPIA,
    NEGATIVE,
    VIGNETTE,
    BLUR,
    NOISE_REDUCTION,
    EDGE_ENHANCEMENT,
    STEREOSCOPIC_3D
}

enum class FilterPreset {
    NONE,
    VIVID,
    CINEMA,
    WARM,
    COOL,
    VINTAGE,
    BLACK_WHITE,
    HDR_ENHANCE,
    NIGHT_MODE,
    DAYLIGHT
}

data class FilterSettings(
    val brightness: Float = 0f,        // -1 to 1
    val contrast: Float = 1f,          // 0 to 2
    val saturation: Float = 1f,        // 0 to 2
    val sharpness: Float = 0f,         // 0 to 2
    val gamma: Float = 1f,             // 0.5 to 2
    val hue: Float = 0f,               // -180 to 180
    val temperature: Float = 0f,       // -1 to 1
    val tint: Float = 0f,              // -1 to 1
    val grayscale: Boolean = false,
    val sepia: Boolean = false,
    val negative: Boolean = false,
    val vignette: Float = 0f,          // 0 to 1
    val blur: Float = 0f,              // 0 to 25
    val noiseReduction: Float = 0f,    // 0 to 1
    val edgeEnhancement: Float = 0f,   // 0 to 1
    val depth3D: Float = 0f            // 0 to 1
)

data class CustomFilterPreset(
    val name: String,
    val settings: FilterSettings
)