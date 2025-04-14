package com.example.android.architecture.blueprints.todoapp.location.webview

import android.annotation.TargetApi
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.amap.maps.jsmap.IAMapJsCallback
import com.amap.maps.jsmap.IAMapWebView

/**
 * WebView包装器，用于实现AMap的WebView接口
 */
class MAWebViewWrapper(private val webView: WebView?) : IAMapWebView {
    private var mapWebViewClient: WebViewClient? = null

    init {
        if (this.webView != null) {
            this.webView.webViewClient = object : WebViewClient() {
                @TargetApi(Build.VERSION_CODES.N)
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (mapWebViewClient != null) {
                        val flag = mapWebViewClient!!.shouldOverrideUrlLoading(view, request)
                        if (flag) {
                            return true
                        }
                    }
                    return super.shouldOverrideUrlLoading(view, request)
                }

                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    if (mapWebViewClient != null) {
                        val response = mapWebViewClient!!.shouldInterceptRequest(view, request)
                        if (response != null) {
                            return response
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? {
                    if (mapWebViewClient != null) {
                        val response = mapWebViewClient!!.shouldInterceptRequest(view, url)
                        if (response != null) {
                            return response
                        }
                    }
                    return super.shouldInterceptRequest(view, url)
                }
            }
        }
    }

    override fun evaluateJavascript(jsCallSig: String, callback: ValueCallback<String>?) {
        if (this.webView != null && callback != null) {
            this.webView.evaluateJavascript(jsCallSig, callback)
        }
    }

    override fun loadUrl(url: String) {
        this.webView?.loadUrl(url)
    }

    override fun addAMapJavascriptInterface(jsCallback: IAMapJsCallback, jsInterfaceName: String) {
        this.webView?.addJavascriptInterface(jsCallback, jsInterfaceName)
    }

    override fun setWebViewClient(webViewClient: WebViewClient) {
        this.mapWebViewClient = webViewClient
    }

    override fun getWidth(): Int {
        return this.webView?.width ?: 0
    }

    override fun getHeight(): Int {
        return this.webView?.height ?: 0
    }

    override fun addView(v: View, params: ViewGroup.LayoutParams) {
        if (webView != null && v != null) {
            webView.addView(v, params)
        }
    }
} 