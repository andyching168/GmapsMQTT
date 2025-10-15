package com.andyching168.gmapmqtt

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

enum class UsbConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class UsbDeviceInfo(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val device: UsbDevice
)

class UsbSerialManager(private val context: Context) {
    private var usbManager: UsbManager? = null
    private var serialPort: UsbSerialPort? = null
    private var connection: UsbDeviceConnection? = null
    private var currentDevice: UsbDevice? = null
    private var currentConfig: UsbConfig? = null
    
    private val _connectionState = MutableStateFlow(UsbConnectionState.DISCONNECTED)
    val connectionState: StateFlow<UsbConnectionState> = _connectionState.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _availableDevices = MutableStateFlow<List<UsbDeviceInfo>>(emptyList())
    val availableDevices: StateFlow<List<UsbDeviceInfo>> = _availableDevices.asStateFlow()
    
    companion object {
        private const val TAG = "UsbSerialManager"
        private const val ACTION_USB_PERMISSION = "com.andyching168.gmapmqtt.USB_PERMISSION"
    }
    
    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let {
                            Log.d(TAG, "USB 權限已授予")
                            connectToDevice(it, currentConfig!!)
                        }
                    } else {
                        Log.w(TAG, "USB 權限被拒絕")
                        _connectionState.value = UsbConnectionState.ERROR
                        _errorMessage.value = "USB 權限被拒絕"
                    }
                }
            }
        }
    }
    
    init {
        usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        
        // 註冊 USB 權限接收器
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbPermissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(usbPermissionReceiver, filter)
        }
    }
    
    fun scanDevices() {
        try {
            val manager = usbManager ?: run {
                Log.e(TAG, "UsbManager 未初始化")
                _errorMessage.value = "UsbManager 未初始化"
                return
            }

            // 先获取所有 USB 设备（包括没有驱动的）
            val allDevices = manager.deviceList
            Log.d(TAG, "系統檢測到 ${allDevices.size} 個 USB 裝置")

            // 列出所有设备的详细信息
            allDevices.values.forEach { device ->
                Log.d(TAG, "USB 裝置: ${device.deviceName}, VID: ${device.vendorId}, PID: ${device.productId}")
            }

            // 使用 UsbSerialProber 查找支持的驱动
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
            
            val deviceList = availableDrivers.map { driver ->
                val device = driver.device
                UsbDeviceInfo(
                    deviceName = device.deviceName,
                    vendorId = device.vendorId,
                    productId = device.productId,
                    device = device
                )
            }
            
            _availableDevices.value = deviceList
            Log.d(TAG, "找到 ${deviceList.size} 個支援的 USB Serial 裝置")

            if (deviceList.isEmpty() && allDevices.isNotEmpty()) {
                _errorMessage.value = "找到 ${allDevices.size} 個 USB 裝置，但沒有支援的 Serial 驅動"
            }
        } catch (e: Exception) {
            Log.e(TAG, "掃描 USB 裝置失敗", e)
            _errorMessage.value = "掃描裝置失敗: ${e.message}"
        }
    }
    
    fun connect(device: UsbDevice, config: UsbConfig) {
        currentConfig = config
        val manager = usbManager ?: return
        
        if (!manager.hasPermission(device)) {
            Log.d(TAG, "請求 USB 權限")
            // 使用 FLAG_IMMUTABLE 以符合 Android U+ 的安全要求
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }
            val permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION),
                flags
            )
            manager.requestPermission(device, permissionIntent)
            return
        }
        
        connectToDevice(device, config)
    }
    
    private fun connectToDevice(device: UsbDevice, config: UsbConfig) {
        try {
            _connectionState.value = UsbConnectionState.CONNECTING
            _errorMessage.value = null
            currentDevice = device
            
            val manager = usbManager ?: throw IOException("UsbManager 未初始化")
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
            val driver = availableDrivers.firstOrNull { it.device == device }
                ?: throw IOException("找不到對應的 USB Serial 驅動")
            
            connection = manager.openDevice(driver.device)
            if (connection == null) {
                throw IOException("無法開啟 USB 裝置")
            }
            
            serialPort = driver.ports[0] // 使用第一個 port
            serialPort?.open(connection)
            serialPort?.setParameters(
                config.baudRate,
                config.dataBits,
                config.stopBits,
                config.parity
            )
            
            _connectionState.value = UsbConnectionState.CONNECTED
            Log.i(TAG, "USB Serial 連線成功: ${device.deviceName}, 鮑率: ${config.baudRate}")
            
        } catch (e: Exception) {
            Log.e(TAG, "USB Serial 連線失敗", e)
            _connectionState.value = UsbConnectionState.ERROR
            _errorMessage.value = "連線失敗: ${e.message}"
            disconnect()
        }
    }
    
    fun disconnect() {
        try {
            serialPort?.close()
            connection?.close()
            serialPort = null
            connection = null
            currentDevice = null
            _connectionState.value = UsbConnectionState.DISCONNECTED
            Log.i(TAG, "USB Serial 已斷線")
        } catch (e: Exception) {
            Log.e(TAG, "斷線時發生錯誤", e)
        }
    }
    
    fun send(data: String) {
        val port = serialPort
        if (port == null || !isConnected()) {
            Log.w(TAG, "無法發送: 未連線")
            return
        }
        
        try {
            val bytes = data.toByteArray(Charsets.UTF_8)
            port.write(bytes, 1000) // 1 秒超時
            Log.d(TAG, "成功發送資料 (${bytes.size} bytes): ${data.take(100)}...")
        } catch (e: Exception) {
            Log.e(TAG, "發送資料失敗", e)
            _errorMessage.value = "發送失敗: ${e.message}"
        }
    }
    
    fun isConnected(): Boolean {
        return serialPort?.isOpen == true && _connectionState.value == UsbConnectionState.CONNECTED
    }
    
    fun getCurrentDeviceName(): String {
        return currentDevice?.deviceName ?: ""
    }
    
    fun cleanup() {
        try {
            context.unregisterReceiver(usbPermissionReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "取消註冊接收器失敗", e)
        }
        disconnect()
    }
}
