package com.astralstream.player.di

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.astralstream.player.services.MediaScanWorker
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.ClassKey
import javax.inject.Inject
import javax.inject.Provider

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkerModule {

    @Binds
    @IntoMap
    @ClassKey(MediaScanWorker::class)
    abstract fun bindMediaScanWorkerFactory(factory: MediaScanWorkerFactory): ChildWorkerFactory
}

class MediaScanWorkerFactory @Inject constructor(
    // Add any dependencies needed by MediaScanWorker
) : ChildWorkerFactory {
    override fun create(appContext: Context, workerParameters: WorkerParameters): ListenableWorker {
        return MediaScanWorker(appContext, workerParameters)
    }
}