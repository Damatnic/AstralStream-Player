package com.astralstream.player.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a playlist
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val coverImagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val isSystemPlaylist: Boolean = false
)