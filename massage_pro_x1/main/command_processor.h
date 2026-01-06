#ifndef COMMAND_PROCESSOR_H
#define COMMAND_PROCESSOR_H

#include <stdint.h>

/**
 * @brief Process BLE command
 * 
 * @param data Command data buffer
 * @param len Length of command data
 */
void process_command(uint8_t *data, uint16_t len);

#endif // COMMAND_PROCESSOR_H
