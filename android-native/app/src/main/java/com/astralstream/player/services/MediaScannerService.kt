package com.astralstream.player.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint

/**
 * Service for scanning media files in the background
 */
@AndroidEntryPoint
class MediaScannerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SCAN_MEDIA -> {
                startMediaScan()
            }
            ACTION_STOP_SCAN -> {
                stopMediaScan()
            }
        }
        return START_NOT_STICKY
    }

    private fun startMediaScan() {
        val scanWorkRequest = OneTimeWorkRequestBuilder<MediaScanWorker>()
            .build()
        
        WorkManager.getInstance(this).enqueue(scanWorkRequest)
        stopSelf()
    }

    private fun stopMediaScan() {
        WorkManager.getInstance(this).cancelAllWorkByTag("media_scan")
        stopSelf()
    }

    companion object {
        const val ACTION_SCAN_MEDIA = "com.astralstream.player.SCAN_MEDIA"
        const val ACTION_STOP_SCAN = "com.astralstream.player.STOP_SCAN"
    }
}