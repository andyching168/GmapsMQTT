# ESP32 測試程式使用說明

## 📋 檔案說明

已建立 `test_esp32.ino` - ESP32 導航資訊接收測試程式

## ✨ 主要功能

1. **WiFi 熱點**
   - 建立名為 `ESP32-Navigation` 的無密碼熱點
   - IP 位址：192.168.4.1

2. **USB Serial 接收**
   - 鮑率：115200
   - 接收 Android 傳送的 JSON 導航資訊
   - 自動解析並儲存

3. **Web Server 顯示**
   - 美觀的網頁介面
   - 即時顯示導航資訊
   - 自動每 3 秒刷新
   - 手動刷新按鈕
   - 顯示連線狀態和最後更新時間

4. **API 介面**
   - `/` - 網頁介面
   - `/api` - JSON API

## 🔧 使用步驟

### 1. 準備 ESP32

**所需函式庫：**
- ArduinoJson (版本 6.x)

**安裝方式：**
1. 開啟 Arduino IDE
2. 工具 → 管理程式庫
3. 搜尋 "ArduinoJson"
4. 安裝 6.x 版本

### 2. 上傳程式

1. 開啟 `test_esp32.ino`
2. 選擇板子：ESP32 Dev Module
3. 選擇連接埠
4. 上傳程式

### 3. 連接 WiFi

1. ESP32 啟動後會建立熱點 `ESP32-Navigation`
2. 用手機或電腦連接此熱點（無密碼）
3. 開啟瀏覽器，訪問 `http://192.168.4.1`

### 4. 連接 Android

1. 用 USB OTG 線將 ESP32 連接到 Android 裝置
2. 開啟 Android App
3. 在 USB 設定中選擇 ESP32 裝置
4. 設定鮑率為 115200
5. 儲存並連線

### 5. 開始導航

1. 開啟 Google Maps
2. 開始導航
3. 導航資訊會自動顯示在網頁上

## 📱 網頁介面功能

### 主要顯示
- **轉彎方向**：圖示 + 方向代碼
- **指示**：中文導航指示
- **距離**：距離下個轉彎的距離
- **最後更新**：顯示資料更新時間

### 狀態指示
- 🟢 **已連線** - 正在接收資料
- 🟠 **等待資料** - 超過 30 秒未收到新資料

### 系統資訊
- WiFi 熱點名稱
- IP 位址
- Serial 鮑率
- 自動刷新設定

## 🎨 支援的方向圖示

| 方向代碼 | 圖示 | 說明 |
|---------|------|------|
| left | ⬅️ | 左轉 |
| right | ➡️ | 右轉 |
| straight | ⬆️ | 直行 |
| side_left | ↖️ | 靠左 |
| side_right | ↗️ | 靠右 |
| ForkLeft | ↖️ | 分岔路靠左 |
| ForkRight | ↗️ | 分岔路靠右 |
| SharpTurnLeft | ↩️ | 向左後急轉 |
| SharpTurnRight | ↪️ | 向右後急轉 |
| Roundabout | 🔄 | 圓環 |
| UTurnLeft | ↩️ | 迴轉 |
| DestinationLeft | 🏁 | 目的地在左方 |
| DestinationRight | 🏁 | 目的地在右方 |
| DestinationFront | 🏁 | 目的地在前方 |

## 🔍 除錯功能

### Serial Monitor 輸出

開啟 Serial Monitor (115200 鮑率) 可以看到：

```
=================================
ESP32 導航資訊接收系統
=================================

正在建立 WiFi 熱點...
✓ WiFi 熱點已建立
  SSID: ESP32-Navigation
  密碼: (無密碼)
  IP 位址: 192.168.4.1

✓ Web Server 已啟動
  請用手機/電腦連接熱點後，開啟瀏覽器訪問：
  http://192.168.4.1

=================================
等待接收導航資訊...
=================================

--- 收到資料 ---
{"turnDirection":"left","direction":"向左轉","turnDistance":"在 500 公尺後","iconData":"..."}
✓ JSON 解析成功
  方向: left
  指示: 向左轉
  距離: 在 500 公尺後
  圖標資料長度: 256
---------------
```

