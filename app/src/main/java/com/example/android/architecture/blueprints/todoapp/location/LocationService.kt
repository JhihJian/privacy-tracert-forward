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
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 定位服务，提供持续的位置更新功能
 */
class LocationService : Service() {
    
    private val TAG = "LocationService"
    
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
    
    // 定位监听器
    private val locationListener = AMapLocationListener { location ->
        if (location != null) {
            if (location.errorCode == 0) {
                // 定位成功，更新位置数据
                _locationFlow.update { location }
                
                // 转换为LocationData并更新流
                val locationData = convertToLocationData(location)
                _locationDataFlow.update { locationData }
                
                Log.i(TAG, "定位成功: ${location.address}, 经纬度: ${location.latitude},${location.longitude}")
            } else {
                // 定位失败
                Log.e(TAG, "定位失败: ${location.errorCode}, ${location.errorInfo}, 当前位置: ${location.latitude},${location.longitude}")
            }
        } else {
            Log.e(TAG, "定位结果为null")
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        // 检查GPS是否启用
        checkGpsStatus()
        initLocationClient()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        stopLocation()
        destroyLocationClient()
        super.onDestroy()
    }
    
    /**
     * 初始化定位客户端
     */
    private fun initLocationClient() {
        try {
            // 确保隐私政策更新
            AMapLocationClient.updatePrivacyShow(this, true, true)
            AMapLocationClient.updatePrivacyAgree(this, true)
            
            // 创建定位客户端
            locationClient = AMapLocationClient(applicationContext)
            
            // 如果需要访问API Key (通常不需要，因为已在Manifest中设置)
            Log.d(TAG, "使用的高德地图Key: ${com.example.android.architecture.blueprints.todoapp.BuildConfig.AMAP_API_KEY}")
            
            // 设置定位监听
            locationClient?.setLocationListener(locationListener)
            
            // 创建并设置定位参数
            locationOption = AMapLocationClientOption().apply {
                // 设置定位模式为高精度模式
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                
                // 设置是否返回地址信息
                isNeedAddress = true
                
                // 设置是否允许模拟位置
                isMockEnable = false
                
                // 设置定位间隔，单位为毫秒，设置为2000ms更新更频繁
                interval = 2000
                
                // 单次定位 - 尝试开启单次定位看是否能获取到位置
                isOnceLocation = true
                
                // 设置是否使用设备传感器
                isSensorEnable = true
                
                // 设置是否开启wifi扫描，对于获取位置有帮助
                isWifiScan = true
                
                // 设置网络超时时间
                httpTimeOut = 20000
            }
            
            // 设置定位参数
            locationClient?.setLocationOption(locationOption)
            
            Log.i(TAG, "定位客户端初始化成功，参数: ${locationOption?.toString()}")
        } catch (e: Exception) {
            Log.e(TAG, "初始化定位客户端失败: ${e.message}", e)
        }
    }
    
    /**
     * 开始定位
     */
    fun startLocation() {
        try {
            // 确保客户端初始化
            if (locationClient == null) {
                Log.e(TAG, "定位客户端为null，重新初始化")
                initLocationClient()
            }
            
            locationClient?.startLocation()
            Log.i(TAG, "开始定位请求已发送")
        } catch (e: Exception) {
            Log.e(TAG, "开始定位失败", e)
        }
    }
    
    /**
     * 停止定位
     */
    fun stopLocation() {
        locationClient?.stopLocation()
        Log.i(TAG, "停止定位")
    }
    
    /**
     * 销毁定位客户端
     */
    private fun destroyLocationClient() {
        locationClient?.onDestroy()
        locationClient = null
        locationOption = null
        Log.i(TAG, "销毁定位客户端")
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