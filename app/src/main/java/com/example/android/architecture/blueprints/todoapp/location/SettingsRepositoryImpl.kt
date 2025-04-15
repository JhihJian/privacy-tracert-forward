package com.example.android.architecture.blueprints.todoapp.location

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// DataStore 实例
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "location_settings")

/**
 * SettingsRepository的实现类，使用DataStore存储配置
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {
    
    companion object {
        // 设置键
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val USER_NAME = stringPreferencesKey("user_name")
        private val UPLOAD_ENABLED = booleanPreferencesKey("upload_enabled")
        private val UPLOAD_INTERVAL = longPreferencesKey("upload_interval")
        private val BACKGROUND_UPLOAD_INTERVAL = longPreferencesKey("background_upload_interval")
        private val LOCATION_UPDATE_INTERVAL = longPreferencesKey("location_update_interval")
        
        // 默认值
        private const val DEFAULT_SERVER_URL = ""
        private const val DEFAULT_USER_NAME = "默认用户"
        private const val DEFAULT_UPLOAD_ENABLED = true
        private const val DEFAULT_UPLOAD_INTERVAL = 5000L // 5秒
        private const val DEFAULT_BACKGROUND_UPLOAD_INTERVAL = 180000L // 3分钟
        private const val DEFAULT_LOCATION_UPDATE_INTERVAL = 60000L // 1分钟
    }
    
    override fun getServerUrl(): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[SERVER_URL] ?: DEFAULT_SERVER_URL
        }
    }
    
    override suspend fun setServerUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[SERVER_URL] = url
        }
    }
    
    override fun getUserName(): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[USER_NAME] ?: DEFAULT_USER_NAME
        }
    }
    
    override suspend fun setUserName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_NAME] = name
        }
    }
    
    override fun isUploadEnabled(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[UPLOAD_ENABLED] ?: DEFAULT_UPLOAD_ENABLED
        }
    }
    
    override suspend fun setUploadEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[UPLOAD_ENABLED] = enabled
        }
    }
    
    override fun getUploadInterval(): Flow<Long> {
        return context.dataStore.data.map { preferences ->
            preferences[UPLOAD_INTERVAL] ?: DEFAULT_UPLOAD_INTERVAL
        }
    }
    
    override suspend fun setUploadInterval(intervalMillis: Long) {
        context.dataStore.edit { preferences ->
            preferences[UPLOAD_INTERVAL] = intervalMillis
        }
    }
    
    override fun getBackgroundUploadInterval(): Flow<Long> {
        return context.dataStore.data.map { preferences ->
            preferences[BACKGROUND_UPLOAD_INTERVAL] ?: DEFAULT_BACKGROUND_UPLOAD_INTERVAL
        }
    }
    
    override suspend fun setBackgroundUploadInterval(intervalMillis: Long) {
        context.dataStore.edit { preferences ->
            preferences[BACKGROUND_UPLOAD_INTERVAL] = intervalMillis
        }
    }
    
    override fun getLocationUpdateInterval(): Flow<Long> {
        return context.dataStore.data.map { preferences ->
            preferences[LOCATION_UPDATE_INTERVAL] ?: DEFAULT_LOCATION_UPDATE_INTERVAL
        }
    }
    
    override suspend fun setLocationUpdateInterval(intervalMillis: Long) {
        context.dataStore.edit { preferences ->
            preferences[LOCATION_UPDATE_INTERVAL] = intervalMillis
        }
    }
} 