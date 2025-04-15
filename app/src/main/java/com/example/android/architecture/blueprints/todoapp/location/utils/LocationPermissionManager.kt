package com.example.android.architecture.blueprints.todoapp.location.utils

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 位置权限管理器
 * 
 * 超级简化版：只需要一行代码即可完成所有权限请求逻辑
 * 包括权限检查、请求、拒绝后提示等全部流程
 */
class LocationPermissionManager private constructor(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val needBackgroundPermission: Boolean = false,
    private val onPermissionGranted: () -> Unit,
    private val onPermissionDenied: (() -> Unit)? = null
) : LifecycleEventObserver {
    
    // 权限状态
    private val _permissionState = MutableStateFlow<PermissionState>(PermissionState.Initial)
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()
    
    // 权限助手
    private val permissionHelper by lazy {
        when (lifecycleOwner) {
            is AppCompatActivity -> PermissionHelper.with(lifecycleOwner) { granted ->
                handlePermissionResult(granted)
            }
            is ComponentActivity -> {
                // 直接使用ComponentActivity版本，不再尝试类型转换
                PermissionHelper.with(lifecycleOwner as ComponentActivity) { granted ->
                    handlePermissionResult(granted)
                }
            }
            is Fragment -> PermissionHelper.with(lifecycleOwner) { granted ->
                handlePermissionResult(granted)
            }
            else -> throw IllegalArgumentException("lifecycleOwner必须是ComponentActivity或Fragment")
        }
    }
    
    init {
        // 注册生命周期观察者
        lifecycleOwner.lifecycle.addObserver(this)
    }
    
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_RESUME -> {
                // 初始化权限检查
                if (permissionState.value is PermissionState.Initial) {
                    checkPermissionStatus()
                }
            }
            Lifecycle.Event.ON_DESTROY -> lifecycleOwner.lifecycle.removeObserver(this)
            else -> { /* 忽略其他事件 */ }
        }
    }
    
    /**
     * 仅检查权限状态，不自动请求
     */
    private fun checkPermissionStatus() {
        try {
            _permissionState.value = PermissionState.Checking
            
            // 根据是否需要后台权限来选择检查方法
            val hasPermission = if (needBackgroundPermission) {
                permissionHelper.hasFullLocationPermissions(context)
            } else {
                permissionHelper.hasBasicLocationPermissions(context)
            }
            
            if (hasPermission) {
                _permissionState.value = PermissionState.Granted
                onPermissionGranted()
            } else {
                _permissionState.value = PermissionState.Requesting
                // 使用延迟请求权限，避免生命周期问题
                lifecycleOwner.lifecycleScope.launch {
                    requestPermissions()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LocationPermissionManager", "权限检查过程中出错", e)
            _permissionState.value = PermissionState.PermanentlyDenied
            onPermissionDenied?.invoke()
        }
    }
    
    /**
     * 请求权限
     */
    private fun requestPermissions() {
        try {
            // 根据是否需要后台权限来选择请求方法
            if (needBackgroundPermission) {
                permissionHelper.requestFullLocationPermissions(context)
            } else {
                permissionHelper.requestBasicLocationPermissions()
            }
        } catch (e: Exception) {
            android.util.Log.e("LocationPermissionManager", "权限请求过程中出错", e)
            _permissionState.value = PermissionState.PermanentlyDenied
            onPermissionDenied?.invoke()
        }
    }
    
    /**
     * 检查并请求权限
     */
    private fun checkAndRequestPermission() {
        try {
            // 已经处于授权或拒绝状态，不再处理
            if (permissionState.value is PermissionState.Granted || 
                permissionState.value is PermissionState.PermanentlyDenied) {
                return
            }
            
            checkPermissionStatus()
        } catch (e: Exception) {
            android.util.Log.e("LocationPermissionManager", "权限检查或请求过程中出错", e)
            // 发生错误时，设置为拒绝状态，并调用拒绝回调
            _permissionState.value = PermissionState.PermanentlyDenied
            onPermissionDenied?.invoke()
        }
    }
    
    /**
     * 处理权限请求结果
     */
    private fun handlePermissionResult(granted: Boolean) {
        _permissionState.value = if (granted) {
            PermissionState.Granted
            onPermissionGranted()
            PermissionState.Granted
        } else {
            PermissionState.PermanentlyDenied
            onPermissionDenied?.invoke()
            PermissionState.PermanentlyDenied
        }
    }
    
    /**
     * 权限状态
     */
    sealed class PermissionState {
        object Initial : PermissionState()
        object Checking : PermissionState()
        object Requesting : PermissionState()
        object Granted : PermissionState()
        object PermanentlyDenied : PermissionState()
    }
    
    companion object {
        /**
         * 在Activity中使用
         * @param activity 宿主Activity
         * @param needBackgroundPermission 是否需要后台位置权限
         * @param onPermissionGranted 权限授予时的回调
         * @param onPermissionDenied 权限拒绝时的回调（可选）
         */
        fun setup(
            activity: ComponentActivity,
            needBackgroundPermission: Boolean = false,
            onPermissionGranted: () -> Unit,
            onPermissionDenied: (() -> Unit)? = null
        ): LocationPermissionManager {
            return LocationPermissionManager(
                activity,
                activity,
                needBackgroundPermission,
                onPermissionGranted,
                onPermissionDenied
            )
        }
        
        /**
         * 在Fragment中使用
         * @param fragment 宿主Fragment
         * @param needBackgroundPermission 是否需要后台位置权限
         * @param onPermissionGranted 权限授予时的回调
         * @param onPermissionDenied 权限拒绝时的回调（可选）
         */
        fun setup(
            fragment: Fragment,
            needBackgroundPermission: Boolean = false,
            onPermissionGranted: () -> Unit,
            onPermissionDenied: (() -> Unit)? = null
        ): LocationPermissionManager {
            return LocationPermissionManager(
                fragment.requireContext(),
                fragment,
                needBackgroundPermission,
                onPermissionGranted,
                onPermissionDenied
            )
        }
    }
} 