#!/usr/bin/env python3
"""Structural regression checks; real BLE/Wi-Fi/USB hardware remains unverified."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"app/src/main/java/com/flipperai/satellite"
COORD=(JAVA/"SatelliteTransportCoordinator.java").read_text(encoding="utf-8")
UI=(JAVA/"MainActivity.java").read_text(encoding="utf-8")
PRE=(ROOT/"tools/preflight.py").read_text(encoding="utf-8")


class TransportCoordinatorSourceTest(unittest.TestCase):
    def test_ble_only_default_does_not_auto_fallback(self):
        self.assertIn("selected=SatelliteTransport.Kind.BLE",COORD)
        self.assertIn("if(!adapters.containsKey(kind))return false;",COORD)
        self.assertIn("if(kind!=selected)",COORD)
        self.assertIn("previous.close()",COORD)
        self.assertNotIn("autoFailover",COORD)

    def test_async_answers_bound_to_selected_session(self):
        self.assertIn("transports.register(ble);",UI)
        self.assertIn("transports.invalidateSession();",UI)
        self.assertIn("final long transportGeneration=transports.generation();",UI)
        self.assertIn("transports.sendFrameIfCurrent(response,transportGeneration)",UI)
        self.assertIn("return generation==token && sendFrame(frame);",COORD)

    def test_next_transport_is_not_claimed_ready(self):
        self.assertIn("Wi-Fi and USB-C relay adapters are reserved for future builds",UI)
        self.assertIn("'SatelliteTransportCoordinator'",PRE)


if __name__=="__main__":
    unittest.main()
