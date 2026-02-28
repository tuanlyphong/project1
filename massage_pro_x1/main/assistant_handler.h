/*
 * Assistant Handler Module
 * Manages automated massage sessions
 */

#ifndef ASSISTANT_HANDLER_H
#define ASSISTANT_HANDLER_H

#include <stdint.h>
#include <stdbool.h>
#include "esp_err.h"

//-----------------------------------------------------------------------------
// Assistant Configuration Structure
//-----------------------------------------------------------------------------

typedef struct {
    uint8_t level;          // Massage level 0-5
    bool heat_enabled;      // Heat on/off
    uint16_t duration_min;  // Duration in minutes
    uint32_t start_time;    // Timestamp when session started
    bool active;            // Session currently running
} assistant_config_t;

//-----------------------------------------------------------------------------
// Function Prototypes
//-----------------------------------------------------------------------------

/**
 * @brief Start an automated massage session
 * @param level Massage intensity level (0-5)
 * @param heat Enable heating element
 * @param duration_min Session duration in minutes
 * @return ESP_OK on success
 */
esp_err_t assistant_start_session(uint8_t level, bool heat, uint16_t duration_min);

/**
 * @brief Stop the current massage session
 */
void assistant_stop_session(void);

/**
 * @brief Get current assistant configuration
 * @return Pointer to current config
 */
assistant_config_t* assistant_get_config(void);

/**
 * @brief Check if assistant session is active
 * @return true if session running
 */
bool assistant_is_active(void);

/**
 * @brief Get time remaining in current session
 * @return Minutes remaining (0 if not active)
 */
uint16_t assistant_get_time_remaining(void);

/**
 * @brief Initialize assistant handler
 */
void assistant_init(void);

/**
 * @brief Initialize assistant timer task (legacy compatibility)
 * This is an alias for assistant_init()
 */
static inline void assistant_init_timer_task(void) {
    assistant_init();
}

/**
 * @brief Assistant task (internal use)
 */
void assistant_task(void *pvParameters);

#endif // ASSISTANT_HANDLER_H
