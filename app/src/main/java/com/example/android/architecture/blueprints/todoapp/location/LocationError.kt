package com.example.android.architecture.blueprints.todoapp.location

/**
 * 位置相关错误的密封类
 */
sealed class LocationError {
    /**
     * 表示权限未授予错误
     */
    object PermissionDenied : LocationError()
    
    /**
     * 表示GPS未启用错误
     */
    object GpsDisabled : LocationError()
    
    /**
     * 表示服务绑定失败错误
     */
    object ServiceBindFailed : LocationError()
    
    /**
     * 表示位置获取失败错误
     * @param code 错误代码
     * @param message 错误消息
     */
    data class LocationFailed(val code: Int, val message: String) : LocationError()
    
    /**
     * 表示网络错误
     * @param message 错误消息
     */
    data class NetworkError(val message: String) : LocationError()
    
    /**
     * 表示服务器错误
     * @param code HTTP状态码
     * @param message 错误消息
     */
    data class ServerError(val code: Int, val message: String) : LocationError()
    
    /**
     * 表示未知错误
     * @param message 错误消息
     */
    data class UnknownError(val message: String) : LocationError()
} 