package com.astralstream.player.playlist

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class PlaylistManager @Inject constructor(
    private val context: Context,
    private val playlistDao: PlaylistDao // Assume DAO exists
) {
    
    companion object {
        private const val M3U_HEADER = "#EXTM3U"
        private const val M3U_INFO = "#EXTINF:"
        private const val PLS_HEADER = "[playlist]"
        private const val XSPF_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
    }
    
    private val _currentPlaylist = MutableStateFlow<Playlist?>(null)
    val currentPlaylist: StateFlow<Playlist?> = _currentPlaylist.asStateFlow()
    
    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()
    
    private val _playbackMode = MutableStateFlow(PlaybackMode.NORMAL)
    val playbackMode: StateFlow<PlaybackMode> = _playbackMode.asStateFlow()
    
    private val _shuffledIndices = mutableListOf<Int>()
    private var originalIndex = 0
    
    // Playlist CRUD operations
    
    suspend fun createPlaylist(
        name: String,
        description: String? = null,
        type: PlaylistType = PlaylistType.CUSTOM
    ): Playlist {
        return withContext(Dispatchers.IO) {
            val playlist = Playlist(
                id = generatePlaylistId(),
                name = name,
                description = description,
                type = type,
                createdAt = Date(),
                modifiedAt = Date(),
                items = emptyList(),
                thumbnailUrl = null,
                duration = 0L,
                itemCount = 0
            )
            
            playlistDao.insertPlaylist(playlist)
            playlist
        }
    }
    
    suspend fun getAllPlaylists(): List<Playlist> {
        return withContext(Dispatchers.IO) {
            playlistDao.getAllPlaylists()
        }
    }
    
    fun observePlaylists(): Flow<List<Playlist>> {
        return playlistDao.observePlaylists()
    }
    
    suspend fun getPlaylist(playlistId: String): Playlist? {
        return withContext(Dispatchers.IO) {
            playlistDao.getPlaylist(playlistId)
        }
    }
    
    suspend fun updatePlaylist(playlist: Playlist) {
        withContext(Dispatchers.IO) {
            val updated = playlist.copy(modifiedAt = Date())
            playlistDao.updatePlaylist(updated)
            
            if (_currentPlaylist.value?.id == playlist.id) {
                _currentPlaylist.value = updated
            }
        }
    }
    
    suspend fun deletePlaylist(playlistId: String) {
        withContext(Dispatchers.IO) {
            playlistDao.deletePlaylist(playlistId)
            
            if (_currentPlaylist.value?.id == playlistId) {
                _currentPlaylist.value = null
                _currentIndex.value = 0
            }
        }
    }
    
    suspend fun renamePlaylist(playlistId: String, newName: String) {
        withContext(Dispatchers.IO) {
            val playlist = playlistDao.getPlaylist(playlistId)
            playlist?.let {
                val updated = it.copy(name = newName, modifiedAt = Date())
                playlistDao.updatePlaylist(updated)
            }
        }
    }
    
    // Playlist item management
    
    suspend fun addToPlaylist(
        playlistId: String,
        item: PlaylistItem
    ) {
        withContext(Dispatchers.IO) {
            val playlist = playlistDao.getPlaylist(playlistId) ?: return@withContext
            
            val updatedItems = playlist.items + item
            val updatedPlaylist = playlist.copy(
                items = updatedItems,
                itemCount = updatedItems.size,
                duration = updatedItems.sumOf { it.duration },
                modifiedAt = Date()
            )
            
            playlistDao.updatePlaylist(updatedPlaylist)
            
            if (_currentPlaylist.value?.id == playlistId) {
                _currentPlaylist.value = updatedPlaylist
            }
        }
    }
    
    suspend fun addMultipleToPlaylist(
        playlistId: String,
        items: List<PlaylistItem>
    ) {
        withContext(Dispatchers.IO) {
            val playlist = playlistDao.getPlaylist(playlistId) ?: return@withContext
            
            val updatedItems = playlist.items + items
            val updatedPlaylist = playlist.copy(
                items = updatedItems,
                itemCount = updatedItems.size,
                duration = updatedItems.sumOf { it.duration },
                modifiedAt = Date()
            )
            
            playlistDao.updatePlaylist(updatedPlaylist)
            
            if (_currentPlaylist.value?.id == playlistId) {
                _currentPlaylist.value = updatedPlaylist
            }
        }
    }
    
    suspend fun removeFromPlaylist(
        playlistId: String,
        itemId: String
    ) {
        withContext(Dispatchers.IO) {
            val playlist = playlistDao.getPlaylist(playlistId) ?: return@withContext
            
            val updatedItems = playlist.items.filter { it.id != itemId }
            val updatedPlaylist = playlist.copy(
                items = updatedItems,
                itemCount = updatedItems.size,
                duration = updatedItems.sumOf { it.duration },
                modifiedAt = Date()
            )
            
            playlistDao.updatePlaylist(updatedPlaylist)
            
            if (_currentPlaylist.value?.id == playlistId) {
                _currentPlaylist.value = updatedPlaylist
                
                // Adjust current index if needed
                val removedIndex = playlist.items.indexOfFirst { it.id == itemId }
                if (removedIndex >= 0 && removedIndex <= _currentIndex.value) {
                    _currentIndex.value = maxOf(0, _currentIndex.value - 1)
                }
            }
        }
    }
    
    suspend fun reorderPlaylistItems(
        playlistId: String,
        fromIndex: Int,
        toIndex: Int
    ) {
        withContext(Dispatchers.IO) {
            val playlist = playlistDao.getPlaylist(playlistId) ?: return@withContext
            
            val items = playlist.items.toMutableList()
            val item = items.removeAt(fromIndex)
            items.add(toIndex, item)
            
            val updatedPlaylist = playlist.copy(
                items = items,
                modifiedAt = Date()
            )
            
            playlistDao.updatePlaylist(updatedPlaylist)
            
            if (_currentPlaylist.value?.id == playlistId) {
                _currentPlaylist.value = updatedPlaylist
                
                // Adjust current index
                when {
                    _currentIndex.value == fromIndex -> _currentIndex.value = toIndex
                    _currentIndex.value in (fromIndex + 1)..toIndex -> _currentIndex.value--
                    _currentIndex.value in toIndex until fromIndex -> _currentIndex.value++
                }
            }
        }
    }
    
    suspend fun clearPlaylist(playlistId: String) {
        withContext(Dispatchers.IO) {
            val playlist = playlistDao.getPlaylist(playlistId) ?: return@withContext
            
            val updatedPlaylist = playlist.copy(
                items = emptyList(),
                itemCount = 0,
                duration = 0L,
                modifiedAt = Date()
            )
            
            playlistDao.updatePlaylist(updatedPlaylist)
            
            if (_currentPlaylist.value?.id == playlistId) {
                _currentPlaylist.value = updatedPlaylist
                _currentIndex.value = 0
            }
        }
    }
    
    // Playback control
    
    fun loadPlaylist(playlist: Playlist, startIndex: Int = 0) {
        _currentPlaylist.value = playlist
        _currentIndex.value = startIndex.coerceIn(0, playlist.items.size - 1)
        
        if (_playbackMode.value == PlaybackMode.SHUFFLE) {
            generateShuffleOrder()
        }
    }
    
    fun getCurrentItem(): PlaylistItem? {
        val playlist = _currentPlaylist.value ?: return null
        val index = if (_playbackMode.value == PlaybackMode.SHUFFLE && _shuffledIndices.isNotEmpty()) {
            _shuffledIndices.getOrNull(_currentIndex.value) ?: return null
        } else {
            _currentIndex.value
        }
        
        return playlist.items.getOrNull(index)
    }
    
    fun getNextItem(): PlaylistItem? {
        val playlist = _currentPlaylist.value ?: return null
        if (playlist.items.isEmpty()) return null
        
        val nextIndex = when (_playbackMode.value) {
            PlaybackMode.NORMAL -> {
                if (_currentIndex.value < playlist.items.size - 1) {
                    _currentIndex.value + 1
                } else {
                    null // End of playlist
                }
            }
            PlaybackMode.REPEAT_ALL -> {
                (_currentIndex.value + 1) % playlist.items.size
            }
            PlaybackMode.REPEAT_ONE -> {
                _currentIndex.value // Same item
            }
            PlaybackMode.SHUFFLE -> {
                if (_shuffledIndices.isEmpty()) {
                    generateShuffleOrder()
                }
                if (_currentIndex.value < _shuffledIndices.size - 1) {
                    _currentIndex.value + 1
                } else if (_playbackMode.value == PlaybackMode.SHUFFLE) {
                    // Reshuffle and start over
                    generateShuffleOrder()
                    0
                } else {
                    null
                }
            }
        }
        
        return nextIndex?.let { index ->
            val actualIndex = if (_playbackMode.value == PlaybackMode.SHUFFLE && _shuffledIndices.isNotEmpty()) {
                _shuffledIndices[index]
            } else {
                index
            }
            playlist.items.getOrNull(actualIndex)
        }
    }
    
    fun getPreviousItem(): PlaylistItem? {
        val playlist = _currentPlaylist.value ?: return null
        if (playlist.items.isEmpty()) return null
        
        val prevIndex = when (_playbackMode.value) {
            PlaybackMode.NORMAL -> {
                if (_currentIndex.value > 0) {
                    _currentIndex.value - 1
                } else {
                    null // Beginning of playlist
                }
            }
            PlaybackMode.REPEAT_ALL -> {
                if (_currentIndex.value > 0) {
                    _currentIndex.value - 1
                } else {
                    playlist.items.size - 1
                }
            }
            PlaybackMode.REPEAT_ONE -> {
                _currentIndex.value // Same item
            }
            PlaybackMode.SHUFFLE -> {
                if (_shuffledIndices.isEmpty()) {
                    generateShuffleOrder()
                }
                if (_currentIndex.value > 0) {
                    _currentIndex.value - 1
                } else {
                    null
                }
            }
        }
        
        return prevIndex?.let { index ->
            val actualIndex = if (_playbackMode.value == PlaybackMode.SHUFFLE && _shuffledIndices.isNotEmpty()) {
                _shuffledIndices[index]
            } else {
                index
            }
            playlist.items.getOrNull(actualIndex)
        }
    }
    
    fun skipToNext(): Boolean {
        val nextItem = getNextItem() ?: return false
        
        _currentIndex.value = when (_playbackMode.value) {
            PlaybackMode.REPEAT_ONE -> _currentIndex.value
            PlaybackMode.SHUFFLE -> {
                if (_currentIndex.value < _shuffledIndices.size - 1) {
                    _currentIndex.value + 1
                } else {
                    0
                }
            }
            else -> (_currentIndex.value + 1) % (_currentPlaylist.value?.items?.size ?: 1)
        }
        
        return true
    }
    
    fun skipToPrevious(): Boolean {
        val prevItem = getPreviousItem() ?: return false
        
        _currentIndex.value = when (_playbackMode.value) {
            PlaybackMode.REPEAT_ONE -> _currentIndex.value
            PlaybackMode.SHUFFLE -> maxOf(0, _currentIndex.value - 1)
            else -> {
                if (_currentIndex.value > 0) {
                    _currentIndex.value - 1
                } else {
                    _currentPlaylist.value?.items?.size?.minus(1) ?: 0
                }
            }
        }
        
        return true
    }
    
    fun skipToIndex(index: Int) {
        val playlist = _currentPlaylist.value ?: return
        if (index in playlist.items.indices) {
            _currentIndex.value = index
        }
    }
    
    fun setPlaybackMode(mode: PlaybackMode) {
        _playbackMode.value = mode
        
        if (mode == PlaybackMode.SHUFFLE) {
            originalIndex = _currentIndex.value
            generateShuffleOrder()
        } else if (_playbackMode.value != PlaybackMode.SHUFFLE && mode != PlaybackMode.SHUFFLE) {
            // Restore original position when leaving shuffle mode
            _currentIndex.value = originalIndex
            _shuffledIndices.clear()
        }
    }
    
    private fun generateShuffleOrder() {
        val playlist = _currentPlaylist.value ?: return
        if (playlist.items.isEmpty()) return
        
        _shuffledIndices.clear()
        _shuffledIndices.addAll(playlist.items.indices.shuffled(Random.Default))
        
        // Ensure current item is first in shuffle
        val currentRealIndex = if (_shuffledIndices.isNotEmpty() && _currentIndex.value < _shuffledIndices.size) {
            _shuffledIndices[_currentIndex.value]
        } else {
            _currentIndex.value
        }
        
        _shuffledIndices.remove(currentRealIndex)
        _shuffledIndices.add(0, currentRealIndex)
        _currentIndex.value = 0
    }
    
    // Import/Export
    
    suspend fun exportPlaylistToM3U(playlist: Playlist, outputFile: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val content = buildString {
                    appendLine(M3U_HEADER)
                    playlist.items.forEach { item ->
                        appendLine("$M3U_INFO${item.duration / 1000},${item.title}")
                        appendLine(item.uri)
                    }
                }
                outputFile.writeText(content)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    suspend fun importPlaylistFromM3U(file: File, playlistName: String? = null): Playlist? {
        return withContext(Dispatchers.IO) {
            try {
                val lines = file.readLines()
                if (lines.firstOrNull() != M3U_HEADER) {
                    return@withContext null
                }
                
                val items = mutableListOf<PlaylistItem>()
                var i = 1
                
                while (i < lines.size) {
                    val line = lines[i]
                    if (line.startsWith(M3U_INFO)) {
                        val info = line.substring(M3U_INFO.length)
                        val parts = info.split(",", limit = 2)
                        val duration = parts[0].toLongOrNull()?.times(1000) ?: 0L
                        val title = parts.getOrNull(1) ?: ""
                        
                        if (i + 1 < lines.size) {
                            val uri = lines[i + 1]
                            items.add(
                                PlaylistItem(
                                    id = generateItemId(),
                                    uri = uri,
                                    title = title,
                                    duration = duration,
                                    addedAt = Date()
                                )
                            )
                            i += 2
                        } else {
                            i++
                        }
                    } else if (line.isNotBlank() && !line.startsWith("#")) {
                        // Plain URI without metadata
                        items.add(
                            PlaylistItem(
                                id = generateItemId(),
                                uri = line,
                                title = File(line).nameWithoutExtension,
                                duration = 0L,
                                addedAt = Date()
                            )
                        )
                        i++
                    } else {
                        i++
                    }
                }
                
                val playlist = Playlist(
                    id = generatePlaylistId(),
                    name = playlistName ?: file.nameWithoutExtension,
                    type = PlaylistType.IMPORTED,
                    createdAt = Date(),
                    modifiedAt = Date(),
                    items = items,
                    itemCount = items.size,
                    duration = items.sumOf { it.duration }
                )
                
                playlistDao.insertPlaylist(playlist)
                playlist
                
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    // Smart playlists
    
    suspend fun createSmartPlaylist(
        name: String,
        criteria: SmartPlaylistCriteria
    ): Playlist {
        return withContext(Dispatchers.IO) {
            val playlist = Playlist(
                id = generatePlaylistId(),
                name = name,
                type = PlaylistType.SMART,
                createdAt = Date(),
                modifiedAt = Date(),
                items = emptyList(),
                smartCriteria = criteria,
                itemCount = 0,
                duration = 0L
            )
            
            playlistDao.insertPlaylist(playlist)
            
            // Update with matching items
            updateSmartPlaylist(playlist)
        }
    }
    
    suspend fun updateSmartPlaylist(playlist: Playlist): Playlist {
        return withContext(Dispatchers.IO) {
            if (playlist.type != PlaylistType.SMART || playlist.smartCriteria == null) {
                return@withContext playlist
            }
            
            // Query media based on criteria
            val items = queryMediaByCriteria(playlist.smartCriteria)
            
            val updated = playlist.copy(
                items = items,
                itemCount = items.size,
                duration = items.sumOf { it.duration },
                modifiedAt = Date()
            )
            
            playlistDao.updatePlaylist(updated)
            updated
        }
    }
    
    private suspend fun queryMediaByCriteria(criteria: SmartPlaylistCriteria): List<PlaylistItem> {
        // This would query your media database based on criteria
        // For now, returning empty list as placeholder
        return emptyList()
    }
    
    // Utilities
    
    private fun generatePlaylistId(): String {
        return "playlist_${System.currentTimeMillis()}_${Random.nextInt(1000)}"
    }
    
    private fun generateItemId(): String {
        return "item_${System.currentTimeMillis()}_${Random.nextInt(1000)}"
    }
    
    suspend fun getRecentlyPlayed(limit: Int = 20): List<PlaylistItem> {
        return withContext(Dispatchers.IO) {
            playlistDao.getRecentlyPlayed(limit)
        }
    }
    
    suspend fun getMostPlayed(limit: Int = 20): List<PlaylistItem> {
        return withContext(Dispatchers.IO) {
            playlistDao.getMostPlayed(limit)
        }
    }
}

data class Playlist(
    val id: String,
    val name: String,
    val description: String? = null,
    val type: PlaylistType,
    val createdAt: Date,
    val modifiedAt: Date,
    val items: List<PlaylistItem>,
    val thumbnailUrl: String? = null,
    val duration: Long, // Total duration in milliseconds
    val itemCount: Int,
    val smartCriteria: SmartPlaylistCriteria? = null
)

data class PlaylistItem(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val duration: Long, // in milliseconds
    val thumbnailUrl: String? = null,
    val addedAt: Date,
    val playCount: Int = 0,
    val lastPlayed: Date? = null,
    val metadata: Map<String, String> = emptyMap()
)

enum class PlaylistType {
    CUSTOM,      // User-created playlist
    SMART,       // Auto-generated based on criteria
    IMPORTED,    // Imported from file
    FAVORITE,    // Favorites playlist
    QUEUE,       // Current playback queue
    RECENT,      // Recently played
    MOST_PLAYED  // Most played items
}

enum class PlaybackMode {
    NORMAL,      // Play in order, stop at end
    REPEAT_ALL,  // Repeat entire playlist
    REPEAT_ONE,  // Repeat current item
    SHUFFLE      // Random order
}

data class SmartPlaylistCriteria(
    val mediaType: MediaType? = null,
    val genres: List<String>? = null,
    val minRating: Float? = null,
    val dateAddedAfter: Date? = null,
    val dateAddedBefore: Date? = null,
    val minPlayCount: Int? = null,
    val maxPlayCount: Int? = null,
    val minDuration: Long? = null,
    val maxDuration: Long? = null,
    val searchQuery: String? = null,
    val limit: Int = 100,
    val orderBy: OrderBy = OrderBy.DATE_ADDED_DESC
)

enum class OrderBy {
    DATE_ADDED_DESC,
    DATE_ADDED_ASC,
    TITLE_ASC,
    TITLE_DESC,
    DURATION_ASC,
    DURATION_DESC,
    PLAY_COUNT_DESC,
    PLAY_COUNT_ASC,
    RANDOM
}

enum class MediaType {
    VIDEO, AUDIO, MIXED
}

// DAO interface (to be implemented with Room)
interface PlaylistDao {
    suspend fun insertPlaylist(playlist: Playlist)
    suspend fun updatePlaylist(playlist: Playlist)
    suspend fun deletePlaylist(playlistId: String)
    suspend fun getPlaylist(playlistId: String): Playlist?
    suspend fun getAllPlaylists(): List<Playlist>
    fun observePlaylists(): Flow<List<Playlist>>
    suspend fun getRecentlyPlayed(limit: Int): List<PlaylistItem>
    suspend fun getMostPlayed(limit: Int): List<PlaylistItem>
}