#!/usr/bin/env python3
"""Static safety regression: active cloud requests must be revoked on consent loss."""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[1]/"app/src/main/java/com/flipperai/satellite"
ENGINE=(ROOT/"WorkerCloudEngine.java").read_text()
UI=(ROOT/"MainActivity.java").read_text()

class CloudRevocationSourceTest(unittest.TestCase):
    def test_consent_loss_cancels_in_flight_http(self):
        self.assertIn("if(!checked)cloud.revokeActive();",UI)
        self.assertIn("public synchronized void revokeActive()",ENGINE)
        self.assertIn("if(previous!=null)previous.disconnect();",ENGINE)
        self.assertIn("generation++;",ENGINE)
    def test_stale_result_rejected(self):
        self.assertIn('if(generation!=requestGeneration)throw new IllegalStateException("Cloud authorization revoked");',ENGINE)
        self.assertIn("if(active==conn)active=null;",ENGINE)
        self.assertIn("conn.disconnect();",ENGINE)
    def test_setup_failures_also_release_active_connection(self):
        setup=ENGINE.index("conn.setInstanceFollowRedirects(false);")
        guard=ENGINE.rfind("    try {",0,setup)
        self.assertGreater(guard,ENGINE.index("active=conn;"))
        self.assertIn("} finally {",ENGINE[setup:])
        self.assertIn("if(active==conn)active=null;",ENGINE[setup:])
        self.assertIn("conn.disconnect();",ENGINE[setup:])

    def test_concurrent_cloud_request_cannot_hide_active_connection(self):
        # An overlapping request must not replace the only connection
        # tracked for revocation; this remains a source-level invariant.
        self.assertIn('if(active!=null) {conn.disconnect();throw new IllegalStateException("Cloud request already in progress");}',ENGINE)
        self.assertLess(ENGINE.index("if(active!=null)"),ENGINE.index("active=conn;"))
        self.assertIn("if(previous!=null)previous.disconnect();",ENGINE)

    def test_configuration_and_clear_revoke(self):
        self.assertIn("revokeActive();\n    endpoint=checked;",ENGINE)
        self.assertIn('public synchronized void clear() {revokeActive();endpoint="";token="";}',ENGINE)

if __name__=="__main__": unittest.main()
