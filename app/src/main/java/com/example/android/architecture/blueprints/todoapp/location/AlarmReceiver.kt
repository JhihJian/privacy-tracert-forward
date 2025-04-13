package com.example.android.architecture.blueprints.todoapp.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 闹钟广播接收器
 */
class AlarmReceiver : BroadcastReceiver() {
    private val TAG = "AlarmReceiver"
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == AlarmService.ACTION_LOCATION_ALARM) {
            Log.d(TAG, "收到闹钟广播")
            // 启动 AlarmService 处理位置更新
            val serviceIntent = Intent(context, AlarmService::class.java)
            context?.startService(serviceIntent)
        }
    }
} 