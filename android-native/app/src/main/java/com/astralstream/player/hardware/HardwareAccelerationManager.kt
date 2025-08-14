package com.astralstream.player.hardware

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.view.Display
import android.view.Surface
import androidx.annotation.RequiresApi
import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class HardwareAccelerationManager @Inject constructor(
    private val context: Context
) {
    
    private val _codecSupport = MutableStateFlow(CodecSupport())
    val codecSupport: StateFlow<CodecSupport> = _codecSupport.asStateFlow()
    
    private val _hardwareCapabilities = MutableStateFlow(HardwareCapabilities())
    val hardwareCapabilities: StateFlow<HardwareCapabilities> = _hardwareCapabilities.asStateFlow()
    
    private val supportedCodecs = mutableMapOf<String, CodecInfo>()
    
    init {
        analyzeHardwareCapabilities()
    }
    
    private fun analyzeHardwareCapabilities() {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        
        // Check for H.264/AVC support
        checkCodecSupport(codecList, MimeTypes.VIDEO_H264, "H.264/AVC")
        
        // Check for H.265/HEVC support
        checkCodecSupport(codecList, MimeTypes.VIDEO_H265, "H.265/HEVC")
        
        // Check for VP8 support
        checkCodecSupport(codecList, MimeTypes.VIDEO_VP8, "VP8")
        
        // Check for VP9 support
        checkCodecSupport(codecList, MimeTypes.VIDEO_VP9, "VP9")
        
        // Check for AV1 support
        checkCodecSupport(codecList, MimeTypes.VIDEO_AV1, "AV1")
        
        // Check for Dolby Vision support
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            checkDolbyVisionSupport(codecList)
        }
        
        // Check HDR capabilities
        checkHDRSupport()
        
        // Update codec support state
        updateCodecSupportState()
    }
    
    private fun checkCodecSupport(codecList: MediaCodecList, mimeType: String, codecName: String) {
        val codecInfos = codecList.codecInfos
        
        for (info in codecInfos) {
            if (info.isEncoder) continue
            
            val types = info.supportedTypes
            for (type in types) {
                if (type.equals(mimeType, ignoreCase = true)) {
                    val capabilities = info.getCapabilitiesForType(type)
                    val videoCapabilities = capabilities.videoCapabilities
                    
                    val codecInfo = CodecInfo(
                        name = codecName,
                        mimeType = mimeType,
                        hardwareAccelerated = isHardwareAccelerated(info),
                        maxWidth = videoCapabilities?.supportedWidths?.upper ?: 0,
                        maxHeight = videoCapabilities?.supportedHeights?.upper ?: 0,
                        maxFrameRate = videoCapabilities?.supportedFrameRates?.upper?.toInt() ?: 0,
                        maxBitrate = videoCapabilities?.bitrateRange?.upper?.toInt() ?: 0,
                        profileLevels = capabilities.profileLevels.map { 
                            ProfileLevel(it.profile, it.level)
                        }
                    )
                    
                    supportedCodecs[mimeType] = codecInfo
                    break
                }
            }
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun checkDolbyVisionSupport(codecList: MediaCodecList) {
        val dolbyVisionMimeType = MimeTypes.VIDEO_DOLBY_VISION
        
        try {
            val decoderInfos = MediaCodecUtil.getDecoderInfos(dolbyVisionMimeType, false, false)
            if (decoderInfos.isNotEmpty()) {
                val codecInfo = CodecInfo(
                    name = "Dolby Vision",
                    mimeType = dolbyVisionMimeType,
                    hardwareAccelerated = true,
                    maxWidth = 3840,
                    maxHeight = 2160,
                    maxFrameRate = 60,
                    maxBitrate = 100_000_000,
                    profileLevels = listOf()
                )
                supportedCodecs[dolbyVisionMimeType] = codecInfo
                
                _hardwareCapabilities.value = _hardwareCapabilities.value.copy(
                    supportsDolbyVision = true
                )
            }
        } catch (e: MediaCodecUtil.DecoderQueryException) {
            // Dolby Vision not supported
        }
    }
    
    private fun checkHDRSupport() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Check for HDR10 support
            val hdr10Support = checkHDRProfile(
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10,
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
            )
            
            // Check for HDR10+ support
            val hdr10PlusSupport = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                checkHDRProfile(
                    MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10,
                    MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus
                )
            } else false
            
            _hardwareCapabilities.value = _hardwareCapabilities.value.copy(
                supportsHDR10 = hdr10Support,
                supportsHDR10Plus = hdr10PlusSupport,
                supportsHLG = checkHLGSupport()
            )
        }
    }
    
    private fun checkHDRProfile(vararg profiles: Int): Boolean {
        val hevcCodec = supportedCodecs[MimeTypes.VIDEO_H265] ?: return false
        
        for (profile in profiles) {
            if (hevcCodec.profileLevels.any { it.profile == profile }) {
                return true
            }
        }
        return false
    }
    
    private fun checkHLGSupport(): Boolean {
        // HLG (Hybrid Log-Gamma) support check
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && 
               supportedCodecs.containsKey(MimeTypes.VIDEO_H265)
    }
    
    private fun isHardwareAccelerated(codecInfo: MediaCodecInfo): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            codecInfo.isHardwareAccelerated
        } else {
            // Fallback for older API levels
            !codecInfo.name.startsWith("OMX.google.") && 
            !codecInfo.name.startsWith("c2.android.")
        }
    }
    
    private fun updateCodecSupportState() {
        _codecSupport.value = CodecSupport(
            h264 = supportedCodecs.containsKey(MimeTypes.VIDEO_H264),
            h265 = supportedCodecs.containsKey(MimeTypes.VIDEO_H265),
            vp8 = supportedCodecs.containsKey(MimeTypes.VIDEO_VP8),
            vp9 = supportedCodecs.containsKey(MimeTypes.VIDEO_VP9),
            av1 = supportedCodecs.containsKey(MimeTypes.VIDEO_AV1),
            dolbyVision = supportedCodecs.containsKey(MimeTypes.VIDEO_DOLBY_VISION)
        )
    }
    
    fun createOptimizedRenderersFactory(): DefaultRenderersFactory {
        return DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableDecoderFallback(true)
            setMediaCodecSelector(createOptimizedCodecSelector())
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setEnableAudioFloatOutput(true)
                // Audio offload is configured via track selector parameters
            }
            
            // Video tunneling is configured via track selector parameters
        }
    }
    
    private fun createOptimizedCodecSelector(): MediaCodecSelector {
        return object : MediaCodecSelector {
            override fun getDecoderInfos(
                mimeType: String,
                requiresSecureDecoder: Boolean,
                requiresTunnelingDecoder: Boolean
            ): List<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> {
                val defaultInfos = MediaCodecUtil.getDecoderInfos(
                    mimeType,
                    requiresSecureDecoder,
                    requiresTunnelingDecoder
                )
                
                // Prioritize hardware decoders
                return defaultInfos.sortedByDescending { codecInfo ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        codecInfo.hardwareAccelerated
                    } else {
                        !codecInfo.name.startsWith("OMX.google.")
                    }
                }
            }
        }
    }
    
    fun configureHDRDisplay(surface: Surface, display: Display?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && display != null) {
            val hdrCapabilities = display.hdrCapabilities
            hdrCapabilities?.let {
                val supportedHdrTypes = it.supportedHdrTypes
                
                _hardwareCapabilities.value = _hardwareCapabilities.value.copy(
                    displaySupportsHDR = supportedHdrTypes.isNotEmpty(),
                    maxLuminance = it.desiredMaxLuminance,
                    minLuminance = it.desiredMinLuminance
                )
            }
        }
    }
    
    fun getRecommendedDecoderMode(format: Format): DecoderRecommendation {
        val mimeType = format.sampleMimeType ?: return DecoderRecommendation.HARDWARE
        val width = format.width
        val height = format.height
        val frameRate = format.frameRate
        
        // Check if codec is supported
        val codecInfo = supportedCodecs[mimeType]
        if (codecInfo == null) {
            return DecoderRecommendation.SOFTWARE
        }
        
        // Check resolution support
        if (width > codecInfo.maxWidth || height > codecInfo.maxHeight) {
            return DecoderRecommendation.SOFTWARE
        }
        
        // Check frame rate support
        if (frameRate > codecInfo.maxFrameRate) {
            return DecoderRecommendation.HARDWARE_WITH_FALLBACK
        }
        
        // Check HDR content
        val colorInfo = format.colorInfo
        if (colorInfo != null && colorInfo.colorTransfer != C.COLOR_TRANSFER_SDR) {
            val hdrSupported = when {
                format.colorInfo?.colorTransfer == C.COLOR_TRANSFER_HLG -> 
                    _hardwareCapabilities.value.supportsHLG
                format.colorInfo?.colorTransfer == C.COLOR_TRANSFER_ST2084 -> 
                    _hardwareCapabilities.value.supportsHDR10
                else -> false
            }
            
            if (!hdrSupported) {
                return DecoderRecommendation.HARDWARE_WITH_FALLBACK
            }
        }
        
        // Use hardware acceleration for supported formats
        return if (codecInfo.hardwareAccelerated) {
            DecoderRecommendation.HARDWARE
        } else {
            DecoderRecommendation.SOFTWARE
        }
    }
    
    fun createVideoFrameMetadataListener(): VideoFrameMetadataListener {
        return VideoFrameMetadataListener { presentationTimeUs, releaseTimeNs, format, mediaFormat ->
            // Process frame metadata for advanced features
            mediaFormat?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val hdrStaticInfo = it.getByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO)
                    val colorTransfer = it.getInteger(MediaFormat.KEY_COLOR_TRANSFER, -1)
                    
                    // Update HDR playback state
                    _hardwareCapabilities.value = _hardwareCapabilities.value.copy(
                        isPlayingHDR = hdrStaticInfo != null || colorTransfer == MediaFormat.COLOR_TRANSFER_HLG
                    )
                }
            }
        }
    }
    
    fun getCodecInfo(mimeType: String): CodecInfo? {
        return supportedCodecs[mimeType]
    }
    
    fun getAllSupportedCodecs(): Map<String, CodecInfo> {
        return supportedCodecs.toMap()
    }
    
    fun supportsCodec(mimeType: String): Boolean {
        return supportedCodecs.containsKey(mimeType)
    }
    
    fun getMaxSupportedResolution(mimeType: String): Pair<Int, Int>? {
        val codecInfo = supportedCodecs[mimeType] ?: return null
        return Pair(codecInfo.maxWidth, codecInfo.maxHeight)
    }
    
    fun enableHardwareAcceleration(player: ExoPlayer) {
        // Configure player for optimal hardware acceleration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            player.setVideoFrameMetadataListener(createVideoFrameMetadataListener())
        }
    }
    
    fun getOptimalBufferSize(format: Format): Int {
        val bitrate = format.bitrate
        val colorInfo = format.colorInfo
        val isHDR = colorInfo != null && colorInfo.colorTransfer != C.COLOR_TRANSFER_SDR
        val is4K = format.width >= 3840 || format.height >= 2160
        
        return when {
            is4K && isHDR -> 50 * 1024 * 1024  // 50MB for 4K HDR
            is4K -> 30 * 1024 * 1024            // 30MB for 4K SDR
            isHDR -> 20 * 1024 * 1024           // 20MB for HDR
            bitrate > 10_000_000 -> 15 * 1024 * 1024  // 15MB for high bitrate
            else -> 10 * 1024 * 1024            // 10MB default
        }
    }
}

enum class DecoderRecommendation {
    HARDWARE,
    HARDWARE_WITH_FALLBACK,
    SOFTWARE
}

data class CodecSupport(
    val h264: Boolean = false,
    val h265: Boolean = false,
    val vp8: Boolean = false,
    val vp9: Boolean = false,
    val av1: Boolean = false,
    val dolbyVision: Boolean = false
)

data class HardwareCapabilities(
    val supportsHDR10: Boolean = false,
    val supportsHDR10Plus: Boolean = false,
    val supportsHLG: Boolean = false,
    val supportsDolbyVision: Boolean = false,
    val displaySupportsHDR: Boolean = false,
    val maxLuminance: Float = 0f,
    val minLuminance: Float = 0f,
    val isPlayingHDR: Boolean = false
)

data class CodecInfo(
    val name: String,
    val mimeType: String,
    val hardwareAccelerated: Boolean,
    val maxWidth: Int,
    val maxHeight: Int,
    val maxFrameRate: Int,
    val maxBitrate: Int,
    val profileLevels: List<ProfileLevel>
)

data class ProfileLevel(
    val profile: Int,
    val level: Int
)