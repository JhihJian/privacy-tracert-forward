package com.example.android.architecture.blueprints.todoapp.location.geofence

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 地理围栏数据类
 */
@Entity(tableName = "geofences")
@TypeConverters(GeofenceConverters::class)
data class GeofenceData(
    @PrimaryKey
    val id: String,
    val name: String,
    val radius: Float,
    val latitude: Double,
    val longitude: Double,
    val address: String = "",
    val polygonPoints: List<LatLng> = emptyList(),
    val type: GeofenceType = GeofenceType.CIRCLE,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)

/**
 * 经纬度点
 */
data class LatLng(
    val latitude: Double,
    val longitude: Double
)

/**
 * 围栏类型
 */
enum class GeofenceType {
    CIRCLE, // 圆形围栏
    POLYGON // 多边形围栏
}

/**
 * Room类型转换器
 */
class GeofenceConverters {
    private val gson = Gson()

    @TypeConverter
    fun fromLatLngList(value: List<LatLng>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toLatLngList(value: String): List<LatLng> {
        val listType = object : TypeToken<List<LatLng>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun fromGeofenceType(value: GeofenceType): String {
        return value.name
    }

    @TypeConverter
    fun toGeofenceType(value: String): GeofenceType {
        return try {
            GeofenceType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            GeofenceType.CIRCLE
        }
    }
} 