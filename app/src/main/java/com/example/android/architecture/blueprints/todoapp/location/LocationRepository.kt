package com.example.android.architecture.blueprints.todoapp.location

import kotlinx.coroutines.flow.Flow

/**
 * 位置仓库接口
 * 定义与位置相关的数据操作
 */
interface LocationRepository {
    
    /**
     * 获取位置数据流
     */
    fun getLocationDataFlow(): Flow<LocationData?>
    
    /**
     * 获取当前位置（用于地图定位）
     */
    suspend fun getCurrentLocation(): LocationData?
    
    /**
     * 开始定位
     */
    fun startLocation()
    
    /**
     * 停止定位
     */
    fun stopLocation()
    
    /**
     * 设置位置服务是否已绑定
     */
    fun setServiceBound(bound: Boolean)
    
    /**
     * 获取位置服务是否已绑定
     */
    fun isServiceBound(): Boolean
    
    /**
     * 检查位置服务状态
     */
    fun checkLocationServices()
    
    /**
     * 更新权限状态
     */
    fun updatePermissionStatus(isGranted: Boolean)
    
    /**
     * 获取权限状态
     */
    fun isPermissionGranted(): Boolean
    
    /**
     * 获取服务绑定状态流
     */
    fun getServiceBoundFlow(): Flow<Boolean>
    
    /**
     * 获取定位服务错误信息
     */
    fun getLocationError(): LocationError?
    
    /**
     * 获取错误信息流
     */
    fun getErrorFlow(): Flow<LocationError?>
    
    /**
     * 设置定位唤醒间隔
     */
    fun setLocationAlarmInterval(intervalMillis: Long)
    
    /**
     * 设置是否启用上传
     */
    fun setUploadEnabled(enabled: Boolean)
    
    /**
     * 设置上传间隔
     */
    fun setUploadInterval(intervalMillis: Long)
    
    /**
     * 设置服务器URL
     */
    fun setServerUrl(url: String)
    
    /**
     * 获取服务器URL
     */
    fun getServerUrl(): String
    
    /**
     * 手动上传当前位置
     */
    fun uploadCurrentLocation()
    
    /**
     * 获取上传状态流
     */
    fun getUploadStatusFlow(): Flow<LocationUploadService.UploadStatus>
    
    /**
     * 设置用户名
     */
    fun setUserName(name: String)
    
    /**
     * 获取用户名
     */
    fun getUserName(): String
    
    /**
     * 设置应用是否在前台运行
     */
    fun setForegroundMode(inForeground: Boolean)
    
    /**
     * 清理资源
     */
    fun cleanup()
} 