package com.astralstream.player.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

@Singleton
class ThumbnailGenerator @Inject constructor(
    private val context: Context
) {
    
    companion object {
        private const val THUMBNAIL_WIDTH = 320
        private const val THUMBNAIL_HEIGHT = 180
        private const val PREVIEW_WIDTH = 160
        private const val PREVIEW_HEIGHT = 90
        private const val SPRITE_COLUMNS = 10
        private const val SPRITE_ROWS = 10
        private const val DEFAULT_INTERVAL_SECONDS = 10
        private const val MAX_CACHE_SIZE_MB = 100
        private const val THUMBNAIL_QUALITY = 80
    }
    
    private val thumbnailCache = File(context.cacheDir, "thumbnails").apply {
        if (!exists()) mkdirs()
    }
    
    private val _generationState = MutableStateFlow(ThumbnailGenerationState())
    val generationState: StateFlow<ThumbnailGenerationState> = _generationState.asStateFlow()
    
    private val memoryCache = mutableMapOf<String, Bitmap>()
    private val retrieverPool = mutableListOf<MediaMetadataRetriever>()
    
    suspend fun generateThumbnails(
        videoUri: Uri,
        intervalSeconds: Int = DEFAULT_INTERVAL_SECONDS,
        size: Size = Size(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
    ): ThumbnailResult {
        return withContext(Dispatchers.IO) {
            val retriever = getRetriever()
            try {
                retriever.setDataSource(context, videoUri)
                
                val durationStr = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )
                val durationMs = durationStr?.toLongOrNull() ?: return@withContext ThumbnailResult.Error(
                    "Could not determine video duration"
                )
                
                val videoHash = generateVideoHash(videoUri.toString())
                val cacheDir = File(thumbnailCache, videoHash)
                if (!cacheDir.exists()) cacheDir.mkdirs()
                
                // Check if thumbnails already exist
                val existingThumbnails = loadExistingThumbnails(cacheDir, durationMs, intervalSeconds)
                if (existingThumbnails.isNotEmpty()) {
                    return@withContext ThumbnailResult.Success(
                        thumbnails = existingThumbnails,
                        duration = durationMs,
                        interval = intervalSeconds * 1000L
                    )
                }
                
                // Generate thumbnails
                val thumbnails = generateThumbnailsInternal(
                    retriever, 
                    durationMs, 
                    intervalSeconds, 
                    size, 
                    cacheDir
                )
                
                ThumbnailResult.Success(
                    thumbnails = thumbnails,
                    duration = durationMs,
                    interval = intervalSeconds * 1000L
                )
                
            } catch (e: Exception) {
                e.printStackTrace()
                ThumbnailResult.Error(e.message ?: "Failed to generate thumbnails")
            } finally {
                returnRetriever(retriever)
            }
        }
    }
    
    private suspend fun generateThumbnailsInternal(
        retriever: MediaMetadataRetriever,
        durationMs: Long,
        intervalSeconds: Int,
        size: Size,
        cacheDir: File
    ): List<ThumbnailInfo> = coroutineScope {
        val intervalMs = intervalSeconds * 1000L
        val thumbnailCount = (durationMs / intervalMs).toInt()
        val thumbnails = mutableListOf<ThumbnailInfo>()
        
        // Update state
        _generationState.value = ThumbnailGenerationState(
            isGenerating = true,
            totalThumbnails = thumbnailCount,
            generatedThumbnails = 0,
            progress = 0f
        )
        
        // Generate thumbnails in parallel batches
        val batchSize = 5
        for (i in 0 until thumbnailCount step batchSize) {
            val batch = (i until min(i + batchSize, thumbnailCount)).map { index ->
                async {
                    val timeUs = index * intervalMs * 1000
                    generateSingleThumbnail(
                        retriever, 
                        timeUs, 
                        size, 
                        cacheDir, 
                        index
                    )
                }
            }
            
            val batchResults = batch.awaitAll()
            thumbnails.addAll(batchResults.filterNotNull())
            
            // Update progress
            _generationState.value = _generationState.value.copy(
                generatedThumbnails = thumbnails.size,
                progress = thumbnails.size.toFloat() / thumbnailCount
            )
        }
        
        // Reset state
        _generationState.value = ThumbnailGenerationState()
        
        thumbnails
    }
    
    private suspend fun generateSingleThumbnail(
        retriever: MediaMetadataRetriever,
        timeUs: Long,
        size: Size,
        cacheDir: File,
        index: Int
    ): ThumbnailInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val frame = retriever.getFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: return@withContext null
                
                // Scale bitmap
                val scaledBitmap = Bitmap.createScaledBitmap(
                    frame,
                    size.width,
                    size.height,
                    true
                )
                
                // Save to file
                val file = File(cacheDir, "thumb_$index.jpg")
                FileOutputStream(file).use { out ->
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, out)
                }
                
                // Add to memory cache
                val cacheKey = "${cacheDir.name}_$index"
                memoryCache[cacheKey] = scaledBitmap
                
                // Clean memory cache if needed
                if (memoryCache.size > 50) {
                    cleanMemoryCache()
                }
                
                ThumbnailInfo(
                    index = index,
                    timestamp = timeUs / 1000,
                    filePath = file.absolutePath,
                    bitmap = scaledBitmap
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    suspend fun generateSpritemap(
        videoUri: Uri,
        intervalSeconds: Int = DEFAULT_INTERVAL_SECONDS
    ): SpritemapResult {
        return withContext(Dispatchers.IO) {
            val retriever = getRetriever()
            try {
                retriever.setDataSource(context, videoUri)
                
                val durationStr = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )
                val durationMs = durationStr?.toLongOrNull() ?: return@withContext SpritemapResult.Error(
                    "Could not determine video duration"
                )
                
                val videoHash = generateVideoHash(videoUri.toString())
                val spriteFile = File(thumbnailCache, "${videoHash}_sprite.jpg")
                
                // Check if sprite already exists
                if (spriteFile.exists()) {
                    return@withContext loadExistingSpritemap(spriteFile, durationMs, intervalSeconds)
                }
                
                // Generate sprite
                val sprite = generateSpritemapInternal(
                    retriever,
                    durationMs,
                    intervalSeconds,
                    spriteFile
                )
                
                SpritemapResult.Success(
                    spritemap = sprite,
                    filePath = spriteFile.absolutePath,
                    duration = durationMs,
                    interval = intervalSeconds * 1000L
                )
                
            } catch (e: Exception) {
                e.printStackTrace()
                SpritemapResult.Error(e.message ?: "Failed to generate spritemap")
            } finally {
                returnRetriever(retriever)
            }
        }
    }
    
    private suspend fun generateSpritemapInternal(
        retriever: MediaMetadataRetriever,
        durationMs: Long,
        intervalSeconds: Int,
        outputFile: File
    ): Bitmap = withContext(Dispatchers.Default) {
        val intervalMs = intervalSeconds * 1000L
        val thumbnailCount = min(
            (durationMs / intervalMs).toInt(),
            SPRITE_COLUMNS * SPRITE_ROWS
        )
        
        val spriteWidth = PREVIEW_WIDTH * SPRITE_COLUMNS
        val spriteHeight = PREVIEW_HEIGHT * ((thumbnailCount - 1) / SPRITE_COLUMNS + 1)
        
        val spriteBitmap = Bitmap.createBitmap(
            spriteWidth,
            spriteHeight,
            Bitmap.Config.RGB_565
        )
        val canvas = Canvas(spriteBitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        
        for (i in 0 until thumbnailCount) {
            val timeUs = i * intervalMs * 1000
            val frame = retriever.getFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
            
            frame?.let {
                val scaledFrame = Bitmap.createScaledBitmap(
                    it,
                    PREVIEW_WIDTH,
                    PREVIEW_HEIGHT,
                    true
                )
                
                val col = i % SPRITE_COLUMNS
                val row = i / SPRITE_COLUMNS
                val x = col * PREVIEW_WIDTH
                val y = row * PREVIEW_HEIGHT
                
                canvas.drawBitmap(scaledFrame, x.toFloat(), y.toFloat(), paint)
                scaledFrame.recycle()
            }
        }
        
        // Save sprite to file
        FileOutputStream(outputFile).use { out ->
            spriteBitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, out)
        }
        
        spriteBitmap
    }
    
    suspend fun getThumbnailAtTime(
        videoUri: Uri,
        timeMs: Long,
        size: Size = Size(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
    ): Bitmap? {
        return withContext(Dispatchers.IO) {
            val retriever = getRetriever()
            try {
                retriever.setDataSource(context, videoUri)
                val frame = retriever.getFrameAtTime(
                    timeMs * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                
                frame?.let {
                    Bitmap.createScaledBitmap(it, size.width, size.height, true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } finally {
                returnRetriever(retriever)
            }
        }
    }
    
    suspend fun generateAnimatedThumbnail(
        videoUri: Uri,
        startTimeMs: Long,
        durationMs: Long = 3000,
        fps: Int = 10
    ): AnimatedThumbnailResult {
        return withContext(Dispatchers.IO) {
            val retriever = getRetriever()
            try {
                retriever.setDataSource(context, videoUri)
                
                val frames = mutableListOf<Bitmap>()
                val frameInterval = 1000L / fps
                val frameCount = (durationMs / frameInterval).toInt()
                
                for (i in 0 until frameCount) {
                    val timeUs = (startTimeMs + i * frameInterval) * 1000
                    val frame = retriever.getFrameAtTime(timeUs)
                    
                    frame?.let {
                        val scaledFrame = Bitmap.createScaledBitmap(
                            it,
                            PREVIEW_WIDTH,
                            PREVIEW_HEIGHT,
                            true
                        )
                        frames.add(scaledFrame)
                    }
                }
                
                AnimatedThumbnailResult.Success(
                    frames = frames,
                    fps = fps,
                    duration = durationMs
                )
                
            } catch (e: Exception) {
                e.printStackTrace()
                AnimatedThumbnailResult.Error(e.message ?: "Failed to generate animated thumbnail")
            } finally {
                returnRetriever(retriever)
            }
        }
    }
    
    fun getCachedThumbnail(videoUri: Uri, index: Int): Bitmap? {
        val videoHash = generateVideoHash(videoUri.toString())
        val cacheKey = "${videoHash}_$index"
        
        // Check memory cache
        memoryCache[cacheKey]?.let { return it }
        
        // Check file cache
        val file = File(thumbnailCache, "$videoHash/thumb_$index.jpg")
        if (file.exists()) {
            try {
                val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                memoryCache[cacheKey] = bitmap
                return bitmap
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        return null
    }
    
    private fun loadExistingThumbnails(
        cacheDir: File,
        durationMs: Long,
        intervalSeconds: Int
    ): List<ThumbnailInfo> {
        val thumbnails = mutableListOf<ThumbnailInfo>()
        val intervalMs = intervalSeconds * 1000L
        val expectedCount = (durationMs / intervalMs).toInt()
        
        for (i in 0 until expectedCount) {
            val file = File(cacheDir, "thumb_$i.jpg")
            if (file.exists()) {
                thumbnails.add(
                    ThumbnailInfo(
                        index = i,
                        timestamp = i * intervalMs,
                        filePath = file.absolutePath,
                        bitmap = null // Load on demand
                    )
                )
            } else {
                // Missing thumbnail, need to regenerate all
                return emptyList()
            }
        }
        
        return thumbnails
    }
    
    private fun loadExistingSpritemap(
        spriteFile: File,
        durationMs: Long,
        intervalSeconds: Int
    ): SpritemapResult {
        return try {
            val bitmap = android.graphics.BitmapFactory.decodeFile(spriteFile.absolutePath)
            SpritemapResult.Success(
                spritemap = bitmap,
                filePath = spriteFile.absolutePath,
                duration = durationMs,
                interval = intervalSeconds * 1000L
            )
        } catch (e: Exception) {
            SpritemapResult.Error("Failed to load existing spritemap")
        }
    }
    
    private fun getRetriever(): MediaMetadataRetriever {
        return retrieverPool.removeFirstOrNull() ?: MediaMetadataRetriever()
    }
    
    private fun returnRetriever(retriever: MediaMetadataRetriever) {
        if (retrieverPool.size < 3) {
            retrieverPool.add(retriever)
        } else {
            retriever.release()
        }
    }
    
    private fun cleanMemoryCache() {
        // Remove oldest entries
        val entriesToRemove = memoryCache.size / 4
        memoryCache.entries.take(entriesToRemove).forEach { entry ->
            entry.value.recycle()
            memoryCache.remove(entry.key)
        }
    }
    
    fun clearCache() {
        memoryCache.values.forEach { it.recycle() }
        memoryCache.clear()
        thumbnailCache.deleteRecursively()
        thumbnailCache.mkdirs()
    }
    
    fun getCacheSize(): Long {
        return thumbnailCache.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
    
    fun trimCache(maxSizeMB: Int = MAX_CACHE_SIZE_MB) {
        val maxSizeBytes = maxSizeMB * 1024L * 1024L
        val files = thumbnailCache.walkTopDown()
            .filter { it.isFile }
            .sortedBy { it.lastModified() }
            .toList()
        
        var totalSize = files.sumOf { it.length() }
        var index = 0
        
        while (totalSize > maxSizeBytes && index < files.size) {
            val file = files[index]
            totalSize -= file.length()
            file.delete()
            index++
        }
    }
    
    private fun generateVideoHash(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
    
    fun getSpriteCoordinates(
        thumbnailIndex: Int,
        columns: Int = SPRITE_COLUMNS
    ): Rect {
        val col = thumbnailIndex % columns
        val row = thumbnailIndex / columns
        val x = col * PREVIEW_WIDTH
        val y = row * PREVIEW_HEIGHT
        
        return Rect(x, y, x + PREVIEW_WIDTH, y + PREVIEW_HEIGHT)
    }
    
    fun release() {
        retrieverPool.forEach { it.release() }
        retrieverPool.clear()
        memoryCache.values.forEach { it.recycle() }
        memoryCache.clear()
    }
}

data class ThumbnailInfo(
    val index: Int,
    val timestamp: Long, // in milliseconds
    val filePath: String,
    val bitmap: Bitmap? = null
)

data class ThumbnailGenerationState(
    val isGenerating: Boolean = false,
    val totalThumbnails: Int = 0,
    val generatedThumbnails: Int = 0,
    val progress: Float = 0f
)

sealed class ThumbnailResult {
    data class Success(
        val thumbnails: List<ThumbnailInfo>,
        val duration: Long,
        val interval: Long
    ) : ThumbnailResult()
    
    data class Error(val message: String) : ThumbnailResult()
}

sealed class SpritemapResult {
    data class Success(
        val spritemap: Bitmap,
        val filePath: String,
        val duration: Long,
        val interval: Long
    ) : SpritemapResult()
    
    data class Error(val message: String) : SpritemapResult()
}

sealed class AnimatedThumbnailResult {
    data class Success(
        val frames: List<Bitmap>,
        val fps: Int,
        val duration: Long
    ) : AnimatedThumbnailResult()
    
    data class Error(val message: String) : AnimatedThumbnailResult()
}