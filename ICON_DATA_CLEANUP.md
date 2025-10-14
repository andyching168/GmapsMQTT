# 圖標數據清除機制說明

## 概述
系統現在會自動管理 JSON 中的 `iconData` 欄位，確保在沒有導航通知時正確清除圖標數據。

## 工作流程

### 情況 1：有導航通知時
```kotlin
// NotificationCatcherService 接收到通知
val info = NavigationInfo(
    direction = "忠孝西路一段",
    turnDistance = "100 公尺",
    turnDirection = "left",  // 有轉向資訊
    hasNotification = true
)

// 同時處理圖標
viewModel.setLastIconHash(hash, bitmap)
// -> lastProcessedIcon = "00640110011E..."

viewModel.updateNavigationInfo(info)
```

**JSON 輸出：**
```json
{
    "turnDirection": "left",
    "direction": "忠孝西路一段",
    "turnDistance": "100 公尺",
    "iconData": "00640110011E021C030A..."
}
```

### 情況 2：導航結束/通知取消時
```kotlin
// NotificationCatcherService 偵測到通知取消
viewModel.updateNavigationInfo(NavigationInfo(hasNotification = false))
// hasNotification = false
// -> 自動清除 lastProcessedIcon = ""
```

**JSON 輸出：**
```json
{
    "turnDirection": "",
    "direction": "",
    "turnDistance": "",
    "iconData": ""
}
```
注意：`iconData` 欄位保持存在，但值為空字串，避免解析工具因欄位消失而出錯

## 實現細節

### updateNavigationInfo 函數
```kotlin
fun updateNavigationInfo(info: NavigationInfo) {
    _navigationInfo.value = info
    
    // 如果沒有導航通知（導航結束），清空圖標數據
    if (!info.hasNotification) {
        lastProcessedIcon = ""
        Log.d("GmapMQTT", "導航已結束，清除圖標數據")
    }
    
    // 當導航資訊更新時，自動推送到 MQTT
    publishNavigationInfo()
}
```

### generateNavigationJson 函數
```kotlin
fun generateNavigationJson(): String {
    val json = JSONObject().apply {
        put("turnDirection", _navigationInfo.value.turnDirection)
        put("direction", _navigationInfo.value.direction)
        put("turnDistance", _navigationInfo.value.turnDistance)
        // 始終包含 iconData 欄位，避免解析工具因欄位消失而出錯
        put("iconData", lastProcessedIcon)
    }
    return json.toString(4)
}
```

## 觸發清除的場景

以下情況會自動清除圖標數據：

1. **導航結束**
   - NotificationCatcherService 偵測到導航通知被移除
   - 調用 `updateNavigationInfo(NavigationInfo(hasNotification = false))`

2. **服務解綁**
   - 當 NotificationCatcherService 解除綁定時
   - 調用 `updateNavigationInfo(NavigationInfo(hasNotification = false))`

3. **手動清除**
   - 任何時候調用 `updateNavigationInfo()` 時傳入 `hasNotification = false`

## 重要說明

**為什麼使用 `hasNotification` 而不是 `turnDirection`？**

- 在導航過程中，即使長距離直行沒有轉向指示，`turnDirection` 可能為空，但這時候**不應該**清除圖標數據
- 只有當導航真正結束（`hasNotification = false`）時，才應該清除所有相關數據
- 這樣可以確保圖標數據在整個導航過程中保持一致

## 日誌輸出

### 有圖標數據時
```
D/GmapMQTT: 圖標處理成功，壓縮字串長度: 256
D/GmapMQTT: 成功匹配方向: left
```

### 清除圖標數據時
```
D/GmapMQTT: 導航已結束，清除圖標數據
```

## 優勢

1. **自動管理**：不需要手動清除圖標數據，系統會自動處理
2. **數據一致性**：圖標數據與導航狀態保持同步
3. **欄位穩定性**：`iconData` 欄位始終存在，避免解析工具因欄位消失而出錯
4. **清晰的狀態**：通過 `iconData` 是否為空字串可以明確知道當前是否有圖標數據

## 測試方法

### 測試 1：正常導航流程
1. 啟動 Google Maps 並開始導航
2. 觀察 JSON 輸出，`iconData` 欄位應有壓縮數據
3. 結束導航
4. 觀察 JSON 輸出，`iconData` 欄位應為空字串 `""`

### 測試 2：快速切換
1. 開始導航
2. 快速結束再開始
3. 確認 `iconData` 正確更新和清除（但欄位始終存在）

### 測試 3：日誌驗證
1. 開啟 Logcat 並過濾 "GmapMQTT"
2. 執行導航操作
3. 確認看到 "導航資訊已清空，同時清除圖標數據" 訊息
