package com.astralstream.player.data.database.dao

import androidx.room.*
import com.astralstream.player.data.database.entities.SubtitleCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubtitleCacheDao {
    @Query("SELECT * FROM subtitle_cache WHERE videoHash = :videoHash")
    fun getSubtitlesByVideoHash(videoHash: String): Flow<List<SubtitleCacheEntity>>

    @Query("SELECT * FROM subtitle_cache WHERE id = :id")
    suspend fun getSubtitleById(id: String): SubtitleCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubtitle(subtitle: SubtitleCacheEntity)

    @Delete
    suspend fun deleteSubtitle(subtitle: SubtitleCacheEntity)

    @Query("DELETE FROM subtitle_cache WHERE videoHash = :videoHash")
    suspend fun deleteSubtitlesByVideoHash(videoHash: String)

    @Query("DELETE FROM subtitle_cache WHERE downloadedAt < :beforeTime")
    suspend fun deleteOldSubtitles(beforeTime: Long)
}