package com.andyching168.gmapmqtt

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// DataStore 擴展
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mqtt_settings")

data class MqttConfig(
    val brokerUrl: String = "",
    val username: String = "",
    val password: String = "",
    val topic: String = "navigation/info",
    val autoConnect: Boolean = false,
    val port: Int = 1883
)

class MqttSettingsManager(private val context: Context) {
    
    companion object {
        private val BROKER_URL_KEY = stringPreferencesKey("broker_url")
        private val USERNAME_KEY = stringPreferencesKey("username")
        private val PASSWORD_KEY = stringPreferencesKey("password")
        private val TOPIC_KEY = stringPreferencesKey("topic")
        private val AUTO_CONNECT_KEY = booleanPreferencesKey("auto_connect")
        private val PORT_KEY = stringPreferencesKey("port")
    }
    
    val mqttConfigFlow: Flow<MqttConfig> = context.dataStore.data.map { preferences ->
        MqttConfig(
            brokerUrl = preferences[BROKER_URL_KEY] ?: "",
            username = preferences[USERNAME_KEY] ?: "",
            password = preferences[PASSWORD_KEY] ?: "",
            topic = preferences[TOPIC_KEY] ?: "navigation/info",
            autoConnect = preferences[AUTO_CONNECT_KEY] ?: false,
            port = preferences[PORT_KEY]?.toIntOrNull() ?: 1883
        )
    }
    
    suspend fun saveMqttConfig(config: MqttConfig) {
        context.dataStore.edit { preferences ->
            preferences[BROKER_URL_KEY] = config.brokerUrl
            preferences[USERNAME_KEY] = config.username
            preferences[PASSWORD_KEY] = config.password
            preferences[TOPIC_KEY] = config.topic
            preferences[AUTO_CONNECT_KEY] = config.autoConnect
            preferences[PORT_KEY] = config.port.toString()
        }
    }
    
    suspend fun updateAutoConnect(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_CONNECT_KEY] = enabled
        }
    }
}
