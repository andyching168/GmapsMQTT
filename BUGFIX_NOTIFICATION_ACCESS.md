# 通知存取權限冷啟動問題修復說明

## 問題描述
冷啟動 App 時，即使已經給過通知存取權限，仍然無法抓取到 Google Maps 的導航通知，必須重新開關通知存取權限才能正常工作。

## 根本原因
1. **Service 生命週期問題**：NotificationListenerService 在 App 冷啟動時可能尚未被系統啟動或連接
2. **缺少連接狀態檢測**：沒有監聽和處理 Service 的連接/斷線事件
3. **缺少主動重新綁定機制**：沒有在 App 啟動時請求系統重新綁定 Service
4. **現有通知未被處理**：Service 連接時沒有檢查是否已經存在 Google Maps 通知

## 修復內容

### 1. MainActivity.kt 的改進

#### 新增權限檢查方法
```kotlin
private fun isNotificationServiceEnabled(): Boolean
```
- 檢查 NotificationListenerService 是否已在系統中啟用
- 通過讀取系統設定 `enabled_notification_listeners` 來驗證

#### 新增 Service 重新綁定方法
```kotlin
private fun requestRebind()
```
- 主動請求系統重新綁定 NotificationListenerService
- 確保 Service 在 App 啟動時能正確連接

#### 覆寫 onResume 生命週期
```kotlin
override fun onResume()
```
- 每次 Activity 恢復時都檢查並確保 Service 已啟用
- 自動嘗試重新連接 Service

#### UI 改進
- 新增權限狀態顯示卡片，即時顯示權限是否已啟用
- 綠色表示已啟用，紅色表示未啟用
- 提供明確的視覺反饋

### 2. NotificationCatcherService.kt 的改進

#### 新增 onListenerConnected 回調
```kotlin
override fun onListenerConnected()
```
- 當 Service 成功連接到系統時被調用
- 自動檢查是否有現存的 Google Maps 通知
- 立即處理已存在的通知，避免遺漏

#### 新增 onListenerDisconnected 回調
```kotlin
override fun onListenerDisconnected()
```
- 當 Service 斷開連接時被調用
- 清空導航資訊，避免顯示過期數據
- 自動嘗試重新連接

#### 增強日誌記錄
- 在關鍵生命週期事件中加入詳細的日誌輸出
- 方便調試和追蹤問題

## 工作原理

### 冷啟動流程
1. App 啟動 → MainActivity.onCreate()
2. MainActivity.onResume() → 檢查權限狀態
3. 如果權限已啟用 → 調用 requestRebind()
4. 系統重新綁定 NotificationCatcherService
5. Service.onListenerConnected() → 檢查現有通知
6. 如果有 Google Maps 通知 → 立即處理並更新 UI

### 權限狀態檢測
- 使用 `Settings.Secure.getString(contentResolver, "enabled_notification_listeners")`
- 解析返回的組件名稱列表
- 檢查是否包含本應用的 NotificationCatcherService

### 自動重連機制
- Activity 每次 onResume 時檢查權限
- Service 斷線時自動請求重新綁定
- 確保 Service 持續保持連接狀態

## 測試建議

### 測試步驟
1. **冷啟動測試**
   - 完全關閉 App（從後台清除）
   - 開啟 Google Maps 並開始導航
   - 重新啟動 App
   - 驗證：應該立即顯示導航資訊，無需手動重新開關權限

2. **權限狀態測試**
   - 觀察 App 頂部的權限狀態卡片
   - 綠色「✓ 通知存取權限已啟用」表示正常
   - 紅色「✗ 通知存取權限未啟用」表示需要設置

3. **Service 重連測試**
   - 在系統設定中關閉通知存取權限
   - 返回 App，權限狀態應顯示為紅色
   - 重新開啟通知存取權限
   - 返回 App，權限狀態應自動變為綠色

4. **現有通知處理測試**
   - Google Maps 導航進行中
   - 啟動 App
   - 驗證：應立即顯示當前導航資訊

### 查看日誌
使用 adb logcat 查看詳細日誌：
```bash
adb logcat -s NotificationCatcher:D
```

預期看到的日誌：
- `Service onCreate - 服務已創建`
- `Service onListenerConnected - 服務已連接到系統`
- `發現現存的 Google Maps 通知，立即處理`（如果有現存通知）

## 注意事項

1. **Android 版本兼容性**
   - `requestRebind()` 方法在 Android 6.0 (API 23) 以上才可用
   - 已使用 try-catch 處理可能的異常

2. **系統限制**
   - NotificationListenerService 的連接完全由系統控制
   - 在某些極端情況下（如系統資源不足），Service 可能仍會被延遲啟動

3. **電池優化**
   - 某些設備的激進電池優化可能會影響 Service 的運行
   - 建議用戶將 App 加入電池優化白名單

## 後續優化建議

1. **前台服務**
   - 考慮將 NotificationListenerService 升級為前台服務
   - 提高 Service 的優先級，降低被系統殺死的風險

2. **WorkManager 定期檢查**
   - 使用 WorkManager 定期檢查 Service 狀態
   - 發現異常時自動嘗試恢復

3. **用戶引導**
   - 在首次啟動時提供詳細的權限設置教程
   - 對於某些品牌手機（如小米、華為），提供額外的設置指引
