// main/main.c
#include <stdio.h>
#include <string.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_log.h"
#include "nvs_flash.h"
#include "esp_bt.h"
#include "esp_gap_ble_api.h"
#include "esp_gatts_api.h"
#include "esp_bt_main.h"
#include "esp_gatt_common_api.h"
#include "driver/gpio.h"
#include "driver/ledc.h"
#include "max30102.h"
#define GATTS_TAG "MASSAGE_BLE"
#define DEVICE_NAME "MassageProX1"

// Command IDs
#define CMD_ROTATE    0x01
#define CMD_HEAT      0x02
#define CMD_ASSISTANT 0x03
#define CMD_LEVEL     0x10

#define GATTS_NUM_HANDLE 10
#define ENA_PIN 25
#define IN1_PIN 26
#define HEAT_PIN 14
#define IN2_PIN 27
static uint16_t gatts_if_val = ESP_GATT_IF_NONE;
static uint16_t conn_id_val = 0xFFFF;
static uint16_t control_handle = 0;
static uint16_t service_handle = 0;
static uint16_t spo2_char_handle = 0;
static uint8_t adv_config_done = 0;
#define ADV_CONFIG_FLAG      (1 << 0)
#define SCAN_RSP_CONFIG_FLAG (1 << 1)

// UUID: 12345678-1234-5678-1234-56789abcdef0
static uint8_t service_uuid128[16] = {
    0xf0, 0xde, 0xbc, 0x9a, 0x78, 0x56, 0x34, 0x12,
    0x78, 0x56, 0x34, 0x12, 0x78, 0x56, 0x34, 0x12
};

// UUID: abcdef01-1234-5678-1234-56789abcdef0
static uint8_t char_uuid128[16] = {
    0xf0, 0xde, 0xbc, 0x9a, 0x78, 0x56, 0x34, 0x12,
    0x78, 0x56, 0x34, 0x12, 0x01, 0xef, 0xcd, 0xab
};
// Standard Bluetooth Base UUID: 00000000-0000-1000-8000-00805f9b34fb
static uint8_t spo2_char_uuid128[16] = {
    0xfb, 0x34, 0x9b, 0x5f, 0x80, 0x00,  // Last 6 bytes
    0x00, 0x80,                           // Fixed bits
    0x00, 0x10,                           // Fixed bits
    0x00, 0x00,                           // First 2 bytes
    0x02, 0x00, 0x00, 0x00               // Your 16-bit UUID (0x0002)
};
// Helper function to send HR/SpO2 data via BLE notification
void notify_spo2_data(uint8_t heart_rate, uint8_t spo2) {
    if (conn_id_val != 0xFFFF && spo2_char_handle != 0 && gatts_if_val != ESP_GATT_IF_NONE) {
        // Data packet matches Android client expectation: [HR, SpO2]
        uint8_t notify_data[2] = {heart_rate, spo2}; 
        
        esp_err_t ret = esp_ble_gatts_send_indicate(gatts_if_val, conn_id_val, 
                                                    spo2_char_handle, 
                                                    sizeof(notify_data), notify_data, false); // false for NOTIFY
        if (ret != ESP_OK) {
             ESP_LOGE(GATTS_TAG, "Notify failed: %s", esp_err_to_name(ret));
        } else {
             ESP_LOGD(GATTS_TAG, "Notified HR:%d SpO2:%d", heart_rate, spo2);
        }
    }
}
void pwm_init(void) {
    // Configure timer
    ledc_timer_config_t ledc_timer = {
        .speed_mode = LEDC_HIGH_SPEED_MODE,
        .duty_resolution = LEDC_TIMER_12_BIT,
        .timer_num = LEDC_TIMER_0,
        .freq_hz = 5000,
        .clk_cfg = LEDC_AUTO_CLK
    };
    ledc_timer_config(&ledc_timer);

    // Configure channel
    ledc_channel_config_t ledc_channel = {
        .gpio_num = ENA_PIN,
        .speed_mode = LEDC_HIGH_SPEED_MODE,
        .channel = LEDC_CHANNEL_0,
        .intr_type = LEDC_INTR_DISABLE,
        .timer_sel = LEDC_TIMER_0,
        .duty = 0,
        .hpoint = 0
    };
    ledc_channel_config(&ledc_channel);
}
void gpio_init(void) {
    gpio_config_t io_conf = {0};

    // Set ENA, IN1, IN2 as outputs
    io_conf.intr_type = GPIO_INTR_DISABLE;
    io_conf.mode = GPIO_MODE_OUTPUT;
    io_conf.pin_bit_mask = (1ULL << ENA_PIN) | (1ULL << IN1_PIN) | (1ULL << IN2_PIN);
    io_conf.pull_down_en = 0;
    io_conf.pull_up_en = 0;
    gpio_config(&io_conf);

    // Initialize pins low
    gpio_set_level(ENA_PIN, 0);
    gpio_set_level(IN1_PIN, 0);
    gpio_set_level(IN2_PIN, 0);
}
// Device state
static struct {
    bool rotate_on;
    bool heat_on;
    bool assistant_on;
    uint8_t intensity_level;
} device_state = {false, false, false, 0};

