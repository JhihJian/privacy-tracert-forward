package com.example.android.architecture.blueprints.todoapp.location.webview

import android.content.Context
import android.util.Log
import com.amap.api.maps.AMap
import com.amap.api.maps.model.LatLng
import java.util.UUID

/**
 * 高德地图包装类
 * 使用H5版高德地图进行显示
 */
class AMapHelper(private val context: Context) {
    private val TAG = "AMapHelper"
    private val uniqueId = UUID.randomUUID().toString()
    
    // 保存WebView引用，供外部访问
    var webViewRef: MyWebView? = null
        private set
    
    // 地图回调
    private var mapReadyCallback: ((MapProxy) -> Unit)? = null
    
    // 地图代理对象
    private val mapProxy = MapProxy()
    
    /**
     * 初始化地图
     * 使用WebView加载高德地图H5版本
     * 由于AMap是final类，改为使用代理模式
     */
    fun initMap(webView: MyWebView, onMapReady: (MapProxy) -> Unit) {
        try {
            // 保存WebView引用
            this.webViewRef = webView
            this.mapReadyCallback = onMapReady
            
            // 在WebView中加载高德地图H5版本
            val mapUrl = "https://m.amap.com/"
            webView.loadUrl(mapUrl)
            
            // 注册WebView加载完成回调
            webView.setWebViewCallback(object : WebViewCallback {
                override fun onPageFinished() {
                    Log.d(TAG, "高德地图H5页面加载完成")
                    
                    // 初始化地图相关JavaScript交互
                    initJavaScriptInterface(webView)
                    
                    // 地图准备完成，回调通知
                    mapReadyCallback?.invoke(mapProxy)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "初始化地图失败", e)
        }
    }
    
    /**
     * 初始化JavaScript交互接口
     */
    private fun initJavaScriptInterface(webView: MyWebView) {
        // 添加JavaScript接口，用于地图与原生代码交互
        webView.addJavascriptInterface(object : Any() {
            @android.webkit.JavascriptInterface
            fun onMapClick(lat: Double, lng: Double): String {
                Log.d(TAG, "地图点击: $lat, $lng")
                // 调用点击监听器
                mapProxy.notifyMapClick(LatLng(lat, lng))
                return uniqueId
            }
        }, "MapBridge")
    }
    
    /**
     * 显示位置
     * 通过JavaScript执行地图操作
     */
    fun showLocation(webView: MyWebView, location: LatLng) {
        try {
            val js = """
                try {
                    // 尝试通过DOM操作定位地图
                    var lat = ${location.latitude};
                    var lng = ${location.longitude};
                    console.log('尝试移动地图到位置:', lat, lng);
                    
                    // 使用更简单的方式注入高德地图代码
                    var script = document.createElement('script');
                    script.innerHTML = `
                        // 如果已有标记，先清除
                        if (window.marker) {
                            window.marker.setMap(null);
                        }
                        
                        // 创建点标记
                        window.marker = new AMap.Marker({
                            position: [${location.longitude}, ${location.latitude}],
                            title: '当前位置',
                            map: map
                        });
                        
                        // 设置地图中心点
                        map.setCenter([${location.longitude}, ${location.latitude}]);
                        map.setZoom(16);
                    `;
                    document.body.appendChild(script);
                } catch(e) {
                    console.error('执行地图JS出错:', e);
                }
            """.trimIndent()
            
            webView.evaluateJavascript(js) { result ->
                Log.d(TAG, "执行JavaScript结果: $result")
            }
        } catch (e: Exception) {
            Log.e(TAG, "通过JavaScript显示位置失败", e)
        }
    }
    
    /**
     * 地图生命周期方法（H5版本不需要）
     */
    fun onResume() {
        // H5版本不需要实现
    }
    
    fun onPause() {
        // H5版本不需要实现
    }
    
    fun onDestroy() {
        webViewRef = null
        mapReadyCallback = null
    }
}

/**
 * WebView回调接口
 */
interface WebViewCallback {
    fun onPageFinished()
}

/**
 * 地图代理类
 * 由于AMap是final class，我们不能继承它
 * 这个类提供一套简化的API，仅满足当前需求
 */
class MapProxy {
    val mapScreenMarkers = mutableListOf<MarkerProxy>()
    private var mapClickListener: ((LatLng) -> Unit)? = null
    private val uiSettings = UiSettingsProxy()
    
    fun setOnMapClickListener(listener: (LatLng) -> Unit) {
        this.mapClickListener = listener
    }
    
    internal fun notifyMapClick(latLng: LatLng) {
        mapClickListener?.invoke(latLng)
    }
    
    fun getUiSettings(): UiSettingsProxy {
        return uiSettings
    }
    
    fun addMarker(options: MarkerOptions): MarkerProxy {
        val marker = MarkerProxy(options.position)
        mapScreenMarkers.add(marker)
        return marker
    }
    
    fun animateCamera(update: CameraUpdate) {
        // 模拟实现，实际操作由JavaScript处理
    }
}

/**
 * UI设置代理类
 */
class UiSettingsProxy {
    fun setZoomControlsEnabled(enabled: Boolean) {}
    fun setCompassEnabled(enabled: Boolean) {}
    fun setMyLocationButtonEnabled(enabled: Boolean) {}
    fun setScaleControlsEnabled(enabled: Boolean) {}
}

/**
 * 标记代理类
 */
class MarkerProxy(var position: LatLng)

/**
 * 标记选项代理类
 */
class MarkerOptions {
    var position: LatLng = LatLng(0.0, 0.0)
    var title: String = ""
    var snippet: String = ""
    var draggable: Boolean = false
    var icon: Any? = null
    
    fun position(position: LatLng): MarkerOptions {
        this.position = position
        return this
    }
    
    fun title(title: String): MarkerOptions {
        this.title = title
        return this
    }
    
    fun snippet(snippet: String): MarkerOptions {
        this.snippet = snippet
        return this
    }
    
    fun draggable(draggable: Boolean): MarkerOptions {
        this.draggable = draggable
        return this
    }
    
    fun icon(icon: Any?): MarkerOptions {
        this.icon = icon
        return this
    }
}

/**
 * 相机更新代理类
 */
class CameraUpdate

/**
 * 相机更新工厂代理类
 */
object CameraUpdateFactory {
    fun newLatLngZoom(latLng: LatLng, zoom: Float): CameraUpdate {
        return CameraUpdate()
    }
} 