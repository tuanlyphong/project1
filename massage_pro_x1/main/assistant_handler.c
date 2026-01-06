#include "assistant_handler.h"
#include "audio_control.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "driver/gpio.h"
#include "driver/ledc.h"
#include "assistant_handler.h"
#include "motor_control.h"  // Add this
#include "esp_log.h"
#define TAG "ASSISTANT"

// External references (these should be in your main file)
extern device_state_t device_state;
extern assistant_config_t assistant_config;



esp_err_t assistant_start_session(uint8_t level, bool heat, uint16_t duration_min) {
    ESP_LOGI(TAG, "Starting assistant session: Level=%d, Heat=%s, Duration=%d min",
             level, heat ? "ON" : "OFF", duration_min);
    
    // Validate parameters
    if (level < 1 || level > 5) {
        ESP_LOGE(TAG, "Invalid level: %d", level);
        return ESP_ERR_INVALID_ARG;
    }
    
    if (duration_min < 1 || duration_min > 60) {
        ESP_LOGE(TAG, "Invalid duration: %d", duration_min);
        return ESP_ERR_INVALID_ARG;
    }
    
    // Stop any existing session
    if (assistant_config.active) {
        ESP_LOGW(TAG, "Stopping existing session");
        assistant_stop_session();
        vTaskDelay(pdMS_TO_TICKS(500));
    }
    
    // Configure assistant
    assistant_config.level = level;
    assistant_config.heat_enabled = heat;
    assistant_config.duration_minutes = duration_min;
    assistant_config.start_time = xTaskGetTickCount() * portTICK_PERIOD_MS / 1000;
    assistant_config.active = 1;
    
    // Apply settings
    device_state.intensity_level = level;
    // Replace apply_motor_level(level) with:
    motor_set_level(level);

    // Replace apply_heat(heat) with:
    motor_set_heat(heat);   
    // Audio feedback for level
    switch (level) {
        case 1: audio_notify(AUDIO_NOTIFY_LEVEL_1); break;
        case 2: audio_notify(AUDIO_NOTIFY_LEVEL_2); break;
        case 3: audio_notify(AUDIO_NOTIFY_LEVEL_3); break;
        case 4: audio_notify(AUDIO_NOTIFY_LEVEL_4); break;
        case 5: audio_notify(AUDIO_NOTIFY_LEVEL_5); break;
    }
    
    vTaskDelay(pdMS_TO_TICKS(300));
    
    // Audio feedback for heat
    if (heat) {
        audio_notify(AUDIO_NOTIFY_HEAT_ON);
    }
    
    // Confirmation beep
    vTaskDelay(pdMS_TO_TICKS(300));
    audio_notify(AUDIO_NOTIFY_READING_OK);
    
    ESP_LOGI(TAG, "Assistant session started successfully");
    return ESP_OK;
}

esp_err_t assistant_stop_session(void) {
    if (!assistant_config.active) {
        ESP_LOGW(TAG, "No active session to stop");
        return ESP_ERR_INVALID_STATE;
    }
    
    ESP_LOGI(TAG, "Stopping assistant session");
    
    // Deactivate assistant
    assistant_config.active = 0;
    
    // Stop motor
    device_state.intensity_level = 0;
    // Replace apply_motor_level(0) with:
    motor_stop_all();   
    // Turn off heat if it was on
    if (device_state.heat_on) {
        motor_set_heat(false);
    }
    
    // Audio feedback
    audio_notify(AUDIO_NOTIFY_ROTATE);
    
    ESP_LOGI(TAG, "Assistant session stopped");
    return ESP_OK;
}

uint32_t assistant_get_elapsed_seconds(void) {
    if (!assistant_config.active) {
        return 0;
    }
    
    uint32_t current_time = xTaskGetTickCount() * portTICK_PERIOD_MS / 1000;
    return current_time - assistant_config.start_time;
}

uint32_t assistant_get_remaining_seconds(void) {
    if (!assistant_config.active) {
        return 0;
    }
    
    uint32_t elapsed = assistant_get_elapsed_seconds();
    uint32_t total = assistant_config.duration_minutes * 60;
    
    if (elapsed >= total) {
        return 0;
    }
    
    return total - elapsed;
}

bool assistant_is_active(void) {
    return assistant_config.active != 0;
}

static void assistant_timer_task(void *arg) {
    static bool one_minute_warning_sent = false;
    bool session_started_announced = false;
    
    ESP_LOGI(TAG, "Assistant timer task started");
    
    while (1) {
        if (assistant_active && assistant_duration > 0) {
            uint32_t elapsed = (esp_timer_get_time() - assistant_start_time) / 1000000;
            uint32_t total = assistant_duration * 60;
            uint32_t remaining = (elapsed < total) ? (total - elapsed) : 0;
            
            // Announce session start (only once per session)
            if (elapsed == 0 && !session_started_announced) {
                ESP_LOGI(TAG, "Starting therapy session: %lu minutes", assistant_duration);
                audio_notify(AUDIO_NOTIFY_SESSION_START);
                session_started_announced = true;
            }
            
            // Check if session completed
            if (remaining == 0) {
                ESP_LOGI(TAG, "Session completed!");
                
                // Play completion voice instead of double beep
                audio_notify(AUDIO_NOTIFY_SESSION_COMPLETE);
                
                one_minute_warning_sent = false;
                session_started_announced = false;
            }
            // Send warning at 1 minute remaining (only once)
            else if (remaining <= 60 && !one_minute_warning_sent) {
                ESP_LOGI(TAG, "1 minute remaining");
                audio_notify(AUDIO_NOTIFY_ONE_MINUTE_WARNING);
                one_minute_warning_sent = true;
            }
            
            // Log status every 30 seconds
            if (elapsed % 30 == 0 && elapsed > 0) {
                ESP_LOGI(TAG, "Session status: %lu/%lu seconds (%lu remaining)",
                         elapsed, total, remaining);
            }
        } else {
            // Reset flags when not active
            one_minute_warning_sent = false;
            session_started_announced = false;
        }
        
        // Check every second
        vTaskDelay(pdMS_TO_TICKS(1000));
    }
}

void assistant_init_timer_task(void) {
    BaseType_t ret = xTaskCreate(
        assistant_timer_task,
        "assistant_timer",
        2048,
        NULL,
        5,
        NULL
    );
    
    if (ret != pdPASS) {
        ESP_LOGE(TAG, "Failed to create assistant timer task");
    } else {
        ESP_LOGI(TAG, "Assistant timer task created successfully");
    }
}
