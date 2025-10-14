# 🐛 資料顯示 "null" 問題診斷指南

## 問題描述

ESP32 Serial Monitor 或網頁顯示 "null"，但 Android App 顯示已連線。

## 可能原因

### 1. JSON 欄位值確實是空的
Android 可能在某些情況下發送空值：
- 導航剛開始，還沒有完整資訊
- 導航已結束
- Google Maps 通知內容不完整

### 2. JSON 欄位名稱不匹配
ESP32 程式讀取的欄位名稱可能與 Android 發送的不一致。

### 3. 字元編碼問題
中文字元可能在 USB Serial 傳輸中出現問題。

## 診斷步驟

### 步驟 1：使用除錯版本

上傳 `test_esp32_debug.ino` 到 ESP32：

1. 此版本會顯示：
   - 收到的原始資料（完整內容）
   - HEX 編碼（檢查不可見字元）
   - 每個 JSON 欄位的詳細狀態
   - 是否為 NULL 或空字串
   - 完整的 JSON 結構

2. 連接 WiFi：`ESP32-Debug`

3. 開啟 Serial Monitor（115200 鮑率）

4. 開始 Google Maps 導航

5. 觀察輸出

### 步驟 2：檢查 Serial Monitor 輸出

**正常的輸出應該像這樣：**

```
╔════════════════════════════════════════════════════
║ 收到資料 #1
╠════════════════════════════════════════════════════
║ 長度: 156 bytes
╠════════════════════════════════════════════════════
║ 原始內容:
{"turnDirection":"left","direction":"向左轉","turnDistance":"在 500 公尺後","iconData":"..."}
╠════════════════════════════════════════════════════
║ ✓ JSON 解析成功
╠════════════════════════════════════════════════════
║ 欄位檢查:
║   turnDirection: "left" (4 chars)
║   direction: "向左轉" (9 chars)
║   turnDistance: "在 500 公尺後" (21 chars)
║   iconData: 256 chars
╠════════════════════════════════════════════════════
║ JSON 結構檢視:
{
  "turnDirection": "left",
  "direction": "向左轉",
  "turnDistance": "在 500 公尺後",
  "iconData": "..."
}
```

**如果顯示 NULL，會看到：**

```
║ 欄位檢查:
║   turnDirection: "left" (4 chars)
║   direction: <NULL>
║   turnDistance: <NULL>
║   iconData: "" (0 chars)
```

### 步驟 3：確認 Android App 發送的資料

在 Android App 中：

1. 點擊「顯示 JSON」按鈕
2. 複製 JSON 內容
3. 檢查內容是否完整

**預期的 JSON 格式：**
```json
{
    "turnDirection": "left",
    "direction": "向左轉",
    "turnDistance": "在 500 公尺後",
    "iconData": "..."
}
```

**問題的 JSON（空值）：**
```json
{
    "turnDirection": "left",
    "direction": "",
    "turnDistance": "",
    "iconData": ""
}
```

或

```json
{
    "turnDirection": "left",
    "direction": null,
    "turnDistance": null,
    "iconData": ""
}
```

## 解決方案

### 方案 1：使用更新後的程式

我已經更新了 `test_esp32_simple.ino` 和 `test_esp32.ino`，新版本：

✅ 處理 null 值
✅ 處理空字串
✅ 優先顯示有內容的欄位
✅ 提供更詳細的除錯資訊

**重新上傳程式即可。**

### 方案 2：檢查導航狀態

確保在 Google Maps 中：

1. ✅ 導航已經**開始**（不只是路線規劃）
2. ✅ 有明確的轉彎指示
3. ✅ 不是在高速公路上長距離直行

某些情況下 Google Maps 不會提供詳細的轉彎資訊：
- 導航剛開始
- 長距離直行
- 導航即將結束

### 方案 3：測試其他導航路線

嘗試不同的導航路線：

1. 選擇市區路線（多個轉彎）
2. 避免高速公路
3. 選擇較短的路線（2-3 公里）

## 測試清單

### Android App 端

- [ ] App 顯示「✓ 已連線」
- [ ] 導航資訊卡片有內容
- [ ] 點擊「顯示 JSON」可以看到完整資料
- [ ] JSON 中的 direction 和 turnDistance 不是空的

### ESP32 端

- [ ] Serial Monitor 顯示「收到資料」
- [ ] 能看到完整的 JSON 字串
- [ ] JSON 解析成功
- [ ] 各欄位不是 <NULL>

### 導航狀態

- [ ] Google Maps 導航已開始（不只是路線規劃）
- [ ] 有明確的轉彎提示音
- [ ] 畫面上顯示轉彎箭頭

## 常見情況分析

### 情況 1：所有欄位都是 null
**原因：** 導航還沒開始或已結束
**解決：** 確認 Google Maps 正在導航中

### 情況 2：turnDirection 有值，其他是 null
**原因：** Android 只捕獲到部分資訊
**解決：** 
- 重新開始導航
- 選擇不同的路線
- 檢查通知權限

### 情況 3：收不到任何資料
**原因：** USB 連接問題
**解決：**
- 檢查 USB 連接
- 確認鮑率為 115200
- 重新連接 USB

### 情況 4：JSON 解析失敗
**原因：** 資料損壞或太大
**解決：**
- 使用除錯版本查看原始資料
- 檢查資料長度
- 確認 JSON 格式正確

## 實際操作建議

### 1. 立即測試

```
1. 上傳 test_esp32_debug.ino
2. 開啟 Serial Monitor
3. 開始導航
4. 截圖 Serial 輸出
5. 檢查每個欄位的狀態
```

### 2. 對比測試

```
┌─────────────────┬──────────────────┐
│   Android App   │      ESP32       │
├─────────────────┼──────────────────┤
│ 顯示 JSON       │ Serial Monitor   │
│ 複製內容        │ 觀察解析結果     │
│ 比對欄位        │ 檢查是否一致     │
└─────────────────┴──────────────────┘
```

### 3. 逐步排查

1. **確認連接**
   - Android: ✓ 已連線
   - ESP32: 顯示「收到資料」

2. **確認資料**
   - Android: JSON 有內容
   - ESP32: 收到完整 JSON

3. **確認解析**
   - ESP32: 解析成功
   - ESP32: 各欄位有值

## 需要提供的資訊

如果問題仍然存在，請提供：

1. **Serial Monitor 完整輸出**（使用除錯版本）
2. **Android App 的 JSON 內容**（點擊「顯示 JSON」）
3. **Google Maps 導航截圖**
4. **ESP32 型號和 USB Serial 晶片型號**

## 快速修復

如果您趕時間，可以嘗試：

1. **重啟所有裝置**
   ```
   1. 關閉 Google Maps
   2. 斷開 USB 連接
   3. 重啟 ESP32
   4. 重新連接
   5. 重新開始導航
   ```

2. **使用簡化版測試**
   ```
   上傳 test_esp32_simple.ino（已更新）
   此版本會優先顯示有值的欄位
   ```

3. **檢查特定路線**
   ```
   選擇一個有多個轉彎的市區路線
   不要選擇高速公路
   ```

記住：某些情況下 Google Maps 確實不會提供完整的導航資訊，這是正常的！
