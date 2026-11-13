#!/usr/bin/env python3
"""Source security and UI guards for main-app voice + safe Markdown presentation.
Host tests, NOT on-device visual or speech verification.
"""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"app/src/main/java/com/flipperai/satellite"
CHAT=(JAVA/"AiChatActivity.java").read_text()
FORMAT=(JAVA/"ChatMarkdownFormatter.java").read_text()
VOICE=(JAVA/"AiSpeech.java").read_text()
MANIFEST=(ROOT/"app/src/main/AndroidManifest.xml").read_text()
GRADLE=(ROOT/"app/build.gradle").read_text()
BLE=(JAVA/"MainActivity.java").read_text()


class MainVoiceMarkdown(unittest.TestCase):
    def test_main_package_and_existing_model_are_preserved(self):
        self.assertIn("applicationId 'com.flipperai.satellite'",GRADLE)
        self.assertNotIn("applicationIdSuffix",GRADLE)
        self.assertIn('android:label="Flipper AI Satellite"',MANIFEST)
        self.assertIn("EmbeddedModelStore.installed(this)",CHAT)
        self.assertIn("embedded.configure(EmbeddedModelStore.modelFile(this).getAbsolutePath())",CHAT)

    def test_offline_voice_is_opt_in_to_installed_tts_only(self):
        self.assertIn("!voice.isNetworkConnectionRequired()",VOICE)
        self.assertIn("!offlineVoiceReady",VOICE)
        self.assertIn("englishLocalVoices()",VOICE)
        self.assertIn("speech.pickVoice(this)",CHAT)
        self.assertIn("speech.setEnabled(!speech.enabled())",CHAT)
        self.assertIn("speech.close()",CHAT)
        self.assertIn('android.intent.action.TTS_SERVICE',MANIFEST)
        self.assertNotIn("RECORD_AUDIO",MANIFEST)

    def test_safe_markdown_cleaned_for_chat_and_speech(self):
        self.assertIn("ChatMarkdownFormatter.render(message)",CHAT)
        self.assertIn("ChatMarkdownFormatter.plainSpeech(answer)",CHAT)
        self.assertIn("styledHistory.clear()",CHAT)
        self.assertIn("transcript.setText(styledHistory)",CHAT)
        self.assertIn("Matcher heading=HEADING.matcher(line)",FORMAT)
        self.assertIn('line="•  "+bullet.group(1)',FORMAT)
        self.assertIn("new StyleSpan(Typeface.BOLD)",FORMAT)
        self.assertIn("new RelativeSizeSpan",FORMAT)
        self.assertIn("new TypefaceSpan",FORMAT)
        self.assertNotIn("Html.fromHtml",FORMAT)
        self.assertNotIn("WebView",FORMAT)
        self.assertNotIn("Runtime.getRuntime",FORMAT)
        self.assertIn("history.toString()",CHAT)

    def test_phone_ble_report_requires_physical_protocol_verification(self):
        self.assertIn("Copy BLE hardware test report",BLE)
        self.assertIn("HELLO/ASK not confirmed",BLE)
        self.assertIn("model file presence does not prove live AI inference",BLE.lower())
        self.assertIn("ClipboardManager",BLE)
        self.assertNotIn('+"Selected address:"',BLE)

    def test_chat_layout_and_consent_preserved(self):
        for value in ["WindowInsets.Type.systemBars()",
                      "WindowInsets.Type.ime()",
                      "Math.max(bars.bottom,ime.bottom)",
                      'send.setText("SEND")',
                      "router.ask(question,requestedMode,approved)",
                      "if(generation==requestGeneration && (!approved||consent))"]:
            self.assertIn(value,CHAT)


if __name__=="__main__":
    unittest.main()
