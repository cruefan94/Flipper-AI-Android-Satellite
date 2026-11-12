#include "ai_link.h"
#include <string.h>
void ai_link_init(AiLink* link, AiLinkWrite write, void* context) {
 if(!link)return;
 memset(link,0,sizeof(*link));link->write=write;link->context=context;ai_client_reset(&link->client);
}
void ai_link_connection(AiLink* link,bool connected,size_t max_payload) {
 if(!link)return;
 ai_client_reset(&link->client);link->write_pending=false;link->deadline_active=false;
 if(!connected)link->reconnect_required=false;
 link->connected=connected&&!link->reconnect_required&&link->write&&max_payload>0;
 link->max_payload=link->connected?(max_payload<AI_WIRE_MAX_FRAME?max_payload:AI_WIRE_MAX_FRAME):0;
}
static AiClientResult send(AiLink* link,const char* prompt,bool ptt) {
 if(!link||!link->connected||link->reconnect_required||!link->write)return AI_CLIENT_NOT_READY;
 if(link->write_pending)return AI_CLIENT_BUSY;
 char frame[AI_WIRE_MAX_FRAME+1];size_t length=0;
 AiClientResult r=ptt?ai_client_ptt(&link->client,frame,sizeof(frame),&length):
                         prompt?ai_client_ask(&link->client,prompt,frame,sizeof(frame),&length):
                                ai_client_hello(&link->client,frame,sizeof(frame),&length);
 if(r!=AI_CLIENT_OK)return r;
 if(length>link->max_payload||!link->write((const uint8_t*)frame,length,link->context)) {
  ai_client_reset(&link->client);link->deadline_active=false;
  link->reconnect_required=true;link->connected=false;return AI_CLIENT_NO_SPACE;
 }
 link->write_pending=true;return AI_CLIENT_OK;
}
AiClientResult ai_link_hello(AiLink* link){return send(link,NULL,false);}
AiClientResult ai_link_ask(AiLink* link,const char* prompt){
 if(!prompt)return AI_CLIENT_BAD_RESPONSE;
 return send(link,prompt,false);
}
AiClientResult ai_link_ptt(AiLink* link){return send(link,NULL,true);}
void ai_link_write_complete(AiLink* link,bool success){
 if(!link||!link->write_pending)return;
 link->write_pending=false;
 if(!success){ai_client_reset(&link->client);link->deadline_active=false;
  link->reconnect_required=true;link->connected=false;}
}
AiClientResult ai_link_notification(AiLink* link,const uint8_t* frame,size_t length,AiWireMessage* reply){
 if(!link||!link->connected||link->write_pending)return AI_CLIENT_NOT_READY;
 if(!frame||length>link->max_payload)return AI_CLIENT_BAD_RESPONSE;
 AiClientResult result=ai_client_receive(&link->client,frame,length,reply);
 if(result==AI_CLIENT_OK||result==AI_CLIENT_REMOTE_ERROR)link->deadline_active=false;
 return result;
}

void ai_link_set_deadline(AiLink* link,uint32_t now_ms,uint32_t timeout_ms){
 if(!link||!link->connected||timeout_ms==0||timeout_ms>=0x80000000u)return;
 if(link->write_pending||link->client.phase==AI_CLIENT_HANDSHAKE_PENDING||
    link->client.phase==AI_CLIENT_ASK_PENDING||
    link->client.phase==AI_CLIENT_PTT_PENDING){
  link->deadline_ms=now_ms+timeout_ms;link->deadline_active=true;
 }
}
bool ai_link_poll_timeout(AiLink* link,uint32_t now_ms){
 if(!link||!link->connected||!link->deadline_active)return false;
 if((int32_t)(now_ms-link->deadline_ms)<0)return false;
 link->deadline_active=false;link->write_pending=false;
 ai_client_reset(&link->client);link->reconnect_required=true;link->connected=false;return true;
}
