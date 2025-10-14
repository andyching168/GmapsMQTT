# Android U+ PendingIntent 修復說明

## 問題描述

在 Android U (API 34+) 中執行應用程式時遇到以下錯誤：

```
java.lang.IllegalArgumentException: Targeting U+ (version 34 and above) disallows creating or retrieving a PendingIntent with FLAG_MUTABLE, an implicit Intent within and without FLAG_NO_CREATE and FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT for security reasons.
```

## 原因分析

從 Android U (API 34) 開始，Google 加強了 PendingIntent 的安全限制：

1. **Implicit Intent（隱式意圖）** + **FLAG_MUTABLE** = ❌ 不允許
2. 必須使用以下其中一種方式：
   - 使用 **FLAG_IMMUTABLE**（不可變）
   - 使用 **Explicit Intent**（顯式意圖）+ FLAG_MUTABLE
   - 使用 FLAG_NO_CREATE 或 FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT

## 修復方案

在 `UsbSerialManager.kt` 中，我們使用的是 implicit Intent（只有 action，沒有明確的 component），因此將 FLAG_MUTABLE 改為 **FLAG_IMMUTABLE**。

### 修復前

```kotlin
val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    PendingIntent.FLAG_MUTABLE  // ❌ 在 Android U+ 中會失敗
} else {
    0
}
```

### 修復後

```kotlin
val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    PendingIntent.FLAG_IMMUTABLE  // ✅ 符合 Android U+ 安全要求
} else {
    0
}
```

## 為什麼可以使用 FLAG_IMMUTABLE？

在我們的使用情境中：

1. **USB 權限請求**不需要修改 Intent 的內容
2. BroadcastReceiver 只需要接收權限結果
3. 不需要在 PendingIntent 中傳遞可變的額外資料

因此使用 **FLAG_IMMUTABLE** 完全符合需求，且更安全。

## 相關版本資訊

- **Android S (API 31)**：引入 FLAG_IMMUTABLE/FLAG_MUTABLE 要求
- **Android U (API 34)**：加強限制，implicit Intent 不能使用 FLAG_MUTABLE

## 測試建議

1. 在 Android 14 (API 34) 或更高版本上測試
2. 連接 USB 裝置並請求權限
3. 確認權限對話框正常顯示
4. 確認授權後可以正常連線

## 參考資料

- [Android Developers - PendingIntent](https://developer.android.com/reference/android/app/PendingIntent)
- [Android 12+ PendingIntent Mutability](https://developer.android.com/about/versions/12/behavior-changes-12#pending-intent-mutability)
- [Android 14 Behavior Changes](https://developer.android.com/about/versions/14/behavior-changes-14)

修復完成！✅
