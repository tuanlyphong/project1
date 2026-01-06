/*
 * Command Processor Module
 * Processes commands received from BLE
 */

#include "command_processor.h"
#include "esp_log.h"
#include "motor_control.h"
#include "assistant_handler.h"
#include "audio_control.h"
#include "commands.h"

#define TAG "CMD_PROC"

// External references
extern device_state_t device_state;

//-----------------------------------------------------------------------------
// Command Handlers
//-----------------------------------------------------------------------------

static void handle_rotate_command(void) {
    motor_toggle_direction();
    audio_notify(AUDIO_NOTIFY_ROTATE);
}

static void handle_heat_command(void) {
    bool new_state = !device_state.heat_on;
    motor_set_heat(new_state);
    
    if (new_state) {
        audio_notify(AUDIO_NOTIFY_HEAT_ON);
    } else {
        audio_notify(AUDIO_NOTIFY_HEAT_OFF);
    }
}

static void handle_level_command(uint8_t level) {
    motor_set_level(level);
    
    // Audio feedback
    switch (level) {
        case 0: /* Silent for stop */ break;
        case 1: audio_notify(AUDIO_NOTIFY_LEVEL_1); break;
        case 2: audio_notify(AUDIO_NOTIFY_LEVEL_2); break;
        case 3: audio_notify(AUDIO_NOTIFY_LEVEL_3); break;
        case 4: audio_notify(AUDIO_NOTIFY_LEVEL_4); break;
        case 5: audio_notify(AUDIO_NOTIFY_LEVEL_5); break;
    }
}

static void handle_assistant_config(cmd_assistant_config_t *cfg) {
    uint16_t duration = get_duration_from_cmd(cfg);
    
    ESP_LOGI(TAG, "Assistant Config:");
    ESP_LOGI(TAG, "  Level: %d", cfg->level);
    ESP_LOGI(TAG, "  Heat: %s", cfg->heat ? "ON" : "OFF");
    ESP_LOGI(TAG, "  Duration: %d min", duration);
    
    esp_err_t ret = assistant_start_session(cfg->level, cfg->heat != 0, duration);
    
    if (ret != ESP_OK) {
        ESP_LOGE(TAG, "Failed to start assistant session");
    }
}

static void handle_assistant_stop(void) {
    ESP_LOGI(TAG, "Stopping assistant session");
    assistant_stop_session();
}

//-----------------------------------------------------------------------------
// Main Command Processor
//-----------------------------------------------------------------------------

void process_command(uint8_t *data, uint16_t len) {
    if (len < 1) {
        ESP_LOGW(TAG, "Empty command received");
        return;
    }
    
    uint8_t cmd = data[0];
    
    switch (cmd) {
        case CMD_ROTATE:
            ESP_LOGI(TAG, "Command: ROTATE");
            handle_rotate_command();
            break;
            
        case CMD_HEAT:
            ESP_LOGI(TAG, "Command: HEAT");
            handle_heat_command();
            break;
            
        case CMD_LEVEL:
            if (len >= 2) {
                ESP_LOGI(TAG, "Command: LEVEL %d", data[1]);
                handle_level_command(data[1]);
            } else {
                ESP_LOGW(TAG, "LEVEL command missing parameter");
            }
            break;
            
        case CMD_ASSISTANT_CONFIG:
            if (len >= 5) {
                ESP_LOGI(TAG, "Command: ASSISTANT_CONFIG");
                handle_assistant_config((cmd_assistant_config_t *)data);
            } else {
                ESP_LOGW(TAG, "ASSISTANT_CONFIG command invalid length: %d", len);
            }
            break;
            
        case CMD_ASSISTANT_STOP:
            ESP_LOGI(TAG, "Command: ASSISTANT_STOP");
            handle_assistant_stop();
            break;
            
        case CMD_ASSISTANT:
            ESP_LOGI(TAG, "Command: ASSISTANT (legacy - ignored)");
            break;
            
        default:
            ESP_LOGW(TAG, "Unknown command: 0x%02X", cmd);
            break;
    }
}
