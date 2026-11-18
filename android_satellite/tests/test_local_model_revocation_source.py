#!/usr/bin/env python3
"""Source-level guard for cancellation and stale localhost inference results."""
from pathlib import Path
import unittest

ENGINE=(Path(__file__).resolve().parents[1] /
        "app/src/main/java/com/flipperai/satellite/LocalModelEngine.java").read_text()

class LocalModelRevocationSourceTest(unittest.TestCase):
    def test_clear_and_reconfigure_cancel_inflight(self):
        self.assertIn("public synchronized void revokeActive()",ENGINE)
        self.assertIn("if(previous!=null)previous.disconnect();",ENGINE)
        self.assertIn('public synchronized void clear(){revokeActive();endpoint="";model="local";}',ENGINE)
        self.assertLess(ENGINE.index("revokeActive();\n    endpoint=validated"),ENGINE.index("endpoint=validated;model=modelName;"))

    def test_overlapping_request_does_not_hide_active_connection(self):
        self.assertIn("if(active!=null){conn.disconnect();throw new IllegalStateException",ENGINE)
        self.assertLess(ENGINE.index("if(active!=null)"),ENGINE.index("active=conn;"))
        self.assertIn("if(active==conn)active=null;",ENGINE)

    def test_stale_response_discarded_and_cleanup(self):
        self.assertIn("if(generation!=requestGeneration)throw new IllegalStateException",ENGINE)
        self.assertLess(ENGINE.rfind("if(generation!=requestGeneration)"),ENGINE.index("return reply;"))
        self.assertIn("}finally{\n      synchronized(this){if(active==conn)active=null;}\n      conn.disconnect();",ENGINE)
        self.assertIn("Proxy.NO_PROXY",ENGINE)
        self.assertIn("conn.setInstanceFollowRedirects(false);",ENGINE)

if __name__=="__main__": unittest.main()
