package com.astralstream.player.data.repository

import com.astralstream.player.data.database.entities.PlaylistEntity
import com.astralstream.player.data.database.entities.PlaylistWithMedia
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    suspend fun createPlaylist(name: String, description: String? = null): Long
    suspend fun deletePlaylist(playlistId: Long)
    suspend fun updatePlaylist(playlist: PlaylistEntity)
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>
    fun getPlaylistWithMedia(playlistId: Long): Flow<PlaylistWithMedia?>
    suspend fun addMediaToPlaylist(playlistId: Long, mediaId: Long)
    suspend fun removeMediaFromPlaylist(playlistId: Long, mediaId: Long)
    suspend fun clearPlaylist(playlistId: Long)
    suspend fun renamePlaylist(playlistId: Long, newName: String)
}