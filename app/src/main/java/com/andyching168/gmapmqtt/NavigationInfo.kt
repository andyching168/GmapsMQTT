package com.andyching168.gmapmqtt

data class NavigationInfo(
    val direction: String = "",
    val totalDistance: String = "",  // 剩餘總距離
    val turnDistance: String = "",   // 轉彎距離
    val duration: String = "",
    val eta: String = "",
    val status: String = "",
    val isRerouting: Boolean = false,
    val hasNotification: Boolean = false,
    val iconResId: Int = 0,
    val turnDirection: String = ""   // 轉彎方向
)

