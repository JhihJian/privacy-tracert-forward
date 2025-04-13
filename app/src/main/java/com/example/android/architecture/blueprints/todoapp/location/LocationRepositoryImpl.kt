package com.example.android.architecture.blueprints.todoapp.location

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 位置仓库实现类
 */
@Singleton
class LocationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : LocationRepository {
    
    private val TAG = "LocationRepository"
    
    // 状态流
    private val _permissionGranted = MutableStateFlow(false)
    private val _serviceBound = MutableStateFlow(false)
    private val _locationError = MutableStateFlow<String?>(null)
    private val _isServiceBound = MutableStateFlow(false)
    
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
                }
            } catch (e: Exception) {
                Log.e(TAG, "服务连接过程中发生错误", e)
                _locationError.value = "服务连接错误: ${e.message}"
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
            } catch (e: Exception) {
                Log.e(TAG, "连接上传服务时出错", e)
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
                _locationError.value = "绑定服务失败"
            }
        } catch (e: Exception) {
            Log.e(TAG, "绑定定位服务失败", e)
            _locationError.value = e.message
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
    
    override fun startLocation() {
        if (_serviceBound.value) {
            locationService?.startLocation()
        }
    }
    
    override fun stopLocation() {
        if (_serviceBound.value) {
            locationService?.stopLocation()
        }
    }
    
    override fun setServiceBound(bound: Boolean) {
        _serviceBound.value = bound
        _isServiceBound.value = bound
    }
    
    override fun isServiceBound(): Boolean {
        return _serviceBound.value
    }
    
    /**
     * 获取服务绑定状态流
     */
    override fun getServiceBoundFlow(): Flow<Boolean> {
        return _isServiceBound.asStateFlow()
    }
    
    override fun checkLocationServices() {
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
            
            Log.i(TAG, "GPS状态: ${if (isGpsEnabled) "开启" else "关闭"}, 网络定位状态: ${if (isNetworkEnabled) "开启" else "关闭"}")
            
            if (!isGpsEnabled && !isNetworkEnabled) {
                _locationError.value = "定位服务未开启"
                
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
    
    override fun updatePermissionStatus(isGranted: Boolean) {
        Log.d(TAG, "更新权限状态: isGranted=$isGranted, 当前ServiceBound=${_serviceBound.value}")
        
        _permissionGranted.value = isGranted
        
        // 如果权限已授予但服务未绑定，绑定服务
        if (isGranted && locationService == null) {
            bindLocationService()
        }
        
        // 如果权限已授予且服务已绑定，开始定位
        if (isGranted && _serviceBound.value) {
            locationService?.startLocation()
        }
    }
    
    override fun isPermissionGranted(): Boolean {
        return _permissionGranted.value
    }
    
    override fun getLocationError(): String? {
        return _locationError.value
    }
    
    /**
     * 获取错误信息流
     */
    override fun getErrorFlow(): Flow<String?> {
        return _locationError.asStateFlow()
    }
    
    override fun setLocationAlarmInterval(intervalMillis: Long) {
        if (intervalMillis >= 60000) { // 至少1分钟
            locationService?.setLocationAlarmInterval(intervalMillis)
            // 同时设置后台上传间隔
            uploadService?.setBackgroundUploadInterval(intervalMillis)
            Log.d(TAG, "设置定位唤醒和后台上传间隔为 ${intervalMillis/60000} 分钟")
        }
    }
    
    override fun setUploadEnabled(enabled: Boolean) {
        uploadService?.setUploadEnabled(enabled)
    }
    
    override fun setUploadInterval(intervalMillis: Long) {
        uploadService?.setUploadInterval(intervalMillis)
    }
    
    override fun setServerUrl(url: String) {
        if (url.isNotEmpty()) {
            uploadService?.setServerUrl(url)
        }
    }
    
    override fun getServerUrl(): String {
        return uploadService?.getServerUrl() ?: ""
    }
    
    override fun uploadCurrentLocation() {
        uploadService?.uploadLatestLocation()
    }
    
    override fun getUploadStatusFlow(): Flow<LocationUploadService.UploadStatus> {
        return uploadService?.uploadStatus ?: MutableStateFlow(LocationUploadService.UploadStatus.Idle)
    }
    
    override fun setUserName(name: String) {
        if (name.isNotEmpty()) {
            uploadService?.setUserName(name)
        }
    }
    
    override fun getUserName(): String {
        return uploadService?.getUserName() ?: "默认用户"
    }
    
    override fun setForegroundMode(inForeground: Boolean) {
        uploadService?.setForegroundMode(inForeground)
        Log.d(TAG, "设置应用模式为: ${if(inForeground) "前台" else "后台"}")
    }
} 