#ifndef MOTOR_CONTROL_H
#define MOTOR_CONTROL_H

#include <stdint.h>
#include <stdbool.h>
#include "esp_err.h"
#include "driver/gpio.h"
#include "commands.h"

// Pin definitions
#define MOTOR_PWM_PIN       GPIO_NUM_25
#define MOTOR_IN1_PIN       GPIO_NUM_26
#define MOTOR_IN2_PIN       GPIO_NUM_27
#define HEAT_PIN            GPIO_NUM_14

/**
 * @brief Initialize motor control system
 * 
 * Initializes GPIO pins and PWM for motor control
 * 
 * @return esp_err_t ESP_OK on success
 */
esp_err_t motor_control_init(void);

/**
 * @brief Set motor intensity level
 * 
 * @param level Intensity level (0-5)
 *              0 = Off
 *              1 = Reserved
 *              2 = 25%
 *              3 = 50%
 *              4 = 75%
 *              5 = 100%
 * @return esp_err_t ESP_OK on success
 */
esp_err_t motor_set_level(uint8_t level);

/**
 * @brief Toggle motor rotation direction
 * 
 * @return esp_err_t ESP_OK on success
 */
esp_err_t motor_toggle_direction(void);

/**
 * @brief Control heat element
 * 
 * @param enable true to turn on, false to turn off
 * @return esp_err_t ESP_OK on success
 */
esp_err_t motor_set_heat(bool enable);

/**
 * @brief Emergency stop - stop motor and turn off heat
 * 
 * @return esp_err_t ESP_OK on success
 */
esp_err_t motor_stop_all(void);

#endif // MOTOR_CONTROL_H
