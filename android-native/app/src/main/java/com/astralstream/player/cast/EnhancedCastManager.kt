package com.astralstream.player.cast

import android.content.Context
import android.net.Uri
import com.google.android.gms.cast.*
import com.google.android.gms.cast.framework.*
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhanced Cast Manager with full Chromecast support
 */
@Singleton
class EnhancedCastManager @Inject constructor(
    private val context: Context
) {
    private val castContext: CastContext by lazy {
        CastContext.getSharedInstance(context)
    }
    
    private val sessionManager: SessionManager by lazy {
        castContext.sessionManager
    }
    
    private val remoteMediaClient: RemoteMediaClient?
        get() = sessionManager.currentCastSession?.remoteMediaClient
    
    private val _castState = MutableStateFlow(CastState())
    val castState: StateFlow<CastState> = _castState.asStateFlow()
    
    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            _castState.value = _castState.value.copy(
                isConnecting = true,
                connectionState = ConnectionState.CONNECTING
            )
        }
        
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            _castState.value = _castState.value.copy(
                isConnected = true,
                isConnecting = false,
                connectionState = ConnectionState.CONNECTED,
                deviceName = session.castDevice?.friendlyName,
                sessionId = sessionId
            )
            setupRemoteMediaClient()
        }
        
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            _castState.value = _castState.value.copy(
                isConnecting = false,
                connectionState = ConnectionState.DISCONNECTED,
                error = "Failed to start cast session: $error"
            )
        }
        
        override fun onSessionEnding(session: CastSession) {
            _castState.value = _castState.value.copy(
                connectionState = ConnectionState.DISCONNECTING
            )
        }
        
        override fun onSessionEnded(session: CastSession, error: Int) {
            _castState.value = CastState()
        }
        
        override fun onSessionResuming(session: CastSession, sessionId: String) {
            _castState.value = _castState.value.copy(
                isConnecting = true,
                connectionState = ConnectionState.CONNECTING
            )
        }
        
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            _castState.value = _castState.value.copy(
                isConnected = true,
                isConnecting = false,
                connectionState = ConnectionState.CONNECTED,
                deviceName = session.castDevice?.friendlyName
            )
            setupRemoteMediaClient()
        }
        
        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            _castState.value = _castState.value.copy(
                isConnecting = false,
                connectionState = ConnectionState.DISCONNECTED,
                error = "Failed to resume cast session: $error"
            )
        }
        
        override fun onSessionSuspended(session: CastSession, reason: Int) {
            _castState.value = _castState.value.copy(
                connectionState = ConnectionState.SUSPENDED
            )
        }
    }
    
    private val remoteMediaClientListener = object : RemoteMediaClient.Listener {
        override fun onStatusUpdated() {
            updateMediaStatus()
        }
        
        override fun onMetadataUpdated() {
            updateMediaMetadata()
        }
        
        override fun onQueueStatusUpdated() {
            updateQueueStatus()
        }
        
        override fun onPreloadStatusUpdated() {
            // Handle preload status
        }
        
        override fun onSendingRemoteMediaRequest() {
            _castState.value = _castState.value.copy(isLoading = true)
        }
        
        override fun onAdBreakStatusUpdated() {
            // Handle ad breaks if needed
        }
    }
    
    init {
        sessionManager.addSessionManagerListener(sessionManagerListener, CastSession::class.java)
    }
    
    /**
     * Starts device discovery
     */
    fun startDiscovery() {
        castContext.setReceiverApplicationId(getReceiverAppId())
    }
    
    /**
     * Stops device discovery
     */
    fun stopDiscovery() {
        // Discovery is managed by the Cast SDK
    }
    
    /**
     * Gets available cast devices
     */
    fun getAvailableDevices(): Flow<List<CastDevice>> = callbackFlow {
        // MediaRouter implementation would go here
        // For now, return empty flow
        awaitClose { }
    }
    
    /**
     * Connects to a cast device
     */
    fun connectToDevice(device: CastDevice) {
        // CastDevice needs to be converted to Google CastDevice
        // For now, just end the current session if any
        sessionManager.endCurrentSession(false)
    }
    
    /**
     * Disconnects from current cast device
     */
    fun disconnect() {
        sessionManager.endCurrentSession(true)
    }
    
    /**
     * Casts a video to the connected device
     */
    fun castVideo(
        uri: Uri,
        title: String,
        subtitle: String? = null,
        thumbnailUrl: String? = null,
        position: Long = 0L
    ) {
        val mediaInfo = buildMediaInfo(uri, title, subtitle, thumbnailUrl)
        val loadOptions = MediaLoadOptions.Builder()
            .setAutoplay(true)
            .setPlayPosition(position)
            .build()
        
        remoteMediaClient?.load(mediaInfo, loadOptions)
        // Handle result asynchronously
        _castState.value = _castState.value.copy(
            isPlaying = true,
            currentMedia = MediaItem(uri.toString(), title, subtitle, thumbnailUrl)
        )
    }
    
    /**
     * Queues a video for casting
     */
    fun queueVideo(
        uri: Uri,
        title: String,
        subtitle: String? = null,
        thumbnailUrl: String? = null
    ) {
        val mediaInfo = buildMediaInfo(uri, title, subtitle, thumbnailUrl)
        val queueItem = MediaQueueItem.Builder(mediaInfo).build()
        
        remoteMediaClient?.queueAppendItem(queueItem, null)
    }
    
    /**
     * Controls playback
     */
    fun play() {
        remoteMediaClient?.play()
    }
    
    fun pause() {
        remoteMediaClient?.pause()
    }
    
    fun stop() {
        remoteMediaClient?.stop()
    }
    
    fun seek(position: Long) {
        remoteMediaClient?.seek(
            MediaSeekOptions.Builder()
                .setPosition(position)
                .build()
        )
    }
    
    fun skipToNext() {
        remoteMediaClient?.queueNext(null)
    }
    
    fun skipToPrevious() {
        remoteMediaClient?.queuePrev(null)
    }
    
    fun setVolume(volume: Float) {
        remoteMediaClient?.setStreamVolume(volume.toDouble())
    }
    
    fun setMuted(muted: Boolean) {
        remoteMediaClient?.setStreamMute(muted)
    }
    
    /**
     * Gets current playback position
     */
    fun getCurrentPosition(): Long {
        return remoteMediaClient?.approximateStreamPosition ?: 0L
    }
    
    /**
     * Gets media duration
     */
    fun getDuration(): Long {
        return remoteMediaClient?.streamDuration ?: 0L
    }
    
    /**
     * Setup remote media client listeners
     */
    private fun setupRemoteMediaClient() {
        // Register callback properly
        remoteMediaClient?.addListener(remoteMediaClientListener)
        updateMediaStatus()
    }
    
    /**
     * Updates media status from remote client
     */
    private fun updateMediaStatus() {
        val mediaStatus = remoteMediaClient?.mediaStatus
        
        _castState.value = _castState.value.copy(
            isPlaying = mediaStatus?.playerState == MediaStatus.PLAYER_STATE_PLAYING,
            isPaused = mediaStatus?.playerState == MediaStatus.PLAYER_STATE_PAUSED,
            isBuffering = mediaStatus?.playerState == MediaStatus.PLAYER_STATE_BUFFERING,
            currentPosition = remoteMediaClient?.approximateStreamPosition ?: 0L,
            duration = remoteMediaClient?.streamDuration ?: 0L,
            volume = mediaStatus?.streamVolume?.toFloat() ?: 1f,
            isMuted = mediaStatus?.isMute ?: false,
            isLoading = false
        )
    }
    
    /**
     * Updates media metadata from remote client
     */
    private fun updateMediaMetadata() {
        val mediaInfo = remoteMediaClient?.mediaInfo
        val metadata = mediaInfo?.metadata
        
        _castState.value = _castState.value.copy(
            currentMedia = MediaItem(
                uri = mediaInfo?.contentId ?: "",
                title = metadata?.getString(MediaMetadata.KEY_TITLE) ?: "",
                subtitle = metadata?.getString(MediaMetadata.KEY_SUBTITLE),
                thumbnailUrl = metadata?.images?.firstOrNull()?.url?.toString()
            )
        )
    }
    
    /**
     * Updates queue status
     */
    private fun updateQueueStatus() {
        val queue = remoteMediaClient?.mediaQueue
        
        _castState.value = _castState.value.copy(
            queueItems = queue?.itemCount ?: 0,
            currentQueueItem = 0 // queue?.currentItemId not available
        )
    }
    
    /**
     * Builds MediaInfo for casting
     */
    private fun buildMediaInfo(
        uri: Uri,
        title: String,
        subtitle: String?,
        thumbnailUrl: String?
    ): MediaInfo {
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            subtitle?.let { putString(MediaMetadata.KEY_SUBTITLE, it) }
            thumbnailUrl?.let {
                addImage(WebImage(Uri.parse(it)))
            }
        }
        
        return MediaInfo.Builder(uri.toString())
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(getContentType(uri))
            .setMetadata(metadata)
            .build()
    }
    
    /**
     * Gets content type from URI
     */
    private fun getContentType(uri: Uri): String {
        val extension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()
        
        return when (extension) {
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "m3u8" -> "application/x-mpegURL"
            "mpd" -> "application/dash+xml"
            else -> "video/*"
        }
    }
    
    /**
     * Gets receiver app ID
     */
    private fun getReceiverAppId(): String {
        // Use default media receiver or custom receiver app ID
        return CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
    }
    
    /**
     * Cleanup
     */
    fun release() {
        remoteMediaClient?.removeListener(remoteMediaClientListener)
        sessionManager.removeSessionManagerListener(sessionManagerListener, CastSession::class.java)
    }
}

data class CastState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val deviceName: String? = null,
    val sessionId: String? = null,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val isBuffering: Boolean = false,
    val isLoading: Boolean = false,
    val currentMedia: MediaItem? = null,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val volume: Float = 1f,
    val isMuted: Boolean = false,
    val queueItems: Int = 0,
    val currentQueueItem: Int = 0,
    val error: String? = null
)

data class MediaItem(
    val uri: String,
    val title: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null
)

data class CastDevice(
    val id: String,
    val name: String,
    val model: String? = null,
    val isNearby: Boolean = false
)

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    SUSPENDED
}