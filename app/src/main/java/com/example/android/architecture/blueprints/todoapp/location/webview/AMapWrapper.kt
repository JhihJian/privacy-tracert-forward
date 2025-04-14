package com.example.android.architecture.blueprints.todoapp.location.webview

import android.content.Context
import com.amap.jsmap.core.AMap
import com.amap.jsmap.core.AMapWrapper
import com.amap.jsmap.core.AMapOptions

/**
 * 高德地图包装类
 * 用于创建和管理AMap对象
 */
class AMapHelper(private val context: Context) {
    private var aMapWrapper: AMapWrapper? = null
    private var aMap: AMap? = null
    
    /**
     * 初始化地图
     */
    fun initMap(webView: MyWebView, onMapReady: (AMap) -> Unit) {
        // 创建WebView包装器
        val webViewWrapper = MAWebViewWrapper(webView)
        
        // 创建地图控制对象
        aMapWrapper = AMapWrapper(context, webViewWrapper)
        
        // 初始化地图并获取AMap对象
        aMapWrapper?.onCreate()
        aMapWrapper?.getMapAsyn { map ->
            aMap = map
            onMapReady(map)
        }
    }
    
    /**
     * 地图生命周期方法
     */
    fun onResume() {
        aMapWrapper?.onResume()
    }
    
    fun onPause() {
        aMapWrapper?.onPause()
    }
    
    fun onDestroy() {
        aMapWrapper?.onDestroy()
        aMapWrapper = null
        aMap = null
    }
    
    /**
     * 获取当前地图对象
     */
    fun getMap(): AMap? = aMap
} 