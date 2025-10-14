package com.andyching168.gmapmqtt

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.ViewModel

class GmapMQTTApp : Application(), ViewModelStoreOwner {
    private val _viewModelStore = ViewModelStore()
    private lateinit var usbSerialManager: UsbSerialManager
    private lateinit var usbSettingsManager: UsbSettingsManager

    private val viewModelFactory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(NavigationViewModel::class.java)) {
                return NavigationViewModel(usbSerialManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
    
    private val viewModelProvider = ViewModelProvider(this, viewModelFactory)

    fun getNavigationViewModel(): NavigationViewModel {
        return viewModelProvider[NavigationViewModel::class.java]
    }
    
    fun getUsbSerialManager(): UsbSerialManager {
        return usbSerialManager
    }
    
    fun getUsbSettingsManager(): UsbSettingsManager {
        return usbSettingsManager
    }

    override val viewModelStore: ViewModelStore
        get() = _viewModelStore

    companion object {
        private var instance: GmapMQTTApp? = null

        fun getInstance(): GmapMQTTApp {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // 初始化 USB Serial 相關組件
        usbSerialManager = UsbSerialManager(this)
        usbSettingsManager = UsbSettingsManager(this)
    }
    
    override fun onTerminate() {
        super.onTerminate()
        usbSerialManager.cleanup()
    }
}

