/*
 * ADC Button Module Header
 * Handles 4-button resistor ladder on single ADC pin
 * 
 * Uses GPIO32 (ADC1_CH4)
 * 
 * Button mapping (highest to lowest voltage):
 * Button 1 (Heat): ~1340 mV
 * Button 2 (Level): ~780 mV  
 * Button 3 (Reverse/Rotate): ~429 mV
 * Button 4 (Turn Off): ~128 mV
 */

#ifndef ADC_BUTTON_H
#define ADC_BUTTON_H

#include "esp_err.h"
#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

// Button identifiers
typedef enum {
    BTN_NONE = 0,
    BTN_HEAT,           // Button 1: Toggle heat (highest voltage ~1340mV)
    BTN_LEVEL,          // Button 2: Cycle intensity level (~780mV)
    BTN_ROTATE,         // Button 3: Toggle rotation direction (~429mV)
    BTN_OFF             // Button 4: Turn off motor (lowest voltage ~128mV)
} adc_button_t;

// Button event callback type
typedef void (*button_callback_t)(adc_button_t button);

/**
 * @brief Initialize ADC button module
 * 
 * Configures ADC1 on GPIO32 for reading voltage divider
 * Sets up calibration if available
 * 
 * @return ESP_OK on success, error code otherwise
 */
esp_err_t adc_button_init(void);

/**
 * @brief Start button monitoring task
 * 
 * Creates FreeRTOS task that continuously monitors button state
 * and triggers callbacks on button press/release events
 * 
 * @param callback Function to call when button is pressed (can be NULL)
 * @return ESP_OK on success, error code otherwise
 */
esp_err_t adc_button_start(button_callback_t callback);

/**
 * @brief Stop button monitoring task
 * 
 * @return ESP_OK on success, error code otherwise
 */
esp_err_t adc_button_stop(void);

/**
 * @brief Read current button state (blocking)
 * 
 * Reads ADC voltage and returns detected button
 * Uses averaging for noise reduction
 * 
 * @return Detected button (BTN_NONE if no button pressed)
 */
adc_button_t adc_button_read(void);

/**
 * @brief Get raw ADC voltage in millivolts
 * 
 * Useful for debugging and calibration
 * 
 * @return Voltage in mV
 */
uint32_t adc_button_get_voltage(void);

/**
 * @brief Get button name as string
 * 
 * @param button Button identifier
 * @return Button name string
 */
const char* adc_button_get_name(adc_button_t button);

#ifdef __cplusplus
}
#endif

#endif // ADC_BUTTON_H
