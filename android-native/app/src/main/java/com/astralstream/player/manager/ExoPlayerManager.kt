package com.astralstream.player.manager

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.TrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Advanced ExoPlayer Manager with hardware/software decoder switching
 * and performance optimization features
 */
@UnstableApi
@Singleton
class ExoPlayerManager @Inject constructor(
    private val context: Context
) {
    
    private var exoPlayer: ExoPlayer? = null
    var trackSelector: DefaultTrackSelector? = null
        private set
    private var cache: SimpleCache? = null
    private var renderersFactory: RenderersFactory? = null
    
    private val _playerState = MutableStateFlow(ExoPlayerState())
    val playerState: StateFlow<ExoPlayerState> = _playerState.asStateFlow()
    
    private var playerListener: Player.Listener? = null
    
    companion object {
        private const val CACHE_SIZE = 100 * 1024 * 1024L // 100MB
        private const val USER_AGENT = "AstralStream/1.0"
    }
    
    init {
        initializeCache()
    }
    
    private fun initializeCache() {
        val cacheDir = File(context.cacheDir, "media_cache")
        val databaseProvider = StandaloneDatabaseProvider(context)
        cache = SimpleCache(
            cacheDir,
            LeastRecentlyUsedCacheEvictor(CACHE_SIZE),
            databaseProvider
        )
    }
    
    fun initializePlayer(
        decoderMode: DecoderMode = DecoderMode.HARDWARE,
        multiCoreDecoding: Boolean = true,
        adaptiveStreaming: Boolean = true
    ): ExoPlayer {
        releasePlayer()
        
        // Configure renderers factory based on decoder mode
        renderersFactory = createRenderersFactory(decoderMode, multiCoreDecoding)
        
        // Configure track selector for adaptive streaming
        trackSelector = createTrackSelector(adaptiveStreaming)
        
        // Configure load control for buffering optimization
        val loadControl = createLoadControl()
        
        // Configure data source factory with caching
        val dataSourceFactory = createDataSourceFactory()
        
        // Configure media source factory
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        
        // Build ExoPlayer
        exoPlayer = ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory!!)
            .setTrackSelector(trackSelector!!)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
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
        
        // Set player listener
        playerListener = createPlayerListener()
        exoPlayer?.addListener(playerListener!!)
        
        // Update state
        _playerState.value = _playerState.value.copy(
            isInitialized = true,
            decoderMode = decoderMode,
            multiCoreDecoding = multiCoreDecoding,
            adaptiveStreaming = adaptiveStreaming
        )
        
        return exoPlayer!!
    }
    
    private fun createRenderersFactory(
        decoderMode: DecoderMode,
        multiCoreDecoding: Boolean
    ): RenderersFactory {
        val extensionRendererMode = when (decoderMode) {
            DecoderMode.HARDWARE -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
            DecoderMode.HARDWARE_PLUS -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            DecoderMode.SOFTWARE -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
        }
        
        return DefaultRenderersFactory(context)
            .setExtensionRendererMode(extensionRendererMode)
            .setEnableDecoderFallback(true)
            .apply {
                if (multiCoreDecoding) {
                    setEnableAudioFloatOutput(true)
                }
            }
    }
    
    private fun createTrackSelector(adaptiveStreaming: Boolean): DefaultTrackSelector {
        val adaptiveTrackSelectionFactory = AdaptiveTrackSelection.Factory()
        
        return DefaultTrackSelector(context, adaptiveTrackSelectionFactory).apply {
            if (adaptiveStreaming) {
                setParameters(
                    buildUponParameters()
                        .setForceHighestSupportedBitrate(false)
                        .setAllowVideoMixedMimeTypeAdaptiveness(true)
                        .setAllowAudioMixedMimeTypeAdaptiveness(true)
                        .setMaxVideoBitrate(Int.MAX_VALUE)
                        .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                )
            }
        }
    }
    
    private fun createLoadControl(): LoadControl {
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .setTargetBufferBytes(C.LENGTH_UNSET)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(30000, true) // 30 second back buffer
            .build()
    }
    
    private fun createDataSourceFactory(): DataSource.Factory {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setConnectTimeoutMs(30000)
            .setReadTimeoutMs(30000)
            .setAllowCrossProtocolRedirects(true)
        
        val upstreamFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        
        return cache?.let {
            CacheDataSource.Factory()
                .setCache(it)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } ?: upstreamFactory
    }
    
    private fun createPlayerListener(): Player.Listener {
        return object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                _playerState.value = _playerState.value.copy(
                    playbackState = when (playbackState) {
                        Player.STATE_IDLE -> PlaybackState.IDLE
                        Player.STATE_BUFFERING -> PlaybackState.BUFFERING
                        Player.STATE_READY -> PlaybackState.READY
                        Player.STATE_ENDED -> PlaybackState.ENDED
                        else -> PlaybackState.IDLE
                    }
                )
            }
            
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
            }
            
            override fun onPlayerError(error: PlaybackException) {
                _playerState.value = _playerState.value.copy(
                    error = error.message ?: "Playback error occurred",
                    playbackState = PlaybackState.ERROR
                )
                
                // Try fallback decoder on hardware decoder error
                if (_playerState.value.decoderMode == DecoderMode.HARDWARE && 
                    error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED) {
                    switchDecoderMode(DecoderMode.SOFTWARE)
                }
            }
            
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                _playerState.value = _playerState.value.copy(
                    videoSize = Pair(videoSize.width, videoSize.height),
                    aspectRatio = if (videoSize.height != 0) {
                        videoSize.width.toFloat() / videoSize.height.toFloat()
                    } else {
                        16f / 9f
                    }
                )
            }
        }
    }
    
    fun switchDecoderMode(newMode: DecoderMode) {
        val currentPosition = exoPlayer?.currentPosition ?: 0L
        val currentMedia = exoPlayer?.currentMediaItem
        val wasPlaying = exoPlayer?.isPlaying ?: false
        
        // Reinitialize player with new decoder mode
        initializePlayer(
            decoderMode = newMode,
            multiCoreDecoding = _playerState.value.multiCoreDecoding,
            adaptiveStreaming = _playerState.value.adaptiveStreaming
        )
        
        // Restore playback state
        currentMedia?.let { mediaItem ->
            exoPlayer?.apply {
                setMediaItem(mediaItem)
                prepare()
                seekTo(currentPosition)
                if (wasPlaying) play()
            }
        }
    }
    
    fun setMediaItem(uri: Uri, title: String? = null) {
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .build()
            )
            .build()
        
        exoPlayer?.setMediaItem(mediaItem)
    }
    
    fun prepare() {
        exoPlayer?.prepare()
    }
    
    fun play() {
        exoPlayer?.play()
    }
    
    fun pause() {
        exoPlayer?.pause()
    }
    
    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }
    
    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed.coerceIn(0.25f, 4f))
    }
    
    fun setVolume(volume: Float) {
        exoPlayer?.volume = volume.coerceIn(0f, 1f)
    }
    
    fun getCurrentPosition(): Long {
        return exoPlayer?.currentPosition ?: 0L
    }
    
    fun getDuration(): Long {
        return exoPlayer?.duration?.takeIf { it != C.TIME_UNSET } ?: 0L
    }
    
    fun getBufferedPosition(): Long {
        return exoPlayer?.bufferedPosition ?: 0L
    }
    
    fun isPlaying(): Boolean {
        return exoPlayer?.isPlaying ?: false
    }
    
    fun getVideoFormat(): androidx.media3.common.Format? {
        return trackSelector?.currentMappedTrackInfo?.let { mappedTrackInfo ->
            val rendererIndex = mappedTrackInfo.getRendererType(0)
            if (rendererIndex == C.TRACK_TYPE_VIDEO) {
                val trackGroups = mappedTrackInfo.getTrackGroups(0)
                if (trackGroups.length > 0) {
                    trackGroups[0].getFormat(0)
                } else null
            } else null
        }
    }
    
    fun getAudioFormat(): androidx.media3.common.Format? {
        return trackSelector?.currentMappedTrackInfo?.let { mappedTrackInfo ->
            // Find audio renderer
            for (i in 0 until mappedTrackInfo.rendererCount) {
                if (mappedTrackInfo.getRendererType(i) == C.TRACK_TYPE_AUDIO) {
                    val trackGroups = mappedTrackInfo.getTrackGroups(i)
                    if (trackGroups.length > 0) {
                        return@let trackGroups[0].getFormat(0)
                    }
                }
            }
            null
        }
    }
    
    fun getAvailableVideoTracks(): List<VideoTrack> {
        val tracks = mutableListOf<VideoTrack>()
        trackSelector?.currentMappedTrackInfo?.let { mappedTrackInfo ->
            for (i in 0 until mappedTrackInfo.rendererCount) {
                if (mappedTrackInfo.getRendererType(i) == C.TRACK_TYPE_VIDEO) {
                    val trackGroups = mappedTrackInfo.getTrackGroups(i)
                    for (groupIndex in 0 until trackGroups.length) {
                        val group = trackGroups[groupIndex]
                        for (trackIndex in 0 until group.length) {
                            val format = group.getFormat(trackIndex)
                            tracks.add(
                                VideoTrack(
                                    id = "${i}_${groupIndex}_${trackIndex}",
                                    width = format.width,
                                    height = format.height,
                                    bitrate = format.bitrate,
                                    frameRate = format.frameRate,
                                    codecs = format.codecs ?: "Unknown"
                                )
                            )
                        }
                    }
                }
            }
        }
        return tracks
    }
    
    fun getAvailableAudioTracks(): List<AudioTrack> {
        val tracks = mutableListOf<AudioTrack>()
        trackSelector?.currentMappedTrackInfo?.let { mappedTrackInfo ->
            for (i in 0 until mappedTrackInfo.rendererCount) {
                if (mappedTrackInfo.getRendererType(i) == C.TRACK_TYPE_AUDIO) {
                    val trackGroups = mappedTrackInfo.getTrackGroups(i)
                    for (groupIndex in 0 until trackGroups.length) {
                        val group = trackGroups[groupIndex]
                        for (trackIndex in 0 until group.length) {
                            val format = group.getFormat(trackIndex)
                            tracks.add(
                                AudioTrack(
                                    id = "${i}_${groupIndex}_${trackIndex}",
                                    language = format.language ?: "Unknown",
                                    bitrate = format.bitrate,
                                    sampleRate = format.sampleRate,
                                    channels = format.channelCount,
                                    codecs = format.codecs ?: "Unknown"
                                )
                            )
                        }
                    }
                }
            }
        }
        return tracks
    }
    
    fun selectVideoTrack(trackIndex: Int) {
        exoPlayer?.let { player ->
            val trackGroups = player.currentTracks
            var videoTrackIndex = 0
            
            for (trackGroup in trackGroups.groups) {
                for (i in 0 until trackGroup.length) {
                    val trackFormat = trackGroup.getTrackFormat(i)
                    if (trackFormat.sampleMimeType?.startsWith("video/") == true) {
                        if (videoTrackIndex == trackIndex) {
                            // Select this video track
                            trackSelector?.let { selector ->
                                val parameters = selector.buildUponParameters()
                                    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
                                    .setMaxVideoSize(trackFormat.width, trackFormat.height)
                                    .build()
                                selector.setParameters(parameters)
                            }
                            return
                        }
                        videoTrackIndex++
                    }
                }
            }
        }
    }
    
    fun getVideoTracks(): List<VideoTrackInfo> {
        val tracks = mutableListOf<VideoTrackInfo>()
        exoPlayer?.currentTracks?.groups?.forEach { trackGroup ->
            for (i in 0 until trackGroup.length) {
                val format = trackGroup.getTrackFormat(i)
                if (format.sampleMimeType?.startsWith("video/") == true) {
                    tracks.add(
                        VideoTrackInfo(
                            index = tracks.size,
                            width = format.width,
                            height = format.height,
                            frameRate = format.frameRate,
                            codec = format.sampleMimeType ?: "Unknown",
                            bitrate = format.bitrate,
                            isSelected = trackGroup.isTrackSelected(i)
                        )
                    )
                }
            }
        }
        return tracks
    }
    
    fun selectAudioTrack(trackIndex: Int) {
        exoPlayer?.let { player ->
            val trackGroups = player.currentTracks
            var audioTrackIndex = 0
            
            for (trackGroup in trackGroups.groups) {
                for (i in 0 until trackGroup.length) {
                    val trackFormat = trackGroup.getTrackFormat(i)
                    if (trackFormat.sampleMimeType?.startsWith("audio/") == true) {
                        if (audioTrackIndex == trackIndex && trackGroup.isTrackSelected(i)) {
                            return // Already selected
                        }
                        if (audioTrackIndex == trackIndex) {
                            // Select this audio track
                            trackSelector?.let { selector ->
                                val parameters = selector.buildUponParameters()
                                    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_AUDIO, false)
                                    .build()
                                selector.setParameters(parameters)
                            }
                            return
                        }
                        audioTrackIndex++
                    }
                }
            }
        }
    }
    
    fun getAudioTracks(): List<AudioTrackInfo> {
        val tracks = mutableListOf<AudioTrackInfo>()
        exoPlayer?.currentTracks?.groups?.forEach { trackGroup ->
            for (i in 0 until trackGroup.length) {
                val format = trackGroup.getTrackFormat(i)
                if (format.sampleMimeType?.startsWith("audio/") == true) {
                    tracks.add(
                        AudioTrackInfo(
                            index = tracks.size,
                            language = format.language ?: "Unknown",
                            label = format.label ?: format.language ?: "Track ${tracks.size + 1}",
                            codec = format.sampleMimeType ?: "Unknown",
                            channels = format.channelCount,
                            sampleRate = format.sampleRate,
                            bitrate = format.bitrate,
                            isSelected = trackGroup.isTrackSelected(i)
                        )
                    )
                }
            }
        }
        return tracks
    }
    
    fun enableAdaptiveStreaming(enable: Boolean) {
        trackSelector?.setParameters(
            trackSelector!!.buildUponParameters()
                .setForceHighestSupportedBitrate(!enable)
        )
        _playerState.value = _playerState.value.copy(adaptiveStreaming = enable)
    }
    
    fun setMaxVideoBitrate(bitrate: Int) {
        trackSelector?.setParameters(
            trackSelector!!.buildUponParameters()
                .setMaxVideoBitrate(bitrate)
        )
    }
    
    fun setMaxVideoResolution(width: Int, height: Int) {
        trackSelector?.setParameters(
            trackSelector!!.buildUponParameters()
                .setMaxVideoSize(width, height)
        )
    }
    
    
    fun getCacheStatistics(): CacheStatistics {
        return cache?.let {
            CacheStatistics(
                cacheSize = it.cacheSpace,
                cachedBytes = 0L, // Would need to calculate actual cached bytes
                maxCacheSize = CACHE_SIZE
            )
        } ?: CacheStatistics(0, 0, 0)
    }
    
    fun clearCache() {
        try {
            cache?.release()
            initializeCache()
        } catch (e: Exception) {
            // Handle cache clearing error
        }
    }
    
    fun addListener(listener: Player.Listener) {
        exoPlayer?.addListener(listener)
    }
    
    fun removeListener(listener: Player.Listener) {
        exoPlayer?.removeListener(listener)
    }
    
    fun releasePlayer() {
        playerListener?.let { listener ->
            exoPlayer?.removeListener(listener)
        }
        exoPlayer?.release()
        exoPlayer = null
        trackSelector = null
        renderersFactory = null
        
        _playerState.value = _playerState.value.copy(
            isInitialized = false,
            playbackState = PlaybackState.IDLE,
            isPlaying = false,
            error = null
        )
    }
    
    fun release() {
        releasePlayer()
        try {
            cache?.release()
        } catch (e: Exception) {
            // Handle cache release error
        }
        cache = null
    }
}

