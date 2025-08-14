package com.astralstream.player.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.view.Surface
import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.astralstream.player.audio.MultiAudioTrackManager
import com.astralstream.player.controls.AdvancedPlayerControls
import com.astralstream.player.controls.LoopMode
import com.astralstream.player.hardware.HardwareAccelerationManager
import com.astralstream.player.manager.DecoderMode
import com.astralstream.player.manager.ExoPlayerManager
import com.astralstream.player.video.FilterPreset
import com.astralstream.player.video.VideoFiltersManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@UnstableApi
@HiltViewModel
class EnhancedPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exoPlayerManager: ExoPlayerManager,
    private val hardwareAccelerationManager: HardwareAccelerationManager,
    private val multiAudioTrackManager: MultiAudioTrackManager,
    private val videoFiltersManager: VideoFiltersManager,
    private val advancedPlayerControls: AdvancedPlayerControls
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(EnhancedPlayerUiState())
    val uiState: StateFlow<EnhancedPlayerUiState> = _uiState.asStateFlow()
    
    private var exoPlayer: ExoPlayer? = null
    private var currentVideoUri: Uri? = null
    
    init {
        observeStates()
        initializePlayer()
    }
    
    private fun observeStates() {
        viewModelScope.launch {
            combine(
                exoPlayerManager.playerState,
                hardwareAccelerationManager.codecSupport,
                hardwareAccelerationManager.hardwareCapabilities,
                multiAudioTrackManager.audioTracks,
                multiAudioTrackManager.currentAudioTrack,
                videoFiltersManager.activeFilters,
                videoFiltersManager.filterSettings,
                advancedPlayerControls.frameNavigationState,
                advancedPlayerControls.zoomPanState,
                advancedPlayerControls.playbackControlState
            ) { states ->
                val playerState = states[0] as com.astralstream.player.manager.ExoPlayerState
                val codecSupport = states[1] as com.astralstream.player.hardware.CodecSupport
                val hardwareCapabilities = states[2] as com.astralstream.player.hardware.HardwareCapabilities
                val audioTracks = states[3] as List<com.astralstream.player.audio.AudioTrackInfo>
                val currentAudioTrack = states[4] as? com.astralstream.player.audio.AudioTrackInfo
                val activeFilters = states[5] as List<com.astralstream.player.video.VideoFilter>
                val filterSettings = states[6] as com.astralstream.player.video.FilterSettings
                val frameNavState = states[7] as com.astralstream.player.controls.FrameNavigationState
                val zoomPanState = states[8] as com.astralstream.player.controls.ZoomPanState
                val playbackControlState = states[9] as com.astralstream.player.controls.PlaybackControlState
                
                EnhancedPlayerUiState(
                    // Basic playback state
                    isInitialized = playerState.isInitialized,
                    isPlaying = playerState.isPlaying,
                    isBuffering = playerState.playbackState == com.astralstream.player.manager.PlaybackState.BUFFERING,
                    isEnded = playerState.playbackState == com.astralstream.player.manager.PlaybackState.ENDED,
                    error = playerState.error,
                    
                    // Decoder and hardware info
                    decoderMode = playerState.decoderMode,
                    codecSupport = codecSupport,
                    hardwareCapabilities = hardwareCapabilities,
                    
                    // Audio tracks
                    audioTracks = audioTracks,
                    currentAudioTrack = currentAudioTrack,
                    hasMultipleAudioTracks = audioTracks.size > 1,
                    
                    // Video filters
                    activeFilters = activeFilters,
                    filterSettings = filterSettings,
                    
                    // Frame navigation
                    frameNavigationState = frameNavState,
                    
                    // Zoom and pan
                    zoomPanState = zoomPanState,
                    
                    // Playback controls
                    loopMode = playbackControlState.loopMode,
                    abLoopStart = playbackControlState.abLoopStart,
                    abLoopEnd = playbackControlState.abLoopEnd,
                    isABLoopEnabled = playbackControlState.isABLoopEnabled
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
    
    private fun initializePlayer() {
        // Determine optimal decoder mode based on hardware capabilities
        val decoderMode = if (hardwareAccelerationManager.hardwareCapabilities.value.supportsHDR10) {
            DecoderMode.HARDWARE_PLUS
        } else {
            DecoderMode.HARDWARE
        }
        
        // Initialize player with hardware acceleration
        exoPlayer = exoPlayerManager.initializePlayer(
            decoderMode = decoderMode,
            multiCoreDecoding = true,
            adaptiveStreaming = true
        )
        
        // Initialize advanced components
        exoPlayer?.let { player ->
            val trackSelector = exoPlayerManager.trackSelector
            if (trackSelector != null) {
                multiAudioTrackManager.initialize(player, trackSelector)
            }
            advancedPlayerControls.initialize(player)
            hardwareAccelerationManager.enableHardwareAcceleration(player)
        }
    }
    
    fun loadVideo(uri: Uri, title: String? = null) {
        currentVideoUri = uri
        exoPlayerManager.setMediaItem(uri, title)
        exoPlayerManager.prepare()
        
        // Refresh audio tracks after loading
        viewModelScope.launch {
            multiAudioTrackManager.refreshAudioTracks()
        }
    }
    
    fun play() {
        exoPlayerManager.play()
    }
    
    fun pause() {
        exoPlayerManager.pause()
    }
    
    fun togglePlayPause() {
        advancedPlayerControls.togglePlayPause()
    }
    
    fun seekTo(positionMs: Long) {
        exoPlayerManager.seekTo(positionMs)
    }
    
    fun seekToPercentage(percentage: Float) {
        advancedPlayerControls.seekToPercentage(percentage)
    }
    
    fun skipForward(seconds: Int = 10) {
        advancedPlayerControls.skipForward(seconds)
    }
    
    fun skipBackward(seconds: Int = 10) {
        advancedPlayerControls.skipBackward(seconds)
    }
    
    // Frame-by-frame navigation
    fun enterFrameByFrameMode() {
        advancedPlayerControls.enterFrameByFrameMode()
    }
    
    fun exitFrameByFrameMode() {
        advancedPlayerControls.exitFrameByFrameMode()
    }
    
    fun seekToNextFrame() {
        advancedPlayerControls.seekToNextFrame()
    }
    
    fun seekToPreviousFrame() {
        advancedPlayerControls.seekToPreviousFrame()
    }
    
    fun seekFrames(frameCount: Int) {
        advancedPlayerControls.seekFrames(frameCount)
    }
    
    fun startContinuousFrameSeek(forward: Boolean) {
        advancedPlayerControls.startContinuousFrameSeek(forward)
    }
    
    fun stopContinuousFrameSeek() {
        advancedPlayerControls.stopContinuousFrameSeek()
    }
    
    // Zoom and pan controls
    fun setupZoomPan(videoView: View) {
        val gestureListener = advancedPlayerControls.createZoomPanGestureListener(videoView)
        videoView.setOnTouchListener { _, event ->
            gestureListener.onTouchEvent(event)
        }
    }
    
    fun resetZoomPan() {
        advancedPlayerControls.resetZoomPan()
    }
    
    fun setZoom(scale: Float) {
        advancedPlayerControls.setZoom(scale)
    }
    
    // Audio track management
    fun selectAudioTrack(track: com.astralstream.player.audio.AudioTrackInfo) {
        multiAudioTrackManager.selectAudioTrack(track)
    }
    
    fun selectAudioTrackByLanguage(language: String) {
        multiAudioTrackManager.selectAudioTrackByLanguage(language)
    }
    
    fun cycleAudioTrack() {
        multiAudioTrackManager.cycleAudioTrack()
    }
    
    fun enableDualAudio(
        primary: com.astralstream.player.audio.AudioTrackInfo,
        secondary: com.astralstream.player.audio.AudioTrackInfo
    ) {
        multiAudioTrackManager.enableDualAudio(primary, secondary)
    }
    
    fun disableDualAudio() {
        multiAudioTrackManager.disableDualAudio()
    }
    
    // Video filters
    fun applyFilterPreset(preset: FilterPreset) {
        videoFiltersManager.applyPreset(preset)
    }
    
    fun adjustBrightness(value: Float) {
        videoFiltersManager.applyBrightnessFilter(value)
    }
    
    fun adjustContrast(value: Float) {
        videoFiltersManager.applyContrastFilter(value)
    }
    
    fun adjustSaturation(value: Float) {
        videoFiltersManager.applySaturationFilter(value)
    }
    
    fun adjustSharpness(value: Float) {
        videoFiltersManager.applySharpnessFilter(value)
    }
    
    fun toggleGrayscale() {
        val current = _uiState.value.filterSettings.grayscale
        videoFiltersManager.applyGrayscaleFilter(!current)
    }
    
    fun toggleSepia() {
        val current = _uiState.value.filterSettings.sepia
        videoFiltersManager.applySepiaFilter(!current)
    }
    
    fun resetFilters() {
        videoFiltersManager.resetFilters()
    }
    
    // Decoder switching
    fun switchDecoderMode(mode: DecoderMode) {
        exoPlayerManager.switchDecoderMode(mode)
    }
    
    fun autoSelectDecoder() {
        val format = exoPlayerManager.getVideoFormat()
        format?.let {
            val recommendation = hardwareAccelerationManager.getRecommendedDecoderMode(it)
            val mode = when (recommendation) {
                com.astralstream.player.hardware.DecoderRecommendation.HARDWARE -> DecoderMode.HARDWARE
                com.astralstream.player.hardware.DecoderRecommendation.HARDWARE_WITH_FALLBACK -> DecoderMode.HARDWARE_PLUS
                com.astralstream.player.hardware.DecoderRecommendation.SOFTWARE -> DecoderMode.SOFTWARE
            }
            switchDecoderMode(mode)
        }
    }
    
    // Loop controls
    fun setLoopMode(mode: LoopMode) {
        advancedPlayerControls.setLoopMode(mode)
    }
    
    fun markABLoopStart() {
        advancedPlayerControls.markLoopStart()
    }
    
    fun markABLoopEnd() {
        advancedPlayerControls.markLoopEnd()
    }
    
    fun clearABLoop() {
        advancedPlayerControls.clearABLoop()
    }
    
    // Playback speed
    fun setPlaybackSpeed(speed: Float) {
        exoPlayerManager.setPlaybackSpeed(speed)
    }
    
    // Volume control
    fun setVolume(volume: Float) {
        exoPlayerManager.setVolume(volume)
    }
    
    // Surface management
    fun setSurface(surface: Surface?) {
        exoPlayer?.setVideoSurface(surface)
    }
    
    // Get current playback info
    fun getCurrentPosition(): Long = exoPlayerManager.getCurrentPosition()
    fun getDuration(): Long = exoPlayerManager.getDuration()
    fun getBufferedPosition(): Long = exoPlayerManager.getBufferedPosition()
    
    // Snapshot management
    fun captureSnapshot() = advancedPlayerControls.captureSnapshot()
    fun restoreSnapshot(snapshot: com.astralstream.player.controls.Snapshot) = 
        advancedPlayerControls.restoreSnapshot(snapshot)
    
    override fun onCleared() {
        super.onCleared()
        advancedPlayerControls.release()
        multiAudioTrackManager.release()
        exoPlayerManager.release()
    }
}

data class EnhancedPlayerUiState(
    // Basic playback state
    val isInitialized: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isEnded: Boolean = false,
    val error: String? = null,
    
    // Decoder and hardware
    val decoderMode: DecoderMode = DecoderMode.HARDWARE,
    val codecSupport: com.astralstream.player.hardware.CodecSupport = com.astralstream.player.hardware.CodecSupport(),
    val hardwareCapabilities: com.astralstream.player.hardware.HardwareCapabilities = com.astralstream.player.hardware.HardwareCapabilities(),
    
    // Audio tracks
    val audioTracks: List<com.astralstream.player.audio.AudioTrackInfo> = emptyList(),
    val currentAudioTrack: com.astralstream.player.audio.AudioTrackInfo? = null,
    val hasMultipleAudioTracks: Boolean = false,
    
    // Video filters
    val activeFilters: List<com.astralstream.player.video.VideoFilter> = emptyList(),
    val filterSettings: com.astralstream.player.video.FilterSettings = com.astralstream.player.video.FilterSettings(),
    
    // Frame navigation
    val frameNavigationState: com.astralstream.player.controls.FrameNavigationState = com.astralstream.player.controls.FrameNavigationState(),
    
    // Zoom and pan
    val zoomPanState: com.astralstream.player.controls.ZoomPanState = com.astralstream.player.controls.ZoomPanState(),
    
    // Loop controls
    val loopMode: LoopMode = LoopMode.OFF,
    val abLoopStart: Long? = null,
    val abLoopEnd: Long? = null,
    val isABLoopEnabled: Boolean = false
)