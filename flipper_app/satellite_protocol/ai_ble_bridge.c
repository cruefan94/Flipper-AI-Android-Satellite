#include "ai_ble_bridge.h"
#include <string.h>
#define AI_BLE_HELLO_TIMEOUT_MS 5000u
#define AI_BLE_ASK_TIMEOUT_MS 30000u
void ai_ble_bridge_init(AiBleBridge* bridge, AiLinkWrite write, void* context) {
    if(!bridge)return;
    memset(bridge,0,sizeof(*bridge));
    ai_link_init(&bridge->link,write,context);
}
void ai_ble_bridge_disconnected(AiBleBridge* bridge) {
    if(!bridge)return;
    ai_link_connection(&bridge->link,false,0);
    bridge->encrypted_and_authenticated=false;
    bridge->subscribed=false;
    bridge->disconnect_requested=false;
    bridge->transport_generation++;
}
void ai_ble_bridge_connected(AiBleBridge* bridge,bool authenticated,bool notifications_enabled,size_t payload) {
    if(!bridge)return;
    ai_ble_bridge_disconnected(bridge);
    bridge->encrypted_and_authenticated=authenticated;
    bridge->subscribed=notifications_enabled;
    if(authenticated&&notifications_enabled&&payload>=32)
        ai_link_connection(&bridge->link,true,payload);
}
static bool ready(AiBleBridge* bridge) {
    return bridge&&bridge->encrypted_and_authenticated&&bridge->subscribed&&
      bridge->link.connected&&!bridge->link.reconnect_required;
}
AiClientResult ai_ble_bridge_start(AiBleBridge* bridge,uint32_t now_ms) {
    if(!ready(bridge))return AI_CLIENT_NOT_READY;
    AiClientResult r=ai_link_hello(&bridge->link);
    if(r==AI_CLIENT_OK)ai_link_set_deadline(&bridge->link,now_ms,AI_BLE_HELLO_TIMEOUT_MS);
    return r;
}
AiClientResult ai_ble_bridge_ask(AiBleBridge* bridge,const char* prompt,uint32_t now_ms) {
    if(!ready(bridge))return AI_CLIENT_NOT_READY;
    AiClientResult r=ai_link_ask(&bridge->link,prompt);
    if(r==AI_CLIENT_OK)ai_link_set_deadline(&bridge->link,now_ms,AI_BLE_ASK_TIMEOUT_MS);
    return r;
}
AiClientResult ai_ble_bridge_ptt(AiBleBridge* bridge,uint32_t now_ms) {
    if(!ready(bridge))return AI_CLIENT_NOT_READY;
    AiClientResult r=ai_link_ptt(&bridge->link);
    if(r==AI_CLIENT_OK)ai_link_set_deadline(&bridge->link,now_ms,AI_BLE_HELLO_TIMEOUT_MS);
    return r;
}
void ai_ble_bridge_write_complete(AiBleBridge* bridge,bool ok) {
    if(bridge)ai_link_write_complete(&bridge->link,ok);
}
AiClientResult ai_ble_bridge_notification(AiBleBridge* bridge,const uint8_t* data,size_t length,AiWireMessage* output) {
    if(!ready(bridge))return AI_CLIENT_NOT_READY;
    return ai_link_notification(&bridge->link,data,length,output);
}
void ai_ble_bridge_tick(AiBleBridge* bridge,uint32_t now_ms,AiBleDisconnectRequest request,void* context) {
    if(!bridge)return;
    if(ai_link_poll_timeout(&bridge->link,now_ms))bridge->link.reconnect_required=true;
    if(bridge->link.reconnect_required&&!bridge->disconnect_requested) {
        bridge->disconnect_requested=true;
        if(request)request(context);
    }
}
