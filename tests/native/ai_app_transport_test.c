#include "../../flipper_app/satellite_protocol/ai_app_transport.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>
typedef struct {
    bool ready;
    size_t mtu;
    uint32_t generation;
    unsigned sends,disconnects;
    char last_sent[181];
    char recv[181];
    bool has_recv;
    bool reject_send;
} Fake;
static bool ready(void* ctx,size_t* mtu,uint32_t* generation) {
    Fake* f=ctx;
    if(f->ready){*mtu=f->mtu;*generation=f->generation;}
    return f->ready;
}
static bool notify(const uint8_t* data,size_t size,void* ctx) {
    Fake* f=ctx;
    if(size>180 || f->reject_send)return false;
    memcpy(f->last_sent,data,size);f->last_sent[size]=0;f->sends++;
    return true;
}
static bool receive(uint8_t* data,size_t size,size_t* count,void* ctx) {
    Fake* f=ctx;
    if(!f->has_recv)return false;
    size_t len=strlen(f->recv);
    if(size<len)return false;
    memcpy(data,f->recv,len);*count=len;f->has_recv=false;return true;
}
static void disconnect_request(void* ctx){((Fake*)ctx)->disconnects++;}
static void push(Fake* f,const char* frame) {
    strcpy(f->recv,frame);f->has_recv=true;
}
int main(void){
    Fake f={0};AiAppTransport app;
    AiAppTransportIo io={ready,notify,receive,disconnect_request,&f};
    AiWireMessage reply;AiClientResult result;
    ai_app_transport_init(&app,io);
    ai_app_transport_poll(&app,0,&reply,&result);
    assert(!f.sends && !ai_app_transport_available(&app));
    f.ready=true;f.mtu=20;f.generation=1;
    ai_app_transport_poll(&app,50,&reply,&result);
    assert(f.sends==0); /* Wait for negotiated MTU instead of failing HELLO. */
    f.mtu=182;
    ai_app_transport_poll(&app,100,&reply,&result);
    assert(f.sends==1 && strcmp(f.last_sent,"AI1|HELLO|1|flipper-v1\n")==0);
    assert(!ai_app_transport_available(&app));
    push(&f,"AI1|HELLO_ACK|1|android-v1\n");
    ai_app_transport_poll(&app,200,&reply,&result);
    assert(result==AI_CLIENT_OK && ai_app_transport_available(&app));
    /* A transient BT status lock failure must pause sends, not restart HELLO.
     * The Android peer keeps the existing HELLO handshake on this link. */
    f.ready=false;
    ai_app_transport_poll(&app,205,&reply,&result);
    assert(!ai_app_transport_available(&app));
    assert(f.sends==1);
    f.ready=true;
    ai_app_transport_poll(&app,210,&reply,&result);
    assert(ai_app_transport_available(&app));
    assert(f.sends==1);
    /* An ASK exceeding the negotiated single-frame limit is an input error.
     * It must not destroy the paired BLE session or consume request ID 2. */
    char too_long[185];
    memset(too_long,'x',sizeof(too_long)-1);
    too_long[sizeof(too_long)-1]='\0';
    assert(ai_app_transport_ask(&app,too_long,240)==AI_CLIENT_NO_SPACE);
    assert(ai_app_transport_available(&app) && f.disconnects==0 && f.sends==1);
    assert(ai_app_transport_ask(&app,"battery help",250)==AI_CLIENT_OK);
    assert(strcmp(f.last_sent,"AI1|ASK|2|battery help\n")==0);
    push(&f,"AI1|ANSWER|2|Power checks\n");
    ai_app_transport_poll(&app,350,&reply,&result);
    assert(result==AI_CLIENT_OK && !strcmp(reply.payload,"Power checks"));
    assert(ai_app_transport_ask(&app,"storage",400)==AI_CLIENT_OK);
    ai_app_transport_poll(&app,31000,&reply,&result);
    assert(f.disconnects==1 && !ai_app_transport_available(&app));
    ai_app_transport_poll(&app,31001,&reply,&result);
    assert(f.sends==3); /* protocol fault blocks new hello on same live link */
    f.ready=false;
    ai_app_transport_poll(&app,31002,&reply,&result);
    assert(!ai_app_transport_available(&app));
    f.ready=true; /* A transient false status is not a verified new link. */
    ai_app_transport_poll(&app,31900,&reply,&result);
    assert(f.sends==3);
    f.generation=2;
    ai_app_transport_poll(&app,32000,&reply,&result);
    assert(f.sends==4 && strcmp(f.last_sent,"AI1|HELLO|1|flipper-v1\n")==0);
    push(&f,"AI1|HELLO_ACK|1|android-v1\n");
    ai_app_transport_poll(&app,32100,&reply,&result);
    assert(ai_app_transport_available(&app));
    f.generation=3; /* Changed trusted link without poll observing disconnect */
    ai_app_transport_poll(&app,32200,&reply,&result);
    assert(f.sends==5 && !ai_app_transport_available(&app));
    assert(strcmp(f.last_sent,"AI1|HELLO|1|flipper-v1\n")==0);
    /* GATT rejection of initial HELLO must request disconnect immediately,
     * and must not hammer the same apparently-ready connection. */
    Fake rejected={0};
    rejected.ready=true;rejected.mtu=180;rejected.generation=5;
    rejected.reject_send=true;
    AiAppTransport failed;
    AiAppTransportIo reject_io={ready,notify,receive,disconnect_request,&rejected};
    ai_app_transport_init(&failed,reject_io);
    ai_app_transport_poll(&failed,40000,&reply,&result);
    assert(result==AI_CLIENT_NO_SPACE);
    assert(rejected.disconnects==1);
    assert(!failed.session_ready && failed.require_link_drop);
    ai_app_transport_poll(&failed,40001,&reply,&result);
    assert(rejected.disconnects==1 && rejected.sends==0);
    rejected.reject_send=false;rejected.generation=6;
    ai_app_transport_poll(&failed,40100,&reply,&result);
    assert(rejected.sends==1);
    /* An unavailable receive function must not disable the HELLO deadline.
     * The app must request one disconnect rather than wait indefinitely. */
    Fake no_rx={0};
    no_rx.ready=true;no_rx.mtu=180;no_rx.generation=10;
    AiAppTransport no_rx_app;
    AiAppTransportIo no_rx_io={ready,notify,NULL,disconnect_request,&no_rx};
    ai_app_transport_init(&no_rx_app,no_rx_io);
    ai_app_transport_poll(&no_rx_app,100,&reply,&result);
    assert(no_rx.sends==1 && no_rx.disconnects==0);
    ai_app_transport_poll(&no_rx_app,5101,&reply,&result);
    assert(no_rx.disconnects==1 && no_rx_app.require_link_drop);
    ai_app_transport_poll(&no_rx_app,5102,&reply,&result);
    assert(no_rx.disconnects==1 && no_rx.sends==1);
    /* PTT is user-triggered only, after a validated HELLO. It must never
     * consume another request ID until ACK or explicit ERROR is processed. */
    Fake ptt={0};
    ptt.ready=true;ptt.mtu=180;ptt.generation=20;
    AiAppTransport ptt_app;
    AiAppTransportIo ptt_io={ready,notify,receive,disconnect_request,&ptt};
    ai_app_transport_init(&ptt_app,ptt_io);
    assert(ai_app_transport_ptt(&ptt_app,1)==AI_CLIENT_NOT_READY);
    ai_app_transport_poll(&ptt_app,100,&reply,&result);
    assert(ptt.sends==1);
    push(&ptt,"AI1|HELLO_ACK|1|android-v1\\n");
    ai_app_transport_poll(&ptt_app,110,&reply,&result);
    assert(ai_app_transport_available(&ptt_app));
    assert(ai_app_transport_ptt(&ptt_app,120)==AI_CLIENT_OK);
    assert(strcmp(ptt.last_sent,"AI1|PTT|2|start\\n")==0);
    assert(ai_app_transport_ptt(&ptt_app,121)==AI_CLIENT_NOT_READY);
    push(&ptt,"AI1|PTT_ACK|2|accepted\\n");
    ai_app_transport_poll(&ptt_app,130,&reply,&result);
    assert(result==AI_CLIENT_OK);
    assert(ptt_app.bridge.link.client.phase==AI_CLIENT_READY);
    assert(ai_app_transport_ptt(&ptt_app,140)==AI_CLIENT_OK);
    assert(strcmp(ptt.last_sent,"AI1|PTT|3|start\\n")==0);
    push(&ptt,"AI1|ERROR|3|PTT_DENIED\\n");
    ai_app_transport_poll(&ptt_app,150,&reply,&result);
    assert(result==AI_CLIENT_REMOTE_ERROR);
    assert(ptt_app.bridge.link.client.phase==AI_CLIENT_READY);
    assert(ptt.disconnects==0);
    puts("ai_app_transport tests passed");
}
