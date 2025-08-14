package com.astralstream.player.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astralstream.player.data.database.entities.MediaEntity
import com.astralstream.player.data.repository.MediaRepository
import com.astralstream.player.thumbnail.ThumbnailGenerator
import com.astralstream.player.thumbnail.AsyncThumbnailLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class FileExplorerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val thumbnailGenerator: ThumbnailGenerator,
    private val asyncThumbnailLoader: AsyncThumbnailLoader
) : ViewModel() {

    data class FileItem(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val size: Long = 0,
        val lastModified: Long = 0,
        val mimeType: String? = null,
        val thumbnailUri: Uri? = null,
        val thumbnailBitmap: Bitmap? = null,
        val duration: Long = 0,
        val videoInfo: VideoInfo? = null
    )

    data class VideoInfo(
        val width: Int,
        val height: Int,
        val duration: Long,
        val bitrate: Long? = null
    )

    data class UiState(
        val currentPath: String = Environment.getExternalStorageDirectory().absolutePath,
        val files: List<FileItem> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
        val searchQuery: String = "",
        val sortOrder: SortOrder = SortOrder.NAME_ASC,
        val showHiddenFiles: Boolean = false,
        val fileTypeFilter: FileTypeFilter = FileTypeFilter.VIDEOS_ONLY,
        val navigationHistory: List<String> = listOf(Environment.getExternalStorageDirectory().absolutePath),
        val canGoBack: Boolean = false,
        val quickAccessPaths: List<QuickAccessPath> = getDefaultQuickAccessPaths()
    )

    data class QuickAccessPath(
        val name: String,
        val path: String,
        val icon: String
    )

    enum class SortOrder {
        NAME_ASC, NAME_DESC,
        SIZE_ASC, SIZE_DESC,
        DATE_ASC, DATE_DESC,
        TYPE_ASC, TYPE_DESC
    }

    enum class FileTypeFilter {
        ALL, VIDEOS_ONLY, FOLDERS_ONLY
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val supportedVideoExtensions = setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm",
        "m4v", "mpg", "mpeg", "3gp", "3g2", "m2ts", "mts",
        "ts", "vob", "ogv", "rm", "rmvb", "asf", "divx", "f4v"
    )

    init {
        loadFiles(_uiState.value.currentPath)
    }

    fun navigateToPath(path: String) {
        viewModelScope.launch {
            val currentHistory = _uiState.value.navigationHistory.toMutableList()
            if (currentHistory.lastOrNull() != path) {
                currentHistory.add(path)
            }
            _uiState.value = _uiState.value.copy(
                currentPath = path,
                navigationHistory = currentHistory,
                canGoBack = currentHistory.size > 1
            )
            loadFiles(path)
        }
    }

    fun navigateUp() {
        val currentPath = _uiState.value.currentPath
        val parentPath = File(currentPath).parent
        if (parentPath != null && File(parentPath).canRead()) {
            navigateToPath(parentPath)
        }
    }

    fun navigateBack() {
        val history = _uiState.value.navigationHistory
        if (history.size > 1) {
            val newHistory = history.dropLast(1)
            val previousPath = newHistory.last()
            _uiState.value = _uiState.value.copy(
                currentPath = previousPath,
                navigationHistory = newHistory,
                canGoBack = newHistory.size > 1
            )
            loadFiles(previousPath)
        }
    }

    fun toggleHiddenFiles() {
        _uiState.value = _uiState.value.copy(
            showHiddenFiles = !_uiState.value.showHiddenFiles
        )
        refreshCurrentDirectory()
    }

    fun setSortOrder(sortOrder: SortOrder) {
        _uiState.value = _uiState.value.copy(sortOrder = sortOrder)
        refreshCurrentDirectory()
    }

    fun setFileTypeFilter(filter: FileTypeFilter) {
        _uiState.value = _uiState.value.copy(fileTypeFilter = filter)
        refreshCurrentDirectory()
    }

    fun searchFiles(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        if (query.isEmpty()) {
            refreshCurrentDirectory()
        } else {
            viewModelScope.launch {
                val filteredFiles = withContext(Dispatchers.IO) {
                    searchInDirectory(File(_uiState.value.currentPath), query)
                }
                _uiState.value = _uiState.value.copy(files = filteredFiles)
            }
        }
    }

    fun refreshCurrentDirectory() {
        loadFiles(_uiState.value.currentPath)
    }

    private fun loadFiles(path: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val files = withContext(Dispatchers.IO) {
                    val directory = File(path)
                    if (!directory.exists() || !directory.canRead()) {
                        throw IllegalStateException("Cannot read directory: $path")
                    }

                    val fileList = directory.listFiles()?.mapNotNull { file ->
                        if (!_uiState.value.showHiddenFiles && file.name.startsWith(".")) {
                            return@mapNotNull null
                        }

                        when (_uiState.value.fileTypeFilter) {
                            FileTypeFilter.VIDEOS_ONLY -> {
                                if (file.isDirectory || isVideoFile(file)) {
                                    // Use basic item for initial load, thumbnails load async
                                    createBasicFileItem(file)
                                } else null
                            }
                            FileTypeFilter.FOLDERS_ONLY -> {
                                if (file.isDirectory) createBasicFileItem(file) else null
                            }
                            FileTypeFilter.ALL -> createBasicFileItem(file)
                        }
                    } ?: emptyList()

                    sortFiles(fileList)
                }

                _uiState.value = _uiState.value.copy(
                    files = files,
                    isLoading = false
                )
                
                // Load thumbnails asynchronously after showing the list
                loadThumbnailsAsync(files.filter { !it.isDirectory && isVideoFile(File(it.path)) })
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
    
    private fun loadThumbnailsAsync(videoFiles: List<FileItem>) {
        videoFiles.forEach { fileItem ->
            asyncThumbnailLoader.loadThumbnail(
                videoPath = fileItem.path,
                onSuccess = { bitmap ->
                    // Update the file item with loaded thumbnail
                    val currentFiles = _uiState.value.files.toMutableList()
                    val index = currentFiles.indexOfFirst { it.path == fileItem.path }
                    if (index != -1) {
                        currentFiles[index] = currentFiles[index].copy(thumbnailBitmap = bitmap)
                        _uiState.value = _uiState.value.copy(files = currentFiles)
                    }
                },
                onError = { /* Thumbnail failed to load, keep default */ }
            )
        }
    }

    private fun createBasicFileItem(file: File): FileItem {
        val isVideo = isVideoFile(file)
        var videoInfo: VideoInfo? = null
        
        if (isVideo) {
            // Get basic metadata without thumbnail for performance
            videoInfo = getVideoMetadataDirectly(file.absolutePath) ?: getVideoMetadata(file.absolutePath)
        }
        
        return FileItem(
            name = file.name,
            path = file.absolutePath,
            isDirectory = file.isDirectory,
            size = if (file.isFile) file.length() else 0,
            lastModified = file.lastModified(),
            mimeType = if (isVideo) getMimeType(file) else null,
            thumbnailUri = null,
            thumbnailBitmap = null,
            duration = videoInfo?.duration ?: 0,
            videoInfo = videoInfo
        )
    }
    
    private suspend fun createFileItem(file: File): FileItem {
        val isVideo = isVideoFile(file)
        var thumbnailUri: Uri? = null
        var thumbnailBitmap: Bitmap? = null
        var videoInfo: VideoInfo? = null

        if (isVideo) {
            // Try multiple methods to get thumbnail, prioritizing reliability
            thumbnailBitmap = withContext(Dispatchers.IO) {
                // Method 1: Try direct MediaMetadataRetriever with better error handling
                getVideoThumbnailEnhanced(file.absolutePath)
                    // Method 2: Try ThumbnailGenerator if first fails
                    ?: try {
                        thumbnailGenerator.getThumbnailAtTime(
                            file.toUri(),
                            1000, // Try 1 second mark for better reliability
                            Size(320, 180)
                        )
                    } catch (e: Exception) {
                        null
                    }
                    // Method 3: Try another position if both fail
                    ?: getVideoThumbnailAtPosition(file.absolutePath, 0)
            }
            
            // If still no bitmap, try MediaStore as last resort
            if (thumbnailBitmap == null) {
                thumbnailUri = getVideoThumbnail(file.absolutePath)
            }
            
            // Get video metadata with enhanced method first
            videoInfo = getVideoMetadataDirectly(file.absolutePath) ?: getVideoMetadata(file.absolutePath)
        }

        return FileItem(
            name = file.name,
            path = file.absolutePath,
            isDirectory = file.isDirectory,
            size = if (file.isFile) file.length() else 0,
            lastModified = file.lastModified(),
            mimeType = if (isVideo) getMimeType(file) else null,
            thumbnailUri = thumbnailUri,
            thumbnailBitmap = thumbnailBitmap,
            duration = videoInfo?.duration ?: 0,
            videoInfo = videoInfo
        )
    }

    private fun isVideoFile(file: File): Boolean {
        if (file.isDirectory) return false
        val extension = file.extension.lowercase(Locale.getDefault())
        return extension in supportedVideoExtensions
    }

    private fun getMimeType(file: File): String {
        val extension = file.extension.lowercase(Locale.getDefault())
        return when (extension) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "flv" -> "video/x-flv"
            "3gp" -> "video/3gpp"
            else -> "video/*"
        }
    }

    private fun getVideoThumbnail(path: String): Uri? {
        return try {
            val projection = arrayOf(MediaStore.Video.Media._ID)
            val selection = "${MediaStore.Video.Media.DATA} = ?"
            val selectionArgs = arrayOf(path)
            
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getVideoThumbnailDirect(path: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val bitmap = retriever.getFrameAtTime(2000000) // 2 seconds in microseconds
            bitmap?.let {
                Bitmap.createScaledBitmap(it, 320, 180, true)
            }
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
    
    private fun getVideoThumbnailEnhanced(path: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            
            // Try to get duration first to validate the file
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0L
            
            // Choose a smart frame position based on duration
            val framePosition = when {
                duration <= 0L -> 0L // Invalid duration, try first frame
                duration < 5000000L -> 0L // Very short video, use first frame
                duration < 30000000L -> 2000000L // Short video, use 2 seconds
                else -> 5000000L // Longer video, use 5 seconds
            }
            
            // Try to get frame with OPTION_CLOSEST_SYNC for better reliability
            val bitmap = retriever.getFrameAtTime(
                framePosition,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: retriever.frameAtTime // Fallback to any frame
            
            bitmap?.let {
                // Create high-quality scaled bitmap
                val scaledBitmap = Bitmap.createScaledBitmap(it, 320, 180, true)
                if (it != scaledBitmap) {
                    it.recycle() // Recycle original if different from scaled
                }
                scaledBitmap
            }
        } catch (e: IllegalArgumentException) {
            // File format not supported
            null
        } catch (e: RuntimeException) {
            // Security exception or other runtime issue
            null
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore release errors
            }
        }
    }
    
    private fun getVideoThumbnailAtPosition(path: String, positionUs: Long): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val bitmap = retriever.getFrameAtTime(positionUs, MediaMetadataRetriever.OPTION_CLOSEST)
            bitmap?.let {
                Bitmap.createScaledBitmap(it, 320, 180, true)
            }
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    private fun getVideoMetadataDirectly(path: String): VideoInfo? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
            
            VideoInfo(
                width = width,
                height = height,
                duration = duration,
                bitrate = bitrate
            )
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun getVideoMetadata(path: String): VideoInfo? {
        return try {
            val projection = arrayOf(
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.BITRATE
            )
            val selection = "${MediaStore.Video.Media.DATA} = ?"
            val selectionArgs = arrayOf(path)

            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    VideoInfo(
                        width = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)),
                        height = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)),
                        duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)),
                        bitrate = try {
                            cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BITRATE))
                        } catch (e: Exception) {
                            null
                        }
                    )
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun sortFiles(files: List<FileItem>): List<FileItem> {
        val (directories, regularFiles) = files.partition { it.isDirectory }
        
        val sortedDirectories = when (_uiState.value.sortOrder) {
            SortOrder.NAME_ASC -> directories.sortedBy { it.name.lowercase() }
            SortOrder.NAME_DESC -> directories.sortedByDescending { it.name.lowercase() }
            SortOrder.SIZE_ASC -> directories.sortedBy { it.size }
            SortOrder.SIZE_DESC -> directories.sortedByDescending { it.size }
            SortOrder.DATE_ASC -> directories.sortedBy { it.lastModified }
            SortOrder.DATE_DESC -> directories.sortedByDescending { it.lastModified }
            SortOrder.TYPE_ASC, SortOrder.TYPE_DESC -> directories.sortedBy { it.name.lowercase() }
        }

        val sortedFiles = when (_uiState.value.sortOrder) {
            SortOrder.NAME_ASC -> regularFiles.sortedBy { it.name.lowercase() }
            SortOrder.NAME_DESC -> regularFiles.sortedByDescending { it.name.lowercase() }
            SortOrder.SIZE_ASC -> regularFiles.sortedBy { it.size }
            SortOrder.SIZE_DESC -> regularFiles.sortedByDescending { it.size }
            SortOrder.DATE_ASC -> regularFiles.sortedBy { it.lastModified }
            SortOrder.DATE_DESC -> regularFiles.sortedByDescending { it.lastModified }
            SortOrder.TYPE_ASC -> regularFiles.sortedBy { it.name.substringAfterLast('.', "") }
            SortOrder.TYPE_DESC -> regularFiles.sortedByDescending { it.name.substringAfterLast('.', "") }
        }

        return sortedDirectories + sortedFiles
    }

    private fun searchInDirectory(directory: File, query: String): List<FileItem> {
        val results = mutableListOf<FileItem>()
        val lowercaseQuery = query.lowercase(Locale.getDefault())

        fun searchRecursive(dir: File, depth: Int = 0) {
            if (depth > 5) return // Limit search depth

            dir.listFiles()?.forEach { file ->
                if (file.name.lowercase(Locale.getDefault()).contains(lowercaseQuery)) {
                    if (_uiState.value.fileTypeFilter == FileTypeFilter.VIDEOS_ONLY) {
                        if (file.isDirectory || isVideoFile(file)) {
                            // Create basic file item without thumbnail for search results
                            results.add(createBasicFileItem(file))
                        }
                    } else {
                        results.add(createBasicFileItem(file))
                    }
                }
                
                if (file.isDirectory && file.canRead()) {
                    searchRecursive(file, depth + 1)
                }
            }
        }

        searchRecursive(directory)
        return sortFiles(results)
    }

    fun playVideo(fileItem: FileItem) {
        if (!fileItem.isDirectory && isVideoFile(File(fileItem.path))) {
            viewModelScope.launch {
                val mediaEntity = MediaEntity(
                    id = 0, // Room will auto-generate the ID
                    title = fileItem.name,
                    path = fileItem.path,
                    duration = fileItem.duration,
                    size = fileItem.size,
                    mimeType = fileItem.mimeType ?: "video/*",
                    width = fileItem.videoInfo?.width ?: 0,
                    height = fileItem.videoInfo?.height ?: 0,
                    dateAdded = System.currentTimeMillis(),
                    dateModified = fileItem.lastModified,
                    thumbnailPath = null
                )
                
                val existingMedia = withContext(Dispatchers.IO) {
                    mediaRepository.searchMedia(fileItem.path).collect { list ->
                        list.firstOrNull { it.path == fileItem.path }
                    }
                    null
                }
                
                if (existingMedia == null) {
                    mediaRepository.insertMedia(mediaEntity)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        asyncThumbnailLoader.release()
    }
    
    companion object {
        private fun getDefaultQuickAccessPaths(): List<QuickAccessPath> {
            return listOf(
                QuickAccessPath(
                    name = "Internal Storage",
                    path = Environment.getExternalStorageDirectory().absolutePath,
                    icon = "storage"
                ),
                QuickAccessPath(
                    name = "Downloads",
                    path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
                    icon = "download"
                ),
                QuickAccessPath(
                    name = "Movies",
                    path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).absolutePath,
                    icon = "movie"
                ),
                QuickAccessPath(
                    name = "DCIM",
                    path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath,
                    icon = "camera"
                ),
                QuickAccessPath(
                    name = "Documents",
                    path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath,
                    icon = "document"
                )
            )
        }
    }
}