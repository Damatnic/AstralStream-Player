package com.astralstream.player.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
    indices = [Index("videoUri")]
)
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val videoUri: String,
    val position: Long,
    val title: String,
    val note: String? = null,
    val thumbnailPath: String? = null,
    val createdAt: Long,
    val modifiedAt: Long,
    val type: String
)