package com.astralstream.player.data.database.dao

import androidx.room.*
import com.astralstream.player.data.database.entities.MediaFileEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for media files
 */
@Dao
interface MediaFileDao {

    @Query("SELECT * FROM media_files ORDER BY dateAdded DESC")
    fun getAllMediaFiles(): Flow<List<MediaFileEntity>>

    @Query("SELECT * FROM media_files WHERE id = :id")
    suspend fun getMediaFileById(id: String): MediaFileEntity?

    @Query("SELECT * FROM media_files WHERE filePath = :filePath")
    suspend fun getMediaFileByPath(filePath: String): MediaFileEntity?

    @Query("SELECT * FROM media_files WHERE isFavorite = 1 ORDER BY dateAdded DESC")
    fun getFavoriteMediaFiles(): Flow<List<MediaFileEntity>>

    @Query("SELECT * FROM media_files WHERE isPrivate = 1 ORDER BY dateAdded DESC")
    fun getPrivateMediaFiles(): Flow<List<MediaFileEntity>>

    @Query("SELECT * FROM media_files WHERE lastPlayed IS NOT NULL ORDER BY lastPlayed DESC LIMIT :limit")
    fun getRecentlyPlayed(limit: Int = 20): Flow<List<MediaFileEntity>>

    @Query("SELECT * FROM media_files WHERE fileName LIKE '%' || :query || '%' OR displayName LIKE '%' || :query || '%'")
    fun searchMediaFiles(query: String): Flow<List<MediaFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaFile(mediaFile: MediaFileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaFiles(mediaFiles: List<MediaFileEntity>)

    @Update
    suspend fun updateMediaFile(mediaFile: MediaFileEntity)

    @Delete
    suspend fun deleteMediaFile(mediaFile: MediaFileEntity)

    @Query("DELETE FROM media_files WHERE id = :id")
    suspend fun deleteMediaFileById(id: String)

    @Query("UPDATE media_files SET lastPlayed = :timestamp, playCount = playCount + 1 WHERE id = :id")
    suspend fun updatePlaybackStats(id: String, timestamp: Long)

    @Query("UPDATE media_files SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: String, isFavorite: Boolean)

    @Query("UPDATE media_files SET isPrivate = :isPrivate WHERE id = :id")
    suspend fun updatePrivateStatus(id: String, isPrivate: Boolean)

    @Query("DELETE FROM media_files")
    suspend fun deleteAllMediaFiles()
}