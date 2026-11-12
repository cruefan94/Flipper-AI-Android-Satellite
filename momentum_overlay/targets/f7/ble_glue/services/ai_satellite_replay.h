#pragma once
/* Monotonic per-link AI1 receive ID gate. Not a substitute for encryption,
 * authentication or application-level request/response correlation.
 * Caller holds the service RX mutex. Commit only after enqueue succeeds.
 */
#include "ai_satellite_frame.h"
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
typedef struct {
    bool initialized;
    uint32_t last_id;
} AiSatelliteReplay;
static inline void ai_sat_replay_reset(AiSatelliteReplay* state) {
    if(!state)return;
    state->initialized=false;
    state->last_id=0;
}
static inline bool ai_sat_replay_peek(
    const AiSatelliteReplay* state,const uint8_t* frame,size_t length,uint32_t* id_out) {
    if(!state || !id_out || !ai_sat_valid_frame(frame,length))return false;
    size_t i=4;
    while(i<length && frame[i]!='|')i++;
    if(i>=length)return false;
    i++;
    uint32_t id=0;
    while(i<length && frame[i]!='|') {
        if(frame[i]<'0'||frame[i]>'9')return false;
        id=id*10u+(uint32_t)(frame[i]-'0');
        i++;
    }
    if(i>=length)return false;
    if(state->initialized && id<=state->last_id)return false;
    *id_out=id;
    return true;
}
static inline void ai_sat_replay_commit(AiSatelliteReplay* state,uint32_t accepted_id) {
    if(!state)return;
    state->last_id=accepted_id;
    state->initialized=true;
}
