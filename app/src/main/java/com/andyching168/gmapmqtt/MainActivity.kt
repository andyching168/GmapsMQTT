package com.andyching168.gmapmqtt

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var hasPromptedBackgroundLocationSettings = false

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach { entry ->
            if (!entry.value) {
                Log.d("MainActivity", "權限被拒絕: ${entry.key}")
            }
        }
        ensureBackgroundLocationPermission()
        startNotificationServiceIfReady()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action == NotificationCatcherService.EXIT_APP_ACTION) {
            Log.d("MainActivity", "收到結束應用指令")
            finishAndRemoveTask()
            return
        }

        enableEdgeToEdge()
        
        // 啟動時檢查是否需要自動連線 MQTT
        autoConnectMqtt()
        
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

        checkBatteryOptimization()
        checkAndRequestPermissions()
    }
    
    private fun autoConnectMqtt() {
        val mqttSettingsManager = GmapMQTTApp.getInstance().getMqttSettingsManager()
        val mqttClientManager = GmapMQTTApp.getInstance().getMqttClientManager()
        
        // 使用協程來讀取設定並連線
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            mqttSettingsManager.mqttConfigFlow.collect { config ->
                if (config.autoConnect && config.brokerUrl.isNotEmpty()) {
                    android.util.Log.d("MainActivity", "Auto-connecting to MQTT: ${config.brokerUrl}")
                    mqttClientManager.connect(config)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d("MainActivity", "onResume called")
        ensureServiceEnabled()
        ensureBackgroundLocationPermission()
        startNotificationServiceIfReady()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == NotificationCatcherService.EXIT_APP_ACTION) {
            Log.d("MainActivity", "收到結束應用指令(onNewIntent)")
            finishAndRemoveTask()
        }
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

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (permissionsToRequest.isEmpty()) {
            ensureBackgroundLocationPermission()
            startNotificationServiceIfReady()
        } else {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun ensureBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (hasPromptedBackgroundLocationSettings) return

        val hasForegroundLocation =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val hasBackgroundLocation =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        if (hasForegroundLocation && !hasBackgroundLocation) {
            hasPromptedBackgroundLocationSettings = true
            Toast.makeText(
                this,
                "請在位置權限中選擇「一律允許」，讓背景 MQTT GPS 持續更新",
                Toast.LENGTH_LONG
            ).show()
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("MainActivity", "開啟 App 權限設定失敗", e)
            }
        }
    }

    private fun startNotificationServiceIfReady() {
        if (!isNotificationServiceEnabled()) {
            Log.d("MainActivity", "通知監聽權限未啟用，暫不啟動前台服務")
            return
        }
        startNotificationService()
    }

    private fun startNotificationService() {
        try {
            val serviceIntent = Intent(this, NotificationCatcherService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Log.d("MainActivity", "已啟動通知監聽前台服務")
        } catch (e: Exception) {
            Log.e("MainActivity", "啟動通知監聽服務失敗", e)
            Toast.makeText(this, "啟動服務失敗: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("MainActivity", "請求忽略電池最佳化失敗", e)
                }
            }
        }
    }
}

