#include "ai_app_transport.h"
#include <string.h>

/* ai_link write only queues a GATT notification; it does not mean peer received it. */
static bool ai_app_write(const uint8_t* frame, size_t length, void* ctx) {
    AiAppTransport* app = ctx;
    return app && app->session_ready && app->status_snapshot_ready && app->io.notify &&
           app->io.notify(frame, length, app->io.context);
}
static void ai_app_disconnect_request(void* ctx) {
    AiAppTransport* app = ctx;
    if(app && app->io.request_disconnect)app->io.request_disconnect(app->io.context);
}
void ai_app_transport_init(AiAppTransport* app, AiAppTransportIo io) {
    if(!app)return;
    memset(app,0,sizeof(*app));
    app->io=io;
    ai_ble_bridge_init(&app->bridge,ai_app_write,app);
}
void ai_app_transport_reset(AiAppTransport* app) {
    if(!app)return;
    ai_ble_bridge_disconnected(&app->bridge);
    app->session_ready=false;
    app->status_snapshot_ready=false;
    app->hello_requested=false;
    app->require_link_drop=false;
    app->last_generation=app->bridge.transport_generation;
    app->trusted_generation_valid=false;
}
bool ai_app_transport_available(const AiAppTransport* app) {
    return app && app->session_ready && app->status_snapshot_ready &&
           app->bridge.link.connected &&
           app->bridge.link.client.phase==AI_CLIENT_READY &&
           !app->bridge.link.reconnect_required;
}
AiClientResult ai_app_transport_ask(AiAppTransport* app,const char* prompt,uint32_t now_ms) {
    if(!ai_app_transport_available(app))return AI_CLIENT_NOT_READY;
    if(!prompt)return AI_CLIENT_BAD_RESPONSE;
    /* User-input size is a normal validation failure, not link corruption.
     * Preflight against negotiated ATT length before ai_link_ask mutates
     * the pending request ID and potentially forces an unnecessary disconnect. */
    char preview[AI_WIRE_MAX_FRAME+1];
    size_t preview_len=0;
    if(ai_wire_format(
          preview,sizeof(preview),"ASK",app->bridge.link.client.next_id,
          prompt,&preview_len)!=AI_WIRE_OK ||
       preview_len>app->bridge.link.max_payload)
        return AI_CLIENT_NO_SPACE;
    AiClientResult result=ai_ble_bridge_ask(&app->bridge,prompt,now_ms);
    /* BLE notifications don't get an application write-completion event.
     * A successful local GATT submit is *not* peer acknowledgement.
     * Protocol response or deadline confirms progress.
     */
    if(result==AI_CLIENT_OK)ai_ble_bridge_write_complete(&app->bridge,true);
    return result;
}
AiClientResult ai_app_transport_ptt(AiAppTransport* app,uint32_t now_ms) {
    if(!ai_app_transport_available(app))return AI_CLIENT_NOT_READY;
    /* The Flipper PTT button is explicit user intent, not a background task.
     * Preview message size before touching the client request-id state. */
    char preview[AI_WIRE_MAX_FRAME+1];
    size_t preview_len=0;
    if(ai_wire_format(preview,sizeof(preview),"PTT",
         app->bridge.link.client.next_id,"start",&preview_len)!=AI_WIRE_OK ||
       preview_len>app->bridge.link.max_payload)
        return AI_CLIENT_NO_SPACE;
    AiClientResult result=ai_ble_bridge_ptt(&app->bridge,now_ms);
    if(result==AI_CLIENT_OK)ai_ble_bridge_write_complete(&app->bridge,true);
    return result;
}
void ai_app_transport_poll(
    AiAppTransport* app,uint32_t now_ms,AiWireMessage* output,AiClientResult* outcome) {
    if(!app || !output || !outcome)return;
    *outcome=AI_CLIENT_NOT_READY;
    size_t payload=0;
    uint32_t trusted_generation=0;
    bool ready=app->io.ready &&
        app->io.ready(app->io.context,&payload,&trusted_generation);
    /* Epoch changes revoke the old handshake, even if the caller skipped
     * polling during the physical disconnect/reconnect transition. */
    if(ready && app->trusted_generation_valid &&
       app->trusted_generation!=trusted_generation) {
        ai_app_transport_reset(app);
    }
    /* Actual ATT payload can exceed our deliberately bounded AI1 single-frame
     * protocol maximum. Clamp; do not treat a larger negotiated MTU as invalid. */
    if(payload>AI_WIRE_MAX_FRAME)payload=AI_WIRE_MAX_FRAME;
    /* Call ready on every poll: the trusted firmware service is authoritative.
     * Never infer authentication from bonding or BLE characteristic presence.
     */
    /* AiBleBridge currently requires >=32 ATT payload for protocol startup. */
    if(!ready || payload<32u) {
        /* Query contention is not a new physical connection. Temporarily
         * disable writes without resetting HELLO/ASK or discarding the trusted
         * generation. Only a changed generation can restart the protocol. */
        app->status_snapshot_ready=false;
        ai_ble_bridge_tick(&app->bridge,now_ms,ai_app_disconnect_request,app);
        if(app->bridge.link.reconnect_required) {
            app->require_link_drop=true;
            app->session_ready=false;
        }
        return;
    }
    app->status_snapshot_ready=true;
    if(app->require_link_drop)return;
    if(!app->session_ready) {
        app->trusted_generation=trusted_generation;
        app->trusted_generation_valid=true;
        app->session_ready=true;
        ai_ble_bridge_connected(&app->bridge,true,true,payload);
        app->last_generation=app->bridge.transport_generation;
    }
    if(!app->hello_requested) {
        AiClientResult result=ai_ble_bridge_start(&app->bridge,now_ms);
        if(result==AI_CLIENT_OK) {
            app->hello_requested=true;
            ai_ble_bridge_write_complete(&app->bridge,true);
        } else if(result!=AI_CLIENT_BUSY) {
            *outcome=result;
            /* Failed HELLO submission can set reconnect_required before a
             * response deadline exists. Request disconnect and latch the
             * current link immediately instead of retrying forever. */
            if(app->bridge.link.reconnect_required) {
                ai_ble_bridge_tick(&app->bridge,now_ms,ai_app_disconnect_request,app);
                app->session_ready=false;
                app->require_link_drop=true;
            }
        }
        return;
    }
    if(app->io.receive) {
        uint8_t frame[AI_WIRE_MAX_FRAME];
        size_t length=0;
        unsigned budget=4;
        while(budget-- && app->io.receive(frame,sizeof(frame),&length,app->io.context)) {
        if(!length || length>payload || length>sizeof(frame)) {
            app->bridge.link.reconnect_required=true;
            break;
        }
        *outcome=ai_ble_bridge_notification(&app->bridge,frame,length,output);
        if(*outcome==AI_CLIENT_BAD_RESPONSE ||
           (*outcome==AI_CLIENT_REMOTE_ERROR &&
            app->bridge.link.client.phase==AI_CLIENT_DISCONNECTED)) {
            /* A malformed reply or rejected HELLO requires a fresh, trusted
             * connection; an ordinary ASK error remains recoverable. */
            app->bridge.link.reconnect_required=true;
            break;
        }
        if(*outcome==AI_CLIENT_OK || *outcome==AI_CLIENT_REMOTE_ERROR)break;
        }
    }
    /* Deadlines must advance even when a transport has no receive callback.
     * Missing/unresponsive receive paths cannot wedge an ASK forever. */
    ai_ble_bridge_tick(&app->bridge,now_ms,ai_app_disconnect_request,app);
    if(app->bridge.link.reconnect_required) {
        app->session_ready=false;
        app->require_link_drop=true;
    }
}
