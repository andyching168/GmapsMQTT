/*
 * ESP32 導航資訊接收測試程式
 * 
 * 功能：
 * 1. 建立無密碼的 WiFi 熱點
 * 2. 透過 USB Serial 接收 Android 傳送的導航 JSON 資料
 * 3. 在內建 Web Server 上顯示導航資訊
 * 4. WiFi Portal 管理介面
 * 
 * 使用方式：
 * 1. 上傳程式到 ESP32
 * 2. ESP32 會建立名為 "ESP32-Navigation" 的熱點
 * 3. 用手機或電腦連接此熱點
 * 4. 開啟瀏覽器訪問 http://192.168.4.1
 * 5. 透過 USB Serial 連接 Android 裝置
 * 6. Android 發送的導航資訊會即時顯示在網頁上
 */

#include <WiFi.h>
#include <WebServer.h>
#include <ArduinoJson.h>

// WiFi 熱點設定
const char* ap_ssid = "ESP32-Navigation";     // 熱點名稱（可修改）
const char* ap_password = "";                 // 無密碼

// Web Server
WebServer server(80);

// 導航資訊結構
struct NavigationData {
  String turnDirection;
  String direction;
  String turnDistance;
  String iconData;
  unsigned long lastUpdate;
  bool hasData;
};

NavigationData navData = {"", "", "", "", 0, false};

// HTML 網頁模板
const char* htmlTemplate = R"rawliteral(
<!DOCTYPE html>
<html lang="zh-TW">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>ESP32 導航資訊</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: 'Microsoft JhengHei', 'Segoe UI', Arial, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            padding: 20px;
        }
        .container {
            max-width: 800px;
            margin: 0 auto;
        }
        .header {
            background: white;
            border-radius: 15px;
            padding: 30px;
            text-align: center;
            box-shadow: 0 10px 30px rgba(0,0,0,0.2);
            margin-bottom: 20px;
        }
        .header h1 {
            color: #667eea;
            font-size: 2em;
            margin-bottom: 10px;
        }
        .status {
            display: inline-block;
            padding: 8px 20px;
            border-radius: 20px;
            font-size: 0.9em;
            font-weight: bold;
        }
        .status.connected {
            background: #10b981;
            color: white;
        }
        .status.waiting {
            background: #f59e0b;
            color: white;
        }
        .nav-card {
            background: white;
            border-radius: 15px;
            padding: 30px;
            box-shadow: 0 10px 30px rgba(0,0,0,0.2);
            margin-bottom: 20px;
        }
        .nav-item {
            margin-bottom: 20px;
            padding-bottom: 20px;
            border-bottom: 2px solid #f0f0f0;
        }
        .nav-item:last-child {
            border-bottom: none;
            margin-bottom: 0;
            padding-bottom: 0;
        }
        .nav-label {
            color: #6b7280;
            font-size: 0.9em;
            font-weight: 600;
            margin-bottom: 8px;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }
        .nav-value {
            color: #1f2937;
            font-size: 1.5em;
            font-weight: bold;
        }
        .direction-icon {
            display: inline-block;
            width: 60px;
            height: 60px;
            background: #667eea;
            color: white;
            border-radius: 50%;
            line-height: 60px;
            text-align: center;
            font-size: 2em;
            margin-right: 15px;
            vertical-align: middle;
        }
        .info-box {
            background: #f3f4f6;
            border-radius: 10px;
            padding: 20px;
            margin-top: 20px;
        }
        .info-box h3 {
            color: #374151;
            margin-bottom: 15px;
            font-size: 1.1em;
        }
        .info-item {
            color: #6b7280;
            margin: 8px 0;
            font-size: 0.95em;
        }
        .info-item strong {
            color: #1f2937;
        }
        .no-data {
            text-align: center;
            color: #9ca3af;
            padding: 40px;
            font-size: 1.1em;
        }
        .refresh-btn {
            background: #667eea;
            color: white;
            border: none;
            padding: 12px 30px;
            border-radius: 25px;
            font-size: 1em;
            font-weight: bold;
            cursor: pointer;
            margin-top: 20px;
            transition: background 0.3s;
        }
        .refresh-btn:hover {
            background: #5568d3;
        }
        .last-update {
            color: #9ca3af;
            font-size: 0.85em;
            margin-top: 15px;
            text-align: center;
        }
        @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.5; }
        }
        .loading {
            animation: pulse 2s ease-in-out infinite;
        }
    </style>
    <script>
        function refreshData() {
            location.reload();
        }
        
        // 自動刷新（每3秒）
        setTimeout(refreshData, 3000);
    </script>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🧭 ESP32 導航資訊</h1>
            <p style="margin: 10px 0 15px 0; color: #6b7280;">Google Maps 即時導航顯示</p>
            <span class="status %STATUS_CLASS%">%STATUS_TEXT%</span>
        </div>
        
        <div class="nav-card">
            %CONTENT%
        </div>
        
        <div class="nav-card">
            <div class="info-box">
                <h3>📡 系統資訊</h3>
                <div class="info-item"><strong>WiFi 熱點：</strong> ESP32-Navigation</div>
                <div class="info-item"><strong>IP 位址：</strong> 192.168.4.1</div>
                <div class="info-item"><strong>Serial 鮑率：</strong> 115200</div>
                <div class="info-item"><strong>自動刷新：</strong> 每 3 秒</div>
            </div>
        </div>
        
        <div style="text-align: center;">
            <button class="refresh-btn" onclick="refreshData()">🔄 立即刷新</button>
        </div>
    </div>
