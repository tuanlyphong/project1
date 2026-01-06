#ifndef ASSISTANT_HANDLER_H
#define ASSISTANT_HANDLER_H

#include <stdint.h>
#include <stdbool.h>
#include "esp_err.h"
#include "commands.h"
#include "motor_control.h"

/**
 * @brief Start an assistant massage session
 * 
 * @param level Intensity level (1-5)
 * @param heat Enable heat therapy (true/false)
 * @param duration_min Session duration in minutes (1-60)
 * @return esp_err_t ESP_OK on success
 */
esp_err_t assistant_start_session(uint8_t level, bool heat, uint16_t duration_min);

/**
 * @brief Stop the current assistant session
 * 
 * @return esp_err_t ESP_OK on success
 */
esp_err_t assistant_stop_session(void);

/**
 * @brief Get elapsed time in seconds since session start
 * 
 * @return uint32_t Elapsed seconds (0 if no active session)
 */
uint32_t assistant_get_elapsed_seconds(void);

/**
 * @brief Get remaining time in seconds
 * 
 * @return uint32_t Remaining seconds (0 if no active session)
 */
uint32_t assistant_get_remaining_seconds(void);

/**
 * @brief Check if assistant mode is currently active
 * 
 * @return true Assistant is active
 * @return false Assistant is not active
 */
bool assistant_is_active(void);

/**
 * @brief Background task for managing assistant timer
 * 
 * @param arg Task parameter (unused)
 */
void assistant_timer_task(void *arg);

/**
 * @brief Initialize and start the assistant timer task
 */
void assistant_init_timer_task(void);

#endif // ASSISTANT_HANDLER_H
