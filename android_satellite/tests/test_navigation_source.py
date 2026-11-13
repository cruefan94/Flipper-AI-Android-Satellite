#!/usr/bin/env python3
"""Guard the restored separate-screen Android UX against launcher/UI regressions."""
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

APP=Path(__file__).resolve().parents[1]/"app"
SRC=APP/"src/main/java/com/flipperai/satellite"
M=APP/"src/main/AndroidManifest.xml"
root=ET.parse(M).getroot()
ns="{http://schemas.android.com/apk/res/android}"
DASH=(SRC/"DashboardActivity.java").read_text()
CHAT=(SRC/"AiChatActivity.java").read_text()
MODELS=(SRC/"ModelsActivity.java").read_text()
BLE=(SRC/"MainActivity.java").read_text()
THEME=(APP/"src/main/res/values/styles.xml").read_text()

class NavigationRegressionTests(unittest.TestCase):
    def test_dashboard_is_only_launcher(self):
        app=root.find("application")
        launcher=[]
        names=set()
        for activity in app.findall("activity"):
            name=activity.attrib.get(ns+"name")
            names.add(name)
            for intent in activity.findall("intent-filter"):
                if any(action.attrib.get(ns+"name")=="android.intent.action.MAIN"
                       for action in intent.findall("action")):
                    launcher.append(name)
        self.assertEqual(launcher,[".DashboardActivity"])
        self.assertTrue({".MainActivity",".AiChatActivity",".ModelsActivity",
                         ".PhoneCompanionActivity",".GitHubActivity"}.issubset(names))
        for activity in app.findall("activity"):
            if activity.attrib.get(ns+"name")!=".DashboardActivity":
                self.assertEqual(activity.attrib.get(ns+"exported"),"false")

    def test_dark_theme_and_dedicated_navigation(self):
        self.assertIn('android:theme="@style/FlipperAiTheme"',M.read_text())
        self.assertIn("#ff9614",THEME)
        self.assertIn("FLIPPER AI SATELLITE",DASH)
        for destination in ["AiChatActivity.class","MainActivity.class",
                            "ModelsActivity.class","PhoneCompanionActivity.class",
                            "GitHubActivity.class"]:
            self.assertIn(destination,DASH)
        self.assertNotIn("RadioScannerActivity.class",DASH)
        self.assertNotIn('nav.addView(text("QUICK NAVIGATION"',DASH)

    def test_imported_model_does_not_show_stale_untested_warning(self):
        self.assertIn("OFFLINE • Model imported • Ready for AI chat",CHAT)
        self.assertNotIn("Model file imported; live inference needs testing",CHAT)
        self.assertIn("OFFLINE • No model file • Basic diagnostics only",CHAT)
        self.assertIn("router.ask(question,requestedMode,approved)",CHAT)

    def test_model_and_chat_are_separate_and_real(self):
        self.assertIn("new EmbeddedLlmEngine(",CHAT)
        self.assertIn("router.ask(question,requestedMode,approved)",CHAT)
        self.assertIn("EmbeddedModelStore.importFromDocument(",MODELS)
        self.assertIn('Verified LIVE embedded offline model response:',MODELS)
        self.assertIn('cloud.clear();',CHAT)
        self.assertIn("SatelliteUi.backHome(this,root)",BLE)

if __name__=="__main__":unittest.main()