</body>
</html>
)rawliteral";

// 將方向代碼轉換為圖示
String getDirectionIcon(String direction) {
  if (direction == "left") return "⬅️";
  if (direction == "right") return "➡️";
  if (direction == "straight") return "⬆️";
  if (direction == "GoStraight") return "⬆️";
  if (direction == "side_left") return "↖️";
  if (direction == "side_right") return "↗️";
  if (direction == "ForkLeft") return "↖️";
  if (direction == "ForkRight") return "↗️";
  if (direction == "ExitRight") return "↗️";
  if (direction == "SharpTurnLeft") return "↩️";
  if (direction == "SharpTurnRight") return "↪️";
  if (direction == "Roundabout") return "🔄";
  if (direction == "Exit1st") return "🔄";
  if (direction == "Exit2nd") return "🔄";
  if (direction == "UTurnLeft") return "↩️";
  if (direction == "DestinationLeft") return "🏁";
  if (direction == "DestinationRight") return "🏁";
  if (direction == "DestinationFront") return "🏁";
  return "📍";
}

// 生成網頁內容
String generateHTML() {
  String html = htmlTemplate;
  
  // 設定狀態
  if (navData.hasData && (millis() - navData.lastUpdate < 30000)) {
    html.replace("%STATUS_CLASS%", "connected");
    html.replace("%STATUS_TEXT%", "✓ 已連線");
  } else {
    html.replace("%STATUS_CLASS%", "waiting");
    html.replace("%STATUS_TEXT%", "○ 等待資料");
  }
  
  // 生成內容
  String content = "";
  
  if (navData.hasData) {
    String icon = getDirectionIcon(navData.turnDirection);
    
    content += "<div class='nav-item'>";
    content += "  <div class='nav-label'>轉彎方向</div>";
    content += "  <div class='nav-value'>";
    content += "    <span class='direction-icon'>" + icon + "</span>";
    content += "    <span>" + navData.turnDirection + "</span>";
    content += "  </div>";
    content += "</div>";
    
    if (navData.direction.length() > 0) {
      content += "<div class='nav-item'>";
      content += "  <div class='nav-label'>指示</div>";
      content += "  <div class='nav-value'>" + navData.direction + "</div>";
      content += "</div>";
    }
    
    if (navData.turnDistance.length() > 0) {
      content += "<div class='nav-item'>";
      content += "  <div class='nav-label'>距離</div>";
      content += "  <div class='nav-value'>" + navData.turnDistance + "</div>";
      content += "</div>";
    }
    
    // 顯示最後更新時間
    unsigned long seconds = (millis() - navData.lastUpdate) / 1000;
    content += "<div class='last-update'>";
    content += "最後更新：" + String(seconds) + " 秒前";
    content += "</div>";
    
  } else {
    content += "<div class='no-data loading'>";
    content += "  <p>⏳ 等待接收導航資訊...</p>";
    content += "  <p style='font-size: 0.9em; margin-top: 10px;'>請確認 Android 裝置已透過 USB 連接</p>";
    content += "</div>";
  }
  
  html.replace("%CONTENT%", content);
  
  return html;
}

// 處理根路徑請求
void handleRoot() {
  server.send(200, "text/html", generateHTML());
}

