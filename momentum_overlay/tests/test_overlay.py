#!/usr/bin/env python3
"""Source consistency tests, NOT a Momentum firmware compile."""
from pathlib import Path
import importlib.util
import re
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]
OVERLAY=ROOT/"momentum_overlay"
SCRIPT=OVERLAY/"install_source_overlay.py"
spec=importlib.util.spec_from_file_location("installer", SCRIPT)
installer=importlib.util.module_from_spec(spec)
spec.loader.exec_module(installer)

class MomentumOverlayTest(unittest.TestCase):
    def test_profile_patch_and_idempotence_guard(self):
        original = """#include <services/serial_service.h>
typedef struct {
    BleServiceSerial* serial_svc;
} BleProfileSerial;
static void start() {
    profile->serial_svc = ble_svc_serial_start();
}
static void stop() {
    ble_svc_serial_stop(serial_profile->serial_svc);
}
"""
        changed=installer.transform_serial(original)
        self.assertIn("ble_svc_ai_satellite_start()",changed)
        self.assertIn("ble_svc_serial_start()",changed)
        self.assertIn("ble_svc_serial_stop(serial_profile->serial_svc)",changed)
        self.assertLess(changed.index("ble_svc_ai_satellite_stop"),changed.index("ble_svc_serial_stop"))
        with self.assertRaises(ValueError):
            installer.transform_serial(changed)
        with self.assertRaises(ValueError):
            installer.transform_serial(original.replace("    BleServiceSerial* serial_svc;",""))

    def test_serial_profile_bridge_header_patch(self):
        header = """#pragma once
#include <stdbool.h>
#ifdef __cplusplus
}
#endif
"""
        updated = installer.transform_serial_header(header)
        self.assertIn("ble_profile_serial_ai_pop_rx(", updated)
        self.assertIn("ble_profile_serial_ai_notify(", updated)
        with self.assertRaises(ValueError):
            installer.transform_serial_header(updated)

    def test_uuid_matches_android_canonical_values(self):
        text=(OVERLAY/"targets/f7/ble_glue/services/ai_satellite_service.c").read_text()
        android=(ROOT/"android_satellite/app/src/main/java/com/flipperai/satellite/BleSatellite.java").read_text()
        groups=dict(re.findall(r'public static final UUID (SERVICE|RX|TX)=UUID.fromString\("([^"]+)"\)',android))
        self.assertEqual(len(groups),3)
        match=re.search(r"#define AI_UUID_BASE\(x\) \{ ([^}]+) \}",text)
        self.assertIsNotNone(match)
        template=[s.strip() for s in match.group(1).split(",")]
        for name,offset in (("SERVICE",1),("RX",2),("TX",3)):
            uuid=re.sub(r"[^0-9a-f]","",groups[name].lower())
            raw=bytes.fromhex(uuid)[::-1]
            actual=bytes((offset if part=="x" else int(part,16)) for part in template)
            self.assertEqual(raw,actual,name)

    def test_fail_closed_session_gate_is_used_by_service(self):
        source=(OVERLAY/"targets/f7/ble_glue/services/ai_satellite_service.c").read_text()
        installer=(OVERLAY/"install_source_overlay.py").read_text()
        self.assertIn("ai_sat_session_can_send(&s->session, length)", source)
        self.assertIn("ai_sat_session_can_receive(&service->session)", source)
        self.assertIn("ai_sat_session_reset(&s->session)", source)
        self.assertIn("ai_sat_session_cccd(&service->session, enabled)", source)
        self.assertIn("ai_satellite_session.h", installer)
        self.assertIn("ai_satellite_frame.h", installer)
        self.assertIn("ai_satellite_replay.h", installer)
        self.assertIn("ai_sat_replay_peek(&service->replay", source)
        self.assertIn("ai_sat_replay_commit(&service->replay", source)
        self.assertIn("ai_sat_replay_reset(&s->replay)", source)
        self.assertIn("!ai_sat_valid_frame(frame, length)", source)
        self.assertIn("ai_sat_valid_frame(changed->Attr_Data, changed->Attr_Data_Length)",source)
        self.assertIn("ble_profile_serial_ai_link_state(", installer)

    def test_ai_tx_cccd_subscription_events_are_observable(self):
        source=(OVERLAY/"targets/f7/ble_glue/services/ai_satellite_service.c").read_text()
        tx=source.split("[AiCharTx] = {",1)[1].split("\n    },",1)[0]
        self.assertIn("CHAR_PROP_NOTIFY",tx)
        self.assertIn("ATTR_PERMISSION_AUTHEN_READ",tx)
        self.assertIn("GATT_NOTIFY_ATTRIBUTE_WRITE",tx)
        self.assertIn("ai_sat_session_cccd(&service->session, enabled)",source)

    def test_authenticated_gatt_permissions_and_frame_length(self):
        src=(OVERLAY/"targets/f7/ble_glue/services/ai_satellite_service.c").read_text()
        self.assertIn("ATTR_PERMISSION_AUTHEN_WRITE",src)
        self.assertIn("ATTR_PERMISSION_AUTHEN_READ",src)
        self.assertIn("CHAR_PROP_NOTIFY",src)
        self.assertIn("length > AI_SVC_MAX_FRAME",src)
        self.assertIn("aci_gatt_update_char_value(",src)
        self.assertNotIn("ble_gatt_characteristic_update(\n        s->service_handle",src)

if __name__=="__main__":
    unittest.main()
