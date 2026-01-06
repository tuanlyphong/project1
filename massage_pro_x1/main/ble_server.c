/*
 * BLE GATT Server Module
 * Handles all Bluetooth Low Energy communication
 */

#include "ble_server.h"
#include "esp_log.h"
#include "esp_bt.h"
#include "esp_bt_main.h"
#include "esp_gap_ble_api.h"
#include "esp_gatts_api.h"
#include "commands.h"
#include "motor_control.h"
#include <string.h>

#define TAG "BLE_SERVER"

// Service ID (for service creation)
// Service ID
static esp_gatt_srvc_id_t service_id = {
    .is_primary = true,
    .id.inst_id = 0x00,
    .id.uuid.len = ESP_UUID_LEN_128,
    .id.uuid.uuid.uuid128 = {
        0xF0, 0xDE, 0xBC, 0x9A, 0x78, 0x56, 0x34, 0x12,
        0x78, 0x56, 0x34, 0x12, 0x78, 0x56, 0x34, 0x12
    }
};

// Characteristic UUIDs
static esp_bt_uuid_t char_write_uuid = {
    .len = ESP_UUID_LEN_128,
    .uuid = {
        .uuid128 = {
            0xf0, 0xde, 0xbc, 0x9a, 0x78, 0x56, 0x34, 0x12,
            0x78, 0x56, 0x34, 0x12, 0x01, 0xef, 0xcd, 0xab
        }
    }
};

static esp_bt_uuid_t char_notify_uuid = {
    .len = ESP_UUID_LEN_128,
    .uuid = {
        .uuid128 = {
            0xf0, 0xde, 0xbc, 0x9a, 0x78, 0x56, 0x34, 0x12,
            0x78, 0x56, 0x34, 0x12, 0x02, 0xef, 0xcd, 0xab
        }
    }
};// Advertising parameters

// Advertising parameters
static esp_ble_adv_params_t adv_params = {
    .adv_int_min        = 0x20,
    .adv_int_max        = 0x40,
    .adv_type           = ADV_TYPE_IND,
    .own_addr_type      = BLE_ADDR_TYPE_PUBLIC,
    .channel_map        = ADV_CHNL_ALL,
    .adv_filter_policy  = ADV_FILTER_ALLOW_SCAN_ANY_CON_ANY,
};

// Advertising data
static esp_ble_adv_data_t adv_data = {
    .set_scan_rsp       = false,
    .include_name       = true,
    .include_txpower    = true,
    .min_interval       = 0x0006,
    .max_interval       = 0x0010,
    .appearance         = 0x00,
    .manufacturer_len   = 0,
    .p_manufacturer_data = NULL,
    .service_data_len   = 0,
    .p_service_data     = NULL,
    .service_uuid_len   = 0,
    .p_service_uuid     = NULL,
    .flag = (ESP_BLE_ADV_FLAG_GEN_DISC | ESP_BLE_ADV_FLAG_BREDR_NOT_SPT),
};

// BLE connection state
static struct {
    uint16_t conn_id;
    uint16_t gatts_if;
    uint16_t service_handle;
    uint16_t char_write_handle;
    uint16_t char_notify_handle;
    esp_bd_addr_t remote_bda;
    bool connected;
} ble_state = {0};

// External references
extern device_state_t device_state;
extern void process_command(uint8_t *data, uint16_t len);

//-----------------------------------------------------------------------------
// GAP Event Handler
//-----------------------------------------------------------------------------

static void gap_event_handler(esp_gap_ble_cb_event_t event, esp_ble_gap_cb_param_t *param) {
    switch (event) {
        case ESP_GAP_BLE_ADV_DATA_SET_COMPLETE_EVT:
            ESP_LOGI(TAG, "Advertising data set, starting advertising...");
            esp_ble_gap_start_advertising(&adv_params);
            break;
            
        case ESP_GAP_BLE_ADV_START_COMPLETE_EVT:
            if (param->adv_start_cmpl.status == ESP_BT_STATUS_SUCCESS) {
                ESP_LOGI(TAG, "✓ Advertising started successfully");
            } else {
                ESP_LOGE(TAG, "✗ Advertising start failed: %d", param->adv_start_cmpl.status);
            }
            break;
        
        // NEW: Handle authentication/pairing requests
        case ESP_GAP_BLE_AUTH_CMPL_EVT:
            if (param->ble_security.auth_cmpl.success) {
                ESP_LOGI(TAG, "Authentication success");
            } else {
                ESP_LOGE(TAG, "Authentication failed, status: %d", 
                         param->ble_security.auth_cmpl.fail_reason);
            }
            break;
            
        case ESP_GAP_BLE_SEC_REQ_EVT:
            // Security request from remote device - just accept it
            ESP_LOGI(TAG, "Security request received");
            esp_ble_gap_security_rsp(param->ble_security.ble_req.bd_addr, true);
            break;
            
        default:
            break;
    }
}

