package com.astralstream.player.analytics

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class AnalyticsManager @Inject constructor(
    private val context: Context,
    private val analyticsDao: AnalyticsDao // Assume DAO exists
) {
    
    private val _viewingStats = MutableStateFlow(ViewingStatistics())
    val viewingStats: StateFlow<ViewingStatistics> = _viewingStats.asStateFlow()
    
    private val _playbackStats = MutableStateFlow(PlaybackStatistics())
    val playbackStats: StateFlow<PlaybackStatistics> = _playbackStats.asStateFlow()
    
    private val _performanceStats = MutableStateFlow(PerformanceStatistics())
    val performanceStats: StateFlow<PerformanceStatistics> = _performanceStats.asStateFlow()
    
    private val sessionStartTime = System.currentTimeMillis()
    private var currentSessionId = UUID.randomUUID().toString()
    private var currentVideoSession: VideoSession? = null
    private val anonymousUserId = UUID.randomUUID().toString()
    
    // Initialization and Setup
    
    fun initialize() {
        // Initialize analytics tracking
        startSession()
    }
    
    fun getAnonymousUserId(): String = anonymousUserId
    
    fun trackAppLaunch() {
        recordEvent(AnalyticsEvent.FeatureUsed("app_launch"))
    }
    
    // Session Management
    
    fun startSession() {
        currentSessionId = UUID.randomUUID().toString()
        recordEvent(AnalyticsEvent.SessionStart(currentSessionId))
    }
    
    fun endSession() {
        val duration = System.currentTimeMillis() - sessionStartTime
        recordEvent(AnalyticsEvent.SessionEnd(currentSessionId, duration))
        updateSessionStatistics(duration)
    }
    
    fun startVideoSession(videoUri: String, videoTitle: String, duration: Long) {
        currentVideoSession = VideoSession(
            sessionId = UUID.randomUUID().toString(),
            videoUri = videoUri,
            videoTitle = videoTitle,
            startTime = System.currentTimeMillis(),
            videoDuration = duration
        )
        
        recordEvent(AnalyticsEvent.VideoStart(
            videoUri = videoUri,
            videoTitle = videoTitle,
            duration = duration
        ))
    }
    
    fun endVideoSession(position: Long, completed: Boolean) {
        currentVideoSession?.let { session ->
            val watchDuration = System.currentTimeMillis() - session.startTime
            val percentWatched = if (session.videoDuration > 0) {
                (position.toFloat() / session.videoDuration * 100).roundToInt()
            } else 0
            
            recordEvent(AnalyticsEvent.VideoEnd(
                videoUri = session.videoUri,
                watchDuration = watchDuration,
                percentWatched = percentWatched,
                completed = completed
            ))
            
            updateVideoStatistics(session, watchDuration, percentWatched, completed)
        }
        currentVideoSession = null
    }
    
    // Event Recording
    
    fun recordEvent(event: AnalyticsEvent) {
        when (event) {
            is AnalyticsEvent.VideoStart -> recordVideoStart(event)
            is AnalyticsEvent.VideoEnd -> recordVideoEnd(event)
            is AnalyticsEvent.VideoSeek -> recordVideoSeek(event)
            is AnalyticsEvent.VideoPause -> recordVideoPause(event)
            is AnalyticsEvent.VideoResume -> recordVideoResume(event)
            is AnalyticsEvent.VideoError -> recordVideoError(event)
            is AnalyticsEvent.SubtitleEnabled -> recordSubtitleEnabled(event)
            is AnalyticsEvent.AudioTrackChanged -> recordAudioTrackChanged(event)
            is AnalyticsEvent.QualityChanged -> recordQualityChanged(event)
            is AnalyticsEvent.FilterApplied -> recordFilterApplied(event)
            is AnalyticsEvent.BookmarkCreated -> recordBookmarkCreated(event)
            is AnalyticsEvent.PlaylistAction -> recordPlaylistAction(event)
            is AnalyticsEvent.SearchPerformed -> recordSearchPerformed(event)
            is AnalyticsEvent.FeatureUsed -> recordFeatureUsed(event)
            is AnalyticsEvent.SessionStart -> recordSessionStart(event)
            is AnalyticsEvent.SessionEnd -> recordSessionEnd(event)
        }
    }
    
    private fun recordVideoStart(event: AnalyticsEvent.VideoStart) {
        // Update viewing statistics
        val stats = _viewingStats.value
        _viewingStats.value = stats.copy(
            totalVideosPlayed = stats.totalVideosPlayed + 1,
            dailyVideosPlayed = stats.dailyVideosPlayed + 1
        )
    }
    
    private fun recordVideoEnd(event: AnalyticsEvent.VideoEnd) {
        val stats = _viewingStats.value
        _viewingStats.value = stats.copy(
            totalWatchTime = stats.totalWatchTime + event.watchDuration,
            dailyWatchTime = stats.dailyWatchTime + event.watchDuration,
            videosCompleted = if (event.completed) stats.videosCompleted + 1 else stats.videosCompleted
        )
    }
    
    // Statistics Calculation
    
    suspend fun calculateViewingStatistics(): ViewingStatistics {
        return withContext(Dispatchers.IO) {
            val events = analyticsDao.getEventsByType(AnalyticsEvent.Type.VIDEO_END)
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }.timeInMillis
            
            var totalWatchTime = 0L
            var dailyWatchTime = 0L
            var totalVideos = 0
            var dailyVideos = 0
            var completedVideos = 0
            
            events.forEach { event ->
                if (event is AnalyticsEvent.VideoEnd) {
                    totalWatchTime += event.watchDuration
                    totalVideos++
                    
                    if (event.timestamp >= today) {
                        dailyWatchTime += event.watchDuration
                        dailyVideos++
                    }
                    
                    if (event.completed) {
                        completedVideos++
                    }
                }
            }
            
            ViewingStatistics(
                totalWatchTime = totalWatchTime,
                dailyWatchTime = dailyWatchTime,
                weeklyWatchTime = calculateWeeklyWatchTime(),
                monthlyWatchTime = calculateMonthlyWatchTime(),
                totalVideosPlayed = totalVideos,
                dailyVideosPlayed = dailyVideos,
                videosCompleted = completedVideos,
                averageWatchDuration = if (totalVideos > 0) totalWatchTime / totalVideos else 0,
                completionRate = if (totalVideos > 0) (completedVideos * 100f / totalVideos) else 0f,
                favoriteGenres = calculateFavoriteGenres(),
                peakWatchingHours = calculatePeakHours(),
                watchingStreak = calculateWatchingStreak()
            )
        }
    }
    
    suspend fun calculatePlaybackStatistics(): PlaybackStatistics {
        return withContext(Dispatchers.IO) {
            val events = analyticsDao.getAllEvents()
            
            var totalSeeks = 0
            var totalPauses = 0
            var bufferingTime = 0L
            var qualityChanges = 0
            val codecUsage = mutableMapOf<String, Int>()
            val resolutionUsage = mutableMapOf<String, Int>()
            val speedUsage = mutableMapOf<Float, Int>()
            
            events.forEach { event ->
                when (event) {
                    is AnalyticsEvent.VideoSeek -> totalSeeks++
                    is AnalyticsEvent.VideoPause -> totalPauses++
                    is AnalyticsEvent.QualityChanged -> {
                        qualityChanges++
                        resolutionUsage[event.newQuality] = 
                            resolutionUsage.getOrDefault(event.newQuality, 0) + 1
                    }
                    else -> {
                        // Handle other event types if needed
                    }
                }
            }
            
            PlaybackStatistics(
                totalSeeks = totalSeeks,
                averageSeeksPerVideo = calculateAverageSeeks(),
                totalPauses = totalPauses,
                bufferingTime = bufferingTime,
                averageBufferingPerVideo = calculateAverageBuffering(),
                qualityChanges = qualityChanges,
                mostUsedCodec = codecUsage.maxByOrNull { it.value }?.key,
                mostUsedResolution = resolutionUsage.maxByOrNull { it.value }?.key,
                mostUsedSpeed = speedUsage.maxByOrNull { it.value }?.key,
                subtitleUsageRate = calculateSubtitleUsage(),
                audioTrackSwitches = calculateAudioTrackSwitches()
            )
        }
    }
    
    suspend fun getWatchHistory(limit: Int = 50): List<WatchHistoryItem> {
        return withContext(Dispatchers.IO) {
            analyticsDao.getWatchHistory(limit)
        }
    }
    
    suspend fun getMostWatchedVideos(limit: Int = 20): List<VideoStatistic> {
        return withContext(Dispatchers.IO) {
            analyticsDao.getMostWatchedVideos(limit)
        }
    }
    
    suspend fun getWatchingHeatmap(): Map<Int, Float> {
        return withContext(Dispatchers.IO) {
            val hourlyData = mutableMapOf<Int, Float>()
            val events = analyticsDao.getEventsByType(AnalyticsEvent.Type.VIDEO_START)
            
            events.forEach { event ->
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = event.timestamp
                }
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                hourlyData[hour] = hourlyData.getOrDefault(hour, 0f) + 1f
            }
            
            // Normalize values
            val maxValue = hourlyData.values.maxOrNull() ?: 1f
            hourlyData.mapValues { it.value / maxValue }
        }
    }
    
    suspend fun getGenreDistribution(): Map<String, Float> {
        return withContext(Dispatchers.IO) {
            // Analyze video metadata and calculate genre distribution
            val genreCount = mutableMapOf<String, Int>()
            // Implementation would fetch video metadata and count genres
            
            val total = genreCount.values.sum().toFloat()
            genreCount.mapValues { it.value / total * 100 }
        }
    }
    
    suspend fun getFeatureUsageStats(): List<FeatureUsage> {
        return withContext(Dispatchers.IO) {
            val featureEvents = analyticsDao.getEventsByType(AnalyticsEvent.Type.FEATURE_USED)
            val usageMap = mutableMapOf<String, Int>()
            
            featureEvents.forEach { event ->
                if (event is AnalyticsEvent.FeatureUsed) {
                    usageMap[event.featureName] = usageMap.getOrDefault(event.featureName, 0) + 1
                }
            }
            
            usageMap.map { (feature, count) ->
                FeatureUsage(
                    featureName = feature,
                    usageCount = count,
                    lastUsed = getLastUsedTime(feature)
                )
            }.sortedByDescending { it.usageCount }
        }
    }
    
    // Insights Generation
    
    suspend fun generateInsights(): List<Insight> {
        return withContext(Dispatchers.IO) {
            val insights = mutableListOf<Insight>()
            
            // Viewing pattern insights
            val stats = _viewingStats.value
            if (stats.dailyWatchTime > TimeUnit.HOURS.toMillis(3)) {
                insights.add(Insight(
                    type = InsightType.VIEWING_PATTERN,
                    title = "Heavy Viewer",
                    description = "You've watched over 3 hours today!",
                    icon = "🎬"
                ))
            }
            
            // Completion rate insight
            if (stats.completionRate < 50) {
                insights.add(Insight(
                    type = InsightType.COMPLETION_RATE,
                    title = "Low Completion Rate",
                    description = "You complete less than 50% of videos. Try shorter content!",
                    icon = "📊"
                ))
            }
            
            // Streak insight
            if (stats.watchingStreak > 7) {
                insights.add(Insight(
                    type = InsightType.STREAK,
                    title = "Watching Streak!",
                    description = "You've watched videos for ${stats.watchingStreak} days in a row!",
                    icon = "🔥"
                ))
            }
            
            // Peak hours insight
            stats.peakWatchingHours.firstOrNull()?.let { peakHour ->
                insights.add(Insight(
                    type = InsightType.PEAK_HOURS,
                    title = "Peak Watching Time",
                    description = "You watch most videos around ${peakHour}:00",
                    icon = "⏰"
                ))
            }
            
            // Feature discovery
            val unusedFeatures = discoverUnusedFeatures()
            if (unusedFeatures.isNotEmpty()) {
                insights.add(Insight(
                    type = InsightType.FEATURE_DISCOVERY,
                    title = "Try These Features",
                    description = "You haven't tried: ${unusedFeatures.joinToString(", ")}",
                    icon = "💡"
                ))
            }
            
            insights
        }
    }
    
    // Helper functions
    
    private suspend fun calculateWeeklyWatchTime(): Long {
        val weekAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        return analyticsDao.getWatchTimeSince(weekAgo)
    }
    
    private suspend fun calculateMonthlyWatchTime(): Long {
        val monthAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        return analyticsDao.getWatchTimeSince(monthAgo)
    }
    
    private suspend fun calculateFavoriteGenres(): List<String> {
        // Implementation would analyze video metadata
        return listOf("Action", "Documentary", "Comedy")
    }
    
    private suspend fun calculatePeakHours(): List<Int> {
        val hourlyData = getWatchingHeatmap()
        return hourlyData.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }
    }
    
    private suspend fun calculateWatchingStreak(): Int {
        val dates = analyticsDao.getUniqueDatesWithActivity()
        var streak = 0
        var currentDate = Calendar.getInstance()
        
        for (date in dates.sortedDescending()) {
            val eventDate = Calendar.getInstance().apply { timeInMillis = date }
            if (isSameDay(currentDate, eventDate) || isYesterday(currentDate, eventDate)) {
                streak++
                currentDate = eventDate
            } else {
                break
            }
        }
        
        return streak
    }
    
    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
    
    private fun isYesterday(today: Calendar, other: Calendar): Boolean {
        val yesterday = Calendar.getInstance().apply {
            timeInMillis = today.timeInMillis
            add(Calendar.DAY_OF_YEAR, -1)
        }
        return isSameDay(yesterday, other)
    }
    
    private suspend fun calculateAverageSeeks(): Float {
        val totalVideos = analyticsDao.getUniqueVideoCount()
        val totalSeeks = analyticsDao.getEventCountByType(AnalyticsEvent.Type.VIDEO_SEEK)
        return if (totalVideos > 0) totalSeeks.toFloat() / totalVideos else 0f
    }
    
    private suspend fun calculateAverageBuffering(): Long {
        // Implementation would track buffering events
        return 0L
    }
    
    private suspend fun calculateSubtitleUsage(): Float {
        val totalVideos = analyticsDao.getUniqueVideoCount()
        val subtitleEvents = analyticsDao.getEventCountByType(AnalyticsEvent.Type.SUBTITLE_ENABLED)
        return if (totalVideos > 0) (subtitleEvents * 100f / totalVideos) else 0f
    }
    
    private suspend fun calculateAudioTrackSwitches(): Int {
        return analyticsDao.getEventCountByType(AnalyticsEvent.Type.AUDIO_TRACK_CHANGED)
    }
    
    private suspend fun getLastUsedTime(feature: String): Long {
        return analyticsDao.getLastEventTime(AnalyticsEvent.Type.FEATURE_USED, feature)
    }
    
    private fun discoverUnusedFeatures(): List<String> {
        val allFeatures = listOf(
            "Video Filters", "Frame Navigation", "Bookmarks", 
            "Smart Playlists", "Subtitle Download", "Cloud Sync"
        )
        val usedFeatures = mutableSetOf<String>()
        // Check which features have been used
        return allFeatures - usedFeatures
    }
    
    private fun updateSessionStatistics(duration: Long) {
        // Update session-based statistics
    }
    
    private fun updateVideoStatistics(
        session: VideoSession,
        watchDuration: Long,
        percentWatched: Int,
        completed: Boolean
    ) {
        // Update video-specific statistics
    }
    
    // Performance monitoring helpers
    private fun recordVideoSeek(event: AnalyticsEvent.VideoSeek) {}
    private fun recordVideoPause(event: AnalyticsEvent.VideoPause) {}
    private fun recordVideoResume(event: AnalyticsEvent.VideoResume) {}
    private fun recordVideoError(event: AnalyticsEvent.VideoError) {}
    private fun recordSubtitleEnabled(event: AnalyticsEvent.SubtitleEnabled) {}
    private fun recordAudioTrackChanged(event: AnalyticsEvent.AudioTrackChanged) {}
    private fun recordQualityChanged(event: AnalyticsEvent.QualityChanged) {}
    private fun recordFilterApplied(event: AnalyticsEvent.FilterApplied) {}
    private fun recordBookmarkCreated(event: AnalyticsEvent.BookmarkCreated) {}
    private fun recordPlaylistAction(event: AnalyticsEvent.PlaylistAction) {}
    private fun recordSearchPerformed(event: AnalyticsEvent.SearchPerformed) {}
    private fun recordFeatureUsed(event: AnalyticsEvent.FeatureUsed) {}
    private fun recordSessionStart(event: AnalyticsEvent.SessionStart) {}
    private fun recordSessionEnd(event: AnalyticsEvent.SessionEnd) {}
    
    // Export analytics data
    suspend fun exportAnalytics(): AnalyticsExport {
        return withContext(Dispatchers.IO) {
            AnalyticsExport(
                exportDate = Date(),
                viewingStats = _viewingStats.value,
                playbackStats = _playbackStats.value,
                performanceStats = _performanceStats.value,
                watchHistory = getWatchHistory(100),
                insights = generateInsights()
            )
        }
    }
}

