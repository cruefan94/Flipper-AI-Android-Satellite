#!/usr/bin/env python3
"""Future Wi-Fi AI1 relay security gates; does not claim an operational ESP relay."""
from pathlib import Path
import unittest

SRC=Path(__file__).resolve().parents[1]/"app/src/main/java/com/flipperai/satellite"
POLICY=(SRC/"WifiRelayEndpointPolicy.java").read_text(encoding="utf-8")
ENVELOPE=(SRC/"WifiRelayEnvelope.java").read_text(encoding="utf-8")
COORD=(SRC/"SatelliteTransportCoordinator.java").read_text(encoding="utf-8")
UI=(SRC/"MainActivity.java").read_text(encoding="utf-8")

class WifiRelaySourceTest(unittest.TestCase):
    def test_https_and_local_dns_only(self):
        self.assertIn('"https".equalsIgnoreCase(uri.getScheme())',POLICY)
        self.assertIn('h.endsWith(".home.arpa")',POLICY)
        self.assertIn('h.endsWith(".local")',POLICY)
        self.assertIn('uri.getRawUserInfo()!=null',POLICY)
        self.assertIn('uri.getRawQuery()!=null',POLICY)
        self.assertIn('"/ai1/relay".equals(uri.getRawPath())',POLICY)

    def test_hmac_session_binding_and_bounded_ai1(self):
        self.assertIn('Mac.getInstance("HmacSHA256")',ENVELOPE)
        self.assertIn('MessageDigest.isEqual',ENVELOPE)
        self.assertIn('RANDOM.nextBytes(bytes)',ENVELOPE)
        self.assertIn('minimumCounter',ENVELOPE)
        self.assertIn('validNonce(nonce)',ENVELOPE)
        self.assertIn('SatelliteProtocol.decode(frame)',ENVELOPE)
        self.assertIn('SatelliteProtocol.MAX_BYTES',ENVELOPE)

    def test_relay_not_selectable_before_server_and_tls_available(self):
        self.assertIn('selected=SatelliteTransport.Kind.BLE',COORD)
        self.assertIn('if(!adapters.containsKey(kind))return false;',COORD)
        self.assertNotIn('transports.register(new Wifi',UI)
        self.assertIn('Wi-Fi and USB-C relay adapters are reserved for future builds',UI)

if __name__=="__main__":
    unittest.main()
