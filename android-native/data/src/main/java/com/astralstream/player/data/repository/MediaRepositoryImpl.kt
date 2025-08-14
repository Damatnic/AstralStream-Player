package com.astralstream.player.data.repository

import com.astralstream.player.data.database.dao.MediaDao
import com.astralstream.player.data.database.entities.MediaEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl @Inject constructor(
    private val mediaDao: MediaDao
) : MediaRepository {

    override suspend fun insertMedia(media: MediaEntity) {
        mediaDao.insertMedia(media)
    }

    override suspend fun updateMedia(media: MediaEntity) {
        mediaDao.updateMedia(media)
    }

    override suspend fun deleteMedia(media: MediaEntity) {
        mediaDao.deleteMedia(media)
    }

    override suspend fun getMediaById(id: Long): MediaEntity? {
        return mediaDao.getMediaById(id)
    }

    override fun getAllMedia(): Flow<List<MediaEntity>> {
        return mediaDao.getAllMedia()
    }

    override fun getRecentMedia(limit: Int): Flow<List<MediaEntity>> {
        return mediaDao.getRecentMedia(limit)
    }

    override fun searchMedia(query: String): Flow<List<MediaEntity>> {
        return mediaDao.searchMedia("%$query%")
    }

    override suspend fun updateLastPlayed(mediaId: Long, position: Long) {
        mediaDao.updateLastPlayed(mediaId, position, System.currentTimeMillis())
    }

    override suspend fun markAsWatched(mediaId: Long) {
        mediaDao.markAsWatched(mediaId, true)
    }

    override fun getFavoriteMedia(): Flow<List<MediaEntity>> {
        return mediaDao.getFavoriteMedia()
    }

    override suspend fun toggleFavorite(mediaId: Long) {
        val media = mediaDao.getMediaById(mediaId)
        media?.let {
            mediaDao.updateMedia(it.copy(isFavorite = !it.isFavorite))
        }
    }
}