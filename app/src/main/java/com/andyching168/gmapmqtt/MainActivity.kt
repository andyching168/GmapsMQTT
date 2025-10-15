package com.andyching168.gmapmqtt

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.andyching168.gmapmqtt.NavigationInfo
import com.andyching168.gmapmqtt.NavigationViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andyching168.gmapmqtt.ui.theme.GmapMQTTTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 啟動時檢查是否需要自動連線 USB
        autoConnectUsb()
        
        setContent {
            GmapMQTTTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavigationScreen()
                }
            }
        }
    }
    
    private fun autoConnectUsb() {
        val usbSettingsManager = GmapMQTTApp.getInstance().getUsbSettingsManager()
        val usbSerialManager = GmapMQTTApp.getInstance().getUsbSerialManager()
        
        // 使用協程來讀取設定並連線
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            usbSettingsManager.usbConfigFlow.collect { config ->
                if (config.autoConnect && config.deviceName.isNotEmpty()) {
                    android.util.Log.d("MainActivity", "Auto-connecting to USB: ${config.deviceName}")
                    // 掃描裝置並嘗試連線到上次的裝置
                    usbSerialManager.scanDevices()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d("MainActivity", "onResume called")
        ensureServiceEnabled()
    }

    private fun ensureServiceEnabled() {
        val isEnabled = isNotificationServiceEnabled()
        android.util.Log.d("MainActivity", "Service enabled: $isEnabled")
        if (isEnabled) {
            android.util.Log.d("MainActivity", "Requesting rebind...")
            requestRebind()
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val packageName = packageName
        val flat = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        if (flat.isNullOrEmpty()) {
            return false
        }
        val names = flat.split(":").toTypedArray()
        for (name in names) {
            val cn = ComponentName.unflattenFromString(name)
            if (cn != null) {
                if (packageName == cn.packageName) {
                    return true
                }
            }
        }
        return false
    }

    private fun requestRebind() {
        try {
            val componentName = ComponentName(this, NotificationCatcherService::class.java)
            android.service.notification.NotificationListenerService.requestRebind(componentName)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun NavigationScreen() {
    val viewModel: NavigationViewModel = GmapMQTTApp.getInstance().getNavigationViewModel()
    val usbSerialManager = GmapMQTTApp.getInstance().getUsbSerialManager()
    val usbSettingsManager = GmapMQTTApp.getInstance().getUsbSettingsManager()
    val context = LocalContext.current
    val navigationInfoState = viewModel.navigationInfo.collectAsStateWithLifecycle()
    val navigationInfo = navigationInfoState.value
    val unknownHashesState = viewModel.unknownHashes.collectAsStateWithLifecycle()
    val unknownHashes = unknownHashesState.value
    var showJsonDialog by remember { mutableStateOf(false) }
    var showRawNotificationDialog by remember { mutableStateOf(false) }
    var showUsbSettings by remember { mutableStateOf(false) }

    var isServiceEnabled by remember {
        mutableStateOf(isNotificationServiceEnabled(context))
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isServiceEnabled = isNotificationServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showUsbSettings) {
        UsbSettingsDialog(
            usbSettingsManager = usbSettingsManager,
            usbSerialManager = usbSerialManager,
            onDismiss = { showUsbSettings = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isServiceEnabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isServiceEnabled) "✓ 通知存取權限已啟用" else "✗ 通知存取權限未啟用",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isServiceEnabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
                if (!isServiceEnabled) {
                    Text(
                        text = "請點擊下方按鈕開啟通知存取權限",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                context.startActivity(intent)
            }
        ) {
            Text("開啟通知存取權限")
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showUsbSettings = true },
                modifier = Modifier.weight(1f)
            ) {
                Text("USB 設定")
            }
            
            Button(
                onClick = { viewModel.openGoogleMaps(context) },
                modifier = Modifier.weight(1f)
            ) {
                Text("開啟 Google Maps")
            }
        }
        
        // USB 連線狀態顯示
        UsbStatusCard(usbSerialManager)

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showRawNotificationDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("顯示原始通知")
                }

                Button(
                    onClick = { showJsonDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("顯示 JSON")
                }
            }

            Button(
                onClick = { viewModel.openGoogleMaps(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("開啟 Google Maps")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "導航狀態",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                if (!navigationInfo.hasNotification) {
                    Text(
                        text = "目前沒有導航通知",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (navigationInfo.isRerouting) {
                    Text(
                        text = "正在重新規劃路線...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    NavigationInfoItem("方向", navigationInfo.direction)
                    NavigationInfoItem("剩餘總距離", navigationInfo.totalDistance)
                    NavigationInfoItem("轉彎距離", navigationInfo.turnDistance)
                    NavigationInfoItem("轉彎方向", navigationInfo.turnDirection)
                    NavigationInfoItem("時間", navigationInfo.duration)
                    NavigationInfoItem("預計到達", navigationInfo.eta)
                }
            }
        }

        if (unknownHashes.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "未知哈希值列表",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        items(unknownHashes) { (timestamp, hash, bitmap) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "未知圖標",
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = timestamp,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = hash,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                                Button(
                                    onClick = { viewModel.copyHashToClipboard(context, hash) }
                                ) {
                                    Text("複製")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showJsonDialog) {
        AlertDialog(
            onDismissRequest = { showJsonDialog = false },
            title = { Text("導航資訊 JSON") },
            text = {
                Column {
                    Text(
                        text = viewModel.generateNavigationJson(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.copyJsonToClipboard(context) }) {
                    Text("複製")
                }
            },
            dismissButton = {
                Button(onClick = { showJsonDialog = false }) {
                    Text("關閉")
                }
            }
        )
    }

    if (showRawNotificationDialog) {
        AlertDialog(
            onDismissRequest = { showRawNotificationDialog = false },
            title = { Text("原始通知內容") },
            text = {
                Column {
                    Text(
                        text = viewModel.getLastRawNotification(),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.copyRawNotificationToClipboard(context) }) {
                    Text("複製")
                }
            },
            dismissButton = {
                Button(onClick = { showRawNotificationDialog = false }) {
                    Text("關閉")
                }
            }
        )
    }
}

@Composable
fun NavigationInfoItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

fun isNotificationServiceEnabled(context: Context): Boolean {
    val packageName = context.packageName
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    )
    if (flat.isNullOrEmpty()) {
        return false
    }
    val names = flat.split(":").toTypedArray()
    for (name in names) {
        val cn = ComponentName.unflattenFromString(name)
        if (cn != null) {
            if (packageName == cn.packageName) {
                return true
            }
        }
    }
    return false
}

@Composable
fun UsbStatusCard(usbSerialManager: UsbSerialManager) {
    val connectionState = usbSerialManager.connectionState.collectAsStateWithLifecycle()
    val errorMessage = usbSerialManager.errorMessage.collectAsStateWithLifecycle()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (connectionState.value) {
                UsbConnectionState.CONNECTED -> MaterialTheme.colorScheme.primaryContainer
                UsbConnectionState.CONNECTING -> MaterialTheme.colorScheme.secondaryContainer
                UsbConnectionState.ERROR -> MaterialTheme.colorScheme.errorContainer
                UsbConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "USB Serial 連線狀態",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = when (connectionState.value) {
                    UsbConnectionState.CONNECTED -> "✓ 已連線: ${usbSerialManager.getCurrentDeviceName()}"
                    UsbConnectionState.CONNECTING -> "⟳ 連線中..."
                    UsbConnectionState.ERROR -> "✗ 錯誤"
                    UsbConnectionState.DISCONNECTED -> "○ 未連線"
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            
            errorMessage.value?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsbSettingsDialog(
    usbSettingsManager: UsbSettingsManager,
    usbSerialManager: UsbSerialManager,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    val usbConfig = usbSettingsManager.usbConfigFlow.collectAsStateWithLifecycle(
        initialValue = UsbConfig()
    )
    
    val availableDevices = usbSerialManager.availableDevices.collectAsStateWithLifecycle()
    
    var selectedDeviceIndex by remember { mutableStateOf(-1) }
    var baudRate by remember { mutableStateOf("115200") }
    var autoConnect by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    
    // 初始化設定值
    LaunchedEffect(usbConfig.value) {
        baudRate = usbConfig.value.baudRate.toString()
        autoConnect = usbConfig.value.autoConnect
    }
    
    // 掃描裝置
    LaunchedEffect(Unit) {
        usbSerialManager.scanDevices()
    }
    
    // 如果只有一個裝置，自動選擇它
    LaunchedEffect(availableDevices.value) {
        if (availableDevices.value.size == 1 && selectedDeviceIndex == -1) {
            selectedDeviceIndex = 0
            android.util.Log.d("UsbSettings", "自動選擇唯一的 USB 裝置: ${availableDevices.value[0].deviceName}")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("USB Serial 設定") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "請選擇 USB 裝置",
                    style = MaterialTheme.typography.titleSmall
                )
                
                // 裝置選擇下拉選單
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = if (selectedDeviceIndex >= 0 && selectedDeviceIndex < availableDevices.value.size) {
                            val device = availableDevices.value[selectedDeviceIndex]
                            "${device.deviceName} (VID:${device.vendorId.toString(16)}, PID:${device.productId.toString(16)})"
                        } else {
                            "未選擇裝置"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("USB 裝置") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        if (availableDevices.value.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("未找到 USB 裝置") },
                                onClick = { }
                            )
                        } else {
                            availableDevices.value.forEachIndexed { index, device ->
                                DropdownMenuItem(
                                    text = {
                                        Text("${device.deviceName}\nVID:${device.vendorId.toString(16)} PID:${device.productId.toString(16)}")
                                    },
                                    onClick = {
                                        selectedDeviceIndex = index
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                Button(
                    onClick = { usbSerialManager.scanDevices() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("重新掃描裝置")
                }
                
                OutlinedTextField(
                    value = baudRate,
                    onValueChange = { baudRate = it },
                    label = { Text("鮑率 (Baud Rate)") },
                    placeholder = { Text("115200") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("自動連線")
                    Switch(
                        checked = autoConnect,
                        onCheckedChange = { autoConnect = it }
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            if (selectedDeviceIndex >= 0 && selectedDeviceIndex < availableDevices.value.size) {
                                val device = availableDevices.value[selectedDeviceIndex]
                                val config = UsbConfig(
                                    deviceName = device.deviceName,
                                    baudRate = baudRate.toIntOrNull() ?: 115200,
                                    dataBits = 8,
                                    stopBits = 1,
                                    parity = 0,
                                    autoConnect = autoConnect,
                                    vendorId = device.vendorId,
                                    productId = device.productId
                                )
                                usbSettingsManager.saveUsbConfig(config)
                                
                                if (usbSerialManager.isConnected()) {
                                    usbSerialManager.disconnect()
                                }
                                
                                usbSerialManager.connect(device.device, config)
                                
                                Toast.makeText(context, "設定已儲存", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                Toast.makeText(context, "請先選擇 USB 裝置", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("儲存並連線")
                }
                
                if (usbSerialManager.isConnected()) {
                    Button(
                        onClick = {
                            usbSerialManager.disconnect()
                            Toast.makeText(context, "已斷線", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("斷線")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// Make sure your build.gradle.kts includes:
// implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")
