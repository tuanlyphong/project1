/*
 * Motor Control Module
 */

#ifndef MOTOR_CONTROL_H
#define MOTOR_CONTROL_H

#include <stdint.h>
#include <stdbool.h>
#include "esp_err.h"

//-----------------------------------------------------------------------------
// Motor Pin Definitions
//-----------------------------------------------------------------------------

#define MOTOR_PWM_PIN       25   // PWM speed control
#define MOTOR_IN1_PIN       26   // Direction control 1
#define MOTOR_IN2_PIN       27   // Direction control 2
#define HEAT_PIN            14   // Heating element control

//-----------------------------------------------------------------------------
// PWM Configuration
//-----------------------------------------------------------------------------

#define PWM_FREQUENCY       5000  // 5 kHz
#define PWM_RESOLUTION      10    // 10-bit (0-1023)
#define PWM_DUTY_MAX        1023

//-----------------------------------------------------------------------------
// Function Prototypes
//-----------------------------------------------------------------------------

/**
 * @brief Initialize motor control system
 * @return ESP_OK on success
 */
esp_err_t motor_control_init(void);

/**
 * @brief Set motor speed level
 * @param level Speed level (0-5, 0=stop)
 * @return ESP_OK on success
 */
esp_err_t motor_set_level(uint8_t level);

/**
 * @brief Get current motor level
 * @return Current level (0-5)
 */
uint8_t motor_get_level(void);

/**
 * @brief Toggle motor rotation direction
 * @return ESP_OK on success
 */
esp_err_t motor_toggle_direction(void);

/**
 * @brief Set motor rotation direction
 * @param clockwise true for clockwise, false for counter-clockwise
 */
void motor_set_direction(bool clockwise);

/**
 * @brief Enable/disable heating element
 * @param enable true to turn on heat
 * @return ESP_OK on success
 */
esp_err_t motor_set_heat(bool enable);

/**
 * @brief Get heating element state
 * @return true if heat is on
 */
bool motor_get_heat_state(void);

/**
 * @brief Emergency stop - immediately stop all motors and heat
 */
void motor_emergency_stop(void);
/**
 * @brief Stop all - stop all motors and heat
 */
esp_err_t motor_stop_all(void);

#endif // MOTOR_CONTROL_H
