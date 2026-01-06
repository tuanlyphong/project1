/* max30102.c */
#include "max30102.h"
#include "driver/i2c.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_log.h"
static const char *TAG = "MAX30102";
#include "ble_server.h"
// Register Addresses
#define MAX30102_ADDR               0x57
#define REG_INTR_STATUS_1           0x00
#define REG_FIFO_WR_PTR             0x04
#define REG_FIFO_RD_PTR             0x06
#define REG_FIFO_DATA               0x07
#define REG_MODE_CONFIG             0x09
#define REG_SPO2_CONFIG             0x0A
#define REG_LED1_PA                 0x0C
#define REG_LED2_PA                 0x0D
// --- Low Level I2C Functions ---

esp_err_t max30102_i2c_init(void) {
    i2c_config_t conf = {
        .mode = I2C_MODE_MASTER,
        .sda_io_num = I2C_MASTER_SDA_IO,
        .sda_pullup_en = GPIO_PULLUP_ENABLE,
        .scl_io_num = I2C_MASTER_SCL_IO,
        .scl_pullup_en = GPIO_PULLUP_ENABLE,
        .master.clk_speed = I2C_MASTER_FREQ_HZ,
    };
    i2c_param_config(I2C_MASTER_NUM, &conf);
    return i2c_driver_install(I2C_MASTER_NUM, conf.mode, 0, 0, 0);
}

static void max30102_write_reg(uint8_t reg, uint8_t data) {
    i2c_cmd_handle_t cmd = i2c_cmd_link_create();
    i2c_master_start(cmd);
    i2c_master_write_byte(cmd, (MAX30102_ADDR << 1) | I2C_MASTER_WRITE, true);
    i2c_master_write_byte(cmd, reg, true);
    i2c_master_write_byte(cmd, data, true);
    i2c_master_stop(cmd);
    i2c_master_cmd_begin(I2C_MASTER_NUM, cmd, 1000 / portTICK_PERIOD_MS);
    i2c_cmd_link_delete(cmd);
}

static void max30102_read_fifo(uint8_t *data, int length) {
    i2c_cmd_handle_t cmd = i2c_cmd_link_create();
    i2c_master_start(cmd);
    i2c_master_write_byte(cmd, (MAX30102_ADDR << 1) | I2C_MASTER_WRITE, true);
    i2c_master_write_byte(cmd, REG_FIFO_DATA, true);
    i2c_master_start(cmd);
    i2c_master_write_byte(cmd, (MAX30102_ADDR << 1) | I2C_MASTER_READ, true);
    i2c_master_read(cmd, data, length, I2C_MASTER_LAST_NACK);
    i2c_master_stop(cmd);
    i2c_master_cmd_begin(I2C_MASTER_NUM, cmd, 1000 / portTICK_PERIOD_MS);
    i2c_cmd_link_delete(cmd);
}

static void max30102_setup_regs() {
    // 1. Reset
    max30102_write_reg(REG_MODE_CONFIG, 0x40);
    vTaskDelay(100 / portTICK_PERIOD_MS);

    // 2. Mode = SpO2 (Red + IR)
    max30102_write_reg(REG_MODE_CONFIG, 0x03);

    // 3. SpO2 Config: 4096nA range, 100Hz sample rate, 411uS pulse width
    max30102_write_reg(REG_SPO2_CONFIG, 0x27);

    // 4. LED Pulse Amplitudes (Current)
    // 0x24 = ~7.2mA. Increase this if values are too low/dark.
    max30102_write_reg(REG_LED1_PA, 0x24); // Red
    max30102_write_reg(REG_LED2_PA, 0x24); // IR

    // 5. Clear FIFO pointers
    max30102_write_reg(REG_FIFO_WR_PTR, 0x00);
    max30102_write_reg(REG_FIFO_RD_PTR, 0x00);
    
    ESP_LOGI(TAG, "MAX30102 Configured");
}

// --- Main Task ---

#include <math.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_log.h"

#define BUFFER_SIZE 100
#define MIN_VALID_IR 50000
#define MAX_VALID_IR 200000
#define SAMPLE_RATE 100  // Hz
#define BEAT_THRESHOLD 3000  // Minimum change to detect a beat

// Circular buffer for filtering
typedef struct {
    uint32_t red[BUFFER_SIZE];
    uint32_t ir[BUFFER_SIZE];
    int head;
    int count;
} sensor_buffer_t;

