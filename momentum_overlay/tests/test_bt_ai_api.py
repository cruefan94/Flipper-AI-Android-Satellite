#!/usr/bin/env python3
"""Fail-closed source-patcher checks, not a firmware or device test."""
import importlib.util
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location("bt_api_patch",ROOT/"install_bt_ai_api.py")
patch=importlib.util.module_from_spec(spec)
spec.loader.exec_module(patch)

class BtAppInterfaceTests(unittest.TestCase):
    def test_public_api_keeps_bluetooth_rpc(self):
        original="#pragma once\n#include <stdint.h>\n/** Change BLE Profile\n */"
        updated=patch.patch_public(original)
        self.assertIn("bt_ai_satellite_status(",updated)
        self.assertIn("bt_ai_satellite_receive(",updated)
        self.assertIn("bt_ai_satellite_send(",updated)
        self.assertIn("Change BLE Profile",updated)
        with self.assertRaises(ValueError):patch.patch_public(updated)
    def test_message_union_and_type_are_distinct(self):
        original="""typedef enum {
    BtMessageTypeUpdateStatus,
} BtMessageType;
typedef union {
    uint32_t pin_code;
} BtMessageData;
typedef struct {
    uint32_t pin_code;
} Bt;
"""
        updated=patch.patch_private(original)
        self.assertIn("BtMessageTypeAiStatus",updated)
        self.assertIn("BtMessageTypeAiReceive",updated)
        self.assertIn("BtMessageTypeAiSend",updated)
        self.assertEqual(updated.count("BtAiSatelliteRequest* ai_request;"),1)
        self.assertEqual(updated.count("    uint32_t pin_code;"),2)
        with self.assertRaises(ValueError):patch.patch_private(updated)
    def test_bt_service_fails_closed_without_verified_session(self):
        src="""    while(1) {
        if(message.type == BtMessageTypeUpdateStatus) {
            bt_statusbar_update(bt);
        }
    }
"""
        changed=patch.patch_bt(src)
        self.assertIn("bt->ai_verified",changed)
        self.assertIn("ble_profile_serial_ai_ready(",changed)
        self.assertIn("bt->current_profile",changed)
        self.assertIn("length<=180u",changed)
        self.assertIn("BtMessageTypeUpdateStatus",changed)
    def test_api_requests_are_serialized_by_existing_lock(self):
        src="#include \"bt_i.h\"\n"
        changed=patch.patch_api(src)
        self.assertIn("api_lock_alloc_locked()",changed)
        self.assertIn("api_lock_wait_unlock_and_free(msg.lock)",changed)
        self.assertIn("bt_ai_satellite_send(",changed)
        self.assertIn("BtMessageTypeAiReceive",changed)

if __name__=="__main__":unittest.main()
