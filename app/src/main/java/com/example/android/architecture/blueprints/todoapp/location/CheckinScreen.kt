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
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.MyLocationStyle
import com.example.android.architecture.blueprints.todoapp.location.utils.AMapHelper
import com.example.android.architecture.blueprints.todoapp.location.utils.LocationPermissionManager
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
    
    // 地图相关变量
    var mapView by remember { mutableStateOf<MapView?>(null) }
    val mapHelper = remember { AMapHelper(context) }
    
    // 使用remember确保权限管理器只初始化一次
    val currentActivity = LocalContext.current as androidx.activity.ComponentActivity
    val permissionManagerRemembered = remember(currentActivity) {
        // 在Compose的remember块中创建权限管理器
        viewModel.setupPermissionManager(currentActivity)
        // 返回一个标记，表示已初始化
        true
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
            // 如果地图已就绪且位置非空，在地图上显示位置
            if (uiState.isMapReady) {
                mapHelper.showLocation(location)
            }
        }
    }
    
    // 监听生命周期，从设置页面返回时刷新权限状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(key1 = lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // 在应用恢复时，刷新权限状态
                    viewModel.refreshPermissionStatus(currentActivity)
                    mapHelper.onResume()
                }
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
            } else if (!uiState.isPermissionGranted) {
                // 添加权限请求按钮
                FloatingActionButton(
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
                            
                            // 提示用户操作
                            Toast.makeText(
                                context, 
                                "请在设置中开启位置权限，然后返回应用", 
                                Toast.LENGTH_LONG
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "无法打开应用设置", Toast.LENGTH_SHORT).show()
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "开启权限",
                        tint = MaterialTheme.colorScheme.error
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
                // 使用AndroidView加载原生地图
                AndroidView(
                    factory = { ctx ->
                        // 创建MapView
                        MapView(ctx).apply {
                            // 保存MapView实例
                            mapView = this
                            
                            // 使用AMapHelper初始化地图
                            mapHelper.initMap(this) { map ->
                                // 地图初始化完成
                                viewModel.updateMapReadyStatus(true)
                                
                                // 设置地图点击事件
                                map.setOnMapClickListener { latLng ->
                                    // 在地图上点击时可以添加自定义逻辑，例如添加打卡点
                                    Log.d("CheckinScreen", "地图点击: ${latLng.latitude}, ${latLng.longitude}")
                                }
                                
                                // 如果已经有位置数据，直接显示
                                uiState.currentLocation?.let { location ->
                                    mapHelper.showLocation(location)
                                }
                            }
                        }
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