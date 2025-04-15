package com.example.android.architecture.blueprints.todoapp.location.utils

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 权限工具类使用示例
 * 此类仅作为参考，展示如何使用权限工具类
 */
class PermissionUsageExample : AppCompatActivity() {
    
    // 方式一：直接使用简化版的LocationPermissionManager
    private lateinit var locationPermissionManager: LocationPermissionManager
    
    // 方式二：使用基础的PermissionHelper（保留以作对比）
    private val permissionHelper = PermissionHelper.with(this) { granted ->
        if (granted) {
            onPermissionsGranted()
        } else {
            onPermissionsDenied()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 方式一：一行代码完成所有权限请求与处理逻辑（超级简化版）
        locationPermissionManager = LocationPermissionManager.setup(
            activity = this,
            needBackgroundPermission = true, // 是否需要后台位置权限
            onPermissionGranted = {
                // 权限授予后的回调
                Toast.makeText(this, "位置权限已授予，可以进行定位操作", Toast.LENGTH_SHORT).show()
                startLocationTracking()
            },
            onPermissionDenied = {
                // 权限拒绝后的回调（可选）
                Toast.makeText(this, "位置权限被拒绝，无法进行定位操作", Toast.LENGTH_LONG).show()
            }
        )
        
        // 可选：观察权限状态变化
        lifecycleScope.launch {
            locationPermissionManager.permissionState.collect { state ->
                when (state) {
                    is LocationPermissionManager.PermissionState.Checking -> {
                        // 正在检查权限
                    }
                    is LocationPermissionManager.PermissionState.Requesting -> {
                        // 正在请求权限
                    }
                    is LocationPermissionManager.PermissionState.Granted -> {
                        // 权限已授予（这里不需要处理，因为已经在setup中提供了onPermissionGranted回调）
                    }
                    is LocationPermissionManager.PermissionState.PermanentlyDenied -> {
                        // 权限被永久拒绝（这里不需要处理，因为已经在setup中提供了onPermissionDenied回调）
                    }
                    else -> { /* 初始状态，不处理 */ }
                }
            }
        }
        
        // 方式二：使用基础的PermissionHelper（保留以作对比）
        // checkLocationPermissions()
    }
    
    /**
     * 开始位置跟踪
     */
    private fun startLocationTracking() {
        // 在此处开始位置跟踪，权限已经得到保证
        lifecycleScope.launch {
            // 例如启动定位服务
            // locationRepository.startLocation()
        }
    }
    
    //==== 以下是原始代码，保留以作对比 ====//
    
    /**
     * 检查并请求位置权限
     */
    private fun checkLocationPermissions() {
        if (!permissionHelper.hasBasicLocationPermissions(this)) {
            // 请求基本位置权限
            permissionHelper.requestBasicLocationPermissions()
        } else if (!permissionHelper.hasFullLocationPermissions(this)) {
            // 如果需要后台位置权限，可以单独请求
            Toast.makeText(this, "需要设置后台位置权限，即将引导至设置页面", Toast.LENGTH_LONG).show()
            permissionHelper.requestFullLocationPermissions(this)
        } else {
            // 已有所有权限，可以进行定位操作
            onPermissionsGranted()
        }
    }
    
    /**
     * 位置权限已授予的处理
     */
    private fun onPermissionsGranted() {
        Toast.makeText(this, "位置权限已授予，可以进行定位操作", Toast.LENGTH_SHORT).show()
        
        // 在此处开始定位相关操作
        lifecycleScope.launch {
            // 例如启动定位服务
            // locationRepository.startLocation()
        }
    }
    
    /**
     * 位置权限被拒绝的处理
     */
    private fun onPermissionsDenied() {
        Toast.makeText(this, "位置权限被拒绝，无法进行定位操作", Toast.LENGTH_LONG).show()
        
        // 可以提示用户手动开启权限
        // 或者仍然提供不依赖位置的功能
    }
    
    /**
     * Fragment中使用权限帮助类的方法演示
     */
    private fun showFragmentExample() {
        // 在Fragment中使用方式与Activity类似：
        /*
        // 方式一：使用超级简化版LocationPermissionManager
        val locationPermissionManager = LocationPermissionManager.setup(
            fragment = this,  // Fragment实例
            needBackgroundPermission = false,  // 是否需要后台权限
            onPermissionGranted = {
                // 权限授予后的回调
            },
            onPermissionDenied = {
                // 权限拒绝后的回调
            }
        )
        
        // 方式二：使用基础的PermissionHelper
        private val permissionHelper = PermissionHelper.with(this) { granted ->
            if (granted) {
                // 权限已授予
            } else {
                // 权限被拒绝
            }
        }
        
        // 在适当的时候请求权限
        private fun checkPermissions() {
            if (!permissionHelper.hasBasicLocationPermissions(requireContext())) {
                permissionHelper.requestBasicLocationPermissions()
            }
        }
        */
    }
} 