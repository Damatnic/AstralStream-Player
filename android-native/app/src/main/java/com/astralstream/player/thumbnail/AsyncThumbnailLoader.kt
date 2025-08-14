package com.astralstream.player.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.collection.LruCache
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AsyncThumbnailLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Memory cache for thumbnails (max 50MB)
    private val memoryCache = object : LruCache<String, Bitmap>(50 * 1024 * 1024) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }
    
    // Track loading operations to avoid duplicates
    private val loadingJobs = ConcurrentHashMap<String, Job>()
    
    // Loading queue with priority support
    private val loadingQueue = Channel<ThumbnailRequest>(Channel.UNLIMITED)
    
    // Coroutine scope for background loading
    private val loaderScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // State flow for loading progress
    private val _loadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val loadingState: StateFlow<LoadingState> = _loadingState
    
    init {
        // Start background loader workers
        repeat(3) { // Run 3 parallel workers
            loaderScope.launch {
                processLoadingQueue()
            }
        }
    }
    
    private suspend fun processLoadingQueue() {
        for (request in loadingQueue) {
            try {
                loadThumbnailInternal(request)
            } catch (e: Exception) {
                request.onError?.invoke(e)
            }
        }
    }
    
    fun loadThumbnail(
        videoPath: String,
        width: Int = 320,
        height: Int = 180,
        priority: Priority = Priority.NORMAL,
        onSuccess: (Bitmap) -> Unit,
        onError: ((Exception) -> Unit)? = null
    ) {
        // Check memory cache first
        val cacheKey = getCacheKey(videoPath, width, height)
        memoryCache.get(cacheKey)?.let {
            onSuccess(it)
            return
        }
        
        // Check if already loading
        if (loadingJobs.containsKey(cacheKey)) {
            return
        }
        
        // Add to loading queue
        val request = ThumbnailRequest(
            videoPath = videoPath,
            width = width,
            height = height,
            priority = priority,
            onSuccess = onSuccess,
            onError = onError,
            cacheKey = cacheKey
        )
        
        loaderScope.launch {
            loadingQueue.send(request)
        }
    }
    
    private suspend fun loadThumbnailInternal(request: ThumbnailRequest) {
        val job = loaderScope.launch {
            try {
                _loadingState.value = LoadingState.Loading(request.videoPath)
                
                // Try multiple strategies to get thumbnail
                val bitmap = withContext(Dispatchers.IO) {
                    generateThumbnailWithFallback(
                        request.videoPath,
                        request.width,
                        request.height
                    )
                }
                
                if (bitmap != null) {
                    // Cache the result
                    memoryCache.put(request.cacheKey, bitmap)
                    
                    // Notify success on main thread
                    withContext(Dispatchers.Main) {
                        request.onSuccess(bitmap)
                    }
                } else {
                    throw Exception("Failed to generate thumbnail")
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    request.onError?.invoke(e)
                }
            } finally {
                loadingJobs.remove(request.cacheKey)
                _loadingState.value = LoadingState.Idle
            }
        }
        
        loadingJobs[request.cacheKey] = job
        job.join()
    }
    
    private fun generateThumbnailWithFallback(
        videoPath: String,
        width: Int,
        height: Int
    ): Bitmap? {
        // Try different positions until we get a valid frame
        val positions = listOf(
            5000000L,  // 5 seconds
            2000000L,  // 2 seconds
            1000000L,  // 1 second
            0L         // First frame
        )
        
        for (position in positions) {
            val bitmap = tryGetFrameAtPosition(videoPath, position, width, height)
            if (bitmap != null) {
                return bitmap
            }
        }
        
        // Last resort: try with OPTION_CLOSEST_SYNC
        return tryGetFrameWithOption(videoPath, width, height)
    }
    
    private fun tryGetFrameAtPosition(
        videoPath: String,
        positionUs: Long,
        width: Int,
        height: Int
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoPath)
            
            // Validate file first
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            if (hasVideo != "yes") {
                return null
            }
            
            val frame = retriever.getFrameAtTime(positionUs)
            frame?.let {
                val scaledBitmap = Bitmap.createScaledBitmap(it, width, height, true)
                if (it != scaledBitmap) {
                    it.recycle()
                }
                scaledBitmap
            }
        } catch (e: Exception) {
            null
        } finally {
            safely { retriever.release() }
        }
    }
    
    private fun tryGetFrameWithOption(
        videoPath: String,
        width: Int,
        height: Int
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoPath)
            
            // Use OPTION_CLOSEST_SYNC for better reliability
            val frame = retriever.getFrameAtTime(
                -1L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
            
            frame?.let {
                val scaledBitmap = Bitmap.createScaledBitmap(it, width, height, true)
                if (it != scaledBitmap) {
                    it.recycle()
                }
                scaledBitmap
            }
        } catch (e: Exception) {
            null
        } finally {
            safely { retriever.release() }
        }
    }
    
    fun preloadThumbnails(videoPaths: List<String>) {
        videoPaths.forEach { path ->
            loadThumbnail(
                videoPath = path,
                priority = Priority.LOW,
                onSuccess = { /* Just cache it */ },
                onError = null
            )
        }
    }
    
    fun clearCache() {
        memoryCache.evictAll()
        loadingJobs.values.forEach { it.cancel() }
        loadingJobs.clear()
    }
    
    fun getCachedThumbnail(videoPath: String, width: Int = 320, height: Int = 180): Bitmap? {
        return memoryCache.get(getCacheKey(videoPath, width, height))
    }
    
    private fun getCacheKey(path: String, width: Int, height: Int): String {
        return "${path}_${width}x${height}"
    }
    
    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    fun release() {
        loaderScope.cancel()
        clearCache()
    }
    
    data class ThumbnailRequest(
        val videoPath: String,
        val width: Int,
        val height: Int,
        val priority: Priority,
        val onSuccess: (Bitmap) -> Unit,
        val onError: ((Exception) -> Unit)?,
        val cacheKey: String
    )
    
    enum class Priority {
        HIGH, NORMAL, LOW
    }
    
    sealed class LoadingState {
        object Idle : LoadingState()
        data class Loading(val path: String) : LoadingState()
        data class Error(val message: String) : LoadingState()
    }
}