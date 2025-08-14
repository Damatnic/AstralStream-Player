package com.astralstream.player.di

import android.content.Context
import com.astralstream.player.analytics.AnalyticsManager
import com.astralstream.player.audio.MultiAudioTrackManager
import com.astralstream.player.bookmarks.BookmarkManager
import com.astralstream.player.controls.AdvancedPlayerControls
import com.astralstream.player.data.database.AstralStreamDatabase
import com.astralstream.player.gestures.GestureCustomization
import com.astralstream.player.hardware.HardwareAccelerationManager
import com.astralstream.player.manager.ExoPlayerManager
import com.astralstream.player.metadata.MetadataFetcher
import com.astralstream.player.playlist.PlaylistManager
import com.astralstream.player.subtitle.SubtitleDownloader
import com.astralstream.player.sync.CloudSyncManager
import com.astralstream.player.theme.ThemeEngine
import com.astralstream.player.thumbnail.ThumbnailGenerator
import com.astralstream.player.video.VideoFiltersManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ManagerModule {

    // Player Core
    // ExoPlayerManager is already provided in PlayerModule
    
    @Provides
    @Singleton
    fun provideHardwareAccelerationManager(
        @ApplicationContext context: Context
    ): HardwareAccelerationManager {
        return HardwareAccelerationManager(context)
    }

    // Audio & Video
    @Provides
    @Singleton
    fun provideMultiAudioTrackManager(): MultiAudioTrackManager {
        return MultiAudioTrackManager()
    }

    @Provides
    @Singleton
    fun provideVideoFiltersManager(
        @ApplicationContext context: Context
    ): VideoFiltersManager {
        return VideoFiltersManager(context)
    }

    // Controls
    @Provides
    @Singleton
    fun provideAdvancedPlayerControls(
        @ApplicationContext context: Context
    ): AdvancedPlayerControls {
        return AdvancedPlayerControls(context)
    }

    @Provides
    @Singleton
    fun provideGestureCustomization(
        @ApplicationContext context: Context
    ): GestureCustomization {
        return GestureCustomization(context)
    }

    // Content Enhancement
    @Provides
    @Singleton
    fun provideMetadataFetcher(
        @ApplicationContext context: Context
    ): MetadataFetcher {
        return MetadataFetcher(context, null)
    }

    @Provides
    @Singleton
    fun provideSubtitleDownloader(
        @ApplicationContext context: Context
    ): SubtitleDownloader {
        return SubtitleDownloader(context, null)
    }

    @Provides
    @Singleton
    fun provideThumbnailGenerator(
        @ApplicationContext context: Context
    ): ThumbnailGenerator {
        return ThumbnailGenerator(context)
    }

    @Provides
    @Singleton
    fun providePlaylistManager(
        @ApplicationContext context: Context,
        database: AstralStreamDatabase
    ): PlaylistManager {
        // Use simplified DAO implementation
        val dao = object : com.astralstream.player.playlist.PlaylistDao {
            override suspend fun insertPlaylist(playlist: com.astralstream.player.playlist.Playlist) {}
            override suspend fun updatePlaylist(playlist: com.astralstream.player.playlist.Playlist) {}
            override suspend fun deletePlaylist(playlistId: String) {}
            override suspend fun getPlaylist(playlistId: String) = null
            override suspend fun getAllPlaylists() = emptyList<com.astralstream.player.playlist.Playlist>()
            override fun observePlaylists() = kotlinx.coroutines.flow.flowOf(emptyList<com.astralstream.player.playlist.Playlist>())
            override suspend fun getRecentlyPlayed(limit: Int) = emptyList<com.astralstream.player.playlist.PlaylistItem>()
            override suspend fun getMostPlayed(limit: Int) = emptyList<com.astralstream.player.playlist.PlaylistItem>()
        }
        return PlaylistManager(context, dao)
    }

    @Provides
    @Singleton
    fun provideBookmarkManager(
        @ApplicationContext context: Context,
        database: AstralStreamDatabase
    ): BookmarkManager {
        // Use simplified DAO implementation
        val dao = object : com.astralstream.player.bookmarks.BookmarkDao {
            override suspend fun insertBookmark(bookmark: com.astralstream.player.bookmarks.Bookmark) {}
            override suspend fun updateBookmark(bookmark: com.astralstream.player.bookmarks.Bookmark) {}
            override suspend fun deleteBookmark(bookmarkId: String) {}
            override suspend fun getBookmark(bookmarkId: String) = null
            override suspend fun getBookmarksForVideo(videoUri: String) = emptyList<com.astralstream.player.bookmarks.Bookmark>()
            override fun observeBookmarksForVideo(videoUri: String) = kotlinx.coroutines.flow.flowOf(emptyList<com.astralstream.player.bookmarks.Bookmark>())
            override suspend fun getAllBookmarks() = emptyList<com.astralstream.player.bookmarks.Bookmark>()
            override suspend fun searchBookmarks(query: String) = emptyList<com.astralstream.player.bookmarks.Bookmark>()
            override suspend fun insertChapter(chapter: com.astralstream.player.bookmarks.Chapter) {}
            override suspend fun updateChapter(chapter: com.astralstream.player.bookmarks.Chapter) {}
            override suspend fun deleteChapter(chapterId: String) {}
            override suspend fun getChapter(chapterId: String) = null
            override suspend fun getChaptersForVideo(videoUri: String) = emptyList<com.astralstream.player.bookmarks.Chapter>()
            override fun observeChaptersForVideo(videoUri: String) = kotlinx.coroutines.flow.flowOf(emptyList<com.astralstream.player.bookmarks.Chapter>())
            override suspend fun insertWatchProgress(progress: com.astralstream.player.bookmarks.WatchProgress) {}
            override suspend fun updateWatchProgress(progress: com.astralstream.player.bookmarks.WatchProgress) {}
            override suspend fun deleteWatchProgress(videoUri: String) {}
            override suspend fun getWatchProgress(videoUri: String) = null
            override suspend fun getAllWatchProgress() = emptyList<com.astralstream.player.bookmarks.WatchProgress>()
            override suspend fun getRecentlyWatched(limit: Int) = emptyList<com.astralstream.player.bookmarks.WatchProgress>()
            override suspend fun getContinueWatching() = emptyList<com.astralstream.player.bookmarks.WatchProgress>()
        }
        return BookmarkManager(context, dao)
    }

    // Premium Features
    @Provides
    @Singleton
    fun provideCloudSyncManager(
        @ApplicationContext context: Context
    ): CloudSyncManager {
        return CloudSyncManager(context)
    }

    @Provides
    @Singleton
    fun provideThemeEngine(
        @ApplicationContext context: Context
    ): ThemeEngine {
        return ThemeEngine(context)
    }

    @Provides
    @Singleton
    fun provideAnalyticsManager(
        @ApplicationContext context: Context,
        database: AstralStreamDatabase
    ): AnalyticsManager {
        // Use simplified DAO implementation
        val dao = object : com.astralstream.player.analytics.AnalyticsDao {
            override suspend fun insertEvent(event: com.astralstream.player.analytics.AnalyticsEvent) {}
            override suspend fun getEventsByType(type: com.astralstream.player.analytics.AnalyticsEvent.Type) = emptyList<com.astralstream.player.analytics.AnalyticsEvent>()
            override suspend fun getAllEvents() = emptyList<com.astralstream.player.analytics.AnalyticsEvent>()
            override suspend fun getWatchTimeSince(timestamp: Long) = 0L
            override suspend fun getUniqueDatesWithActivity() = emptyList<Long>()
            override suspend fun getUniqueVideoCount() = 0
            override suspend fun getEventCountByType(type: com.astralstream.player.analytics.AnalyticsEvent.Type) = 0
            override suspend fun getLastEventTime(type: com.astralstream.player.analytics.AnalyticsEvent.Type, param: String) = 0L
            override suspend fun getWatchHistory(limit: Int) = emptyList<com.astralstream.player.analytics.WatchHistoryItem>()
            override suspend fun getMostWatchedVideos(limit: Int) = emptyList<com.astralstream.player.analytics.VideoStatistic>()
        }
        return AnalyticsManager(context, dao)
    }
}