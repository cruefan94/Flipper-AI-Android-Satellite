"""Host-only guard: cloud transport exceptions must not expose URL/token in UI."""
from pathlib import Path
import unittest

SOURCE = (Path(__file__).resolve().parents[1] /
          "app/src/main/java/com/flipperai/satellite/WorkerCloudEngine.java")


class WorkerNetworkRedactionSourceTest(unittest.TestCase):
    def test_raw_io_error_is_not_propagated(self):
        source = SOURCE.read_text(encoding="utf-8")
        self.assertIn("catch(IOException networkFailure)", source)
        handler = source.split("catch(IOException networkFailure)", 1)[1].split("finally", 1)[0]
        self.assertIn("Cloud network or TLS request failed.", handler)
        self.assertNotIn("networkFailure.getMessage()", handler)
        self.assertNotIn("networkFailure.toString()", handler)
        self.assertNotIn("networkFailure.printStackTrace", handler)
        self.assertNotIn("networkFailure);", handler)
        self.assertNotIn("networkFailure,", handler)

    def test_revoked_request_takes_priority_over_disconnect_io(self):
        source = SOURCE.read_text(encoding="utf-8")
        handler = source.split("catch(IOException networkFailure)", 1)[1].split("finally", 1)[0]
        self.assertIn("if(generation!=requestGeneration)", handler)
        self.assertIn('throw new IllegalStateException("Cloud authorization revoked")', handler)
        self.assertLess(handler.index("Cloud authorization revoked"),
                        handler.index("Cloud network or TLS request failed."))
        # Cancellation must not leak the original IOException as a cause.
        self.assertNotIn("new IllegalStateException(networkFailure", handler)
        self.assertNotIn("throw networkFailure", handler)

    def test_authentication_diagnostic_is_separate(self):
        source = SOURCE.read_text(encoding="utf-8")
        self.assertIn("WorkerHttpStatus.describe(status)", source)
        self.assertIn("conn.getResponseCode()", source)


if __name__ == "__main__":
    unittest.main()
