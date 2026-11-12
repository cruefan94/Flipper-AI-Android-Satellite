#ifndef FLIPPER_AI_LINK_H
#define FLIPPER_AI_LINK_H
#include "ai_client.h"
typedef bool (*AiLinkWrite)(const uint8_t* frame, size_t length, void* context);
typedef struct {
    AiClient client;
    AiLinkWrite write;
    void* context;
    size_t max_payload;
    bool connected;
    /* A protocol fault requires an actual disconnect and authenticated reconnect. */
    bool reconnect_required;
    bool write_pending;
    bool deadline_active;
    uint32_t deadline_ms;
} AiLink;
/* The caller must verify BLE authentication before connected=true. */
void ai_link_init(AiLink* link, AiLinkWrite write, void* context);
void ai_link_connection(AiLink* link, bool connected, size_t max_payload);
AiClientResult ai_link_hello(AiLink* link);
AiClientResult ai_link_ask(AiLink* link, const char* prompt);
AiClientResult ai_link_ptt(AiLink* link);
void ai_link_write_complete(AiLink* link, bool success);
/* Call set_deadline after accepted send, using a monotonic millisecond clock.
 * Poll periodically; timeouts reset session and require a new HELLO.
 * Timeout intervals must be < 2^31 ms. */
void ai_link_set_deadline(AiLink* link, uint32_t now_ms, uint32_t timeout_ms);
bool ai_link_poll_timeout(AiLink* link, uint32_t now_ms);
AiClientResult ai_link_notification(AiLink* link, const uint8_t* frame, size_t length, AiWireMessage* reply);
#endif
