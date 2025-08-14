package com.astralstream.player.data.database.dao

import androidx.room.*
import com.astralstream.player.data.database.entities.MediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: MediaEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaList(mediaList: List<MediaEntity>)
    
    @Update
    suspend fun updateMedia(media: MediaEntity)
    
    @Delete
    suspend fun deleteMedia(media: MediaEntity)
    
    @Query("DELETE FROM media WHERE id = :mediaId")
    suspend fun deleteMediaById(mediaId: Long)
    
    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun getMediaById(id: Long): MediaEntity?
    
    @Query("SELECT * FROM media ORDER BY dateAdded DESC")
    fun getAllMedia(): Flow<List<MediaEntity>>
    
    @Query("SELECT * FROM media ORDER BY lastPlayedTime DESC LIMIT :limit")
    fun getRecentMedia(limit: Int): Flow<List<MediaEntity>>
    
    @Query("SELECT * FROM media WHERE title LIKE :query OR path LIKE :query")
    fun searchMedia(query: String): Flow<List<MediaEntity>>
    
    @Query("UPDATE media SET lastPlayedPosition = :position, lastPlayedTime = :timestamp WHERE id = :mediaId")
    suspend fun updateLastPlayed(mediaId: Long, position: Long, timestamp: Long)
    
    @Query("UPDATE media SET isWatched = :watched WHERE id = :mediaId")
    suspend fun markAsWatched(mediaId: Long, watched: Boolean)
    
    @Query("SELECT * FROM media WHERE isFavorite = 1 ORDER BY dateAdded DESC")
    fun getFavoriteMedia(): Flow<List<MediaEntity>>
    
    @Query("UPDATE media SET isFavorite = NOT isFavorite WHERE id = :mediaId")
    suspend fun toggleFavorite(mediaId: Long)
    
    @Query("SELECT * FROM media WHERE path = :path LIMIT 1")
    suspend fun getMediaByPath(path: String): MediaEntity?
    
    @Query("DELETE FROM media")
    suspend fun deleteAllMedia()
}