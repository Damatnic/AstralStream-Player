package com.astralstream.player.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subtitle_cache",
    indices = [Index("videoHash")]
)
data class SubtitleCacheEntity(
    @PrimaryKey val id: String,
    val videoHash: String,
    val language: String,
    val fileName: String,
    val filePath: String,
    val source: String,
    val downloadedAt: Long
)