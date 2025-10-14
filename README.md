# GmapNavNotiCatcher with USB Serial

![image](https://github.com/andyching168/GmapNavNotiCatcher/blob/main/Screenshot_20250430_191045.png)

一個用於捕獲 Google Maps 導航通知並透過 USB Serial 傳送到 ESP32 的 Android 應用程式。這個專案展示了如何通過 Android 的通知監聽服務來獲取 Google Maps 的導航信息，並通過圖標哈希值識別導航方向，最後將資料透過 USB Serial 傳送給外部裝置。

## 功能特點

- 實時監聽 Google Maps 的導航通知
- 解析導航信息，包括：
  - 當前道路名稱
  - 剩餘總距離
  - 轉彎距離
  - 轉彎方向
  - 預計到達時間
- 使用區域亮度哈希算法識別導航方向圖標
- **透過 USB Serial 傳送導航資訊到 ESP32**
- **自動掃描和連接 USB Serial 裝置**
- **支援自動連線和資料自動發送**
- 自動收集未知的導航圖標哈希值
- 支持複製哈希值以便後續分析
- 支持多種導航方向識別：
  - 基本方向：左轉、右轉、直行
  - 特殊直行：直行(接到下一個路)
  - 靠左/靠右行駛
  - 分岔路（靠左/靠右）
  - 下交流道
  - 急轉彎
  - 圓環相關：
    - 圓環
    - 駛出圓環(4分之1)
    - 駛出圓環(2分之1)
  - 迴轉
  - 目的地相關：
    - 目的地在左方
    - 目的地在右方
    - 目的地在前方

## USB Serial 通訊功能

### 主要特點

✅ **USB 裝置管理**：自動掃描、選擇、連線 USB Serial 裝置
✅ **本地儲存設定**：所有設定自動保存，重啟 App 後保留
✅ **自動連線**：可選擇 App 啟動時自動連線
✅ **自動發送**：導航資訊變動時自動發送 JSON 到 ESP32
✅ **連線狀態顯示**：即時顯示 USB 連線狀態
✅ **錯誤處理**：顯示連線錯誤訊息
✅ **權限管理**：自動處理 USB 權限請求

### 支援的 USB Serial 晶片

- CP2102 (Silicon Labs)
- CH340 (WCH)
- FT232R (FTDI)
- 其他 usb-serial-for-android 支援的晶片

### JSON 資料格式

應用程式會將導航資訊以 JSON 格式透過 USB Serial 發送：

\`\`\`json
{
    "turnDirection": "left",
    "direction": "向左轉",
    "turnDistance": "在 500 公尺後",
    "iconData": "..."
}
\`\`\`

每條資料以換行符號 (\n) 結尾，方便 ESP32 解析。

### ESP32 接收範例

\`\`\`cpp
#include <ArduinoJson.h>

void setup() {
  Serial.begin(115200);
  Serial.println("ESP32 準備接收導航資訊");
}

void loop() {
  if (Serial.available()) {
    String jsonString = Serial.readStringUntil('\\n');
    
    StaticJsonDocument<1024> doc;
    DeserializationError error = deserializeJson(doc, jsonString);
    
    if (!error) {
      const char* turnDirection = doc["turnDirection"];
      const char* direction = doc["direction"];
      const char* turnDistance = doc["turnDistance"];
      
      // 在這裡加入您的處理邏輯
      // 例如：顯示在螢幕上、控制 LED 等
    }
  }
}
\`\`\`

## 技術實現

### 核心組件

1. **NotificationCatcherService**
   - 繼承自 `NotificationListenerService`
   - 監聽 Google Maps 的通知
   - 解析通知內容
   - 計算圖標哈希值

2. **NavigationViewModel**
   - 管理導航狀態
   - 存儲已知的圖標哈希值對應表
   - 處理未知哈希值的收集

3. **圖標哈希算法**
   ```kotlin
   fun simpleIconHash(bitmap: Bitmap): String {
       val resized = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
       val grayscale = IntArray(32 * 32)
       resized.getPixels(grayscale, 0, 32, 0, 0, 32, 32)

       // 區域亮度平均值
       val parts = 4
       val avgByRegion = Array(parts * parts) { 0 }
       for (y in 0 until 32) {
           for (x in 0 until 32) {
               val gray = Color.red(grayscale[y * 32 + x])
               val regionX = x / (32 / parts)
               val regionY = y / (32 / parts)
               val index = regionY * parts + regionX
               avgByRegion[index] += gray
           }
       }

       return avgByRegion.joinToString("-") { (it / ((32 / parts) * (32 / parts))).toString() }
   }
   ```

### 已知的哈希值對應表

```kotlin
private val iconHashMap: Map<String, String> = mapOf(
    // 基本方向
    "3-59-0-0-123-151-99-71-11-71-0-95-0-0-0-47" to "left",   // 左轉
    "0-0-59-3-71-99-151-123-95-0-71-11-47-0-0-0" to "right",  // 右轉
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
    
    // 圓環
    "0-143-143-0-0-167-167-0-0-139-139-0-0-39-39-0" to "Roundabout", // 圓環
    "23-123-119-11-115-51-171-75-91-127-119-0-0-63-0-0" to "Exit1st", // 駛出圓環(4分之1)
    "0-59-79-0-0-131-159-3-39-131-147-3-0-0-39-15" to "Exit2nd", // 駛出圓環(2分之1)
    
    // 迴轉
    "0-51-115-15-11-115-23-107-75-203-51-95-0-15-0-47" to "UTurnLeft", // 迴轉（左）
    
    // 目的地
    "99-131-11-0-119-111-39-11-67-167-203-143-0-55-91-103" to "DestinationLeft" // 目的地在左方
)
```

## 使用說明

### 基本設置

1. 安裝應用程式
2. 授予通知存取權限

### USB Serial 設定

1. 使用 USB OTG 線將 ESP32 連接到 Android 裝置
2. 開啟 App，點擊「USB 設定」
3. 點擊「重新掃描裝置」找到您的 ESP32
4. 選擇裝置並設定鮑率（預設 115200）
5. 點擊「儲存並連線」
6. 確認連線狀態顯示「✓ 已連線」

### 開始導航

1. 開啟 Google Maps 並開始導航
2. 應用程式會自動捕獲導航資訊
3. 導航資訊會自動透過 USB Serial 發送到 ESP32
4. 可在 App 中查看：
   - 當前導航信息
   - USB 連線狀態
   - 未知的導航圖標哈希值（如果有）

詳細的 USB Serial 使用指南請參閱 [USB_SERIAL_GUIDE.md](USB_SERIAL_GUIDE.md)。

## 開發指南

### 添加新的哈希值

1. 在 `NavigationViewModel` 中的 `iconHashMap` 添加新的哈希值對應
2. 格式為：`"哈希值" to "方向描述"`

### 修改哈希算法

1. 在 `NotificationCatcherService` 中修改 `simpleIconHash` 方法
2. 確保新的算法能產生穩定的哈希值

### 擴展功能

- 可以添加更多導航信息的解析
- 可以實現哈希值的自動學習
- 可以添加導航歷史記錄
- 可以實現導航狀態的持久化存儲

## 注意事項

- 需要 Android 通知存取權限
- 需要 USB OTG 支援
- 只支持 Google Maps 的導航通知
- App 和 ESP32 的鮑率必須一致（預設 115200）
- 哈希值對應表可能需要根據 Google Maps 的更新進行調整
- 不同裝置的 DPI 和顯示效果可能略有不同，系統已內建容錯機制

## 相關文件

- [USB Serial 使用指南](USB_SERIAL_GUIDE.md) - 詳細的使用說明和 ESP32 範例
- [USB Serial 改造總結](USB_SERIAL_MIGRATION_SUMMARY.md) - 從 MQTT 到 USB Serial 的改造記錄
- [ESP32 測試指南](ESP32_TESTING_GUIDE.md) - ESP32 WiFi Portal 測試程式使用說明
- [Android U+ 修復說明](ANDROID_U_FIX.md) - Android 14+ 相容性修復

## ESP32 測試程式

專案包含兩個 ESP32 測試程式：

### 1. test_esp32.ino（完整版）
- ✨ 美觀的網頁介面
- 📊 即時顯示導航資訊
- 🔄 自動刷新（每 3 秒）
- 📡 系統資訊顯示
- 🎨 方向圖示支援
- 📱 響應式設計

### 2. test_esp32_simple.ino（簡化版）
- 🎯 極簡設計
- ⚡ 快速啟動
- 📱 基本導航顯示
- 🔄 自動刷新（每 2 秒）

兩個版本都會：
- 建立無密碼 WiFi 熱點
- 透過 USB Serial 接收資料
- 在網頁上顯示導航資訊

詳細使用說明請參閱 [ESP32_TESTING_GUIDE.md](ESP32_TESTING_GUIDE.md)

## 貢獻

歡迎提交 Pull Request 或 Issue 來改進這個專案。

## 授權

MIT License 
