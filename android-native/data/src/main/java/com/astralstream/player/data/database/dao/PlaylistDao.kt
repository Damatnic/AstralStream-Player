package com.astralstream.player.data.database.dao

import androidx.room.*
import com.astralstream.player.data.database.entities.PlaylistEntity
import com.astralstream.player.data.database.entities.PlaylistMediaCrossRef
import com.astralstream.player.data.database.entities.PlaylistWithMedia
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for playlists
 */
@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): PlaylistEntity?

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun getPlaylistWithMedia(playlistId: Long): Flow<PlaylistWithMedia?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistMediaCrossRef(crossRef: PlaylistMediaCrossRef)

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_media_cross_ref WHERE playlistId = :playlistId AND mediaId = :mediaId")
    suspend fun deletePlaylistMediaCrossRef(playlistId: Long, mediaId: Long)

    @Query("DELETE FROM playlist_media_cross_ref WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query("SELECT COUNT(*) FROM playlist_media_cross_ref WHERE playlistId = :playlistId")
    suspend fun getPlaylistMediaCount(playlistId: Long): Int

    @Query("UPDATE playlists SET name = :newName, modifiedAt = :timestamp WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, newName: String, timestamp: Long)
}