package com.astralstream.player.data.database.entities

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class PlaylistWithMedia(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            PlaylistMediaCrossRef::class,
            parentColumn = "playlistId",
            entityColumn = "mediaId"
        )
    )
    val mediaList: List<MediaEntity>
)