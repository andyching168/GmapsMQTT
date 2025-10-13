package com.andyching168.gmapmqtt

import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.util.UUID

enum class MqttConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

class MqttClientManager {
    private var mqttClient: Mqtt3AsyncClient? = null
    private var currentConfig: MqttConfig? = null
    
    private val _connectionState = MutableStateFlow(MqttConnectionState.DISCONNECTED)
    val connectionState: StateFlow<MqttConnectionState> = _connectionState.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    companion object {
        private const val TAG = "MqttClientManager"
    }
    
    fun connect(config: MqttConfig) {
        if (config.brokerUrl.isEmpty()) {
            Log.w(TAG, "Broker URL is empty, skipping connection")
            _errorMessage.value = "Broker URL 不能為空"
            return
        }
        
        try {
            _connectionState.value = MqttConnectionState.CONNECTING
            _errorMessage.value = null
            currentConfig = config
            
            // 建立 MQTT 客戶端
            val clientBuilder = MqttClient.builder()
                .useMqttVersion3()
                .identifier("GmapMQTT_${UUID.randomUUID()}")
                .serverHost(config.brokerUrl)
                .serverPort(config.port)
                .automaticReconnect()
                    .initialDelay(1, java.util.concurrent.TimeUnit.SECONDS)
                    .maxDelay(30, java.util.concurrent.TimeUnit.SECONDS)
                    .applyAutomaticReconnect()
            
            mqttClient = clientBuilder.buildAsync()
            
            // 添加連線狀態監聽器
            mqttClient?.toAsync()?.let { client ->
                // 註冊連線狀態變化監聽
                Log.d(TAG, "MQTT client created, initial state: ${client.state}")
            }
            
            // 設定連線選項
            val connectBuilder = mqttClient!!.connectWith()
            
            // 如果有帳號密碼，則設定
            if (config.username.isNotEmpty()) {
                connectBuilder.simpleAuth()
                    .username(config.username)
                    .password(config.password.toByteArray(StandardCharsets.UTF_8))
                    .applySimpleAuth()
            }
            
            // 執行連線
            connectBuilder.send()
                .whenComplete { _, throwable ->
                    if (throwable != null) {
                        Log.e(TAG, "MQTT connection failed", throwable)
                        _connectionState.value = MqttConnectionState.ERROR
                        _errorMessage.value = "連線失敗: ${throwable.message}"
                    } else {
                        // 確認客戶端狀態
                        val actualState = mqttClient?.state
                        if (actualState == com.hivemq.client.mqtt.MqttClientState.CONNECTED) {
                            Log.i(TAG, "MQTT connected successfully, client state: $actualState")
                            _connectionState.value = MqttConnectionState.CONNECTED
                            _errorMessage.value = null
                        } else {
                            Log.w(TAG, "Connection callback but client state is: $actualState")
                            _connectionState.value = MqttConnectionState.CONNECTING
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up MQTT client", e)
            _connectionState.value = MqttConnectionState.ERROR
            _errorMessage.value = "設定錯誤: ${e.message}"
        }
    }
    
    fun disconnect() {
        try {
            mqttClient?.disconnect()?.whenComplete { _, throwable ->
                if (throwable != null) {
                    Log.e(TAG, "MQTT disconnection failed", throwable)
                } else {
                    Log.i(TAG, "MQTT disconnected successfully")
                }
                _connectionState.value = MqttConnectionState.DISCONNECTED
                mqttClient = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting MQTT client", e)
            _connectionState.value = MqttConnectionState.DISCONNECTED
        }
    }
    
    fun publish(topic: String, message: String) {
        val client = mqttClient
        if (client == null) {
            Log.w(TAG, "Cannot publish: client is null")
            return
        }
        
        // 檢查客戶端的實際連線狀態
        val state = client.state
        if (state != com.hivemq.client.mqtt.MqttClientState.CONNECTED) {
            Log.w(TAG, "Cannot publish: client state is $state, not CONNECTED")
            return
        }
        
        try {
            client.publishWith()
                .topic(topic)
                .payload(message.toByteArray(StandardCharsets.UTF_8))
                .send()
                .whenComplete { _, throwable ->
                    if (throwable != null) {
                        Log.e(TAG, "Failed to publish message", throwable)
                    } else {
                        Log.d(TAG, "Message published to $topic: $message")
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error publishing message", e)
        }
    }
    
    fun isConnected(): Boolean {
        val client = mqttClient
        return client != null && client.state == com.hivemq.client.mqtt.MqttClientState.CONNECTED
    }
    
    fun getCurrentTopic(): String {
        return currentConfig?.topic ?: ""
    }
}
