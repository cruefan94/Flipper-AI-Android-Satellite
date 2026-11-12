#include "../targets/f7/ble_glue/services/ai_satellite_queue.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>
int main(void) {
    AiSatelliteQueue q = {0};
    uint8_t out[AI_SAT_QUEUE_FRAME_MAX];
    size_t got = 0;
    ai_sat_queue_reset(&q);
    assert(!ai_sat_queue_pop(&q,out,sizeof(out),&got));
    assert(!ai_sat_queue_push(&q,NULL,1));
    assert(!ai_sat_queue_push(&q,(const uint8_t*)"hi",0));
    uint8_t overflow[AI_SAT_QUEUE_FRAME_MAX+1]={0};
    assert(!ai_sat_queue_push(&q,overflow,sizeof(overflow)));
    const char* parts[] = {"alpha","bravo","charlie","delta"};
    for(unsigned i=0;i<AI_SAT_QUEUE_DEPTH;i++)
        assert(ai_sat_queue_push(&q,(const uint8_t*)parts[i],strlen(parts[i])));
    assert(!ai_sat_queue_push(&q,(const uint8_t*)"overflow",8));
    assert(q.dropped==1 && q.count==AI_SAT_QUEUE_DEPTH);
    size_t unchanged=77;
    assert(!ai_sat_queue_pop(&q,out,2,&unchanged));
    assert(unchanged==77 && q.count==AI_SAT_QUEUE_DEPTH);
    for(unsigned i=0;i<AI_SAT_QUEUE_DEPTH;i++) {
        assert(ai_sat_queue_pop(&q,out,sizeof(out),&got));
        assert(got==strlen(parts[i]) && memcmp(out,parts[i],got)==0);
    }
    assert(q.count==0);
    assert(ai_sat_queue_push(&q,(const uint8_t*)"wrap",4));
    assert(ai_sat_queue_pop(&q,out,sizeof(out),&got));
    assert(got==4 && memcmp(out,"wrap",4)==0);
    uint8_t max[AI_SAT_QUEUE_FRAME_MAX];
    memset(max,0x7e,sizeof(max));
    assert(ai_sat_queue_push(&q,max,sizeof(max)));
    assert(ai_sat_queue_pop(&q,out,sizeof(out),&got));
    assert(got==sizeof(max) && memcmp(max,out,sizeof(max))==0);
    puts("ai_satellite_queue tests passed");
    return 0;
}
