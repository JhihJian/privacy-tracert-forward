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

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.Handler
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.annotation.RequiresApi
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.android.architecture.blueprints.todoapp.R
import kotlinx.coroutines.flow.update
import android.net.ConnectivityManager
import android.content.ComponentName
import android.content.ServiceConnection
import com.example.android.architecture.blueprints.todoapp.location.AlarmService.Companion.ACTION_LOCATION_ALARM

/**
 * 定位服务，提供持续的位置更新功能
 */
class LocationService : Service() {
    
    private val TAG = "LocationService"
    
    // 通知相关常量
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "LocationServiceChannel"
        const val NOTIFICATION_CHANNEL_NAME = "Location Service"
        const val NOTIFICATION_ID = 101 // 前台服务通知的唯一ID
    }
    
    // 定位客户端
    private var locationClient: AMapLocationClient? = null
    
    // 定位选项
    private var locationOption: AMapLocationClientOption? = null
    
    // 位置数据流
    private val _locationFlow = MutableStateFlow<AMapLocation?>(null)
    private val _locationDataFlow = MutableStateFlow<LocationData?>(null)
    val locationFlow: StateFlow<AMapLocation?> = _locationFlow.asStateFlow()
    val locationDataFlow: StateFlow<LocationData?> = _locationDataFlow.asStateFlow()
    
    // 绑定服务的Binder
    private val binder = LocationBinder()
    
    // AlarmManager相关
    private lateinit var alarmManager: AlarmManager
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var locationIntent: PendingIntent
    private var isAlarmEnabled = false
    
    // 定位唤醒间隔 (默认1分钟)
    private var alarmInterval = 60 * 1000L
    
    // 定位状态
    private var isLocationEnabled = false
    
    // 位置更新广播接收器
    private val locationAlarmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_LOCATION_ALARM) {
                Log.d(TAG, "收到定位唤醒广播")
                acquireWakeLock()
                performLocationUpdate()
            }
        }
    }
    
    // 定位监听器
    private val locationListener = AMapLocationListener { location ->
        try {
            if (location.errorCode == 0) {
                // 定位成功
                _locationFlow.update { location }
                
                // 转换为LocationData并更新
                val locationData = convertToLocationData(location)
                _locationDataFlow.update { locationData }
                
                Log.i(TAG, "定位成功: ${location.address}, 经纬度: ${location.latitude},${location.longitude}")
                
                // 发送位置更新的广播，以便LocationUploadService可以接收并处理上传
                sendLocationBroadcast()
            } else {
                // 定位失败
                Log.e(TAG, "定位失败: ${location.errorCode}, ${location.errorInfo}, 当前位置: ${location.latitude},${location.longitude}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理定位结果时发生异常", e)
        }
    }
    
    private var alarmService: AlarmService? = null
    private var isAlarmServiceBound = false
    
    // 闹钟服务连接
    private val alarmServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AlarmService.AlarmBinder
            alarmService = binder.getService()
            isAlarmServiceBound = true
            Log.d(TAG, "AlarmService已连接")
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            isAlarmServiceBound = false
            alarmService = null
            Log.d(TAG, "AlarmService已断开连接")
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "LocationService onCreate")
        
        // 检查GPS是否启用
        checkGpsStatus()
        initLocationClient()
        
        // 绑定闹钟服务
        val alarmIntent = Intent(this, AlarmService::class.java)
        bindService(alarmIntent, alarmServiceConnection, Context.BIND_AUTO_CREATE)
        
        // 创建通知渠道 (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
        
        // 启动前台服务
        try {
            startForeground(NOTIFICATION_ID, buildForegroundNotification())
            Log.d(TAG, "前台服务启动成功")
        } catch (e: Exception) {
            Log.e(TAG, "启动前台服务失败", e)
            // 如果启动前台服务失败，尝试停止服务
            stopSelf()
            return
        }
        
        // 注册广播接收器
        try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Context.RECEIVER_NOT_EXPORTED
            } else {
                0
            }
            registerReceiver(locationAlarmReceiver, IntentFilter(ACTION_LOCATION_ALARM), flags)
            Log.d(TAG, "广播接收器注册成功")
        } catch (e: Exception) {
            Log.e(TAG, "注册广播接收器失败", e)
        }
        
        // 初始化WakeLock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LocationService::LocationWakeLock"
        )
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "LocationService onStartCommand")
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        // 解绑闹钟服务
        if (isAlarmServiceBound) {
            unbindService(alarmServiceConnection)
        }
        
        try {
            stopLocation()
            destroyLocationClient()
            releaseWakeLock()
            
            try {
                unregisterReceiver(locationAlarmReceiver)
            } catch (e: Exception) {
                Log.e(TAG, "注销广播接收器失败", e)
            }
            
            stopForeground(STOP_FOREGROUND_REMOVE) // 移除通知
        } catch (e: Exception) {
            Log.e(TAG, "服务销毁过程中发生异常", e)
        }
        super.onDestroy()
    }
    
    /**
     * 创建通知渠道 (Android 8.0及以上需要)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    NOTIFICATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW // 低重要性，避免声音和振动
                ).apply {
                    description = "持续进行后台定位"
                    setShowBadge(false) // 不显示角标
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(channel)
                Log.d(TAG, "通知渠道已创建: $NOTIFICATION_CHANNEL_ID")
            } catch (e: Exception) {
                Log.e(TAG, "创建通知渠道失败", e)
            }
        }
    }
    
    /**
     * 构建前台服务通知
     */
    private fun buildForegroundNotification(): Notification {
        return try {
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("定位服务运行中")
                .setContentText("正在后台持续获取位置信息")
                .setSmallIcon(R.drawable.ic_my_location)
                .setOngoing(true) // 使通知持续存在，不可滑动清除
                .setPriority(NotificationCompat.PRIORITY_LOW) // 低优先级
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "构建通知失败", e)
            // 返回一个基本的通知，避免服务崩溃
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("定位服务")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .build()
        }
    }
    
    /**
     * 初始化定位客户端
     */
    private fun initLocationClient() {
        try {
            Log.d(TAG, "开始初始化高德地图定位客户端")
            
            // 检查网络连接
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo = connectivityManager.activeNetworkInfo
            if (networkInfo == null || !networkInfo.isConnected) {
                Log.e(TAG, "网络未连接，无法初始化定位服务")
                return
            }
            
            // 初始化定位客户端
            locationClient = AMapLocationClient(applicationContext)
            locationOption = AMapLocationClientOption().apply {
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                isOnceLocation = false
                interval = 2000
                isNeedAddress = true
                isLocationCacheEnable = false
            }
            
            locationClient?.apply {
                setLocationOption(locationOption)
                setLocationListener(locationListener)
                startLocation()
            }
            
            Log.d(TAG, "高德地图定位客户端初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "初始化高德地图定位客户端失败", e)
            // 尝试重新初始化
            Handler(mainLooper).postDelayed({
                initLocationClient()
            }, 5000) // 5秒后重试
        }
    }
    
    /**
     * 开始定位
     */
    fun startLocation() {
        try {
            if (!isLocationEnabled) {
                locationClient?.startLocation()
                isLocationEnabled = true
                Log.d(TAG, "定位已启动")
                
                // 启动闹钟
                if (isAlarmServiceBound) {
                    alarmService?.startAlarm()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动定位失败", e)
        }
    }
    
    /**
     * 停止定位
     */
    fun stopLocation() {
        try {
            if (isLocationEnabled) {
                locationClient?.stopLocation()
                isLocationEnabled = false
                Log.d(TAG, "定位已停止")
                
                // 停止闹钟
                if (isAlarmServiceBound) {
                    alarmService?.stopAlarm()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "停止定位失败", e)
        }
    }
    
    /**
     * 销毁定位客户端
     */
    private fun destroyLocationClient() {
        try {
            locationClient?.apply {
                stopLocation()
                onDestroy()
            }
            locationClient = null
            locationOption = null
            Log.d(TAG, "高德地图定位客户端已销毁")
        } catch (e: Exception) {
            Log.e(TAG, "销毁高德地图定位客户端失败", e)
        }
    }
    
    /**
     * 检查GPS状态
     */
    private fun checkGpsStatus() {
        try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
            
            Log.i(TAG, "GPS状态: ${if (isGpsEnabled) "开启" else "关闭"}, 网络定位状态: ${if (isNetworkEnabled) "开启" else "关闭"}")
            
            if (!isGpsEnabled && !isNetworkEnabled) {
                Log.e(TAG, "GPS和网络定位都未开启，定位功能可能无法正常工作")
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查GPS状态失败", e)
        }
    }
    
    /**
     * 将AMapLocation转换为LocationData
     */
    private fun convertToLocationData(location: AMapLocation): LocationData {
        return LocationData(
            address = location.address ?: "",
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            time = location.time,
            // 其他必要的位置信息字段
            country = location.country ?: "",
            province = location.province ?: "",
            city = location.city ?: "",
            district = location.district ?: "",
            street = location.street ?: "",
            streetNum = location.streetNum ?: "",
            cityCode = location.cityCode ?: "",
            adCode = location.adCode ?: "",
            poiName = location.poiName ?: "",
            aoiName = location.aoiName ?: "",
            buildingId = location.buildingId ?: "",
            floor = location.floor ?: "",
            gpsAccuracyStatus = location.gpsAccuracyStatus,
            locationType = location.locationType,
            speed = location.speed,
            bearing = location.bearing,
            altitude = location.altitude,
            errorCode = location.errorCode,
            errorInfo = location.errorInfo ?: "",
            coordType = location.coordType ?: "",
            locationDetail = location.locationDetail ?: ""
        )
    }
    
    /**
     * 用于客户端绑定服务的Binder
     */
    inner class LocationBinder : Binder() {
        fun getService(): LocationService = this@LocationService
    }
    
    /**
     * 初始化AlarmManager
     */
    private fun initAlarmManager() {
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ACTION_LOCATION_ALARM)
        locationIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or 
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
    }
    
    /**
     * 获取WakeLock
     */
    private fun acquireWakeLock() {
        try {
            if (!wakeLock.isHeld) {
                wakeLock.acquire(5 * 60 * 1000L) // 最多持有5分钟
                Log.d(TAG, "获取WakeLock成功")
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取WakeLock失败", e)
        }
    }
    
    /**
     * 释放WakeLock
     */
    private fun releaseWakeLock() {
        try {
            if (wakeLock.isHeld) {
                wakeLock.release()
                Log.d(TAG, "释放WakeLock成功")
            }
        } catch (e: Exception) {
            Log.e(TAG, "释放WakeLock失败", e)
        }
    }
    
    /**
     * 执行定位更新
     */
    private fun performLocationUpdate() {
        try {
            Log.d(TAG, "执行定位更新")
            
            // 确保客户端初始化
            if (locationClient == null) {
                Log.e(TAG, "定位客户端为null，重新初始化")
                initLocationClient()
            }
            
            // 设置为单次定位
            locationOption?.isOnceLocation = true
            locationClient?.setLocationOption(locationOption)
            
            // 开始定位
            locationClient?.startLocation()
            
            // 确保数据上传 - 等待2秒让定位完成，然后强制触发一次上传
            Handler().postDelayed({
                // 发送明确的位置更新广播以触发上传
                sendLocationBroadcast()
                
                // 释放WakeLock
                releaseWakeLock()
            }, 2000)
            
        } catch (e: Exception) {
            Log.e(TAG, "执行定位更新失败", e)
            releaseWakeLock()
        }
    }
    
    /**
     * 设置定位唤醒间隔（毫秒）
     * @param intervalMillis 间隔时间，单位毫秒
     * @return 是否设置成功
     */
    fun setLocationAlarmInterval(intervalMillis: Long): Boolean {
        return if (isAlarmServiceBound) {
            alarmService?.setLocationAlarmInterval(intervalMillis) ?: false
        } else {
            Log.e(TAG, "设置定位间隔失败：AlarmService未连接")
            false
        }
    }
    
    /**
     * 获取当前定位间隔（毫秒）
     */
    fun getLocationAlarmInterval(): Long {
        return if (isAlarmServiceBound) {
            alarmService?.getLocationAlarmInterval() ?: 60 * 1000L
        } else {
            60 * 1000L
        }
    }
    
    /**
     * 获取最小定位间隔（毫秒）
     */
    fun getMinLocationAlarmInterval(): Long {
        return if (isAlarmServiceBound) {
            alarmService?.getMinLocationAlarmInterval() ?: 60 * 1000L
        } else {
            60 * 1000L
        }
    }
    
    /**
     * 获取最大定位间隔（毫秒）
     */
    fun getMaxLocationAlarmInterval(): Long {
        return if (isAlarmServiceBound) {
            alarmService?.getMaxLocationAlarmInterval() ?: 30 * 60 * 1000L
        } else {
            30 * 60 * 1000L
        }
    }
    
    /**
     * 发送位置更新的广播
     */
    private fun sendLocationBroadcast() {
        try {
            val intent = Intent("com.example.android.architecture.blueprints.todoapp.LOCATION_UPDATED")
            // 我们不直接传递AMapLocation对象，而是传递相关信息
            intent.putExtra("isSuccessful", true)
            sendBroadcast(intent)
            Log.d(TAG, "已发送位置更新广播")
        } catch (e: Exception) {
            Log.e(TAG, "发送位置更新广播失败", e)
        }
    }
}

// 位置数据DTO，用于传递给ViewModel，不暴露高德地图API
data class LocationData(
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val time: Long = 0L,
    // 其他必要的位置信息字段
    val country: String = "",
    val province: String = "",
    val city: String = "",
    val district: String = "",
    val street: String = "",
    val streetNum: String = "",
    val cityCode: String = "",
    val adCode: String = "",
    val poiName: String = "",
    val aoiName: String = "",
    val buildingId: String = "",
    val floor: String = "",
    val gpsAccuracyStatus: Int = 0,
    val locationType: Int = 0,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val altitude: Double = 0.0,
    val errorCode: Int = 0,
    val errorInfo: String = "",
    val coordType: String = "",
    val locationDetail: String = ""
) 