//-----------------------------------------------------------------------------
// GATTS Event Handler
//-----------------------------------------------------------------------------

static void gatts_event_handler(esp_gatts_cb_event_t event, esp_gatt_if_t gatts_if,
                               esp_ble_gatts_cb_param_t *param) {
    switch (event) {
        case ESP_GATTS_REG_EVT:
            ESP_LOGI(TAG, "GATT server registered, app_id: %d", param->reg.app_id);
            
            // Set device name
            esp_ble_gap_set_device_name(DEVICE_NAME);
            
            // Configure advertising data
            esp_ble_gap_config_adv_data(&adv_data);
            
            // Create service
            esp_ble_gatts_create_service(gatts_if, &service_id, GATTS_NUM_HANDLE);
            break;
            
        case ESP_GATTS_CREATE_EVT:
            ESP_LOGI(TAG, "Service created, handle: %d", param->create.service_handle);
            ble_state.service_handle = param->create.service_handle;
            ble_state.gatts_if = gatts_if;
            
            // Start service
            esp_ble_gatts_start_service(ble_state.service_handle);
            
            // Add write characteristic
            esp_ble_gatts_add_char(ble_state.service_handle, &char_write_uuid,
                                  ESP_GATT_PERM_READ | ESP_GATT_PERM_WRITE,  // Allow read too
                                  ESP_GATT_CHAR_PROP_BIT_WRITE | ESP_GATT_CHAR_PROP_BIT_READ,
                                  NULL, NULL);
            break;
            
        case ESP_GATTS_ADD_CHAR_EVT:
            ESP_LOGI(TAG, "Characteristic added, handle: %d", param->add_char.attr_handle);
            
            if (ble_state.char_write_handle == 0) {
                ble_state.char_write_handle = param->add_char.attr_handle;
                
                // Add notify characteristic
                esp_ble_gatts_add_char(ble_state.service_handle, &char_notify_uuid,
                                      ESP_GATT_PERM_READ,
                                      ESP_GATT_CHAR_PROP_BIT_NOTIFY | ESP_GATT_CHAR_PROP_BIT_READ,
                                      NULL, NULL);
            } else {
                ble_state.char_notify_handle = param->add_char.attr_handle;
                ESP_LOGI(TAG, "All characteristics added successfully");
            }
            break;
            
        case ESP_GATTS_CONNECT_EVT:
            ESP_LOGI(TAG, "✓ Client connected, conn_id: %d", param->connect.conn_id);
            ble_state.conn_id = param->connect.conn_id;
            ble_state.connected = true;
            memcpy(ble_state.remote_bda, param->connect.remote_bda, sizeof(esp_bd_addr_t));
            
            // Update connection parameters for better performance
            esp_ble_conn_update_params_t conn_params = {0};
            memcpy(conn_params.bda, param->connect.remote_bda, sizeof(esp_bd_addr_t));
            conn_params.min_int = 0x10;  // 20ms
            conn_params.max_int = 0x20;  // 40ms
            conn_params.latency = 0;
            conn_params.timeout = 400;   // 4s
            esp_ble_gap_update_conn_params(&conn_params);
            
            // Play connection sound
            audio_notify(AUDIO_NOTIFY_BLE_CONNECTED);
            break;
            
        case ESP_GATTS_DISCONNECT_EVT:
            ESP_LOGI(TAG, "✗ Client disconnected, reason: %d", param->disconnect.reason);
            ble_state.connected = false;
            
            // Play disconnection sound
            audio_notify(AUDIO_NOTIFY_BLE_DISCONNECTED);
            
            // Restart advertising
            esp_ble_gap_start_advertising(&adv_params);
            break;
            
        case ESP_GATTS_WRITE_EVT:
            if (param->write.handle == ble_state.char_write_handle) {
                ESP_LOGI(TAG, "Write received: %d bytes", param->write.len);
                ESP_LOG_BUFFER_HEX(TAG, param->write.value, param->write.len);
                
                // Process command
                process_command(param->write.value, param->write.len);
                
                // Send response if needed
                if (param->write.need_rsp) {
                    esp_ble_gatts_send_response(gatts_if, param->write.conn_id,
                                               param->write.trans_id,
                                               ESP_GATT_OK, NULL);
                }
            }
            break;
        
        case ESP_GATTS_MTU_EVT:
            ESP_LOGI(TAG, "MTU exchange, MTU: %d", param->mtu.mtu);
            break;
            
        default:
            break;
    }
}

