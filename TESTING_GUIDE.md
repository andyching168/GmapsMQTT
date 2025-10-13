# 冷啟動抓取現有通知測試指南

## 測試場景：使用者開啟導航後才冷啟動 App

### 測試步驟

#### 1. 準備工作
```bash
# 清空日誌
adb logcat -c

# 啟動日誌監控（開一個終端機視窗）
adb logcat -s NotificationCatcher:D MainActivity:D
```

#### 2. 設置測試環境
1. **確保通知存取權限已啟用**
   - 打開 App
   - 檢查權限狀態 banner 是否顯示「✓ 通知存取權限已啟用」
   - 如果未啟用，點擊按鈕開啟權限

2. **完全關閉 App**
   - 從最近任務列表中滑掉 App
   - 或使用指令：`adb shell am force-stop com.andyching168.notificationcatcher`

#### 3. 執行測試
1. **開啟 Google Maps 並啟動導航**
   - 打開 Google Maps
   - 設定目的地
   - 點擊「開始」啟動導航
   - **確認導航通知出現在通知欄**

2. **冷啟動 App**
   - 點擊 App 圖標啟動
   - **觀察：App 應該立即顯示導航資訊**

#### 4. 檢查日誌輸出

**預期看到的日誌順序：**

```
D/MainActivity: onResume called
D/MainActivity: Service enabled: true
D/MainActivity: Requesting rebind...
D/NotificationCatcher: Service onListenerConnected - 服務已連接到系統
D/NotificationCatcher: 當前活躍通知數量: X
D/NotificationCatcher: 檢查通知: com.google.android.apps.maps
D/NotificationCatcher: ✓ 發現現存的 Google Maps 通知，立即處理
D/NotificationCatcher: 原始通知內容:
Package: com.google.android.apps.maps
Title: [轉彎距離]
Direction: [轉彎方向]
SubText: [剩餘距離·時間·ETA]
...
D/NotificationCatcher: 解析後的資訊: direction=[轉彎方向]
D/NotificationCatcher: 更新導航資訊: NavigationInfo(...)
```

### 預期結果

✅ **成功標準：**
- App 啟動後 1-2 秒內顯示導航資訊
- 權限狀態 banner 顯示綠色「✓ 通知存取權限已啟用」
- 導航卡片顯示完整資訊（方向、距離、時間等）
- 日誌中看到「✓ 發現現存的 Google Maps 通知，立即處理」

❌ **失敗情況：**
- App 啟動後顯示「目前沒有導航通知」
- 日誌中看到「未發現 Google Maps 通知」
- 需要重新開關權限才能抓到

### 故障排除

#### 情況 1: 權限 banner 顯示紅色「未啟用」
**原因：** 權限未正確設置  
**解決：**
1. 點擊「開啟通知存取權限」按鈕
2. 在系統設定中找到 App 名稱
3. 開啟開關
4. 返回 App（banner 應自動變綠色）

#### 情況 2: 權限已啟用但抓不到通知
**檢查日誌：**

**如果看到：**
```
D/NotificationCatcher: 當前活躍通知數量: 0
```
**可能原因：** Google Maps 通知被系統隱藏或分組  
**解決：** 在 Google Maps 中重新規劃路線

**如果看到：**
```
D/NotificationCatcher: 檢查通知: com.android.systemui
D/NotificationCatcher: 檢查通知: com.whatsapp
D/NotificationCatcher: 未發現 Google Maps 通知
```
**可能原因：** Google Maps 沒有發送通知  
**解決：** 檢查 Google Maps 的通知設定是否啟用

**如果看到：**
```
D/NotificationCatcher: activeNotifications 為 null
```
**可能原因：** Service 未正確連接  
**解決：**
1. 重新開關通知存取權限
2. 重啟手機
3. 檢查 Android 版本是否支援

#### 情況 3: onListenerConnected 沒有被調用
**可能原因：** `requestRebind()` 在某些設備上不生效  
**解決：**
1. 手動重新開關一次通知存取權限
2. 下次冷啟動應該就能正常工作

### 不同 Android 版本的行為

#### Android 7.0 - 9.0
- `requestRebind()` 可能需要較長時間（2-5 秒）
- 建議觀察日誌確認 Service 何時連接

#### Android 10+
- `requestRebind()` 通常很快（1-2 秒內）
- Service 連接更穩定

#### Android 14+
- 可能有額外的隱私限制
- 首次安裝後需要明確授權

### 進階測試

#### 測試多個通知場景
1. 同時有多個 App 的通知（WhatsApp, LINE 等）
2. Google Maps 通知在通知列表的不同位置
3. Google Maps 有多個通知（例如：導航 + 停車提醒）

#### 壓力測試
1. 快速切換 App（冷啟動 → Google Maps → 冷啟動）
2. 在導航過程中頻繁啟動/關閉 App
3. 長時間導航（超過 30 分鐘）

### 性能檢查

使用以下指令監控記憶體和 CPU：
```bash
# 記憶體使用
adb shell dumpsys meminfo com.andyching168.notificationcatcher

# CPU 使用
adb shell top -n 1 | grep notificationcatcher
```

### 完整測試檢查清單

- [ ] 冷啟動能立即抓到現有導航通知
- [ ] 權限狀態 banner 正確顯示
- [ ] 從設定返回後 banner 自動更新
- [ ] Service 斷線後能自動重連
- [ ] 日誌輸出符合預期
- [ ] 無記憶體洩漏
- [ ] 多次冷啟動都能正常工作
- [ ] 在不同 Android 版本上測試

## 回報問題

如果測試失敗，請提供：
1. 完整的 logcat 日誌
2. Android 版本和設備型號
3. Google Maps 版本
4. 具體的測試步驟和結果
