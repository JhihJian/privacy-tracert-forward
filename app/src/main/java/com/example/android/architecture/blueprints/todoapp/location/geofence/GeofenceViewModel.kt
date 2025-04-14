package com.example.android.architecture.blueprints.todoapp.location.geofence

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 地理围栏UI状态
 */
data class GeofenceUiState(
    val geofences: List<GeofenceData> = emptyList(),
    val currentGeofence: GeofenceData? = null,
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isMapReady: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

/**
 * 地理围栏ViewModel
 */
@HiltViewModel
class GeofenceViewModel @Inject constructor(
    private val geofenceRepository: GeofenceRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(GeofenceUiState())
    val uiState: StateFlow<GeofenceUiState> = _uiState.asStateFlow()
    
    init {
        loadGeofences()
    }
    
    /**
     * 加载所有围栏
     */
    fun loadGeofences() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                val geofencesFlow = geofenceRepository.getGeofences()
                geofencesFlow.collect { geofences ->
                    _uiState.update { 
                        it.copy(
                            geofences = geofences,
                            isLoading = false,
                            error = null
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "加载围栏失败：${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * 设置地图准备好的状态
     */
    fun setMapReady(ready: Boolean) {
        _uiState.update { it.copy(isMapReady = ready) }
    }
    
    /**
     * 选择当前编辑的围栏
     */
    fun selectGeofence(geofence: GeofenceData?) {
        _uiState.update { 
            it.copy(
                currentGeofence = geofence,
                isEditing = geofence != null
            )
        }
    }
    
    /**
     * 开始创建新围栏
     */
    fun startNewGeofence() {
        _uiState.update { 
            it.copy(
                currentGeofence = GeofenceData(
                    id = "",
                    name = "新围栏",
                    radius = 100f,
                    latitude = 0.0,
                    longitude = 0.0
                ),
                isEditing = true
            )
        }
    }
    
    /**
     * 取消编辑
     */
    fun cancelEditing() {
        _uiState.update { 
            it.copy(
                currentGeofence = null,
                isEditing = false
            )
        }
    }
    
    /**
     * 保存地理围栏
     */
    fun saveGeofence(geofence: GeofenceData) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                val result = geofenceRepository.saveGeofence(geofence)
                if (result) {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            isEditing = false,
                            currentGeofence = null,
                            message = "围栏已保存"
                        )
                    }
                } else {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = "保存围栏失败"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "保存围栏出错：${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * 删除围栏
     */
    fun deleteGeofence(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                val result = geofenceRepository.deleteGeofence(id)
                if (result) {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            isEditing = false,
                            currentGeofence = null,
                            message = "围栏已删除"
                        )
                    }
                } else {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = "删除围栏失败"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "删除围栏出错：${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * 清除消息
     */
    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
    
    /**
     * 清除错误
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
} 