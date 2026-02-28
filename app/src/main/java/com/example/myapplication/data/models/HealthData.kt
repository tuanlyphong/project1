package com.example.myapplication.data.models

data class HealthData(
    val heartRate: Int = 0,
    val spO2: Int = 0,
    val irValue: Float = 0f,
    val redValue: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

data class WaveformData(
    val value: Float,
    val timestamp: Long = System.currentTimeMillis()
)
