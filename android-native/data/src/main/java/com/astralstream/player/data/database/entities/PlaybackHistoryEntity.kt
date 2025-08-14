package com.astralstream.player.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Entity representing playback history
 */
@Entity(
    tableName = "playback_history",
    foreignKeys = [
        ForeignKey(
            entity = MediaFileEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["mediaId"])]
)
data class PlaybackHistoryEntity(
    @PrimaryKey
    val id: String,
    val mediaId: String,
    val playbackPosition: Long,
    val totalDuration: Long,
    val playbackDate: Date,
    val watchedPercentage: Float,
    val isCompleted: Boolean = false
)