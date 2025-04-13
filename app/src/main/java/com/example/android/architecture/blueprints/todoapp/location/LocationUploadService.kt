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
import com.amap.api.location.AMapLocation
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
    
    // 服务端API地址
    private var serverUrl = ""
    
    // 是否启用位置上传
    private var isUploadEnabled = true
    
    // 位置服务
    private var locationService: LocationService? = null
    
    // 位置数据收集任务
    private var locationCollectionJob: Job? = null
    
    // 上传状态
    private val _uploadStatus = MutableStateFlow<UploadStatus>(UploadStatus.Idle)
    val uploadStatus: StateFlow<UploadStatus> = _uploadStatus.asStateFlow()
    
    // 上次上传时间
    private var lastUploadTime = 0L
    
    // 上传间隔（毫秒）
    private var uploadInterval = 5000L // 默认5秒
    
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
        
        // 从AndroidManifest.xml读取服务器URL
        readServerUrlFromManifest()
        
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
            locationService?.locationFlow?.collect { location ->
                if (location != null && isUploadEnabled) {
                    val currentTime = System.currentTimeMillis()
                    // 检查是否达到上传间隔
                    if (currentTime - lastUploadTime >= uploadInterval) {
                        uploadLocation(location)
                        lastUploadTime = currentTime
                    }
                }
            }
        }
        Log.d(TAG, "开始收集位置数据")
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
     * 设置是否启用上传
     */
    fun setUploadEnabled(enabled: Boolean) {
        isUploadEnabled = enabled
        Log.d(TAG, "位置上传功能已${if (enabled) "启用" else "禁用"}")
    }
    
    /**
     * 设置上传间隔
     */
    fun setUploadInterval(intervalMillis: Long) {
        uploadInterval = intervalMillis
        Log.d(TAG, "位置上传间隔已设置为 $intervalMillis 毫秒")
    }
    
    /**
     * 设置服务器URL
     */
    fun setServerUrl(url: String) {
        if (url.isNotEmpty()) {
            serverUrl = url
            Log.d(TAG, "服务器URL已更改: $url")
        }
    }
    
    /**
     * 获取当前服务器URL
     */
    fun getServerUrl(): String {
        return serverUrl
    }
    
    /**
     * 手动上传最新位置
     */
    fun uploadLatestLocation() {
        locationService?.locationFlow?.value?.let { location ->
            uploadLocation(location)
        } ?: run {
            Log.e(TAG, "无可用的位置数据")
            _uploadStatus.update { UploadStatus.Error("无可用的位置数据") }
        }
    }
    
    /**
     * 上传位置数据到服务器
     */
    private fun uploadLocation(location: AMapLocation) {
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
                val jsonData = formatLocationToJson(location)
                
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
    private fun formatLocationToJson(location: AMapLocation): String {
        return try {
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            val formattedDate = dateFormat.format(java.util.Date(location.time))
            
            """
            {
              "timestamp": "${formattedDate}",
              "latitude": ${location.latitude},
              "longitude": ${location.longitude},
              "accuracy": ${location.accuracy},
              "address": "${location.address ?: ""}",
              "country": "${location.country ?: ""}",
              "province": "${location.province ?: ""}",
              "city": "${location.city ?: ""}",
              "district": "${location.district ?: ""}",
              "street": "${location.street ?: ""}",
              "streetNum": "${location.streetNum ?: ""}",
              "cityCode": "${location.cityCode ?: ""}",
              "adCode": "${location.adCode ?: ""}",
              "poiName": "${location.poiName ?: ""}",
              "aoiName": "${location.aoiName ?: ""}",
              "buildingId": "${location.buildingId ?: ""}",
              "floor": "${location.floor ?: ""}",
              "gpsAccuracyStatus": ${location.gpsAccuracyStatus},
              "locationType": ${location.locationType},
              "speed": ${location.speed},
              "bearing": ${location.bearing},
              "altitude": ${location.altitude},
              "errorCode": ${location.errorCode},
              "errorInfo": "${location.errorInfo ?: ""}"
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
     * 从AndroidManifest.xml读取服务器URL
     */
    private fun readServerUrlFromManifest() {
        try {
            val appInfo = packageManager.getApplicationInfo(
                packageName, 
                PackageManager.GET_META_DATA
            )
            
            val metaData = appInfo.metaData
            if (metaData != null && metaData.containsKey("com.example.location.server.url")) {
                val url = metaData.getString("com.example.location.server.url")
                if (!url.isNullOrEmpty()) {
                    serverUrl = url
                    Log.i(TAG, "从Manifest读取到服务器URL: $serverUrl")
                } else {
                    Log.e(TAG, "未从Manifest读取到有效的服务器URL")
                }
            } else {
                Log.e(TAG, "Manifest中未找到服务器URL配置")
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取Manifest中的服务器URL失败", e)
        }
    }
} 