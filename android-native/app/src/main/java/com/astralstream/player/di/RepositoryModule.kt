package com.astralstream.player.di

import com.astralstream.player.data.repository.MediaRepository
import com.astralstream.player.data.repository.MediaRepositoryImpl
import com.astralstream.player.data.repository.PlaylistRepository
import com.astralstream.player.data.repository.PlaylistRepositoryImpl
import com.astralstream.player.data.repository.SettingsRepository
import com.astralstream.player.data.repository.SettingsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMediaRepository(
        mediaRepositoryImpl: MediaRepositoryImpl
    ): MediaRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(
        playlistRepositoryImpl: PlaylistRepositoryImpl
    ): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        settingsRepositoryImpl: SettingsRepositoryImpl
    ): SettingsRepository
}