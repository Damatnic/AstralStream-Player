package com.astralstream.player

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.astralstream.player.analytics.AnalyticsManager
import com.astralstream.player.bookmarks.BookmarkManager
import com.astralstream.player.data.database.AstralStreamDatabase
import com.astralstream.player.playlist.PlaylistManager
import com.astralstream.player.sync.CloudSyncManager
import com.astralstream.player.theme.ThemeEngine
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Integration test to verify all components are properly wired
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class IntegrationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: AstralStreamDatabase

    @Inject
    lateinit var analyticsManager: AnalyticsManager

    @Inject
    lateinit var bookmarkManager: BookmarkManager

    @Inject
    lateinit var playlistManager: PlaylistManager

    @Inject
    lateinit var cloudSyncManager: CloudSyncManager

    @Inject
    lateinit var themeEngine: ThemeEngine

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun testDatabaseIsInitialized() {
        assertNotNull(database)
        assertNotNull(database.mediaDao())
        assertNotNull(database.playlistDao())
        assertNotNull(database.bookmarkDao())
        assertNotNull(database.analyticsDao())
        assertNotNull(database.settingsDao())
        assertNotNull(database.playbackHistoryDao())
        assertNotNull(database.subtitleCacheDao())
        assertNotNull(database.metadataCacheDao())
    }

    @Test
    fun testManagersAreInitialized() {
        assertNotNull(analyticsManager)
        assertNotNull(bookmarkManager)
        assertNotNull(playlistManager)
        assertNotNull(cloudSyncManager)
        assertNotNull(themeEngine)
    }

    @Test
    fun testApplicationContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assert(appContext.packageName.contains("com.astralstream.player"))
    }
}