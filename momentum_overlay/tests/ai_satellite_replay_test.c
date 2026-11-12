#include "../targets/f7/ble_glue/services/ai_satellite_replay.h"
#include "../targets/f7/ble_glue/services/ai_satellite_queue.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>
static bool enqueue(AiSatelliteReplay* replay,AiSatelliteQueue* queue,const char* s) {
    uint32_t id=0;
    size_t n=strlen(s);
    if(!ai_sat_replay_peek(replay,(const uint8_t*)s,n,&id))return false;
    if(!ai_sat_queue_push(queue,(const uint8_t*)s,n))return false;
    ai_sat_replay_commit(replay,id);
    return true;
}
int main(void) {
    AiSatelliteReplay replay={0};
    AiSatelliteQueue queue={0};
    ai_sat_queue_reset(&queue);
    ai_sat_replay_reset(&replay);
    assert(enqueue(&replay,&queue,"AI1|RESPONSE|1|hello\n"));
    assert(!enqueue(&replay,&queue,"AI1|RESPONSE|1|duplicate\n"));
    assert(!enqueue(&replay,&queue,"AI1|RESPONSE|0|old\n"));
    assert(enqueue(&replay,&queue,"AI1|RESPONSE|4294967295|top\n"));
    assert(!enqueue(&replay,&queue,"AI1|RESPONSE|4294967294|old\n"));
    assert(!enqueue(&replay,&queue,"AI1|RESPONSE|4294967296|overflow\n"));
    ai_sat_replay_reset(&replay);
    assert(enqueue(&replay,&queue,"AI1|HELLO|1|reconnected\n"));
    assert(enqueue(&replay,&queue,"AI1|RESPONSE|2|full\n"));
    assert(queue.count==AI_SAT_QUEUE_DEPTH);
    uint32_t candidate=0;
    assert(ai_sat_replay_peek(&replay,(const uint8_t*)"AI1|RESPONSE|3|retry\n",
                               strlen("AI1|RESPONSE|3|retry\n"),&candidate));
    assert(!enqueue(&replay,&queue,"AI1|RESPONSE|3|retry\n"));
    assert(replay.last_id==2); /* Rejected queue overflow does not consume ID. */
    uint8_t out[AI_SAT_QUEUE_FRAME_MAX]; size_t len=0;
    assert(ai_sat_queue_pop(&queue,out,sizeof(out),&len));
    assert(enqueue(&replay,&queue,"AI1|RESPONSE|3|retry\n"));
    assert(!enqueue(&replay,&queue,"AI1|RESPONSE|3|retry\n"));
    puts("AI1 per-link replay tests passed");
    return 0;
}
