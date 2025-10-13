package com.andyching168.notificationcatcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andyching168.notificationcatcher.ui.theme.NotificationCatcherTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NotificationCatcherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavigationScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d("MainActivity", "onResume called")
        // 每次 Activity 恢復時，確保 Service 已啟用並嘗試重新連接
        ensureServiceEnabled()
    }

    private fun ensureServiceEnabled() {
        val isEnabled = isNotificationServiceEnabled()
        android.util.Log.d("MainActivity", "Service enabled: $isEnabled")
        if (isEnabled) {
            // 權限已啟用，嘗試重新啟動服務以確保連接
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
            // 請求系統重新綁定 NotificationListenerService
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                val componentName = ComponentName(this, NotificationCatcherService::class.java)
                android.service.notification.NotificationListenerService.requestRebind(componentName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun NavigationScreen() {
    val viewModel: NavigationViewModel = NotificationCatcherApp.getInstance().getNavigationViewModel()
    val context = LocalContext.current
    val navigationInfo by viewModel.navigationInfo.collectAsStateWithLifecycle()
    val unknownHashes by viewModel.unknownHashes.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    var showJsonDialog by remember { mutableStateOf(false) }
    var showRawNotificationDialog by remember { mutableStateOf(false) }
    
    // 檢查通知權限狀態
    var isServiceEnabled by remember {
        mutableStateOf(isNotificationServiceEnabled(context))
    }

    // 使用 DisposableEffect 來監聽生命週期變化
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                // 當畫面恢復時重新檢查權限狀態
                isServiceEnabled = isNotificationServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 權限狀態卡片
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
        
        // 權限設定按鈕
        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                context.startActivity(intent)
            }
        ) {
            Text("開啟通知存取權限")
        }

        // 功能按鈕區
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 第一行：顯示原始通知 和 顯示 JSON
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        showRawNotificationDialog = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("顯示原始通知")
                }

                Button(
                    onClick = {
                        showJsonDialog = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("顯示 JSON")
                }
            }

            // 第二行：開啟 Google Maps（置中）
            Button(
                onClick = {
                    viewModel.openGoogleMaps(context)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("開啟 Google Maps")
            }
        }

        // 導航資訊顯示
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

        // 未知哈希值列表
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
                                    onClick = {
                                        viewModel.copyHashToClipboard(context, hash)
                                    }
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

    // JSON 對話框
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
                Button(
                    onClick = {
                        viewModel.copyJsonToClipboard(context)
                    }
                ) {
                    Text("複製")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showJsonDialog = false }
                ) {
                    Text("關閉")
                }
            }
        )
    }

    // 原始通知對話框
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
                Button(
                    onClick = {
                        viewModel.copyRawNotificationToClipboard(context)
                    }
                ) {
                    Text("複製")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showRawNotificationDialog = false }
                ) {
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

// 檢查通知監聽服務是否已啟用
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