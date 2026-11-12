#ifndef FLIPPER_AI_WIRE_H
#define FLIPPER_AI_WIRE_H
#include <stddef.h>
#include <stdint.h>
/* Platform-neutral protocol parser for later Momentum BLE GATT integration.
 * No BLE service registration, authentication, or hardware actions are performed here. */
#define AI_WIRE_MAX_FRAME 180u
#define AI_WIRE_MAX_TYPE 16u
typedef enum {
    AI_WIRE_OK = 0,
    AI_WIRE_BAD_FRAME,
    AI_WIRE_BAD_VERSION,
    AI_WIRE_BAD_ID,
    AI_WIRE_BAD_TYPE,
    AI_WIRE_OVERFLOW
} AiWireResult;
typedef struct {
    char type[AI_WIRE_MAX_TYPE + 1];
    uint32_t request_id;
    char payload[AI_WIRE_MAX_FRAME + 1];
} AiWireMessage;
AiWireResult ai_wire_parse(const uint8_t* data, size_t length, AiWireMessage* out);
AiWireResult ai_wire_format(char* out, size_t capacity, const char* type, uint32_t id, const char* payload, size_t* written);
#endif
