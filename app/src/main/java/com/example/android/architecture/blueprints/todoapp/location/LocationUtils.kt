/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.architecture.blueprints.todoapp.location

import android.text.TextUtils
import com.amap.api.location.AMapLocation
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 位置工具类，提供位置数据处理相关功能
 */
object LocationUtils {
    
    /**
     * 将位置信息转换为JSON格式字符串
     */
    fun locationToJson(location: AMapLocation?): String {
        if (location == null) {
            return "{\"error\": \"没有位置数据\"}"
        }
        
        val jsonObject = JSONObject()
        try {
            // 定位时间
            val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
            val time = timeFormat.format(Date(location.time))
            
            if (location.errorCode == 0) {
                // 定位成功
                jsonObject.put("status", "success")
                
                // 基本位置信息
                val basic = JSONObject()
                basic.put("latitude", location.latitude)
                basic.put("longitude", location.longitude)
                basic.put("accuracy", location.accuracy)
                basic.put("provider", location.provider)
                basic.put("time", time)
                jsonObject.put("basic", basic)
                
                // 地址信息
                if (!TextUtils.isEmpty(location.address)) {
                    val address = JSONObject()
                    address.put("country", location.country ?: "")
                    address.put("province", location.province ?: "")
                    address.put("city", location.city ?: "")
                    address.put("district", location.district ?: "")
                    address.put("street", location.street ?: "")
                    address.put("streetNum", location.streetNum ?: "")
                    address.put("cityCode", location.cityCode ?: "")
                    address.put("adCode", location.adCode ?: "")
                    address.put("address", location.address ?: "")
                    address.put("poiName", location.poiName ?: "")
                    jsonObject.put("address", address)
                }
                
                // 附加信息
                val extra = JSONObject()
                if (location.hasAltitude()) {
                    extra.put("altitude", location.altitude)
                }
                if (location.hasBearing()) {
                    extra.put("bearing", location.bearing)
                }
                if (location.hasSpeed()) {
                    extra.put("speed", location.speed)
                }
                jsonObject.put("extra", extra)
                
                // GPS信息
                if (location.satellites > 0) {
                    val gps = JSONObject()
                    gps.put("satellites", location.satellites)
                    jsonObject.put("gps", gps)
                }
            } else {
                // 定位失败
                jsonObject.put("status", "error")
                jsonObject.put("errorCode", location.errorCode)
                jsonObject.put("errorInfo", location.errorInfo)
                jsonObject.put("time", time)
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        
        return jsonObject.toString(2) // 格式化JSON，缩进为2个空格
    }
} 