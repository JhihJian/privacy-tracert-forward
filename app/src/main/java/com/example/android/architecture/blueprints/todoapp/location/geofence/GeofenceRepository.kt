package com.example.android.architecture.blueprints.todoapp.location.geofence

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 地理围栏仓库接口
 */
interface GeofenceRepository {
    suspend fun getGeofences(): Flow<List<GeofenceData>>
    suspend fun getGeofenceById(id: String): GeofenceData?
    suspend fun saveGeofence(geofence: GeofenceData): Boolean
    suspend fun updateGeofence(geofence: GeofenceData): Boolean
    suspend fun deleteGeofence(id: String): Boolean
    suspend fun deleteAllGeofences(): Boolean
    suspend fun activateGeofence(id: String, active: Boolean): Boolean
}

/**
 * 地理围栏仓库实现类（内存实现，可以后续替换为Room数据库实现）
 */
@Singleton
class GeofenceRepositoryImpl @Inject constructor() : GeofenceRepository {
    
    // 使用内存存储围栏数据
    private val _geofencesFlow = MutableStateFlow<List<GeofenceData>>(emptyList())
    
    override suspend fun getGeofences(): Flow<List<GeofenceData>> {
        return _geofencesFlow.asStateFlow()
    }
    
    override suspend fun getGeofenceById(id: String): GeofenceData? {
        return _geofencesFlow.value.find { it.id == id }
    }
    
    override suspend fun saveGeofence(geofence: GeofenceData): Boolean {
        try {
            val geofenceWithId = if (geofence.id.isBlank()) {
                geofence.copy(id = UUID.randomUUID().toString())
            } else {
                geofence
            }
            
            // 检查是否已存在同ID的围栏
            val exists = _geofencesFlow.value.any { it.id == geofenceWithId.id }
            if (exists) {
                // 如果存在，更新它
                return updateGeofence(geofenceWithId)
            }
            
            // 添加新围栏
            _geofencesFlow.update { currentList ->
                currentList + geofenceWithId
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    override suspend fun updateGeofence(geofence: GeofenceData): Boolean {
        try {
            _geofencesFlow.update { currentList ->
                currentList.map {
                    if (it.id == geofence.id) geofence else it
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    override suspend fun deleteGeofence(id: String): Boolean {
        try {
            _geofencesFlow.update { currentList ->
                currentList.filter { it.id != id }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    override suspend fun deleteAllGeofences(): Boolean {
        try {
            _geofencesFlow.update { emptyList() }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    override suspend fun activateGeofence(id: String, active: Boolean): Boolean {
        try {
            val geofence = getGeofenceById(id) ?: return false
            val updatedGeofence = geofence.copy(isActive = active)
            return updateGeofence(updatedGeofence)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
} 