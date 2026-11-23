#!/usr/bin/env python3
"""Source checks for the opt-in on-device TTS feature. Not a hardware audio test."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/northerncircuitstudios/ai"
SPEAKER = (JAVA / "VoiceSpeaker.java").read_text()
SETTINGS = (JAVA / "VoiceSettings.java").read_text()
ACTIVITY = (JAVA / "AssistantActivity.java").read_text()
MANIFEST = (ROOT / "src/main/AndroidManifest.xml").read_text()
GRADLE = (ROOT / "build.gradle").read_text()


class VoiceSourceTests(unittest.TestCase):
    def test_android_tts_only(self):
        self.assertIn("new TextToSpeech", SPEAKER)
        self.assertIn("speech.getVoices()", SPEAKER)
        self.assertIn("voice.isNetworkConnectionRequired()", SPEAKER)
        self.assertIn("KEY_FEATURE_NOT_INSTALLED", SPEAKER)
        self.assertIn("speech.setVoice(chosen)", SPEAKER)
        self.assertIn("speech.setSpeechRate(settings.rate)", SPEAKER)
        self.assertIn("speech.setPitch(settings.pitch)", SPEAKER)
        self.assertIn("QUEUE_FLUSH", SPEAKER)
        self.assertIn("QUEUE_ADD", SPEAKER)
        self.assertIn("speech.shutdown()", SPEAKER)
        self.assertNotIn("HttpsURLConnection", SPEAKER)
        self.assertNotIn("apiKey", SPEAKER)

    def test_voice_user_controls_and_modes(self):
        self.assertIn('"AUTO VOICE: OFF"', ACTIVITY)
        self.assertIn('"CHOOSE VOICE"', ACTIVITY)
        self.assertIn('"READ LAST"', ACTIVITY)
        self.assertIn('"STOP VOICE"', ACTIVITY)
        self.assertIn('voiceSettings.autoRead && voiceSpeaker != null', ACTIVITY)
        self.assertIn('voiceSpeaker.speak(answer, voiceSettings)', ACTIVITY)
        self.assertIn('voiceSpeaker.speak(latest, voiceSettings)', ACTIVITY)
        self.assertIn('voiceSpeaker.stop()', ACTIVITY)
        self.assertIn('voiceSpeaker.shutdown()', ACTIVITY)
        self.assertIn('speed.setOnSeekBarChangeListener(changes)', ACTIVITY)
        self.assertIn('pitch.setOnSeekBarChangeListener(changes)', ACTIVITY)
        self.assertIn('Local', ACTIVITY)
        self.assertIn('cloud', ACTIVITY.lower())

    def test_voice_default_off_and_app_private_persistence(self):
        self.assertIn('p.getBoolean("auto", false)', SETTINGS)
        self.assertIn("getSharedPreferences", SETTINGS)
        self.assertIn("Context.MODE_PRIVATE", SETTINGS)
        self.assertIn("Math.max(0.7f, Math.min(1.5f, value))", SETTINGS)
        self.assertIn('android:allowBackup="false"', MANIFEST)
        self.assertIn('android.intent.action.TTS_SERVICE', MANIFEST)
        self.assertNotIn("android.permission.RECORD_AUDIO", MANIFEST)
        self.assertNotIn("android.permission.BLUETOOTH", MANIFEST)

    def test_side_by_side_no_app_replacement(self):
        self.assertIn("applicationIdSuffix '.m6preview'", GRADLE)
        self.assertIn("'Northern AI S25 Preview'", GRADLE)


if __name__ == "__main__":
    unittest.main()