static esp_ble_adv_data_t adv_data = {
    .set_scan_rsp = false,
    .include_name = true,
    .include_txpower = true,
    .min_interval = 0x0006,
    .max_interval = 0x0010,
    .appearance = 0x00,
    .manufacturer_len = 0,
    .p_manufacturer_data = NULL,
    .service_data_len = 0,
    .p_service_data = NULL,
    .service_uuid_len = 16,
    .p_service_uuid = service_uuid128,
    .flag = (ESP_BLE_ADV_FLAG_GEN_DISC | ESP_BLE_ADV_FLAG_BREDR_NOT_SPT),
};

static esp_ble_adv_data_t scan_rsp_data = {
    .set_scan_rsp = true,
    .include_name = true,
    .include_txpower = true,
    .appearance = 0x00,
    .manufacturer_len = 0,
    .p_manufacturer_data = NULL,
    .service_data_len = 0,
    .p_service_data = NULL,
    .service_uuid_len = 0,
    .p_service_uuid = NULL,
    .flag = (ESP_BLE_ADV_FLAG_GEN_DISC | ESP_BLE_ADV_FLAG_BREDR_NOT_SPT),
};

static esp_ble_adv_params_t adv_params = {
    .adv_int_min = 0x20,
    .adv_int_max = 0x40,
    .adv_type = ADV_TYPE_IND,
    .own_addr_type = BLE_ADDR_TYPE_PUBLIC,
    .channel_map = ADV_CHNL_ALL,
    .adv_filter_policy = ADV_FILTER_ALLOW_SCAN_ANY_CON_ANY,
};

