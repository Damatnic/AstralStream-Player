package com.astralstream.core.domain.model

data class MediaItem(
    val id: Long = 0,
    val title: String,
    val path: String,
    val duration: Long = 0,
    val size: Long = 0,
    val mimeType: String? = null,
    val resolution: String? = null,
    val thumbnailPath: String? = null,
    val lastPlayedPosition: Long = 0,
    val lastPlayedTime: Long = 0,
    val isWatched: Boolean = false,
    val isFavorite: Boolean = false,
    val addedDate: Long = System.currentTimeMillis()
)