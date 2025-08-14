package com.astralstream.player.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "metadata_cache",
    indices = [Index("videoHash", unique = true)]
)
data class MetadataCacheEntity(
    @PrimaryKey val videoHash: String,
    val title: String,
    val year: Int? = null,
    val plot: String? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val rating: Float = 0f,
    val runtime: Int? = null,
    val genres: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val cachedAt: Long
)