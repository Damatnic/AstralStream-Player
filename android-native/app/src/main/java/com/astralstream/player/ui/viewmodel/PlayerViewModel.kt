package com.astralstream.player.ui.viewmodel

import android.app.PictureInPictureParams
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Advanced Player ViewModel with comprehensive playback controls
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _playerState = MutableStateFlow(PlayerUiState())
    val playerState: StateFlow<PlayerUiState> = _playerState.asStateFlow()

    private var exoPlayer: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var sleepTimerJob: Job? = null
    private var positionUpdateJob: Job? = null
    private var repeatAPosition: Long = -1L
    private var repeatBPosition: Long = -1L

    init {
        initializePlayer()
    }

    private fun initializePlayer() {
        trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSizeSd()
                    .setPreferredAudioLanguage("en")
                    .setPreferredTextLanguage("en")
            )
        }
        
        exoPlayer = ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector!!)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .apply {
                addListener(playerListener)
            }
        
        startPositionUpdates()
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _playerState.value = _playerState.value.copy(
                isLoading = playbackState == Player.STATE_BUFFERING,
                isEnded = playbackState == Player.STATE_ENDED
            )
            
            if (playbackState == Player.STATE_ENDED && isABRepeatEnabled()) {
                seekTo(repeatAPosition)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
        }

        override fun onPlayerError(error: PlaybackException) {
            _playerState.value = _playerState.value.copy(
                error = error.message ?: "Playback error occurred"
            )
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            _playerState.value = _playerState.value.copy(
                videoSize = Pair(videoSize.width, videoSize.height),
                aspectRatio = if (videoSize.height != 0) 
                    videoSize.width.toFloat() / videoSize.height.toFloat() 
                else 16f / 9f
            )
        }
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            while (true) {
                exoPlayer?.let { player ->
                    _playerState.value = _playerState.value.copy(
                        currentPosition = player.currentPosition,
                        bufferedPosition = player.bufferedPosition,
                        duration = if (player.duration != C.TIME_UNSET) player.duration else 0L
                    )
                }
                delay(100) // Update every 100ms for smooth progress
            }
        }
    }

    // Basic playback controls
    fun playMedia(uri: Uri, title: String? = null) {
        viewModelScope.launch {
            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(title)
                        .build()
                )
                .build()

            exoPlayer?.apply {
                setMediaItem(mediaItem)
                prepare()
                play()
            }

            _playerState.value = _playerState.value.copy(
                currentMedia = uri,
                isPlaying = true,
                mediaTitle = title,
                error = null
            )
        }
    }

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun togglePlayPause() {
        if (_playerState.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs.coerceIn(0, _playerState.value.duration))
    }

    fun seekForward(incrementMs: Long = 10000) {
        val newPosition = _playerState.value.currentPosition + incrementMs
        seekTo(newPosition)
    }

    fun seekBackward(incrementMs: Long = 10000) {
        val newPosition = _playerState.value.currentPosition - incrementMs
        seekTo(newPosition)
    }

    // Advanced playback controls
    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed.coerceIn(0.25f, 4f))
        _playerState.value = _playerState.value.copy(playbackSpeed = speed)
    }

    fun frameStep(forward: Boolean = true) {
        exoPlayer?.let { player ->
            val currentPos = player.currentPosition
            val frameTime = 1000L / 30 // Assume 30fps
            val newPos = if (forward) currentPos + frameTime else currentPos - frameTime
            seekTo(newPos.coerceAtLeast(0))
        }
    }

    // A-B Repeat functionality
    fun setRepeatAPoint() {
        repeatAPosition = _playerState.value.currentPosition
        _playerState.value = _playerState.value.copy(
            repeatAPosition = repeatAPosition,
            isABRepeatSet = isABRepeatEnabled()
        )
    }

    fun setRepeatBPoint() {
        repeatBPosition = _playerState.value.currentPosition
        _playerState.value = _playerState.value.copy(
            repeatBPosition = repeatBPosition,
            isABRepeatSet = isABRepeatEnabled()
        )
    }

    fun clearABRepeat() {
        repeatAPosition = -1L
        repeatBPosition = -1L
        _playerState.value = _playerState.value.copy(
            repeatAPosition = -1L,
            repeatBPosition = -1L,
            isABRepeatSet = false
        )
    }

    private fun isABRepeatEnabled(): Boolean {
        return repeatAPosition >= 0 && repeatBPosition > repeatAPosition
    }

    // Volume and brightness controls
    fun setVolume(volume: Float) {
        exoPlayer?.volume = volume.coerceIn(0f, 1f)
        _playerState.value = _playerState.value.copy(volume = volume)
    }

    fun setBrightness(brightness: Float) {
        _playerState.value = _playerState.value.copy(brightness = brightness.coerceIn(0f, 1f))
    }

    // Screen mode controls
    fun setScreenMode(mode: ScreenMode) {
        _playerState.value = _playerState.value.copy(screenMode = mode)
    }

    fun toggleScreenMode() {
        val currentMode = _playerState.value.screenMode
        val nextMode = when (currentMode) {
            ScreenMode.FIT_SCREEN -> ScreenMode.FILL_SCREEN
            ScreenMode.FILL_SCREEN -> ScreenMode.STRETCH
            ScreenMode.STRETCH -> ScreenMode.ZOOM
            ScreenMode.ZOOM -> ScreenMode.FIT_SCREEN
        }
        setScreenMode(nextMode)
    }

    // Sleep timer
    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes > 0) {
            sleepTimerJob = viewModelScope.launch {
                delay(minutes * 60 * 1000L)
                pause()
                _playerState.value = _playerState.value.copy(
                    sleepTimerMinutes = 0,
                    isSleepTimerActive = false
                )
            }
            _playerState.value = _playerState.value.copy(
                sleepTimerMinutes = minutes,
                isSleepTimerActive = true
            )
        } else {
            _playerState.value = _playerState.value.copy(
                sleepTimerMinutes = 0,
                isSleepTimerActive = false
            )
        }
    }

    fun cancelSleepTimer() {
        setSleepTimer(0)
    }

    // Kids lock
    fun toggleKidsLock() {
        _playerState.value = _playerState.value.copy(
            isKidsLockEnabled = !_playerState.value.isKidsLockEnabled
        )
    }

    // Screenshot capture
    fun captureScreenshot(): Bitmap? {
        return try {
            exoPlayer?.videoFormat?.let { format ->
                val width = format.width
                val height = format.height
                if (width > 0 && height > 0) {
                    // Create a bitmap for the screenshot
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    // Note: Actual frame capture would require VideoProcessor or TextureView
                    // This is a placeholder that returns a black bitmap
                    // In production, you'd use VideoProcessor.getVideoFrameBitmap() or similar
                    bitmap
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Subtitle controls
    fun toggleSubtitles() {
        trackSelector?.let { selector ->
            val newParams = selector.buildUponParameters()
                .setRendererDisabled(C.TRACK_TYPE_TEXT, _playerState.value.areSubtitlesEnabled)
                .build()
            selector.setParameters(newParams)
        }
        _playerState.value = _playerState.value.copy(
            areSubtitlesEnabled = !_playerState.value.areSubtitlesEnabled
        )
    }
    
    fun getAvailableSubtitleTracks(): List<String> {
        val tracks = mutableListOf<String>()
        val currentTracks = exoPlayer?.currentTracks ?: return tracks
        
        for (group in currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val language = format.language ?: "Track ${i + 1}"
                    tracks.add(language)
                }
            }
        }
        return tracks
    }
    
    fun getAvailableAudioTracks(): List<String> {
        val tracks = mutableListOf<String>()
        val currentTracks = exoPlayer?.currentTracks ?: return tracks
        
        for (group in currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val language = format.language ?: "Unknown"
                    val channels = format.channelCount
                    tracks.add("$language (${channels}ch)")
                }
            }
        }
        return tracks
    }

    fun setSubtitleDelay(delayMs: Long) {
        _playerState.value = _playerState.value.copy(subtitleDelay = delayMs)
    }

    fun selectSubtitleTrack(trackIndex: Int) {
        trackSelector?.let { selector ->
            val params = selector.buildUponParameters()
                .setPreferredTextLanguage("en")
                .setRendererDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
            selector.setParameters(params)
        }
        _playerState.value = _playerState.value.copy(selectedSubtitleTrack = trackIndex)
    }

    // Audio track controls
    fun selectAudioTrack(trackIndex: Int) {
        trackSelector?.let { selector ->
            val params = selector.buildUponParameters()
                .setPreferredAudioLanguage("en")
                .setRendererDisabled(C.TRACK_TYPE_AUDIO, false)
                .build()
            selector.setParameters(params)
        }
        _playerState.value = _playerState.value.copy(selectedAudioTrack = trackIndex)
    }

    // Video filters
    fun setVideoBrightness(value: Float) {
        _playerState.value = _playerState.value.copy(
            videoFilters = _playerState.value.videoFilters.copy(brightness = value.coerceIn(-1f, 1f))
        )
    }

    fun setContrast(value: Float) {
        _playerState.value = _playerState.value.copy(
            videoFilters = _playerState.value.videoFilters.copy(contrast = value.coerceIn(0f, 2f))
        )
    }

    fun setSaturation(value: Float) {
        _playerState.value = _playerState.value.copy(
            videoFilters = _playerState.value.videoFilters.copy(saturation = value.coerceIn(0f, 2f))
        )
    }

    fun setHue(value: Float) {
        _playerState.value = _playerState.value.copy(
            videoFilters = _playerState.value.videoFilters.copy(hue = value.coerceIn(-180f, 180f))
        )
    }

    fun resetVideoFilters() {
        _playerState.value = _playerState.value.copy(
            videoFilters = VideoFilters()
        )
    }

    // Control UI visibility
    fun showControls() {
        _playerState.value = _playerState.value.copy(
            areControlsVisible = true,
            controlsHideTime = System.currentTimeMillis() + 5000 // Hide after 5 seconds
        )
    }

    fun hideControls() {
        _playerState.value = _playerState.value.copy(areControlsVisible = false)
    }

    fun toggleControls() {
        if (_playerState.value.areControlsVisible) {
            hideControls()
        } else {
            showControls()
        }
    }

    // Picture-in-Picture
    fun setPictureInPictureMode(isInPipMode: Boolean) {
        _playerState.value = _playerState.value.copy(isInPictureInPictureMode = isInPipMode)
    }

    fun shouldEnterPipMode(): Boolean {
        return _playerState.value.isPlaying && !_playerState.value.isInPictureInPictureMode
    }

    fun isPlaying(): Boolean {
        return _playerState.value.isPlaying
    }

    fun getPictureInPictureParams(): PictureInPictureParams? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val aspectRatio = _playerState.value.videoSize?.let { (width, height) ->
                android.util.Rational(width, height)
            } ?: android.util.Rational(16, 9)
            
            PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
        } else {
            null
        }
    }

    // Gesture handling
    fun handleVolumeGesture(deltaY: Float, screenHeight: Int) {
        val volumeChange = -deltaY / screenHeight
        val newVolume = (_playerState.value.volume + volumeChange).coerceIn(0f, 1f)
        setVolume(newVolume)
    }

    fun handleBrightnessGesture(deltaY: Float, screenHeight: Int) {
        val brightnessChange = -deltaY / screenHeight
        val newBrightness = (_playerState.value.brightness + brightnessChange).coerceIn(0f, 1f)
        setBrightness(newBrightness)
    }

    fun handleSeekGesture(deltaX: Float, screenWidth: Int) {
        val seekChange = (deltaX / screenWidth) * _playerState.value.duration
        val newPosition = _playerState.value.currentPosition + seekChange.toLong()
        seekTo(newPosition)
    }

    fun handleZoomGesture(scaleFactor: Float) {
        val newZoom = (_playerState.value.zoomLevel * scaleFactor).coerceIn(0.5f, 3f)
        _playerState.value = _playerState.value.copy(zoomLevel = newZoom)
    }

    fun getAvailableVideoQualities(): List<String> {
        val qualities = mutableListOf<String>()
        val currentTracks = exoPlayer?.currentTracks ?: return qualities
        
        for (group in currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_VIDEO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val quality = when {
                        format.height >= 2160 -> "4K (${format.height}p)"
                        format.height >= 1080 -> "FHD (${format.height}p)"
                        format.height >= 720 -> "HD (${format.height}p)"
                        format.height >= 480 -> "SD (${format.height}p)"
                        else -> "${format.height}p"
                    }
                    if (!qualities.contains(quality)) {
                        qualities.add(quality)
                    }
                }
            }
        }
        return qualities.sortedByDescending { 
            it.substringAfter("(").substringBefore("p").toIntOrNull() ?: 0 
        }
    }
    
    fun setVideoQuality(height: Int) {
        trackSelector?.let { selector ->
            val params = selector.buildUponParameters()
                .setMaxVideoSize(Int.MAX_VALUE, height)
                .build()
            selector.setParameters(params)
        }
    }
    
    fun releasePlayer() {
        positionUpdateJob?.cancel()
        sleepTimerJob?.cancel()
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
        trackSelector = null
        _playerState.value = PlayerUiState()
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }
}

