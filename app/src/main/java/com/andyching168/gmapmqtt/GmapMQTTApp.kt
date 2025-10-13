package com.andyching168.gmapmqtt

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

class GmapMQTTApp : Application(), ViewModelStoreOwner {
    private val _viewModelStore = ViewModelStore()
    private val viewModelProvider = ViewModelProvider(this)

    fun getNavigationViewModel(): NavigationViewModel {
        return viewModelProvider[NavigationViewModel::class.java]
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
    }
}