// Heart rate detection state
typedef struct {
    uint32_t last_beat_time;
    uint32_t beat_intervals[4];  // Store last 4 intervals
    int beat_count;
    uint32_t last_ir_value;
    bool beat_detected;
} hr_state_t;

static sensor_buffer_t buffer = {0};
static hr_state_t hr_state = {0};

// Add sample to circular buffer
void add_sample(uint32_t red, uint32_t ir) {
    buffer.red[buffer.head] = red;
    buffer.ir[buffer.head] = ir;
    buffer.head = (buffer.head + 1) % BUFFER_SIZE;
    if (buffer.count < BUFFER_SIZE) {
        buffer.count++;
    }
}

// Simple moving average filter
uint32_t moving_average(uint32_t *data, int window) {
    if (buffer.count < window) return 0;
    
    uint64_t sum = 0;
    int start = (buffer.head - window + BUFFER_SIZE) % BUFFER_SIZE;
    
    for (int i = 0; i < window; i++) {
        int idx = (start + i) % BUFFER_SIZE;
        sum += data[idx];
    }
    
    return (uint32_t)(sum / window);
}

// DC removal using moving average
int32_t remove_dc(uint32_t current, uint32_t *data) {
    uint32_t dc = moving_average(data, 20);
    if (dc == 0) return 0;
    return (int32_t)current - (int32_t)dc;
}

// Detect heartbeat using peak detection
bool detect_beat(int32_t ir_ac, uint32_t current_time) {
    static int32_t last_value = 0;
    static int32_t max_value = 0;
    static bool rising = false;
    
    // Track if we're in rising phase
    if (ir_ac > last_value) {
        rising = true;
        if (ir_ac > max_value) {
            max_value = ir_ac;
        }
    } else if (rising && (max_value - ir_ac) > BEAT_THRESHOLD) {
        // Detected a peak (transition from rising to falling)
        rising = false;
        last_value = ir_ac;
        max_value = 0;
        
        // Check if enough time passed since last beat (min 300ms = 200 BPM max)
        if (current_time - hr_state.last_beat_time > 300) {
            return true;
        }
    }
    
    last_value = ir_ac;
    return false;
}

// Calculate heart rate from beat intervals
uint8_t calculate_heart_rate(void) {
    if (hr_state.beat_count < 2) {
        return 0;  // Need at least 2 beats
    }
    
    // Average the last beat intervals
    uint32_t sum = 0;
    int count = (hr_state.beat_count < 4) ? hr_state.beat_count : 4;
    
    for (int i = 0; i < count; i++) {
        sum += hr_state.beat_intervals[i];
    }
    
    uint32_t avg_interval = sum / count;
    
    // Convert to BPM: 60000 ms / interval
    uint16_t bpm = 60000 / avg_interval;
    
    // Sanity check: valid range 40-200 BPM
    if (bpm < 40 || bpm > 200) {
        return 0;
    }
    
    return (uint8_t)bpm;
}

// Calculate SpO2 using red/IR ratio
uint8_t calculate_spo2(void) {
    if (buffer.count < 50) {
        return 0;
    }
    
    // Get AC and DC components
    uint32_t red_dc = moving_average(buffer.red, 50);
    uint32_t ir_dc = moving_average(buffer.ir, 50);
    
    if (red_dc == 0 || ir_dc == 0) {
        return 0;
    }
    
    // Calculate AC components (peak-to-peak)
    uint32_t red_max = 0, red_min = UINT32_MAX;
    uint32_t ir_max = 0, ir_min = UINT32_MAX;
    
    int start = (buffer.head - 50 + BUFFER_SIZE) % BUFFER_SIZE;
    for (int i = 0; i < 50; i++) {
        int idx = (start + i) % BUFFER_SIZE;
        
        if (buffer.red[idx] > red_max) red_max = buffer.red[idx];
        if (buffer.red[idx] < red_min) red_min = buffer.red[idx];
        if (buffer.ir[idx] > ir_max) ir_max = buffer.ir[idx];
        if (buffer.ir[idx] < ir_min) ir_min = buffer.ir[idx];
    }
    
    uint32_t red_ac = red_max - red_min;
    uint32_t ir_ac = ir_max - ir_min;
    
    if (ir_ac == 0 || ir_dc == 0) {
        return 0;
    }
    
    // Calculate R value: (Red_AC/Red_DC) / (IR_AC/IR_DC)
    float r = ((float)red_ac / (float)red_dc) / ((float)ir_ac / (float)ir_dc);
    
    // Empirical formula for SpO2 (typical for MAX30102)
    // SpO2 = 110 - 25*R (this is a common approximation)
    float spo2 = 110.0 - 25.0 * r;
    
    // Clamp to valid range 70-100%
    if (spo2 > 100.0) spo2 = 100.0;
    if (spo2 < 70.0) spo2 = 70.0;
    
    return (uint8_t)spo2;
}