static void process_command(uint8_t *data, uint16_t len) {
    if (len < 1) return;

    uint8_t cmd = data[0];
    uint32_t pwm_levels[6] = {0,0, 1024, 2048, 3072, 4095}; // 12-bit PWM

    switch (cmd) {
        case CMD_ROTATE:
            device_state.rotate_on = !device_state.rotate_on;
            ESP_LOGI(GATTS_TAG, "Rotate Direction: %s", device_state.rotate_on ? "REVERSE" : "FORWARD");

            if (device_state.intensity_level > 0) {
                uint32_t speed = pwm_levels[device_state.intensity_level];
                if (device_state.rotate_on) {
                    gpio_set_level(IN1_PIN, 0); // reverse
                    gpio_set_level(IN2_PIN, 1);
                } else {
                    gpio_set_level(IN1_PIN, 1); // forward
                    gpio_set_level(IN2_PIN, 0);
                }
                ledc_set_duty(LEDC_HIGH_SPEED_MODE, LEDC_CHANNEL_0, speed);
                ledc_update_duty(LEDC_HIGH_SPEED_MODE, LEDC_CHANNEL_0);
            }
            break;

        case CMD_HEAT:
            device_state.heat_on = !device_state.heat_on;
            ESP_LOGI(GATTS_TAG, "Heat: %s", device_state.heat_on ? "ON" : "OFF");
            gpio_set_level(HEAT_PIN, device_state.heat_on ? 1 : 0);
            break;

        case CMD_ASSISTANT:
            device_state.assistant_on = !device_state.assistant_on;
            ESP_LOGI(GATTS_TAG, "Assistant: %s", device_state.assistant_on ? "ON" : "OFF");
            break;

        case CMD_LEVEL:
            if (len >= 2) {
                uint8_t level = data[1];
                if (level > 5) level = 5; // clamp 0-4
                device_state.intensity_level = level;
                ESP_LOGI(GATTS_TAG, "Speed Level: %d", device_state.intensity_level);

                uint32_t speed = pwm_levels[level];
                if (level == 1||level == 0) {
                    // Motor OFF
                    gpio_set_level(IN1_PIN, 0);
                    gpio_set_level(IN2_PIN, 0);
                    ledc_set_duty(LEDC_HIGH_SPEED_MODE, LEDC_CHANNEL_0, 0);
                    ledc_update_duty(LEDC_HIGH_SPEED_MODE, LEDC_CHANNEL_0);
                } else {
                    // Motor ON with current rotation direction
                    if (device_state.rotate_on) {
                        gpio_set_level(IN1_PIN, 0); // reverse
                        gpio_set_level(IN2_PIN, 1);
                    } else {
                        gpio_set_level(IN1_PIN, 1); // forward
                        gpio_set_level(IN2_PIN, 0);
                    }
                    ledc_set_duty(LEDC_HIGH_SPEED_MODE, LEDC_CHANNEL_0, speed);
                    ledc_update_duty(LEDC_HIGH_SPEED_MODE, LEDC_CHANNEL_0);
                }
            }
            break;

        default:
            ESP_LOGW(GATTS_TAG, "Unknown command: 0x%02X", cmd);
            break;
    }
}


static void gap_event_handler(esp_gap_ble_cb_event_t event, esp_ble_gap_cb_param_t *param) {
    switch (event) {
        case ESP_GAP_BLE_ADV_DATA_SET_COMPLETE_EVT:
            adv_config_done &= (~ADV_CONFIG_FLAG);
            if (adv_config_done == 0) {
                esp_ble_gap_start_advertising(&adv_params);
            }
            break;
            
        case ESP_GAP_BLE_SCAN_RSP_DATA_SET_COMPLETE_EVT:
            adv_config_done &= (~SCAN_RSP_CONFIG_FLAG);
            if (adv_config_done == 0) {
                esp_ble_gap_start_advertising(&adv_params);
            }
            break;
            
        case ESP_GAP_BLE_ADV_START_COMPLETE_EVT:
            if (param->adv_start_cmpl.status != ESP_BT_STATUS_SUCCESS) {
                ESP_LOGE(GATTS_TAG, "Advertising start failed");
            } else {
                ESP_LOGI(GATTS_TAG, "Advertising started");
            }
            break;
            
        case ESP_GAP_BLE_AUTH_CMPL_EVT:
            ESP_LOGI(GATTS_TAG, "Authentication complete, status: %d", param->ble_security.auth_cmpl.success);
            break;
            
        case ESP_GAP_BLE_SEC_REQ_EVT:
            ESP_LOGI(GATTS_TAG, "Security request");
            esp_ble_gap_security_rsp(param->ble_security.ble_req.bd_addr, true);
            break;
            
        default:
            break;
    }
}

