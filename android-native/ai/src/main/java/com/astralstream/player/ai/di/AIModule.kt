package com.astralstream.player.ai.di

import android.content.Context
import com.astralstream.player.ai.AstralStreamAICoordinator
import com.astralstream.player.ai.audio.AudioEnhancementProcessor
import com.astralstream.player.ai.content.ContentIntelligenceProcessor
import com.astralstream.player.ai.integration.AIEnhancedExoPlayerManager
import com.astralstream.player.ai.organization.SmartOrganizationProcessor
import com.astralstream.player.ai.performance.PerformanceOptimizationProcessor
import com.astralstream.player.ai.subtitle.SubtitleGenerationProcessor
import com.astralstream.player.ai.video.VideoEnhancementProcessor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency injection module for AI components
 * Provides all AI processors and the coordinating system
 */
@Module
@InstallIn(SingletonComponent::class)
object AIModule {
    
    /**
     * Provides video enhancement processor
     */
    @Provides
    @Singleton
    fun provideVideoEnhancementProcessor(): VideoEnhancementProcessor {
        return VideoEnhancementProcessor()
    }
    
    /**
     * Provides audio enhancement processor
     */
    @Provides
    @Singleton
    fun provideAudioEnhancementProcessor(): AudioEnhancementProcessor {
        return AudioEnhancementProcessor()
    }
    
    /**
     * Provides subtitle generation processor
     */
    @Provides
    @Singleton
    fun provideSubtitleGenerationProcessor(): SubtitleGenerationProcessor {
        return SubtitleGenerationProcessor()
    }
    
    /**
     * Provides content intelligence processor
     */
    @Provides
    @Singleton
    fun provideContentIntelligenceProcessor(): ContentIntelligenceProcessor {
        return ContentIntelligenceProcessor()
    }
    
    /**
     * Provides smart organization processor
     */
    @Provides
    @Singleton
    fun provideSmartOrganizationProcessor(
        contentIntelligence: ContentIntelligenceProcessor
    ): SmartOrganizationProcessor {
        return SmartOrganizationProcessor(contentIntelligence)
    }
    
    /**
     * Provides performance optimization processor
     */
    @Provides
    @Singleton
    fun providePerformanceOptimizationProcessor(
        @ApplicationContext context: Context
    ): PerformanceOptimizationProcessor {
        return PerformanceOptimizationProcessor(context)
    }
    
    /**
     * Provides the main AI coordinator
     */
    @Provides
    @Singleton
    fun provideAstralStreamAICoordinator(
        @ApplicationContext context: Context,
        videoEnhancement: VideoEnhancementProcessor,
        audioEnhancement: AudioEnhancementProcessor,
        subtitleGeneration: SubtitleGenerationProcessor,
        contentIntelligence: ContentIntelligenceProcessor,
        smartOrganization: SmartOrganizationProcessor,
        performanceOptimization: PerformanceOptimizationProcessor
    ): AstralStreamAICoordinator {
        return AstralStreamAICoordinator(
            context = context,
            videoEnhancement = videoEnhancement,
            audioEnhancement = audioEnhancement,
            subtitleGeneration = subtitleGeneration,
            contentIntelligence = contentIntelligence,
            smartOrganization = smartOrganization,
            performanceOptimization = performanceOptimization
        )
    }
    
    /**
     * Provides AI-enhanced ExoPlayer manager
     */
    @Provides
    @Singleton
    fun provideAIEnhancedExoPlayerManager(
        @ApplicationContext context: Context,
        aiCoordinator: AstralStreamAICoordinator
    ): AIEnhancedExoPlayerManager {
        return AIEnhancedExoPlayerManager(
            context = context,
            aiCoordinator = aiCoordinator
        )
    }
}