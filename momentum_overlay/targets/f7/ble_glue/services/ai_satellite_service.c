/* Flipper AI Satellite dedicated GATT service, additive to Momentum Serial RPC.
 * Targets Next-Flip/Momentum-Firmware targets/f7/ble_glue interfaces.
 * IMPORTANT: this source is not linked or exported until the firmware overlay
 * integration steps have been completed and a Momentum build verified.
 */
#include "ai_satellite_service.h"
#include "ai_satellite_queue.h"
#include "ai_satellite_session.h"
#include "ai_satellite_frame.h"
#include "ai_satellite_replay.h"
#include "app_common.h"
#include <ble/ble.h>
#include <furi_ble/event_dispatcher.h>
#include <furi_ble/gatt.h>
#include <furi.h>
#include <stdlib.h>

#define TAG "BtAiSatellite"
#define AI_SVC_MAX_FRAME 180u
/* STM32 BLE UUID arrays are byte-reversed relative to canonical string UUIDs.
 * SERVICE: c8c7ab10-6355-4ea8-a76a-0a4a64de0001
 * RX:      c8c7ab10-6355-4ea8-a76a-0a4a64de0002
 * TX:      c8c7ab10-6355-4ea8-a76a-0a4a64de0003
 */
#define AI_UUID_BASE(x) { x, 0x00, 0xde, 0x64, 0x4a, 0x0a, 0x6a, 0xa7, 0xa8, 0x4e, 0x55, 0x63, 0x10, 0xab, 0xc7, 0xc8 }
static const Service_UUID_t ai_service_uuid = {.Service_UUID_128 = AI_UUID_BASE(0x01)};
enum { AiCharRx = 0, AiCharTx, AiCharCount };
static const BleGattCharacteristicParams ai_characteristics[AiCharCount] = {
    [AiCharRx] = {
        .name = "AI request/response RX",
        .data_prop_type = FlipperGattCharacteristicDataFixed,
        .data.fixed.length = AI_SVC_MAX_FRAME,
        .uuid.Char_UUID_128 = AI_UUID_BASE(0x02),
        .uuid_type = UUID_TYPE_128,
        .char_properties = CHAR_PROP_WRITE,
        .security_permissions = ATTR_PERMISSION_AUTHEN_WRITE,
        .gatt_evt_mask = GATT_NOTIFY_ATTRIBUTE_WRITE,
        .is_variable = CHAR_VALUE_LEN_VARIABLE,
    },
    [AiCharTx] = {
        .name = "AI request/response TX",
        .data_prop_type = FlipperGattCharacteristicDataFixed,
        .data.fixed.length = AI_SVC_MAX_FRAME,
        .uuid.Char_UUID_128 = AI_UUID_BASE(0x03),
        .uuid_type = UUID_TYPE_128,
        .char_properties = CHAR_PROP_NOTIFY | CHAR_PROP_READ,
        .security_permissions = ATTR_PERMISSION_AUTHEN_READ,
        /* CCCD subscription writes must reach the session gate. */
        .gatt_evt_mask = GATT_NOTIFY_ATTRIBUTE_WRITE,
        .is_variable = CHAR_VALUE_LEN_VARIABLE,
    },
};
struct BleServiceAiSatellite {
    uint16_t service_handle;
    BleGattCharacteristicInstance chars[AiCharCount];
    GapSvcEventHandler* event_handler;
    FuriMutex* rx_lock;
    AiSatelliteQueue rx_queue;
    AiSatelliteSession session;
    AiSatelliteReplay replay;
};
static BleEventAckStatus ai_service_event(void* raw, void* context) {
    BleServiceAiSatellite* service = context;
    hci_event_pckt* event = (hci_event_pckt*)(((hci_uart_pckt*)raw)->data);
    if(event->evt != HCI_VENDOR_SPECIFIC_DEBUG_EVT_CODE) return BleEventNotAck;
    evt_blecore_aci* aci = (evt_blecore_aci*)event->data;
    if(aci->ecode != ACI_GATT_ATTRIBUTE_MODIFIED_VSEVT_CODE) return BleEventNotAck;
    aci_gatt_attribute_modified_event_rp0* changed =
        (aci_gatt_attribute_modified_event_rp0*)aci->data;
    /* GATT characteristic value = characteristic declaration handle + 1. */
    /* TX client characteristic configuration: notify bit (0x0001).
     * CCCD event alone is never sufficient to authorize transmission. */
    if(changed->Attr_Handle == service->chars[AiCharTx].handle + 2) {
        if(changed->Attr_Data_Length == 2 &&
           furi_mutex_acquire(service->rx_lock, 0) == FuriStatusOk) {
            bool enabled = (changed->Attr_Data[0] & 1u) != 0 &&
                           changed->Attr_Data[1] == 0;
            ai_sat_session_cccd(&service->session, enabled);
            furi_mutex_release(service->rx_lock);
        }
        return BleEventAckFlowEnable;
    }
    if(changed->Attr_Handle != service->chars[AiCharRx].handle + 1)
        return BleEventNotAck;
    if(changed->Attr_Data_Length > 0 && changed->Attr_Data_Length <= AI_SVC_MAX_FRAME &&
       furi_mutex_acquire(service->rx_lock, 0) == FuriStatusOk) {
        uint32_t accepted_id=0;
        if(ai_sat_session_can_receive(&service->session) &&
           ai_sat_replay_peek(&service->replay, changed->Attr_Data,
                              changed->Attr_Data_Length, &accepted_id)) {
            /* Commit ID only after the frame fits the bounded RX queue.
             * The BLE packet buffer is transient; copy on dispatch thread.
             */
            if(ai_sat_queue_push(&service->rx_queue, changed->Attr_Data,
                                 changed->Attr_Data_Length))
                ai_sat_replay_commit(&service->replay, accepted_id);
        }
        furi_mutex_release(service->rx_lock);
    }
    return BleEventAckFlowEnable;
}
BleServiceAiSatellite* ble_svc_ai_satellite_start(void) {
    BleServiceAiSatellite* s = calloc(1, sizeof(*s));
    if(!s) return NULL;
    s->rx_lock = furi_mutex_alloc(FuriMutexTypeNormal);
    if(!s->rx_lock) {free(s); return NULL;}
    ai_sat_queue_reset(&s->rx_queue);
    ai_sat_session_reset(&s->session);
    ai_sat_replay_reset(&s->replay);
    if(!ble_gatt_service_add(
           UUID_TYPE_128, &ai_service_uuid, PRIMARY_SERVICE, 8, &s->service_handle)) {
        furi_mutex_free(s->rx_lock);
        free(s);
        return NULL;
    }
    for(size_t i = 0; i < AiCharCount; ++i) {
        ble_gatt_characteristic_init(s->service_handle, &ai_characteristics[i], &s->chars[i]);
    }
    s->event_handler = ble_event_dispatcher_register_svc_handler(ai_service_event, s);
    if(!s->event_handler) {
        for(size_t i = 0; i < AiCharCount; ++i)
            ble_gatt_characteristic_delete(s->service_handle, &s->chars[i]);
        ble_gatt_service_delete(s->service_handle);
        furi_mutex_free(s->rx_lock);
        free(s);
        return NULL;
    }
    return s;
}
void ble_svc_ai_satellite_stop(BleServiceAiSatellite* s) {
    if(!s) return;
    if(s->event_handler) ble_event_dispatcher_unregister_svc_handler(s->event_handler);
    for(size_t i = 0; i < AiCharCount; ++i)
        ble_gatt_characteristic_delete(s->service_handle, &s->chars[i]);
    ble_gatt_service_delete(s->service_handle);
    furi_mutex_free(s->rx_lock);
    free(s);
}
void ble_svc_ai_satellite_link_state(
    BleServiceAiSatellite* s, bool connected, bool authenticated, size_t att_payload) {
    if(!s) return;
    if(furi_mutex_acquire(s->rx_lock, FuriWaitForever) != FuriStatusOk) return;
    if(!connected) {
        ai_sat_session_reset(&s->session);
        ai_sat_queue_reset(&s->rx_queue);
        ai_sat_replay_reset(&s->replay);
    } else if(!s->session.connected) {
        ai_sat_session_connected(&s->session, att_payload);
        ai_sat_queue_reset(&s->rx_queue);
        ai_sat_replay_reset(&s->replay);
        ai_sat_session_authenticated(&s->session, authenticated);
    } else {
        ai_sat_session_mtu(&s->session, att_payload);
        ai_sat_session_authenticated(&s->session, authenticated);
        if(!authenticated) {
            ai_sat_queue_reset(&s->rx_queue);
            ai_sat_replay_reset(&s->replay);
        }
    }
    furi_mutex_release(s->rx_lock);
}
bool ble_svc_ai_satellite_ready(
    BleServiceAiSatellite* s, size_t* att_payload, uint32_t* generation) {
    if(!s || !att_payload || !generation)return false;
    if(furi_mutex_acquire(s->rx_lock, 0)!=FuriStatusOk)return false;
    bool ready=ai_sat_session_can_send(&s->session, 1);
    if(ready){
        *att_payload=s->session.att_payload;
        *generation=s->session.generation;
    }
    furi_mutex_release(s->rx_lock);
    return ready;
}
bool ble_svc_ai_satellite_pop_rx(
    BleServiceAiSatellite* s, uint8_t* output, size_t capacity, size_t* received) {
    if(!s || !output || !received) return false;
    if(furi_mutex_acquire(s->rx_lock, 0) != FuriStatusOk) return false;
    bool permitted = ai_sat_session_can_receive(&s->session);
    bool result = permitted && ai_sat_queue_pop(&s->rx_queue, output, capacity, received);
    furi_mutex_release(s->rx_lock);
    return result;
}
bool ble_svc_ai_satellite_notify(
    BleServiceAiSatellite* s, const uint8_t* frame, size_t length) {
    if(!s || !frame || !length || length > AI_SVC_MAX_FRAME ||
       !ai_sat_valid_frame(frame, length)) return false;
    /* Never transmit on a link that has not been verified and subscribed.
     * Session updates must come only from the trusted firmware GAP layer.
     * The lock ensures that disconnect cannot invalidate authorization
     * during this local GATT update call. */
    if(furi_mutex_acquire(s->rx_lock, 0) != FuriStatusOk) return false;
    if(!ai_sat_session_can_send(&s->session, length)) {
        furi_mutex_release(s->rx_lock);
        return false;
    }
    /* Caller must verify connected+authenticated and CCCD subscription.
     * ATT notifications are not application-level write confirmations. */
    /* Momentum's ble_gatt_characteristic_update() uses fixed.length (180)
     * even for short frames. Send exactly length bytes to avoid overread
     * and transmitting bytes beyond the AI frame boundary. */
    const tBleStatus result = aci_gatt_update_char_value(
        s->service_handle, s->chars[AiCharTx].handle, 0, (uint8_t)length, frame);
    furi_mutex_release(s->rx_lock);
    return result == BLE_STATUS_SUCCESS;
}
