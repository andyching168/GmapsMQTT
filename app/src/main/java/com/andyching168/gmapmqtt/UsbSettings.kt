package com.andyching168.gmapmqtt

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// DataStore 擴展
private val Context.usbDataStore: DataStore<Preferences> by preferencesDataStore(name = "usb_settings")

data class UsbConfig(
    val deviceName: String = "",
    val baudRate: Int = 115200,
    val dataBits: Int = 8,
    val stopBits: Int = 1,
    val parity: Int = 0, // 0=None, 1=Odd, 2=Even
    val autoConnect: Boolean = false,
    val vendorId: Int = -1,
    val productId: Int = -1
)

class UsbSettingsManager(private val context: Context) {
    
    companion object {
        private val DEVICE_NAME_KEY = stringPreferencesKey("device_name")
        private val BAUD_RATE_KEY = intPreferencesKey("baud_rate")
        private val DATA_BITS_KEY = intPreferencesKey("data_bits")
        private val STOP_BITS_KEY = intPreferencesKey("stop_bits")
        private val PARITY_KEY = intPreferencesKey("parity")
        private val AUTO_CONNECT_KEY = booleanPreferencesKey("auto_connect")
        private val VENDOR_ID_KEY = intPreferencesKey("vendor_id")
        private val PRODUCT_ID_KEY = intPreferencesKey("product_id")
    }
    
    val usbConfigFlow: Flow<UsbConfig> = context.usbDataStore.data.map { preferences ->
        UsbConfig(
            deviceName = preferences[DEVICE_NAME_KEY] ?: "",
            baudRate = preferences[BAUD_RATE_KEY] ?: 115200,
            dataBits = preferences[DATA_BITS_KEY] ?: 8,
            stopBits = preferences[STOP_BITS_KEY] ?: 1,
            parity = preferences[PARITY_KEY] ?: 0,
            autoConnect = preferences[AUTO_CONNECT_KEY] ?: false,
            vendorId = preferences[VENDOR_ID_KEY] ?: -1,
            productId = preferences[PRODUCT_ID_KEY] ?: -1
        )
    }
    
    suspend fun saveUsbConfig(config: UsbConfig) {
        context.usbDataStore.edit { preferences ->
            preferences[DEVICE_NAME_KEY] = config.deviceName
            preferences[BAUD_RATE_KEY] = config.baudRate
            preferences[DATA_BITS_KEY] = config.dataBits
            preferences[STOP_BITS_KEY] = config.stopBits
            preferences[PARITY_KEY] = config.parity
            preferences[AUTO_CONNECT_KEY] = config.autoConnect
            preferences[VENDOR_ID_KEY] = config.vendorId
            preferences[PRODUCT_ID_KEY] = config.productId
        }
    }
}
