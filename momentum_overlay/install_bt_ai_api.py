#!/usr/bin/env python3
"""Patch a separate pinned Momentum tree with queued BT AI APIs. Source only."""
from pathlib import Path
import sys

def once(data, old, new):
    n=data.count(old)
    if n!=1:raise ValueError(f"Expected one BT API anchor, found {n}: {old[:90]}")
    return data.replace(old,new,1)

def patch_public(s):
    if "bt_ai_satellite_status(" in s:
        raise ValueError("AI BT public API already installed")
    anchor="/** Change BLE Profile"
    api="""/* Experimental synchronous BT-service-queued AI access.
 * Status true only for a verified, live and notification-subscribed session.
 * Application-thread only; never use from BT or GAP callbacks.
 */
bool bt_ai_satellite_status(Bt* bt, size_t* payload, uint32_t* generation);
bool bt_ai_satellite_receive(Bt* bt, uint8_t* dst, size_t capacity, size_t* read);
bool bt_ai_satellite_send(Bt* bt, const uint8_t* src, size_t length);

"""
    s=once(s,"#include <stdint.h>","#include <stdint.h>\n#include <stddef.h>")
    return once(s,anchor,api+anchor)

def patch_private(s):
    s=once(s,"    BtMessageTypeUpdateStatus,","    BtMessageTypeAiStatus,\n    BtMessageTypeAiReceive,\n    BtMessageTypeAiSend,\n    BtMessageTypeUpdateStatus,")
    s=once(s,"typedef union {", """typedef struct {
    uint8_t* output;
    const uint8_t* input;
    size_t capacity;
    size_t length;
    size_t* read;
    size_t* payload;
    uint32_t* generation;
    bool success;
} BtAiSatelliteRequest;

typedef union {""")
    s=once(s,"typedef union {\n    uint32_t pin_code;",
        "typedef union {\n    BtAiSatelliteRequest* ai_request;\n    uint32_t pin_code;")
    return s

def patch_bt(s):
    anchor="        if(message.type == BtMessageTypeUpdateStatus) {"
    replacement="""        if(message.type == BtMessageTypeAiStatus ||
           message.type == BtMessageTypeAiReceive ||
           message.type == BtMessageTypeAiSend) {
            BtAiSatelliteRequest* ai=message.data.ai_request;
            if(ai) {
                ai->success=false;
                if(bt->ai_verified && bt->current_profile &&
                   bt->current_profile->config == ble_profile_serial) {
                    size_t available=0; uint32_t epoch=0;
                    bool ok=ble_profile_serial_ai_ready(
                        bt->current_profile, &available, &epoch);
                    if(ok && message.type == BtMessageTypeAiStatus &&
                       ai->payload && ai->generation) {
                        *ai->payload=available; *ai->generation=epoch;
                        ai->success=true;
                    } else if(ok && message.type == BtMessageTypeAiReceive &&
                              ai->output && ai->read && ai->capacity<=180u) {
                        ai->success=ble_profile_serial_ai_pop_rx(
                            bt->current_profile, ai->output, ai->capacity, ai->read);
                    } else if(ok && message.type == BtMessageTypeAiSend &&
                              ai->input && ai->length>0 &&
                              ai->length<=available && ai->length<=180u) {
                        ai->success=ble_profile_serial_ai_notify(
                            bt->current_profile, ai->input, ai->length);
                    }
                }
            }
        } else if(message.type == BtMessageTypeUpdateStatus) {"""
    return once(s,anchor,replacement)

def patch_api(s):
    s += """
/* Synchronous queue handoff keeps stack-allocated request buffers live.
 * Never call from BT/GAP threads. Authentication remains firmware-owned.
 */
static bool bt_ai_call(Bt* bt, BtMessageType kind, BtAiSatelliteRequest* request) {
    if(!bt || !request)return false;
    BtMessage msg={
        .lock=api_lock_alloc_locked(),
        .type=kind,
        .data.ai_request=request,
    };
    if(furi_message_queue_put(bt->message_queue, &msg, 100)!=FuriStatusOk) {
        api_lock_unlock(msg.lock);
        api_lock_wait_unlock_and_free(msg.lock);
        return false;
    }
    api_lock_wait_unlock_and_free(msg.lock);
    return request->success;
}
bool bt_ai_satellite_status(Bt* bt,size_t* payload,uint32_t* generation) {
    if(!bt || !payload || !generation)return false;
    BtAiSatelliteRequest r={.payload=payload,.generation=generation};
    return bt_ai_call(bt,BtMessageTypeAiStatus,&r);
}
bool bt_ai_satellite_receive(Bt* bt,uint8_t* output,size_t capacity,size_t* read) {
    if(!bt || !output || !read || capacity==0 || capacity>180u)return false;
    BtAiSatelliteRequest r={.output=output,.capacity=capacity,.read=read};
    return bt_ai_call(bt,BtMessageTypeAiReceive,&r);
}
bool bt_ai_satellite_send(Bt* bt,const uint8_t* bytes,size_t length) {
    if(!bt || !bytes || length==0 || length>180u)return false;
    BtAiSatelliteRequest r={.input=bytes,.length=length};
    return bt_ai_call(bt,BtMessageTypeAiSend,&r);
}
"""
    return s

PATCH={
"applications/services/bt/bt_service/bt.h":patch_public,
"applications/services/bt/bt_service/bt_i.h":patch_private,
"applications/services/bt/bt_service/bt.c":patch_bt,
"applications/services/bt/bt_service/bt_api.c":patch_api,
}

def main(root):
    changed={}
    for rel,change in PATCH.items():
        path=root/rel
        if not path.is_file():raise FileNotFoundError(path)
        source=path.read_text()
        if "bt_ai_satellite_status(" in source:
            raise ValueError("BT AI API already installed: "+rel)
        changed[path]=change(source)
    for path,content in changed.items():path.write_text(content)
    print("Installed queued AI BT service accessors (firmware build not yet verified).")

if __name__=="__main__":
    if len(sys.argv)!=2:raise SystemExit("Usage: install_bt_ai_api.py /path/to/Momentum-Firmware")
    main(Path(sys.argv[1]).resolve())
