#!/usr/bin/env python3
"""Safety regression tests for rotation/app-switch continuity and cloud settings."""
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/"app/src/main/java/com/flipperai/satellite"
CHAT=(SRC/"AiChatActivity.java").read_text()
BLE=(SRC/"MainActivity.java").read_text()
STORE=(SRC/"CloudCredentialStore.java").read_text()
MANIFEST=(ROOT/"app/src/main/AndroidManifest.xml").read_text()

class ChatContinuityAndCloudSettingsSourceTest(unittest.TestCase):
    def test_rotation_does_not_recreate_active_ai_chat_or_ble(self):
        self.assertIn('android:name=".AiChatActivity" android:exported="false" android:configChanges="orientation|screenSize|keyboardHidden"', MANIFEST)
        self.assertIn('android:name=".MainActivity" android:exported="false" android:configChanges="orientation|screenSize|keyboardHidden"', MANIFEST)
        self.assertIn("void onConfigurationChanged(",CHAT)
        self.assertIn("requestApplyInsets()",CHAT)

    def test_app_switch_does_not_interrupt_active_tts_or_reload_unchanged_model(self):
        on_stop=CHAT[CHAT.index("protected void onStop()"):CHAT.index("@Override protected void onDestroy()")]
        self.assertNotIn("speech.stop();",on_stop)
        self.assertIn("if(revision.equals(loadedModelRevision))return;",CHAT)
        self.assertIn("if(!revision.equals(modelRevision()))return;",CHAT)
        # Privacy guard: background microphone capture stays explicitly cancelled.
        self.assertIn("if(voiceInput!=null)voiceInput.cancelListening();",CHAT)

    def test_credentials_are_sealed_using_non_exportable_keystore_key(self):
        for expected in ('"AndroidKeyStore"', '"AES/GCM/NoPadding"',
                         'GCMParameterSpec(128,iv)', 'KeyProperties.PURPOSE_ENCRYPT',
                         'KeyProperties.PURPOSE_DECRYPT',
                         '.setRandomizedEncryptionRequired(true)',
                         '.putString(RECORD,sealed.toString())',
                         'cipher.doFinal(plain)', 'cipher.doFinal(data)'):
            self.assertIn(expected,STORE)
        self.assertNotIn('putString("token"',STORE)
        self.assertNotIn('getExternalFilesDir',STORE)
        self.assertIn('catch(Exception invalidOrUnrecoverable)',STORE)
        self.assertIn('clear(context);',STORE)

    def test_cloud_credentials_persist_but_consent_is_not_saved(self):
        for ui in (CHAT,BLE):
            self.assertIn('CloudCredentialStore.load(this)',ui)
            self.assertIn('CloudCredentialStore.save(this,',ui)
            self.assertIn('CloudCredentialStore.clear(this)',ui)
        self.assertIn('private volatile boolean consent=false;',CHAT)
        self.assertIn('cloudConsent.setChecked(false);',BLE)
        self.assertNotIn('putBoolean("consent"',STORE)
        self.assertNotIn('GEMINI_API_KEY',STORE)
        self.assertIn('WorkerEndpointPolicy.validate(endpoint,token)',STORE)

    def test_active_cloud_requests_remain_revocable(self):
        self.assertIn('consent=false;cloud.clear();CloudCredentialStore.clear(this);++requestGeneration;',CHAT)
        self.assertIn('cloudConsent.setChecked(false);',BLE)
        self.assertIn('cloud.clear();',BLE)

if __name__=="__main__":
    unittest.main()
