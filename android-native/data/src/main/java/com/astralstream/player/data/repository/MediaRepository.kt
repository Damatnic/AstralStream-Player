package com.astralstream.player.data.repository

import com.astralstream.player.data.database.entities.MediaEntity
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    suspend fun insertMedia(media: MediaEntity)
    suspend fun updateMedia(media: MediaEntity)
    suspend fun deleteMedia(media: MediaEntity)
    suspend fun getMediaById(id: Long): MediaEntity?
    fun getAllMedia(): Flow<List<MediaEntity>>
    fun getRecentMedia(limit: Int = 10): Flow<List<MediaEntity>>
    fun searchMedia(query: String): Flow<List<MediaEntity>>
    suspend fun updateLastPlayed(mediaId: Long, position: Long)
    suspend fun markAsWatched(mediaId: Long)
    fun getFavoriteMedia(): Flow<List<MediaEntity>>
    suspend fun toggleFavorite(mediaId: Long)
}