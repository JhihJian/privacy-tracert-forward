package com.example.android.architecture.blueprints.todoapp.location

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * 闹钟服务，用于定时触发位置更新
 */
class AlarmService : Service() {
    private val TAG = "AlarmService"
    
    // AlarmManager相关
    private lateinit var alarmManager: AlarmManager
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var locationIntent: PendingIntent
    private var isAlarmEnabled = false
    
    // 定位唤醒间隔 (默认1分钟)
    private var alarmInterval = 60 * 1000L
    
    // 位置更新广播接收器ACTION
    companion object {
        const val ACTION_LOCATION_ALARM = "com.example.android.architecture.blueprints.todoapp.ACTION_LOCATION_ALARM"
        
        // 定位间隔配置
        const val MIN_INTERVAL = 60 * 1000L // 最小间隔1分钟
        const val MAX_INTERVAL = 30 * 60 * 1000L // 最大间隔30分钟
        const val DEFAULT_INTERVAL = 60 * 1000L // 默认间隔1分钟
    }
    
    // 位置更新广播接收器
    private val locationAlarmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_LOCATION_ALARM) {
                Log.d(TAG, "收到定位唤醒广播")
                acquireWakeLock()
                // 发送广播通知 LocationService 进行位置更新
                val locationIntent = Intent(context, LocationService::class.java)
                context?.startService(locationIntent)
            }
        }
    }
    
    // 绑定服务的Binder
    private val binder = AlarmBinder()
    
    inner class AlarmBinder : Binder() {
        fun getService(): AlarmService = this@AlarmService
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AlarmService onCreate")
        
        // 初始化AlarmManager
        initAlarmManager()
        
        // 注册广播接收器
        try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Context.RECEIVER_NOT_EXPORTED
            } else {
                0
            }
            registerReceiver(locationAlarmReceiver, IntentFilter(ACTION_LOCATION_ALARM), flags)
            Log.d(TAG, "广播接收器注册成功")
        } catch (e: Exception) {
            Log.e(TAG, "注册广播接收器失败", e)
        }
        
        // 初始化WakeLock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AlarmService::LocationWakeLock"
        )
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "AlarmService onStartCommand")
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        stopAlarm()
        releaseWakeLock()
        try {
            unregisterReceiver(locationAlarmReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "注销广播接收器失败", e)
        }
        super.onDestroy()
    }
    
    /**
     * 初始化AlarmManager
     */
    private fun initAlarmManager() {
        try {
            alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, AlarmReceiver::class.java).apply {
                action = ACTION_LOCATION_ALARM
            }
            locationIntent = PendingIntent.getBroadcast(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            Log.d(TAG, "AlarmManager初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "初始化AlarmManager失败", e)
        }
    }
    
    /**
     * 启动闹钟
     */
    fun startAlarm() {
        try {
            if (!isAlarmEnabled) {
                val triggerAtTime = SystemClock.elapsedRealtime() + alarmInterval
                alarmManager.setInexactRepeating(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtTime,
                    alarmInterval,
                    locationIntent
                )
                isAlarmEnabled = true
                Log.d(TAG, "闹钟已启动，间隔: ${alarmInterval}ms")
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动闹钟失败", e)
        }
    }
    
    /**
     * 停止闹钟
     */
    fun stopAlarm() {
        try {
            if (isAlarmEnabled) {
                alarmManager.cancel(locationIntent)
                isAlarmEnabled = false
                Log.d(TAG, "闹钟已停止")
            }
        } catch (e: Exception) {
            Log.e(TAG, "停止闹钟失败", e)
        }
    }
    
    /**
     * 设置定位唤醒间隔（毫秒）
     * @param intervalMillis 间隔时间，单位毫秒
     * @return 是否设置成功
     */
    fun setLocationAlarmInterval(intervalMillis: Long): Boolean {
        return try {
            if (intervalMillis in MIN_INTERVAL..MAX_INTERVAL) {
                this.alarmInterval = intervalMillis
                Log.d(TAG, "定位唤醒间隔已设置为: ${intervalMillis}ms (${intervalMillis / 1000 / 60}分钟)")
                
                // 如果闹钟已启动，重新启动以应用新间隔
                if (isAlarmEnabled) {
                    stopAlarm()
                    startAlarm()
                }
                true
            } else {
                Log.e(TAG, "定位间隔设置失败：间隔必须在${MIN_INTERVAL/1000/60}到${MAX_INTERVAL/1000/60}分钟之间")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置定位间隔失败", e)
            false
        }
    }
    
    /**
     * 获取当前定位间隔（毫秒）
     */
    fun getLocationAlarmInterval(): Long {
        return alarmInterval
    }
    
    /**
     * 获取最小定位间隔（毫秒）
     */
    fun getMinLocationAlarmInterval(): Long {
        return MIN_INTERVAL
    }
    
    /**
     * 获取最大定位间隔（毫秒）
     */
    fun getMaxLocationAlarmInterval(): Long {
        return MAX_INTERVAL
    }
    
    /**
     * 获取闹钟状态
     */
    fun isAlarmRunning(): Boolean {
        return isAlarmEnabled
    }
    
    /**
     * 获取WakeLock
     */
    private fun acquireWakeLock() {
        try {
            if (!wakeLock.isHeld) {
                wakeLock.acquire(10 * 60 * 1000L) // 10分钟超时
                Log.d(TAG, "WakeLock已获取")
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取WakeLock失败", e)
        }
    }
    
    /**
     * 释放WakeLock
     */
    private fun releaseWakeLock() {
        try {
            if (wakeLock.isHeld) {
                wakeLock.release()
                Log.d(TAG, "WakeLock已释放")
            }
        } catch (e: Exception) {
            Log.e(TAG, "释放WakeLock失败", e)
        }
    }
} 