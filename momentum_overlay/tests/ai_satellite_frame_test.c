#include "../targets/f7/ble_glue/services/ai_satellite_frame.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>
static bool accepts(const char* value) {
    return ai_sat_valid_frame((const uint8_t*)value,strlen(value));
}
int main(void) {
    assert(accepts("AI1|HELLO|1|android-v1\n"));
    assert(accepts("AI1|ASK|4294967295|Battery status\n"));
    assert(accepts("AI1|PING|2|ok\n"));
    assert(!accepts("AI1|ASK|4294967296|overflow\n"));
    assert(!accepts("AI1|ASK|-1|invalid\n"));
    assert(!accepts("AI1|ASK||missing id\n"));
    assert(!accepts("AI1|ask|1|invalid type\n"));
    assert(!accepts("AI1|ASK|1|bad|separator\n"));
    assert(!accepts("AI1|ASK|1|line\rbreak\n"));
    assert(!accepts("AI1|ASK|1|embedded\nline\n"));
    assert(!accepts("AI1|ASK|1|missing LF"));
    assert(!accepts("AI2|ASK|1|wrong version\n"));
    assert(!ai_sat_valid_frame(NULL,12));
    char too_long[AI_SAT_WIRE_MAX+2];
    memset(too_long,'x',sizeof(too_long));
    too_long[sizeof(too_long)-1]='\n';
    assert(!ai_sat_valid_frame((const uint8_t*)too_long,sizeof(too_long)));
    puts("AI1 frame validation tests passed");
    return 0;
}
