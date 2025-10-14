/*
 * ESP32 導航資訊接收 - 簡化版
 * 
 * 極簡版本：只顯示最基本的導航資訊
 * 適合初學者或只需要基本功能的使用者
 */

#include <WiFi.h>
#include <WebServer.h>
#include <ArduinoJson.h>

// WiFi 設定（可修改）
const char* ap_ssid = "ESP32-Nav";
const char* ap_password = "";  // 無密碼

WebServer server(80);

// 儲存導航資訊
String currentDirection = "等待資料...";
String currentDistance = "---";

// 簡單的網頁
const char* html = R"(
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>導航</title>
    <style>
        body { 
            font-family: Arial; 
            text-align: center; 
            padding: 20px;
            background: #f0f0f0;
        }
        .box {
            background: white;
            border-radius: 10px;
            padding: 30px;
            margin: 20px auto;
            max-width: 400px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        .direction {
            font-size: 3em;
            margin: 20px 0;
        }
        .distance {
            font-size: 2em;
            color: #666;
        }
    </style>
    <script>
        setTimeout(function(){ location.reload(); }, 2000);
    </script>
</head>
<body>
    <div class="box">
        <h1>導航資訊</h1>
        <div class="direction">%DIRECTION%</div>
        <div class="distance">%DISTANCE%</div>
    </div>
    <div class="box">
        <small>IP: 192.168.4.1 | 每2秒自動更新</small>
    </div>
</body>
</html>
)";

void handleRoot() {
  String page = html;
  page.replace("%DIRECTION%", currentDirection);
  page.replace("%DISTANCE%", currentDistance);
  server.send(200, "text/html", page);
}

void setup() {
  Serial.begin(115200);
  
  // 建立熱點
  WiFi.softAP(ap_ssid, ap_password);
  Serial.println("WiFi: " + String(ap_ssid));
  Serial.println("IP: " + WiFi.softAPIP().toString());
  
  // 啟動網頁
  server.on("/", handleRoot);
  server.begin();
  
  Serial.println("準備就緒！");
}

void loop() {
  server.handleClient();
  
  if (Serial.available()) {
    String json = Serial.readStringUntil('\n');
    json.trim();
    
    if (json.length() > 0) {
      // 先顯示收到的原始資料
      Serial.println("\n=== 收到資料 ===");
      Serial.println(json);
      Serial.println("================");
      
      StaticJsonDocument<1024> doc;  // 增加記憶體容量
      DeserializationError error = deserializeJson(doc, json);
      
      if (error) {
        Serial.print("JSON 解析錯誤: ");
        Serial.println(error.c_str());
      } else {
        // 嘗試多種可能的欄位名稱
        String direction = "";
        String distance = "";
        String turnDir = "";
        
        // 讀取所有可能的欄位
        if (doc.containsKey("direction")) {
          direction = doc["direction"].as<String>();
        }
        if (doc.containsKey("turnDistance")) {
          distance = doc["turnDistance"].as<String>();
        }
        if (doc.containsKey("turnDirection")) {
          turnDir = doc["turnDirection"].as<String>();
        }
        
        // 顯示解析結果
        Serial.println("\n--- 解析結果 ---");
        Serial.println("turnDirection: " + turnDir);
        Serial.println("direction: " + direction);
        Serial.println("turnDistance: " + distance);
        Serial.println("---------------\n");
        
        // 更新顯示（優先使用有內容的欄位）
        if (direction.length() > 0) {
          currentDirection = direction;
        } else if (turnDir.length() > 0) {
          currentDirection = turnDir;
        }
        
        if (distance.length() > 0) {
          currentDistance = distance;
        }
        
        // 如果兩個都是空的，顯示轉彎方向
        if (currentDirection.length() == 0 || currentDirection == "null") {
          currentDirection = turnDir.length() > 0 ? turnDir : "等待資料...";
        }
        if (currentDistance.length() == 0 || currentDistance == "null") {
          currentDistance = "---";
        }
      }
    }
  }
  
  delay(10);
}
