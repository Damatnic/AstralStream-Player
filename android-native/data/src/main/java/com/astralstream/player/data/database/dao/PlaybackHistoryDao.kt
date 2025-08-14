package com.astralstream.player.data.database.dao

import androidx.room.*
import com.astralstream.player.data.database.entities.PlaybackHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for playback history
 */
@Dao
interface PlaybackHistoryDao {

    @Query("SELECT * FROM playback_history ORDER BY playbackDate DESC")
    fun getAllPlaybackHistory(): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history WHERE mediaId = :mediaId ORDER BY playbackDate DESC LIMIT 1")
    suspend fun getLatestPlaybackHistory(mediaId: String): PlaybackHistoryEntity?

    @Query("SELECT * FROM playback_history WHERE mediaId = :mediaId ORDER BY playbackDate DESC")
    fun getPlaybackHistoryForMedia(mediaId: String): Flow<List<PlaybackHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaybackHistory(history: PlaybackHistoryEntity)

    @Update
    suspend fun updatePlaybackHistory(history: PlaybackHistoryEntity)

    @Delete
    suspend fun deletePlaybackHistory(history: PlaybackHistoryEntity)

    @Query("DELETE FROM playback_history WHERE mediaId = :mediaId")
    suspend fun deletePlaybackHistoryForMedia(mediaId: String)

    @Query("DELETE FROM playback_history WHERE playbackDate < :cutoffDate")
    suspend fun deleteOldPlaybackHistory(cutoffDate: Long)
}