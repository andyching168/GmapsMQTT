# USB Serial 功能使用指南

## 功能概述

此應用程式已改造為使用 USB Serial 通訊，可以將 Google Maps 導航資訊透過 USB 傳送到 ESP32 或其他支援 Serial 的裝置。

## 主要功能

1. **USB Serial 連線設定**：可選擇 USB 裝置、配置鮑率等參數
2. **自動連線**：啟動 App 時可自動連線到上次使用的 USB 裝置
3. **自動發送**：當導航資訊變動時，自動發送 JSON 格式的資料
4. **本地儲存**：所有設定都會自動保存到本地

## 使用步驟

### 1. 連接 USB 裝置

- 使用 USB OTG 線將 ESP32 連接到 Android 裝置
- 確保 ESP32 的 USB Serial 驅動晶片被支援（如 CP2102、CH340、FT232 等）

### 2. 開啟 USB 設定

在主畫面點擊「USB 設定」按鈕。

### 3. 選擇 USB 裝置

- 點擊「重新掃描裝置」按鈕掃描可用的 USB Serial 裝置
- 從下拉選單中選擇您的 ESP32 裝置
  - 可以查看裝置名稱、Vendor ID (VID) 和 Product ID (PID)

### 4. 設定通訊參數

- **鮑率 (Baud Rate)**：預設 115200，需與 ESP32 設定一致
- **自動連線**：開啟此選項後，App 啟動時會自動連線

### 5. 儲存並連線

點擊「儲存並連線」按鈕，設定會自動保存並嘗試連線到選定的 USB 裝置。

### 6. 查看連線狀態

主畫面會顯示 USB Serial 連線狀態卡片，顯示目前的連線狀態：
- ✓ 已連線
- ⟳ 連線中...
- ✗ 錯誤（會顯示錯誤訊息）
- ○ 未連線

## JSON 資料格式

當導航進行時，應用程式會自動發送以下格式的 JSON 資料到 USB Serial：

\`\`\`json
{
    "turnDirection": "left",
    "direction": "向左轉",
    "turnDistance": "在 500 公尺後",
    "iconData": "..."
}
\`\`\`

每條 JSON 資料後會附加一個換行符號 (\n)，方便 ESP32 解析。

## ESP32 接收範例程式

以下是一個簡單的 ESP32 Arduino 程式範例，用於接收並解析資料：

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
      
      Serial.print("方向: ");
      Serial.println(turnDirection);
      Serial.print("指示: ");
      Serial.println(direction);
      Serial.print("距離: ");
      Serial.println(turnDistance);
      
      // 在這裡加入您的處理邏輯
      // 例如：顯示在螢幕上、控制 LED 等
    } else {
      Serial.println("JSON 解析失敗");
    }
  }
}
\`\`\`

## 常見的 ESP32 USB Serial 晶片

| 晶片型號 | Vendor ID | Product ID | 說明 |
|---------|-----------|------------|------|
| CP2102  | 0x10C4    | 0xEA60     | Silicon Labs |
| CH340   | 0x1A86    | 0x7523     | WCH |
| FT232R  | 0x0403    | 0x6001     | FTDI |

## 注意事項

1. **USB OTG 支援**：確保您的 Android 裝置支援 USB OTG 功能
2. **電源供應**：某些 ESP32 板可能需要外部供電
3. **鮑率設定**：App 和 ESP32 的鮑率必須一致（預設 115200）
4. **資料格式**：每條 JSON 資料以換行符號 (\n) 結尾
5. **權限**：首次連線時會要求 USB 裝置存取權限

## 疑難排解

### 找不到 USB 裝置
- 檢查 USB OTG 線是否正常
- 確認 ESP32 已正確連接並供電
- 嘗試點擊「重新掃描裝置」

### 連線失敗
- 確認已授予 USB 權限
- 檢查鮑率設定是否正確
- 嘗試重新插拔 USB 裝置

### ESP32 收不到資料
- 確認 ESP32 的 Serial.begin() 鮑率與 App 設定一致
- 檢查 USB Serial 晶片驅動是否正常
- 使用 Serial Monitor 確認 ESP32 能正常接收資料

## 技術細節

- **USB Serial Library**：usb-serial-for-android 3.7.3
- **儲存方式**：Android DataStore Preferences
- **UI Framework**：Jetpack Compose
- **狀態管理**：Kotlin Flow + StateFlow
- **支援的通訊參數**：
  - 鮑率：可設定（預設 115200）
  - 資料位元：8
  - 停止位元：1
  - 同位位元：None

## 進階設定

如需限制只連接特定的 USB 裝置（例如只允許特定 VID/PID），可以編輯：
`app/src/main/res/xml/device_filter.xml`

## 更新紀錄

- 從 MQTT 改造為 USB Serial 通訊
- 移除網路相關依賴和權限
- 新增 USB Host 功能支援
- 保留原有的導航資訊擷取功能
- 保留原有的 JSON 格式
