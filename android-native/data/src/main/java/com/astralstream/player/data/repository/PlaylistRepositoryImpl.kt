package com.astralstream.player.data.repository

import com.astralstream.player.data.database.dao.PlaylistDao
import com.astralstream.player.data.database.entities.PlaylistEntity
import com.astralstream.player.data.database.entities.PlaylistMediaCrossRef
import com.astralstream.player.data.database.entities.PlaylistWithMedia
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao
) : PlaylistRepository {

    override suspend fun createPlaylist(name: String, description: String?): Long {
        val playlist = PlaylistEntity(
            name = name,
            description = description,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis()
        )
        return playlistDao.insertPlaylist(playlist)
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deletePlaylist(playlistId)
    }

    override suspend fun updatePlaylist(playlist: PlaylistEntity) {
        playlistDao.updatePlaylist(playlist.copy(modifiedAt = System.currentTimeMillis()))
    }

    override fun getAllPlaylists(): Flow<List<PlaylistEntity>> {
        return playlistDao.getAllPlaylists()
    }

    override fun getPlaylistWithMedia(playlistId: Long): Flow<PlaylistWithMedia?> {
        return playlistDao.getPlaylistWithMedia(playlistId)
    }

    override suspend fun addMediaToPlaylist(playlistId: Long, mediaId: Long) {
        val crossRef = PlaylistMediaCrossRef(playlistId, mediaId)
        playlistDao.insertPlaylistMediaCrossRef(crossRef)
    }

    override suspend fun removeMediaFromPlaylist(playlistId: Long, mediaId: Long) {
        playlistDao.deletePlaylistMediaCrossRef(playlistId, mediaId)
    }

    override suspend fun clearPlaylist(playlistId: Long) {
        playlistDao.clearPlaylist(playlistId)
    }

    override suspend fun renamePlaylist(playlistId: Long, newName: String) {
        playlistDao.renamePlaylist(playlistId, newName, System.currentTimeMillis())
    }
}