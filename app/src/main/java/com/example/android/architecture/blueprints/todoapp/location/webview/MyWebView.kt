package com.example.android.architecture.blueprints.todoapp.location.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * 自定义WebView，用于展示高德地图
 * 包含了展示高德地图所需的所有WebView设置
 */
class MyWebView : WebView {
    private var webViewCallback: WebViewCallback? = null
    
    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initBridgeWebView(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initBridgeWebView(context, attrs)
    }
    
    /**
     * 设置WebView回调
     */
    fun setWebViewCallback(callback: WebViewCallback) {
        this.webViewCallback = callback
    }

    /**
     * 执行JavaScript代码
     */
    fun evaluateJavascript(script: String, resultCallback: ((String) -> Unit)? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            super.evaluateJavascript(script, resultCallback)
        } else {
            // 兼容低版本
            loadUrl("javascript:$script")
            resultCallback?.invoke("")
        }
    }
    
    /**
     * 添加JavaScript接口
     * 确保所有接口方法都使用@JavascriptInterface注解
     */
    override fun addJavascriptInterface(obj: Any, name: String) {
        // 验证对象中是否有@JavascriptInterface注解
        val hasAnnotation = obj.javaClass.methods.any { 
            it.isAnnotationPresent(android.webkit.JavascriptInterface::class.java) 
        }
        if (!hasAnnotation) {
            Log.w("MyWebView", "添加的JavaScript接口对象($name)没有任何方法使用@JavascriptInterface注解，在API 17以上将不可见")
        }
        super.addJavascriptInterface(obj, name)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initBridgeWebView(context: Context, attrs: AttributeSet?) {
        val settings = settings

        // 允许使用js
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // 设置适应Html5
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        // 设置WebView属性，能够执行Javascript脚本
        settings.defaultTextEncodingName = "utf-8"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            setWebContentsDebuggingEnabled(true)
        }

        // android 4.1
        // 允许webview对文件的操作
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.allowUniversalAccessFromFileURLs = true
        }
        settings.allowFileAccess = true

        // android 4.1
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.allowFileAccessFromFileURLs = true
        }
        settings.allowContentAccess = true
        settings.databaseEnabled = true
        // 允许blob请求过不了
        settings.allowFileAccessFromFileURLs = true
        // 允许本地的缓存
        settings.allowUniversalAccessFromFileURLs = true

        setWebChromeClient(WebChromeClient())
        
        // 设置WebViewClient以监听页面加载完成事件
        setWebViewClient(object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                webViewCallback?.onPageFinished()
            }
        })
    }
} 