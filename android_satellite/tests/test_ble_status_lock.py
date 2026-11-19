"""Regression guard: BLE status accessors share the callback's connection monitor.

These static assertions protect a concurrency invariant; they do not simulate
Android Binder callbacks or establish hardware BLE reliability.
"""
from pathlib import Path
import re
import unittest

SOURCE = Path(__file__).resolve().parents[1] / "app/src/main/java/com/flipperai/satellite/BleSatellite.java"


class BleStatusLockTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SOURCE.read_text(encoding="utf-8")

    def test_readiness_and_rpc_accessors_use_connection_monitor(self):
        methods = (
            "hasStockRpc", "isStockRpcSubscribed", "stockRpcNotificationCount",
            "stockRpcFlowCredits", "isReady",
            "gattDiscoveryReport", "gattDiscoveryNextStep",
        )
        for name in methods:
            with self.subTest(name=name):
                self.assertRegex(
                    self.source,
                    r"public\s+synchronized\s+[^\n{]+\b"
                    + re.escape(name) + r"\s*\(",
                )

    def test_callback_and_disconnect_use_same_monitor(self):
        self.assertIn("synchronized(BleSatellite.this)", self.source)
        self.assertRegex(self.source, r"public\s+synchronized\s+void\s+disconnect\s*\(")

    def test_rpc_and_ai_status_remain_distinct(self):
        self.assertIn("return subscribed && approved() && state.phase()==TransportState.Phase.READY;", self.source)
        self.assertIn("return hasStockRpc() && stockRpcSubscribed;", self.source)


if __name__ == "__main__":
    unittest.main()
