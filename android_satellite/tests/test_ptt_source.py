#!/usr/bin/env python3
"""Static safety audit for opt-in Flipper-originated phone push-to-talk."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/"app/src/main/java/com/flipperai/satellite"
UI=(SRC/"MainActivity.java").read_text()
HANDLER=(SRC/"SatelliteRequestHandler.java").read_text()

class PttSourceTest(unittest.TestCase):
    def test_explicit_session_opt_in_and_ble_gate(self):
        self.assertIn("private volatile boolean flipperPttAllowed=false;",UI)
        self.assertIn("flipperPttCheckbox.setChecked(false);",UI)
        self.assertIn("!flipperPttAllowed || !ble.isReady()",UI)
        self.assertIn("Manifest.permission.RECORD_AUDIO",UI)
        self.assertIn("connectionEpoch.matches(authorizedEpoch)",UI)
        self.assertIn("flipperPttAllowed=false;",UI)
    def test_no_phone_mic_action_in_protocol_dispatcher(self):
        self.assertIn('case "PTT":',HANDLER)
        self.assertIn('return fit("ERROR",id,"PTT_DENIED"',HANDLER)
        self.assertNotIn("SpeechRecognizer",HANDLER)
        self.assertNotIn("startListening",HANDLER)

if __name__=="__main__":unittest.main()
