package it.ipzs.scan_data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import it.ipzs.scan_data.managers.LogManager
import it.ipzs.scan_data.managers.LogManagerImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class LogManagerModule {
    @Binds
    abstract fun providesLogManager(logManagerImpl: LogManagerImpl): LogManager
}