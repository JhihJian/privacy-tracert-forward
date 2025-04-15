package com.example.android.architecture.blueprints.todoapp.location

import kotlinx.coroutines.flow.Flow

/**
 * 设置仓库接口，用于集中管理位置跟踪相关的配置
 */
interface SettingsRepository {
    /**
     * 获取服务器URL
     */
    fun getServerUrl(): Flow<String>
    
    /**
     * 设置服务器URL
     */
    suspend fun setServerUrl(url: String)
    
    /**
     * 获取用户名
     */
    fun getUserName(): Flow<String>
    
    /**
     * 设置用户名
     */
    suspend fun setUserName(name: String)
    
    /**
     * 获取位置上传状态 (是否启用)
     */
    fun isUploadEnabled(): Flow<Boolean>
    
    /**
     * 设置位置上传状态
     */
    suspend fun setUploadEnabled(enabled: Boolean)
    
    /**
     * 获取前台模式上传间隔 (毫秒)
     */
    fun getUploadInterval(): Flow<Long>
    
    /**
     * 设置前台模式上传间隔
     */
    suspend fun setUploadInterval(intervalMillis: Long)
    
    /**
     * 获取后台模式上传间隔 (毫秒)
     */
    fun getBackgroundUploadInterval(): Flow<Long>
    
    /**
     * 设置后台模式上传间隔
     */
    suspend fun setBackgroundUploadInterval(intervalMillis: Long)
    
    /**
     * 获取位置更新间隔 (毫秒)
     */
    fun getLocationUpdateInterval(): Flow<Long>
    
    /**
     * 设置位置更新间隔
     */
    suspend fun setLocationUpdateInterval(intervalMillis: Long)
} 