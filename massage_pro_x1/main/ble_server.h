#ifndef BLE_SERVER_H
#define BLE_SERVER_H

#include <stdint.h>
#include <stdbool.h>
#include "esp_err.h"
#include "audio_control.h"

// Device configuration
#define DEVICE_NAME             "Massage_Pro_X1"
#define GATTS_NUM_HANDLE        8

/**
 * @brief Initialize BLE GATT server
 * 
 * @return esp_err_t ESP_OK on success
 */
esp_err_t ble_server_init(void);

/**
 * @brief Send notification to connected client
 * 
 * @param data Data to send
 * @param len Length of data
 * @return esp_err_t ESP_OK on success
 */
esp_err_t ble_server_notify(uint8_t *data, uint16_t len);

/**
 * @brief Check if a client is connected
 * 
 * @return true Client is connected
 * @return false No client connected
 */
bool ble_server_is_connected(void);

/**
 * @brief Send health data notification (HR + SpO2)
 * 
 * @param heart_rate Heart rate in BPM (0-255)
 * @param spo2 SpO2 percentage (0-100)
 */
void notify_spo2_data(uint8_t heart_rate, uint8_t spo2);

/**
 * @brief Send waveform data notification
 * 
 * @param ir_value IR sensor reading (18-bit value)
 */
void notify_waveform_data(uint32_t ir_value);

#endif // BLE_SERVER_H#endif // BLE_SERVER_H
