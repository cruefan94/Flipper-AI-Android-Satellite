#pragma once
/* Experimental Momentum BLE service. Firmware source overlay, not an external FAP.
 * Register alongside the existing serial RPC service; do not replace its profile.
 */
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
typedef struct BleServiceAiSatellite BleServiceAiSatellite;
/* Nonblocking event-to-application receive queue. Whole frames only.
 * Poll from an application thread; never run application logic in BLE callbacks.
 * A failed poll leaves the queue unchanged (including insufficient capacity).
 */
BleServiceAiSatellite* ble_svc_ai_satellite_start(void);
void ble_svc_ai_satellite_stop(BleServiceAiSatellite* service);
/* Read-only snapshot for trusted BT message queue; returns false unless live
 * authenticated link and CCCD notify are confirmed. */
bool ble_svc_ai_satellite_ready(
    BleServiceAiSatellite* service, size_t* att_payload, uint32_t* generation);
bool ble_svc_ai_satellite_pop_rx(
    BleServiceAiSatellite* service, uint8_t* output, size_t capacity, size_t* received);
/* Trusted firmware/GAP layer ONLY: authenticated must be verified for the live
 * encrypted connection; bonding alone does not qualify. Clears RX on disconnect.
 * No application/FAP is allowed to set this flag directly.
 */
void ble_svc_ai_satellite_link_state(
    BleServiceAiSatellite* service, bool connected, bool authenticated, size_t att_payload);
/* Notify only when verified authenticated+subscribed+within negotiated MTU. */
bool ble_svc_ai_satellite_notify(
    BleServiceAiSatellite* service, const uint8_t* frame, size_t length);
