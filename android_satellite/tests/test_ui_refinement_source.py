#!/usr/bin/env python3
"""UI refinement guards; source-only, not device screenshots or interaction tests."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app"
SRC=APP/"src/main/java/com/flipperai/satellite"
UI=(SRC/"SatelliteUi.java").read_text()
DASH=(SRC/"DashboardActivity.java").read_text()
CHAT=(SRC/"AiChatActivity.java").read_text()
RADIO=(SRC/"RadioScannerActivity.java").read_text()
MODELS=(SRC/"ModelsActivity.java").read_text()
PHONE=(SRC/"PhoneCompanionActivity.java").read_text()
THEME=(APP/"src/main/res/values/styles.xml").read_text()

class UiRefinementSourceTests(unittest.TestCase):
    def test_existing_dark_orange_theme_preserved_and_accessible(self):
        self.assertIn("0xff080b10",UI)
        self.assertIn("0xffff9614",UI)
        self.assertIn("#ff9614",THEME)
        self.assertIn('name="android:textColorPrimary"',THEME)
        self.assertIn('name="android:textColorSecondary"',THEME)
        self.assertIn('name="android:textColorHint"',THEME)
        self.assertIn("v.setMinHeight(dp(a,52))",UI)
        self.assertIn("v.setFocusable(true)",UI)
        self.assertIn("v.setContentDescription(title)",UI)

    def test_dashboard_has_one_route_per_destination_and_remains_multi_screen(self):
        self.assertIn("SatelliteUi.sectionTitle(this,content",DASH)
        self.assertIn("wide?dp(680):-1",DASH)
        self.assertNotIn('link(nav,"◉","AI CHAT"',DASH)
        self.assertNotIn("nav.addView(text(\"NAVIGATION\"",DASH)
        self.assertEqual(DASH.count("MainActivity.class);"),1)
        self.assertEqual(DASH.count("AiChatActivity.class);"),1)
        for activity in ["ModelsActivity","RadioScannerActivity","PhoneCompanionActivity","GitHubActivity"]:
            self.assertEqual(DASH.count(activity+".class);"),1)
        self.assertIn('panelCard(content,"♫","RADIO LISTENING"',DASH)

    def test_chat_optional_tools_preserve_ai_and_microphone(self):
        self.assertIn('smallAction(navigation,"‹ HOME"',CHAT)
        self.assertIn('smallAction(navigation,"MODE",this::chooseMode)',CHAT)
        self.assertIn('smallAction(navigation,"TOOLS"',CHAT)
        self.assertIn('smallAction(cloudControls,"CLOUD",this::cloudSettings)',CHAT)
        self.assertIn('smallAction(cloudControls,"MODELS"',CHAT)
        self.assertIn("toolsPanel.setVisibility(View.GONE)",CHAT)
        self.assertIn("toolsPanel.setVisibility(toolsExpanded && !keyboardCompact?",CHAT)
        self.assertIn("toolsPanel.addView(voiceControls)",CHAT)
        self.assertIn("toolsPanel.addView(actions)",CHAT)
        self.assertIn("if(compact!=keyboardCompact)",CHAT)
        self.assertIn("speech.speak(ChatMarkdownFormatter.plainSpeech(answer))",CHAT)
        self.assertIn('smallAction(microphoneControls,"TALK",this::startVoiceInput)',CHAT)
        self.assertIn("prompt.append(recognized);",CHAT)
        self.assertIn("router.askWithSource(question,requestedMode,approved)",CHAT)
        self.assertIn("cloud.revokeActive();",CHAT)

    def test_radio_reuses_theme_and_single_accessible_playback_group(self):
        self.assertIn('heading(page,"PLAYBACK")',RADIO)
        self.assertEqual(RADIO.count('"PAUSE RADIO"'),1)
        self.assertEqual(RADIO.count('"RESUME RADIO"'),1)
        self.assertEqual(RADIO.count('"STOP RADIO"'),1)
        self.assertIn('mediaButton(playback,"PAUSE RADIO"',RADIO)
        self.assertIn('mediaButton(playback,"STOP RADIO"',RADIO)
        self.assertIn("control.setMinHeight(SatelliteUi.dp(this,52))",RADIO)
        self.assertIn("control.setContentDescription(label)",RADIO)
        self.assertIn("SatelliteUi.sectionTitle(this,page,title)",RADIO)
        self.assertIn("RadioStreamPolicy.isAllowed(url)",RADIO)

    def test_models_do_not_queue_overlapping_operations(self):
        self.assertIn("operationProgress=new ProgressBar(this)",MODELS)
        self.assertIn("operationProgress.setIndeterminate(true)",MODELS)
        self.assertIn("if(!beginOperation(",MODELS)
        self.assertIn("operationRunning=true;",MODELS)
        self.assertIn("operationRunning=false;",MODELS)
        self.assertGreaterEqual(MODELS.count("finally{finishOperation();}"),3)
        self.assertIn("EmbeddedModelStore.importFromDocument(this,selected)",MODELS)
        self.assertIn("Verified LIVE embedded offline model response",MODELS)

    def test_phone_status_is_grounded_and_radio_copy_is_current(self):
        self.assertIn("AndroidPhoneFacts.snapshot(this)",PHONE)
        self.assertIn('PhoneFacts.answer("phone battery",snapshot)',PHONE)
        self.assertIn('PhoneFacts.answer("network status",snapshot)',PHONE)
        self.assertIn("Phone Bluetooth:",PHONE)
        self.assertIn("individual feeds unverified",PHONE)
        self.assertNotIn("Radio Scanner: deferred",PHONE)
        self.assertIn("SatelliteUi.backHome(this,root)",PHONE)

if __name__=="__main__":
    unittest.main()