@Composable
fun NavigationScreen() {
    val viewModel: NavigationViewModel = GmapMQTTApp.getInstance().getNavigationViewModel()
    val mqttClientManager = GmapMQTTApp.getInstance().getMqttClientManager()
    val mqttSettingsManager = GmapMQTTApp.getInstance().getMqttSettingsManager()
    val context = LocalContext.current
    val navigationInfoState = viewModel.navigationInfo.collectAsStateWithLifecycle()
    val navigationInfo = navigationInfoState.value
    val unknownHashesState = viewModel.unknownHashes.collectAsStateWithLifecycle()
    val unknownHashes = unknownHashesState.value
    var showJsonDialog by remember { mutableStateOf(false) }
    var showRawNotificationDialog by remember { mutableStateOf(false) }
    var showMqttSettings by remember { mutableStateOf(false) }

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

    if (showMqttSettings) {
        MqttSettingsDialog(
            mqttSettingsManager = mqttSettingsManager,
            mqttClientManager = mqttClientManager,
            onDismiss = { showMqttSettings = false }
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
                onClick = { showMqttSettings = true },
                modifier = Modifier.weight(1f)
            ) {
                Text("MQTT 設定")
            }
            
            Button(
                onClick = { viewModel.openGoogleMaps(context) },
                modifier = Modifier.weight(1f)
            ) {
                Text("開啟 Google Maps")
            }
        }
        
        // MQTT 連線狀態顯示
        MqttStatusCard(mqttClientManager)

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

            Button(
                onClick = {
                    val serviceIntent = Intent(context, NotificationCatcherService::class.java).apply {
                        action = NotificationCatcherService.EXIT_APP_ACTION
                    }
                    context.startService(serviceIntent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("結束應用程式")
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
fun MqttStatusCard(mqttClientManager: MqttClientManager) {
    val connectionState = mqttClientManager.connectionState.collectAsStateWithLifecycle()
    val errorMessage = mqttClientManager.errorMessage.collectAsStateWithLifecycle()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (connectionState.value) {
                MqttConnectionState.CONNECTED -> MaterialTheme.colorScheme.primaryContainer
                MqttConnectionState.CONNECTING -> MaterialTheme.colorScheme.secondaryContainer
                MqttConnectionState.ERROR -> MaterialTheme.colorScheme.errorContainer
                MqttConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant
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
                text = "MQTT 連線狀態",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = when (connectionState.value) {
                    MqttConnectionState.CONNECTED -> "✓ 已連線"
                    MqttConnectionState.CONNECTING -> "⟳ 連線中..."
                    MqttConnectionState.ERROR -> "✗ 錯誤"
                    MqttConnectionState.DISCONNECTED -> "○ 未連線"
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
fun MqttSettingsDialog(
    mqttSettingsManager: MqttSettingsManager,
    mqttClientManager: MqttClientManager,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val mqttConfig = mqttSettingsManager.mqttConfigFlow.collectAsStateWithLifecycle(
        initialValue = MqttConfig()
    )
    
    var brokerUrl by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var autoConnect by remember { mutableStateOf(false) }
    
    // 初始化設定值
    LaunchedEffect(mqttConfig.value) {
        brokerUrl = mqttConfig.value.brokerUrl
        port = mqttConfig.value.port.toString()
        username = mqttConfig.value.username
        password = mqttConfig.value.password
        topic = mqttConfig.value.topic
        autoConnect = mqttConfig.value.autoConnect
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("MQTT 設定") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = brokerUrl,
                    onValueChange = { brokerUrl = it },
                    label = { Text("Broker URL") },
                    placeholder = { Text("例: broker.hivemq.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    placeholder = { Text("1883") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("使用者名稱 (選填)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密碼 (選填)") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("推送主題") },
                    placeholder = { Text("navigation/info") },
                    modifier = Modifier.fillMaxWidth(),
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
                            val config = MqttConfig(
                                brokerUrl = brokerUrl,
                                username = username,
                                password = password,
                                topic = topic.ifEmpty { "navigation/info" },
                                autoConnect = autoConnect,
                                port = port.toIntOrNull() ?: 1883
                            )
                            mqttSettingsManager.saveMqttConfig(config)
                            
                            if (mqttClientManager.isConnected()) {
                                mqttClientManager.disconnect()
                            }
                            
                            if (brokerUrl.isNotEmpty()) {
                                mqttClientManager.connect(config)
                            }
                            
                            Toast.makeText(context, "設定已儲存", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }
                ) {
                    Text("儲存並連線")
                }
                
                if (mqttClientManager.isConnected()) {
                    Button(
                        onClick = {
                            mqttClientManager.disconnect()
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
            Button(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// Make sure your build.gradle.kts includes:
// implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")
