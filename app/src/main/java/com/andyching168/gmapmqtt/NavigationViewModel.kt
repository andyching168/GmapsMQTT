package com.andyching168.gmapmqtt

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class NavigationViewModel : ViewModel() {
    private val _navigationInfo = MutableStateFlow(NavigationInfo())
    val navigationInfo: StateFlow<NavigationInfo> = _navigationInfo.asStateFlow()

    private val _unknownHashes = MutableStateFlow<List<Triple<String, String, Bitmap?>>>(emptyList())
    val unknownHashes: StateFlow<List<Triple<String, String, Bitmap?>>> = _unknownHashes.asStateFlow()

    private var lastRawNotification: String = ""
    private var lastIconHash: String = ""
    private var lastUnknownHash: String = ""

    // 哈希值對應表
    private val iconHashMap: Map<String, String> = mapOf(
        // 基本方向
        "3-59-0-0-123-151-99-71-11-71-0-95-0-0-0-47" to "left",   // 左轉
        "0-63-0-0-127-175-127-71-11-79-0-127-0-0-0-63" to "left",
        "0-0-59-3-71-99-151-123-95-0-71-11-47-0-0-0" to "right",  // 右轉
        "0-0-63-3-71-127-175-127-127-0-79-11-63-0-0-0" to "right",
        "0-39-39-0-0-175-175-0-0-55-55-0-0-23-23-0" to "straight", // 直行
        "0-39-39-0-0-175-175-0-0-63-63-0-0-31-31-0" to "straight", // 直行
        "0-39-39-0-0-175-175-0-0-139-135-0-7-55-55-3" to "GoStraight", // 直行(接到下一個路）

        // 靠左/靠右
        "0-119-47-0-0-191-79-0-0-0-111-23-0-0-31-31" to "side_left", // 靠左
        "0-47-119-0-0-79-191-0-23-111-0-0-31-31-0-0" to "side_right", // 靠右

        // 分岔路
        "0-23-111-47-43-127-155-95-0-139-11-0-0-63-0-0" to "ForkRight", // 分岔路（靠右）
        "47-111-11-3-95-171-131-43-0-11-139-0-0-0-63-0" to "ForkLeft", // 分岔路（靠左）

        // 下交流道
        "47-15-99-15-95-139-171-103-95-75-43-3-47-15-0-0" to "ExitRight", // 下交流道(右)

        //急轉
        "0-0-51-19-47-79-91-103-119-171-0-95-0-0-0-47" to "SharpTurnLeft", // 向左後急轉
        "19-51-0-0-103-91-79-47-95-0-171-119-47-0-0-0" to "SharpTurnRight", // 向右後急轉

        // 圓環
        "0-143-143-0-0-167-167-0-0-139-139-0-0-39-39-0" to "Roundabout", // 圓環
        "23-123-119-11-115-51-171-75-91-127-119-0-0-63-0-0" to "Exit1st", // 駛出圓環(4分之1)
        "0-59-79-0-0-131-159-3-39-131-147-3-0-0-39-15" to "Exit2nd", // 駛出圓環(2分之1)

        // 迴轉
        "0-51-115-15-11-115-23-107-75-203-51-95-0-15-0-47" to "UTurnLeft", // 迴轉（左）

        // 目的地
        "99-131-11-0-119-111-39-11-67-167-203-143-0-55-91-103" to "DestinationLeft", // 目的地在左方
        "0-11-131-99-11-39-115-119-143-203-167-67-103-91-55-0" to "DestinationRight", // 目的地在右方
        "7-103-103-7-87-71-75-83-35-87-83-35-0-71-71-0" to "DestinationFront", // 目的地在前方

        //其他
        "0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0" to "Blank" // 空白
    )

    // 容錯值設定
    private val TOLERANCE = 30

    // 比較兩個哈希值是否在容錯範圍內
    private fun isHashSimilar(hash1: String, hash2: String): Boolean {
        val parts1 = hash1.split("-").map { it.toInt() }
        val parts2 = hash2.split("-").map { it.toInt() }

        if (parts1.size != parts2.size) return false

        val differences = parts1.zip(parts2).map { (p1, p2) -> Math.abs(p1 - p2) }
        val maxDiff = differences.maxOrNull() ?: 0

        // 記錄最大差異，方便調試
        if (maxDiff > 20) {
            Log.d("GmapMQTT", """
                哈希值比較:
                原始: $hash1
                當前: $hash2
                最大差異: $maxDiff
                差異分布: ${differences.joinToString(", ")}
            """.trimIndent())
        }

        return differences.all { it <= TOLERANCE }
    }

    // 根據哈希值獲取方向（帶容錯）
    private fun getDirectionWithTolerance(hash: String): String? {
        val direction = iconHashMap.entries.find { isHashSimilar(it.key, hash) }?.value
        if (direction != null) {
            Log.d("GmapMQTT", "成功匹配方向: $direction")
        } else {
            Log.d("GmapMQTT", "未找到匹配的方向，當前哈希值: $hash")
        }
        return direction
    }

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun updateNavigationInfo(info: NavigationInfo) {
        _navigationInfo.value = info
    }

    fun setLastRawNotification(raw: String) {
        lastRawNotification = raw
    }

    fun getLastRawNotification(): String {
        return lastRawNotification.ifEmpty { "目前沒有原始通知內容" }
    }

    fun copyRawNotificationToClipboard(context: Context) {
        if (lastRawNotification.isNotEmpty()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Raw Notification", lastRawNotification)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "已複製到剪貼簿", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "沒有可複製的內容", Toast.LENGTH_SHORT).show()
        }
    }

    fun setLastIconHash(hash: String, bitmap: Bitmap? = null) {
        lastIconHash = hash
        val direction = getDirectionWithTolerance(hash)
        if (direction != null) {
            val currentInfo = _navigationInfo.value
            _navigationInfo.value = currentInfo.copy(turnDirection = direction)
        } else {
            // 只在哈希值變化時添加
            if (lastUnknownHash != hash) {
                val currentList = _unknownHashes.value.toMutableList()
                val timestamp = dateFormat.format(Date())
                currentList.add(Triple(timestamp, hash, bitmap))
                _unknownHashes.value = currentList
                lastUnknownHash = hash
            }
        }
    }

    fun copyHashToClipboard(context: Context, hash: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Hash Value", hash)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "已複製到剪貼簿", Toast.LENGTH_SHORT).show()
    }

    fun getLastTurnDirection(): String {
        return iconHashMap[lastIconHash] ?: ""
    }

    fun openGoogleMaps(context: Context) {
        try {
            // 直接打開 Google Maps 主畫面（不啟動導航）
            val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.maps")
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                // 如果 Google Maps 未安裝，打開 Play Store
                val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.maps"))
                context.startActivity(playStoreIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "無法開啟 Google Maps", Toast.LENGTH_SHORT).show()
            Log.e("GmapMQTT", "開啟 Google Maps 失敗", e)
        }
    }

    fun generateNavigationJson(): String {
        val json = JSONObject().apply {
            put("turnDirection", _navigationInfo.value.turnDirection)
            put("direction", _navigationInfo.value.direction)
            put("turnDistance", _navigationInfo.value.turnDistance)
        }
        return json.toString(4) // 使用 4 個空格進行格式化
    }

    fun copyJsonToClipboard(context: Context) {
        val jsonString = generateNavigationJson()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Navigation Info", jsonString)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "已複製到剪貼簿", Toast.LENGTH_SHORT).show()
    }
}
