#!/usr/bin/env python3
"""Radio integration guards. These checks do not prove any station is playable."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/"app/src/main/java/com/flipperai/satellite"
M=ROOT/"app/src/main/AndroidManifest.xml"
NS="{http://schemas.android.com/apk/res/android}"
RADIO=(SRC/"RadioScannerActivity.java").read_text()
SERVICE=(SRC/"RadioPlayerService.java").read_text()
DIRECTORY=(SRC/"RadioStationDirectory.java").read_text()
POLICY=(SRC/"RadioStreamPolicy.java").read_text()
DASH=(SRC/"DashboardActivity.java").read_text()
CHAT=(SRC/"AiChatActivity.java").read_text()
BLE=(SRC/"MainActivity.java").read_text()
MANIFEST=ET.parse(M).getroot()

class RadioIntegrationSourceTests(unittest.TestCase):
    def test_dedicated_radio_screen_preserves_existing_app(self):
        root=MANIFEST.find("application")
        activities={x.attrib.get(NS+"name"):x for x in root.findall("activity")}
        self.assertEqual(activities[".RadioScannerActivity"].attrib.get(NS+"exported"),"false")
        self.assertEqual(activities[".DashboardActivity"].attrib.get(NS+"exported"),"true")
        for name in [".AiChatActivity",".ModelsActivity",".MainActivity",".PhoneCompanionActivity",".GitHubActivity"]:
            self.assertIn(name,activities)
        self.assertIn('panelCard(content,"♫","RADIO LISTENING"',DASH)
        self.assertIn("RadioScannerActivity.class",DASH)
        self.assertIn('SatelliteUi.scrollPage(this,"RADIO LISTENING"',RADIO)
        self.assertIn('SatelliteUi.backHome(this,page)',RADIO)
        self.assertNotIn("RadioScannerActivity.class",CHAT)
        self.assertIn("class MainActivity extends Activity",BLE)

    def test_no_transmitting_or_recording_features_in_radio(self):
        for source in [RADIO,SERVICE,DIRECTORY]:
            self.assertNotIn("SpeechRecognizer",source)
            self.assertNotIn("MediaRecorder",source)
            self.assertNotIn("AudioRecord(",source)
            self.assertNotIn("RECORD_AUDIO",source)
            self.assertNotIn("startAdvertising",source)
            self.assertNotIn("sendFrame(",source)
            self.assertNotIn("System.loadLibrary",source)
        self.assertIn("Receive-only",SERVICE)
        self.assertIn("public internet feed",RADIO)

    def test_explicit_user_controlled_playback_and_favorites(self):
        self.assertIn('"PLAY SELECTED ONLINE STATION",this::playDiscovered',RADIO)
        self.assertIn('"PLAY SELECTED FAVORITE",this::playFavorite',RADIO)
        self.assertIn('"PAUSE RADIO",()->command("pause",null,null)',RADIO)
        self.assertIn('"STOP RADIO",()->command("stop",null,null)',RADIO)
        self.assertIn("RadioStreamPolicy.isAllowed(url)",RADIO)
        self.assertIn("MAX_SAVED=100",RADIO)
        self.assertIn('getSharedPreferences(PREFS,MODE_PRIVATE)',RADIO)
        self.assertIn("startForegroundService(intent)",RADIO)
        self.assertIn("MediaPlayer",SERVICE)
        self.assertIn("MediaSession",SERVICE)
        self.assertIn("startForeground(NOTIFICATION,notification())",SERVICE)

    def test_media_service_is_private_and_declares_required_type(self):
        root=MANIFEST.find("application")
        services={x.attrib.get(NS+"name"):x for x in root.findall("service")}
        service=services[".RadioPlayerService"]
        self.assertEqual(service.attrib.get(NS+"exported"),"false")
        self.assertEqual(service.attrib.get(NS+"foregroundServiceType"),"mediaPlayback")
        perms=[x.attrib.get(NS+"name") for x in MANIFEST.findall("uses-permission")]
        for required in ["android.permission.INTERNET","android.permission.FOREGROUND_SERVICE",
                         "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"]:
            self.assertIn(required,perms)
        self.assertIn("Build.VERSION.SDK_INT>=33",RADIO)
        self.assertIn("Context.RECEIVER_NOT_EXPORTED",RADIO)

    def test_only_https_directory_and_stream_urls(self):
        self.assertIn("https://de1.api.radio-browser.info/",DIRECTORY)
        self.assertIn('connection.setInstanceFollowRedirects(false)',DIRECTORY)
        self.assertIn("RadioStreamPolicy.isAllowed(s)",DIRECTORY)
        self.assertIn("RadioStreamPolicy.isAllowed(url)",SERVICE)
        self.assertIn('"https".equalsIgnoreCase(uri.getScheme())',POLICY)
        self.assertIn('h.equals("localhost")',POLICY)
        self.assertNotIn('"http://',RADIO)
        self.assertNotIn("Runtime.getRuntime()",RADIO)

if __name__=="__main__":unittest.main()