static void gatts_event_handler(esp_gatts_cb_event_t event, esp_gatt_if_t gatts_if, esp_ble_gatts_cb_param_t *param) {
    switch (event) {
        case ESP_GATTS_REG_EVT: {
            ESP_LOGI(GATTS_TAG, "GATT server registered, app_id %04x", param->reg.app_id);
            gatts_if_val = gatts_if;
            
            esp_ble_gap_set_device_name(DEVICE_NAME);
            esp_ble_gap_config_adv_data(&adv_data);
            adv_config_done |= ADV_CONFIG_FLAG;
            esp_ble_gap_config_adv_data(&scan_rsp_data);
            adv_config_done |= SCAN_RSP_CONFIG_FLAG;
            
            // Set security parameters
            esp_ble_auth_req_t auth_req = ESP_LE_AUTH_REQ_SC_MITM_BOND;
            esp_ble_io_cap_t iocap = ESP_IO_CAP_NONE;
            uint8_t key_size = 16;
            uint8_t init_key = ESP_BLE_ENC_KEY_MASK | ESP_BLE_ID_KEY_MASK;
            uint8_t rsp_key = ESP_BLE_ENC_KEY_MASK | ESP_BLE_ID_KEY_MASK;
            
            esp_ble_gap_set_security_param(ESP_BLE_SM_AUTHEN_REQ_MODE, &auth_req, sizeof(uint8_t));
            esp_ble_gap_set_security_param(ESP_BLE_SM_IOCAP_MODE, &iocap, sizeof(uint8_t));
            esp_ble_gap_set_security_param(ESP_BLE_SM_MAX_KEY_SIZE, &key_size, sizeof(uint8_t));
            esp_ble_gap_set_security_param(ESP_BLE_SM_SET_INIT_KEY, &init_key, sizeof(uint8_t));
            esp_ble_gap_set_security_param(ESP_BLE_SM_SET_RSP_KEY, &rsp_key, sizeof(uint8_t));
            
            // Create service with proper UUID structure
            esp_gatt_srvc_id_t service_id = {
                .is_primary = true,
                .id = {
                    .uuid = {
                        .len = ESP_UUID_LEN_128,
                    },
                    .inst_id = 0,
                },
            };
            memcpy(service_id.id.uuid.uuid.uuid128, service_uuid128, 16);
            
            esp_ble_gatts_create_service(gatts_if, &service_id, GATTS_NUM_HANDLE);
            break;
        }
            
        case ESP_GATTS_CREATE_EVT:
            ESP_LOGI(GATTS_TAG, "Service created, status %d, service_handle %d", 
                    param->create.status, param->create.service_handle);
            
            service_handle = param->create.service_handle;
            
            // 1. Add Control Characteristic FIRST
            esp_bt_uuid_t char_uuid;
            char_uuid.len = ESP_UUID_LEN_128;
            memcpy(char_uuid.uuid.uuid128, char_uuid128, 16);
            
            esp_ble_gatts_add_char(service_handle,
                                  &char_uuid,
                                  ESP_GATT_PERM_WRITE,
                                  ESP_GATT_CHAR_PROP_BIT_WRITE,
                                  NULL, NULL);
            
            // 2. Add SpO2 Characteristic SECOND
            esp_bt_uuid_t spo2_uuid;
            spo2_uuid.len = ESP_UUID_LEN_128;
            memcpy(spo2_uuid.uuid.uuid128, spo2_char_uuid128, 16);
            
            esp_ble_gatts_add_char(service_handle,
                                  &spo2_uuid,
                                  ESP_GATT_PERM_READ,
                                  ESP_GATT_CHAR_PROP_BIT_READ | ESP_GATT_CHAR_PROP_BIT_NOTIFY,
                                  NULL, NULL);
            
            // 3. Start service AFTER adding ALL characteristics
            // We'll do this in ESP_GATTS_ADD_CHAR_EVT after the LAST characteristic is added
            break;            
        // ... inside gatts_event_handler ...
        case ESP_GATTS_ADD_CHAR_EVT:
            ESP_LOGI(GATTS_TAG, "Characteristic added, status %d, attr_handle %d",
                    param->add_char.status, param->add_char.attr_handle);
            
            if (param->add_char.char_uuid.len == ESP_UUID_LEN_128 && 
                memcmp(param->add_char.char_uuid.uuid.uuid128, char_uuid128, 16) == 0) {
                // Control Characteristic
                control_handle = param->add_char.attr_handle;
                ESP_LOGI(GATTS_TAG, "Control Char Handle: %d", control_handle);
                
            } else if (param->add_char.char_uuid.len == ESP_UUID_LEN_128 && 
                      memcmp(param->add_char.char_uuid.uuid.uuid128, spo2_char_uuid128, 16) == 0) {
                // SpO2 Characteristic
                spo2_char_handle = param->add_char.attr_handle;
                ESP_LOGI(GATTS_TAG, "SpO2 Char Handle: %d", spo2_char_handle);
                
                // Start service AFTER the last characteristic is added
                esp_ble_gatts_start_service(service_handle);
                ESP_LOGI(GATTS_TAG, "Service started with both characteristics");
            }
            break;// ... rest of event handler ...           
        case ESP_GATTS_CONNECT_EVT:
            ESP_LOGI(GATTS_TAG, "Device connected, conn_id %d", param->connect.conn_id);
            conn_id_val = param->connect.conn_id;
            
            esp_ble_conn_update_params_t conn_params = {0};
            memcpy(conn_params.bda, param->connect.remote_bda, sizeof(esp_bd_addr_t));
            conn_params.min_int = 0x10;
            conn_params.max_int = 0x20;
            conn_params.latency = 0;
            conn_params.timeout = 400;
            esp_ble_gap_update_conn_params(&conn_params);
            break;
            
        case ESP_GATTS_DISCONNECT_EVT:
            ESP_LOGI(GATTS_TAG, "Device disconnected, reason: 0x%x", param->disconnect.reason);
            conn_id_val = 0xFFFF;
            esp_ble_gap_start_advertising(&adv_params);
            break;
            
        case ESP_GATTS_WRITE_EVT:
            ESP_LOGI(GATTS_TAG, "Write event, handle %d, len %d", 
                     param->write.handle, param->write.len);
            
            if (param->write.handle == control_handle) {
                ESP_LOGI(GATTS_TAG, "Command received on control characteristic");
                process_command(param->write.value, param->write.len);
            }
            
            if (param->write.need_rsp) {
                esp_ble_gatts_send_response(gatts_if, param->write.conn_id,
                                           param->write.trans_id, ESP_GATT_OK, NULL);
            }
            break;
            
        default:
            break;
    }
}

