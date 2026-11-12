#!/usr/bin/env python3
"""Host-only source patch regression; doesn't attest to real Bluetooth security."""
import importlib.util
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location("gap_installer",ROOT/"install_trusted_gap_hook.py")
patch=importlib.util.module_from_spec(spec)
spec.loader.exec_module(patch)

class TrustedGapPatchTests(unittest.TestCase):
    def test_gap_event_definitions_are_additive(self):
        baseline="GapEventTypeUpdateMTU,\n    GapEventTypeBeaconStart,\n"
        modified=patch.patch_gap_h(baseline)
        self.assertIn("GapEventTypeAiVerified", modified)
        self.assertIn("GapEventTypeAiRevoked", modified)
        self.assertIn("GapEventTypeUpdateMTU", modified)
        with self.assertRaises(ValueError):patch.patch_gap_h(modified)

    def test_bt_events_do_not_replace_standard_rpc(self):
        baseline="""    bt->max_packet_size = BLE_PROFILE_SERIAL_PACKET_SIZE_MAX;
    } else if(event.type == GapEventTypeDisconnected) {
    } else if(event.type == GapEventTypeUpdateMTU) {
        bt->max_packet_size = event.data.max_packet_size;
    }
"""
        modified=patch.patch_bt_c(baseline)
        self.assertIn("GapEventTypeAiVerified",modified)
        self.assertIn("GapEventTypeAiRevoked",modified)
        self.assertIn("GapEventTypeDisconnected",modified)
        self.assertIn("bt->ai_mtu_known = false;",modified)
        self.assertIn("bt->ai_mtu_known = true;",modified)
        self.assertIn("20u",modified)
        self.assertNotIn("current_profile_is_serial",modified)
        self.assertEqual(modified.count(
            "bt->current_profile && bt->current_profile->config == ble_profile_serial"),4)
        self.assertEqual(modified.count("ble_profile_serial_ai_link_state("),4)
        self.assertIn("GapEventTypeAiRevoked",modified)
        self.assertIn("bt->ai_verified = false;",modified)
        with self.assertRaises(ValueError):patch.patch_bt_c(modified)

    def test_gap_requires_pairing_and_live_hci_encryption(self):
        baseline="""#include "gap.h"
typedef struct {
    bool is_secure;
} Gap;
    case HCI_LE_META_EVT_CODE:
    case HCI_DISCONNECTION_COMPLETE_EVT_CODE: {
        gap->is_secure = false;
        gap->negotiation_round = 0;
    }
            gap->service.connection_handle = event->Connection_Handle;
            FURI_LOG_D(TAG, "Slave security initiated");
            if(pairing_complete->Status) {\n                aci_gap_terminate(gap->service.connection_handle, 5);\n            }\n                FURI_LOG_I(TAG, "Pairing complete");
"""
        modified=patch.patch_gap_c(baseline)
        self.assertIn("HCI_ENCRYPTION_CHANGE_EVT_CODE",modified)
        self.assertIn("enc->Connection_Handle",modified)
        self.assertIn("ai_sat_trust_pairing(&gap->ai_trust, true)",modified)
        self.assertIn("ai_sat_trust_security_started(&gap->ai_trust)",modified)
        self.assertIn("ai_sat_trust_reset(&gap->ai_trust)",modified)
        self.assertIn("ai_sat_trust_pairing(&gap->ai_trust, false)",modified)
        self.assertIn("GapEvent ai_revoked = {.type = GapEventTypeAiRevoked}",modified)
        with self.assertRaises(ValueError):patch.patch_gap_c(modified)

if __name__=="__main__":unittest.main()
