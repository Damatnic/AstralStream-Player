package com.astralstream.player.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media")
data class MediaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val path: String,
    val duration: Long = 0,
    val size: Long = 0,
    val mimeType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val bitrate: Long? = null,
    val frameRate: Float? = null,
    val thumbnailPath: String? = null,
    val lastPlayedPosition: Long = 0,
    val lastPlayedTime: Long = 0,
    val isWatched: Boolean = false,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val dateAdded: Long = System.currentTimeMillis(),
    val dateModified: Long = System.currentTimeMillis()
)