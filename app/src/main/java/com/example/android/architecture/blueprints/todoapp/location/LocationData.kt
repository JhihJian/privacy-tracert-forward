package com.example.android.architecture.blueprints.todoapp.location

/**
 * 位置数据类
 * 封装详细的位置信息
 */
data class LocationData(
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val time: Long = 0L,
    // 其他必要的位置信息字段
    val country: String = "",
    val province: String = "",
    val city: String = "",
    val district: String = "",
    val street: String = "",
    val streetNum: String = "",
    val cityCode: String = "",
    val adCode: String = "",
    val poiName: String = "",
    val aoiName: String = "",
    val buildingId: String = "",
    val floor: String = "",
    val gpsAccuracyStatus: Int = 0,
    val locationType: Int = 0,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val altitude: Double = 0.0,
    val errorCode: Int = 0,
    val errorInfo: String = "",
    val coordType: String = "",
    val locationDetail: String = ""
) 