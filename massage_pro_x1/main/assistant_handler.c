/*
 * Assistant Handler Module
 * Manages automated massage sessions
 */

#include "assistant_handler.h"
#include "motor_control.h"
#include "audio_control.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

static const char *TAG = "ASSISTANT";

// Global assistant configuration
static assistant_config_t assistant_config = {0};
static TaskHandle_t assistant_task_handle = NULL;
static bool one_minute_warning_played = false;

//-----------------------------------------------------------------------------
// Private Functions
//-----------------------------------------------------------------------------

static void assistant_timer_callback(void) {
    if (!assistant_config.active) {
        return;
    }
    
    uint32_t current_time = xTaskGetTickCount() * portTICK_PERIOD_MS / 1000; // seconds
    uint32_t elapsed_sec = current_time - assistant_config.start_time;
    uint32_t duration_sec = assistant_config.duration_min * 60;
    uint32_t remaining_sec = duration_sec - elapsed_sec;
    
    // Play one-minute warning
    if (remaining_sec <= 60 && remaining_sec > 59 && !one_minute_warning_played) {
        ESP_LOGI(TAG, "One minute remaining");
        audio_notify(AUDIO_NOTIFY_ONE_MINUTE_WARNING);
        one_minute_warning_played = true;
    }
    
    // Session complete
    if (elapsed_sec >= duration_sec) {
        ESP_LOGI(TAG, "Session completed");
        audio_notify(AUDIO_NOTIFY_SESSION_COMPLETE);
        assistant_stop_session();
    }
}

//-----------------------------------------------------------------------------
// Assistant Task
//-----------------------------------------------------------------------------

void assistant_task(void *pvParameters) {
    ESP_LOGI(TAG, "Assistant task started");
    
    while (assistant_config.active) {
        // Check if session should end
        assistant_timer_callback();
        
        // Update every second
        vTaskDelay(1000 / portTICK_PERIOD_MS);
    }
    
    ESP_LOGI(TAG, "Assistant task stopped");
    assistant_task_handle = NULL;
    vTaskDelete(NULL);
}

//-----------------------------------------------------------------------------
// Public API
//-----------------------------------------------------------------------------

void assistant_init(void) {
    assistant_config.active = false;
    assistant_config.level = 0;
    assistant_config.heat_enabled = false;
    assistant_config.duration_min = 0;
    assistant_config.start_time = 0;
    one_minute_warning_played = false;
    
    ESP_LOGI(TAG, "Assistant handler initialized");
}

esp_err_t assistant_start_session(uint8_t level, bool heat, uint16_t duration_min) {
    if (assistant_config.active) {
        ESP_LOGW(TAG, "Session already active, stopping previous session");
        assistant_stop_session();
        vTaskDelay(100 / portTICK_PERIOD_MS);
    }
    
    // Validate parameters
    if (level > 5) {
        ESP_LOGE(TAG, "Invalid level: %d (max 5)", level);
        return ESP_ERR_INVALID_ARG;
    }
    
    if (duration_min == 0 || duration_min > 120) {
        ESP_LOGE(TAG, "Invalid duration: %d min (1-120 allowed)", duration_min);
        return ESP_ERR_INVALID_ARG;
    }
    
    // Configure session
    assistant_config.level = level;
    assistant_config.heat_enabled = heat;
    assistant_config.duration_min = duration_min;
    assistant_config.start_time = xTaskGetTickCount() * portTICK_PERIOD_MS / 1000;
    assistant_config.active = true;
    one_minute_warning_played = false; // Reset warning flag
    
    // Apply settings to motor
    motor_set_level(level);
    motor_set_heat(heat);
    
    // Start assistant task
    xTaskCreate(assistant_task, "assistant_task", 2048, NULL, 4, &assistant_task_handle);
    
    ESP_LOGI(TAG, "Session started: Level %d, Heat %s, Duration %d min",
             level, heat ? "ON" : "OFF", duration_min);
    
    // Play session start sound (CHANGED from AUDIO_NOTIFY_ROTATE)
    audio_notify(AUDIO_NOTIFY_SESSION_START);
    
    return ESP_OK;
}

void assistant_stop_session(void) {
    if (!assistant_config.active) {
        ESP_LOGW(TAG, "No active session to stop");
        return;
    }
    
    ESP_LOGI(TAG, "Stopping session");
    
    // Stop motors and heat
    motor_set_level(0);
    motor_set_heat(false);
    
    // Mark as inactive (task will self-delete)
    assistant_config.active = false;
    
    // Reset config
    assistant_config.level = 0;
    assistant_config.heat_enabled = false;
    assistant_config.duration_min = 0;
    assistant_config.start_time = 0;
    one_minute_warning_played = false;
}

assistant_config_t* assistant_get_config(void) {
    return &assistant_config;
}

bool assistant_is_active(void) {
    return assistant_config.active;
}

uint16_t assistant_get_time_remaining(void) {
    if (!assistant_config.active) {
        return 0;
    }
    
    uint32_t current_time = xTaskGetTickCount() * portTICK_PERIOD_MS / 1000; // seconds
    uint32_t elapsed_sec = current_time - assistant_config.start_time;
    uint32_t duration_sec = assistant_config.duration_min * 60;
    
    if (elapsed_sec >= duration_sec) {
        return 0;
    }
    
    uint32_t remaining_sec = duration_sec - elapsed_sec;
    return (uint16_t)((remaining_sec + 59) / 60); // Round up to minutes
}
