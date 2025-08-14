package com.astralstream.player.di

import android.content.Context
import androidx.room.Room
import com.astralstream.player.data.database.AstralStreamDatabase
import com.astralstream.player.data.database.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AstralStreamDatabase {
        return Room.databaseBuilder(
            context,
            AstralStreamDatabase::class.java,
            "astralstream_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideMediaDao(database: AstralStreamDatabase): MediaDao {
        return database.mediaDao()
    }

    @Provides
    @Singleton
    fun providePlaylistDao(database: AstralStreamDatabase): PlaylistDao {
        return database.playlistDao()
    }

    @Provides
    @Singleton
    fun providePlaybackHistoryDao(database: AstralStreamDatabase): PlaybackHistoryDao {
        return database.playbackHistoryDao()
    }

    @Provides
    @Singleton
    fun provideSettingsDao(database: AstralStreamDatabase): SettingsDao {
        return database.settingsDao()
    }
    
    @Provides
    @Singleton
    fun provideBookmarkDao(database: AstralStreamDatabase): com.astralstream.player.data.database.dao.BookmarkDao {
        return database.bookmarkDao()
    }
    
    @Provides
    @Singleton
    fun provideAnalyticsDao(database: AstralStreamDatabase): com.astralstream.player.data.database.dao.AnalyticsDao {
        return database.analyticsDao()
    }
    
    @Provides
    @Singleton
    fun provideSubtitleCacheDao(database: AstralStreamDatabase): com.astralstream.player.data.database.dao.SubtitleCacheDao {
        return database.subtitleCacheDao()
    }
    
    @Provides
    @Singleton
    fun provideMetadataCacheDao(database: AstralStreamDatabase): com.astralstream.player.data.database.dao.MetadataCacheDao {
        return database.metadataCacheDao()
    }
}