/* max30102.h */
#ifndef MAX30102_H
#define MAX30102_H

#include "esp_err.h"
#include <stdint.h>

// --- Hardware Configuration ---
// Check your wiring! 
#define I2C_MASTER_SCL_IO           22      // GPIO for SCL
#define I2C_MASTER_SDA_IO           21      // GPIO for SDA
#define I2C_MASTER_NUM              0       // I2C Port Number
#define I2C_MASTER_FREQ_HZ          100000  // I2C Frequency

// --- Function Prototypes ---
// Call this in app_main to setup I2C
esp_err_t max30102_i2c_init(void);
void notify_spo2_data(uint8_t heart_rate, uint8_t spo2);
// This is the FreeRTOS task that runs the sensor
void max30102_task(void *pvParameters);

#endif
