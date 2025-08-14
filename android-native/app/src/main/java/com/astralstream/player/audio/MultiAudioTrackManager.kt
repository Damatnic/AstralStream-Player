package com.astralstream.player.audio

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class MultiAudioTrackManager @Inject constructor() {
    
    private val _audioTracks = MutableStateFlow<List<AudioTrackInfo>>(emptyList())
    val audioTracks: StateFlow<List<AudioTrackInfo>> = _audioTracks.asStateFlow()
    
    private val _currentAudioTrack = MutableStateFlow<AudioTrackInfo?>(null)
    val currentAudioTrack: StateFlow<AudioTrackInfo?> = _currentAudioTrack.asStateFlow()
    
    private val _dualAudioEnabled = MutableStateFlow(false)
    val dualAudioEnabled: StateFlow<Boolean> = _dualAudioEnabled.asStateFlow()
    
    private val _primaryAudioTrack = MutableStateFlow<AudioTrackInfo?>(null)
    val primaryAudioTrack: StateFlow<AudioTrackInfo?> = _primaryAudioTrack.asStateFlow()
    
    private val _secondaryAudioTrack = MutableStateFlow<AudioTrackInfo?>(null)
    val secondaryAudioTrack: StateFlow<AudioTrackInfo?> = _secondaryAudioTrack.asStateFlow()
    
    private var exoPlayer: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    
    fun initialize(player: ExoPlayer, selector: DefaultTrackSelector) {
        exoPlayer = player
        trackSelector = selector
        refreshAudioTracks()
    }
    
    fun refreshAudioTracks() {
        val tracks = mutableListOf<AudioTrackInfo>()
        
        trackSelector?.currentMappedTrackInfo?.let { mappedTrackInfo ->
            for (rendererIndex in 0 until mappedTrackInfo.rendererCount) {
                if (mappedTrackInfo.getRendererType(rendererIndex) == C.TRACK_TYPE_AUDIO) {
                    val trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex)
                    
                    for (groupIndex in 0 until trackGroups.length) {
                        val trackGroup = trackGroups[groupIndex]
                        
                        for (trackIndex in 0 until trackGroup.length) {
                            val format = trackGroup.getFormat(trackIndex)
                            val isSupported = mappedTrackInfo.getTrackSupport(
                                rendererIndex, groupIndex, trackIndex
                            ) == C.FORMAT_HANDLED
                            
                            if (isSupported) {
                                tracks.add(
                                    AudioTrackInfo(
                                        rendererIndex = rendererIndex,
                                        groupIndex = groupIndex,
                                        trackIndex = trackIndex,
                                        format = format,
                                        language = format.language ?: "Unknown",
                                        label = format.label ?: generateLabel(format),
                                        bitrate = format.bitrate,
                                        sampleRate = format.sampleRate,
                                        channelCount = format.channelCount,
                                        codec = format.codecs ?: "Unknown",
                                        isDefault = format.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0,
                                        isForced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0,
                                        isAutoSelected = format.selectionFlags and C.SELECTION_FLAG_AUTOSELECT != 0
                                    )
                                )
                            }
                        }
                    }
                    
                    // Update current track
                    val selectedTrack = getSelectedAudioTrack(rendererIndex, trackGroups)
                    _currentAudioTrack.value = selectedTrack
                }
            }
        }
        
        _audioTracks.value = tracks
    }
    
    private fun generateLabel(format: Format): String {
        val parts = mutableListOf<String>()
        
        format.language?.let { lang ->
            parts.add(getLanguageDisplayName(lang))
        }
        
        when (format.channelCount) {
            1 -> parts.add("Mono")
            2 -> parts.add("Stereo")
            6 -> parts.add("5.1")
            8 -> parts.add("7.1")
            else -> format.channelCount?.let { parts.add("$it channels") }
        }
        
        format.codecs?.let { codec ->
            when {
                codec.contains("ac-3") -> parts.add("Dolby Digital")
                codec.contains("ec-3") -> parts.add("Dolby Digital Plus")
                codec.contains("ac-4") -> parts.add("Dolby AC-4")
                codec.contains("dts") -> parts.add("DTS")
                codec.contains("dtshd") -> parts.add("DTS-HD")
                codec.contains("truehd") -> parts.add("Dolby TrueHD")
                codec.contains("opus") -> parts.add("Opus")
                codec.contains("aac") -> parts.add("AAC")
                codec.contains("mp3") -> parts.add("MP3")
                codec.contains("flac") -> parts.add("FLAC")
                codec.contains("pcm") -> parts.add("PCM")
            }
        }
        
        if (format.bitrate > 0) {
            parts.add("${format.bitrate / 1000} kbps")
        }
        
        return parts.joinToString(" • ")
    }
    
    private fun getLanguageDisplayName(languageCode: String): String {
        return when (languageCode.lowercase()) {
            "en", "eng" -> "English"
            "es", "spa" -> "Spanish"
            "fr", "fra", "fre" -> "French"
            "de", "deu", "ger" -> "German"
            "it", "ita" -> "Italian"
            "pt", "por" -> "Portuguese"
            "ru", "rus" -> "Russian"
            "ja", "jpn" -> "Japanese"
            "ko", "kor" -> "Korean"
            "zh", "chi", "zho" -> "Chinese"
            "ar", "ara" -> "Arabic"
            "hi", "hin" -> "Hindi"
            "nl", "nld", "dut" -> "Dutch"
            "pl", "pol" -> "Polish"
            "tr", "tur" -> "Turkish"
            "sv", "swe" -> "Swedish"
            "no", "nor" -> "Norwegian"
            "da", "dan" -> "Danish"
            "fi", "fin" -> "Finnish"
            "el", "ell", "gre" -> "Greek"
            "he", "heb" -> "Hebrew"
            "th", "tha" -> "Thai"
            "vi", "vie" -> "Vietnamese"
            "id", "ind" -> "Indonesian"
            "ms", "msa", "may" -> "Malay"
            "uk", "ukr" -> "Ukrainian"
            "cs", "ces", "cze" -> "Czech"
            "hu", "hun" -> "Hungarian"
            "ro", "ron", "rum" -> "Romanian"
            "bg", "bul" -> "Bulgarian"
            "hr", "hrv" -> "Croatian"
            "sr", "srp" -> "Serbian"
            "sk", "slk", "slo" -> "Slovak"
            "sl", "slv" -> "Slovenian"
            "et", "est" -> "Estonian"
            "lv", "lav" -> "Latvian"
            "lt", "lit" -> "Lithuanian"
            "fa", "fas", "per" -> "Persian"
            "ur", "urd" -> "Urdu"
            "bn", "ben" -> "Bengali"
            "ta", "tam" -> "Tamil"
            "te", "tel" -> "Telugu"
            "ml", "mal" -> "Malayalam"
            "kn", "kan" -> "Kannada"
            "gu", "guj" -> "Gujarati"
            "mr", "mar" -> "Marathi"
            "pa", "pan" -> "Punjabi"
            else -> languageCode.uppercase()
        }
    }
    
    private fun getSelectedAudioTrack(
        rendererIndex: Int, 
        trackGroups: TrackGroupArray
    ): AudioTrackInfo? {
        val trackSelections = exoPlayer?.currentTracks
        if (trackSelections != null) {
            for (group in trackSelections.groups) {
                if (group.isSelected) {
                    for (i in 0 until group.length) {
                        if (group.isTrackSelected(i)) {
                            val format = group.getTrackFormat(i)
                            // Find matching track in audioTracks
                            return _audioTracks.value.find { track ->
                                track.format == format
                            }
                        }
                    }
                }
            }
        }
        return null
    }
    
    fun selectAudioTrack(track: AudioTrackInfo) {
        trackSelector?.let { selector ->
            val parameters = selector.buildUponParameters()
            
            // Create track selection override
            val override = TrackSelectionOverride(
                TrackGroup(track.format),
                listOf(0)
            )
            
            parameters.setOverrideForType(override)
            selector.setParameters(parameters.build())
            
            _currentAudioTrack.value = track
            refreshAudioTracks()
        }
    }
    
    fun selectAudioTrackByLanguage(language: String) {
        val track = _audioTracks.value.find { 
            it.language.equals(language, ignoreCase = true) 
        }
        track?.let { selectAudioTrack(it) }
    }
    
    fun selectAudioTrackByIndex(index: Int) {
        if (index in _audioTracks.value.indices) {
            selectAudioTrack(_audioTracks.value[index])
        }
    }
    
    fun enableDualAudio(primary: AudioTrackInfo, secondary: AudioTrackInfo) {
        _dualAudioEnabled.value = true
        _primaryAudioTrack.value = primary
        _secondaryAudioTrack.value = secondary
        
        // Note: Actual dual audio playback would require custom audio processor
        // This is a placeholder for the UI state management
    }
    
    fun disableDualAudio() {
        _dualAudioEnabled.value = false
        _primaryAudioTrack.value = null
        _secondaryAudioTrack.value = null
    }
    
    fun cycleAudioTrack() {
        val tracks = _audioTracks.value
        if (tracks.isEmpty()) return
        
        val currentIndex = tracks.indexOf(_currentAudioTrack.value)
        val nextIndex = (currentIndex + 1) % tracks.size
        selectAudioTrackByIndex(nextIndex)
    }
    
    fun getPreferredAudioTrack(preferredLanguages: List<String>): AudioTrackInfo? {
        for (language in preferredLanguages) {
            val track = _audioTracks.value.find { 
                it.language.equals(language, ignoreCase = true) 
            }
            if (track != null) return track
        }
        
        // Return default track if no preferred language found
        return _audioTracks.value.find { it.isDefault } ?: _audioTracks.value.firstOrNull()
    }
    
    fun hasMultipleAudioTracks(): Boolean {
        return _audioTracks.value.size > 1
    }
    
    fun getAudioTrackCount(): Int {
        return _audioTracks.value.size
    }
    
    fun getAudioTracksByLanguage(language: String): List<AudioTrackInfo> {
        return _audioTracks.value.filter { 
            it.language.equals(language, ignoreCase = true) 
        }
    }
    
    fun getAudioTracksByCodec(codec: String): List<AudioTrackInfo> {
        return _audioTracks.value.filter { 
            it.codec.contains(codec, ignoreCase = true) 
        }
    }
    
    fun getSurroundSoundTracks(): List<AudioTrackInfo> {
        return _audioTracks.value.filter { 
            it.channelCount > 2 
        }
    }
    
    fun setAudioTrackVolume(trackIndex: Int, volume: Float) {
        // This would require custom audio processor implementation
        // Placeholder for future enhancement
    }
    
    fun setAudioDelay(delayMs: Long) {
        // Apply audio delay to current track
        exoPlayer?.let { player ->
            // This would require custom audio processor
            // Placeholder for audio sync adjustment
        }
    }
    
    fun release() {
        exoPlayer = null
        trackSelector = null
        _audioTracks.value = emptyList()
        _currentAudioTrack.value = null
        _dualAudioEnabled.value = false
        _primaryAudioTrack.value = null
        _secondaryAudioTrack.value = null
    }
}

// Extension to work with Media3's TrackGroup array
typealias TrackGroupArray = com.google.common.collect.ImmutableList<TrackGroup>

data class AudioTrackInfo(
    val rendererIndex: Int,
    val groupIndex: Int,
    val trackIndex: Int,
    val format: Format,
    val language: String,
    val label: String,
    val bitrate: Int,
    val sampleRate: Int,
    val channelCount: Int,
    val codec: String,
    val isDefault: Boolean,
    val isForced: Boolean,
    val isAutoSelected: Boolean
) {
    val id: String = "${rendererIndex}_${groupIndex}_${trackIndex}"
    
    val displayName: String
        get() = buildString {
            append(label)
            if (isDefault) append(" (Default)")
            if (isForced) append(" (Forced)")
        }
    
    val isSurroundSound: Boolean
        get() = channelCount > 2
    
    val qualityInfo: String
        get() = buildString {
            if (sampleRate > 0) {
                append("${sampleRate}Hz")
            }
            if (bitrate > 0) {
                if (isNotEmpty()) append(" • ")
                append("${bitrate / 1000}kbps")
            }
        }
}