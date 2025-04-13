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

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.android.architecture.blueprints.todoapp.TodoTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.material3.Slider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationScreen(
    openDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LocationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("实时定位") },
                navigationIcon = {
                    IconButton(onClick = openDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "菜单")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                )
            )
        },
        floatingActionButton = {
            if (uiState.isPermissionGranted && uiState.isServiceBound) {
                FloatingActionButton(
                    onClick = { 
                        if (uiState.isLoading) viewModel.stopLocation() else viewModel.startLocation() 
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    if (uiState.isLoading) {
                        Icon(
                            Icons.Filled.Stop,
                            contentDescription = "停止定位",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "刷新定位",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        LocationContent(
            uiState = uiState,
            onPermissionResult = viewModel::updatePermissionStatus,
            onStartLocation = { viewModel.startLocation() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            viewModel = viewModel
        )
    }
}

@Composable
private fun LocationContent(
    uiState: LocationUiState,
    onPermissionResult: (Boolean) -> Unit,
    onStartLocation: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LocationViewModel
) {
    val context = LocalContext.current
    
    // 定位权限请求 - 仅保留必要的位置权限
    val permissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.FOREGROUND_SERVICE_LOCATION
    )
    
    // 记录权限状态
    var permissionsRequested by remember { mutableStateOf(false) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val allGranted = permissionsMap.values.all { it }
        Log.d("LocationScreen", "权限请求结果: $permissionsMap, 全部授予: $allGranted")
        onPermissionResult(allGranted)
        
        if (!allGranted) {
            // 显示指导用户如何手动开启权限的提示
            val message = "需要位置权限才能获取位置信息。请在设置中手动开启位置权限"
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    
    // 检查并请求权限
    LaunchedEffect(key1 = Unit) {
        val hasPermissions = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        
        Log.d("LocationScreen", "当前权限状态: $hasPermissions, 是否已请求过: $permissionsRequested")
        
        if (hasPermissions) {
            // 已有权限，通知ViewModel
            onPermissionResult(true)
        } else if (!permissionsRequested) {
            // 没有权限且未请求过，发起请求
            permissionsRequested = true
            permissionLauncher.launch(permissions)
        }
    }
    
    // 添加一个显式的权限请求按钮
    DisposableEffect(key1 = Unit) {
        // 首次进入界面时，请求权限
        if (!permissionsRequested) {
            permissionsRequested = true
            permissionLauncher.launch(permissions)
        }
        onDispose { }
    }
    
    Surface(
        modifier = modifier.fillMaxWidth(),
    ) {
        if (uiState.isLoading && uiState.currentLocation != null && uiState.currentLocation.address.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(text = "正在实时更新位置信息...")
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 显示JSON格式位置信息
                LocationJsonDisplay(locationJson = uiState.locationJson)
            }
        } else if (uiState.isPermissionGranted && uiState.isServiceBound) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.padding(bottom = 16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                    
                )
                Text(
                    text = "当前位置信息",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                if (uiState.currentLocation != null) {
                    Text(
                        text = "定位成功: ${uiState.currentLocation.address}",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // 显示具体的经纬度
                    Text(
                        text = "经度: ${uiState.currentLocation.longitude}, 纬度: ${uiState.currentLocation.latitude}",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // 显示定位精度和时间
                    Text(
                        text = "精度: ${uiState.currentLocation.accuracy}米, 时间: ${
                            uiState.currentLocation.time.let { time -> 
                                android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", java.util.Date(time)).toString()
                            }
                        }",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                } else {
                    Text(
                        text = uiState.message,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // 显示详细的错误信息
                    if (uiState.error != null) {
                        Text(
                            text = "错误详情: ${uiState.error}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                    
                    // 添加刷新按钮
                    Button(
                        onClick = { onStartLocation() },
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("重试定位")
                    }
                }
                
                // 显示JSON格式位置信息
                LocationJsonDisplay(
                    locationJson = uiState.locationJson,
                    modifier = Modifier.weight(1f)
                )
                
                // 添加上传控制UI
                UploadControl(
                    isEnabled = uiState.isUploadEnabled,
                    status = uiState.uploadStatus,
                    onEnableChanged = viewModel::setUploadEnabled,
                    onManualUpload = viewModel::uploadCurrentLocation,
                    serverUrl = viewModel.getServerUrl(),
                    onServerUrlChanged = viewModel::setServerUrl,
                    userName = uiState.userName,
                    onUserNameChanged = viewModel::setUserName
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.padding(bottom = 16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "准备定位",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = uiState.message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 检查是否具有位置权限，并提供请求按钮
                val hasLocationPermissions = permissions.all {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                }
                
                if (!hasLocationPermissions) {
                    Text(
                        text = "请授予位置权限以使用实时定位功能",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    
                    Button(
                        onClick = { 
                            permissionLauncher.launch(permissions)
                        },
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("请求位置权限")
                    }
                    
                    // 添加打开应用设置的按钮
                    Button(
                        onClick = {
                            try {
                                // 打开应用设置页面
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.parse("package:" + context.packageName)
                                )
                                intent.addCategory(android.content.Intent.CATEGORY_DEFAULT)
                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "无法打开应用设置", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("打开应用设置")
                    }
                } else if (!uiState.isServiceBound) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(16.dp)
                    )
                    Text(
                        text = "正在初始化定位服务...",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationJsonDisplay(
    locationJson: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text(
                text = "定位数据 (JSON格式)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF5F5F5))
                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(4.dp))
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (locationJson.isNotEmpty()) {
                    Text(
                        text = locationJson,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "暂无位置数据",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
private fun UploadControl(
    isEnabled: Boolean,
    status: String,
    onEnableChanged: (Boolean) -> Unit,
    onManualUpload: () -> Unit,
    serverUrl: String = "",
    onServerUrlChanged: (String) -> Unit = {},
    userName: String = "默认用户",
    onUserNameChanged: (String) -> Unit = {}
) {
    var showUrlDialog by remember { mutableStateOf(false) }
    var currentServerUrl by remember { mutableStateOf(serverUrl) }
    
    // 用户名对话框状态
    var showUserNameDialog by remember { mutableStateOf(false) }
    var currentUserName by remember { mutableStateOf(userName) }
    
    // 刷新URL显示
    LaunchedEffect(serverUrl) {
        currentServerUrl = serverUrl
    }
    
    // 刷新用户名显示
    LaunchedEffect(userName) {
        currentUserName = userName
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "位置数据上传",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // 显示当前服务器URL
            Text(
                text = "服务器URL: ${if (currentServerUrl.isNotEmpty()) currentServerUrl else "未设置"}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
            
            // 修改URL按钮
            Button(
                onClick = { showUrlDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("修改服务器URL")
            }
            
            // 显示当前用户名
            Text(
                text = "用户名: $currentUserName",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
            
            // 修改用户名按钮
            Button(
                onClick = { showUserNameDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("修改用户名")
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "启用数据上传:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onEnableChanged
                )
            }
            
            Text(
                text = "当前状态: $status",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            Button(
                onClick = onManualUpload,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                enabled = isEnabled
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("手动上传当前位置")
            }
        }
    }
    
    // URL修改对话框
    if (showUrlDialog) {
        var inputUrl by remember { mutableStateOf(currentServerUrl) }
        
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("设置服务器URL") },
            text = {
                Column {
                    Text("请输入位置数据上传的服务器URL:", 
                        modifier = Modifier.padding(bottom = 8.dp))
                    
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        label = { Text("服务器URL") },
                        placeholder = { Text("http://your-server/api/location") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputUrl.isNotEmpty()) {
                            onServerUrlChanged(inputUrl)
                            currentServerUrl = inputUrl
                        }
                        showUrlDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showUrlDialog = false }
                ) {
                    Text("取消")
                }
            }
        )
    }
    
    // 用户名修改对话框
    if (showUserNameDialog) {
        var inputUserName by remember { mutableStateOf(currentUserName) }
        
        AlertDialog(
            onDismissRequest = { showUserNameDialog = false },
            title = { Text("设置用户名") },
            text = {
                Column {
                    Text("请输入您的用户名:", 
                        modifier = Modifier.padding(bottom = 8.dp))
                    
                    OutlinedTextField(
                        value = inputUserName,
                        onValueChange = { inputUserName = it },
                        label = { Text("用户名") },
                        placeholder = { Text("请输入用户名") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputUserName.isNotEmpty()) {
                            onUserNameChanged(inputUserName)
                            currentUserName = inputUserName
                        }
                        showUserNameDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showUserNameDialog = false }
                ) {
                    Text("取消")
                }
            }
        )
    }
}

@Preview
@Composable
fun LocationScreenPreview() {
    TodoTheme {
        LocationScreen(openDrawer = {})
    }
} 
