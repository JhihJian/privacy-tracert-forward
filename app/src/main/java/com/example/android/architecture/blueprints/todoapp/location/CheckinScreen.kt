package com.example.android.architecture.blueprints.todoapp.location

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.android.architecture.blueprints.todoapp.location.webview.AMapHelper
import com.example.android.architecture.blueprints.todoapp.location.webview.MyWebView
import com.example.android.architecture.blueprints.todoapp.location.webview.MapProxy
import com.example.android.architecture.blueprints.todoapp.location.webview.MarkerOptions
import com.example.android.architecture.blueprints.todoapp.location.webview.CameraUpdateFactory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckinScreen(
    openDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CheckinViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    // 地图控制器
    val mapHelper = remember { AMapHelper(context) }
    
    // 检查位置权限
    val permissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    
    var permissionsRequested by remember { mutableStateOf(false) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val allGranted = permissionsMap.values.all { it }
        Log.d("CheckinScreen", "权限请求结果: $permissionsMap, 全部授予: $allGranted")
        viewModel.updatePermissionStatus(allGranted)
        
        if (!allGranted) {
            // 显示权限提示
            coroutineScope.launch {
                snackbarHostState.showSnackbar("需要位置权限才能获取位置信息")
            }
        }
    }
    
    // 检查权限状态
    LaunchedEffect(key1 = Unit) {
        val hasPermissions = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        
        if (hasPermissions) {
            viewModel.updatePermissionStatus(true)
        } else if (!permissionsRequested) {
            permissionsRequested = true
            permissionLauncher.launch(permissions)
        }
    }
    
    // 处理错误信息
    LaunchedEffect(key1 = uiState.errorMessage) {
        uiState.errorMessage?.let { errorMessage ->
            snackbarHostState.showSnackbar(errorMessage)
        }
    }
    
    // 订阅位置数据流
    LaunchedEffect(Unit) {
        viewModel.subscribeToLocationUpdates()
    }
    
    // 监听位置变化，更新地图
    LaunchedEffect(key1 = uiState.currentLocation) {
        uiState.currentLocation?.let { location ->
            // 如果地图已就绪且位置非空，在网页中显示位置
            if (uiState.isMapReady) {
                // 使用JavaScript在网页中显示位置
                mapHelper.webViewRef?.let { webView ->
                    mapHelper.showLocation(webView, location)
                }
            }
        }
    }
    
    // 监听生命周期
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(key1 = lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapHelper.onResume()
                Lifecycle.Event.ON_PAUSE -> mapHelper.onPause()
                Lifecycle.Event.ON_DESTROY -> mapHelper.onDestroy()
                else -> {}
            }
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("新增打卡点") },
                navigationIcon = {
                    IconButton(onClick = openDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "菜单")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            if (uiState.isMapReady && uiState.isPermissionGranted) {
                FloatingActionButton(
                    onClick = { viewModel.getCurrentLocation() },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "定位",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 地图容器
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
            ) {
                // 使用AndroidView加载WebView
                AndroidView(
                    factory = { ctx ->
                        val webView = MyWebView(ctx).apply {
                            // 初始化地图
                            mapHelper.initMap(this) { mapProxy ->
                                // 地图加载完成
                                viewModel.updateMapReadyStatus(true)
                                
                                // 设置地图UI控件
                                val uiSettings = mapProxy.getUiSettings()
                                uiSettings.setZoomControlsEnabled(true)
                                uiSettings.setCompassEnabled(true)
                                uiSettings.setMyLocationButtonEnabled(true)
                                uiSettings.setScaleControlsEnabled(true)
                                
                                // 设置地图点击事件
                                mapProxy.setOnMapClickListener { latLng ->
                                    // 在地图上点击时可以添加自定义逻辑，例如添加打卡点
                                    Log.d("CheckinScreen", "地图点击: ${latLng.latitude}, ${latLng.longitude}")
                                }
                                
                                // 如果已经有位置数据，直接显示
                                uiState.currentLocation?.let { location ->
                                    mapHelper.showLocation(this, location)
                                }
                            }
                        }
                        webView
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                // 加载指示器
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
} 