void app_main(void) {
    gpio_init();      // initialize GPIO pins first
    pwm_init();       // initialize LEDC before using PWM
    if (max30102_i2c_init() == ESP_OK) {
        // 3. Start the sensor task
        xTaskCreate(max30102_task, "max30102_task", 4096, NULL, 5, NULL);
    } else {
        printf("Failed to init I2C\n");
    }
    
    esp_err_t ret;
    ret = nvs_flash_init();
    if (ret == ESP_ERR_NVS_NO_FREE_PAGES || ret == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        ret = nvs_flash_init();
    }
    ESP_ERROR_CHECK(ret);
    
    ESP_ERROR_CHECK(esp_bt_controller_mem_release(ESP_BT_MODE_CLASSIC_BT));
    
    esp_bt_controller_config_t bt_cfg = BT_CONTROLLER_INIT_CONFIG_DEFAULT();
    ret = esp_bt_controller_init(&bt_cfg);
    if (ret) {
        ESP_LOGE(GATTS_TAG, "Bluetooth controller init failed: %s", esp_err_to_name(ret));
        return;
    }
    
    ret = esp_bt_controller_enable(ESP_BT_MODE_BLE);
    if (ret) {
        ESP_LOGE(GATTS_TAG, "Bluetooth controller enable failed: %s", esp_err_to_name(ret));
        return;
    }
    
    ret = esp_bluedroid_init();
    if (ret) {
        ESP_LOGE(GATTS_TAG, "Bluedroid init failed: %s", esp_err_to_name(ret));
        return;
    }
    
    ret = esp_bluedroid_enable();
    if (ret) {
        ESP_LOGE(GATTS_TAG, "Bluedroid enable failed: %s", esp_err_to_name(ret));
        return;
    }
    
    esp_ble_gatts_register_callback(gatts_event_handler);
    esp_ble_gap_register_callback(gap_event_handler);
    esp_ble_gatts_app_register(0);
    
    ESP_LOGI(GATTS_TAG, "BLE GATT Server initialized");
    ESP_LOGI(GATTS_TAG, "Service UUID: 12345678-1234-5678-1234-56789abcdef0");
    ESP_LOGI(GATTS_TAG, "Char UUID: abcdef01-1234-5678-1234-56789abcdef0");

}
