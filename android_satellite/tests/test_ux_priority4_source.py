#!/usr/bin/env python3
"""Regression gates for the next Android UX revision (source-only checks)."""
from pathlib import Path
import unittest

SOURCE = Path(__file__).resolve().parents[1] / "app/src/main/java/com/flipperai/satellite"


class NextBuildUxTests(unittest.TestCase):
    def test_picker_change_does_not_claim_live_ble_disconnected(self):
        ui = (SOURCE / "MainActivity.java").read_text(encoding="utf-8")
        begin = ui.index("sources.setOnCheckedChangeListener(")
        end = ui.index("pairedChoice.setChecked(true)", begin)
        handler = ui[begin:end]
        self.assertNotIn('connectionState.setText("AI CONNECTION: NOT CONNECTED")', handler)
        self.assertIn("selectedAddress=null;", handler)
        self.assertIn("pairedSection.setVisibility", handler)
        self.assertIn("discoverableSection.setVisibility", handler)
        self.assertIn("Stock Momentum RPC indications enabled", ui)
        self.assertIn("AI BLE ready", ui)

    def test_selection_and_inference_test_precede_technical_details(self):
        models = (SOURCE / "ModelsActivity.java").read_text(encoding="utf-8")
        first_choice = models.index("USE INSTALLED LITERT MODEL")
        live_test = models.index("3  TEST LIVE OFFLINE AI")
        technical = models.index('sectionTitle(this,page,"MODEL FILES & COMPATIBILITY")')
        self.assertLess(first_choice, technical)
        self.assertLess(live_test, technical)
        self.assertEqual(models.count('3  TEST LIVE OFFLINE AI'), 1)
        self.assertIn("LlamaCppSettings.setEnabled(this,false);", models)
        self.assertIn("LlamaCppSettings.setEnabled(this,true);", models)

    def test_diagnostics_are_behind_advanced_controls(self):
        ui = (SOURCE / "MainActivity.java").read_text(encoding="utf-8")
        advance = ui.index("root.addView(advancedSection);")
        report = ui.index('advancedButton("COPY BLUETOOTH RELIABILITY REPORT"')
        rpc = ui.index('advancedButton("TEST MOMENTUM RPC PING"')
        self.assertLess(advance, report)
        self.assertLess(advance, rpc)
        self.assertIn('button("Connect selected"', ui)
        self.assertIn('button("Disconnect"', ui)

    def test_ux_does_not_weaken_ai_and_cloud_security(self):
        ui = (SOURCE / "MainActivity.java").read_text(encoding="utf-8")
        self.assertIn("CloudCredentialStore.save(", ui)
        self.assertIn("cloudConsent.setChecked(false);", ui)
        self.assertIn("bleService.stopFromUser()", ui)


if __name__ == "__main__":
    unittest.main()
