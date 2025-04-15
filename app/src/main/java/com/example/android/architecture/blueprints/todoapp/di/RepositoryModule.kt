package com.example.android.architecture.blueprints.todoapp.di

import com.example.android.architecture.blueprints.todoapp.location.LocationRepository
import com.example.android.architecture.blueprints.todoapp.location.LocationRepositoryImpl
import com.example.android.architecture.blueprints.todoapp.location.SettingsRepository
import com.example.android.architecture.blueprints.todoapp.location.SettingsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 位置仓库模块
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LocationRepositoryModule {
    
    /**
     * 提供位置仓库
     */
    @Singleton
    @Binds
    abstract fun provideLocationRepository(repository: LocationRepositoryImpl): LocationRepository
    
    /**
     * 提供设置仓库
     */
    @Singleton
    @Binds
    abstract fun provideSettingsRepository(repository: SettingsRepositoryImpl): SettingsRepository
} 