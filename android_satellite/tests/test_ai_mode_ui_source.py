"""Source regression checks for AI mode selection and explicit cloud authorization."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
CHAT = ROOT / "app/src/main/java/com/flipperai/satellite/AiChatActivity.java"
ROUTER = ROOT / "app/src/main/java/com/flipperai/satellite/InferenceRouter.java"
CLOUD = ROOT / "app/src/main/java/com/flipperai/satellite/WorkerCloudEngine.java"

class AiModeUiSourceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.chat = CHAT.read_text(encoding="utf-8")
        cls.router = ROUTER.read_text(encoding="utf-8")
        cls.cloud = CLOUD.read_text(encoding="utf-8")

    def test_default_is_offline(self):
        self.assertIn("aiMode=InferenceRouter.Mode.OFFLINE", self.chat)
        self.assertIn('saved.getString("ai_mode","OFFLINE")', self.chat)
        self.assertIn('saved.getBoolean("cloud_consent",false)', self.chat)

    def test_cloud_consent_requires_confirmation(self):
        self.assertIn('setPositiveButton("I AGREE"', self.chat)
        self.assertIn('putBoolean("cloud_consent",true)', self.chat)
        self.assertIn('putBoolean("cloud_consent",false)', self.chat)

    def test_mode_is_routed(self):
        self.assertIn("new InferenceRouter(local,cloud).ask(text,selectedMode,selectedConsent)", self.chat)
        self.assertIn("if (!cloudConsent) throw new IllegalStateException", self.router)
        self.assertIn("if (cloudConsent && cloud.ready())", self.router)

    def test_cloud_revocation_cancels_request(self):
        self.assertIn("cloud.revokeActive();", self.chat)
        self.assertIn("cloud.clear();", self.chat)
        self.assertIn("previous.disconnect();", self.cloud)

    def test_cloud_key_is_not_persisted(self):
        self.assertNotIn('putString("cloud_token"', self.chat)
        self.assertNotIn('putString("cloud_endpoint"', self.chat)

if __name__ == "__main__":
    unittest.main()
