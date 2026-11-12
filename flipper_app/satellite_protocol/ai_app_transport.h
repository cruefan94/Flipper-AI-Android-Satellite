#ifndef FLIPPER_AI_APP_TRANSPORT_H
#define FLIPPER_AI_APP_TRANSPORT_H
#include "ai_ble_bridge.h"

/* Firmware-independent, application-thread BLE transport contract.
 * Implementations MUST reject writes without a verified encrypted session
 * and active CCCD notifications. Client does not own firmware profile.
 */
typedef struct {
    /* Trusted firmware supplies monotonic generation on each BLE link lifetime. */
    bool (*ready)(void* context, size_t* att_payload, uint32_t* connection_generation);
    bool (*notify)(const uint8_t* frame, size_t size, void* context);
    bool (*receive)(uint8_t* frame, size_t capacity, size_t* count, void* context);
    void (*request_disconnect)(void* context);
    void* context;
} AiAppTransportIo;

typedef struct {
    AiBleBridge bridge;
    AiAppTransportIo io;
    bool session_ready;
    bool status_snapshot_ready; /* only current trusted BT query, not handshake state */
    bool hello_requested;
    bool require_link_drop;
    uint32_t last_generation;
    uint32_t trusted_generation;
    bool trusted_generation_valid;
} AiAppTransport;

void ai_app_transport_init(AiAppTransport* app, AiAppTransportIo io);
void ai_app_transport_poll(AiAppTransport* app, uint32_t now_ms, AiWireMessage* output,
                           AiClientResult* outcome);
AiClientResult ai_app_transport_ask(AiAppTransport* app, const char* prompt, uint32_t now_ms);
/* Invoke only from an explicit Flipper button/UI action; no auto microphone trigger. */
AiClientResult ai_app_transport_ptt(AiAppTransport* app, uint32_t now_ms);
bool ai_app_transport_available(const AiAppTransport* app);
void ai_app_transport_reset(AiAppTransport* app);
#endif
