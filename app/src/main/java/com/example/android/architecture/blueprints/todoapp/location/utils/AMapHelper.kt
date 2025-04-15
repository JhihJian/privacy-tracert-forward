package com.example.android.architecture.blueprints.todoapp.location.utils

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.MyLocationStyle
import com.example.android.architecture.blueprints.todoapp.utils.NativeLibraryLoader
import java.util.UUID

/**
 * 高德地图助手类
 * 用于管理原生高德地图的初始化和常用功能
 */
class AMapHelper(private val context: Context) {
    private val TAG = "AMapHelper"
    private val uniqueId = UUID.randomUUID().toString()
    
    // 地图对象
    private var aMap: AMap? = null
    
    // 当前位置的标记
    private var locationMarker: Marker? = null
    
    // 保存MapView引用
    private var mapView: MapView? = null
    
    init {
        // 确保本地库已加载
        try {
            NativeLibraryLoader.init(context)
            
            // 设置高德地图的额外参数
            MapsInitializer.updatePrivacyShow(context, true, true)
            MapsInitializer.updatePrivacyAgree(context, true)
        } catch (e: Exception) {
            Log.e(TAG, "初始化地图SDK时出错", e)
        }
    }
    
    /**
     * 初始化地图
     */
    fun initMap(mapView: MapView, onMapReady: (AMap) -> Unit) {
        try {
            // 保存MapView引用
            this.mapView = mapView
            
            // 确保地图视图已创建
            mapView.onCreate(Bundle())
            
            // 直接获取AMap对象
            val map = mapView.map
            this.aMap = map
            
            // 配置地图
            setupMap(map)
            
            // 通知地图已就绪
            onMapReady(map)
        } catch (e: Exception) {
            Log.e(TAG, "初始化地图失败", e)
        }
    }
    
    /**
     * 配置地图
     */
    private fun setupMap(map: AMap) {
        // 设置地图类型
        map.mapType = AMap.MAP_TYPE_NORMAL
        
        // 设置地图UI控件
        val uiSettings = map.uiSettings
        uiSettings.isZoomControlsEnabled = true    // 缩放按钮
        uiSettings.isCompassEnabled = true         // 指南针
        uiSettings.isMyLocationButtonEnabled = true // 定位按钮
        uiSettings.isScaleControlsEnabled = true    // 比例尺
        
        // 配置我的位置样式
        val myLocationStyle = MyLocationStyle()
            .myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE)
            .interval(2000)
            .strokeColor(Color.argb(180, 3, 145, 255))
            .radiusFillColor(Color.argb(10, 0, 0, 255))
            .strokeWidth(5f)
        
        // 应用我的位置样式
        map.myLocationStyle = myLocationStyle
        
        // 启用定位层
        map.isMyLocationEnabled = true
        
        // 开启室内地图
        map.showIndoorMap(true)
        
        // 显示交通状况
        map.isTrafficEnabled = false
        
        // 注册地图加载完成监听器
        map.setOnMapLoadedListener {
            Log.d(TAG, "地图加载完成")
        }
    }
    
    /**
     * 显示位置
     */
    fun showLocation(location: LatLng, title: String = "当前位置") {
        aMap?.let { map ->
            // 如果存在之前的标记，先移除
            locationMarker?.remove()
            
            // 创建新的标记
            val markerOptions = MarkerOptions()
                .position(location)
                .title(title)
                .snippet("纬度:${location.latitude}, 经度:${location.longitude}")
                .draggable(false)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            
            // 添加标记
            locationMarker = map.addMarker(markerOptions)
            
            // 移动相机到标记位置
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 16f))
        }
    }
    
    /**
     * 添加标记
     */
    fun addMarker(location: LatLng, title: String, snippet: String): Marker? {
        return aMap?.addMarker(
            MarkerOptions()
                .position(location)
                .title(title)
                .snippet(snippet)
        )
    }
    
    /**
     * 清除所有标记
     */
    fun clearMarkers() {
        aMap?.clear()
        locationMarker = null
    }
    
    /**
     * 移动相机到指定位置
     */
    fun moveCamera(location: LatLng, zoom: Float = 16f) {
        aMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(location, zoom))
    }
    
    /**
     * 平滑移动相机到指定位置
     */
    fun animateCamera(location: LatLng, zoom: Float = 16f) {
        aMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(location, zoom))
    }
    
    /**
     * 生命周期方法：恢复
     */
    fun onResume() {
        mapView?.onResume()
    }
    
    /**
     * 生命周期方法：暂停
     */
    fun onPause() {
        mapView?.onPause()
    }
    
    /**
     * 生命周期方法：销毁
     */
    fun onDestroy() {
        mapView?.onDestroy()
    }
    
    /**
     * 生命周期方法：保存状态
     */
    fun onSaveInstanceState(outState: Bundle) {
        mapView?.onSaveInstanceState(outState)
    }
} 