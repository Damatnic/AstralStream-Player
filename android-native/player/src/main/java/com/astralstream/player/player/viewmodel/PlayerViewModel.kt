package com.astralstream.player.player.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()
    
    private var exoPlayer: ExoPlayer? = null
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _playerState.value = _playerState.value.copy(isBuffering = true)
                }
                Player.STATE_READY -> {
                    _playerState.value = _playerState.value.copy(
                        isBuffering = false,
                        duration = exoPlayer?.duration ?: 0L
                    )
                }
                Player.STATE_ENDED -> {
                    _playerState.value = _playerState.value.copy(isPlaying = false)
                    playNext()
                }
                Player.STATE_IDLE -> {
                    _playerState.value = _playerState.value.copy(isBuffering = false)
                }
            }
        }
        
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
        }
    }
    
    fun setExoPlayer(player: ExoPlayer) {
        exoPlayer?.removeListener(playerListener)
        exoPlayer = player
        player.addListener(playerListener)
        
        // If we have a pending media to play, load it now
        _playerState.value.currentMedia?.let { mediaPath ->
            loadMedia(mediaPath)
        }
    }
    
    fun pausePlayback() {
        exoPlayer?.pause()
        _playerState.value = _playerState.value.copy(isPlaying = false)
    }
    
    fun resumePlayback() {
        exoPlayer?.play()
        _playerState.value = _playerState.value.copy(isPlaying = true)
    }
    
    fun setMedia(mediaPath: String) {
        _playerState.value = _playerState.value.copy(currentMedia = mediaPath)
        loadMedia(mediaPath)
        // Try to restore playback position
        restorePlaybackPosition(mediaPath)
    }
    
    private fun loadMedia(mediaPath: String) {
        exoPlayer?.let { player ->
            val mediaItem = if (mediaPath.startsWith("http") || mediaPath.startsWith("https")) {
                MediaItem.fromUri(Uri.parse(mediaPath))
            } else {
                MediaItem.fromUri(Uri.parse("file://$mediaPath"))
            }
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
        }
    }
    
    fun shouldEnterPipMode(): Boolean {
        return _playerState.value.isPlaying
    }
    
    fun playMedia(uri: String) {
        setMedia(uri)
        resumePlayback()
    }
    
    fun releasePlayer() {
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
        _playerState.value = PlayerState()
    }
    
    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
        _playerState.value = _playerState.value.copy(currentPosition = position)
    }
    
    fun seekForward(seconds: Int = 10) {
        val newPosition = (exoPlayer?.currentPosition ?: 0L) + (seconds * 1000)
        seekTo(newPosition.coerceAtMost(exoPlayer?.duration ?: 0L))
    }
    
    fun seekBackward(seconds: Int = 10) {
        val newPosition = (exoPlayer?.currentPosition ?: 0L) - (seconds * 1000)
        seekTo(newPosition.coerceAtLeast(0))
    }
    
    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
        _playerState.value = _playerState.value.copy(playbackSpeed = speed)
    }
    
    fun setLongPressSeekSpeed(speed: Float) {
        _playerState.value = _playerState.value.copy(longPressSeekSpeed = speed)
    }
    
    fun startLongPressSeek(forward: Boolean) {
        // Start seeking with the configured speed
        val speed = _playerState.value.longPressSeekSpeed
        exoPlayer?.let { player ->
            // Store original speed
            val originalSpeed = player.playbackParameters.speed
            _playerState.value = _playerState.value.copy(
                isLongPressSeeking = true,
                originalPlaybackSpeed = originalSpeed
            )
            // Set fast forward/rewind speed
            player.setPlaybackSpeed(if (forward) speed else -speed)
        }
    }
    
    fun stopLongPressSeek() {
        // Stop seeking and resume normal playback
        if (_playerState.value.isLongPressSeeking) {
            exoPlayer?.let { player ->
                // Restore original playback speed
                player.setPlaybackSpeed(_playerState.value.originalPlaybackSpeed)
                // Resume playing
                player.play()
            }
            _playerState.value = _playerState.value.copy(
                isLongPressSeeking = false
            )
        }
    }
    
    fun toggleFullscreen() {
        _playerState.value = _playerState.value.copy(
            isFullscreen = !_playerState.value.isFullscreen
        )
    }
    
    override fun onCleared() {
        super.onCleared()
        savePlaybackPosition()
        releasePlayer()
    }
    
    fun setPictureInPictureMode(enabled: Boolean) {
        // This would typically update UI state for PiP mode
    }
    
    val isPlaying: Boolean
        get() = _playerState.value.isPlaying
        
    val videoSize: Pair<Int, Int>
        get() = Pair(1920, 1080) // Default HD size, should be updated from actual video
    
    fun getPictureInPictureParams(): Any? {
        // Return null for now, would normally return PictureInPictureParams
        return null
    }
    
    fun playNext() {
        val playlist = _playerState.value.playlist
        if (playlist.isNotEmpty()) {
            val nextIndex = (_playerState.value.currentIndex + 1) % playlist.size
            _playerState.value = _playerState.value.copy(currentIndex = nextIndex)
            playlist.getOrNull(nextIndex)?.let { nextMedia ->
                setMedia(nextMedia)
            }
        }
    }
    
    fun playPrevious() {
        val playlist = _playerState.value.playlist
        if (playlist.isNotEmpty()) {
            val prevIndex = if (_playerState.value.currentIndex > 0) {
                _playerState.value.currentIndex - 1
            } else {
                playlist.size - 1
            }
            _playerState.value = _playerState.value.copy(currentIndex = prevIndex)
            playlist.getOrNull(prevIndex)?.let { prevMedia ->
                setMedia(prevMedia)
            }
        }
    }
    
    fun setPlaylist(playlist: List<String>, startIndex: Int = 0) {
        _playerState.value = _playerState.value.copy(
            playlist = playlist,
            currentIndex = startIndex
        )
        playlist.getOrNull(startIndex)?.let { media ->
            setMedia(media)
        }
    }
    
    fun getCurrentPosition(): Long {
        return exoPlayer?.currentPosition ?: 0L
    }
    
    // Save playback position for resume
    fun savePlaybackPosition() {
        _playerState.value.currentMedia?.let { media ->
            val position = getCurrentPosition()
            if (position > 5000) { // Only save if more than 5 seconds
                context.getSharedPreferences("playback_positions", Context.MODE_PRIVATE)
                    .edit()
                    .putLong(media.hashCode().toString(), position)
                    .apply()
            }
        }
    }
    
    private fun restorePlaybackPosition(mediaPath: String) {
        val savedPosition = context.getSharedPreferences("playback_positions", Context.MODE_PRIVATE)
            .getLong(mediaPath.hashCode().toString(), 0L)
        if (savedPosition > 0) {
            exoPlayer?.seekTo(savedPosition)
        }
    }
    
    // Aspect ratio control
    private val aspectRatios = listOf(
        AspectRatio("Fit", 0f), // Fit to screen
        AspectRatio("16:9", 16f / 9f),
        AspectRatio("4:3", 4f / 3f),
        AspectRatio("21:9", 21f / 9f),
        AspectRatio("1:1", 1f),
        AspectRatio("Stretch", -1f) // Fill screen
    )
    private var currentAspectRatioIndex = 0
    
    fun getAspectRatio(): AspectRatio {
        return aspectRatios[currentAspectRatioIndex]
    }
    
    fun cycleAspectRatio(): AspectRatio {
        currentAspectRatioIndex = (currentAspectRatioIndex + 1) % aspectRatios.size
        val ratio = aspectRatios[currentAspectRatioIndex]
        _playerState.value = _playerState.value.copy(aspectRatio = ratio)
        return ratio
    }
    
    fun setAspectRatio(index: Int) {
        if (index in aspectRatios.indices) {
            currentAspectRatioIndex = index
            _playerState.value = _playerState.value.copy(aspectRatio = aspectRatios[index])
        }
    }
    
    // Gesture control methods for MX Player style controls
    private var currentVolume = 0.5f
    private var currentBrightness = 0.5f
    
    fun getVolume(): Float {
        return currentVolume
    }
    
    fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 1f)
        // Apply volume to audio manager
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        val newVolume = (currentVolume * maxVolume).toInt()
        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVolume, 0)
    }
    
    fun getBrightness(): Float {
        return currentBrightness
    }
    
    fun setBrightness(brightness: Float) {
        currentBrightness = brightness.coerceIn(0f, 1f)
        // This needs to be handled in the Activity as it requires window access
        // The activity should observe this value and update window brightness
    }
}

data class AspectRatio(
    val label: String,
    val value: Float
)

data class PlayerState(
    val isPlaying: Boolean = false,
    val currentMedia: String? = null,
    val duration: Long = 0L,
    val currentPosition: Long = 0L,
    val isBuffering: Boolean = false,
    val isFullscreen: Boolean = false,
    val playlist: List<String> = emptyList(),
    val currentIndex: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val volume: Float = 1.0f,
    val brightness: Float = 0.5f,
    val showControls: Boolean = true,
    val isLocked: Boolean = false,
    val aspectRatio: AspectRatio = AspectRatio("Fit", 0f),
    val audioDelay: Long = 0L,
    val subtitleDelay: Long = 0L,
    val error: String? = null,
    val longPressSeekSpeed: Float = 2.0f, // MX Player default: 2x speed for long press
    val isLongPressSeeking: Boolean = false,
    val originalPlaybackSpeed: Float = 1.0f
)