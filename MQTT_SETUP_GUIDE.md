# MQTT 功能使用指南

## 功能概述

此應用程式已整合 HiveMQ MQTT Client，可以自動將 Google Maps 導航資訊推送到 MQTT Broker。

## 主要功能

1. **MQTT 連線設定**：可配置 Broker URL、帳號、密碼、Port 和 Topic
2. **自動連線**：啟動 App 時可自動連線到 MQTT Broker
3. **自動推送**：當導航資訊變動時，自動推送 JSON 格式的資料
4. **本地儲存**：所有設定都會自動保存到本地

## 使用步驟

### 1. 開啟 MQTT 設定

在主畫面點擊「MQTT 設定」按鈕。

### 2. 填寫 MQTT 設定

- **Broker URL**：MQTT Broker 的網址（例：`broker.hivemq.com`）
- **Port**：MQTT Broker 的端口（預設：`1883`）
- **使用者名稱**：MQTT 帳號（選填，如果 Broker 需要認證）
- **密碼**：MQTT 密碼（選填，如果 Broker 需要認證）
- **推送主題**：MQTT Topic 名稱（預設：`navigation/info`）
- **自動連線**：開啟此選項後，App 啟動時會自動連線

### 3. 儲存並連線

點擊「儲存並連線」按鈕，設定會自動保存並嘗試連線到 MQTT Broker。

### 4. 查看連線狀態

主畫面會顯示 MQTT 連線狀態卡片，顯示目前的連線狀態：
- ✓ 已連線
- ⟳ 連線中...
- ✗ 錯誤（會顯示錯誤訊息）
- ○ 未連線

## 推送的 JSON 格式

當導航資訊變動時，會自動推送以下格式的 JSON：

```json
{
    "turnDirection": "left",
    "direction": "向左轉",
    "turnDistance": "100 公尺"
}
```

### 欄位說明

- **turnDirection**：轉彎方向的英文代碼（如：`left`, `right`, `straight`, `GoStraight` 等）
- **direction**：導航指示的完整文字
- **turnDistance**：距離下一個轉彎點的距離

## 測試用的免費 MQTT Broker

如果您沒有自己的 MQTT Broker，可以使用以下免費的測試 Broker：

### HiveMQ Public Broker
- **URL**: `broker.hivemq.com`
- **Port**: `1883`
- **不需要帳號密碼**

### Eclipse Mosquitto Test Server
- **URL**: `test.mosquitto.org`
- **Port**: `1883`
- **不需要帳號密碼**

## 訂閱測試

您可以使用 MQTT 客戶端工具來訂閱並查看推送的資料：

### 使用 MQTT Explorer（推薦）
1. 下載並安裝 [MQTT Explorer](http://mqtt-explorer.com/)
2. 連線到與 App 相同的 Broker
3. 訂閱您設定的 Topic（預設：`navigation/info`）
4. 開始導航後，就能看到推送的資料

### 使用 mosquitto_sub 命令列工具
```bash
mosquitto_sub -h broker.hivemq.com -t navigation/info
```

## 注意事項

1. **網路連線**：確保設備已連接到網路
2. **Broker 可用性**：確認 Broker URL 和 Port 正確
3. **防火牆設定**：某些網路環境可能會阻擋 MQTT 連線
4. **持續連線**：App 會自動重新連線，如果連線中斷
5. **資料隱私**：使用公共 Broker 時，注意資料可能被其他人看到

## 進階設定

### 使用 TLS/SSL（Port 8883）

如果您的 Broker 支援加密連線，可以：
1. 將 Port 改為 `8883`
2. 確保 Broker 支援 TLS/SSL

### 自訂 Topic 格式

您可以使用階層式的 Topic 命名，例如：
- `gmap/navigation/direction`
- `car/gps/navigation`
- `home/auto/maps`

## 故障排除

### 無法連線
1. 檢查網路連線
2. 確認 Broker URL 和 Port 正確
3. 檢查是否需要帳號密碼
4. 查看錯誤訊息

### 沒有收到資料
1. 確認 MQTT 連線狀態為「已連線」
2. 確認 Google Maps 正在導航
3. 確認訂閱的 Topic 與設定相同
4. 檢查導航資訊是否有變動

### 自動連線失敗
1. 確認「自動連線」選項已開啟
2. 重新啟動 App
3. 手動連線一次測試設定是否正確

## 開發資訊

- **MQTT Library**: HiveMQ MQTT Client 1.3.3
- **儲存方式**: Android DataStore
- **連線方式**: MQTT v3 with automatic reconnection
