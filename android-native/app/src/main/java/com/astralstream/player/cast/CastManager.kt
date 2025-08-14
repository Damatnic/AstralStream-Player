package com.astralstream.player.cast

import android.content.Context
import android.net.Uri
import com.google.android.gms.cast.*
import com.google.android.gms.cast.framework.*
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Google Cast sessions and media playback
 */
@Singleton
class CastManager @Inject constructor(
    private val context: Context
) {
    
    data class CastingState(
        val isConnected: Boolean = false,
        val isConnecting: Boolean = false,
        val connectedDevice: CastDevice? = null,
        val isPlaying: Boolean = false,
        val currentMedia: MediaInfo? = null,
        val currentPosition: Long = 0L,
        val duration: Long = 0L,
        val volume: Double = 1.0,
        val isMuted: Boolean = false,
        val error: String? = null
    )
    
    private val _castState = MutableStateFlow(CastingState())
    val castState: StateFlow<CastingState> = _castState.asStateFlow()
    
    private var castContext: CastContext? = null
    private var sessionManager: SessionManager? = null
    private var castSession: CastSession? = null
    private var remoteMediaClient: RemoteMediaClient? = null
    
    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            _castState.value = _castState.value.copy(
                isConnecting = true,
                error = null
            )
        }
        
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            castSession = session
            remoteMediaClient = session.remoteMediaClient
            setupRemoteMediaClientListener()
            
            _castState.value = _castState.value.copy(
                isConnected = true,
                isConnecting = false,
                connectedDevice = session.castDevice?.let {
                    CastDevice(
                        id = it.deviceId,
                        name = it.friendlyName ?: "Unknown",
                        model = it.modelName
                    )
                },
                error = null
            )
        }
        
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            _castState.value = _castState.value.copy(
                isConnected = false,
                isConnecting = false,
                connectedDevice = null,
                error = getErrorMessage(error)
            )
        }
        
        override fun onSessionEnding(session: CastSession) {
            // Session is ending
        }
        
        override fun onSessionEnded(session: CastSession, error: Int) {
            castSession = null
            remoteMediaClient = null
            
            _castState.value = _castState.value.copy(
                isConnected = false,
                isConnecting = false,
                connectedDevice = null,
                currentMedia = null,
                isPlaying = false,
                error = if (error != 0) getErrorMessage(error) else null
            )
        }
        
        override fun onSessionResuming(session: CastSession, sessionId: String) {
            _castState.value = _castState.value.copy(isConnecting = true)
        }
        
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            castSession = session
            remoteMediaClient = session.remoteMediaClient
            setupRemoteMediaClientListener()
            
            _castState.value = _castState.value.copy(
                isConnected = true,
                isConnecting = false,
                connectedDevice = session.castDevice?.let {
                    CastDevice(
                        id = it.deviceId,
                        name = it.friendlyName ?: "Unknown",
                        model = it.modelName
                    )
                },
                error = null
            )
        }
        
        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            _castState.value = _castState.value.copy(
                isConnected = false,
                isConnecting = false,
                error = getErrorMessage(error)
            )
        }
        
        override fun onSessionSuspended(session: CastSession, reason: Int) {
            _castState.value = _castState.value.copy(
                isConnected = false,
                connectedDevice = null
            )
        }
    }
    
    private val remoteMediaClientListener = object : RemoteMediaClient.Listener {
        override fun onStatusUpdated() {
            updateMediaStatus()
        }
        
        override fun onMetadataUpdated() {
            updateMediaStatus()
        }
        
        override fun onQueueStatusUpdated() {
            // Handle queue updates if needed
        }
        
        override fun onPreloadStatusUpdated() {
            // Handle preload status if needed
        }
        
        override fun onSendingRemoteMediaRequest() {
            // Request is being sent
        }
        
        override fun onAdBreakStatusUpdated() {
            // Handle ad breaks if needed
        }
    }
    
    fun initialize() {
        try {
            castContext = CastContext.getSharedInstance(context)
            sessionManager = castContext?.sessionManager
            sessionManager?.addSessionManagerListener(sessionManagerListener, CastSession::class.java)
            
            // Check if there's an existing session
            castSession = sessionManager?.currentCastSession
            if (castSession != null && castSession?.isConnected == true) {
                remoteMediaClient = castSession?.remoteMediaClient
                setupRemoteMediaClientListener()
                
                _castState.value = _castState.value.copy(
                    isConnected = true,
                    connectedDevice = castSession?.castDevice?.let {
                        CastDevice(
                            id = it.deviceId,
                            name = it.friendlyName ?: "Unknown",
                            model = it.modelName
                        )
                    }
                )
                
                updateMediaStatus()
            }
        } catch (e: Exception) {
            _castState.value = _castState.value.copy(
                error = "Failed to initialize Cast: ${e.message}"
            )
        }
    }
    
    fun cleanup() {
        remoteMediaClient?.removeListener(remoteMediaClientListener)
        sessionManager?.removeSessionManagerListener(sessionManagerListener, CastSession::class.java)
        castSession = null
        remoteMediaClient = null
        sessionManager = null
        castContext = null
    }
    
    /**
     * Cast a video to the connected device
     */
    fun castVideo(
        videoUrl: String,
        title: String,
        description: String? = null,
        thumbnailUrl: String? = null,
        subtitleUrl: String? = null,
        startPosition: Long = 0
    ) {
        val remoteMediaClient = this.remoteMediaClient ?: return
        
        val movieMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            description?.let { putString(MediaMetadata.KEY_SUBTITLE, it) }
            thumbnailUrl?.let {
                addImage(WebImage(Uri.parse(it)))
            }
        }
        
        val mediaInfo = MediaInfo.Builder(videoUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(getContentType(videoUrl))
            .setMetadata(movieMetadata)
            .apply {
                // Add subtitle track if provided
                subtitleUrl?.let {
                    val subtitleTrack = MediaTrack.Builder(1, MediaTrack.TYPE_TEXT)
                        .setContentId(it)
                        .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                        .setName("Subtitles")
                        .setLanguage("en-US")
                        .build()
                    setMediaTracks(listOf(subtitleTrack))
                }
            }
            .build()
        
        val loadOptions = MediaLoadOptions.Builder()
            .setAutoplay(true)
            .setPlayPosition(startPosition)
            .build()
        
        remoteMediaClient.load(mediaInfo, loadOptions)
            .addStatusListener { 
                if (it.isSuccess) {
                    _castState.value = _castState.value.copy(
                        currentMedia = mediaInfo,
                        error = null
                    )
                } else {
                    _castState.value = _castState.value.copy(
                        error = "Failed to load media"
                    )
                }
            }
    }
    
    /**
     * Control playback
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
        remoteMediaClient?.seek(position)
    }
    
    fun setVolume(volume: Double) {
        castSession?.volume = volume
        _castState.value = _castState.value.copy(volume = volume)
    }
    
    fun toggleMute() {
        val isMuted = castSession?.isMute ?: false
        castSession?.isMute = !isMuted
        _castState.value = _castState.value.copy(isMuted = !isMuted)
    }
    
    fun skipForward(seconds: Int = 30) {
        val currentPosition = remoteMediaClient?.approximateStreamPosition ?: 0
        seek(currentPosition + (seconds * 1000))
    }
    
    fun skipBackward(seconds: Int = 10) {
        val currentPosition = remoteMediaClient?.approximateStreamPosition ?: 0
        seek(maxOf(0, currentPosition - (seconds * 1000)))
    }
    
    /**
     * Queue management
     */
    fun addToQueue(
        videoUrl: String,
        title: String,
        description: String? = null,
        thumbnailUrl: String? = null
    ) {
        val remoteMediaClient = this.remoteMediaClient ?: return
        
        val movieMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            description?.let { putString(MediaMetadata.KEY_SUBTITLE, it) }
            thumbnailUrl?.let {
                addImage(WebImage(Uri.parse(it)))
            }
        }
        
        val mediaInfo = MediaInfo.Builder(videoUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(getContentType(videoUrl))
            .setMetadata(movieMetadata)
            .build()
        
        val queueItem = MediaQueueItem.Builder(mediaInfo).build()
        
        remoteMediaClient.queueAppendItem(queueItem, null)
    }
    
    fun clearQueue() {
        // Clear all items from queue
        val queueItemIds = remoteMediaClient?.mediaStatus?.queueItems?.map { it.itemId }?.toIntArray()
        if (queueItemIds != null && queueItemIds.isNotEmpty()) {
            remoteMediaClient?.queueRemoveItems(queueItemIds, null)
        }
    }
    
    fun nextInQueue() {
        remoteMediaClient?.queueNext(null)
    }
    
    fun previousInQueue() {
        remoteMediaClient?.queuePrev(null)
    }
    
    /**
     * Get current Cast session
     */
    fun getCurrentSession(): CastSession? = castSession
    
    /**
     * Check if casting is available
     */
    fun isCastAvailable(): Boolean {
        return castContext?.castState == com.google.android.gms.cast.framework.CastState.NOT_CONNECTED ||
               castContext?.castState == com.google.android.gms.cast.framework.CastState.CONNECTED
    }
    
    /**
     * Show Cast dialog
     */
    fun showCastDialog() {
        castContext?.let {
            if (it.castState != com.google.android.gms.cast.framework.CastState.NO_DEVICES_AVAILABLE) {
                // The Cast button will handle showing the dialog
                // This is here for programmatic access if needed
            }
        }
    }
    
    private fun setupRemoteMediaClientListener() {
        remoteMediaClient?.addListener(remoteMediaClientListener)
        updateMediaStatus()
    }
    
    private fun updateMediaStatus() {
        val mediaStatus = remoteMediaClient?.mediaStatus
        val mediaInfo = remoteMediaClient?.mediaInfo
        
        _castState.value = _castState.value.copy(
            isPlaying = mediaStatus?.playerState == MediaStatus.PLAYER_STATE_PLAYING,
            currentMedia = mediaInfo,
            currentPosition = remoteMediaClient?.approximateStreamPosition ?: 0,
            duration = mediaInfo?.streamDuration ?: 0
        )
    }
    
    private fun getContentType(url: String): String {
        return when {
            url.endsWith(".mp4", true) -> "video/mp4"
            url.endsWith(".mkv", true) -> "video/x-matroska"
            url.endsWith(".webm", true) -> "video/webm"
            url.endsWith(".m3u8", true) -> "application/x-mpegURL"
            url.endsWith(".mpd", true) -> "application/dash+xml"
            else -> "video/mp4"
        }
    }
    
    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            CastStatusCodes.INTERRUPTED -> "Connection interrupted"
            CastStatusCodes.AUTHENTICATION_FAILED -> "Authentication failed"
            CastStatusCodes.INVALID_REQUEST -> "Invalid request"
            CastStatusCodes.CANCELED -> "Operation canceled"
            CastStatusCodes.NOT_ALLOWED -> "Operation not allowed"
            CastStatusCodes.APPLICATION_NOT_FOUND -> "Application not found"
            CastStatusCodes.APPLICATION_NOT_RUNNING -> "Application not running"
            CastStatusCodes.MESSAGE_TOO_LARGE -> "Message too large"
            CastStatusCodes.MESSAGE_SEND_BUFFER_TOO_FULL -> "Message buffer full"
            CastStatusCodes.NETWORK_ERROR -> "Network error"
            CastStatusCodes.TIMEOUT -> "Connection timeout"
            CastStatusCodes.UNKNOWN_ERROR -> "Unknown error"
            else -> "Error code: $errorCode"
        }
    }
    
    companion object {
        const val CAST_STATE_NO_DEVICES = 1
        const val CAST_STATE_NOT_CONNECTED = 2
        const val CAST_STATE_CONNECTING = 3
        const val CAST_STATE_CONNECTED = 4
    }
}