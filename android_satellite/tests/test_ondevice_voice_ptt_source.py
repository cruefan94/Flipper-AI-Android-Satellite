#!/usr/bin/env python3
"""Source-only opt-in/on-device voice regression. Not a real microphone test."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

APP=Path(__file__).resolve().parents[1]/"app"
SRC=APP/"src/main/java/com/flipperai/satellite"
CHAT=(SRC/"AiChatActivity.java").read_text()
VOICE=(SRC/"OnDeviceVoiceInput.java").read_text()
TTS=(SRC/"AiSpeech.java").read_text()
MANIFEST=ET.parse(APP/"src/main/AndroidManifest.xml").getroot()
ANDROID="{http://schemas.android.com/apk/res/android}"


class VoicePttSourceTest(unittest.TestCase):
    def test_user_initiated_permission_not_automatic_capture(self):
        uses=[e.attrib.get(ANDROID+"name") for e in MANIFEST.findall("uses-permission")]
        self.assertIn("android.permission.RECORD_AUDIO",uses)
        self.assertIn('smallAction(microphoneControls,"TALK",this::startVoiceInput)',CHAT)
        self.assertIn('requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}',CHAT)
        self.assertIn('MICROPHONE_PERMISSION_REQUEST',CHAT)
        self.assertIn("grants[0]==PackageManager.PERMISSION_GRANTED",CHAT)
        self.assertIn("voiceInput.startListening();",CHAT)
        self.assertIn("checkSelfPermission(Manifest.permission.RECORD_AUDIO)",VOICE)
        # Permission approval does not begin listening; a second TALK tap is required.
        handler=CHAT.split("onRequestPermissionsResult(",1)[1].split("private void ask()",1)[0]
        self.assertNotIn("startListening()",handler)
        self.assertNotIn("voiceInput.startListening()",CHAT.split("public void onCreate(",1)[1].split("private void applyChatWindowInsets",1)[0])

    def test_only_guaranteed_ondevice_recognizer(self):
        self.assertIn("Build.VERSION.SDK_INT<31",VOICE)
        self.assertIn("SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)",VOICE)
        self.assertIn("SpeechRecognizer.createOnDeviceSpeechRecognizer(activity)",VOICE)
        self.assertNotIn("SpeechRecognizer.createSpeechRecognizer(",VOICE)
        self.assertIn("RecognizerIntent.EXTRA_PREFER_OFFLINE,true",VOICE)
        self.assertNotIn("HttpURLConnection",VOICE)
        self.assertNotIn("URL(",VOICE)
        self.assertNotIn("cloud.",VOICE)
        self.assertNotIn("new WorkerCloudEngine",VOICE)
        queries=MANIFEST.find("queries")
        self.assertIsNotNone(queries)
        services=[i.attrib.get(ANDROID+"name") for i in queries.findall("./intent/action")]
        self.assertIn("android.speech.RecognitionService",services)
        self.assertIn("android.intent.action.TTS_SERVICE",services)

    def test_transcript_only_prepopulates_composer(self):
        self.assertIn('smallAction(microphoneControls,"FINISH",()->voiceInput.finishListening())',CHAT)
        self.assertIn('smallAction(microphoneControls,"CANCEL MIC",()->voiceInput.cancelListening())',CHAT)
        self.assertIn("prompt.append(recognized);",CHAT)
        self.assertIn("Review it, then tap SEND",CHAT)
        self.assertNotIn("ask();",CHAT.split("@Override public void transcript(String recognized)",1)[1].split("});",1)[0])
        self.assertNotIn("router.ask(",VOICE)
        self.assertNotIn("sendFrame(",VOICE)

    def test_capture_stops_on_lifecycle_and_stale_callbacks_are_rejected(self):
        self.assertIn("@Override protected void onPause()",CHAT)
        self.assertIn("if(voiceInput!=null)voiceInput.cancelListening();",CHAT)
        self.assertIn("if(voiceInput!=null)voiceInput.close();",CHAT)
        self.assertIn("int currentGeneration=++generation;",VOICE)
        self.assertIn("if(currentGeneration!=generation)return;",VOICE)
        self.assertIn("generation++;",VOICE)
        self.assertIn("old.cancel();",VOICE)
        self.assertIn("old.destroy();",VOICE)

    def test_existing_outbound_tts_remains_offline_voice_only(self):
        self.assertIn("!voice.isNetworkConnectionRequired()",TTS)
        self.assertIn("speech.speak(ChatMarkdownFormatter.plainSpeech(answer))",CHAT)
        self.assertIn("speech.pickVoice(this)",CHAT)


if __name__=="__main__":
    unittest.main()
