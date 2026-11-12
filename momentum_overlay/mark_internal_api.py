#!/usr/bin/env python3
"""Mark ONLY known experimental AI GATT symbols disabled in a temporary Momentum SDK CSV.

Do not expose unstable firmware-internal BLE service functions to Flipper FAPs.
Never apply to the user's original Momentum firmware checkout.
"""
import csv
import sys
from pathlib import Path

EXPECTED = {
    ("Header", "targets/f7/ble_glue/services/ai_satellite_queue.h", "", ""),
    ("Header", "targets/f7/ble_glue/services/ai_satellite_frame.h", "", ""),
    ("Header", "targets/f7/ble_glue/services/ai_satellite_replay.h", "", ""),
    ("Header", "targets/f7/ble_glue/services/ai_satellite_session.h", "", ""),
    ("Header", "targets/f7/ble_glue/services/ai_satellite_trust.h", "", ""),
    ("Header", "targets/f7/ble_glue/services/ai_satellite_service.h", "", ""),
    ("Function", "ble_svc_ai_satellite_start", "BleServiceAiSatellite*", ""),
    ("Function", "ble_svc_ai_satellite_link_state", "void",
     "BleServiceAiSatellite*, _Bool, _Bool, size_t"),
    ("Function", "ble_profile_serial_ai_link_state", "void",
     "FuriHalBleProfileBase*, _Bool, _Bool, size_t"),
    ("Function", "ble_svc_ai_satellite_stop", "void", "BleServiceAiSatellite*"),
    ("Function", "ble_svc_ai_satellite_pop_rx", "_Bool",
     "BleServiceAiSatellite*, uint8_t*, size_t, size_t*"),
    ("Function", "ble_svc_ai_satellite_notify", "_Bool",
     "BleServiceAiSatellite*, const uint8_t*, size_t"),
    ("Function", "ble_svc_ai_satellite_ready", "_Bool",
     "BleServiceAiSatellite*, size_t*, uint32_t*"),
    ("Function", "ble_profile_serial_ai_ready", "_Bool",
     "FuriHalBleProfileBase*, size_t*, uint32_t*"),
    # BT-to-app API is provisional; do not export to FAP until reviewed.
    ("Function", "bt_ai_satellite_status", "_Bool",
     "Bt*, size_t*, uint32_t*"),
    ("Function", "bt_ai_satellite_receive", "_Bool",
     "Bt*, uint8_t*, size_t, size_t*"),
    ("Function", "bt_ai_satellite_send", "_Bool",
     "Bt*, const uint8_t*, size_t"),
    ("Function", "ble_profile_serial_ai_pop_rx", "_Bool",
     "FuriHalBleProfileBase*, uint8_t*, size_t, size_t*"),
    ("Function", "ble_profile_serial_ai_notify", "_Bool",
     "FuriHalBleProfileBase*, const uint8_t*, size_t"),
}
FIELDS = ["entry", "status", "name", "type", "params"]

def private_entries(path: Path):
    with path.open(newline="") as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames != FIELDS:
            raise ValueError("Unknown SDK symbol CSV schema")
        rows = list(reader)
    existing = {(r["entry"],r["name"],r["type"],r["params"]):r for r in rows}
    for kind,name,type_,params in sorted(EXPECTED):
        key=(kind,name,type_,params)
        if key in existing:
            if existing[key]["status"] == "+":
                raise ValueError("Refusing to disable an approved SDK symbol: "+name)
            existing[key]["status"] = "-"
        else:
            rows.append(dict(zip(FIELDS,(kind,"-",name,type_,params))))
    if rows[0]["entry"]!="Version":
        raise ValueError("SDK version header is missing")
    with path.open("w",newline="") as handle:
        writer=csv.DictWriter(handle,fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(rows)
    print("Recorded experimental AI GATT service entries as private SDK symbols")
    print("This is only for temporary pinned-source firmware compilation")

if __name__=="__main__":
    if len(sys.argv)!=2:
        raise SystemExit("Usage: mark_internal_api.py /path/to/temporary/api_symbols.csv")
    private_entries(Path(sys.argv[1]))
