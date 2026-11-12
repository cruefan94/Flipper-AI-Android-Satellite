#!/usr/bin/env python3
"""Radio listening source audit. Does not simulate actual audio streaming."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/"app/src/main/java/com/flipperai/satellite"
UI=(BASE/"MainActivity.java").read_text()
RADIO=(BASE/"OnlineRadioPlayer.java").read_text()
POLICY=(BASE/"RadioStreamPolicy.java").read_text()

class RadioSourceTest(unittest.TestCase):
    def test_user_initiated_and_stoppable(self):
        self.assertIn('button("Play selected online radio stream"',UI)
        self.assertIn('button("Stop radio listening"',UI)
        self.assertIn('radioPlayer.stop();',UI)
        self.assertIn('radioPlayer.close();',UI)
        self.assertIn('radioPlayer.play(radioUrl.getText().toString())',UI)
        self.assertIn('candidate.prepareAsync();',RADIO)
        self.assertIn('candidate.setOnErrorListener',RADIO)
    def test_no_recording_or_transmission_apis(self):
        self.assertNotIn("MediaRecorder",RADIO)
        self.assertNotIn("AudioRecord",RADIO)
        self.assertNotIn("BluetoothGatt",RADIO)
        self.assertNotIn("startRecording",RADIO)
    def test_https_only_and_no_source_credentials(self):
        self.assertIn('"https".equalsIgnoreCase(uri.getScheme())',POLICY)
        self.assertIn('uri.getRawUserInfo()!=null',POLICY)

if __name__=="__main__":
    unittest.main()
