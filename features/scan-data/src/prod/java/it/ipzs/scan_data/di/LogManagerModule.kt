package it.ipzs.scan_data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import it.ipzs.scan_data.managers.LogManager
import it.ipzs.scan_data.managers.LogManagerImpl

@Module
@InstallIn(SingletonComponent::class)
object LogManagerModule {
    @Provides
    fun providesLogManager(): LogManager = LogManagerImpl()
}