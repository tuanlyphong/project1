/*
 * ADC Button Module Implementation
 * Handles 4-button resistor ladder on single ADC pin
 */

#include "adc_button.h"
#include "esp_log.h"
#include "esp_adc/adc_oneshot.h"
#include "esp_adc/adc_cali.h"
#include "esp_adc/adc_cali_scheme.h"
#include "driver/gpio.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#define TAG "ADC_BTN"

// Hardware Configuration
#define KEY_ADC_CHANNEL     ADC_CHANNEL_4   // GPIO32 = ADC1_CH4
#define KEY_ADC_PIN         GPIO_NUM_32
#define ADC_ATTEN           ADC_ATTEN_DB_12
#define ADC_WIDTH           ADC_BITWIDTH_12

// Voltage Thresholds (based on measured values)
// Button 1 (Heat): 1340 mV
#define BTN_HEAT_MIN        1200
#define BTN_HEAT_MAX        1500

// Button 2 (Level): 780 mV
#define BTN_LEVEL_MIN       650
#define BTN_LEVEL_MAX       900

// Button 3 (Rotate): 429 mV
#define BTN_ROTATE_MIN      300
#define BTN_ROTATE_MAX      550

// Button 4 (Off): 128 mV
#define BTN_OFF_MIN         50
#define BTN_OFF_MAX         200

// Sampling Configuration
#define ADC_SAMPLE_COUNT    32      // Number of samples to average
#define DEBOUNCE_MS         50      // Debounce delay in milliseconds
#define POLL_RATE_MS        20      // Button polling rate

// ADC handles
static adc_oneshot_unit_handle_t adc_handle = NULL;
static adc_cali_handle_t adc_cali_handle = NULL;

// Task handle
static TaskHandle_t button_task_handle = NULL;
static button_callback_t button_callback = NULL;
static bool task_running = false;

// Button state tracking
static adc_button_t last_button = BTN_NONE;
static uint32_t last_press_time = 0;

//-----------------------------------------------------------------------------
// Private Functions
//-----------------------------------------------------------------------------

/**
 * Read averaged ADC voltage
 */
static uint32_t read_voltage_internal(void) {
    int adc_raw;
    int voltage = 0;
    uint32_t total = 0;
    
    // Read multiple samples and average
    for (int i = 0; i < ADC_SAMPLE_COUNT; i++) {
        esp_err_t ret = adc_oneshot_read(adc_handle, KEY_ADC_CHANNEL, &adc_raw);
        if (ret != ESP_OK) {
            ESP_LOGW(TAG, "ADC read failed: %s", esp_err_to_name(ret));
            return 0;
        }
        
        // Convert to voltage
        if (adc_cali_handle != NULL) {
            adc_cali_raw_to_voltage(adc_cali_handle, adc_raw, &voltage);
            total += voltage;
        } else {
            // Fallback: approximate conversion
            total += (adc_raw * 3300) / 4095;
        }
    }
    
    return total / ADC_SAMPLE_COUNT;
}

/**
 * Detect button from voltage
 */
static adc_button_t detect_button_from_voltage(uint32_t voltage) {
    // Check in order from highest to lowest voltage
    if (voltage >= BTN_HEAT_MIN && voltage <= BTN_HEAT_MAX) {
        return BTN_HEAT;
    }
    if (voltage >= BTN_LEVEL_MIN && voltage <= BTN_LEVEL_MAX) {
        return BTN_LEVEL;
    }
    if (voltage >= BTN_ROTATE_MIN && voltage <= BTN_ROTATE_MAX) {
        return BTN_ROTATE;
    }
    if (voltage >= BTN_OFF_MIN && voltage <= BTN_OFF_MAX) {
        return BTN_OFF;
    }
    
    return BTN_NONE;
}

/**
 * Button monitoring task
 */
static void button_monitor_task(void *arg) {
    ESP_LOGI(TAG, "Button monitoring task started");
    
    while (task_running) {
        // Read current button state
        adc_button_t current_button = adc_button_read();
        
        // Detect button press (transition from NONE to button)
        if (current_button != BTN_NONE && last_button == BTN_NONE) {
            uint32_t now = xTaskGetTickCount() * portTICK_PERIOD_MS;
            
            // Debounce check
            if (now - last_press_time > DEBOUNCE_MS) {
                last_press_time = now;
                
                ESP_LOGI(TAG, "Button pressed: %s", adc_button_get_name(current_button));
                
                // Trigger callback if registered
                if (button_callback != NULL) {
                    button_callback(current_button);
                }
            }
        }
        
        last_button = current_button;
        
        // Poll at configured rate
        vTaskDelay(pdMS_TO_TICKS(POLL_RATE_MS));
    }
    
    ESP_LOGI(TAG, "Button monitoring task stopped");
    button_task_handle = NULL;
    vTaskDelete(NULL);
}

//-----------------------------------------------------------------------------
// Public Functions
//-----------------------------------------------------------------------------

