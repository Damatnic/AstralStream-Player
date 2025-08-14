package com.astralstream.player.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.astralstream.player.recording.StreamRecorder
import com.astralstream.player.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

/**
 * Foreground service for recording streams in the background
 */
@AndroidEntryPoint
class RecordingService : Service() {
    
    @Inject
    lateinit var streamRecorder: StreamRecorder
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var recordingStateJob: Job? = null
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        observeRecordingState()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Recording"
                startRecording(url, title)
            }
            ACTION_STOP_RECORDING -> {
                stopRecording()
            }
            ACTION_PAUSE_RECORDING -> {
                pauseRecording()
            }
            ACTION_RESUME_RECORDING -> {
                resumeRecording()
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        recordingStateJob?.cancel()
        serviceScope.cancel()
        streamRecorder.stopRecording()
    }
    
    private fun startRecording(url: String, title: String) {
        // Start foreground service
        startForeground(NOTIFICATION_ID, createNotification(
            title = "Recording: $title",
            content = "Starting...",
            isRecording = true
        ))
        
        // Start recording
        streamRecorder.startRecording(url, title)
    }
    
    private fun stopRecording() {
        streamRecorder.stopRecording()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    private fun pauseRecording() {
        streamRecorder.pauseRecording()
        updateNotification(
            title = "Recording Paused",
            content = "Tap to resume",
            isRecording = false,
            isPaused = true
        )
    }
    
    private fun resumeRecording() {
        streamRecorder.resumeRecording()
        updateNotification(
            title = "Recording",
            content = "In progress...",
            isRecording = true,
            isPaused = false
        )
    }
    
    private fun observeRecordingState() {
        recordingStateJob = serviceScope.launch {
            streamRecorder.recordingState.collectLatest { state ->
                if (state.isRecording) {
                    val title = if (state.isPaused) "Recording Paused" else "Recording"
                    val content = when {
                        state.isPaused -> "Tap to resume"
                        state.bytesRecorded > 0 -> {
                            val mb = state.bytesRecorded / (1024 * 1024)
                            val duration = formatDuration(state.duration)
                            "$mb MB • $duration"
                        }
                        else -> "Starting..."
                    }
                    
                    updateNotification(
                        title = title,
                        content = content,
                        isRecording = !state.isPaused,
                        isPaused = state.isPaused,
                        progress = state.progress
                    )
                } else if (state.error != null) {
                    updateNotification(
                        title = "Recording Failed",
                        content = state.error,
                        isRecording = false
                    )
                    stopSelf()
                }
            }
        }
    }
    
    private fun createNotification(
        title: String,
        content: String,
        isRecording: Boolean,
        isPaused: Boolean = false,
        progress: Float = 0f
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setOngoing(true)
        
        // Add progress if recording
        if (isRecording && progress > 0) {
            builder.setProgress(100, (progress * 100).toInt(), false)
        }
        
        // Add actions
        if (isRecording) {
            if (isPaused) {
                builder.addAction(
                    android.R.drawable.ic_media_play,
                    "Resume",
                    createActionPendingIntent(ACTION_RESUME_RECORDING)
                )
            } else {
                builder.addAction(
                    android.R.drawable.ic_media_pause,
                    "Pause",
                    createActionPendingIntent(ACTION_PAUSE_RECORDING)
                )
            }
            
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                createActionPendingIntent(ACTION_STOP_RECORDING)
            )
        }
        
        return builder.build()
    }
    
    private fun updateNotification(
        title: String,
        content: String,
        isRecording: Boolean,
        isPaused: Boolean = false,
        progress: Float = 0f
    ) {
        val notification = createNotification(title, content, isRecording, isPaused, progress)
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }
    
    private fun createActionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, RecordingService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Stream Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows recording progress"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    private fun formatDuration(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }
    
    companion object {
        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIFICATION_ID = 1001
        
        const val ACTION_START_RECORDING = "com.astralstream.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.astralstream.action.STOP_RECORDING"
        const val ACTION_PAUSE_RECORDING = "com.astralstream.action.PAUSE_RECORDING"
        const val ACTION_RESUME_RECORDING = "com.astralstream.action.RESUME_RECORDING"
        
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        
        fun startRecording(context: Context, url: String, title: String) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_START_RECORDING
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopRecording(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_STOP_RECORDING
            }
            context.startService(intent)
        }
    }
}