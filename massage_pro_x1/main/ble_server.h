/*
 * BLE Server Module
 */

#ifndef BLE_SERVER_H
#define BLE_SERVER_H

#include <stdint.h>
#include <stdbool.h>
#include "esp_err.h"
#include "esp_gatts_api.h"
#include "audio_control.h"

//-----------------------------------------------------------------------------
// BLE Configuration
//-----------------------------------------------------------------------------

#define DEVICE_NAME         "Massage_Pro_X1"
#define GATTS_NUM_HANDLE    10

//-----------------------------------------------------------------------------
// BLE State Structure
//-----------------------------------------------------------------------------

typedef struct {
    bool initialized;
    bool connected;
    uint16_t conn_id;
    esp_gatt_if_t gatts_if;
    uint16_t service_handle;
    uint16_t char_handle;
    uint8_t remote_addr[6];
} ble_state_t;

// Global BLE state
extern ble_state_t ble_state;

//-----------------------------------------------------------------------------
// Function Prototypes
//-----------------------------------------------------------------------------

/**
 * @brief Initialize BLE server
 * @return ESP_OK on success
 */
esp_err_t ble_server_init(void);

/**
 * @brief Send notification to connected client
 * @param data Data buffer to send
 * @param len Length of data
 * @return ESP_OK on success
 */
esp_err_t ble_server_notify(uint8_t *data, uint16_t len);

/**
 * @brief Check if client is connected
 * @return true if connected
 */
bool ble_is_connected(void);

/**
 * @brief Get connection handle
 * @return Connection ID (0 if not connected)
 */
uint16_t ble_get_conn_id(void);

/**
 * @brief Disconnect current client
 */
void ble_disconnect(void);

/**
 * @brief Notify SpO2 data to client
 * @param heart_rate Heart rate in BPM (0 = no reading)
 * @param spo2 SpO2 percentage (0 = no reading)
 */
void notify_device_state(uint8_t level, uint8_t heat, uint8_t flags);

#endif // BLE_SERVER_H
