#include "ai_client.h"
#include <string.h>
#include <limits.h>
void ai_client_reset(AiClient* c) {
 if(c){c->phase=AI_CLIENT_DISCONNECTED;c->next_id=1;c->pending_id=0;}
}
AiClientResult ai_client_hello(AiClient* c,char* out,size_t cap,size_t* n) {
 if(n)*n=0;
 if(!c||!out||!n)return AI_CLIENT_BAD_RESPONSE;
 if(c->phase!=AI_CLIENT_DISCONNECTED)return AI_CLIENT_BUSY;
 size_t len=0;
 if(ai_wire_format(out,cap,"HELLO",c->next_id,"flipper-v1",&len)!=AI_WIRE_OK)return AI_CLIENT_NO_SPACE;
 c->pending_id=c->next_id++;c->phase=AI_CLIENT_HANDSHAKE_PENDING;*n=len;return AI_CLIENT_OK;
}
AiClientResult ai_client_ask(AiClient* c,const char* question,char* out,size_t cap,size_t* n) {
 if(n)*n=0;
 if(!c||!question||!out||!n)return AI_CLIENT_BAD_RESPONSE;
 if(c->phase!=AI_CLIENT_READY || c->next_id==UINT32_MAX)return AI_CLIENT_NOT_READY;
 size_t len=0;
 if(ai_wire_format(out,cap,"ASK",c->next_id,question,&len)!=AI_WIRE_OK)return AI_CLIENT_NO_SPACE;
 c->pending_id=c->next_id++;c->phase=AI_CLIENT_ASK_PENDING;*n=len;return AI_CLIENT_OK;
}
AiClientResult ai_client_ptt(AiClient* c,char* out,size_t cap,size_t* n) {
 if(n)*n=0;
 if(!c||!out||!n)return AI_CLIENT_BAD_RESPONSE;
 if(c->phase!=AI_CLIENT_READY || c->next_id==UINT32_MAX)return AI_CLIENT_NOT_READY;
 size_t len=0;
 if(ai_wire_format(out,cap,"PTT",c->next_id,"start",&len)!=AI_WIRE_OK)return AI_CLIENT_NO_SPACE;
 c->pending_id=c->next_id++;c->phase=AI_CLIENT_PTT_PENDING;*n=len;return AI_CLIENT_OK;
}
AiClientResult ai_client_receive(AiClient* c,const uint8_t* input,size_t length,AiWireMessage* reply) {
 if(!c||!reply)return AI_CLIENT_BAD_RESPONSE;
 if(c->phase!=AI_CLIENT_HANDSHAKE_PENDING&&c->phase!=AI_CLIENT_ASK_PENDING&&c->phase!=AI_CLIENT_PTT_PENDING)return AI_CLIENT_NOT_READY;
 if(ai_wire_parse(input,length,reply)!=AI_WIRE_OK||reply->request_id!=c->pending_id)return AI_CLIENT_BAD_RESPONSE;
 if(strcmp(reply->type,"ERROR")==0){
  if(c->phase==AI_CLIENT_HANDSHAKE_PENDING)ai_client_reset(c);
  else c->phase=AI_CLIENT_READY;
  return AI_CLIENT_REMOTE_ERROR;
 }
 if(c->phase==AI_CLIENT_HANDSHAKE_PENDING){
  if(strcmp(reply->type,"HELLO_ACK")!=0||strcmp(reply->payload,"android-v1")!=0)return AI_CLIENT_BAD_RESPONSE;
  c->phase=AI_CLIENT_READY;return AI_CLIENT_OK;
 }
 if(c->phase==AI_CLIENT_PTT_PENDING) {
  if(strcmp(reply->type,"PTT_ACK")!=0||strcmp(reply->payload,"accepted")!=0)
   return AI_CLIENT_BAD_RESPONSE;
  c->phase=AI_CLIENT_READY;return AI_CLIENT_OK;
 }
 if(strcmp(reply->type,"ANSWER")!=0)return AI_CLIENT_BAD_RESPONSE;
 c->phase=AI_CLIENT_READY;return AI_CLIENT_OK;
}
