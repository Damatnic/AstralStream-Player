package com.astralstream.player.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.Date

/**
 * Entity representing media files in playlists (many-to-many relationship)
 */
@Entity(
    tableName = "playlist_media",
    primaryKeys = ["playlistId", "mediaId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MediaFileEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["mediaId"])
    ]
)
data class PlaylistMediaEntity(
    val playlistId: String,
    val mediaId: String,
    val sortOrder: Int,
    val dateAdded: Date
)