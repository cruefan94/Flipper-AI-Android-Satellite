"""Read-only source contract: app context, runtime diagnostics and no extra privileges."""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/"app/src/main/java/com/flipperai/satellite"
def read(name):
    return (SRC/name).read_text(encoding="utf-8")
class AppAwarenessSource(unittest.TestCase):
    def test_runtime_context_and_local_identity_answer(self):
        self.assertIn("ConversationConfig",read("EmbeddedLlmEngine.kt"))
        self.assertIn("AppIdentity.SYSTEM_INSTRUCTION",read("EmbeddedLlmEngine.kt"))
        self.assertIn('"role","system"',read("LocalModelEngine.java"))
        self.assertIn('AppIdentity.isAppQuestion(question)',read("AiChatActivity.java"))
        self.assertIn('AppAwareness.answer(this,requestedMode,approved)',read("AiChatActivity.java"))
    def test_read_only_diagnostics_and_no_secrets(self):
        aw=read("AppAwareness.java")
        self.assertIn('BleConnectionService.readOnlyLinkReport()',aw)
        self.assertIn('EmbeddedModelStore.installed(context)',aw)
        self.assertIn('LlamaCppSettings.enabled(context)',aw)
        self.assertIn('CloudCredentialStore.load(context)!=null',aw)
        self.assertNotIn('getAddress()',aw)
        self.assertNotIn('credentials.token',aw)
        self.assertNotIn('requestPermissions(',read("AboutFlipperAiActivity.java"))
        self.assertIn('AboutFlipperAiActivity',read("DashboardActivity.java"))
        self.assertIn('AboutFlipperAiActivity', (ROOT/"app/src/main/AndroidManifest.xml").read_text())
    def test_no_new_network_or_unrestricted_command_permissions(self):
        app=(ROOT/"app/src/main/AndroidManifest.xml").read_text()
        self.assertNotIn('BIND_ACCESSIBILITY_SERVICE',app)
        self.assertNotIn('SYSTEM_ALERT_WINDOW',app)
        self.assertNotIn('QUERY_ALL_PACKAGES',app)
if __name__=="__main__":
    unittest.main()