// Data Classes

data class ViewingStatistics(
    val totalWatchTime: Long = 0,
    val dailyWatchTime: Long = 0,
    val weeklyWatchTime: Long = 0,
    val monthlyWatchTime: Long = 0,
    val totalVideosPlayed: Int = 0,
    val dailyVideosPlayed: Int = 0,
    val videosCompleted: Int = 0,
    val averageWatchDuration: Long = 0,
    val completionRate: Float = 0f,
    val favoriteGenres: List<String> = emptyList(),
    val peakWatchingHours: List<Int> = emptyList(),
    val watchingStreak: Int = 0
) {
    val formattedTotalTime: String
        get() = formatDuration(totalWatchTime)
    
    val formattedDailyTime: String
        get() = formatDuration(dailyWatchTime)
    
    private fun formatDuration(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        return "${hours}h ${minutes}m"
    }
}

data class PlaybackStatistics(
    val totalSeeks: Int = 0,
    val averageSeeksPerVideo: Float = 0f,
    val totalPauses: Int = 0,
    val bufferingTime: Long = 0,
    val averageBufferingPerVideo: Long = 0,
    val qualityChanges: Int = 0,
    val mostUsedCodec: String? = null,
    val mostUsedResolution: String? = null,
    val mostUsedSpeed: Float? = null,
    val subtitleUsageRate: Float = 0f,
    val audioTrackSwitches: Int = 0
)

