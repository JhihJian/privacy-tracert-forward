/*
 * Copyright 2019 The Android Open Source Project
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

package com.example.android.architecture.blueprints.todoapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import android.util.Log
import com.example.android.architecture.blueprints.todoapp.location.LocationViewModel

/**
 * Main activity for the todoapp
 */
@AndroidEntryPoint
class TodoActivity : ComponentActivity() {
    
    private val TAG = "TodoActivity"
    
    // 延迟初始化ViewModel
    private lateinit var locationViewModel: LocationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 初始化ViewModel
        locationViewModel = ViewModelProvider(this)[LocationViewModel::class.java]
        
        // 注册生命周期监听器
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    // 应用进入前台
                    Log.d(TAG, "应用进入前台")
                    locationViewModel.setForegroundMode(true)
                }
                Lifecycle.Event.ON_STOP -> {
                    // 应用进入后台
                    Log.d(TAG, "应用进入后台")
                    locationViewModel.setForegroundMode(false)
                }
                else -> {}
            }
        })
        
        setContent {
            TodoTheme {
                TodoNavGraph()
            }
        }
    }
}
