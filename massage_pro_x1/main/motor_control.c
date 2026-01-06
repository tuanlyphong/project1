/*
 * Motor Control Module
 * Controls DC motor speed and direction via L298N driver
 */

#include "motor_control.h"
#include "esp_log.h"
#include "driver/gpio.h"
#include "driver/ledc.h"

#define TAG "MOTOR"

// PWM Configuration
#define PWM_TIMER          LEDC_TIMER_0
#define PWM_MODE           LEDC_LOW_SPEED_MODE
#define PWM_CHANNEL        LEDC_CHANNEL_0
#define PWM_DUTY_RES       LEDC_TIMER_12_BIT
#define PWM_FREQUENCY      5000

// PWM duty cycle levels (0-4095 for 12-bit resolution)
static const uint32_t PWM_LEVELS[6] = {
    0,      // Level 0: Off
    0,      // Level 1: Off (reserved for future use)
    1024,   // Level 2: 25%
    2048,   // Level 3: 50%
    3072,   // Level 4: 75%
    4095    // Level 5: 100%
};

// External device state
extern device_state_t device_state;

//-----------------------------------------------------------------------------
// Private Functions
//-----------------------------------------------------------------------------

static esp_err_t init_gpio(void) {
    gpio_config_t io_conf = {
        .mode = GPIO_MODE_OUTPUT,
        .pull_up_en = GPIO_PULLUP_DISABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    
    // Configure motor direction pins
    io_conf.pin_bit_mask = (1ULL << MOTOR_IN1_PIN) | (1ULL << MOTOR_IN2_PIN);
    esp_err_t ret = gpio_config(&io_conf);
    if (ret != ESP_OK) {
        ESP_LOGE(TAG, "Failed to configure motor pins");
        return ret;
    }
    
    // Configure heat pin
    io_conf.pin_bit_mask = (1ULL << HEAT_PIN);
    ret = gpio_config(&io_conf);
    if (ret != ESP_OK) {
        ESP_LOGE(TAG, "Failed to configure heat pin");
        return ret;
    }
    
    // Initialize all outputs to LOW
    gpio_set_level(MOTOR_IN1_PIN, 0);
    gpio_set_level(MOTOR_IN2_PIN, 0);
    gpio_set_level(HEAT_PIN, 0);
    
    return ESP_OK;
}

static esp_err_t init_pwm(void) {
    // Configure PWM timer
    ledc_timer_config_t timer_conf = {
        .speed_mode = PWM_MODE,
        .duty_resolution = PWM_DUTY_RES,
        .timer_num = PWM_TIMER,
        .freq_hz = PWM_FREQUENCY,
        .clk_cfg = LEDC_AUTO_CLK
    };
    
    esp_err_t ret = ledc_timer_config(&timer_conf);
    if (ret != ESP_OK) {
        ESP_LOGE(TAG, "Failed to configure PWM timer");
        return ret;
    }
    
    // Configure PWM channel
    ledc_channel_config_t channel_conf = {
        .gpio_num = MOTOR_PWM_PIN,
        .speed_mode = PWM_MODE,
        .channel = PWM_CHANNEL,
        .timer_sel = PWM_TIMER,
        .duty = 0,
        .hpoint = 0
    };
    
    ret = ledc_channel_config(&channel_conf);
    if (ret != ESP_OK) {
        ESP_LOGE(TAG, "Failed to configure PWM channel");
        return ret;
    }
    
    return ESP_OK;
}

//-----------------------------------------------------------------------------
// Public Functions
//-----------------------------------------------------------------------------

esp_err_t motor_control_init(void) {
    esp_err_t ret;
    
    // Initialize GPIO
    ret = init_gpio();
    if (ret != ESP_OK) {
        return ret;
    }
    
    // Initialize PWM
    ret = init_pwm();
    if (ret != ESP_OK) {
        return ret;
    }
    
    ESP_LOGI(TAG, "Motor control initialized");
    ESP_LOGI(TAG, "  PWM: GPIO%d @ %d Hz", MOTOR_PWM_PIN, PWM_FREQUENCY);
    ESP_LOGI(TAG, "  DIR: GPIO%d, GPIO%d", MOTOR_IN1_PIN, MOTOR_IN2_PIN);
    ESP_LOGI(TAG, "  HEAT: GPIO%d", HEAT_PIN);
    
    return ESP_OK;
}

esp_err_t motor_set_level(uint8_t level) {
    if (level > 5) {
        ESP_LOGW(TAG, "Invalid level %d, clamping to 5", level);
        level = 5;
    }
    
    device_state.intensity_level = level;
    uint32_t duty = PWM_LEVELS[level];
    
    if (level == 0) {
        // Stop motor
        gpio_set_level(MOTOR_IN1_PIN, 0);
        gpio_set_level(MOTOR_IN2_PIN, 0);
        ledc_set_duty(PWM_MODE, PWM_CHANNEL, 0);
        ledc_update_duty(PWM_MODE, PWM_CHANNEL);
        ESP_LOGI(TAG, "Motor stopped");
    } else {
        // Set direction
        if (device_state.rotate_on) {
            gpio_set_level(MOTOR_IN1_PIN, 0);
            gpio_set_level(MOTOR_IN2_PIN, 1);
            ESP_LOGI(TAG, "Motor level %d (reverse) - duty: %lu", level, duty);
        } else {
            gpio_set_level(MOTOR_IN1_PIN, 1);
            gpio_set_level(MOTOR_IN2_PIN, 0);
            ESP_LOGI(TAG, "Motor level %d (forward) - duty: %lu", level, duty);
        }
        
        // Set speed
        ledc_set_duty(PWM_MODE, PWM_CHANNEL, duty);
        ledc_update_duty(PWM_MODE, PWM_CHANNEL);
    }
    
    return ESP_OK;
}

esp_err_t motor_toggle_direction(void) {
    device_state.rotate_on = !device_state.rotate_on;
    
    ESP_LOGI(TAG, "Direction: %s", device_state.rotate_on ? "REVERSE" : "FORWARD");
    
    // Reapply current level with new direction
    if (device_state.intensity_level > 0) {
        return motor_set_level(device_state.intensity_level);
    }
    
    return ESP_OK;
}

esp_err_t motor_set_heat(bool enable) {
    device_state.heat_on = enable;
    gpio_set_level(HEAT_PIN, enable ? 1 : 0);
    
    ESP_LOGI(TAG, "Heat: %s", enable ? "ON" : "OFF");
    
    return ESP_OK;
}

esp_err_t motor_stop_all(void) {
    // Stop motor
    motor_set_level(0);
    
    // Turn off heat
    motor_set_heat(false);
    
    ESP_LOGI(TAG, "All motors and heat stopped");
    
    return ESP_OK;
}
