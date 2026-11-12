#pragma once
/* Experimental security policy: only the trusted GAP dispatcher can call these.
 * Pairing success does not prove that link encryption is active.
 * A live security procedure or pairing-complete event plus confirmed active
 * encryption is required. Bonding records alone never authorize traffic.
 */
#include <stdbool.h>
#include <stdint.h>
typedef struct {
    uint16_t handle;
    bool connected;
    bool pairing_confirmed;
    bool encryption_confirmed;
    bool emitted_ready;
} AiSatelliteTrust;
static inline void ai_sat_trust_reset(AiSatelliteTrust* s) {
    if(!s)return;
    s->handle=0;s->connected=false;s->pairing_confirmed=false;
    s->encryption_confirmed=false;s->emitted_ready=false;
}
static inline void ai_sat_trust_connected(AiSatelliteTrust* s,uint16_t handle) {
    if(!s)return;
    ai_sat_trust_reset(s);
    /* HCI connection handle zero is valid; connected is a separate flag. */
    s->connected=true;s->handle=handle;
}
static inline bool ai_sat_trust_pairing(AiSatelliteTrust* s,bool ok) {
    if(!s||!s->connected)return false;
    if(!ok){s->pairing_confirmed=false;s->emitted_ready=false;return false;}
    s->pairing_confirmed=true;
    return s->pairing_confirmed&&s->encryption_confirmed;
}
/* GAP security procedure is initiated for this live link, including bonded
 * reconnects. It is not sufficient to authorize traffic by itself: the
 * controller must separately report successful active encryption.
 */
static inline bool ai_sat_trust_security_started(AiSatelliteTrust* s) {
    if(!s||!s->connected)return false;
    s->pairing_confirmed=true;
    return s->pairing_confirmed&&s->encryption_confirmed;
}
static inline bool ai_sat_trust_encryption(
    AiSatelliteTrust* s,uint16_t handle,bool status_ok,bool enabled) {
    if(!s||!s->connected||handle!=s->handle)return false;
    s->encryption_confirmed=status_ok&&enabled;
    if(!s->encryption_confirmed)s->emitted_ready=false;
    return s->pairing_confirmed&&s->encryption_confirmed;
}
static inline bool ai_sat_trust_take_ready(AiSatelliteTrust* s) {
    if(!s||!s->connected||!s->pairing_confirmed||!s->encryption_confirmed||s->emitted_ready)
        return false;
    s->emitted_ready=true;
    return true;
}
