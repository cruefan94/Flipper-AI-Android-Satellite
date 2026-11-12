#pragma once
/* Strict AI1 frame validation before BLE data reaches application code.
 * No allocation, no hardware actions. UTF-8 payload bytes are opaque;
 * forbid C0 controls, pipe separators and DEL. Exactly one trailing LF.
 */
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#define AI_SAT_WIRE_MAX 180u
static inline bool ai_sat_valid_frame(const uint8_t* buf,size_t len) {
    if(!buf || len<11 || len>AI_SAT_WIRE_MAX || buf[len-1]!='\n')return false;
    if(buf[0]!='A'||buf[1]!='I'||buf[2]!='1'||buf[3]!='|')return false;
    size_t i=4;
    size_t kind=0;
    while(i<len && buf[i]!='|') {
        uint8_t c=buf[i++];
        if(!((c>='A'&&c<='Z')||c=='_') || ++kind>16)return false;
    }
    if(kind==0 || i>=len-1)return false;
    i++;
    uint64_t id=0;
    size_t digits=0;
    while(i<len && buf[i]!='|') {
        uint8_t c=buf[i++];
        if(c<'0'||c>'9'||++digits>10)return false;
        id=id*10u+(uint64_t)(c-'0');
        if(id>UINT32_MAX)return false;
    }
    if(digits==0 || i>=len-1)return false;
    i++;
    /* Empty payloads may be valid for PING/HELLO; reject embedded separators,
     * line breaks and ASCII controls so frames cannot contain extra commands.
     */
    for(;i<len-1;i++) {
        uint8_t c=buf[i];
        if(c=='|' || c<0x20u || c==0x7fu)return false;
    }
    return true;
}
