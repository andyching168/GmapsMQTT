/*
 * ESP32 除錯版本 - 用於診斷資料傳輸問題
 * 
 * 此版本會：
 * 1. 顯示所有收到的原始資料（包括不可見字元）
 * 2. 顯示每個欄位的詳細資訊
 * 3. 檢測空值和 null
 * 4. 提供詳細的 JSON 解析日誌
 */

#include <WiFi.h>
#include <WebServer.h>
#include <ArduinoJson.h>

const char* ap_ssid = "ESP32-Debug";
const char* ap_password = "";

WebServer server(80);

// 統計資訊
unsigned long totalReceived = 0;
unsigned long successParsed = 0;
unsigned long failedParsed = 0;
String lastError = "";
String lastJsonReceived = "";

void handleRoot() {
  String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'>";
  html += "<meta name='viewport' content='width=device-width, initial-scale=1.0'>";
  html += "<title>除錯資訊</title>";
  html += "<style>body{font-family:monospace;padding:20px;background:#f0f0f0;}";
  html += ".box{background:white;padding:15px;margin:10px 0;border-radius:5px;}";
  html += "h2{color:#333;margin:10px 0;}";
  html += ".good{color:green;}.bad{color:red;}.warn{color:orange;}";
  html += "pre{background:#f5f5f5;padding:10px;overflow-x:auto;font-size:12px;}";
  html += "</style>";
  html += "<script>setTimeout(function(){location.reload();},3000);</script>";
  html += "</head><body>";
  
  html += "<div class='box'><h2>📊 統計資訊</h2>";
  html += "<p>總接收: <b>" + String(totalReceived) + "</b></p>";
  html += "<p>成功解析: <b class='good'>" + String(successParsed) + "</b></p>";
  html += "<p>失敗解析: <b class='bad'>" + String(failedParsed) + "</b></p>";
  html += "</div>";
  
  if (lastError.length() > 0) {
    html += "<div class='box'><h2 class='bad'>❌ 最後錯誤</h2>";
    html += "<p>" + lastError + "</p></div>";
  }
  
  html += "<div class='box'><h2>📥 最後收到的資料</h2>";
  if (lastJsonReceived.length() > 0) {
    html += "<p>長度: " + String(lastJsonReceived.length()) + " bytes</p>";
    html += "<pre>" + lastJsonReceived + "</pre>";
  } else {
    html += "<p class='warn'>等待資料...</p>";
  }
  html += "</div>";
  
  html += "<div class='box'><p style='text-align:center;color:#666;'>自動刷新 | IP: 192.168.4.1</p></div>";
  html += "</body></html>";
  
  server.send(200, "text/html", html);
}

void setup() {
  Serial.begin(115200);
  Serial.println("\n\n╔═══════════════════════════════════════╗");
  Serial.println("║  ESP32 除錯模式 - 資料診斷工具       ║");
  Serial.println("╚═══════════════════════════════════════╝\n");
  
  WiFi.softAP(ap_ssid, ap_password);
  Serial.println("✓ WiFi 熱點: " + String(ap_ssid));
  Serial.println("✓ IP 位址: " + WiFi.softAPIP().toString());
  
  server.on("/", handleRoot);
  server.begin();
  Serial.println("✓ Web Server 啟動");
  
  Serial.println("\n開始監聽 Serial 資料...\n");
  Serial.println("提示：");
  Serial.println("  - 每次收到資料會顯示完整內容");
  Serial.println("  - 會標示不可見字元（\\n, \\r 等）");
  Serial.println("  - 會檢測 null 值和空字串");
  Serial.println("  - 可在網頁上查看統計資訊\n");
  Serial.println("═══════════════════════════════════════\n");
}

void printHexDump(String data) {
  Serial.print("HEX: ");
  for (int i = 0; i < min(100, (int)data.length()); i++) {
    char c = data[i];
    if (c < 16) Serial.print("0");
    Serial.print(c, HEX);
    Serial.print(" ");
  }
  if (data.length() > 100) Serial.print("...");
  Serial.println();
}

