package com.example.android.architecture.blueprints.todoapp

import android.app.Application
import com.amap.api.location.AMapLocationClient
import com.example.android.architecture.blueprints.todoapp.utils.NativeLibraryLoader
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import timber.log.Timber.DebugTree

/**
 * Application that sets up Timber in the DEBUG BuildConfig.
 * Read Timber's documentation for production setups.
 */
@HiltAndroidApp
class TodoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(DebugTree())
        
        // 加载本地库
        NativeLibraryLoader.init(this)
        
        // 设置高德地图隐私政策
        AMapLocationClient.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyAgree(this, true)
        
        // 如果需要，也可以在这里动态设置API Key
        // 但通常通过Manifest设置就足够了
        // 如果你确实需要动态设置，可以参考高德地图的官方文档
    }
}
