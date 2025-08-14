package com.astralstream.player.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Entity representing a media file in the database
 */
@Entity(tableName = "media_files")
data class MediaFileEntity(
    @PrimaryKey
    val id: String,
    val filePath: String,
    val fileName: String,
    val displayName: String,
    val mimeType: String,
    val size: Long,
    val duration: Long,
    val width: Int?,
    val height: Int?,
    val bitrate: Long?,
    val frameRate: Float?,
    val thumbnailPath: String?,
    val dateAdded: Date,
    val dateModified: Date,
    val lastPlayed: Date?,
    val playCount: Int = 0,
    val isFavorite: Boolean = false,
    val isPrivate: Boolean = false,
    val tags: List<String> = emptyList()
)