package com.astralstream.player.services

import android.content.Context
import android.os.Environment
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Background worker for scanning media files
 */
@HiltWorker
class MediaScanWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_SCAN_PATH = "scan_path"
        const val KEY_TOTAL_FILES = "total_files"
        const val KEY_SCANNED_FILES = "scanned_files"
        
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm",
            "m4v", "mpg", "mpeg", "3gp", "3g2", "ts", "mts"
        )
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val scanPath = inputData.getString(KEY_SCAN_PATH) 
                ?: Environment.getExternalStorageDirectory().absolutePath
            
            val videoFiles = mutableListOf<File>()
            scanForVideos(File(scanPath), videoFiles)
            
            var scannedCount = 0
            val totalCount = videoFiles.size
            
            // Report initial progress
            setProgress(workDataOf(
                KEY_TOTAL_FILES to totalCount,
                KEY_SCANNED_FILES to 0
            ))
            
            // Process each video file
            videoFiles.forEach { file ->
                // Extract basic metadata
                val metadata = extractBasicMetadata(file)
                
                // Store in database (mock for now)
                storeVideoInDatabase(file, metadata)
                
                // Update progress
                scannedCount++
                setProgress(workDataOf(
                    KEY_TOTAL_FILES to totalCount,
                    KEY_SCANNED_FILES to scannedCount
                ))
                
                // Check if cancelled
                if (isStopped) {
                    return@withContext Result.failure()
                }
            }
            
            Result.success(workDataOf(
                KEY_TOTAL_FILES to totalCount,
                KEY_SCANNED_FILES to totalCount
            ))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
    
    private fun scanForVideos(directory: File, videoFiles: MutableList<File>) {
        if (!directory.exists() || !directory.canRead()) return
        
        directory.listFiles()?.forEach { file ->
            when {
                file.isDirectory && !file.name.startsWith(".") -> {
                    // Recursively scan subdirectories
                    scanForVideos(file, videoFiles)
                }
                file.isFile && isVideoFile(file) -> {
                    videoFiles.add(file)
                }
            }
        }
    }
    
    private fun isVideoFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        return VIDEO_EXTENSIONS.contains(extension)
    }
    
    private fun extractBasicMetadata(file: File): VideoMetadata {
        return VideoMetadata(
            fileName = file.name,
            filePath = file.absolutePath,
            fileSize = file.length(),
            lastModified = file.lastModified(),
            duration = 0L // Would use MediaMetadataRetriever in real implementation
        )
    }
    
    private suspend fun storeVideoInDatabase(file: File, metadata: VideoMetadata) {
        // In a real implementation, this would store to Room database
        // For now, just simulate processing time
        kotlinx.coroutines.delay(10)
    }
    
    data class VideoMetadata(
        val fileName: String,
        val filePath: String,
        val fileSize: Long,
        val lastModified: Long,
        val duration: Long
    )
}