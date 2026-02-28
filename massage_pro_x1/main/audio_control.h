/*
 * Audio Control Module
 */

#ifndef AUDIO_CONTROL_H
#define AUDIO_CONTROL_H

#include <stdint.h>
#include <stdbool.h>
#include "esp_err.h"

//-----------------------------------------------------------------------------
// I2S Pin Definitions
//-----------------------------------------------------------------------------

#define I2S_BCLK_PIN    17
#define I2S_LRC_PIN     16
#define I2S_DOUT_PIN    4
#define I2S_SD_PIN      2   // SD (Shutdown) control pin for MAX98357A

//-----------------------------------------------------------------------------
// SD Card Pin Definitions
//-----------------------------------------------------------------------------

#define SD_MOSI_PIN     23
#define SD_MISO_PIN     19
#define SD_SCK_PIN      18
#define SD_CS_PIN       5

//-----------------------------------------------------------------------------
// Audio Notification Types
//-----------------------------------------------------------------------------

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
    AUDIO_NOTIFY_READING_OK,
    AUDIO_NOTIFY_ERROR,
    AUDIO_NOTIFY_SUCCESS,
    AUDIO_NOTIFY_SESSION_START,
    AUDIO_NOTIFY_SESSION_COMPLETE,
    AUDIO_NOTIFY_ONE_MINUTE_WARNING,
} audio_notify_type_t;

//-----------------------------------------------------------------------------
// Function Prototypes
//-----------------------------------------------------------------------------

/**
 * @brief Initialize audio system
 * @return ESP_OK on success
 */
esp_err_t audio_init(void);

/**
 * @brief Play notification sound
 * @param type Type of notification
 * @return ESP_OK on success
 */
esp_err_t audio_notify(audio_notify_type_t type);

/**
 * @brief Set audio volume
 * @param volume Volume level (0-100)
 */
void audio_set_volume(uint8_t volume);

/**
 * @brief Get current volume
 * @return Volume level (0-100)
 */
uint8_t audio_get_volume(void);

/**
 * @brief Mute/unmute audio
 * @param mute true to mute
 */
void audio_set_mute(bool mute);

/**
 * @brief Get mute state
 * @return true if muted
 */
bool audio_is_muted(void);

/**
 * @brief Play WAV file from SD card
 * @param filename File path (e.g., "/sdcard/sounds/beep.wav")
 * @return ESP_OK on success
 */
esp_err_t audio_play_file(const char *filename);

/**
 * @brief Stop current audio playback
 */
void audio_stop(void);

#endif // AUDIO_CONTROL_H
