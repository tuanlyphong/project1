#ifndef COMMANDS_H
#define COMMANDS_H

#include <stdint.h>

//-----------------------------------------------------------------------------
// Command Codes
//-----------------------------------------------------------------------------

// Basic control commands
#define CMD_ROTATE              0x01
#define CMD_HEAT                0x02
#define CMD_LEVEL               0x03

// Assistant commands
#define CMD_ASSISTANT           0x04  // Legacy
#define CMD_ASSISTANT_CONFIG    0x05
#define CMD_ASSISTANT_STOP      0x06

// Audio commands
#define CMD_AUDIO_VOLUME        0x07
#define CMD_AUDIO_MUTE          0x08

//-----------------------------------------------------------------------------
// Notification Packet Types (from device to app)
//-----------------------------------------------------------------------------
#define NOTIFY_DEVICE_STATE     0xF3  // [0xF3][level][heat][flags]

//-----------------------------------------------------------------------------
// Command Structures
//-----------------------------------------------------------------------------

// Assistant configuration command
typedef struct __attribute__((packed)) {
    uint8_t cmd;          // CMD_ASSISTANT_CONFIG (0x05)
    uint8_t level;        // 0-5
    uint8_t heat;         // 0=off, 1=on
    uint8_t duration_h;   // Duration high byte
    uint8_t duration_l;   // Duration low byte
} cmd_assistant_config_t;

//-----------------------------------------------------------------------------
// Helper Functions
//-----------------------------------------------------------------------------

static inline uint16_t get_duration_from_cmd(cmd_assistant_config_t *cfg) {
    return ((uint16_t)cfg->duration_h << 8) | cfg->duration_l;
}

#endif // COMMANDS_H
