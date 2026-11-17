#!/usr/bin/env python3
"""BLE connection owner must survive in-app screen navigation."""
import unittest
from pathlib import Path

SRC=Path(__file__).resolve().parents[1]/"app/src/main/java/com/flipperai/satellite"
UI=(SRC/"SatelliteUi.java").read_text(encoding="utf-8")
BLE=(SRC/"MainActivity.java").read_text(encoding="utf-8")
DASH=(SRC/"DashboardActivity.java").read_text(encoding="utf-8")

class BleNavigationSessionRetentionTests(unittest.TestCase):
    def test_dashboard_round_trip_reuses_ble_owner(self):
        self.assertIn("if(destination==MainActivity.class)",UI)
        self.assertIn("Intent.FLAG_ACTIVITY_REORDER_TO_FRONT",UI)
        self.assertIn("Intent.FLAG_ACTIVITY_SINGLE_TOP",UI)
        self.assertIn("if(!(from instanceof MainActivity))from.finish();",UI)
        self.assertNotIn("Intent.FLAG_ACTIVITY_CLEAR_TOP",UI.replace("// CLEAR_TOP destroys the BLE Activity on its way to the dashboard.",""))

    def test_back_button_and_system_back_take_same_route(self):
        self.assertIn('action(from,page,"‹  DASHBOARD",()->goHome(from));',UI)
        self.assertIn("public void onBackPressed()",BLE)
        self.assertIn("SatelliteUi.goHome(this);",BLE)

    def test_explicit_disconnect_and_task_exit_still_release_ble(self):
        self.assertIn('if(bleService!=null)bleService.stopFromUser();',BLE)
        self.assertIn("bleService.leaveAiSettingsScreen();",BLE)
        self.assertNotIn("bleSessionJobs",BLE)
        self.assertIn("bleService.detach(this);",BLE)
        self.assertIn("unbindService(bleBinding);",BLE)
        self.assertNotIn("ble.stopScan();transports.close();",BLE)
        self.assertIn("finishAffinity();",DASH)
        self.assertIn("public void onBackPressed()",DASH)

if __name__=="__main__":
    unittest.main()
