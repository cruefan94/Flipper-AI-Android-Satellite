"""Source regression: direct Worker calls must validate Unicode before networking."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
WORKER = ROOT / "app/src/main/java/com/flipperai/satellite/WorkerCloudEngine.java"

class CloudPromptValidationSourceTest(unittest.TestCase):
    def test_validation_precedes_connection(self):
        source = WORKER.read_text(encoding="utf-8")
        method = source.split("public String answer(String prompt)", 1)[1]
        validation = method.index("PromptLimits.validate(prompt);")
        size_limit = method.index("if(prompt.length()>127)")
        connection = method.index("openConnection(Proxy.NO_PROXY)")
        self.assertLess(validation, size_limit)
        self.assertLess(size_limit, connection)

if __name__ == "__main__":
    unittest.main()
