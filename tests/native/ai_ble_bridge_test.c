#include "../../flipper_app/satellite_protocol/ai_ble_bridge.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>
typedef struct {char frame[181];int writes;int disconnects;} Test;
static bool tx(const uint8_t* data,size_t length,void* ctx){
 Test* t=ctx;assert(length<=180);memcpy(t->frame,data,length);
 t->frame[length]=0;t->writes++;return true;
}
static void disconnect_cb(void* ctx){((Test*)ctx)->disconnects++;}
int main(void){
 Test t={0};AiBleBridge b;AiWireMessage response;
 ai_ble_bridge_init(&b,tx,&t);
 assert(ai_ble_bridge_start(&b,0)==AI_CLIENT_NOT_READY);
 ai_ble_bridge_connected(&b,false,true,182);
 assert(ai_ble_bridge_start(&b,0)==AI_CLIENT_NOT_READY);
 ai_ble_bridge_connected(&b,true,false,182);
 assert(ai_ble_bridge_start(&b,0)==AI_CLIENT_NOT_READY);
 ai_ble_bridge_connected(&b,true,true,20);
 assert(ai_ble_bridge_start(&b,0)==AI_CLIENT_NOT_READY);
 ai_ble_bridge_connected(&b,true,true,182);
 assert(ai_ble_bridge_start(&b,100)==AI_CLIENT_OK);
 assert(strcmp(t.frame,"AI1|HELLO|1|flipper-v1\n")==0);
 const char* hello="AI1|HELLO_ACK|1|android-v1\n";
 assert(ai_ble_bridge_notification(&b,(const uint8_t*)hello,strlen(hello),&response)==AI_CLIENT_NOT_READY);
 ai_ble_bridge_write_complete(&b,true);
 assert(ai_ble_bridge_notification(&b,(const uint8_t*)hello,strlen(hello),&response)==AI_CLIENT_OK);
 assert(ai_ble_bridge_ask(&b,"battery",200)==AI_CLIENT_OK);
 ai_ble_bridge_write_complete(&b,true);
 const char* answer="AI1|ANSWER|2|Battery check\n";
 assert(ai_ble_bridge_notification(&b,(const uint8_t*)answer,strlen(answer),&response)==AI_CLIENT_OK);
 assert(strcmp(response.payload,"Battery check")==0);
 assert(ai_ble_bridge_ptt(&b,260)==AI_CLIENT_OK);
 assert(strcmp(t.frame,"AI1|PTT|3|start\n")==0);
 ai_ble_bridge_write_complete(&b,true);
 const char* ptt_ack="AI1|PTT_ACK|3|accepted\n";
 assert(ai_ble_bridge_notification(&b,(const uint8_t*)ptt_ack,strlen(ptt_ack),&response)==AI_CLIENT_OK);
 assert(b.link.client.phase==AI_CLIENT_READY);
 assert(ai_ble_bridge_ptt(&b,270)==AI_CLIENT_OK);
 ai_ble_bridge_write_complete(&b,true);
 const char* ptt_denied="AI1|ERROR|4|PTT_DENIED\n";
 assert(ai_ble_bridge_notification(&b,(const uint8_t*)ptt_denied,strlen(ptt_denied),&response)==AI_CLIENT_REMOTE_ERROR);
 assert(b.link.client.phase==AI_CLIENT_READY);
 assert(ai_ble_bridge_ask(&b,"storage",300)==AI_CLIENT_OK);
 ai_ble_bridge_write_complete(&b,true);
 ai_ble_bridge_tick(&b,30301,disconnect_cb,&t);
 assert(t.disconnects==1);
 ai_ble_bridge_tick(&b,30400,disconnect_cb,&t);
 assert(t.disconnects==1);
 assert(ai_ble_bridge_ask(&b,"battery",30400)==AI_CLIENT_NOT_READY);
 ai_ble_bridge_disconnected(&b);
 ai_ble_bridge_connected(&b,true,true,182);
 assert(ai_ble_bridge_start(&b,40000)==AI_CLIENT_OK);
 ai_ble_bridge_write_complete(&b,false);
 ai_ble_bridge_tick(&b,40001,disconnect_cb,&t);
 assert(t.disconnects==2);
 /* PTT timeout is a protocol fault: never auto-retry the microphone
  * request on a stale authenticated link. */
 Test silent={0};AiBleBridge ptt_link;
 ai_ble_bridge_init(&ptt_link,tx,&silent);
 ai_ble_bridge_connected(&ptt_link,true,true,180);
 assert(ai_ble_bridge_start(&ptt_link,100)==AI_CLIENT_OK);
 ai_ble_bridge_write_complete(&ptt_link,true);
 assert(ai_ble_bridge_notification(&ptt_link,(const uint8_t*)hello,strlen(hello),&response)==AI_CLIENT_OK);
 assert(ai_ble_bridge_ptt(&ptt_link,200)==AI_CLIENT_OK);
 ai_ble_bridge_write_complete(&ptt_link,true);
 ai_ble_bridge_tick(&ptt_link,5199,disconnect_cb,&silent);
 assert(silent.disconnects==0);
 ai_ble_bridge_tick(&ptt_link,5200,disconnect_cb,&silent);
 assert(silent.disconnects==1);
 assert(ai_ble_bridge_ptt(&ptt_link,5201)==AI_CLIENT_NOT_READY);
 ai_ble_bridge_tick(&ptt_link,5300,disconnect_cb,&silent);
 assert(silent.disconnects==1);
 puts("ai_ble_bridge native tests passed");
}
