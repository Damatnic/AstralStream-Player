package com.astralstream.player.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "analytics_events",
    indices = [Index("eventType"), Index("timestamp")]
)
data class AnalyticsEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventType: String,
    val eventData: String,
    val timestamp: Long,
    val sessionId: String? = null
)