void loop() {
  server.handleClient();
  
  if (Serial.available()) {
    String jsonString = Serial.readStringUntil('\n');
    totalReceived++;
    lastJsonReceived = jsonString;
    
    // 移除可能的空白字元
    jsonString.trim();
    
    if (jsonString.length() > 0) {
      Serial.println("╔════════════════════════════════════════════════════");
      Serial.println("║ 收到資料 #" + String(totalReceived));
      Serial.println("╠════════════════════════════════════════════════════");
      Serial.println("║ 長度: " + String(jsonString.length()) + " bytes");
      Serial.println("╠════════════════════════════════════════════════════");
      Serial.println("║ 原始內容:");
      Serial.println(jsonString);
      Serial.println("╠════════════════════════════════════════════════════");
      printHexDump(jsonString);
      Serial.println("╠════════════════════════════════════════════════════");
      
      // 解析 JSON
      StaticJsonDocument<2048> doc;
      DeserializationError error = deserializeJson(doc, jsonString);
      
      if (!error) {
        successParsed++;
        Serial.println("║ ✓ JSON 解析成功");
        Serial.println("╠════════════════════════════════════════════════════");
        
        // 檢查每個欄位
        Serial.println("║ 欄位檢查:");
        
        // turnDirection
        if (doc.containsKey("turnDirection")) {
          if (doc["turnDirection"].isNull()) {
            Serial.println("║   turnDirection: <NULL>");
          } else {
            String val = doc["turnDirection"].as<String>();
            Serial.println("║   turnDirection: \"" + val + "\" (" + String(val.length()) + " chars)");
          }
        } else {
          Serial.println("║   turnDirection: <不存在>");
        }
        
        // direction
        if (doc.containsKey("direction")) {
          if (doc["direction"].isNull()) {
            Serial.println("║   direction: <NULL>");
          } else {
            String val = doc["direction"].as<String>();
            Serial.println("║   direction: \"" + val + "\" (" + String(val.length()) + " chars)");
          }
        } else {
          Serial.println("║   direction: <不存在>");
        }
        
        // turnDistance
        if (doc.containsKey("turnDistance")) {
          if (doc["turnDistance"].isNull()) {
            Serial.println("║   turnDistance: <NULL>");
          } else {
            String val = doc["turnDistance"].as<String>();
            Serial.println("║   turnDistance: \"" + val + "\" (" + String(val.length()) + " chars)");
          }
        } else {
          Serial.println("║   turnDistance: <不存在>");
        }
        
        // iconData
        if (doc.containsKey("iconData")) {
          if (doc["iconData"].isNull()) {
            Serial.println("║   iconData: <NULL>");
          } else {
            String val = doc["iconData"].as<String>();
            Serial.println("║   iconData: " + String(val.length()) + " chars");
          }
        } else {
          Serial.println("║   iconData: <不存在>");
        }
        
        Serial.println("╠════════════════════════════════════════════════════");
        Serial.println("║ JSON 結構檢視:");
        serializeJsonPretty(doc, Serial);
        Serial.println();
        
        lastError = "";
        
      } else {
        failedParsed++;
        Serial.println("║ ✗ JSON 解析失敗");
        Serial.println("╠════════════════════════════════════════════════════");
        Serial.println("║ 錯誤: " + String(error.c_str()));
        Serial.println("║");
        Serial.println("║ 可能原因:");
        
        if (error == DeserializationError::InvalidInput) {
          Serial.println("║   - JSON 格式錯誤（語法錯誤）");
          Serial.println("║   - 缺少引號、括號不匹配等");
        } else if (error == DeserializationError::NoMemory) {
          Serial.println("║   - 資料太大（超過 2048 bytes）");
          Serial.println("║   - 需要增加 StaticJsonDocument 的容量");
        } else if (error == DeserializationError::IncompleteInput) {
          Serial.println("║   - 資料不完整");
          Serial.println("║   - 可能在傳輸中被截斷");
        }
        
        lastError = String(error.c_str());
      }
      
      Serial.println("╚════════════════════════════════════════════════════\n");
    }
  }
  
  delay(10);
}
