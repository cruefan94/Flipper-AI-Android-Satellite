#!/usr/bin/env python3
"""Scope regression: radio streaming is excluded from the AI Satellite Android app."""
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

APP = Path(__file__).resolve().parents[1] / "app"
SRC = APP / "src/main/java/com/flipperai/satellite"
MANIFEST = APP / "src/main/AndroidManifest.xml"
DASH = (SRC / "DashboardActivity.java").read_text()
PHONE = (SRC / "PhoneCompanionActivity.java").read_text()
ROOT = ET.parse(MANIFEST).getroot()
NS = "{http://schemas.android.com/apk/res/android}"

class SatelliteScopeTests(unittest.TestCase):
    def test_radio_sources_and_entry_points_are_removed(self):
        for name in ("RadioScannerActivity", "RadioPlayerService",
                     "RadioStationDirectory", "RadioStreamPolicy"):
            self.assertFalse((SRC / (name + ".java")).exists(), name)
            self.assertNotIn(name, DASH)
            self.assertNotIn("." + name, MANIFEST.read_text())
        self.assertNotIn("RADIO LISTENING", DASH)
        self.assertNotIn("Radio: public internet streams", PHONE)

    def test_media_playback_permission_removed_but_essential_services_kept(self):
        permissions = {x.attrib.get(NS + "name") for x in ROOT.findall("uses-permission")}
        self.assertNotIn("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK", permissions)
        self.assertIn("android.permission.BLUETOOTH_CONNECT", permissions)
        self.assertIn("android.permission.INTERNET", permissions)
        app = ROOT.find("application")
        services = {x.attrib.get(NS + "name") for x in app.findall("service")}
        self.assertIn(".BleConnectionService", services)
        activities = {x.attrib.get(NS + "name") for x in app.findall("activity")}
        for name in (".DashboardActivity", ".MainActivity", ".AiChatActivity", ".ModelsActivity"):
            self.assertIn(name, activities)

    def test_core_ai_and_flipper_navigation_preserved(self):
        for screen in ("MainActivity", "AiChatActivity", "ModelsActivity"):
            self.assertEqual(DASH.count(screen + ".class);"), 1)

if __name__ == "__main__":
    unittest.main()
