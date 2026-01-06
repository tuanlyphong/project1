#ifndef AUDIO_CONTROL_H
#define AUDIO_CONTROL_H

#include <stdint.h>
#include <stdbool.h>
#include "esp_err.h"

// I2S Pin Definitions for MAX98357A
#define I2S_BCLK_PIN    17   // Bit clock
#define I2S_LRC_PIN     16   // Word select (left/right clock)
#define I2S_DOUT_PIN    5    // Data out

// SD Card SPI Pins
#define SD_MISO_PIN     19
#define SD_MOSI_PIN     23
#define SD_SCK_PIN      18
#define SD_CS_PIN       15

// audio.h - Add new notification types
typedef enum {
    AUDIO_NOTIFY_STARTUP = 0,
    AUDIO_NOTIFY_BLE_CONNECTED,
    AUDIO_NOTIFY_BLE_DISCONNECTED,
    AUDIO_NOTIFY_ROTATE,
    AUDIO_NOTIFY_HEAT_ON,
    AUDIO_NOTIFY_HEAT_OFF,
    AUDIO_NOTIFY_LEVEL_1,
    AUDIO_NOTIFY_LEVEL_2,
    AUDIO_NOTIFY_LEVEL_3,
    AUDIO_NOTIFY_LEVEL_4,
    AUDIO_NOTIFY_LEVEL_5,
    AUDIO_NOTIFY_SPO2_LOW,
    AUDIO_NOTIFY_HR_HIGH,
    AUDIO_NOTIFY_READING_OK,
    // New assistant mode notifications
    AUDIO_NOTIFY_SESSION_START,
    AUDIO_NOTIFY_SESSION_COMPLETE,
    AUDIO_NOTIFY_ONE_MINUTE_WARNING,
    AUDIO_NOTIFY_PLEASE_STAY_STILL,
    AUDIO_NOTIFY_MEASURING,
} audio_notify_type_t;
// Function declarations
esp_err_t audio_init(void);
esp_err_t audio_play_file(const char* filepath);
esp_err_t audio_play_tone(uint16_t frequency, uint16_t duration_ms);
esp_err_t audio_notify(audio_notify_type_t type);
void audio_stop(void);
void audio_set_volume(uint8_t volume); // 0-21

#endif // AUDIO_CONTROL_H
