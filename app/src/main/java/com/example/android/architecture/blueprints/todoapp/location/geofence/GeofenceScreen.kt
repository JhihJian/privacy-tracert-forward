package com.example.android.architecture.blueprints.todoapp.location.geofence

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.amap.maps.jsmap.AMap
import com.amap.maps.jsmap.AMapWrapper
import com.example.android.architecture.blueprints.todoapp.location.webview.MAWebViewWrapper
import com.example.android.architecture.blueprints.todoapp.location.webview.MyWebView
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 地理围栏页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofenceScreen(
    openDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GeofenceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // 处理消息和错误的副作用
    LaunchedEffect(uiState.message, uiState.error) {
        if (uiState.message != null) {
            snackbarHostState.showSnackbar(uiState.message!!)
            viewModel.clearMessage()
        }
        
        if (uiState.error != null) {
            snackbarHostState.showSnackbar(uiState.error!!)
            viewModel.clearError()
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
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                )
            )
        },
        floatingActionButton = {
            if (uiState.isMapReady && !uiState.isEditing) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.startNewGeofence() },
                    icon = { Icon(Icons.Filled.Add, contentDescription = "添加") },
                    text = { Text("添加打卡点") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // 地图容器
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    MapView(
                        onMapReady = { mapView ->
                            viewModel.setMapReady(true)
                            
                            // 加载已保存的围栏
                            uiState.geofences.forEach { geofence ->
                                when (geofence.type) {
                                    GeofenceType.CIRCLE -> {
                                        // 添加圆形围栏
                                        val circleJs = """
                                            var circle = new AMap.Circle({
                                                center: new AMap.LngLat(${geofence.longitude}, ${geofence.latitude}),
                                                radius: ${geofence.radius},
                                                strokeColor: "#3366FF", 
                                                strokeWeight: 2,
                                                strokeOpacity: 0.8,
                                                fillColor: "#3366FF",
                                                fillOpacity: 0.35,
                                                zIndex: 50,
                                                bubble: true,
                                                cursor: 'pointer'
                                            });
                                            circle.setMap(map);
                                            circle.geofenceId = "${geofence.id}";
                                        """.trimIndent()
                                        
                                        mapView.evaluateJavascript(circleJs, null)
                                    }
                                    GeofenceType.POLYGON -> {
                                        // 添加多边形围栏
                                        if (geofence.polygonPoints.isNotEmpty()) {
                                            val pointsString = geofence.polygonPoints.joinToString(",") { 
                                                "[${it.longitude},${it.latitude}]" 
                                            }
                                            
                                            val polygonJs = """
                                                var polygon = new AMap.Polygon({
                                                    path: [$pointsString],
                                                    strokeColor: "#3366FF", 
                                                    strokeWeight: 2,
                                                    strokeOpacity: 0.8,
                                                    fillColor: "#3366FF",
                                                    fillOpacity: 0.35,
                                                    zIndex: 50,
                                                    bubble: true,
                                                    cursor: 'pointer'
                                                });
                                                polygon.setMap(map);
                                                polygon.geofenceId = "${geofence.id}";
                                            """.trimIndent()
                                            
                                            mapView.evaluateJavascript(polygonJs, null)
                                        }
                                    }
                                }
                            }
                        }
                    )
                    
                    // 当正在编辑时显示编辑控件
                    if (uiState.isEditing) {
                        GeofenceEditControls(
                            geofence = uiState.currentGeofence,
                            onSave = { geofence -> 
                                viewModel.saveGeofence(geofence)
                            },
                            onCancel = { viewModel.cancelEditing() }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 地图视图组件
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapView(
    onMapReady: (AMap) -> Unit
) {
    val context = LocalContext.current
    
    AndroidView(
        factory = { context ->
            MyWebView(context).apply {
                webChromeClient = WebChromeClient()
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { webView ->
            // 初始化WebView和地图
            val webViewWrapper = MAWebViewWrapper(webView)
            val aMapWrapper = AMapWrapper(context, webViewWrapper)
            
            // 添加JavaScript接口
            class JsInterface {
                @JavascriptInterface
                fun onMapClick(data: String) {
                    // 处理地图点击事件
                    println("Map clicked: $data")
                }
            }
            
            webView.addJavascriptInterface(JsInterface(), "MapInterface")
            
            // 初始化地图
            aMapWrapper.onCreate()
            aMapWrapper.getMapAsyn(object : AMap.OnMapReadyListener {
                override fun onMapReady(map: AMap) {
                    // 设置地图点击事件
                    val setClickJs = """
                        map.on('click', function(e) {
                            const data = {
                                lnglat: {
                                    longitude: e.lnglat.getLng(),
                                    latitude: e.lnglat.getLat()
                                }
                            };
                            window.MapInterface.onMapClick(JSON.stringify(data));
                        });
                    """.trimIndent()
                    
                    webView.evaluateJavascript(setClickJs, null)
                    onMapReady(map)
                }
            })
        }
    )
    
    // 确保在组件销毁时清理资源
    DisposableEffect(Unit) {
        onDispose {
            // 在这里清理资源
        }
    }
}

/**
 * 围栏编辑控件
 */
@Composable
fun GeofenceEditControls(
    geofence: GeofenceData?,
    onSave: (GeofenceData) -> Unit,
    onCancel: () -> Unit
) {
    if (geofence == null) return
    
    var name by remember { mutableStateOf(geofence.name) }
    var radius by remember { mutableStateOf(geofence.radius) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                .padding(16.dp)
        ) {
            Text(
                text = "编辑打卡点",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("半径: ${radius.toInt()}米")
            Slider(
                value = radius,
                onValueChange = { radius = it },
                valueRange = 50f..500f,
                steps = 45,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    val updatedGeofence = geofence.copy(
                        name = name,
                        radius = radius
                    )
                    onSave(updatedGeofence)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Check, contentDescription = "保存")
                Spacer(modifier = Modifier.padding(4.dp))
                Text("保存")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = { showConfirmDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "删除")
                Spacer(modifier = Modifier.padding(4.dp))
                Text("删除")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            TextButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Close, contentDescription = "取消")
                Spacer(modifier = Modifier.padding(4.dp))
                Text("取消")
            }
        }
    }
    
    // 删除确认对话框
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除这个打卡点吗？此操作无法撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        geofence.id.let { id -> 
                            viewModel.deleteGeofence(id)
                        }
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmDialog = false }
                ) {
                    Text("取消")
                }
            }
        )
    }
} 