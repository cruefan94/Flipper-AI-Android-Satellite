#include "../targets/f7/ble_glue/services/ai_satellite_session.h"
#include <assert.h>
#include <stdio.h>
int main(void) {
    AiSatelliteSession s={0};
    assert(!ai_sat_session_can_send(&s,10));
    ai_sat_session_cccd(&s,true);
    ai_sat_session_authenticated(&s,true);
    assert(!ai_sat_session_can_send(&s,10));
    ai_sat_session_connected(&s,20);
    uint32_t first=s.generation;
    assert(!ai_sat_session_can_receive(&s));
    ai_sat_session_cccd(&s,true);
    assert(!ai_sat_session_can_send(&s,10));
    ai_sat_session_authenticated(&s,true);
    assert(ai_sat_session_can_receive(&s));
    assert(ai_sat_session_can_send(&s,20));
    assert(!ai_sat_session_can_send(&s,21));
    assert(!ai_sat_session_can_send(&s,0));
    ai_sat_session_mtu(&s,180);
    assert(ai_sat_session_can_send(&s,180));
    assert(!ai_sat_session_can_send(&s,181));
    ai_sat_session_authenticated(&s,false);
    assert(!ai_sat_session_can_send(&s,10));
    ai_sat_session_authenticated(&s,true);
    assert(!ai_sat_session_can_send(&s,10)); /* Revocation drops CCCD */
    ai_sat_session_cccd(&s,true);
    assert(ai_sat_session_can_send(&s,10));
    ai_sat_session_reset(&s);
    assert(s.generation!=first);
    assert(!ai_sat_session_can_send(&s,10));
    ai_sat_session_connected(&s,180);
    assert(!ai_sat_session_can_send(&s,10)); /* No reuse across reconnect */
    puts("ai_satellite_session tests passed");
    return 0;
}