enum class DecoderMode {
    HARDWARE,       // Use hardware decoders only
    HARDWARE_PLUS,  // Prefer hardware, fallback to software
    SOFTWARE        // Use software decoders only
}

enum class PlaybackState {
    IDLE, BUFFERING, READY, ENDED, ERROR
}

data class ExoPlayerState(
    val isInitialized: Boolean = false,
    val playbackState: PlaybackState = PlaybackState.IDLE,
    val isPlaying: Boolean = false,
    val decoderMode: DecoderMode = DecoderMode.HARDWARE,
    val multiCoreDecoding: Boolean = true,
    val adaptiveStreaming: Boolean = true,
    val videoSize: Pair<Int, Int>? = null,
    val aspectRatio: Float = 16f / 9f,
    val error: String? = null
)

data class VideoTrackInfo(
    val index: Int,
    val width: Int,
    val height: Int,
    val frameRate: Float,
    val codec: String,
    val bitrate: Int,
    val isSelected: Boolean
)

data class AudioTrackInfo(
    val index: Int,
    val language: String,
    val label: String,
    val codec: String,
    val channels: Int,
    val sampleRate: Int,
    val bitrate: Int,
    val isSelected: Boolean
)

data class VideoTrack(
    val id: String,
    val width: Int,
    val height: Int,
    val bitrate: Int,
    val frameRate: Float,
    val codecs: String
)

data class AudioTrack(
    val id: String,
    val language: String,
    val bitrate: Int,
    val sampleRate: Int,
    val channels: Int,
    val codecs: String
)

data class CacheStatistics(
    val cacheSize: Long,
    val cachedBytes: Long,
    val maxCacheSize: Long
)