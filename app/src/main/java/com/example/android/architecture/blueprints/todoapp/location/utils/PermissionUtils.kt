package com.example.android.architecture.blueprints.todoapp.location.utils

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.android.architecture.blueprints.todoapp.R

/**
 * 权限检查工具类
 * 提供权限检查和请求功能
 */
object PermissionUtils {
    
    // 基本位置权限
    private val BASIC_LOCATION_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    
    // 包含后台位置权限的完整权限组
    private val FULL_LOCATION_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        )
    } else {
        BASIC_LOCATION_PERMISSIONS
    }
    
    /**
     * 检查是否有基本位置权限
     */
    fun hasBasicLocationPermissions(context: Context): Boolean {
        return BASIC_LOCATION_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * 检查是否有包括后台位置在内的所有位置权限
     */
    fun hasFullLocationPermissions(context: Context): Boolean {
        return FULL_LOCATION_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * 获取需要请求的权限列表
     */
    fun findDeniedPermissions(context: Context, permissions: Array<String>): List<String> {
        return permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * 为Activity注册权限请求回调
     */
    fun registerPermissionLauncher(
        activity: AppCompatActivity,
        onPermissionResult: (Boolean) -> Unit
    ): ActivityResultLauncher<Array<String>> {
        return activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.values.all { it }
            if (!allGranted) {
                showPermissionSettingsDialog(activity)
            }
            onPermissionResult(allGranted)
        }
    }
    
    /**
     * 为ComponentActivity注册权限请求回调
     * 这个方法专门处理非AppCompatActivity的ComponentActivity
     */
    fun registerPermissionLauncherForComponentActivity(
        activity: ComponentActivity,
        onPermissionResult: (Boolean) -> Unit
    ): ActivityResultLauncher<Array<String>> {
        return activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.values.all { it }
            if (!allGranted) {
                // 使用Context版本的对话框显示方法
                showPermissionSettingsDialog(activity)
            }
            onPermissionResult(allGranted)
        }
    }
    
    /**
     * 请求基本位置权限
     */
    fun requestBasicLocationPermissions(permissionLauncher: ActivityResultLauncher<Array<String>>) {
        permissionLauncher.launch(BASIC_LOCATION_PERMISSIONS)
    }
    
    /**
     * 请求完整位置权限（包括后台位置）
     * 注意：在Android 10及以上版本，应先请求基本权限，再单独请求后台权限
     */
    fun requestFullLocationPermissions(activity: Activity, permissionLauncher: ActivityResultLauncher<Array<String>>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 对于Android 10及以上，先请求基本位置权限
            if (!hasBasicLocationPermissions(activity)) {
                permissionLauncher.launch(BASIC_LOCATION_PERMISSIONS)
            } else if (ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // 基本权限已授予，单独请求后台位置权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Android 11及以上需要引导用户到设置页面
                    showBackgroundLocationPermissionDialog(activity)
                } else {
                    // Android 10可以直接请求
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                }
            }
        } else {
            // Android 9及以下版本，直接请求所有权限
            permissionLauncher.launch(BASIC_LOCATION_PERMISSIONS)
        }
    }
    
    /**
     * 显示权限设置对话框
     */
    fun showPermissionSettingsDialog(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.location_permission_title)
            .setMessage(R.string.location_permission_message)
            .setNegativeButton(R.string.cancel) { _, _ ->
                // 用户拒绝，不做任何操作
            }
            .setPositiveButton(R.string.settings) { _, _ ->
                // 跳转到应用设置页面
                openAppSettings(activity)
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * 显示权限设置对话框
     */
    fun showPermissionSettingsDialog(context: Context) {
        if (context is Activity) {
            showPermissionSettingsDialog(context)
        } else {
            AlertDialog.Builder(context)
                .setTitle(R.string.location_permission_title)
                .setMessage(R.string.location_permission_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.settings) { _, _ ->
                    openAppSettings(context)
                }
                .setCancelable(false)
                .show()
        }
    }
    
    /**
     * 显示后台位置权限对话框
     */
    fun showBackgroundLocationPermissionDialog(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.background_location_permission_title)
            .setMessage(R.string.background_location_permission_message)
            .setNegativeButton(R.string.cancel) { _, _ ->
                // 用户拒绝
            }
            .setPositiveButton(R.string.settings) { _, _ ->
                // 跳转到应用设置页面
                openAppSettings(activity)
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * 显示后台位置权限对话框 - Context版本
     */
    fun showBackgroundLocationPermissionDialog(context: Context) {
        if (context is Activity) {
            showBackgroundLocationPermissionDialog(context)
        } else {
            AlertDialog.Builder(context)
                .setTitle(R.string.background_location_permission_title)
                .setMessage(R.string.background_location_permission_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.settings) { _, _ ->
                    openAppSettings(context)
                }
                .setCancelable(false)
                .show()
        }
    }
    
    /**
     * 打开应用设置页面
     */
    fun openAppSettings(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${activity.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("PermissionUtils", "无法打开应用设置", e)
            // 显示一个toast提示用户
            android.widget.Toast.makeText(
                activity, 
                "无法打开应用设置，请手动前往设置 > 应用 > 权限中授予位置权限", 
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
    
    /**
     * 打开应用设置页面（Context版本）
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("PermissionUtils", "无法打开应用设置", e)
            // 显示一个toast提示用户
            android.widget.Toast.makeText(
                context, 
                "无法打开应用设置，请手动前往设置 > 应用 > 权限中授予位置权限", 
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
} 