esp_err_t adc_button_init(void) {
    esp_err_t ret;
    
    if (adc_handle != NULL) {
        ESP_LOGW(TAG, "ADC button already initialized");
        return ESP_OK;
    }
    
    // Enable pull-up on ADC pin
    gpio_set_pull_mode(KEY_ADC_PIN, GPIO_PULLUP_ONLY);
    
    // Configure ADC oneshot unit
    adc_oneshot_unit_init_cfg_t init_config = {
        .unit_id = ADC_UNIT_1,
        .ulp_mode = ADC_ULP_MODE_DISABLE,
    };
    
    ret = adc_oneshot_new_unit(&init_config, &adc_handle);
    if (ret != ESP_OK) {
        ESP_LOGE(TAG, "Failed to initialize ADC unit: %s", esp_err_to_name(ret));
        return ret;
    }
    
    // Configure ADC channel
    adc_oneshot_chan_cfg_t config = {
        .atten = ADC_ATTEN,
        .bitwidth = ADC_WIDTH,
    };
    
    ret = adc_oneshot_config_channel(adc_handle, KEY_ADC_CHANNEL, &config);
    if (ret != ESP_OK) {
        ESP_LOGE(TAG, "Failed to configure ADC channel: %s", esp_err_to_name(ret));
        adc_oneshot_del_unit(adc_handle);
        adc_handle = NULL;
        return ret;
    }
    
    // Initialize calibration
    adc_cali_line_fitting_config_t cali_config = {
        .unit_id = ADC_UNIT_1,
        .atten = ADC_ATTEN,
        .bitwidth = ADC_WIDTH,
    };
    
    ret = adc_cali_create_scheme_line_fitting(&cali_config, &adc_cali_handle);
    if (ret == ESP_OK) {
        ESP_LOGI(TAG, "ADC calibration enabled");
    } else {
        ESP_LOGW(TAG, "ADC calibration not available, using approximation");
        adc_cali_handle = NULL;
    }
    
    ESP_LOGI(TAG, "ADC button initialized on GPIO%d", KEY_ADC_PIN);
    ESP_LOGI(TAG, "Button thresholds (mV):");
    ESP_LOGI(TAG, "  Heat:   %d - %d", BTN_HEAT_MIN, BTN_HEAT_MAX);
    ESP_LOGI(TAG, "  Level:  %d - %d", BTN_LEVEL_MIN, BTN_LEVEL_MAX);
    ESP_LOGI(TAG, "  Rotate: %d - %d", BTN_ROTATE_MIN, BTN_ROTATE_MAX);
    ESP_LOGI(TAG, "  Off:    %d - %d", BTN_OFF_MIN, BTN_OFF_MAX);
    
    return ESP_OK;
}

esp_err_t adc_button_start(button_callback_t callback) {
    if (adc_handle == NULL) {
        ESP_LOGE(TAG, "ADC button not initialized");
        return ESP_ERR_INVALID_STATE;
    }
    
    if (button_task_handle != NULL) {
        ESP_LOGW(TAG, "Button monitoring task already running");
        return ESP_OK;
    }
    
    button_callback = callback;
    task_running = true;
    
    BaseType_t ret = xTaskCreate(
        button_monitor_task,
        "adc_button",
        2048,
        NULL,
        5,
        &button_task_handle
    );
    
    if (ret != pdPASS) {
        ESP_LOGE(TAG, "Failed to create button monitoring task");
        task_running = false;
        return ESP_FAIL;
    }
    
    ESP_LOGI(TAG, "Button monitoring started");
    return ESP_OK;
}

esp_err_t adc_button_stop(void) {
    if (button_task_handle == NULL) {
        return ESP_OK;
    }
    
    task_running = false;
    
    // Wait for task to finish (max 1 second)
    for (int i = 0; i < 100 && button_task_handle != NULL; i++) {
        vTaskDelay(pdMS_TO_TICKS(10));
    }
    
    if (button_task_handle != NULL) {
        ESP_LOGW(TAG, "Force deleting button task");
        vTaskDelete(button_task_handle);
        button_task_handle = NULL;
    }
    
    ESP_LOGI(TAG, "Button monitoring stopped");
    return ESP_OK;
}

adc_button_t adc_button_read(void) {
    if (adc_handle == NULL) {
        return BTN_NONE;
    }
    
    uint32_t voltage = read_voltage_internal();
    return detect_button_from_voltage(voltage);
}

uint32_t adc_button_get_voltage(void) {
    if (adc_handle == NULL) {
        return 0;
    }
    
    return read_voltage_internal();
}

const char* adc_button_get_name(adc_button_t button) {
    switch (button) {
        case BTN_HEAT:   return "HEAT";
        case BTN_LEVEL:  return "LEVEL";
        case BTN_ROTATE: return "ROTATE";
        case BTN_OFF:    return "OFF";
        case BTN_NONE:   return "NONE";
        default:         return "UNKNOWN";
    }
}
