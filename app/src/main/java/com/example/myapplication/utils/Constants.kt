package com.example.myapplication.utils

import java.util.*

object Constants {
    // BLE Configuration
    const val DEVICE_NAME = "Massage_Pro_X1"

    val SERVICE_UUID: UUID = UUID.fromString("12345678-1234-5678-1234-56789ABCDEF0")
    val CONTROL_CHAR_UUID: UUID = UUID.fromString("ABCDEF01-1234-5678-1234-56789ABCDEF0")

    // BLE Commands
    object BleCommands {
        const val ROTATE: Byte = 0x01
        const val HEAT: Byte = 0x02
        const val LEVEL: Byte = 0x03
        const val ASSISTANT_CONFIG: Byte = 0x05
        const val ASSISTANT_STOP: Byte = 0x06
        const val AUDIO_VOLUME: Byte = 0x07
        const val AUDIO_MUTE: Byte = 0x08
    }

    // Massage Settings
    const val MIN_LEVEL = 0
    const val MAX_LEVEL = 5
    const val MIN_DURATION = 10
    const val MAX_DURATION = 30

    // Request Codes
    const val ASSISTANT_REQUEST_CODE = 1001
}