//-----------------------------------------------------------------------------
// Public Functions
//-----------------------------------------------------------------------------

esp_err_t ble_server_init(void) {
    esp_err_t ret;
    
    // Release classic BT memory (we only use BLE)
    ESP_ERROR_CHECK(esp_bt_controller_mem_release(ESP_BT_MODE_CLASSIC_BT));
    
    // Initialize BT controller
    esp_bt_controller_config_t bt_cfg = BT_CONTROLLER_INIT_CONFIG_DEFAULT();
    ret = esp_bt_controller_init(&bt_cfg);
    if (ret != ESP_OK) {
        ESP_LOGE(TAG, "Bluetooth controller init failed: %s", esp_err_to_name(ret));
        return ret;
    }
    
    ret = esp_bt_controller_enable(ESP_BT_MODE_BLE);
    if (ret != ESP_OK) {
        ESP_LOGE(TAG, "Bluetooth controller enable failed: %s", esp_err_to_name(ret));
        return ret;
    }
    
    // Initialize Bluedroid stack
    ret = esp_bluedroid_init();
    if (ret != ESP_OK) {
        ESP_LOGE(TAG, "Bluedroid init failed: %s", esp_err_to_name(ret));
        return ret;
    }
    
    ret = esp_bluedroid_enable();
    if (ret != ESP_OK) {
        ESP_LOGE(TAG, "Bluedroid enable failed: %s", esp_err_to_name(ret));
        return ret;
    }
    
    // NEW: Set IO capability to NoInputNoOutput (no pairing required)
    esp_ble_io_cap_t iocap = ESP_IO_CAP_NONE;
    esp_ble_gap_set_security_param(ESP_BLE_SM_IOCAP_MODE, &iocap, sizeof(uint8_t));
    
    // NEW: Set authentication requirements to NONE
    uint8_t auth_req = ESP_LE_AUTH_NO_BOND;
    esp_ble_gap_set_security_param(ESP_BLE_SM_AUTHEN_REQ_MODE, &auth_req, sizeof(uint8_t));
    
    // NEW: Disable key distribution
    uint8_t key_size = 16;
    esp_ble_gap_set_security_param(ESP_BLE_SM_MAX_KEY_SIZE, &key_size, sizeof(uint8_t));
    
    uint8_t init_key = 0;
    esp_ble_gap_set_security_param(ESP_BLE_SM_SET_INIT_KEY, &init_key, sizeof(uint8_t));
    
    uint8_t rsp_key = 0;
    esp_ble_gap_set_security_param(ESP_BLE_SM_SET_RSP_KEY, &rsp_key, sizeof(uint8_t));
    
    // Register callbacks
    esp_ble_gatts_register_callback(gatts_event_handler);
    esp_ble_gap_register_callback(gap_event_handler);
    
    // Register GATT application
    esp_ble_gatts_app_register(0);
    
    ESP_LOGI(TAG, "BLE GATT Server initialized");
    ESP_LOGI(TAG, "Device name: %s", DEVICE_NAME);
    ESP_LOGI(TAG, "Security: NO PAIRING REQUIRED");
    
    return ESP_OK;
}

esp_err_t ble_server_notify(uint8_t *data, uint16_t len) {
    if (!ble_state.connected) {
        ESP_LOGW(TAG, "Cannot notify - not connected");
        return ESP_ERR_INVALID_STATE;
    }
    
    return esp_ble_gatts_send_indicate(ble_state.gatts_if,
                                       ble_state.conn_id,
                                       ble_state.char_notify_handle,
                                       len, data, false);
}

bool ble_server_is_connected(void) {
    return ble_state.connected;
}

void notify_spo2_data(uint8_t heart_rate, uint8_t spo2) {
    if (!ble_state.connected) {
        return;
    }
    
    uint8_t data[3] = {0xF1, heart_rate, spo2};  // 0xF1 = health data packet
    ble_server_notify(data, sizeof(data));
    
    ESP_LOGD(TAG, "Health data sent: HR=%d, SpO2=%d", heart_rate, spo2);
}

void notify_waveform_data(uint32_t ir_value) {
    if (!ble_state.connected) {
        return;
    }
    
    // Send as 4 bytes: [0xF2][IR_HIGH][IR_MID][IR_LOW]
    uint8_t data[4] = {
        0xF2,
        (ir_value >> 16) & 0xFF,
        (ir_value >> 8) & 0xFF,
        ir_value & 0xFF
    };
    
    ble_server_notify(data, sizeof(data));
}

