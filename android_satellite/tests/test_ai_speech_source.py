#!/usr/bin/env python3
"""Source guards for optional, offline-only spoken AI replies and accessible chat composer.

These cannot replace a phone audio / screen-rotation / keyboard test.
"""
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]/"app"
SRC=ROOT/"src/main/java/com/flipperai/satellite"
CHAT=(SRC/"AiChatActivity.java").read_text()
SPEECH=(SRC/"AiSpeech.java").read_text()
MANIFEST=(ROOT/"src/main/AndroidManifest.xml").read_text()


class AiSpeechSourceGuards(unittest.TestCase):
    def test_only_local_tts_voices_are_eligible(self):
        self.assertIn("!voice.isNetworkConnectionRequired()",SPEECH)
        self.assertIn("offlineVoiceReady",SPEECH)
        self.assertIn("!offlineVoiceReady",SPEECH)
        self.assertIn('name.contains("female")',SPEECH)
        self.assertIn("englishLocalVoices()",SPEECH)
        self.assertNotIn("RECORD_AUDIO",MANIFEST)
        self.assertNotIn("SpeechRecognizer",CHAT+SPEECH)

    def test_voice_selector_and_mute_are_user_controllable(self):
        for s in ['"VOICE ON"','"VOICE OFF"','"CHOOSE VOICE"','"STOP"']:
            self.assertIn(s,CHAT)
        self.assertIn("speech.setEnabled(!speech.enabled())",CHAT)
        self.assertIn("speech.pickVoice(this)",CHAT)
        self.assertIn('prefs.edit().putString(PREF_VOICE,selectedName).apply()',SPEECH)
        self.assertIn('prefs.edit().putBoolean(PREF_ENABLED,value).apply()',SPEECH)

    def test_speech_only_after_current_ai_response(self):
        start=CHAT.index("if(generation==requestGeneration && (!approved||consent))")
        end=CHAT.index("}else setStatus",start)
        accepted=CHAT[start:end]
        self.assertIn('append("AI",answer);',accepted)
        self.assertIn("speech.speak(answer);",accepted)
        self.assertLess(accepted.index('append("AI",answer);'),
                        accepted.index("speech.speak(answer);"))
        self.assertIn("speech.stop();",CHAT)
        self.assertIn("speech.close();",CHAT)

    def test_voice_preview_keeps_working_app_and_its_model_untouched(self):
        gradle=(ROOT/"build.gradle").read_text()
        self.assertIn("applicationIdSuffix '.voicepreview'",gradle)
        self.assertIn('Flipper AI Satellite Voice Test',MANIFEST)

    def test_chat_respects_android_system_bars_and_ime(self):
        self.assertIn("WindowInsets.Type.systemBars()",CHAT)
        self.assertIn("WindowInsets.Type.displayCutout()",CHAT)
        self.assertIn("WindowInsets.Type.ime()",CHAT)
        self.assertIn("Math.max(bars.bottom,ime.bottom)",CHAT)
        self.assertIn("page.requestApplyInsets()",CHAT)
        self.assertIn("prompt.setBackground(SatelliteUi.panel(this,false))",CHAT)
        self.assertIn('send.setText("SEND")',CHAT)
        self.assertIn("android.intent.action.TTS_SERVICE",MANIFEST)

if __name__=="__main__":
    unittest.main()
