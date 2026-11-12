#ifndef FLIPPER_AI_CLIENT_H
#define FLIPPER_AI_CLIENT_H
#include "ai_wire.h"
#include <stdbool.h>
typedef enum {AI_CLIENT_OK=0,AI_CLIENT_BUSY,AI_CLIENT_NOT_READY,AI_CLIENT_BAD_RESPONSE,AI_CLIENT_REMOTE_ERROR,AI_CLIENT_NO_SPACE} AiClientResult;
typedef enum {AI_CLIENT_DISCONNECTED=0,AI_CLIENT_HANDSHAKE_PENDING,AI_CLIENT_READY,AI_CLIENT_ASK_PENDING,AI_CLIENT_PTT_PENDING} AiClientPhase;
typedef struct {AiClientPhase phase;uint32_t next_id;uint32_t pending_id;} AiClient;
/* Transport-neutral client; caller must authenticate BLE and reset on disconnect.
 * Only one outstanding request; transport adapter must honor MTU/write callbacks. */
void ai_client_reset(AiClient* client);
AiClientResult ai_client_hello(AiClient* client,char* output,size_t capacity,size_t* written);
AiClientResult ai_client_ask(AiClient* client,const char* question,char* output,size_t capacity,size_t* written);
/* One user-triggered, BLE-authenticated PTT request. ACK is not audio content. */
AiClientResult ai_client_ptt(AiClient* client,char* output,size_t capacity,size_t* written);
AiClientResult ai_client_receive(AiClient* client,const uint8_t* input,size_t length,AiWireMessage* response);
#endif
