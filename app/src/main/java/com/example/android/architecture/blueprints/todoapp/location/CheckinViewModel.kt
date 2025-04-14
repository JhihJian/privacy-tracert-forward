package com.example.android.architecture.blueprints.todoapp.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amap.api.maps.AMap
import com.amap.api.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 打卡点页面的UI状态
 */
data class CheckinUiState(
    val isMapReady: Boolean = false,
    val isLoading: Boolean = false,
    val currentLocation: LatLng? = null,
    val errorMessage: String? = null,
    val isPermissionGranted: Boolean = false
)

/**
 * 打卡点ViewModel
 */
@HiltViewModel
class CheckinViewModel @Inject constructor(
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckinUiState())
    val uiState: StateFlow<CheckinUiState> = _uiState.asStateFlow()

    init {
        // 初始化时订阅服务绑定状态
        viewModelScope.launch {
            locationRepository.getServiceBoundFlow().collect { isBound ->
                if (isBound && _uiState.value.isPermissionGranted) {
                    // 服务已绑定且有权限，可以获取位置
                    getCurrentLocation()
                }
            }
        }
    }

    /**
     * 更新地图准备状态
     */
    fun updateMapReadyStatus(isReady: Boolean) {
        _uiState.update { it.copy(isMapReady = isReady) }
    }

    /**
     * 更新权限状态
     */
    fun updatePermissionStatus(isGranted: Boolean) {
        _uiState.update { it.copy(isPermissionGranted = isGranted) }
        
        // 如果有权限，尝试获取位置
        if (isGranted && locationRepository.isServiceBound()) {
            getCurrentLocation()
        }
    }

    /**
     * 获取当前位置
     */
    fun getCurrentLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                locationRepository.getCurrentLocation()?.let { location ->
                    val latLng = LatLng(location.latitude, location.longitude)
                    _uiState.update { 
                        it.copy(
                            currentLocation = latLng,
                            isLoading = false,
                            errorMessage = null
                        ) 
                    }
                } ?: run {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            errorMessage = "无法获取当前位置"
                        ) 
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = "获取位置失败: ${e.message}"
                    ) 
                }
            }
        }
    }
} 