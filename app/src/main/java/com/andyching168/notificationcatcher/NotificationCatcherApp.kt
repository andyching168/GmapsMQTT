package com.andyching168.notificationcatcher

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

class NotificationCatcherApp : Application(), ViewModelStoreOwner {
    private val _viewModelStore = ViewModelStore()
    private val viewModelProvider = ViewModelProvider(this)

    fun getNavigationViewModel(): NavigationViewModel {
        return viewModelProvider[NavigationViewModel::class.java]
    }

    override val viewModelStore: ViewModelStore
        get() = _viewModelStore

    companion object {
        private var instance: NotificationCatcherApp? = null

        fun getInstance(): NotificationCatcherApp {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
} 