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

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import android.content.pm.PackageManager
import javax.inject.Inject

/**
 * 位置上传服务，负责将位置数据上传到指定服务器
 */
class LocationUploadService : Service() {
    
    private val TAG = "LocationUploadService"
    
    // 协程作用域
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // OkHttp客户端
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    // 设置仓库
    private var settingsRepository: SettingsRepository? = null
    
    // 位置服务
    private var locationService: LocationService? = null
    
    // 位置数据收集任务
    private var locationCollectionJob: Job? = null
    
    // 设置收集任务
    private var settingsCollectionJob: Job? = null
    
    // 上传状态
    private val _uploadStatus = MutableStateFlow<UploadStatus>(UploadStatus.Idle)
    val uploadStatus: StateFlow<UploadStatus> = _uploadStatus.asStateFlow()
    
    // 上次上传时间
    private var lastUploadTime = 0L
    
    // 配置参数
    private var isUploadEnabled = true
    private var uploadInterval = 5000L // 默认5秒
    private var backgroundUploadInterval = 3 * 60 * 1000L // 默认3分钟
    private var isInForeground = true
    private var serverUrl = ""
    private var userName = "默认用户"
    
    // 绑定位置服务的连接
    private val locationServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "位置服务已连接")
            locationService = (binder as LocationService.LocationBinder).getService()
            startCollectingLocation()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "位置服务已断开")
            locationService = null
            stopCollectingLocation()
        }
    }
    
    // 暴露给客户端的Binder
    private val binder = UploadBinder()
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "位置上传服务已创建")
        
        // 尝试获取SettingsRepository（如果有DI配置）
        try {
            settingsRepository = (application as? SettingsProvider)?.provideSettingsRepository()
            if (settingsRepository != null) {
                Log.d(TAG, "成功获取SettingsRepository")
                startCollectingSettings()
            } else {
                Log.w(TAG, "无法获取SettingsRepository，将使用默认配置")
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取SettingsRepository时出错", e)
        }
        
        // 绑定位置服务
        val intent = Intent(this, LocationService::class.java)
        bindService(intent, locationServiceConnection, Context.BIND_AUTO_CREATE)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "位置上传服务已启动")
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        stopCollectingLocation()
        stopCollectingSettings()
        
        // 解绑位置服务
        try {
            unbindService(locationServiceConnection)
        } catch (e: Exception) {
            Log.e(TAG, "解绑位置服务失败", e)
        }
        
        super.onDestroy()
        Log.d(TAG, "位置上传服务已销毁")
    }
    
    /**
     * 开始收集位置数据
     */
    private fun startCollectingLocation() {
        locationCollectionJob?.cancel()
        locationCollectionJob = serviceScope.launch {
            locationService?.locationDataFlow?.collect { locationData ->
                if (locationData != null && isUploadEnabled) {
                    val currentTime = System.currentTimeMillis()
                    // 检查是否达到上传间隔
                    if (currentTime - lastUploadTime >= getCurrentUploadInterval()) {
                        uploadLocationData(locationData)
                        lastUploadTime = currentTime
                    }
                }
            }
        }
        Log.d(TAG, "开始收集位置数据")
    }
    
    /**
     * 开始收集设置数据
     */
    private fun startCollectingSettings() {
        settingsCollectionJob?.cancel()
        settingsRepository?.let { repo ->
            settingsCollectionJob = serviceScope.launch {
                // 收集服务器URL
                launch {
                    repo.getServerUrl().collect { url ->
                        serverUrl = url
                        Log.d(TAG, "服务器URL已更新: $url")
                    }
                }
                
                // 收集用户名
                launch {
                    repo.getUserName().collect { name ->
                        userName = name
                        Log.d(TAG, "用户名已更新: $name")
                    }
                }
                
                // 收集上传状态
                launch {
                    repo.isUploadEnabled().collect { enabled ->
                        isUploadEnabled = enabled
                        Log.d(TAG, "上传状态已更新: ${if (enabled) "启用" else "禁用"}")
                    }
                }
                
                // 收集前台上传间隔
                launch {
                    repo.getUploadInterval().collect { interval ->
                        uploadInterval = interval
                        Log.d(TAG, "前台上传间隔已更新: $interval ms")
                    }
                }
                
                // 收集后台上传间隔
                launch {
                    repo.getBackgroundUploadInterval().collect { interval ->
                        backgroundUploadInterval = interval
                        Log.d(TAG, "后台上传间隔已更新: $interval ms")
                    }
                }
            }
        }
    }
    
    /**
     * 停止收集位置数据
     */
    private fun stopCollectingLocation() {
        locationCollectionJob?.cancel()
        locationCollectionJob = null
        Log.d(TAG, "停止收集位置数据")
    }
    
    /**
     * 停止收集设置数据
     */
    private fun stopCollectingSettings() {
        settingsCollectionJob?.cancel()
        settingsCollectionJob = null
        Log.d(TAG, "停止收集设置数据")
    }
    
    /**
     * 获取当前上传间隔（根据前台/后台状态）
     */
    private fun getCurrentUploadInterval(): Long {
        return if (isInForeground) uploadInterval else backgroundUploadInterval
    }
    
    /**
     * 设置应用是否在前台运行
     */
    fun setForegroundMode(inForeground: Boolean) {
        this.isInForeground = inForeground
        Log.d(TAG, "应用前台状态已更新: ${if (inForeground) "前台" else "后台"}")
    }
    
    /**
     * 设置是否启用上传
     */
    fun setUploadEnabled(enabled: Boolean) {
        isUploadEnabled = enabled
        Log.d(TAG, "位置上传功能已${if (enabled) "启用" else "禁用"}")
        
        // 同步到设置仓库
        settingsRepository?.let { repo ->
            serviceScope.launch {
                try {
                    repo.setUploadEnabled(enabled)
                } catch (e: Exception) {
                    Log.e(TAG, "保存上传状态到设置仓库失败", e)
                }
            }
        }
    }
    
    /**
     * 设置上传间隔
     */
    fun setUploadInterval(intervalMillis: Long) {
        uploadInterval = intervalMillis
        Log.d(TAG, "位置上传间隔已设置为 $intervalMillis 毫秒（前台模式）")
        
        // 同步到设置仓库
        settingsRepository?.let { repo ->
            serviceScope.launch {
                try {
                    repo.setUploadInterval(intervalMillis)
                } catch (e: Exception) {
                    Log.e(TAG, "保存上传间隔到设置仓库失败", e)
                }
            }
        }
    }
    
    /**
     * 设置后台上传间隔
     */
    fun setBackgroundUploadInterval(intervalMillis: Long) {
        backgroundUploadInterval = intervalMillis
        Log.d(TAG, "后台位置上传间隔已设置为 $intervalMillis 毫秒")
        
        // 同步到设置仓库
        settingsRepository?.let { repo ->
            serviceScope.launch {
                try {
                    repo.setBackgroundUploadInterval(intervalMillis)
                } catch (e: Exception) {
                    Log.e(TAG, "保存后台上传间隔到设置仓库失败", e)
                }
            }
        }
    }
    
    /**
     * 设置服务器URL
     */
    fun setServerUrl(url: String) {
        serverUrl = url
        Log.d(TAG, "服务器URL已设置为: $url")
        
        // 同步到设置仓库
        settingsRepository?.let { repo ->
            serviceScope.launch {
                try {
                    repo.setServerUrl(url)
                } catch (e: Exception) {
                    Log.e(TAG, "保存服务器URL到设置仓库失败", e)
                }
            }
        }
    }
    
    /**
     * 设置用户名
     */
    fun setUserName(name: String) {
        userName = name
        Log.d(TAG, "用户名已设置为: $name")
        
        // 同步到设置仓库
        settingsRepository?.let { repo ->
            serviceScope.launch {
                try {
                    repo.setUserName(name)
                } catch (e: Exception) {
                    Log.e(TAG, "保存用户名到设置仓库失败", e)
                }
            }
        }
    }
    
    /**
     * 上传位置数据
     */
    private fun uploadLocationData(locationData: LocationData) {
        serviceScope.launch {
            try {
                // 检查URL是否有效
                if (serverUrl.isEmpty()) {
                    Log.e(TAG, "服务器URL未设置，无法上传位置数据")
                    _uploadStatus.update { UploadStatus.Error("服务器URL未设置") }
                    return@launch
                }
                
                _uploadStatus.update { UploadStatus.Uploading }
                
                // 格式化位置数据为JSON
                val jsonData = formatLocationToJson(locationData)
                
                // 创建请求体
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = jsonData.toRequestBody(mediaType)
                
                // 创建请求
                val request = Request.Builder()
                    .url(serverUrl)
                    .post(requestBody)
                    .header("Content-Type", "application/json")
                    .build()
                
                // 执行请求
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.i(TAG, "位置数据上传成功: ${response.code}")
                        _uploadStatus.update { UploadStatus.Success(response.code) }
                    } else {
                        Log.e(TAG, "位置数据上传失败: ${response.code}, ${response.message}")
                        _uploadStatus.update { UploadStatus.Error("服务器响应错误: ${response.code}") }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "发送位置数据到服务器时出错", e)
                _uploadStatus.update { UploadStatus.Error(e.message ?: "未知错误") }
            }
        }
    }
    
    /**
     * 格式化位置数据为JSON字符串
     */
    private fun formatLocationToJson(locationData: LocationData): String {
        return try {
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            val formattedDate = dateFormat.format(java.util.Date(locationData.time))
            
            """
            {
              "timestamp": "${formattedDate}",
              "latitude": ${locationData.latitude},
              "longitude": ${locationData.longitude},
              "accuracy": ${locationData.accuracy},
              "address": "${locationData.address}",
              "country": "${locationData.country}",
              "province": "${locationData.province}",
              "city": "${locationData.city}",
              "district": "${locationData.district}",
              "street": "${locationData.street}",
              "streetNum": "${locationData.streetNum}",
              "cityCode": "${locationData.cityCode}",
              "adCode": "${locationData.adCode}",
              "poiName": "${locationData.poiName}",
              "aoiName": "${locationData.aoiName}",
              "buildingId": "${locationData.buildingId}",
              "floor": "${locationData.floor}",
              "gpsAccuracyStatus": ${locationData.gpsAccuracyStatus},
              "locationType": ${locationData.locationType},
              "speed": ${locationData.speed},
              "bearing": ${locationData.bearing},
              "altitude": ${locationData.altitude},
              "errorCode": ${locationData.errorCode},
              "errorInfo": "${locationData.errorInfo}",
              "userName": "${userName}"
            }
            """.trimIndent()
        } catch (e: Exception) {
            Log.e(TAG, "格式化位置数据失败", e)
            "{\"error\": \"格式化位置数据失败: ${e.message}\"}"
        }
    }
    
    /**
     * 上传状态封装类
     */
    sealed class UploadStatus {
        object Idle : UploadStatus()
        object Uploading : UploadStatus()
        data class Success(val responseCode: Int) : UploadStatus()
        data class Error(val message: String) : UploadStatus()
    }
    
    /**
     * 用于客户端绑定服务的Binder
     */
    inner class UploadBinder : Binder() {
        fun getService(): LocationUploadService = this@LocationUploadService
    }
    
    /**
     * 用于应用提供Settings仓库的接口
     */
    interface SettingsProvider {
        fun provideSettingsRepository(): SettingsRepository
    }
} 