# 🚀 快速開始指南

## 完整系統測試流程

### 📱 第一步：Android App 設定

1. **編譯並安裝 App**
   ```
   Build → Rebuild Project
   Run → Run 'app'
   ```

2. **授予權限**
   - 通知存取權限
   - USB 裝置權限

3. **準備導航**
   - 開啟 Google Maps
   - 設定目的地（先不要開始導航）

---

### 🔧 第二步：ESP32 設定

#### 選擇方案

**方案 A：完整版（推薦）**
- 檔案：`test_esp32.ino`
- 特點：美觀介面、完整功能
- 適合：展示、實際使用

**方案 B：簡化版**
- 檔案：`test_esp32_simple.ino`
- 特點：極簡設計、快速啟動
- 適合：測試、學習

#### 上傳程式

1. **安裝 ArduinoJson 函式庫**
   ```
   工具 → 管理程式庫
   搜尋：ArduinoJson
   安裝：6.x 版本
   ```

2. **設定 Arduino IDE**
   - 板子：ESP32 Dev Module
   - Upload Speed: 115200
   - Port: 選擇 ESP32 的 COM 埠

3. **上傳**
   - 開啟 `test_esp32.ino` 或 `test_esp32_simple.ino`
   - 點擊上傳按鈕
   - 等待完成

4. **確認啟動**
   - 開啟 Serial Monitor (115200)
   - 應該看到：
     ```
     WiFi: ESP32-Navigation
     IP: 192.168.4.1
     準備就緒！
     ```

---

### 🔌 第三步：硬體連接

1. **Android ←→ ESP32 USB 連接**
   ```
   Android 裝置 → USB OTG 線 → ESP32
   ```

2. **確認連接**
   - Android App 點擊「USB 設定」
   - 點擊「重新掃描裝置」
   - 應該能看到 ESP32 裝置（CP2102/CH340/FT232 等）

3. **設定參數**
   - 選擇裝置
   - 鮑率：115200
   - 勾選「自動連線」

4. **連線**
   - 點擊「儲存並連線」
   - 授予 USB 權限
   - 確認狀態顯示「✓ 已連線」

---

### 📡 第四步：WiFi 連接（查看顯示）

1. **用手機/平板/電腦連接 WiFi**
   - SSID: `ESP32-Navigation`（完整版）或 `ESP32-Nav`（簡化版）
   - 密碼: 無

2. **開啟瀏覽器**
   - 輸入：`http://192.168.4.1`
   - 應該看到網頁介面

3. **確認狀態**
   - 完整版：應顯示「○ 等待資料」
   - 簡化版：應顯示「等待資料...」

---

### 🗺️ 第五步：開始導航測試

1. **在 Android 上開始 Google Maps 導航**

2. **觀察變化**

   **Android App：**
   - 導航資訊卡片更新
   - JSON 資訊更新
   - USB 連線狀態保持「已連線」

   **ESP32 Serial Monitor：**
   ```
   --- 收到資料 ---
   {"turnDirection":"left","direction":"向左轉",...}
   ✓ JSON 解析成功
     方向: left
     指示: 向左轉
     距離: 在 500 公尺後
   ```

   **網頁介面：**
   - 狀態變成「✓ 已連線」
   - 顯示導航指示
   - 自動更新

3. **測試不同指示**
   - 繼續導航
   - 觀察不同轉彎指示的顯示

---

## ✅ 檢查清單

### Android App
- [ ] App 已安裝
- [ ] 通知權限已授予
- [ ] USB 裝置已連接
- [ ] USB 設定完成
- [ ] 連線狀態顯示「已連線」

### ESP32
- [ ] ArduinoJson 函式庫已安裝
- [ ] 程式已上傳
- [ ] Serial Monitor 顯示正常啟動
- [ ] WiFi 熱點已建立

### 網頁
- [ ] 已連接到 ESP32 熱點
- [ ] 能開啟 http://192.168.4.1
- [ ] 網頁顯示正常

### 導航
- [ ] Google Maps 已開啟
- [ ] 已設定目的地
- [ ] 導航已開始
- [ ] 資料在網頁上顯示

---

## 🐛 快速除錯

### 問題：Android 找不到 USB 裝置
```
✓ 檢查 USB OTG 線是否正常
✓ 確認 ESP32 已供電
✓ 點擊「重新掃描裝置」
✓ 嘗試重新插拔
```

### 問題：ESP32 熱點連不上
```
✓ 確認 ESP32 已啟動（看 Serial Monitor）
✓ 重新掃描 WiFi
✓ 重啟 ESP32
```

### 問題：網頁打不開
```
✓ 確認已連接到正確的 WiFi
✓ 輸入 http://192.168.4.1（不是 https）
✓ 清除瀏覽器快取
```

### 問題：沒有收到資料
```
✓ 確認 Android USB 已連線
✓ 確認鮑率設定為 115200
✓ 確認 Google Maps 正在導航
✓ 查看 Serial Monitor 是否有錯誤
```

### 問題：JSON 解析失敗
```
✓ 確認 ArduinoJson 函式庫版本為 6.x
✓ 查看 Serial Monitor 的錯誤訊息
✓ 確認收到的資料格式正確
```

---

## 📊 預期結果

### 成功的測試流程

1. **啟動階段**
   - ESP32 建立熱點 ✓
   - Android 連接 USB ✓
   - 網頁可以訪問 ✓

2. **導航階段**
   - Android 捕獲導航資訊 ✓
   - 透過 USB 發送到 ESP32 ✓
   - ESP32 解析 JSON ✓
   - 網頁顯示資訊 ✓

3. **更新階段**
   - 轉彎指示改變 ✓
   - 資料即時更新 ✓
   - 網頁自動刷新 ✓

---

## 🎯 效能指標

| 項目 | 預期值 |
|------|--------|
| 資料延遲 | < 1 秒 |
| 網頁刷新 | 2-3 秒 |
| JSON 解析成功率 | > 99% |
| WiFi 連接穩定性 | 持續連線 |
| USB Serial 穩定性 | 持續連線 |

---

## 🎨 自訂設定（可選）

### 修改熱點名稱

**test_esp32.ino:**
```cpp
const char* ap_ssid = "我的導航系統";
```

**test_esp32_simple.ino:**
```cpp
const char* ap_ssid = "NavESP32";
```

### 設定熱點密碼

```cpp
const char* ap_password = "12345678";  // 至少 8 位
```

### 修改刷新速度

**完整版（test_esp32.ino）:**
```javascript
setTimeout(refreshData, 3000);  // 改成 2000 = 2秒
```

**簡化版（test_esp32_simple.ino）:**
```javascript
setTimeout(function(){ location.reload(); }, 2000);  // 改成 1000 = 1秒
```

---

## 💡 提示

1. **首次測試建議使用簡化版**
   - 更容易除錯
   - 啟動更快
   - 程式碼更容易理解

2. **實際使用建議使用完整版**
   - 介面更美觀
   - 資訊更豐富
   - 功能更完整

3. **可以同時上傳兩個程式到不同的 ESP32**
   - 比較功能差異
   - 選擇最適合的版本

4. **Serial Monitor 是最好的除錯工具**
   - 隨時觀察接收狀態
   - 查看錯誤訊息
   - 確認資料格式

---

## 📞 需要協助？

參考詳細文件：
- [ESP32 測試指南](ESP32_TESTING_GUIDE.md)
- [USB Serial 使用指南](USB_SERIAL_GUIDE.md)
- [完整檢查清單](CHECKLIST.md)

祝您測試順利！🎉
