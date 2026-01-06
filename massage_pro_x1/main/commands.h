#ifndef COMMANDS_H
#define COMMANDS_H

#include <stdint.h>

// BLE Command Definitions
#define CMD_ROTATE              0x01  // Toggle rotation direction
#define CMD_HEAT                0x02  // Toggle heat on/off
#define CMD_ASSISTANT           0x03  // Toggle assistant mode (deprecated - use CONFIG)
#define CMD_LEVEL               0x04  // Set intensity level (0-5)
#define CMD_ASSISTANT_CONFIG    0x06  // Configure assistant mode with parameters
#define CMD_ASSISTANT_STOP      0x07  // Stop assistant mode

// Device State Structure
typedef struct {
    uint8_t intensity_level;    // 0-5
    uint8_t rotate_on;          // 0=forward, 1=reverse
    uint8_t heat_on;            // 0=off, 1=on
    uint8_t assistant_on;       // 0=off, 1=on (legacy)
} device_state_t;

// Assistant Configuration Structure
typedef struct {
    uint8_t active;                 // Is assistant mode active
    uint8_t level;                  // Intensity level (1-5)
    uint8_t heat_enabled;           // Heat on/off
    uint16_t duration_minutes;      // Session duration in minutes
    uint32_t start_time;            // Start time in seconds
} assistant_config_t;

// Command Packet Structures

// CMD_LEVEL packet: [CMD][LEVEL]
typedef struct __attribute__((packed)) {
    uint8_t cmd;
    uint8_t level;
} cmd_level_t;

// CMD_ASSISTANT_CONFIG packet: [CMD][LEVEL][HEAT][DURATION_HIGH][DURATION_LOW]
typedef struct __attribute__((packed)) {
    uint8_t cmd;
    uint8_t level;
    uint8_t heat;
    uint8_t duration_high;
    uint8_t duration_low;
} cmd_assistant_config_t;

// Helper function to get duration from command
static inline uint16_t get_duration_from_cmd(cmd_assistant_config_t *cmd) {
    return (cmd->duration_high << 8) | cmd->duration_low;
}

#endif // COMMANDS_H
