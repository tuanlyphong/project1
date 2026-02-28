/*
 * Device State Definitions
 */

#ifndef DEVICE_STATE_H
#define DEVICE_STATE_H

#include <stdint.h>
#include <stdbool.h>

//-----------------------------------------------------------------------------
// Device State Structure
//-----------------------------------------------------------------------------

typedef struct {
    // Motor control
    uint8_t intensity_level;    // Motor speed level 0-5 (was motor_level)
    bool heat_on;               // Heat enabled
    bool rotate_on;             // Rotation direction (was direction_cw)
    
    // Audio
    uint8_t audio_volume;       // 0-100
    bool audio_muted;
    
    // Health monitoring
     
    // Assistant
    bool assistant_active;
    uint16_t assistant_time_remaining;  // minutes
    
    // System
    uint32_t uptime_seconds;
    bool battery_charging;
    uint8_t battery_level;      // 0-100%
    
} device_state_t;

//-----------------------------------------------------------------------------
// Global State Instance
//-----------------------------------------------------------------------------

// Declare global state (defined in main.c or similar)
extern device_state_t device_state;

#endif // DEVICE_STATE_H
