package io.github.aedev.flow.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.aedev.flow.core.CoreExtractionService
import io.github.aedev.flow.core.CoreExtractionServiceImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CoreModule {
    @Binds
    @Singleton
    abstract fun bindCoreExtractionService(impl: CoreExtractionServiceImpl): CoreExtractionService
}
