package com.example.android.architecture.blueprints.todoapp.di

import com.example.android.architecture.blueprints.todoapp.location.geofence.GeofenceRepository
import com.example.android.architecture.blueprints.todoapp.location.geofence.GeofenceRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GeofenceModule {
    
    @Singleton
    @Binds
    abstract fun bindGeofenceRepository(
        geofenceRepositoryImpl: GeofenceRepositoryImpl
    ): GeofenceRepository
} 