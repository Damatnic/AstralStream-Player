package com.astralstream.player.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.astralstream.player.data.database.dao.*
import com.astralstream.player.data.database.entities.*

/**
 * Room database for AstralStream
 */
@Database(
    entities = [
        MediaEntity::class,
        MediaFileEntity::class,
        PlaylistEntity::class,
        PlaylistMediaEntity::class,
        PlaylistMediaCrossRef::class,
        PlaybackHistoryEntity::class,
        SettingsEntity::class,
        BookmarkEntity::class,
        AnalyticsEventEntity::class,
        SubtitleCacheEntity::class,
        MetadataCacheEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(DatabaseConverters::class)
abstract class AstralStreamDatabase : RoomDatabase() {

    abstract fun mediaDao(): MediaDao
    abstract fun mediaFileDao(): MediaFileDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
    abstract fun settingsDao(): SettingsDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun analyticsDao(): AnalyticsDao
    abstract fun subtitleCacheDao(): SubtitleCacheDao
    abstract fun metadataCacheDao(): MetadataCacheDao

    companion object {
        const val DATABASE_NAME = "astralstream_database"

        fun create(context: Context): AstralStreamDatabase {
            return Room.databaseBuilder(
                context,
                AstralStreamDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}