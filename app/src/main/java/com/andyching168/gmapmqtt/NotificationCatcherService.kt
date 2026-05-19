package com.andyching168.gmapmqtt

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.graphics.drawable.Icon
import android.graphics.drawable.BitmapDrawable
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class NotificationCatcherService : NotificationListenerService(), SensorEventListener {
    private lateinit var viewModel: NavigationViewModel
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private var sensorManager: SensorManager? = null
    private var rotationVectorSensor: Sensor? = null
    private var lastLocation: Location? = null
    private var currentBearing: Float? = null
    private var lastGpsPublishTime = 0L
    private val channelId = "gmapmqtt_navigation_channel"
    private val notificationId = 1001
    private val exitAction = "com.andyching168.gmapmqtt.EXIT_APP"
    private var isExiting = false

    companion object {
        const val EXIT_APP_ACTION = "com.andyching168.gmapmqtt.EXIT_APP"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("GmapMQTT", "Service onCreate - 服務已創建")
        viewModel = GmapMQTTApp.getInstance().getNavigationViewModel()
        // 初始化時設置為沒有通知
        viewModel.updateNavigationInfo(NavigationInfo(hasNotification = false))
        createNotificationChannel()
        startNavigationForeground()
        startLocationTracking()
        startHeadingTracking()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == exitAction) {
            exitApp()
            return START_NOT_STICKY
        }

        if (::viewModel.isInitialized) {
            startNavigationForeground()
            startLocationTracking()
            startHeadingTracking()
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "導航監聽服務",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "用於保持 Google Maps 導航監聽與 MQTT 推送"
                setShowBadge(false)
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startNavigationForeground(includeLocation: Boolean = false) {
        val contentIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val exitIntent = Intent(this, NotificationCatcherService::class.java).apply {
            action = exitAction
        }
        val exitPendingIntent = PendingIntent.getService(
            this,
            1,
            exitIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("GmapMQTT 導航同步")
            .setContentText("正在監聽 Google Maps 導航通知")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "結束APP", exitPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val foregroundTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                if (includeLocation) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            ServiceCompat.startForeground(this, notificationId, notification, foregroundTypes)
        } else {
            startForeground(notificationId, notification)
        }
        Log.d("GmapMQTT", "前台服務已啟動")
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun startLocationTracking() {
        if (!hasLocationPermission()) {
            Log.w("GmapMQTT", "缺少定位權限，暫不啟動 GPS/MQTT 位置更新")
            return
        }
        if (locationListener != null) return

        var lastKnownLocation: Location? = null
        try {
            startNavigationForeground(includeLocation = true)
            val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationManager = manager
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    lastLocation = location
                    val bearing = if (location.hasBearing()) location.bearing else currentBearing
                    publishGpsInfo(location, bearing, force = true)
                }

                override fun onProviderDisabled(provider: String) {
                    Log.w("GmapMQTT", "定位 provider 已停用: $provider")
                }

                override fun onProviderEnabled(provider: String) {
                    Log.d("GmapMQTT", "定位 provider 已啟用: $provider")
                }

                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            }
            locationListener = listener

            val minTimeMs = 1_000L
            val minDistanceM = 1f
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTimeMs, minDistanceM, listener)
            }
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minTimeMs, minDistanceM, listener)
            }

            lastKnownLocation = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: SecurityException) {
            Log.e("GmapMQTT", "啟動定位更新失敗，權限不足", e)
        } catch (e: Exception) {
            Log.e("GmapMQTT", "啟動定位更新失敗", e)
        }

        lastKnownLocation?.let { location ->
            lastLocation = location
            publishGpsInfo(location, if (location.hasBearing()) location.bearing else currentBearing, force = true)
        }
    }

    private fun stopLocationTracking() {
        val listener = locationListener ?: return
        try {
            locationManager?.removeUpdates(listener)
        } catch (e: Exception) {
            Log.e("GmapMQTT", "停止定位更新失敗", e)
        }
        locationListener = null
    }

    private fun startHeadingTracking() {
        if (rotationVectorSensor != null) return
        val manager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager = manager
        rotationVectorSensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val sensor = rotationVectorSensor
        if (sensor == null) {
            Log.w("GmapMQTT", "找不到 Rotation Vector 感測器，bearing 將只使用 GPS")
            return
        }
        manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    private fun stopHeadingTracking() {
        sensorManager?.unregisterListener(this)
        rotationVectorSensor = null
    }

    private fun publishGpsInfo(location: Location, bearing: Float?, force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastGpsPublishTime < 2_000L) return
        lastGpsPublishTime = now

        val speedKmh = if (location.hasSpeed()) location.speed * 3.6f else null
        viewModel.updateGpsInfo(
            latitude = location.latitude,
            longitude = location.longitude,
            speedKmh = speedKmh,
            bearing = bearing
        )
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientation)
        val azimuthDegrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
        currentBearing = (azimuthDegrees + 360f) % 360f

        lastLocation?.let { location ->
            publishGpsInfo(location, currentBearing, force = false)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun exitApp() {
        try {
            isExiting = true
            viewModel.publishEmptyNavigationInfo()
            Log.d("GmapMQTT", "已發布清除導航資訊")

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()

            val exitIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                action = EXIT_APP_ACTION
            }
            startActivity(exitIntent)
            Log.d("GmapMQTT", "已發送結束應用指令")
        } catch (e: Exception) {
            Log.e("GmapMQTT", "結束應用時出錯", e)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("GmapMQTT", "Service onListenerConnected - 服務已連接到系統")

        // 服務連接時，檢查是否有現存的 Google Maps 通知
        try {
            val activeNotifications = activeNotifications
            Log.d("GmapMQTT", "當前活躍通知數量: ${activeNotifications?.size ?: 0}")

            if (activeNotifications != null) {
                var foundGoogleMaps = false
                for (sbn in activeNotifications) {
                    Log.d("GmapMQTT", "檢查通知: ${sbn.packageName}")
                    if (sbn.packageName == "com.google.android.apps.maps") {
                        Log.d("GmapMQTT", "✓ 發現現存的 Google Maps 通知，立即處理")
                        foundGoogleMaps = true
                        onNotificationPosted(sbn)
                        break
                    }
                }
                if (!foundGoogleMaps) {
                    Log.d("GmapMQTT", "未發現 Google Maps 通知")
                }
            } else {
                Log.d("GmapMQTT", "activeNotifications 為 null")
            }
        } catch (e: Exception) {
            Log.e("GmapMQTT", "檢查現存通知時出錯", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d("GmapMQTT", "Service onListenerDisconnected - 服務已斷開連接")
        // 服務斷線時，清空導航資訊
        if (::viewModel.isInitialized) {
            viewModel.publishEmptyNavigationInfo()
        }

        // 嘗試重新連接
        if (!isExiting) {
            requestRebind(android.content.ComponentName(this, NotificationCatcherService::class.java))
        }
    }

    private fun simpleIconHash(bitmap: Bitmap): String {
        val resized = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
        val grayscale = IntArray(32 * 32)
        resized.getPixels(grayscale, 0, 32, 0, 0, 32, 32)

        // 區域亮度平均值
        val parts = 4
        val avgByRegion = Array(parts * parts) { 0 }
        for (y in 0 until 32) {
            for (x in 0 until 32) {
                val gray = Color.red(grayscale[y * 32 + x]) // 灰階代表亮度即可
                val regionX = x / (32 / parts)
                val regionY = y / (32 / parts)
                val index = regionY * parts + regionX
                avgByRegion[index] += gray
            }
        }

        return avgByRegion.joinToString("-") { (it / ((32 / parts) * (32 / parts))).toString() }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        val packageName = sbn.packageName
        val notification = sbn.notification
        val extras = notification.extras

        if (packageName == "com.google.android.apps.maps") {
            // 記錄原始通知內容
            val rawNotification = """
                Package: $packageName
                Title: ${extras.get("android.title")}
                Direction: ${extras.get("android.text")}
                SubText: ${extras.get("android.subText")}
                Icon: ${extras.get("android.largeIcon")}
                Template: ${extras.get("android.template")}
                ShortCriticalText: ${extras.get("android.shortCriticalText")}
                Extras: ${extras.keySet().joinToString("\n") { key ->
                    "$key: ${extras.get(key)}"
                }}
            """.trimIndent()

            viewModel.setLastRawNotification(rawNotification)
            Log.d("GmapMQTT", "原始通知內容:\n$rawNotification")

            // 處理圖標資訊
            val icon = extras.get("android.largeIcon") as? Icon
            try {
                val drawable = icon?.loadDrawable(this)
                if (drawable is BitmapDrawable) {
                    val bitmap = drawable.bitmap
                    val hash = simpleIconHash(bitmap)

                    Log.d("GmapMQTT", """
                        圖標信息:
                        寬度: ${bitmap.width}
                        高度: ${bitmap.height}
                        區域亮度哈希值: $hash
                    """.trimIndent())

                    // 更新 ViewModel 中的哈希值
                    viewModel.setLastIconHash(hash, bitmap)
                }
            } catch (e: Exception) {
                Log.e("GmapMQTT", "獲取圖標信息時出錯", e)
            }

            // 判斷通知類型（Android 16 的 ProgressStyle 或舊版通知）
            val isProgressStyle = extras.get("android.template")?.toString()
                ?.contains("ProgressStyle") == true

            val info = if (isProgressStyle) {
                // Android 16+ ProgressStyle 通知
                parseProgressStyleNotification(extras)
            } else {
                // Android 15 及以下的傳統通知
                val title = extras.get("android.title")?.toString() ?: ""
                val direction = extras.get("android.text")?.toString() ?: ""
                val subText = extras.get("android.subText")?.toString() ?: ""
                parseTraditionalNotification(title, direction, subText)
            }

            Log.d("GmapMQTT", "解析後的資訊 (${if (isProgressStyle) "ProgressStyle" else "Traditional"}): $info")
            viewModel.updateNavigationInfo(info.copy(hasNotification = true))
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        if (sbn.packageName == "com.google.android.apps.maps") {
            // 當 Google Maps 通知被移除時，設置為沒有通知
            viewModel.publishEmptyNavigationInfo()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationTracking()
        stopHeadingTracking()
        Log.d("GmapMQTT", "服務已銷毀")
    }

    private fun parseProgressStyleNotification(extras: android.os.Bundle): NavigationInfo {
        // Android 16+ ProgressStyle 通知解析
        // Title 格式可能有兩種：
        // 1. "450 公尺 · 走中間車道靠右行駛，繼續走中正路/新北市特一號道路/106甲縣道"
        // 2. "往北走中正路/新北市特一號道路/106甲縣道朝建二路前進" (沒有距離)
        val fullTitle = extras.get("android.title")?.toString() ?: ""
        val subText = extras.get("android.subText")?.toString() ?: "" // 例如: "下午3:48 抵達"
        val shortCriticalText = extras.get("android.shortCriticalText")?.toString() ?: ""
        
        // progress 相關資訊（用於計算總距離，不是轉彎距離）
        val progress = extras.getInt("android.progress", 0)
        val progressMax = extras.getInt("android.progressMax", 0)

        Log.d("GmapMQTT", "ProgressStyle - fullTitle: $fullTitle, subText: $subText, shortCriticalText: $shortCriticalText, progress: $progress/$progressMax")

        var turnDistance = ""
        var totalDistance = ""
        var direction = ""
        var eta = ""

        // 優先使用 shortCriticalText 作為轉彎距離（如果有的話）
        if (shortCriticalText.isNotBlank()) {
            turnDistance = shortCriticalText
        }

        // 解析 title
        if (fullTitle.contains("·")) {
            // 格式 1: "距離 · 方向指示"
            val parts = fullTitle.split("·", limit = 2)
            if (turnDistance.isBlank()) {
                turnDistance = parts[0].trim()
            }
            if (parts.size > 1) {
                direction = parts[1].trim()
            }
        } else {
            // 格式 2: 只有方向指示，沒有距離
            // 嘗試從開頭提取距離（如果有的話）
            val distanceMatch = Regex("^(\\d+\\s*[公尺|公里])").find(fullTitle)
            if (distanceMatch != null) {
                if (turnDistance.isBlank()) {
                    turnDistance = distanceMatch.value.trim()
                }
                direction = fullTitle.substring(distanceMatch.range.last + 1).trim()
            } else {
                // 完全沒有距離資訊，整個 title 都是方向
                direction = fullTitle
                // turnDistance 保持為空
            }
        }

        // 從 progress 計算總距離（不是轉彎距離）
        if (progressMax > 0) {
            val remainingMeters = progressMax - progress
            totalDistance = if (remainingMeters >= 1000) {
                String.format("%.1f 公里", remainingMeters / 1000.0)
            } else {
                "$remainingMeters 公尺"
            }
            Log.d("GmapMQTT", "從 progress 計算總距離: $totalDistance (剩餘: $remainingMeters 公尺)")
        }

        // 從 subText 解析 ETA (格式: "下午3:48 抵達" 或 "15:48 抵達")
        if (subText.contains("抵達")) {
            eta = subText.replace("抵達", "").trim()
        }

        // 簡化方向指示：將 "XX走AA朝BB前進" 格式簡化為 "前往BB"
        direction = simplifyDirection(direction)

        // 從 ETA 計算剩餘時間（Android 16 沒有提供 duration）
        var duration = ""
        if (eta.isNotBlank()) {
            duration = calculateDurationFromETA(eta)
        }

        // 獲取當前導航信息，保留已設置的 turnDirection
        val currentInfo = viewModel.navigationInfo.value
        val info = NavigationInfo(
            direction = direction,
            totalDistance = totalDistance,
            turnDistance = turnDistance, // 如果沒有就是空字串
            duration = duration, // 從 ETA 計算出來的剩餘時間
            eta = eta,
            status = "導航中",
            turnDirection = currentInfo.turnDirection,
            hasNotification = true
        )

        Log.d("GmapMQTT", "ProgressStyle 解析結果: $info")
        return info
    }

    private fun parseTraditionalNotification(title: String, direction: String, subText: String): NavigationInfo {
        // Android 15 及以下的傳統通知解析
        var totalDistance = ""
        val turnDistance = title  // 轉彎距離直接使用 title
        var duration = ""
        var eta = ""

        // 從 subText 中解析所有資訊 (格式: "21 分鐘 · 18 公里 · 預計到達時間：15:52")
        subText.split("·").forEach { part ->
            when {
                part.contains("公尺") -> {
                    val meters = part.trim().replace("公尺", "").trim()
                    totalDistance = if ((meters.toIntOrNull() ?: 0) >= 1000) {
                        "${(meters.toIntOrNull() ?: 0) / 1000.0} 公里"
                    } else {
                        "$meters 公尺"
                    }
                }
                part.contains("公里") -> totalDistance = part.trim()
                part.contains("分鐘") -> duration = part.trim()
                part.contains("預計到達時間") -> {
                    // 移除「預計到達時間：」前綴，只保留時間
                    eta = part.trim().replace("預計到達時間：", "").replace("預計到達時間", "").trim()
                }
            }
        }

        // 獲取當前導航信息，保留已設置的 turnDirection
        val currentInfo = viewModel.navigationInfo.value
        val info = NavigationInfo(
            direction = direction,
            totalDistance = totalDistance,
            turnDistance = turnDistance,
            duration = duration,
            eta = eta,
            status = "導航中",
            turnDirection = currentInfo.turnDirection,
            hasNotification = true
        )

        Log.d("GmapMQTT", "Traditional 解析結果: $info")
        return info
    }

    private fun simplifyDirection(direction: String): String {
        // 簡化方向指示
        // 格式1: "往北走中正路/新北市特一號道路/106甲縣道朝建二路前進" -> "前往建二路"
        // 格式2: "走中間車道靠右行駛，繼續走中正路/新北市特一號道路/106甲縣道" -> "走中間車道靠右行駛"
        // 格式3: "走左側車道繼續沿著思源路/新北市特一號道路/106甲縣道走" -> "走左側車道"
        // 格式4: "經過位於右側的ŠKODA 新莊展示中心後向右轉 行駛中正路/台1甲線" -> "經過ŠKODA 新莊展示中心後向右轉"
        
        if (direction.isBlank()) return direction
        
        // 優先檢查 "朝XX前進/行駛" 模式
        val destinationMatch = Regex("朝([^前行]+)(?:前進|行駛)").find(direction)
        if (destinationMatch != null) {
            val destination = destinationMatch.groupValues[1].trim()
            return "前往$destination"
        }
        
        // 檢查 "經過位於X側的XX後..." 模式，簡化為 "經過XX後..."
        if (direction.contains("經過位於")) {
            var simplified = direction.replace(Regex("位於[左右上下前後]側的"), "")
            // 移除 "行駛XX路/XX線" 部分
            simplified = simplified.replace(Regex("\\s*行駛[^\\s]+"), "").trim()
            return simplified
        }
        
        // 檢查 "繼續沿著XX走" 模式，保留前面的動作指示
        if (direction.contains("繼續沿著")) {
            val beforeContinue = direction.split(Regex("繼續沿著"))[0].trim()
            if (beforeContinue.isNotBlank()) {
                return beforeContinue
            }
        }
        
        // 檢查 "，繼續走" 模式，保留前面的動作指示
        if (direction.contains("，繼續走") || direction.contains(",繼續走")) {
            val simplified = direction.split(Regex("[，,]繼續走"))[0].trim()
            return simplified
        }
        
        // 如果沒有匹配到，保持原樣
        return direction
    }

    private fun calculateDurationFromETA(eta: String): String {
        // 從 ETA 計算剩餘時間
        // ETA 格式可能是: "下午3:48", "15:48", "上午10:30" 等
        
        try {
            val now = Calendar.getInstance()
            val etaCalendar = Calendar.getInstance()
            
            // 解析 ETA 時間
            var hour = 0
            var minute = 0
            
            when {
                eta.contains("下午") -> {
                    // 格式: "下午3:48"
                    val timeStr = eta.replace("下午", "").trim()
                    val parts = timeStr.split(":")
                    hour = parts[0].toInt()
                    if (hour != 12) hour += 12 // 下午需要加12小時，但12點除外
                    minute = parts[1].toInt()
                }
                eta.contains("上午") -> {
                    // 格式: "上午10:30"
                    val timeStr = eta.replace("上午", "").trim()
                    val parts = timeStr.split(":")
                    hour = parts[0].toInt()
                    if (hour == 12) hour = 0 // 上午12點是00:00
                    minute = parts[1].toInt()
                }
                eta.contains(":") -> {
                    // 格式: "15:48" (24小時制)
                    val parts = eta.split(":")
                    hour = parts[0].toInt()
                    minute = parts[1].toInt()
                }
                else -> {
                    return "" // 無法解析
                }
            }
            
            // 設定 ETA 時間（同一天）
            etaCalendar.set(Calendar.HOUR_OF_DAY, hour)
            etaCalendar.set(Calendar.MINUTE, minute)
            etaCalendar.set(Calendar.SECOND, 0)
            
            // 如果 ETA 時間比現在早，表示是明天
            if (etaCalendar.before(now)) {
                etaCalendar.add(Calendar.DAY_OF_MONTH, 1)
            }
            
            // 計算時間差（分鐘）
            val diffMillis = etaCalendar.timeInMillis - now.timeInMillis
            val diffMinutes = (diffMillis / 1000 / 60).toInt()
            
            return if (diffMinutes > 0) {
                "$diffMinutes 分鐘"
            } else {
                "" // 已經到達或時間異常
            }
            
        } catch (e: Exception) {
            Log.e("GmapMQTT", "計算剩餘時間時出錯: eta=$eta", e)
            return ""
        }
    }
}
