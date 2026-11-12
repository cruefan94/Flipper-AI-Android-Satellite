#ifndef FLIPPER_AI_BLE_BRIDGE_H
#define FLIPPER_AI_BLE_BRIDGE_H
#include "ai_link.h"
/* Adapter for a future encrypted Momentum AI GATT profile. Does not register BLE itself. */
typedef struct {
    AiLink link;
    bool encrypted_and_authenticated;
    bool subscribed;
    bool disconnect_requested;
    uint32_t transport_generation;
} AiBleBridge;
typedef void (*AiBleDisconnectRequest)(void* context);
void ai_ble_bridge_init(AiBleBridge* bridge, AiLinkWrite write, void* context);
void ai_ble_bridge_connected(AiBleBridge* bridge, bool authenticated, bool notifications_enabled, size_t max_att_payload);
void ai_ble_bridge_disconnected(AiBleBridge* bridge);
AiClientResult ai_ble_bridge_start(AiBleBridge* bridge, uint32_t now_ms);
AiClientResult ai_ble_bridge_ask(AiBleBridge* bridge, const char* prompt, uint32_t now_ms);
AiClientResult ai_ble_bridge_ptt(AiBleBridge* bridge, uint32_t now_ms);
void ai_ble_bridge_write_complete(AiBleBridge* bridge, bool ok);
AiClientResult ai_ble_bridge_notification(AiBleBridge* bridge, const uint8_t* data, size_t length, AiWireMessage* result);
void ai_ble_bridge_tick(AiBleBridge* bridge, uint32_t now_ms, AiBleDisconnectRequest request, void* context);
#endif