void max30102_task(void *pvParameters) {
    uint8_t fifo_buffer[6];
    uint32_t red_raw, ir_raw;
    uint32_t current_time;
    
    // Configure sensor once
    max30102_setup_regs();
    
    // Initialize state
    hr_state.last_beat_time = 0;
    hr_state.beat_count = 0;
    
    ESP_LOGI(TAG, "MAX30102 Algorithm started");
    
    while (1) {
        current_time = xTaskGetTickCount() * portTICK_PERIOD_MS;
        
        // Read FIFO sample
        max30102_read_fifo(fifo_buffer, 6);
        
        // Extract 18-bit values
        red_raw = ((uint32_t)fifo_buffer[0] << 16 | 
                   (uint32_t)fifo_buffer[1] << 8 | 
                   fifo_buffer[2]) & 0x03FFFF;
        ir_raw  = ((uint32_t)fifo_buffer[3] << 16 | 
                   (uint32_t)fifo_buffer[4] << 8 | 
                   fifo_buffer[5]) & 0x03FFFF;
        notify_waveform_data(ir_raw);       
        // Check if finger is present
        if (ir_raw < MIN_VALID_IR || ir_raw > MAX_VALID_IR) {
            // No finger or sensor saturated
            notify_spo2_data(0, 0);
            ESP_LOGD(TAG, "No valid finger detected (IR: %lu)", ir_raw);
            
            // Reset state
            buffer.count = 0;
            buffer.head = 0;
            hr_state.beat_count = 0;
            
            vTaskDelay(100 / portTICK_PERIOD_MS);
            continue;
        }
        
        // Add to buffer for filtering
        add_sample(red_raw, ir_raw);
        
        // Need enough samples before processing
        if (buffer.count < 50) {
            notify_spo2_data(0, 0);
            vTaskDelay(10 / portTICK_PERIOD_MS);
            continue;
        }
        
        // Remove DC component from IR signal
        int32_t ir_ac = remove_dc(ir_raw, buffer.ir);
        
        // Detect heartbeat
        if (detect_beat(ir_ac, current_time)) {
            uint32_t interval = current_time - hr_state.last_beat_time;
            
            // Store interval
            if (hr_state.beat_count < 4) {
                hr_state.beat_intervals[hr_state.beat_count] = interval;
                hr_state.beat_count++;
            } else {
                // Shift and add new interval
                for (int i = 0; i < 3; i++) {
                    hr_state.beat_intervals[i] = hr_state.beat_intervals[i + 1];
                }
                hr_state.beat_intervals[3] = interval;
            }
            
            hr_state.last_beat_time = current_time;
            ESP_LOGD(TAG, "Beat detected! Interval: %lu ms", interval);
        }
        
        // Calculate heart rate (every 500ms)
        static uint32_t last_calc_time = 0;
        if (current_time - last_calc_time > 500) {
            uint8_t hr = calculate_heart_rate();
            uint8_t spo2 = calculate_spo2();
            
            // Only send if we have valid readings
            if (hr > 0 && spo2 > 0) {
                notify_spo2_data(hr, spo2);
                ESP_LOGI(TAG, "HR: %d BPM | SpO2: %d%% | Raw - R:%lu IR:%lu", 
                         hr, spo2, red_raw, ir_raw);
            } else {
                // Still collecting data
                notify_spo2_data(0, 0);
                ESP_LOGD(TAG, "Collecting data... (beats: %d)", hr_state.beat_count);
            }
            
            last_calc_time = current_time;
        }
        
        // Match sample rate (~100Hz)
        vTaskDelay(10 / portTICK_PERIOD_MS);
    }
}
