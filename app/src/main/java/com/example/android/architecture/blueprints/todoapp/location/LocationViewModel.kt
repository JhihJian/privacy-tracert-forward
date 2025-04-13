/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.architecture.blueprints.todoapp.location

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.amap.api.location.AMapLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val currentLocation: AMapLocation? = null,
    val locationJson: String = "",
    val message: String = "等待定位中...",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isServiceBound: Boolean = false
)

/**
 * 位置功能ViewModel
 */
@HiltViewModel
class LocationViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val TAG = "LocationViewModel"
    
    // UI状态
    private val _uiState = MutableStateFlow(LocationUiState())
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()
    
    // 定位服务
    private var locationService: LocationService? = null
    
    // 服务连接
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            try {
                Log.d(TAG, "服务已连接")
                val localBinder = binder as LocationService.LocationBinder
                locationService = localBinder.getService()
                _uiState.update { it.copy(isServiceBound = true) }
                
                // 订阅位置更新
                viewModelScope.launch {
                    locationService?.locationFlow?.collect { location ->
                        if (location != null) {
                            updateLocation(location)
                        }
                    }
                }
                
                // 如果已有权限，立即开始定位
                if (_uiState.value.isPermissionGranted) {
                    Log.d(TAG, "服务已连接，且有权限，开始定位")
                    startLocation()
                } else {
                    Log.d(TAG, "服务已连接，但无权限，等待权限")
                }
            } catch (e: Exception) {
                Log.e(TAG, "服务连接过程中发生错误", e)
                _uiState.update { it.copy(
                    error = "服务连接错误: ${e.message}",
                    message = "定位服务连接失败: ${e.message}"
                ) }
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            locationService = null
            _uiState.update { it.copy(isServiceBound = false) }
        }
    }
    
    init {
        checkLocationServices()
        bindLocationService()
    }
    
    override fun onCleared() {
        unbindLocationService()
        super.onCleared()
    }
    
    /**
     * 绑定定位服务
     */
    private fun bindLocationService() {
        try {
            val intent = Intent(context, LocationService::class.java)
            val bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            Log.d(TAG, "尝试绑定定位服务: $bound")
            
            if (!bound) {
                _uiState.update { it.copy(
                    message = "无法绑定定位服务",
                    error = "绑定服务失败"
                ) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "绑定定位服务失败", e)
            _uiState.update { it.copy(
                message = "绑定定位服务发生错误: ${e.message}",
                error = e.message
            ) }
        }
    }
    
    /**
     * 解绑定位服务
     */
    private fun unbindLocationService() {
        if (_uiState.value.isServiceBound) {
            context.unbindService(serviceConnection)
            _uiState.update { it.copy(isServiceBound = false) }
            Log.d(TAG, "解绑定位服务")
        }
    }
    
    /**
     * 更新权限状态
     */
    fun updatePermissionStatus(isGranted: Boolean) {
        Log.d(TAG, "更新权限状态: isGranted=$isGranted, 当前ServiceBound=${_uiState.value.isServiceBound}")
        
        _uiState.update { currentState ->
            currentState.copy(
                isPermissionGranted = isGranted,
                message = if (!isGranted) "需要位置权限才能使用实时定位功能" else "等待定位中..."
            )
        }
        
        // 如果权限已授予但服务未绑定，绑定服务
        if (isGranted && locationService == null) {
            bindLocationService()
        }
        
        // 如果权限已授予且服务已绑定，开始定位
        if (isGranted && _uiState.value.isServiceBound) {
            locationService?.startLocation()
        }
    }
    
    /**
     * 更新位置信息
     */
    fun updateLocation(location: AMapLocation) {
        viewModelScope.launch {
            val locationJson = LocationUtils.locationToJson(location)
            
            _uiState.update { currentState ->
                currentState.copy(
                    currentLocation = location,
                    locationJson = locationJson,
                    isLoading = false,
                    error = null,
                    message = if (location.errorCode == 0) "定位成功" else "定位失败: ${location.errorInfo}"
                )
            }
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
        if (_uiState.value.isServiceBound) {
            locationService?.startLocation()
            _uiState.update { it.copy(isLoading = true, message = "正在定位...") }
        }
    }
    
    /**
     * 停止定位
     */
    fun stopLocation() {
        if (_uiState.value.isServiceBound) {
            locationService?.stopLocation()
            _uiState.update { it.copy(isLoading = false, message = "定位已停止") }
        }
    }
    
    /**
     * 检查位置服务状态
     */
    private fun checkLocationServices() {
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
            
            Log.i(TAG, "GPS状态: ${if (isGpsEnabled) "开启" else "关闭"}, 网络定位状态: ${if (isNetworkEnabled) "开启" else "关闭"}")
            
            if (!isGpsEnabled && !isNetworkEnabled) {
                _uiState.update { it.copy(
                    message = "请开启GPS或网络定位服务",
                    error = "定位服务未开启"
                ) }
                
                // 显示开启GPS的提示
                showGpsSettings()
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查位置服务状态失败", e)
        }
    }
    
    /**
     * 显示GPS设置提示
     */
    private fun showGpsSettings() {
        try {
            // 创建一个Intent跳转到位置设置页面
            val intent = android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "无法打开GPS设置页面", e)
        }
    }
} 