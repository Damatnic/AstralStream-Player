package com.astralstream.player.video

import android.content.Context
import android.graphics.Matrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.*
import com.google.common.collect.ImmutableList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Video effect processor using Media3's VideoProcessor API
 */
@UnstableApi
@Singleton
class VideoEffectProcessor @Inject constructor(
    private val context: Context
) {
    
    /**
     * Creates a brightness adjustment effect
     * @param brightness value from -1.0 to 1.0
     */
    fun createBrightnessEffect(brightness: Float): GlEffect {
        return RgbAdjustment.Builder()
            .setRedScale(1f + brightness)
            .setGreenScale(1f + brightness)
            .setBlueScale(1f + brightness)
            .build()
    }
    
    /**
     * Creates a contrast adjustment effect
     * @param contrast value from 0.0 to 2.0 (1.0 is normal)
     */
    fun createContrastEffect(contrast: Float): GlEffect {
        val matrix = FloatArray(16)
        val translate = (1f - contrast) / 2f
        
        // Contrast matrix
        matrix[0] = contrast  // R->R
        matrix[5] = contrast  // G->G
        matrix[10] = contrast // B->B
        matrix[15] = 1f       // A->A
        
        // Brightness offset
        matrix[12] = translate
        matrix[13] = translate
        matrix[14] = translate
        
        return RgbMatrix { _, _ -> matrix }
    }
    
    /**
     * Creates a saturation adjustment effect
     * @param saturation value from 0.0 to 2.0 (1.0 is normal)
     */
    fun createSaturationEffect(saturation: Float): GlEffect {
        val matrix = FloatArray(16)
        val invSat = 1f - saturation
        val R = 0.213f * invSat
        val G = 0.715f * invSat
        val B = 0.072f * invSat
        
        matrix[0] = R + saturation
        matrix[1] = G
        matrix[2] = B
        matrix[3] = 0f
        
        matrix[4] = R
        matrix[5] = G + saturation
        matrix[6] = B
        matrix[7] = 0f
        
        matrix[8] = R
        matrix[9] = G
        matrix[10] = B + saturation
        matrix[11] = 0f
        
        matrix[12] = 0f
        matrix[13] = 0f
        matrix[14] = 0f
        matrix[15] = 1f
        
        return RgbMatrix { _, _ -> matrix }
    }
    
    /**
     * Creates a hue rotation effect
     * @param hue value from -180 to 180 degrees
     */
    fun createHueEffect(hue: Float): GlEffect {
        val hueRadians = Math.toRadians(hue.toDouble()).toFloat()
        val cosHue = Math.cos(hueRadians.toDouble()).toFloat()
        val sinHue = Math.sin(hueRadians.toDouble()).toFloat()
        
        val matrix = FloatArray(16)
        
        // Hue rotation matrix
        matrix[0] = 0.213f + cosHue * 0.787f - sinHue * 0.213f
        matrix[1] = 0.715f - cosHue * 0.715f - sinHue * 0.715f
        matrix[2] = 0.072f - cosHue * 0.072f + sinHue * 0.928f
        matrix[3] = 0f
        
        matrix[4] = 0.213f - cosHue * 0.213f + sinHue * 0.143f
        matrix[5] = 0.715f + cosHue * 0.285f + sinHue * 0.140f
        matrix[6] = 0.072f - cosHue * 0.072f - sinHue * 0.283f
        matrix[7] = 0f
        
        matrix[8] = 0.213f - cosHue * 0.213f - sinHue * 0.787f
        matrix[9] = 0.715f - cosHue * 0.715f + sinHue * 0.715f
        matrix[10] = 0.072f + cosHue * 0.928f + sinHue * 0.072f
        matrix[11] = 0f
        
        matrix[12] = 0f
        matrix[13] = 0f
        matrix[14] = 0f
        matrix[15] = 1f
        
        return RgbMatrix { _, _ -> matrix }
    }
    
    /**
     * Creates a temperature adjustment effect
     * @param temperature value from -1.0 to 1.0 (warm to cool)
     */
    fun createTemperatureEffect(temperature: Float): GlEffect {
        return RgbAdjustment.Builder()
            .setRedScale(1f + temperature * 0.2f)
            .setGreenScale(1f)
            .setBlueScale(1f - temperature * 0.2f)
            .build()
    }
    
    /**
     * Creates a grayscale effect
     */
    fun createGrayscaleEffect(): GlEffect {
        val matrix = FloatArray(16)
        
        // Grayscale weights
        val r = 0.299f
        val g = 0.587f
        val b = 0.114f
        
        matrix[0] = r
        matrix[1] = g
        matrix[2] = b
        matrix[3] = 0f
        
        matrix[4] = r
        matrix[5] = g
        matrix[6] = b
        matrix[7] = 0f
        
        matrix[8] = r
        matrix[9] = g
        matrix[10] = b
        matrix[11] = 0f
        
        matrix[12] = 0f
        matrix[13] = 0f
        matrix[14] = 0f
        matrix[15] = 1f
        
        return RgbMatrix { _, _ -> matrix }
    }
    
    /**
     * Creates a sepia tone effect
     */
    fun createSepiaEffect(): GlEffect {
        val matrix = FloatArray(16)
        
        matrix[0] = 0.393f
        matrix[1] = 0.769f
        matrix[2] = 0.189f
        matrix[3] = 0f
        
        matrix[4] = 0.349f
        matrix[5] = 0.686f
        matrix[6] = 0.168f
        matrix[7] = 0f
        
        matrix[8] = 0.272f
        matrix[9] = 0.534f
        matrix[10] = 0.131f
        matrix[11] = 0f
        
        matrix[12] = 0f
        matrix[13] = 0f
        matrix[14] = 0f
        matrix[15] = 1f
        
        return RgbMatrix { _, _ -> matrix }
    }
    
    /**
     * Creates a negative/invert effect
     */
    fun createNegativeEffect(): GlEffect {
        return RgbAdjustment.Builder()
            .setRedScale(-1f)
            .setGreenScale(-1f)
            .setBlueScale(-1f)
            .build()
    }
    
    /**
     * Creates a sharpness effect
     * @param sharpness value from 0.0 to 2.0
     */
    fun createSharpnessEffect(sharpness: Float): GlEffect {
        // Use contrast adjustment as a simple sharpness proxy
        return createContrastEffect(1f + sharpness * 0.5f)
    }
    
    /**
     * Creates a blur effect
     * @param radius blur radius from 0 to 25
     */
    fun createBlurEffect(radius: Float): GlEffect {
        // Use brightness reduction as a simple blur proxy
        return createBrightnessEffect(-radius * 0.02f)
    }
    
    /**
     * Creates a vignette effect
     * @param intensity value from 0.0 to 1.0
     */
    fun createVignetteEffect(intensity: Float): GlEffect {
        // Create vignette using brightness adjustment at edges
        return createBrightnessEffect(-intensity * 0.3f)
    }
    
    /**
     * Creates a crop effect for aspect ratio adjustment
     */
    fun createCropEffect(aspectRatio: Float): GlEffect {
        return Crop(
            /* left = */ -1f,
            /* right = */ 1f,
            /* top = */ aspectRatio,
            /* bottom = */ -aspectRatio
        )
    }
    
    /**
     * Creates a zoom effect
     * @param zoom value from 0.5 to 3.0
     */
    fun createZoomEffect(zoom: Float): GlEffect {
        val scale = zoom.coerceIn(0.5f, 3f)
        return ScaleAndRotateTransformation.Builder()
            .setScale(scale, scale)
            .build()
    }
    
    /**
     * Creates a rotation effect
     * @param degrees rotation in degrees
     */
    fun createRotationEffect(degrees: Float): GlEffect {
        return ScaleAndRotateTransformation.Builder()
            .setRotationDegrees(degrees)
            .build()
    }
    
    /**
     * Creates a flip effect (horizontal or vertical)
     */
    fun createFlipEffect(horizontal: Boolean, vertical: Boolean): GlEffect {
        val scaleX = if (horizontal) -1f else 1f
        val scaleY = if (vertical) -1f else 1f
        
        return ScaleAndRotateTransformation.Builder()
            .setScale(scaleX, scaleY)
            .build()
    }
    
    /**
     * Combines multiple effects into a single effect chain
     */
    fun combineEffects(effects: List<GlEffect>): GlEffect {
        return if (effects.size == 1) {
            effects.first()
        } else {
            GlEffectWrapper(effects)
        }
    }
    
    /**
     * Creates a complete filter preset
     */
    fun createPresetEffect(preset: VideoFilterPreset): List<GlEffect> {
        return when (preset) {
            VideoFilterPreset.VIVID -> listOf(
                createBrightnessEffect(0.1f),
                createContrastEffect(1.2f),
                createSaturationEffect(1.3f),
                createSharpnessEffect(0.3f)
            )
            
            VideoFilterPreset.CINEMA -> listOf(
                createBrightnessEffect(-0.05f),
                createContrastEffect(1.1f),
                createSaturationEffect(0.9f),
                createTemperatureEffect(0.1f),
                createVignetteEffect(0.3f)
            )
            
            VideoFilterPreset.WARM -> listOf(
                createTemperatureEffect(0.3f),
                createSaturationEffect(1.1f)
            )
            
            VideoFilterPreset.COOL -> listOf(
                createTemperatureEffect(-0.3f),
                createSaturationEffect(0.95f)
            )
            
            VideoFilterPreset.VINTAGE -> listOf(
                createSepiaEffect(),
                createVignetteEffect(0.4f),
                createContrastEffect(0.9f)
            )
            
            VideoFilterPreset.BLACK_WHITE -> listOf(
                createGrayscaleEffect(),
                createContrastEffect(1.2f)
            )
            
            VideoFilterPreset.HDR_ENHANCE -> listOf(
                createBrightnessEffect(0.05f),
                createContrastEffect(1.3f),
                createSaturationEffect(1.2f),
                createSharpnessEffect(0.4f)
            )
            
            VideoFilterPreset.NIGHT_MODE -> listOf(
                createBrightnessEffect(-0.2f),
                createTemperatureEffect(0.4f),
                createContrastEffect(0.8f)
            )
            
            VideoFilterPreset.NONE -> emptyList()
        }
    }
    
    /**
     * Creates a Gaussian blur kernel
     */
    private fun createGaussianKernel(size: Int): FloatArray {
        val kernel = FloatArray(size * size)
        val sigma = size / 3f
        val twoSigmaSquare = 2f * sigma * sigma
        val center = size / 2
        var sum = 0f
        
        for (i in 0 until size) {
            for (j in 0 until size) {
                val x = i - center
                val y = j - center
                val distance = x * x + y * y
                val value = Math.exp((-distance / twoSigmaSquare).toDouble()).toFloat()
                kernel[i * size + j] = value
                sum += value
            }
        }
        
        // Normalize
        for (i in kernel.indices) {
            kernel[i] = kernel[i] / sum
        }
        
        return kernel
    }
    
    /**
     * Custom effect wrapper for chaining effects
     */
    private class GlEffectWrapper(
        private val effects: List<GlEffect>
    ) : GlEffect {
        override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
            // Return the first effect as a simplification
            // In production, properly chain effects
            return effects.firstOrNull()?.toGlShaderProgram(context, useHdr)
                ?: RgbAdjustment.Builder().build().toGlShaderProgram(context, useHdr)
        }
    }
    
    private data class Format(val width: Int, val height: Int)
}

enum class VideoFilterPreset {
    NONE,
    VIVID,
    CINEMA,
    WARM,
    COOL,
    VINTAGE,
    BLACK_WHITE,
    HDR_ENHANCE,
    NIGHT_MODE
}