enum class ScreenMode {
    FIT_SCREEN, FILL_SCREEN, STRETCH, ZOOM
}

data class VideoFilters(
    val brightness: Float = 0f, // -1 to 1
    val contrast: Float = 1f,   // 0 to 2
    val saturation: Float = 1f, // 0 to 2
    val hue: Float = 0f         // -180 to 180
)

data class PlayerUiState(
    // Basic playback state
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isEnded: Boolean = false,
    val currentMedia: Uri? = null,
    val mediaTitle: String? = null,
    val duration: Long = 0L,
    val currentPosition: Long = 0L,
    val bufferedPosition: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    
    // Video properties
    val videoSize: Pair<Int, Int>? = null,
    val aspectRatio: Float = 16f / 9f,
    val screenMode: ScreenMode = ScreenMode.FIT_SCREEN,
    val zoomLevel: Float = 1f,
    val videoFilters: VideoFilters = VideoFilters(),
    
    // Audio properties
    val volume: Float = 1f,
    val selectedAudioTrack: Int = -1,
    
    // Subtitle properties
    val areSubtitlesEnabled: Boolean = true,
    val subtitleDelay: Long = 0L,
    val selectedSubtitleTrack: Int = -1,
    
    // A-B Repeat
    val repeatAPosition: Long = -1L,
    val repeatBPosition: Long = -1L,
    val isABRepeatSet: Boolean = false,
    
    // UI state
    val areControlsVisible: Boolean = true,
    val controlsHideTime: Long = 0L,
    val brightness: Float = 0.5f,
    
    // Advanced features
    val sleepTimerMinutes: Int = 0,
    val isSleepTimerActive: Boolean = false,
    val isKidsLockEnabled: Boolean = false,
    
    // Picture-in-Picture
    val isInPictureInPictureMode: Boolean = false,
    
    // Error state
    val error: String? = null
)