// 處理 API 請求（返回 JSON）
void handleAPI() {
  StaticJsonDocument<512> doc;
  
  doc["hasData"] = navData.hasData;
  doc["turnDirection"] = navData.turnDirection;
  doc["direction"] = navData.direction;
  doc["turnDistance"] = navData.turnDistance;
  doc["lastUpdate"] = navData.lastUpdate;
  doc["secondsAgo"] = (millis() - navData.lastUpdate) / 1000;
  
  String json;
  serializeJson(doc, json);
  
  server.send(200, "application/json", json);
}

// 處理 404
void handleNotFound() {
  String message = "404 Not Found\n\n";
  message += "URI: " + server.uri() + "\n";
  server.send(404, "text/plain", message);
}

void setup() {
  // 初始化 Serial（用於接收 Android 資料）
  Serial.begin(115200);
  Serial.println("\n\n=================================");
  Serial.println("ESP32 導航資訊接收系統");
  Serial.println("=================================");
  
  // 建立 WiFi 熱點
  Serial.println("\n正在建立 WiFi 熱點...");
  WiFi.mode(WIFI_AP);
  WiFi.softAP(ap_ssid, ap_password);
  
  IPAddress IP = WiFi.softAPIP();
  Serial.println("✓ WiFi 熱點已建立");
  Serial.println("  SSID: " + String(ap_ssid));
  Serial.println("  密碼: (無密碼)");
  Serial.println("  IP 位址: " + IP.toString());
  
  // 設定 Web Server 路由
  server.on("/", handleRoot);
  server.on("/api", handleAPI);
  server.onNotFound(handleNotFound);
  
  // 啟動 Web Server
  server.begin();
  Serial.println("\n✓ Web Server 已啟動");
  Serial.println("  請用手機/電腦連接熱點後，開啟瀏覽器訪問：");
  Serial.println("  http://" + IP.toString());
  
  Serial.println("\n=================================");
  Serial.println("等待接收導航資訊...");
  Serial.println("=================================\n");
}

void loop() {
  // 處理 Web Server 請求
  server.handleClient();
  
  // 接收並解析 Serial 資料
  if (Serial.available()) {
    String jsonString = Serial.readStringUntil('\n');
    
    // 移除可能的空白字元
    jsonString.trim();
    
    if (jsonString.length() > 0) {
      Serial.println("\n=== 收到資料 ===");
      Serial.println("長度: " + String(jsonString.length()) + " bytes");
      Serial.println("內容: " + jsonString);
      Serial.println("================");
      
      // 解析 JSON
      StaticJsonDocument<2048> doc;  // 增加容量以容納圖標資料
      DeserializationError error = deserializeJson(doc, jsonString);
      
      if (!error) {
        // 讀取所有欄位（包括可能是 null 的）
        String turnDir = doc["turnDirection"] | "";
        String direction = doc["direction"] | "";
        String distance = doc["turnDistance"] | "";
        String iconData = doc["iconData"] | "";
        
        // 更新導航資料
        navData.turnDirection = turnDir;
        navData.direction = direction;
        navData.turnDistance = distance;
        navData.iconData = iconData;
        navData.lastUpdate = millis();
        navData.hasData = true;
        
        // 顯示詳細解析結果
        Serial.println("\n--- 解析結果 ---");
        Serial.println("turnDirection: [" + turnDir + "] (" + String(turnDir.length()) + " chars)");
        Serial.println("direction: [" + direction + "] (" + String(direction.length()) + " chars)");
        Serial.println("turnDistance: [" + distance + "] (" + String(distance.length()) + " chars)");
        Serial.println("iconData 長度: " + String(iconData.length()) + " chars");
        
        // 檢查是否有空值
        if (turnDir.length() == 0) Serial.println("⚠ turnDirection 是空的");
        if (direction.length() == 0) Serial.println("⚠ direction 是空的");
        if (distance.length() == 0) Serial.println("⚠ turnDistance 是空的");
        
        Serial.println("✓ 資料已更新");
        Serial.println("---------------\n");
        
      } else {
        Serial.println("\n✗ JSON 解析失敗");
        Serial.println("錯誤代碼: " + String(error.c_str()));
        Serial.println("可能原因:");
        Serial.println("  - JSON 格式不正確");
        Serial.println("  - 資料太大（超過 2048 bytes）");
        Serial.println("  - 資料損壞");
        Serial.println("---------------\n");
      }
    }
  }
  
  // 短暫延遲避免 CPU 佔用過高
  delay(10);
}
