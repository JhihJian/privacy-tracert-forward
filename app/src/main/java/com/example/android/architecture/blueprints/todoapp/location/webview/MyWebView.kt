package com.example.android.architecture.blueprints.todoapp.location.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * 自定义WebView，用于加载高德地图
 */
class MyWebView : WebView {
    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initBridgeWebView(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initBridgeWebView(context, attrs)
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
        
        // 允许blob请求
        settings.allowFileAccessFromFileURLs = true
        // 允许本地的缓存
        settings.allowUniversalAccessFromFileURLs = true

        webChromeClient = WebChromeClient()
    }
} 