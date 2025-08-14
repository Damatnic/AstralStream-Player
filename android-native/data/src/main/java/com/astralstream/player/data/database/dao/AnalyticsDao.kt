package com.astralstream.player.data.database.dao

import androidx.room.*
import com.astralstream.player.data.database.entities.AnalyticsEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalyticsDao {
    @Insert
    suspend fun insertEvent(event: AnalyticsEventEntity)

    @Query("SELECT * FROM analytics_events WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    fun getEventsSince(startTime: Long): Flow<List<AnalyticsEventEntity>>

    @Query("SELECT * FROM analytics_events WHERE eventType = :eventType ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getEventsByType(eventType: String, limit: Int = 100): List<AnalyticsEventEntity>

    @Query("DELETE FROM analytics_events WHERE timestamp < :beforeTime")
    suspend fun deleteEventsOlderThan(beforeTime: Long)

    @Query("SELECT COUNT(*) FROM analytics_events WHERE eventType = :eventType")
    suspend fun getEventCount(eventType: String): Int

    @Query("SELECT * FROM analytics_events WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    suspend fun getEventsBySession(sessionId: String): List<AnalyticsEventEntity>
}