package com.astralstream.player.recording

import android.content.Context
import android.net.Uri
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles recording of network streams to local storage
 */
@Singleton
class StreamRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    data class RecordingState(
        val isRecording: Boolean = false,
        val isPaused: Boolean = false,
        val currentUrl: String? = null,
        val outputFile: File? = null,
        val bytesRecorded: Long = 0,
        val totalBytes: Long = 0,
        val duration: Long = 0,
        val startTime: Long = 0,
        val error: String? = null,
        val progress: Float = 0f
    )
    
    data class RecordingInfo(
        val id: String,
        val url: String,
        val title: String,
        val outputPath: String,
        val startTime: Long,
        val endTime: Long = 0,
        val bytesRecorded: Long = 0,
        val status: RecordingStatus
    )
    
    enum class RecordingStatus {
        RECORDING,
        PAUSED,
        COMPLETED,
        FAILED,
        CANCELLED
    }
    
    private val _recordingState = MutableStateFlow(RecordingState())
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()
    
    private val activeRecordings = mutableMapOf<String, RecordingJob>()
    private val recordingHistory = mutableListOf<RecordingInfo>()
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()
    
    private var recordingJob: Job? = null
    private var currentResponse: Response? = null
    
    /**
     * Start recording a stream
     */
    fun startRecording(
        url: String,
        title: String,
        outputDirectory: File? = null
    ): String {
        if (_recordingState.value.isRecording) {
            stopRecording()
        }
        
        val recordingId = generateRecordingId()
        val outputDir = outputDirectory ?: getDefaultRecordingDirectory()
        val fileName = sanitizeFileName(title) + "_" + System.currentTimeMillis() + ".mp4"
        val outputFile = File(outputDir, fileName)
        
        // Ensure directory exists
        outputDir.mkdirs()
        
        _recordingState.value = RecordingState(
            isRecording = true,
            currentUrl = url,
            outputFile = outputFile,
            startTime = System.currentTimeMillis()
        )
        
        val recordingInfo = RecordingInfo(
            id = recordingId,
            url = url,
            title = title,
            outputPath = outputFile.absolutePath,
            startTime = System.currentTimeMillis(),
            status = RecordingStatus.RECORDING
        )
        
        recordingHistory.add(recordingInfo)
        
        // Start recording in background
        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                recordStream(url, outputFile) { bytesWritten, totalBytes ->
                    val progress = if (totalBytes > 0) {
                        (bytesWritten.toFloat() / totalBytes.toFloat())
                    } else {
                        0f
                    }
                    
                    _recordingState.value = _recordingState.value.copy(
                        bytesRecorded = bytesWritten,
                        totalBytes = totalBytes,
                        progress = progress,
                        duration = System.currentTimeMillis() - _recordingState.value.startTime
                    )
                }
                
                // Recording completed successfully
                updateRecordingStatus(recordingId, RecordingStatus.COMPLETED)
                _recordingState.value = _recordingState.value.copy(
                    isRecording = false,
                    error = null
                )
                
            } catch (e: CancellationException) {
                // Recording was cancelled
                updateRecordingStatus(recordingId, RecordingStatus.CANCELLED)
                _recordingState.value = _recordingState.value.copy(
                    isRecording = false,
                    error = "Recording cancelled"
                )
                
            } catch (e: Exception) {
                // Recording failed
                updateRecordingStatus(recordingId, RecordingStatus.FAILED)
                _recordingState.value = _recordingState.value.copy(
                    isRecording = false,
                    error = e.message
                )
            }
        }
        
        return recordingId
    }
    
    /**
     * Stop current recording
     */
    fun stopRecording() {
        recordingJob?.cancel()
        currentResponse?.close()
        recordingJob = null
        currentResponse = null
        
        _recordingState.value = _recordingState.value.copy(
            isRecording = false,
            isPaused = false
        )
    }
    
    /**
     * Pause current recording
     */
    fun pauseRecording() {
        if (_recordingState.value.isRecording && !_recordingState.value.isPaused) {
            _recordingState.value = _recordingState.value.copy(isPaused = true)
        }
    }
    
    /**
     * Resume paused recording
     */
    fun resumeRecording() {
        if (_recordingState.value.isRecording && _recordingState.value.isPaused) {
            _recordingState.value = _recordingState.value.copy(isPaused = false)
        }
    }
    
    /**
     * Record stream to file
     */
    private suspend fun recordStream(
        url: String,
        outputFile: File,
        onProgress: (Long, Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "AstralStream/1.0")
            .build()
        
        currentResponse = httpClient.newCall(request).execute()
        val response = currentResponse ?: throw IOException("Failed to connect")
        
        if (!response.isSuccessful) {
            throw IOException("Failed to connect: ${response.code}")
        }
        
        val contentLength = response.body?.contentLength() ?: -1
        val inputStream = response.body?.byteStream() ?: throw IOException("No response body")
        
        FileOutputStream(outputFile).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytesRead: Int
            var totalBytesRead = 0L
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                // Check if recording is paused
                while (_recordingState.value.isPaused) {
                    delay(100)
                    if (!_recordingState.value.isRecording) {
                        throw CancellationException("Recording stopped")
                    }
                }
                
                // Check if job is cancelled
                if (!isActive) {
                    throw CancellationException("Recording cancelled")
                }
                
                output.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                onProgress(totalBytesRead, contentLength)
            }
        }
    }
    
    /**
     * Get default recording directory
     */
    private fun getDefaultRecordingDirectory(): File {
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "AstralStream/Recordings"
        )
    }
    
    /**
     * Generate unique recording ID
     */
    private fun generateRecordingId(): String {
        return "REC_${System.currentTimeMillis()}_${(0..9999).random()}"
    }
    
    /**
     * Sanitize filename
     */
    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(100) // Limit filename length
    }
    
    /**
     * Update recording status in history
     */
    private fun updateRecordingStatus(recordingId: String, status: RecordingStatus) {
        val index = recordingHistory.indexOfFirst { it.id == recordingId }
        if (index >= 0) {
            val recording = recordingHistory[index]
            recordingHistory[index] = recording.copy(
                status = status,
                endTime = System.currentTimeMillis(),
                bytesRecorded = _recordingState.value.bytesRecorded
            )
        }
    }
    
    /**
     * Get recording history
     */
    fun getRecordingHistory(): List<RecordingInfo> {
        return recordingHistory.toList()
    }
    
    /**
     * Clear recording history
     */
    fun clearHistory() {
        recordingHistory.clear()
    }
    
    /**
     * Delete recording file
     */
    fun deleteRecording(recordingInfo: RecordingInfo): Boolean {
        val file = File(recordingInfo.outputPath)
        val deleted = file.delete()
        if (deleted) {
            recordingHistory.remove(recordingInfo)
        }
        return deleted
    }
    
    /**
     * Get available storage space
     */
    fun getAvailableSpace(): Long {
        val recordingDir = getDefaultRecordingDirectory()
        return recordingDir.usableSpace
    }
    
    /**
     * Check if there's enough space for recording
     */
    fun hasEnoughSpace(estimatedSize: Long = MIN_REQUIRED_SPACE): Boolean {
        return getAvailableSpace() > estimatedSize
    }
    
    /**
     * Data class for recording job
     */
    private data class RecordingJob(
        val id: String,
        val job: Job,
        val info: RecordingInfo
    )
    
    companion object {
        private const val DEFAULT_BUFFER_SIZE = 65536 // 64KB
        private const val MIN_REQUIRED_SPACE = 100 * 1024 * 1024L // 100MB minimum
    }
}