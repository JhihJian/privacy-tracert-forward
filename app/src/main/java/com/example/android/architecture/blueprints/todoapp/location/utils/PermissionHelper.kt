package com.example.android.architecture.blueprints.todoapp.location.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import android.util.Log

/**
 * 权限帮助类，简化权限请求操作
 * 适用于Activity和Fragment
 */
class PermissionHelper : DefaultLifecycleObserver {
    
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private var isPermissionLauncherInitialized = false
    private var onPermissionResult: ((Boolean) -> Unit)? = null
    private var isActivityInstalled = false
    private var activity: AppCompatActivity? = null
    private var componentActivity: ComponentActivity? = null
    private var fragment: Fragment? = null
    
    /**
     * 安装到Activity
     */
    fun installOn(activity: AppCompatActivity, onResult: (Boolean) -> Unit) {
        this.activity = activity
        this.onPermissionResult = onResult
        activity.lifecycle.addObserver(this)
        isActivityInstalled = true
    }
    
    /**
     * 安装到ComponentActivity
     */
    fun installOn(activity: ComponentActivity, onResult: (Boolean) -> Unit) {
        this.componentActivity = activity
        this.onPermissionResult = onResult
        activity.lifecycle.addObserver(this)
        isActivityInstalled = true
    }
    
    /**
     * 安装到Fragment
     */
    fun installOn(fragment: Fragment, onResult: (Boolean) -> Unit) {
        this.fragment = fragment
        this.onPermissionResult = onResult
        fragment.lifecycle.addObserver(this)
    }
    
    /**
     * 在适当的生命周期阶段初始化权限请求启动器
     */
    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        
        if (isPermissionLauncherInitialized) {
            // 已经初始化过，不重复初始化
            return
        }
        
        try {
            when {
                activity != null -> {
                    permissionLauncher = PermissionUtils.registerPermissionLauncher(activity!!) { granted ->
                        onPermissionResult?.invoke(granted)
                    }
                }
                componentActivity != null -> {
                    if (componentActivity is AppCompatActivity) {
                        permissionLauncher = PermissionUtils.registerPermissionLauncher(componentActivity as AppCompatActivity) { granted ->
                            onPermissionResult?.invoke(granted)
                        }
                    } else {
                        permissionLauncher = PermissionUtils.registerPermissionLauncherForComponentActivity(componentActivity!!) { granted ->
                            onPermissionResult?.invoke(granted)
                        }
                    }
                }
                fragment != null -> {
                    permissionLauncher = fragment!!.registerForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
                    ) { permissions ->
                        val allGranted = permissions.values.all { it }
                        if (!allGranted) {
                            fragment!!.context?.let { PermissionUtils.showPermissionSettingsDialog(it) }
                        }
                        onPermissionResult?.invoke(allGranted)
                    }
                }
            }
            isPermissionLauncherInitialized = true
        } catch (e: Exception) {
            Log.e("PermissionHelper", "初始化权限请求启动器时出错", e)
        }
    }
    
    /**
     * 请求基本位置权限
     */
    fun requestBasicLocationPermissions() {
        if (isPermissionLauncherInitialized) {
            PermissionUtils.requestBasicLocationPermissions(permissionLauncher)
        } else {
            Log.e("PermissionHelper", "权限请求启动器尚未初始化，无法请求权限")
            onPermissionResult?.invoke(false)
        }
    }
    
    /**
     * 请求完整位置权限（包括后台位置）
     */
    fun requestFullLocationPermissions(context: Context) {
        if (!isPermissionLauncherInitialized) {
            Log.e("PermissionHelper", "权限请求启动器尚未初始化，无法请求权限")
            onPermissionResult?.invoke(false)
            return
        }
        
        try {
            if (context is Activity) {
                PermissionUtils.requestFullLocationPermissions(context, permissionLauncher)
            } else {
                // 如果context不是Activity，尝试一种简化的处理方式
                if (PermissionUtils.hasBasicLocationPermissions(context)) {
                    // 如果已经有基本位置权限，但是需要后台位置权限
                    // 我们可以直接弹出设置对话框引导用户去设置中授予后台权限
                    PermissionUtils.showBackgroundLocationPermissionDialog(context)
                } else {
                    // 如果连基本位置权限都没有，先请求基本权限
                    PermissionUtils.requestBasicLocationPermissions(permissionLauncher)
                }
            }
        } catch (e: Exception) {
            Log.e("PermissionHelper", "请求完整位置权限时出错", e)
            onPermissionResult?.invoke(false)
        }
    }
    
    /**
     * 检查是否有基本位置权限
     */
    fun hasBasicLocationPermissions(context: Context): Boolean {
        return PermissionUtils.hasBasicLocationPermissions(context)
    }
    
    /**
     * 检查是否有完整位置权限（包括后台位置）
     */
    fun hasFullLocationPermissions(context: Context): Boolean {
        return PermissionUtils.hasFullLocationPermissions(context)
    }
    
    /**
     * 打开应用设置页面
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("PermissionHelper", "无法打开应用设置", e)
            // 显示一个toast提示用户
            android.widget.Toast.makeText(
                context, 
                "无法打开应用设置，请手动前往设置 > 应用 > 权限中授予位置权限", 
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        onPermissionResult = null
        activity = null
        componentActivity = null
        fragment = null
        super.onDestroy(owner)
    }
    
    companion object {
        /**
         * 创建权限帮助类实例 (Activity)
         */
        fun with(activity: AppCompatActivity, onResult: (Boolean) -> Unit): PermissionHelper {
            return PermissionHelper().apply {
                installOn(activity, onResult)
            }
        }
        
        /**
         * 创建权限帮助类实例 (ComponentActivity)
         */
        fun with(activity: ComponentActivity, onResult: (Boolean) -> Unit): PermissionHelper {
            return PermissionHelper().apply {
                installOn(activity, onResult)
            }
        }
        
        /**
         * 创建权限帮助类实例 (Fragment)
         */
        fun with(fragment: Fragment, onResult: (Boolean) -> Unit): PermissionHelper {
            return PermissionHelper().apply {
                installOn(fragment, onResult)
            }
        }
    }
} 