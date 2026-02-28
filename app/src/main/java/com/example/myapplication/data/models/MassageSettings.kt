package com.example.myapplication.data.models

data class MassageSettings(
    val level: Int = 3,
    val duration: Int = 10,
    val useHeat: Boolean = false,
    val isRotating: Boolean = false,
    val volume: Int = 50,
    val isMuted: Boolean = false
)

data class MassageRecommendation(
    val level: Int,
    val duration: Int,
    val useHeat: Boolean,
    val mode: String,
    val explanation: String
)

data class DeviceInfo(
    val name: String = "",
    val batteryLevel: Int = 0,
    val isConnected: Boolean = false
)