## 🎯 JSON 資料格式範例

**接收格式：**
```json
{
    "turnDirection": "left",
    "direction": "向左轉",
    "turnDistance": "在 500 公尺後",
    "iconData": "..."
}
```

**API 輸出格式：**
```json
{
    "hasData": true,
    "turnDirection": "left",
    "direction": "向左轉",
    "turnDistance": "在 500 公尺後",
    "lastUpdate": 12345,
    "secondsAgo": 5
}
```

## ⚙️ 自訂設定

### 修改熱點名稱

```cpp
const char* ap_ssid = "ESP32-Navigation";  // 改成您想要的名稱
```

### 設定密碼（可選）

```cpp
const char* ap_password = "your_password";  // 設定密碼
```

### 修改刷新頻率

在 HTML 的 JavaScript 部分：

```javascript
// 自動刷新（每3秒）- 修改 3000 為其他值（毫秒）
setTimeout(refreshData, 3000);
```

### 修改資料過期時間

```cpp
// 30 秒內的資料視為有效 - 修改 30000 為其他值（毫秒）
if (navData.hasData && (millis() - navData.lastUpdate < 30000)) {
```

## 🐛 常見問題

### Q: 無法連接到熱點？
A: 
- 確認 ESP32 已正確上電
- 檢查 Serial Monitor 是否顯示熱點已建立
- 某些裝置可能需要重新掃描 WiFi

### Q: 網頁無法開啟？
A: 
- 確認已連接到 ESP32-Navigation 熱點
- 確認輸入正確的 IP: http://192.168.4.1
- 不要加 https://

### Q: 收不到導航資料？
A: 
1. 檢查 Android App 是否已連線
2. 確認鮑率設定為 115200
3. 開啟 Serial Monitor 查看是否收到資料
4. 確認 Google Maps 正在導航中

### Q: JSON 解析失敗？
A: 
- 檢查 Serial Monitor 的錯誤訊息
- 確認 ArduinoJson 函式庫已正確安裝
- 確認使用 ArduinoJson 6.x 版本

## 🚀 進階應用

### 1. 添加 OLED 顯示

可以加入 OLED 螢幕直接顯示導航資訊：

```cpp
#include <Wire.h>
#include <Adafruit_SSD1306.h>

Adafruit_SSD1306 display(128, 64, &Wire, -1);

// 在 setup() 中初始化
display.begin(SSD1306_SWITCHCAPVCC, 0x3C);

// 在收到資料後更新顯示
display.clearDisplay();
display.setTextSize(2);
display.setTextColor(WHITE);
display.setCursor(0, 0);
display.println(getDirectionIcon(navData.turnDirection));
display.setTextSize(1);
display.println(navData.direction);
display.println(navData.turnDistance);
display.display();
```

### 2. 添加蜂鳴器提示

轉彎時發出聲音提示：

```cpp
const int buzzerPin = 25;

void setup() {
  pinMode(buzzerPin, OUTPUT);
}

// 在收到新資料時
if (navData.turnDirection != previousDirection) {
  tone(buzzerPin, 1000, 200);  // 發出 200ms 的提示音
}
```

### 3. 添加 LED 指示燈

用 LED 顯示轉彎方向：

```cpp
const int ledLeft = 26;
const int ledRight = 27;
const int ledStraight = 25;

// 根據方向點亮對應 LED
if (navData.turnDirection == "left") {
  digitalWrite(ledLeft, HIGH);
} else if (navData.turnDirection == "right") {
  digitalWrite(ledRight, HIGH);
} else {
  digitalWrite(ledStraight, HIGH);
}
```

## 📦 完整系統測試

1. ✅ 上傳程式到 ESP32
2. ✅ 連接 WiFi 熱點
3. ✅ 開啟網頁確認顯示
4. ✅ 連接 USB 到 Android
5. ✅ 開始 Google Maps 導航
6. ✅ 確認網頁即時更新

## 📝 注意事項

- ESP32 需要穩定的電源供應
- USB Serial 和 WiFi 可以同時使用
- 建議使用優質的 USB OTG 線
- 網頁會自動刷新，不需手動操作
- 可以多個裝置同時連接熱點查看

祝您測試順利！🎉
