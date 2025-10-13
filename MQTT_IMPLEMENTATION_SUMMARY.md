# MQTT 功能實現總結

## 新增的檔案

1. **MqttSettings.kt** - MQTT 設定管理
   - 使用 DataStore 儲存設定
   - 提供設定的讀取和保存功能

2. **MqttClientManager.kt** - MQTT 客戶端管理
   - 處理 MQTT 連線、斷線
   - 發送訊息到 MQTT Broker
   - 自動重新連線機制
   - 連線狀態管理

3. **MQTT_SETUP_GUIDE.md** - 使用者指南

## 修改的檔案

1. **app/build.gradle.kts**
   - 添加 HiveMQ MQTT Client 依賴
   - 添加 DataStore 依賴

2. **NavigationViewModel.kt**
   - 整合 MqttClientManager
   - 當導航資訊更新時自動推送到 MQTT

3. **GmapMQTTApp.kt**
   - 初始化 MqttClientManager 和 MqttSettingsManager
   - 提供全域訪問這些管理器的方法
   - 使用自訂 ViewModelFactory

4. **MainActivity.kt**
   - 添加 MQTT 設定 UI（MqttSettingsDialog）
   - 添加 MQTT 連線狀態顯示卡片
   - 實現 App 啟動時的自動連線功能
   - 添加「MQTT 設定」按鈕

5. **AndroidManifest.xml**
   - 添加網路權限（INTERNET）
   - 添加網路狀態權限（ACCESS_NETWORK_STATE）

## 功能特點

✅ **完整的 UI 介面**：使用者可以輕鬆設定所有 MQTT 參數
✅ **本地儲存**：所有設定自動保存，重啟 App 後保留
✅ **自動連線**：可選擇 App 啟動時自動連線
✅ **自動推送**：導航資訊變動時自動推送 JSON
✅ **連線狀態顯示**：即時顯示 MQTT 連線狀態
✅ **錯誤處理**：顯示連線錯誤訊息
✅ **自動重連**：連線中斷時自動嘗試重新連線
✅ **安全性**：密碼欄位使用遮罩顯示

## 使用流程

1. 點擊「MQTT 設定」按鈕
2. 輸入 Broker URL、Port、帳號密碼、Topic
3. 選擇是否要自動連線
4. 點擊「儲存並連線」
5. 查看連線狀態
6. 開始 Google Maps 導航
7. 導航資訊會自動推送到 MQTT Broker

## 推送的資料格式

```json
{
    "turnDirection": "left",
    "direction": "向左轉", 
    "turnDistance": "100 公尺"
}
```

## 測試建議

1. 使用免費的 MQTT Broker 測試（如：broker.hivemq.com）
2. 使用 MQTT Explorer 或 mosquitto_sub 訂閱 Topic
3. 啟動 Google Maps 導航
4. 觀察資料是否正確推送

## 技術細節

- **MQTT 版本**：MQTT v3
- **MQTT Library**：HiveMQ MQTT Client 1.3.3
- **儲存方式**：Android DataStore Preferences
- **UI Framework**：Jetpack Compose
- **狀態管理**：Kotlin Flow + StateFlow
