package com.example.android.architecture.blueprints.todoapp.location

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 位置仓库实现类
 */
@Singleton
class LocationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : LocationRepository {
    
    private val TAG = "LocationRepository"
    
    // 协程作用域
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 状态流
    private val _permissionGranted = MutableStateFlow(false)
    private val _serviceBound = MutableStateFlow(false)
    private val _locationError = MutableStateFlow<LocationError?>(null)
    
    // 定位服务
    private var locationService: LocationService? = null
    
    // 位置上传服务
    private var uploadService: LocationUploadService? = null
    
    // 服务连接
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            try {
                Log.d(TAG, "服务已连接")
                val localBinder = binder as LocationService.LocationBinder
                locationService = localBinder.getService()
                setServiceBound(true)
                
                // 如果已有权限，立即开始定位
                if (isPermissionGranted()) {
                    Log.d(TAG, "服务已连接，且有权限，开始定位")
                    startLocation()
                } else {
                    Log.d(TAG, "服务已连接，但无权限，等待权限")
                    _locationError.value = LocationError.PermissionDenied
                }
                
                // 监听位置服务的错误流
                repositoryScope.launch {
                    locationService?.errorFlow?.collect { error ->
                        if (error != null) {
                            _locationError.value = error
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "服务连接过程中发生错误", e)
                _locationError.value = LocationError.ServiceBindFailed
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            locationService = null
            setServiceBound(false)
        }
    }
    
    // 上传服务连接
    private val uploadServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            try {
                Log.d(TAG, "上传服务已连接")
                val localBinder = binder as LocationUploadService.UploadBinder
                uploadService = localBinder.getService()
                
                // 设置服务中的前台/后台状态
                uploadService?.setForegroundMode(true) // 默认为前台模式
            } catch (e: Exception) {
                Log.e(TAG, "连接上传服务时出错", e)
                _locationError.value = LocationError.UnknownError("连接上传服务时出错: ${e.message}")
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "上传服务已断开")
            uploadService = null
        }
    }
    
    init {
        bindLocationService()
        bindUploadService()
        checkLocationServices()
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
                _locationError.value = LocationError.ServiceBindFailed
            }
        } catch (e: Exception) {
            Log.e(TAG, "绑定定位服务失败", e)
            _locationError.value = LocationError.UnknownError("绑定定位服务失败: ${e.message}")
        }
    }
    
    /**
     * 解绑定位服务
     */
    private fun unbindLocationService() {
        if (_serviceBound.value) {
            context.unbindService(serviceConnection)
            setServiceBound(false)
            Log.d(TAG, "解绑定位服务")
        }
    }
    
    /**
     * 绑定上传服务
     */
    private fun bindUploadService() {
        try {
            val intent = Intent(context, LocationUploadService::class.java)
            val bound = context.bindService(intent, uploadServiceConnection, Context.BIND_AUTO_CREATE)
            Log.d(TAG, "尝试绑定上传服务: $bound")
        } catch (e: Exception) {
            Log.e(TAG, "绑定上传服务失败", e)
            _locationError.value = LocationError.UnknownError("绑定上传服务失败: ${e.message}")
        }
    }
    
    /**
     * 解绑上传服务
     */
    private fun unbindUploadService() {
        if (uploadService != null) {
            try {
                context.unbindService(uploadServiceConnection)
                uploadService = null
            } catch (e: Exception) {
                Log.e(TAG, "解绑上传服务失败", e)
            }
        }
    }
    
    /**
     * 清理资源
     */
    override fun cleanup() {
        unbindLocationService()
        unbindUploadService()
    }
    
    override fun getLocationDataFlow(): Flow<LocationData?> {
        return locationService?.locationDataFlow ?: MutableStateFlow(null)
    }
    
    /**
     * 获取当前位置（用于地图定位）
     */
    override suspend fun getCurrentLocation(): LocationData? {
        return locationService?.getLastLocation()
    }
    
    override fun startLocation() {
        locationService?.startLocation()
    }
    
    override fun stopLocation() {
        locationService?.stopLocation()
    }
    
    override fun setServiceBound(bound: Boolean) {
        _serviceBound.value = bound
    }
    
    override fun isServiceBound(): Boolean {
        return _serviceBound.value
    }
    
    /**
     * 获取服务绑定状态流
     */
    override fun getServiceBoundFlow(): Flow<Boolean> {
        return _serviceBound.asStateFlow()
    }
    
    override fun checkLocationServices() {
        // 检查GPS是否开启，这个逻辑已经移到LocationService中
    }
    
    override fun updatePermissionStatus(isGranted: Boolean) {
        Log.d(TAG, "更新位置权限状态: $isGranted")
        _permissionGranted.value = isGranted
        
        if (isGranted && isServiceBound()) {
            startLocation()
        } else if (!isGranted) {
            _locationError.value = LocationError.PermissionDenied
        }
    }
    
    override fun isPermissionGranted(): Boolean {
        return _permissionGranted.value
    }
    
    override fun getLocationError(): LocationError? {
        return _locationError.value
    }
    
    /**
     * 获取错误信息流
     */
    override fun getErrorFlow(): Flow<LocationError?> {
        return _locationError.asStateFlow()
    }
    
    override fun setLocationAlarmInterval(intervalMillis: Long) {
        locationService?.setLocationAlarmInterval(intervalMillis)
        
        // 同步到设置仓库
        repositoryScope.launch {
            settingsRepository.setLocationUpdateInterval(intervalMillis)
        }
    }
    
    override fun setUploadEnabled(enabled: Boolean) {
        uploadService?.setUploadEnabled(enabled)
        
        // 同步到设置仓库
        repositoryScope.launch {
            settingsRepository.setUploadEnabled(enabled)
        }
    }
    
    override fun setUploadInterval(intervalMillis: Long) {
        uploadService?.setUploadInterval(intervalMillis)
        
        // 同步到设置仓库
        repositoryScope.launch {
            settingsRepository.setUploadInterval(intervalMillis)
        }
    }
    
    override fun setServerUrl(url: String) {
        uploadService?.setServerUrl(url)
        
        // 同步到设置仓库
        repositoryScope.launch {
            settingsRepository.setServerUrl(url)
        }
    }
    
    override fun getServerUrl(): String {
        // 从设置仓库获取最新值
        return "" // 不再直接获取，而是通过Flow获取
    }
    
    override fun uploadCurrentLocation() {
        repositoryScope.launch {
            val latestLocation = getCurrentLocation()
            if (latestLocation != null) {
                // 由于不再支持直接触发上传，所以这里不执行任何操作
                // 上传服务会根据间隔自动上传最新位置
                Log.d(TAG, "不再支持手动触发上传，上传服务会根据间隔自动上传")
            }
        }
    }
    
    override fun getUploadStatusFlow(): Flow<LocationUploadService.UploadStatus> {
        return uploadService?.uploadStatus ?: MutableStateFlow(LocationUploadService.UploadStatus.Idle)
    }
    
    override fun setUserName(name: String) {
        uploadService?.setUserName(name)
        
        // 同步到设置仓库
        repositoryScope.launch {
            settingsRepository.setUserName(name)
        }
    }
    
    override fun getUserName(): String {
        // 从设置仓库获取最新值
        return "" // 不再直接获取，而是通过Flow获取
    }
    
    override fun setForegroundMode(inForeground: Boolean) {
        uploadService?.setForegroundMode(inForeground)
    }
} 