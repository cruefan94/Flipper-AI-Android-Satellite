#pragma once
/* Nonblocking, fixed-capacity packet queue for the BLE event dispatcher.
 * External synchronization is required between RX (producer) and app (consumer).
 * Zero heap allocation; overflow drops a WHOLE frame, never a tail fragment.
 */
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#define AI_SAT_QUEUE_DEPTH 4u
#define AI_SAT_QUEUE_FRAME_MAX 180u
typedef struct {
    uint8_t frames[AI_SAT_QUEUE_DEPTH][AI_SAT_QUEUE_FRAME_MAX];
    uint16_t lengths[AI_SAT_QUEUE_DEPTH];
    uint8_t head, tail, count;
    uint32_t dropped;
} AiSatelliteQueue;
static inline void ai_sat_queue_reset(AiSatelliteQueue* q) {
    if(!q)return;
    q->head=0;q->tail=0;q->count=0;q->dropped=0;
}
static inline bool ai_sat_queue_push(AiSatelliteQueue* q,const uint8_t* data,size_t length) {
    if(!q || !data || !length || length>AI_SAT_QUEUE_FRAME_MAX)return false;
    if(q->count>=AI_SAT_QUEUE_DEPTH){q->dropped++;return false;}
    for(size_t i=0;i<length;i++)q->frames[q->tail][i]=data[i];
    q->lengths[q->tail]=(uint16_t)length;
    q->tail=(uint8_t)((q->tail+1u)%AI_SAT_QUEUE_DEPTH);
    q->count++;
    return true;
}
static inline bool ai_sat_queue_pop(AiSatelliteQueue* q,uint8_t* output,size_t capacity,size_t* length) {
    if(!q || !output || !length || !q->count)return false;
    size_t count=q->lengths[q->head];
    if(capacity<count)return false; /* Do not consume on insufficient output space. */
    for(size_t i=0;i<count;i++)output[i]=q->frames[q->head][i];
    *length=count;
    q->head=(uint8_t)((q->head+1u)%AI_SAT_QUEUE_DEPTH);
    q->count--;
    return true;
}
