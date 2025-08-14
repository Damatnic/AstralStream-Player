package com.astralstream.player.bookmarks

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkManager @Inject constructor(
    private val context: Context,
    private val bookmarkDao: BookmarkDao // Assume DAO exists
) {
    
    private val _currentVideoBookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val currentVideoBookmarks: StateFlow<List<Bookmark>> = _currentVideoBookmarks.asStateFlow()
    
    private val _currentChapters = MutableStateFlow<List<Chapter>>(emptyList())
    val currentChapters: StateFlow<List<Chapter>> = _currentChapters.asStateFlow()
    
    private val _watchProgress = MutableStateFlow<WatchProgress?>(null)
    val watchProgress: StateFlow<WatchProgress?> = _watchProgress.asStateFlow()
    
    // Bookmark operations
    
    suspend fun addBookmark(
        videoUri: String,
        position: Long,
        title: String? = null,
        note: String? = null,
        thumbnail: Bitmap? = null
    ): Bookmark {
        return withContext(Dispatchers.IO) {
            val bookmark = Bookmark(
                id = generateBookmarkId(),
                videoUri = videoUri,
                position = position,
                title = title ?: formatTimestamp(position),
                note = note,
                thumbnailPath = thumbnail?.let { saveThumbnail(it, videoUri, position) },
                createdAt = Date(),
                modifiedAt = Date(),
                type = BookmarkType.USER_CREATED
            )
            
            bookmarkDao.insertBookmark(bookmark)
            refreshCurrentVideoBookmarks(videoUri)
            bookmark
        }
    }
    
    suspend fun updateBookmark(bookmark: Bookmark) {
        withContext(Dispatchers.IO) {
            val updated = bookmark.copy(modifiedAt = Date())
            bookmarkDao.updateBookmark(updated)
            refreshCurrentVideoBookmarks(bookmark.videoUri)
        }
    }
    
    suspend fun deleteBookmark(bookmarkId: String) {
        withContext(Dispatchers.IO) {
            val bookmark = bookmarkDao.getBookmark(bookmarkId)
            bookmark?.let {
                bookmarkDao.deleteBookmark(bookmarkId)
                refreshCurrentVideoBookmarks(it.videoUri)
                
                // Delete thumbnail if exists
                it.thumbnailPath?.let { path ->
                    try {
                        java.io.File(path).delete()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
    
    suspend fun getBookmarksForVideo(videoUri: String): List<Bookmark> {
        return withContext(Dispatchers.IO) {
            bookmarkDao.getBookmarksForVideo(videoUri)
        }
    }
    
    fun observeBookmarksForVideo(videoUri: String): Flow<List<Bookmark>> {
        return bookmarkDao.observeBookmarksForVideo(videoUri)
    }
    
    suspend fun getAllBookmarks(): List<Bookmark> {
        return withContext(Dispatchers.IO) {
            bookmarkDao.getAllBookmarks()
        }
    }
    
    suspend fun searchBookmarks(query: String): List<Bookmark> {
        return withContext(Dispatchers.IO) {
            bookmarkDao.searchBookmarks(query)
        }
    }
    
    private suspend fun refreshCurrentVideoBookmarks(videoUri: String) {
        _currentVideoBookmarks.value = getBookmarksForVideo(videoUri)
    }
    
    // Chapter operations
    
    suspend fun addChapter(
        videoUri: String,
        startTime: Long,
        endTime: Long,
        title: String,
        description: String? = null
    ): Chapter {
        return withContext(Dispatchers.IO) {
            val chapter = Chapter(
                id = generateChapterId(),
                videoUri = videoUri,
                startTime = startTime,
                endTime = endTime,
                title = title,
                description = description,
                createdAt = Date()
            )
            
            bookmarkDao.insertChapter(chapter)
            refreshCurrentChapters(videoUri)
            chapter
        }
    }
    
    suspend fun addChapters(videoUri: String, chapters: List<Chapter>) {
        withContext(Dispatchers.IO) {
            chapters.forEach { chapter ->
                bookmarkDao.insertChapter(
                    chapter.copy(
                        id = generateChapterId(),
                        videoUri = videoUri,
                        createdAt = Date()
                    )
                )
            }
            refreshCurrentChapters(videoUri)
        }
    }
    
    suspend fun updateChapter(chapter: Chapter) {
        withContext(Dispatchers.IO) {
            bookmarkDao.updateChapter(chapter)
            refreshCurrentChapters(chapter.videoUri)
        }
    }
    
    suspend fun deleteChapter(chapterId: String) {
        withContext(Dispatchers.IO) {
            val chapter = bookmarkDao.getChapter(chapterId)
            chapter?.let {
                bookmarkDao.deleteChapter(chapterId)
                refreshCurrentChapters(it.videoUri)
            }
        }
    }
    
    suspend fun getChaptersForVideo(videoUri: String): List<Chapter> {
        return withContext(Dispatchers.IO) {
            bookmarkDao.getChaptersForVideo(videoUri).sortedBy { it.startTime }
        }
    }
    
    fun observeChaptersForVideo(videoUri: String): Flow<List<Chapter>> {
        return bookmarkDao.observeChaptersForVideo(videoUri)
    }
    
    suspend fun importChaptersFromFile(videoUri: String, chaptersFile: String): List<Chapter> {
        return withContext(Dispatchers.IO) {
            val chapters = parseChaptersFile(chaptersFile)
            addChapters(videoUri, chapters)
            chapters
        }
    }
    
    private suspend fun refreshCurrentChapters(videoUri: String) {
        _currentChapters.value = getChaptersForVideo(videoUri)
    }
    
    fun getCurrentChapter(position: Long): Chapter? {
        return _currentChapters.value.find { 
            position >= it.startTime && position < it.endTime 
        }
    }
    
    fun getNextChapter(position: Long): Chapter? {
        return _currentChapters.value
            .filter { it.startTime > position }
            .minByOrNull { it.startTime }
    }
    
    fun getPreviousChapter(position: Long): Chapter? {
        return _currentChapters.value
            .filter { it.startTime < position }
            .maxByOrNull { it.startTime }
    }
    
    // Watch progress tracking
    
    suspend fun saveWatchProgress(
        videoUri: String,
        position: Long,
        duration: Long,
        videoTitle: String? = null
    ) {
        withContext(Dispatchers.IO) {
            val existing = bookmarkDao.getWatchProgress(videoUri)
            
            val progress = if (existing != null) {
                existing.copy(
                    position = position,
                    duration = duration,
                    lastWatched = Date(),
                    watchCount = existing.watchCount + if (position >= duration * 0.9f) 1 else 0,
                    completed = position >= duration * 0.95f
                )
            } else {
                WatchProgress(
                    id = generateProgressId(),
                    videoUri = videoUri,
                    videoTitle = videoTitle ?: extractVideoTitle(videoUri),
                    position = position,
                    duration = duration,
                    lastWatched = Date(),
                    watchCount = if (position >= duration * 0.9f) 1 else 0,
                    completed = position >= duration * 0.95f
                )
            }
            
            if (existing != null) {
                bookmarkDao.updateWatchProgress(progress)
            } else {
                bookmarkDao.insertWatchProgress(progress)
            }
            
            _watchProgress.value = progress
        }
    }
    
    suspend fun getWatchProgress(videoUri: String): WatchProgress? {
        return withContext(Dispatchers.IO) {
            bookmarkDao.getWatchProgress(videoUri).also {
                _watchProgress.value = it
            }
        }
    }
    
    suspend fun getAllWatchProgress(): List<WatchProgress> {
        return withContext(Dispatchers.IO) {
            bookmarkDao.getAllWatchProgress()
        }
    }
    
    suspend fun getRecentlyWatched(limit: Int = 20): List<WatchProgress> {
        return withContext(Dispatchers.IO) {
            bookmarkDao.getRecentlyWatched(limit)
        }
    }
    
    suspend fun getContinueWatching(): List<WatchProgress> {
        return withContext(Dispatchers.IO) {
            bookmarkDao.getContinueWatching()
        }
    }
    
    suspend fun clearWatchProgress(videoUri: String) {
        withContext(Dispatchers.IO) {
            bookmarkDao.deleteWatchProgress(videoUri)
            if (_watchProgress.value?.videoUri == videoUri) {
                _watchProgress.value = null
            }
        }
    }
    
    suspend fun markAsWatched(videoUri: String) {
        withContext(Dispatchers.IO) {
            val progress = bookmarkDao.getWatchProgress(videoUri)
            progress?.let {
                val updated = it.copy(
                    completed = true,
                    position = it.duration,
                    lastWatched = Date()
                )
                bookmarkDao.updateWatchProgress(updated)
            }
        }
    }
    
    // Auto-bookmarking
    
    suspend fun createAutoBookmark(
        videoUri: String,
        position: Long,
        type: BookmarkType,
        title: String? = null
    ) {
        withContext(Dispatchers.IO) {
            // Check if similar auto-bookmark exists
            val existing = bookmarkDao.getBookmarksForVideo(videoUri)
                .find { it.type == type && kotlin.math.abs(it.position - position) < 5000 }
            
            if (existing == null) {
                val bookmark = Bookmark(
                    id = generateBookmarkId(),
                    videoUri = videoUri,
                    position = position,
                    title = title ?: when (type) {
                        BookmarkType.SCENE_CHANGE -> "Scene Change"
                        BookmarkType.SUBTITLE_START -> "Subtitle Start"
                        BookmarkType.AUDIO_PEAK -> "Audio Peak"
                        else -> formatTimestamp(position)
                    },
                    createdAt = Date(),
                    modifiedAt = Date(),
                    type = type
                )
                
                bookmarkDao.insertBookmark(bookmark)
                refreshCurrentVideoBookmarks(videoUri)
            }
        }
    }
    
    // Utilities
    
    private fun generateBookmarkId(): String {
        return "bookmark_${System.currentTimeMillis()}_${kotlin.random.Random.nextInt(1000)}"
    }
    
    private fun generateChapterId(): String {
        return "chapter_${System.currentTimeMillis()}_${kotlin.random.Random.nextInt(1000)}"
    }
    
    private fun generateProgressId(): String {
        return "progress_${System.currentTimeMillis()}_${kotlin.random.Random.nextInt(1000)}"
    }
    
    private fun formatTimestamp(positionMs: Long): String {
        val totalSeconds = positionMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
    
    private fun extractVideoTitle(uri: String): String {
        return try {
            java.io.File(uri).nameWithoutExtension
        } catch (e: Exception) {
            uri.substringAfterLast('/').substringBeforeLast('.')
        }
    }
    
    private suspend fun saveThumbnail(bitmap: Bitmap, videoUri: String, position: Long): String {
        return withContext(Dispatchers.IO) {
            val thumbnailDir = java.io.File(context.cacheDir, "bookmark_thumbnails")
            if (!thumbnailDir.exists()) thumbnailDir.mkdirs()
            
            val hash = videoUri.hashCode().toString()
            val file = java.io.File(thumbnailDir, "${hash}_${position}.jpg")
            
            java.io.FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            
            file.absolutePath
        }
    }
    
    private fun parseChaptersFile(content: String): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        val lines = content.lines()
        
        // Parse common chapter formats (e.g., YouTube format)
        // Format: 00:00 Chapter Title
        val pattern = Regex("(\\d{1,2}:\\d{2}(?::\\d{2})?)\\s+(.+)")
        
        lines.forEachIndexed { index, line ->
            pattern.matchEntire(line.trim())?.let { match ->
                val timeStr = match.groupValues[1]
                val title = match.groupValues[2]
                
                val time = parseTimeString(timeStr)
                
                // Find end time from next chapter or use Long.MAX_VALUE for last
                val endTime = if (index < lines.size - 1) {
                    lines.subList(index + 1, lines.size)
                        .firstNotNullOfOrNull { nextLine ->
                            pattern.matchEntire(nextLine.trim())?.let {
                                parseTimeString(it.groupValues[1])
                            }
                        } ?: Long.MAX_VALUE
                } else {
                    Long.MAX_VALUE
                }
                
                chapters.add(
                    Chapter(
                        id = "",
                        videoUri = "",
                        startTime = time,
                        endTime = endTime,
                        title = title,
                        createdAt = Date()
                    )
                )
            }
        }
        
        return chapters
    }
    
    private fun parseTimeString(timeStr: String): Long {
        val parts = timeStr.split(":")
        return when (parts.size) {
            2 -> { // MM:SS
                val minutes = parts[0].toLongOrNull() ?: 0
                val seconds = parts[1].toLongOrNull() ?: 0
                (minutes * 60 + seconds) * 1000
            }
            3 -> { // HH:MM:SS
                val hours = parts[0].toLongOrNull() ?: 0
                val minutes = parts[1].toLongOrNull() ?: 0
                val seconds = parts[2].toLongOrNull() ?: 0
                (hours * 3600 + minutes * 60 + seconds) * 1000
            }
            else -> 0
        }
    }
    
    suspend fun exportBookmarks(videoUri: String): String {
        return withContext(Dispatchers.IO) {
            val bookmarks = getBookmarksForVideo(videoUri)
            val chapters = getChaptersForVideo(videoUri)
            
            buildString {
                appendLine("# Video Bookmarks and Chapters")
                appendLine("# Video: $videoUri")
                appendLine("# Exported: ${Date()}")
                appendLine()
                
                if (chapters.isNotEmpty()) {
                    appendLine("## Chapters")
                    chapters.forEach { chapter ->
                        appendLine("${formatTimestamp(chapter.startTime)} ${chapter.title}")
                        chapter.description?.let { 
                            appendLine("  $it")
                        }
                    }
                    appendLine()
                }
                
                if (bookmarks.isNotEmpty()) {
                    appendLine("## Bookmarks")
                    bookmarks.forEach { bookmark ->
                        appendLine("${formatTimestamp(bookmark.position)} ${bookmark.title}")
                        bookmark.note?.let {
                            appendLine("  Note: $it")
                        }
                    }
                }
            }
        }
    }
}

data class Bookmark(
    val id: String,
    val videoUri: String,
    val position: Long, // Position in milliseconds
    val title: String,
    val note: String? = null,
    val thumbnailPath: String? = null,
    val createdAt: Date,
    val modifiedAt: Date,
    val type: BookmarkType = BookmarkType.USER_CREATED
)

data class Chapter(
    val id: String,
    val videoUri: String,
    val startTime: Long, // in milliseconds
    val endTime: Long, // in milliseconds
    val title: String,
    val description: String? = null,
    val thumbnailPath: String? = null,
    val createdAt: Date
) {
    val duration: Long
        get() = endTime - startTime
}

data class WatchProgress(
    val id: String,
    val videoUri: String,
    val videoTitle: String,
    val position: Long, // Current position in milliseconds
    val duration: Long, // Total duration in milliseconds
    val lastWatched: Date,
    val watchCount: Int = 0,
    val completed: Boolean = false
) {
    val progressPercentage: Float
        get() = if (duration > 0) (position.toFloat() / duration * 100) else 0f
    
    val remainingTime: Long
        get() = duration - position
}

enum class BookmarkType {
    USER_CREATED,    // Manually created by user
    SCENE_CHANGE,    // Auto-detected scene change
    SUBTITLE_START,  // Start of subtitle segment
    AUDIO_PEAK,      // Significant audio event
    AUTO_SAVE        // Automatic progress save
}

// DAO interface (to be implemented with Room)
interface BookmarkDao {
    // Bookmark operations
    suspend fun insertBookmark(bookmark: Bookmark)
    suspend fun updateBookmark(bookmark: Bookmark)
    suspend fun deleteBookmark(bookmarkId: String)
    suspend fun getBookmark(bookmarkId: String): Bookmark?
    suspend fun getBookmarksForVideo(videoUri: String): List<Bookmark>
    fun observeBookmarksForVideo(videoUri: String): Flow<List<Bookmark>>
    suspend fun getAllBookmarks(): List<Bookmark>
    suspend fun searchBookmarks(query: String): List<Bookmark>
    
    // Chapter operations
    suspend fun insertChapter(chapter: Chapter)
    suspend fun updateChapter(chapter: Chapter)
    suspend fun deleteChapter(chapterId: String)
    suspend fun getChapter(chapterId: String): Chapter?
    suspend fun getChaptersForVideo(videoUri: String): List<Chapter>
    fun observeChaptersForVideo(videoUri: String): Flow<List<Chapter>>
    
    // Watch progress operations
    suspend fun insertWatchProgress(progress: WatchProgress)
    suspend fun updateWatchProgress(progress: WatchProgress)
    suspend fun deleteWatchProgress(videoUri: String)
    suspend fun getWatchProgress(videoUri: String): WatchProgress?
    suspend fun getAllWatchProgress(): List<WatchProgress>
    suspend fun getRecentlyWatched(limit: Int): List<WatchProgress>
    suspend fun getContinueWatching(): List<WatchProgress>
}