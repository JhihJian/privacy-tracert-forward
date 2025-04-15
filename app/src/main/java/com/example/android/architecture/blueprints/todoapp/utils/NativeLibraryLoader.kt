package com.example.android.architecture.blueprints.todoapp.utils

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

/**
 * 本地库加载器
 * 用于显式加载应用所需的本地库（.so文件）
 */
object NativeLibraryLoader {
    private const val TAG = "NativeLibraryLoader"
    private var isInitialized = false

    /**
     * 高德地图相关库 - 32位ARM架构
     */
    private val AMAP_LIBRARIES_32BIT = arrayOf(
        "AMapSDK_MAP_v10_1_200",
        "AMap3DMap"
    )

    /**
     * 高德地图相关库 - 64位ARM架构
     * 当前项目中尚未提供64位库
     */
    private val AMAP_LIBRARIES_64BIT = arrayOf(
        "Empty"
        // 当前无64位库
    )

    /**
     * 初始化并加载所有需要的本地库
     */
    fun init(context: Context) {
        if (isInitialized) {
            return
        }

        try {
            // 检查库文件是否存在
            checkLibraryFiles(context)
            
            // 根据设备ABI选择加载相应的库
            val abi = getPrimaryAbi()
            Log.d(TAG, "Device primary ABI: $abi")
            
            // 选择要加载的库列表
            val librariesToLoad = when {
                abi.contains("arm64-v8a") -> AMAP_LIBRARIES_64BIT
                abi.contains("armeabi") -> AMAP_LIBRARIES_32BIT
                else -> AMAP_LIBRARIES_32BIT // 默认使用32位库
            }
            
            // 加载高德地图相关库
            for (lib in librariesToLoad) {
                try {
                    System.loadLibrary(lib)
                    Log.d(TAG, "Successfully loaded library: $lib")
                } catch (e: UnsatisfiedLinkError) {
                    // 如果加载失败，记录日志但继续尝试其他库
                    Log.w(TAG, "Failed to load library: $lib, ${e.message}")
                }
            }

            isInitialized = true
            Log.d(TAG, "Native libraries initialization completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing native libraries", e)
        }
    }
    
    /**
     * 获取设备主要ABI
     */
    private fun getPrimaryAbi(): String {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> {
                if (Build.SUPPORTED_ABIS.isNotEmpty()) {
                    Build.SUPPORTED_ABIS[0]
                } else {
                    Build.CPU_ABI
                }
            }
            else -> Build.CPU_ABI
        }
    }
    
    /**
     * 检查库文件是否存在
     * 这会在应用启动时提供更明确的错误信息，帮助排查问题
     */
    private fun checkLibraryFiles(context: Context) {
        val abi = getPrimaryAbi()
        Log.d(TAG, "Device primary ABI: $abi")
        
        // 检查jniLibs目录下对应ABI的.so文件
        val jniLibsDir = File(context.applicationInfo.nativeLibraryDir)
        Log.d(TAG, "Native library directory: ${jniLibsDir.absolutePath}")
        
        if (jniLibsDir.exists() && jniLibsDir.isDirectory) {
            val files = jniLibsDir.listFiles()
            Log.d(TAG, "Found ${files?.size ?: 0} files in native library directory")
            
            files?.forEach { file ->
                Log.d(TAG, "Native library file: ${file.name}")
            }
            
            // 选择需要检查的库
            val librariesToCheck = when {
                abi.contains("arm64-v8a") -> AMAP_LIBRARIES_64BIT
                abi.contains("armeabi") -> AMAP_LIBRARIES_32BIT
                else -> AMAP_LIBRARIES_32BIT
            }
            
            // 检查需要的库文件
            for (lib in librariesToCheck) {
                val libFile = File(jniLibsDir, "lib$lib.so")
                if (!libFile.exists()) {
                    Log.w(TAG, "Library file not found: ${libFile.absolutePath}")
                } else {
                    Log.d(TAG, "Library file exists: ${libFile.absolutePath}")
                }
            }
        } else {
            Log.e(TAG, "Native library directory does not exist: ${jniLibsDir.absolutePath}")
        }
    }
} 