#!/usr/bin/env python3
"""Apply additive AI GATT service overlay to an explicit local Momentum source tree.

Usage: python3 momentum_overlay/install_source_overlay.py /path/to/Momentum-Firmware
Does not build, flash, or alter this project's tracked Momentum baseline.
Fails closed when source API anchors do not match. Make a branch/backup first.
"""
from pathlib import Path
import shutil
import sys

SERIAL_REL = Path("targets/f7/ble_glue/profiles/serial_profile.c")
SERIAL_H_REL = Path("targets/f7/ble_glue/profiles/serial_profile.h")
SERVICE_REL = Path("targets/f7/ble_glue/services")

def replace_once(text, existing, replacement):
    count = text.count(existing)
    if count != 1:
        raise ValueError(f"Momentum API mismatch: expected anchor exactly once, got {count}: {existing!r}")
    return text.replace(existing, replacement, 1)

def transform_serial(text):
    if "ble_svc_ai_satellite_start()" in text:
        raise ValueError("AI overlay already applied; refusing duplicate service registration")
    text = replace_once(
        text, "#include <services/serial_service.h>",
        "#include <services/serial_service.h>\n#include <services/ai_satellite_service.h>")
    text = replace_once(
        text, "    BleServiceSerial* serial_svc;",
        "    BleServiceSerial* serial_svc;\n    BleServiceAiSatellite* ai_satellite_svc;")
    text = replace_once(
        text, "    profile->serial_svc = ble_svc_serial_start();",
        "    profile->serial_svc = ble_svc_serial_start();\n"
        "    /* Add the AI GATT service without replacing serial RPC. */\n"
        "    profile->ai_satellite_svc = ble_svc_ai_satellite_start();")
    text = replace_once(
        text, "    ble_svc_serial_stop(serial_profile->serial_svc);",
        "    if(serial_profile->ai_satellite_svc)\n"
        "        ble_svc_ai_satellite_stop(serial_profile->ai_satellite_svc);\n"
        "    ble_svc_serial_stop(serial_profile->serial_svc);")
    text += """
/* Experimental AI bridge; existing RPC serial functions are unchanged. */
bool ble_profile_serial_ai_pop_rx(
    FuriHalBleProfileBase* base, uint8_t* out, size_t capacity, size_t* length) {
    if(!base || base->config != ble_profile_serial || !out || !length) return false;
    BleProfileSerial* serial = (BleProfileSerial*)base;
    if(!serial->ai_satellite_svc) return false;
    return ble_svc_ai_satellite_pop_rx(serial->ai_satellite_svc, out, capacity, length);
}
bool ble_profile_serial_ai_ready(
    FuriHalBleProfileBase* base, size_t* payload, uint32_t* generation) {
    if(!base || base->config != ble_profile_serial || !payload || !generation)return false;
    BleProfileSerial* serial=(BleProfileSerial*)base;
    return serial->ai_satellite_svc &&
        ble_svc_ai_satellite_ready(serial->ai_satellite_svc, payload, generation);
}
bool ble_profile_serial_ai_notify(
    FuriHalBleProfileBase* base, const uint8_t* data, size_t length) {
    if(!base || base->config != ble_profile_serial || !data || !length) return false;
    BleProfileSerial* serial = (BleProfileSerial*)base;
    if(!serial->ai_satellite_svc) return false;
    return ble_svc_ai_satellite_notify(serial->ai_satellite_svc, data, length);
}
/* Internal bridge. Only the trusted BT/GAP service may supply authenticated=true. */
void ble_profile_serial_ai_link_state(
    FuriHalBleProfileBase* base, bool connected, bool authenticated, size_t payload) {
    if(!base || base->config != ble_profile_serial) return;
    BleProfileSerial* serial = (BleProfileSerial*)base;
    if(serial->ai_satellite_svc)
        ble_svc_ai_satellite_link_state(serial->ai_satellite_svc, connected, authenticated, payload);
}
"""
    return text

def transform_serial_header(text):
    if "ble_profile_serial_ai_pop_rx(" in text:
        raise ValueError("Experimental AI profile API already applied")
    anchor = "#ifdef __cplusplus\n}\n#endif"
    if text.count(anchor)!=1:
        raise ValueError("Pinned Momentum serial profile header format changed")
    api = """
/* Experimental source-only API: not approved for normal external FAP access. */
bool ble_profile_serial_ai_pop_rx(
    FuriHalBleProfileBase* profile, uint8_t* output, size_t capacity, size_t* length);
bool ble_profile_serial_ai_ready(
    FuriHalBleProfileBase* profile, size_t* att_payload, uint32_t* generation);
bool ble_profile_serial_ai_notify(
    FuriHalBleProfileBase* profile, const uint8_t* data, size_t length);
/* Firmware-internal. Must be called only by trusted BT service. */
void ble_profile_serial_ai_link_state(
    FuriHalBleProfileBase* profile, bool connected, bool authenticated, size_t att_payload);

"""
    return text.replace(anchor, api + anchor, 1)

def main(root):
    profile = root / SERIAL_REL
    if not profile.is_file():
        raise FileNotFoundError(f"Expected Momentum source file missing: {profile}")
    before = profile.read_text()
    after = transform_serial(before)  # verify ALL anchors before changing files
    profile_header = root / SERIAL_H_REL
    if not profile_header.is_file():
        raise FileNotFoundError(f"Expected Momentum profile header missing: {profile_header}")
    before_header = profile_header.read_text()
    after_header = transform_serial_header(before_header)
    src_dir = Path(__file__).resolve().parent / SERVICE_REL
    service_dir = root / SERVICE_REL
    if not service_dir.is_dir():
        raise FileNotFoundError(f"Missing service directory: {service_dir}")
    filenames = ["ai_satellite_service.c", "ai_satellite_service.h", "ai_satellite_queue.h", "ai_satellite_session.h", "ai_satellite_frame.h", "ai_satellite_replay.h"]
    if any((service_dir / name).exists() for name in filenames):
        raise FileExistsError("AI GATT source already present; avoid overwriting user changes")
    # Source has been checked; now create overlay files.
    for name in filenames:
        shutil.copyfile(src_dir / name, service_dir / name)
    profile.write_text(after)
    profile_header.write_text(after_header)
    print("Added AI GATT service to Momentum serial profile sources.")
    print("NOT BUILD-VERIFIED: confirm service source discovery, compile Momentum,")
    print("and wire event callbacks/authentication/CCCD before claiming BLE support.")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: install_source_overlay.py /path/to/Momentum-Firmware")
    main(Path(sys.argv[1]).resolve())
