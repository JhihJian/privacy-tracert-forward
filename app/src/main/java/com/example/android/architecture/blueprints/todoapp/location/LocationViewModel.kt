package com.example.android.architecture.blueprints.todoapp.location

import android.util.Log
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
 * 位置数据状态
 */
data class LocationUiState(
    val isPermissionGranted: Boolean = false,
    val currentLocation: LocationData? = null,
    val locationJson: String = "",
    val message: String = "等待定位中...",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isServiceBound: Boolean = false,
    val isUploadEnabled: Boolean = false,
    val uploadStatus: String = "未启动",
    val userName: String = "默认用户"
)

/**
 * 位置功能ViewModel
 */
@HiltViewModel
class LocationViewModel @Inject constructor(
    private val locationRepository: LocationRepository
) : ViewModel() {
    
    private val TAG = "LocationViewModel"
    
    // UI状态
    private val _uiState = MutableStateFlow(LocationUiState())
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()
    
    init {
        // 订阅位置更新
        subscribeToLocationUpdates()
        
        // 订阅上传状态
        subscribeToUploadStatus()
        
        // 订阅服务绑定状态
        subscribeToServiceBoundStatus()
        
        // 订阅错误状态
        subscribeToErrorStatus()
    }
    
    /**
     * 订阅位置更新
     */
    private fun subscribeToLocationUpdates() {
        viewModelScope.launch {
            locationRepository.getLocationDataFlow().collect { location ->
                if (location != null) {
                    updateLocation(location)
                }
            }
        }
    }
    
    /**
     * 订阅上传状态
     */
    private fun subscribeToUploadStatus() {
        viewModelScope.launch {
            locationRepository.getUploadStatusFlow().collect { status ->
                updateUploadStatus(status)
            }
        }
    }
    
    /**
     * 订阅服务绑定状态
     */
    private fun subscribeToServiceBoundStatus() {
        viewModelScope.launch {
            locationRepository.getServiceBoundFlow().collect { isBound ->
                _uiState.update { it.copy(isServiceBound = isBound) }
            }
        }
    }
    
    /**
     * 订阅错误状态
     */
    private fun subscribeToErrorStatus() {
        viewModelScope.launch {
            locationRepository.getErrorFlow().collect { error ->
                if (error != null) {
                    _uiState.update { 
                        it.copy(
                            error = error,
                            isLoading = false,
                            message = "定位错误: $error"
                        )
                    }
                }
            }
        }
    }
    
    override fun onCleared() {
        // 清理资源
        locationRepository.cleanup()
        super.onCleared()
    }
    
    /**
     * 更新权限状态
     */
    fun updatePermissionStatus(isGranted: Boolean) {
        Log.d(TAG, "更新权限状态: isGranted=$isGranted")
        
        // 更新UI状态
        _uiState.update { currentState ->
            currentState.copy(
                isPermissionGranted = isGranted,
                message = if (!isGranted) "需要位置权限才能使用实时定位功能" else "等待定位中..."
            )
        }
        
        // 通知仓库
        locationRepository.updatePermissionStatus(isGranted)
    }
    
    /**
     * 更新位置信息
     */
    private fun updateLocation(location: LocationData) {
        // 将位置数据转换为JSON格式
        val locationJson = formatLocationToJson(location)
        
        // 更新UI状态
        _uiState.update { currentState ->
            currentState.copy(
                currentLocation = location,
                isLoading = false,
                message = "定位成功: ${location.address}",
                locationJson = locationJson,
                error = null
            )
        }
    }
    
    /**
     * 将位置数据格式化为JSON字符串
     */
    private fun formatLocationToJson(location: LocationData): String {
        return try {
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            val formattedDate = dateFormat.format(java.util.Date(location.time))
            
            // 使用扁平化格式
            """
            {
              "timestamp": "${formattedDate}",
              "latitude": ${location.latitude},
              "longitude": ${location.longitude},
              "accuracy": ${location.accuracy},
              "address": "${location.address}",
              "country": "${location.country}",
              "province": "${location.province}",
              "city": "${location.city}",
              "district": "${location.district}",
              "street": "${location.street}",
              "streetNum": "${location.streetNum}",
              "cityCode": "${location.cityCode}",
              "adCode": "${location.adCode}",
              "poiName": "${location.poiName}",
              "aoiName": "${location.aoiName}",
              "buildingId": "${location.buildingId}",
              "floor": "${location.floor}",
              "gpsAccuracyStatus": ${location.gpsAccuracyStatus},
              "locationType": ${location.locationType},
              "speed": ${location.speed},
              "bearing": ${location.bearing},
              "altitude": ${location.altitude},
              "errorCode": ${location.errorCode},
              "errorInfo": "${location.errorInfo}",
              "coordType": "${location.coordType}",
              "locationDetail": "${location.locationDetail}"
            }
            """.trimIndent()
        } catch (e: Exception) {
            Log.e(TAG, "格式化位置数据失败", e)
            """{"错误": "格式化位置数据失败: ${e.message}"}"""
        }
    }
    
    /**
     * 获取定位类型描述
     */
    private fun getLocationTypeDesc(locationType: Int): String {
        return when (locationType) {
            1 -> "GPS定位"
            2 -> "前次定位"
            4 -> "缓存定位"
            5 -> "Wifi定位"
            6 -> "基站定位"
            8 -> "离线定位"
            9 -> "最后位置缓存"
            else -> "未知定位类型"
        }
    }
    
    /**
     * 获取GPS状态描述
     */
    private fun getGpsStatusDesc(gpsStatus: Int): String {
        return when (gpsStatus) {
            0 -> "GPS质量未知"
            1 -> "GPS质量好"
            2 -> "GPS质量中等"
            3 -> "GPS质量差"
            4 -> "GPS质量极差"
            else -> "GPS未开启"
        }
    }
    
    /**
     * 设置加载状态
     */
    fun setLoading(isLoading: Boolean) {
        _uiState.update { it.copy(isLoading = isLoading) }
    }
    
    /**
     * 设置错误信息
     */
    fun setError(errorMessage: String) {
        _uiState.update { 
            it.copy(
                error = errorMessage,
                isLoading = false,
                message = "定位失败: $errorMessage"
            )
        }
    }
    
    /**
     * 开始定位
     */
    fun startLocation() {
        _uiState.update { it.copy(isLoading = true, message = "正在定位...") }
        locationRepository.startLocation()
    }
    
    /**
     * 停止定位
     */
    fun stopLocation() {
        _uiState.update { it.copy(isLoading = false, message = "定位已停止") }
        locationRepository.stopLocation()
    }
    
    /**
     * 更新上传状态
     */
    private fun updateUploadStatus(status: LocationUploadService.UploadStatus) {
        val statusMessage = when (status) {
            is LocationUploadService.UploadStatus.Idle -> "空闲"
            is LocationUploadService.UploadStatus.Uploading -> "上传中..."
            is LocationUploadService.UploadStatus.Success -> "上传成功 (${status.responseCode})"
            is LocationUploadService.UploadStatus.Error -> "上传失败: ${status.message}"
        }
        
        _uiState.update { it.copy(
            uploadStatus = statusMessage,
            isUploadEnabled = true
        ) }
    }
    
    /**
     * 设置是否启用上传
     */
    fun setUploadEnabled(enabled: Boolean) {
        locationRepository.setUploadEnabled(enabled)
        _uiState.update { it.copy(
            uploadStatus = if (enabled) "已启用" else "已禁用"
        ) }
    }
    
    /**
     * 设置上传间隔
     */
    fun setUploadInterval(intervalMillis: Long) {
        locationRepository.setUploadInterval(intervalMillis)
    }
    
    /**
     * 设置服务器URL
     */
    fun setServerUrl(url: String) {
        if (url.isNotEmpty()) {
            locationRepository.setServerUrl(url)
        }
    }
    
    /**
     * 获取服务器URL
     */
    fun getServerUrl(): String {
        return locationRepository.getServerUrl()
    }
    
    /**
     * 手动上传当前位置
     */
    fun uploadCurrentLocation() {
        locationRepository.uploadCurrentLocation()
    }
    
    /**
     * 设置用户名
     */
    fun setUserName(name: String) {
        if (name.isNotEmpty()) {
            locationRepository.setUserName(name)
            _uiState.update { it.copy(userName = name) }
        }
    }
    
    /**
     * 获取当前用户名
     */
    fun getUserName(): String {
        return locationRepository.getUserName()
    }
    
    /**
     * 设置定位唤醒间隔（分钟）
     */
    fun setLocationAlarmInterval(minutes: Int) {
        if (minutes >= 1) {
            val intervalMillis = minutes * 60 * 1000L
            locationRepository.setLocationAlarmInterval(intervalMillis)
            Log.d(TAG, "设置定位唤醒和后台上传间隔为 $minutes 分钟")
        }
    }
    
    /**
     * 设置应用是否在前台运行
     */
    fun setForegroundMode(inForeground: Boolean) {
        locationRepository.setForegroundMode(inForeground)
        Log.d(TAG, "设置应用模式为: ${if(inForeground) "前台" else "后台"}")
    }
} 