data class PerformanceStatistics(
    val averageFps: Float = 0f,
    val droppedFrames: Int = 0,
    val averageLoadTime: Long = 0,
    val crashCount: Int = 0,
    val errorCount: Int = 0,
    val memoryUsage: Long = 0,
    val batteryDrain: Float = 0f,
    val networkUsage: Long = 0
)

data class VideoSession(
    val sessionId: String,
    val videoUri: String,
    val videoTitle: String,
    val startTime: Long,
    val videoDuration: Long
)

data class WatchHistoryItem(
    val videoUri: String,
    val videoTitle: String,
    val watchedAt: Long,
    val duration: Long,
    val percentWatched: Int
)

data class VideoStatistic(
    val videoUri: String,
    val videoTitle: String,
    val playCount: Int,
    val totalWatchTime: Long,
    val averageWatchTime: Long,
    val completionRate: Float
)

data class FeatureUsage(
    val featureName: String,
    val usageCount: Int,
    val lastUsed: Long
)

data class Insight(
    val type: InsightType,
    val title: String,
    val description: String,
    val icon: String
)

enum class InsightType {
    VIEWING_PATTERN,
    COMPLETION_RATE,
    STREAK,
    PEAK_HOURS,
    FEATURE_DISCOVERY,
    PERFORMANCE,
    RECOMMENDATION
}

