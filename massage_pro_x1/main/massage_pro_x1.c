/*
 * Massage Pro X1 - Main Application
 * Smart Massage Device with BLE Control, Health Monitoring & AI Assistant
 */

#include <stdio.h>
#include <string.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_log.h"
#include "nvs_flash.h"

// Component headers
#include "device_state.h"
#include "ble_server.h"
#include "motor_control.h"
#include "audio_control.h"
#include "assistant_handler.h"
#include "commands.h"

// Logging tag
static const char *TAG = "MAIN";

// Global device state - DEFINE it here (not just declare)
device_state_t device_state = {
    .intensity_level = 0,
    .heat_on = false,
    .rotate_on = false,
    .audio_volume = 70,
    .audio_muted = false,
    .assistant_active = false,
    .assistant_time_remaining = 0,
    .uptime_seconds = 0,
    .battery_charging = false,
    .battery_level = 100,
};

//-----------------------------------------------------------------------------
// Initialization Functions
//-----------------------------------------------------------------------------

/**
 * @brief Initialize NVS (Non-Volatile Storage)
 */
static esp_err_t init_nvs(void) {
    ESP_LOGI(TAG, "Initializing NVS...");
    
    esp_err_t ret = nvs_flash_init();
    if (ret == ESP_ERR_NVS_NO_FREE_PAGES || ret == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_LOGW(TAG, "NVS partition was truncated, erasing...");
        ESP_ERROR_CHECK(nvs_flash_erase());
        ret = nvs_flash_init();
    }
    
    if (ret == ESP_OK) {
        ESP_LOGI(TAG, "✓ NVS initialized successfully");
    }
    return ret;
}

/**
 * @brief Initialize all hardware peripherals
 */
static esp_err_t init_hardware(void) {
    ESP_LOGI(TAG, "Initializing hardware peripherals...");
    esp_err_t ret;
    
    // Initialize motor control
    ESP_LOGI(TAG, "  - Motor control...");
    ret = motor_control_init();
    if (ret != ESP_OK) {
        ESP_LOGE(TAG, "✗ Motor control init failed: %s", esp_err_to_name(ret));
        return ret;
    }
    ESP_LOGI(TAG, "  ✓ Motor control ready");
    
   
    // Initialize audio system
    ESP_LOGI(TAG, "  - Audio system...");
    ret = audio_init();
    if (ret == ESP_OK) {
        ESP_LOGI(TAG, "  ✓ Audio system ready");
        audio_notify(AUDIO_NOTIFY_STARTUP);
    } else {
        ESP_LOGW(TAG, "  ⚠ Audio init failed (non-critical)");
    }
    
    ESP_LOGI(TAG, "✓ Hardware initialization complete");
    return ESP_OK;
}

/**
 * @brief Initialize BLE GATT server
 */
static esp_err_t init_bluetooth(void) {
    ESP_LOGI(TAG, "Initializing Bluetooth...");
    
    esp_err_t ret = ble_server_init();
    if (ret == ESP_OK) {
        ESP_LOGI(TAG, "✓ Bluetooth initialized successfully");
    } else {
        ESP_LOGE(TAG, "✗ Bluetooth init failed: %s", esp_err_to_name(ret));
    }
    
    return ret;
}

/**
 * @brief Initialize AI assistant system
 */
static esp_err_t init_assistant(void) {
    ESP_LOGI(TAG, "Initializing AI Assistant...");
    
    assistant_init();
    ESP_LOGI(TAG, "✓ AI Assistant ready");
    
    return ESP_OK;
}

//-----------------------------------------------------------------------------
// Main Application Entry Point
//-----------------------------------------------------------------------------

void app_main(void) {
    ESP_LOGI(TAG, "========================================");
    ESP_LOGI(TAG, "  Massage Pro X1 - Starting Up");
    ESP_LOGI(TAG, "  Firmware Version: 1.0.0");
    ESP_LOGI(TAG, "========================================");
    
    // Step 1: Initialize NVS
    ESP_ERROR_CHECK(init_nvs());
    
    // Step 2: Initialize hardware peripherals
    ESP_ERROR_CHECK(init_hardware());
    
    // Step 3: Initialize Bluetooth
    ESP_ERROR_CHECK(init_bluetooth());
    
    // Step 4: Initialize AI Assistant
    ESP_ERROR_CHECK(init_assistant());
    
    // System ready
    ESP_LOGI(TAG, "========================================");
    ESP_LOGI(TAG, "  ✓ System Ready!");
    ESP_LOGI(TAG, "  Waiting for BLE connections...");
    ESP_LOGI(TAG, "========================================");
    
    // Main loop (if needed for future tasks)
    while (1) {
        vTaskDelay(pdMS_TO_TICKS(1000));
    }
}
