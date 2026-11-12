#include "../targets/f7/ble_glue/services/ai_satellite_trust.h"
#include <assert.h>
#include <stdio.h>
int main(void){
 AiSatelliteTrust s={0};
 assert(!ai_sat_trust_take_ready(&s));
 ai_sat_trust_connected(&s,23);
 assert(!ai_sat_trust_pairing(&s,true));
 assert(!ai_sat_trust_take_ready(&s)); /* paired but not encrypted */
 assert(!ai_sat_trust_encryption(&s,22,true,true)); /* wrong connection */
 assert(ai_sat_trust_encryption(&s,23,true,true));
 assert(ai_sat_trust_take_ready(&s));
 assert(!ai_sat_trust_take_ready(&s)); /* exactly once */
 assert(!ai_sat_trust_encryption(&s,23,true,false));
 assert(!ai_sat_trust_take_ready(&s)); /* encryption lost */
 assert(ai_sat_trust_encryption(&s,23,true,true));
 assert(ai_sat_trust_take_ready(&s)); /* explicit revalidation */
 ai_sat_trust_reset(&s);
 assert(!ai_sat_trust_take_ready(&s));
 ai_sat_trust_connected(&s,24);
 assert(!ai_sat_trust_encryption(&s,24,true,true)); /* encryption alone must not authorize a reconnect */
 assert(!ai_sat_trust_take_ready(&s));
 assert(ai_sat_trust_pairing(&s,true));
 assert(ai_sat_trust_take_ready(&s));
 ai_sat_trust_reset(&s);
 ai_sat_trust_connected(&s,0); /* Valid HCI ACL handle */
 assert(!ai_sat_trust_pairing(&s,true));
 assert(ai_sat_trust_encryption(&s,0,true,true));
 assert(ai_sat_trust_take_ready(&s));
 /* Previously bonded Android: new security procedure + active encryption,
  * but no new pairing-complete event, must be able to reconnect securely. */
 ai_sat_trust_reset(&s);
 ai_sat_trust_connected(&s,31);
 assert(!ai_sat_trust_security_started(&s));
 assert(!ai_sat_trust_take_ready(&s));
 assert(ai_sat_trust_encryption(&s,31,true,true));
 assert(ai_sat_trust_take_ready(&s));
 assert(!ai_sat_trust_take_ready(&s));
 ai_sat_trust_reset(&s);
 ai_sat_trust_connected(&s,32);
 assert(!ai_sat_trust_security_started(&s));
 assert(!ai_sat_trust_encryption(&s,32,false,true));
 assert(!ai_sat_trust_take_ready(&s));
 ai_sat_trust_reset(&s);
 ai_sat_trust_connected(&s,33);
 assert(!ai_sat_trust_encryption(&s,33,true,true));
 assert(ai_sat_trust_security_started(&s));
 assert(ai_sat_trust_take_ready(&s));
 /* Pairing failure immediately removes authorization, even if the
  * controller still reports encryption before the ACL disconnect.
  */
 ai_sat_trust_reset(&s);
 ai_sat_trust_connected(&s,34);
 assert(!ai_sat_trust_security_started(&s));
 assert(ai_sat_trust_encryption(&s,34,true,true));
 assert(ai_sat_trust_take_ready(&s));
 assert(!ai_sat_trust_pairing(&s,false));
 assert(!ai_sat_trust_take_ready(&s));
 assert(!ai_sat_trust_encryption(&s,34,true,true));
 assert(!ai_sat_trust_take_ready(&s));
 puts("ai_satellite_trust tests passed");
 return 0;
}
