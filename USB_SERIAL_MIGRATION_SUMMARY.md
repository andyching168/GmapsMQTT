# USB Serial 改造完成總結

## 改造成果

✅ 已成功將 GmapsMQTT 從 MQTT 通訊改造為 USB Serial 通訊

## 主要變更

### 新增的檔案
1. **UsbSerialManager.kt** - USB Serial 連線管理器
   - 管理 USB 裝置連線、斷線
   - 掃描可用的 USB Serial 裝置
   - 發送資料到 USB Serial
   - 處理 USB 權限請求

2. **UsbSettings.kt** - USB Serial 設定管理
   - 使用 DataStore 儲存設定
   - 儲存裝置名稱、鮑率、自動連線等設定

3. **device_filter.xml** - USB 裝置過濾器
   - 定義支援的 USB 裝置類型
   - 可選擇性限制特定 VID/PID

4. **USB_SERIAL_GUIDE.md** - 使用者指南
   - 詳細的使用說明
   - ESP32 接收範例程式
   - 疑難排解指南

### 修改的檔案
1. **build.gradle.kts**
   - 移除 HiveMQ MQTT Client 依賴
   - 新增 usb-serial-for-android 依賴
   - 移除 MQTT 相關的 packaging excludes

2. **settings.gradle.kts**
   - 新增 JitPack repository

3. **NavigationViewModel.kt**
   - 將依賴從 MqttClientManager 改為 UsbSerialManager
   - 更新資料發送邏輯（改為 USB Serial）
   - JSON 資料添加換行符號方便解析

4. **GmapMQTTApp.kt**
   - 替換 MQTT 相關組件為 USB Serial 組件
   - 新增 cleanup 處理

5. **MainActivity.kt**
   - 移除 MQTT 相關 UI
   - 新增 USB 裝置選擇介面
   - 新增 USB 連線狀態顯示
   - 新增 USB 設定對話框（含裝置掃描、鮑率設定等）

6. **AndroidManifest.xml**
   - 移除網路相關權限 (INTERNET, ACCESS_NETWORK_STATE)
   - 新增 USB Host 功能宣告
   - 新增 USB 裝置連接意圖過濾器

### 刪除的檔案
1. **MqttClientManager.kt** - 已刪除
2. **MqttSettings.kt** - 已刪除
3. **MQTT_SETUP_GUIDE.md** - 已刪除
4. **MQTT_IMPLEMENTATION_SUMMARY.md** - 已刪除

## 功能特點

✅ **完整的 USB 裝置管理**：自動掃描、選擇、連線
✅ **本地儲存設定**：所有設定自動保存，重啟 App 後保留
✅ **自動連線**：可選擇 App 啟動時自動連線
✅ **自動發送**：導航資訊變動時自動發送 JSON
✅ **連線狀態顯示**：即時顯示 USB 連線狀態
✅ **錯誤處理**：顯示連線錯誤訊息
✅ **權限管理**：自動處理 USB 權限請求
✅ **裝置識別**：顯示裝置名稱、VID、PID

## 使用流程

1. 用 USB OTG 線連接 ESP32 到 Android 裝置
2. 開啟 App，點擊「USB 設定」
3. 點擊「重新掃描裝置」
4. 選擇您的 ESP32 裝置
5. 設定鮑率（預設 115200）
6. 點擊「儲存並連線」
7. 開始 Google Maps 導航
8. ESP32 會自動接收導航資訊的 JSON 資料

## ESP32 端設定

ESP32 需要使用相同的鮑率（預設 115200）來接收資料：

\`\`\`cpp
void setup() {
  Serial.begin(115200);
}

void loop() {
  if (Serial.available()) {
    String json = Serial.readStringUntil('\\n');
    // 解析 JSON...
  }
}
\`\`\`

## JSON 資料格式

\`\`\`json
{
    "turnDirection": "left",
    "direction": "向左轉",
    "turnDistance": "在 500 公尺後",
    "iconData": "..."
}
\`\`\`

每條資料以換行符號 (\n) 結尾。

## 技術細節

- **USB Serial Library**：usb-serial-for-android 3.7.3
- **支援的晶片**：CP2102, CH340, FT232 等常見 USB Serial 晶片
- **通訊參數**：115200 鮑率, 8N1 (8 資料位元, 無同位, 1 停止位元)
- **資料格式**：JSON + 換行符號
- **儲存方式**：Android DataStore
- **UI Framework**：Jetpack Compose

## 測試建議

1. 確認 Android 裝置支援 USB OTG
2. 使用 USB OTG 線連接 ESP32
3. 在 ESP32 上執行 Serial Monitor 測試程式
4. 啟動 App 並連線到 ESP32
5. 開始 Google Maps 導航
6. 確認 ESP32 能接收到 JSON 資料

## 常見問題

**Q: 找不到 USB 裝置？**
A: 確認 USB OTG 線正常、ESP32 已供電、嘗試重新掃描

**Q: 連線失敗？**
A: 檢查是否授予 USB 權限、鮑率設定是否正確

**Q: ESP32 收不到資料？**
A: 確認鮑率一致（預設 115200）、檢查 USB Serial 晶片驅動

## 優點

相比於 MQTT 方案：
- ✅ 不需要網路連線
- ✅ 更低的延遲
- ✅ 更穩定的連線
- ✅ 不需要額外的 MQTT Broker
- ✅ 更簡單的設定流程
- ✅ 適合車載應用場景

## 下一步建議

1. 在 ESP32 上實作 JSON 解析和顯示邏輯
2. 根據需要調整鮑率或通訊參數
3. 如需限制特定裝置，修改 device_filter.xml
4. 測試不同的 ESP32 板和 USB Serial 晶片

改造完成！現在您可以透過 USB Serial 將導航資訊傳送到 ESP32 了。
