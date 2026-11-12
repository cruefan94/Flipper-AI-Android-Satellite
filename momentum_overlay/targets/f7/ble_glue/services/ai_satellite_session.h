#pragma once
/* Firmware-internal AI BLE session policy.
 * Caller must provide authenticated link proof from the trusted BT/GAP layer.
 * Bonding by itself, or a CCCD write by itself, does NOT satisfy authentication.
 * Do not expose to third-party FAP API without a separate security review.
 */
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#define AI_SAT_SESSION_MAX_FRAME 180u
typedef struct {
    bool connected;
    bool authenticated;
    bool notifications_enabled;
    size_t att_payload;
    uint32_t generation;
} AiSatelliteSession;
static inline void ai_sat_session_reset(AiSatelliteSession* s) {
    if(!s)return;
    s->connected=false;
    s->authenticated=false;
    s->notifications_enabled=false;
    s->att_payload=0;
    s->generation++;
}
static inline void ai_sat_session_connected(AiSatelliteSession* s,size_t att_payload) {
    if(!s)return;
    ai_sat_session_reset(s);
    s->connected=true;
    s->att_payload=att_payload;
}
static inline void ai_sat_session_authenticated(AiSatelliteSession* s,bool verified) {
    if(!s)return;
    if(!s->connected){s->authenticated=false;return;}
    s->authenticated=verified;
    if(!verified)s->notifications_enabled=false;
}
static inline void ai_sat_session_cccd(AiSatelliteSession* s,bool enabled) {
    if(!s)return;
    s->notifications_enabled=s->connected&&enabled;
}
static inline void ai_sat_session_mtu(AiSatelliteSession* s,size_t payload) {
    if(s&&s->connected)s->att_payload=payload;
}
static inline bool ai_sat_session_can_send(const AiSatelliteSession* s,size_t frame) {
    return s&&s->connected&&s->authenticated&&s->notifications_enabled&&
        frame>0&&frame<=AI_SAT_SESSION_MAX_FRAME&&frame<=s->att_payload;
}
static inline bool ai_sat_session_can_receive(const AiSatelliteSession* s) {
    return s&&s->connected&&s->authenticated;
}