sealed class AnalyticsEvent(val timestamp: Long = System.currentTimeMillis()) {
    enum class Type {
        VIDEO_START, VIDEO_END, VIDEO_SEEK, VIDEO_PAUSE, VIDEO_RESUME,
        VIDEO_ERROR, SUBTITLE_ENABLED, AUDIO_TRACK_CHANGED, QUALITY_CHANGED,
        FILTER_APPLIED, BOOKMARK_CREATED, PLAYLIST_ACTION, SEARCH_PERFORMED,
        FEATURE_USED, SESSION_START, SESSION_END
    }
    
    data class VideoStart(val videoUri: String, val videoTitle: String, val duration: Long) : AnalyticsEvent()
    data class VideoEnd(val videoUri: String, val watchDuration: Long, val percentWatched: Int, val completed: Boolean) : AnalyticsEvent()
    data class VideoSeek(val from: Long, val to: Long) : AnalyticsEvent()
    data class VideoPause(val position: Long) : AnalyticsEvent()
    data class VideoResume(val position: Long) : AnalyticsEvent()
    data class VideoError(val error: String, val position: Long) : AnalyticsEvent()
    data class SubtitleEnabled(val language: String) : AnalyticsEvent()
    data class AudioTrackChanged(val trackId: String) : AnalyticsEvent()
    data class QualityChanged(val oldQuality: String, val newQuality: String) : AnalyticsEvent()
    data class FilterApplied(val filterName: String) : AnalyticsEvent()
    data class BookmarkCreated(val position: Long) : AnalyticsEvent()
    data class PlaylistAction(val action: String, val playlistId: String) : AnalyticsEvent()
    data class SearchPerformed(val query: String, val resultCount: Int) : AnalyticsEvent()
    data class FeatureUsed(val featureName: String) : AnalyticsEvent()
    data class SessionStart(val sessionId: String) : AnalyticsEvent()
    data class SessionEnd(val sessionId: String, val duration: Long) : AnalyticsEvent()
}

data class AnalyticsExport(
    val exportDate: Date,
    val viewingStats: ViewingStatistics,
    val playbackStats: PlaybackStatistics,
    val performanceStats: PerformanceStatistics,
    val watchHistory: List<WatchHistoryItem>,
    val insights: List<Insight>
)

// DAO Interface
interface AnalyticsDao {
    suspend fun insertEvent(event: AnalyticsEvent)
    suspend fun getEventsByType(type: AnalyticsEvent.Type): List<AnalyticsEvent>
    suspend fun getAllEvents(): List<AnalyticsEvent>
    suspend fun getWatchTimeSince(timestamp: Long): Long
    suspend fun getUniqueDatesWithActivity(): List<Long>
    suspend fun getUniqueVideoCount(): Int
    suspend fun getEventCountByType(type: AnalyticsEvent.Type): Int
    suspend fun getLastEventTime(type: AnalyticsEvent.Type, param: String): Long
    suspend fun getWatchHistory(limit: Int): List<WatchHistoryItem>
    suspend fun getMostWatchedVideos(limit: Int): List<VideoStatistic>
}