#!/usr/bin/env python3
"""Add conservative GAP->BT AI session events to an *already overlaid* Momentum tree.
Build-only experiment. Encrypted+fresh-pairing event required; bonded reconnects
without new pairing remain disabled. No changes to upstream RPC traffic.
"""
from pathlib import Path
import sys

def one(s, before, after):
    if s.count(before) != 1:
        raise ValueError(f"Unexpected pinned Momentum anchor ({s.count(before)}): {before[:80]!r}")
    return s.replace(before,after,1)

def patch_gap_h(s):
    if "GapEventTypeAiVerified" in s:
        raise ValueError("AI GAP events already patched")
    return one(s, "    GapEventTypeBeaconStart,",
        "    GapEventTypeAiVerified,\n    GapEventTypeAiRevoked,\n    GapEventTypeBeaconStart,")

def patch_gap_c(s):
    s=one(s,'#include "gap.h"','#include "gap.h"\n#include <services/ai_satellite_trust.h>')
    s=one(s,"    bool is_secure;","    bool is_secure;\n    AiSatelliteTrust ai_trust;")
    s=one(s,"        gap->is_secure = false;\n        gap->negotiation_round = 0;",
        "        gap->is_secure = false;\n        ai_sat_trust_reset(&gap->ai_trust);\n        gap->negotiation_round = 0;")
    s=one(s,"            gap->service.connection_handle = event->Connection_Handle;",
        "            gap->service.connection_handle = event->Connection_Handle;\n"
        "            ai_sat_trust_connected(&gap->ai_trust, event->Connection_Handle);")
    # Require HCI verified encryption for the same live ACL connection handle.
    s=one(s,"    case HCI_LE_META_EVT_CODE:",
        """    case HCI_ENCRYPTION_CHANGE_EVT_CODE: {
        hci_encryption_change_event_rp0* enc =
            (hci_encryption_change_event_rp0*)event_pckt->data;
        bool valid = ai_sat_trust_encryption(
            &gap->ai_trust, enc->Connection_Handle, enc->Status == 0,
            enc->Encryption_Enabled == 1);
        if(valid && ai_sat_trust_take_ready(&gap->ai_trust)) {
            GapEvent ai_event = {.type = GapEventTypeAiVerified};
            gap->on_event_cb(ai_event, gap->context);
        } else if(enc->Connection_Handle == gap->ai_trust.handle &&
                  (enc->Status != 0 || enc->Encryption_Enabled != 1)) {
            GapEvent ai_event = {.type = GapEventTypeAiRevoked};
            gap->on_event_cb(ai_event, gap->context);
        }
    } break;
    case HCI_LE_META_EVT_CODE:""")
    s=one(s,'            FURI_LOG_D(TAG, "Slave security initiated");',
        """            FURI_LOG_D(TAG, "Slave security initiated");
            if(ai_sat_trust_security_started(&gap->ai_trust) &&
               ai_sat_trust_take_ready(&gap->ai_trust)) {
                GapEvent ai_event = {.type = GapEventTypeAiVerified};
                gap->on_event_cb(ai_event, gap->context);
            }""")
    s=one(s,"            if(pairing_complete->Status) {",
        """            if(pairing_complete->Status) {
                /* Never keep an experimental AI session authorized after a
                 * failed security procedure, even before ACL termination. */
                ai_sat_trust_pairing(&gap->ai_trust, false);
                GapEvent ai_revoked = {.type = GapEventTypeAiRevoked};
                gap->on_event_cb(ai_revoked, gap->context);""")
    s=one(s,"                FURI_LOG_I(TAG, \"Pairing complete\");",
        """                FURI_LOG_I(TAG, "Pairing complete");
                if(ai_sat_trust_pairing(&gap->ai_trust, true) &&
                   ai_sat_trust_take_ready(&gap->ai_trust)) {
                    GapEvent ai_event = {.type = GapEventTypeAiVerified};
                    gap->on_event_cb(ai_event, gap->context);
                }""")
    return s

def patch_bt_i(s):
    return one(s,"    bool beacon_active;","    bool beacon_active;\n    bool ai_verified;\n    bool ai_mtu_known;")

def patch_bt_c(s):
    if "GapEventTypeAiVerified" in s:
        raise ValueError("AI GAP->BT event handler already patched")
    s=one(s,"    bt->max_packet_size = BLE_PROFILE_SERIAL_PACKET_SIZE_MAX;",
        "    bt->max_packet_size = BLE_PROFILE_SERIAL_PACKET_SIZE_MAX;\n    bt->ai_verified = false;\n    bt->ai_mtu_known = false;")
    s=one(s,"    } else if(event.type == GapEventTypeDisconnected) {",
        """    } else if(event.type == GapEventTypeAiVerified) {
        bt->ai_verified = true;
        if(bt->current_profile && bt->current_profile->config == ble_profile_serial)
            ble_profile_serial_ai_link_state(bt->current_profile, true, true,
                             bt->ai_mtu_known ? bt->max_packet_size : 20u);
        ret = true;
    } else if(event.type == GapEventTypeAiRevoked) {
        bt->ai_verified = false;
        if(bt->current_profile && bt->current_profile->config == ble_profile_serial)
            ble_profile_serial_ai_link_state(bt->current_profile, false, false, 0);
        ret = true;
    } else if(event.type == GapEventTypeDisconnected) {
        bt->ai_verified = false;
        bt->ai_mtu_known = false;
        if(bt->current_profile && bt->current_profile->config == ble_profile_serial)
            ble_profile_serial_ai_link_state(bt->current_profile, false, false, 0);""")
    s=one(s,"        bt->max_packet_size = event.data.max_packet_size;",
        """        bt->max_packet_size = event.data.max_packet_size;
        bt->ai_mtu_known = true;
        if(bt->current_profile && bt->current_profile->config == ble_profile_serial && bt->ai_verified)
            ble_profile_serial_ai_link_state(bt->current_profile, true, true,
                                             bt->max_packet_size);""")
    return s

PATCHES={
 "targets/f7/ble_glue/gap.h":patch_gap_h,
 "targets/f7/ble_glue/gap.c":patch_gap_c,
 "applications/services/bt/bt_service/bt_i.h":patch_bt_i,
 "applications/services/bt/bt_service/bt.c":patch_bt_c,
}
if __name__=="__main__":
    if len(sys.argv)!=2:raise SystemExit("Usage: install_trusted_gap_hook.py /path/to/Momentum-Firmware")
    root=Path(sys.argv[1]).resolve()
    changed={}
    for relative,fn in PATCHES.items():
        file=root/relative
        changed[file]=fn(file.read_text())
    source=Path(__file__).resolve().parent/"targets/f7/ble_glue/services/ai_satellite_trust.h"
    dest=root/"targets/f7/ble_glue/services/ai_satellite_trust.h"
    if dest.exists():raise FileExistsError(dest)
    dest.write_bytes(source.read_bytes())
    for file,text in changed.items():file.write_text(text)
    print("Installed strictly gated GAP encryption+pairing event bridge; no firmware build claimed.")
