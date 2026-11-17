#!/usr/bin/env python3
"""Static Android safety contract; no network, file download or hardware execution."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/com/flipperai/satellite"

def source(name):
    return (SRC / name).read_text(encoding="utf-8")

class DownloadSourceTests(unittest.TestCase):
    def test_pin_known_version_and_digest(self):
        catalog = source("VettedModelCatalog.java")
        self.assertIn("e6687177e00897731e7716267eebd4eba0dde7c2",catalog)
        self.assertIn("613722264L",catalog)
        self.assertIn("c5f39b4191926786c3e6668b62af4d6eb15248dfc7a5348252a9b406f33f2108",catalog)
        self.assertIn("https://huggingface.co/",catalog)

    def test_download_fails_closed(self):
        downloader=source("VettedModelDownloader.java")
        integrity=source("ModelDownloadIntegrity.java")
        self.assertIn('!"https".equalsIgnoreCase(url.getProtocol())',downloader)
        self.assertIn("setInstanceFollowRedirects(false)",downloader)
        self.assertIn("MAX_REDIRECTS=8",downloader)
        self.assertIn("getUsableSpace()",downloader)
        self.assertIn("ATOMIC_MOVE",downloader)
        self.assertIn("if(stage.exists())stage.delete()",downloader)
        self.assertIn("copyChecked(",downloader)
        self.assertIn("MessageDigest.isEqual",integrity)
        self.assertIn("total>expectedBytes-n",integrity)
        self.assertIn("Thread.currentThread().isInterrupted()",integrity)

    def test_explicit_ui_only_and_existing_model_preserved(self):
        models=source("ModelsActivity.java")
        store=source("EmbeddedModelStore.java")
        self.assertIn('"DOWNLOAD & INSTALL QWEN3-0.6B"',models)
        self.assertIn('"CANCEL CURRENT DOWNLOAD"',models)
        self.assertIn("VettedModelDownloader.installQwen3(this",models)
        self.assertIn("LlamaCppSettings.setEnabled(this,false)",models)
        self.assertIn('"USE LLAMA.CPP (TERMUX SERVER)"',models)
        self.assertIn("StandardCopyOption.ATOMIC_MOVE",store)
        self.assertIn("setEnabled(this,false)",models)

    def test_saved_cloud_without_silent_consent(self):
        vault=source("CloudCredentialStore.java")
        chat=source("AiChatActivity.java")
        ble=source("MainActivity.java")
        self.assertIn('"AndroidKeyStore"',vault)
        self.assertIn("AES/GCM/NoPadding",vault)
        self.assertIn("CloudCredentialStore.save(this",chat)
        self.assertIn("CloudCredentialStore.load(this)",chat)
        self.assertIn("CloudCredentialStore.save(this",ble)
        self.assertIn("CloudCredentialStore.clear(this)",ble)
        self.assertIn("private volatile boolean consent=false;",chat)
        self.assertIn("cloudConsent.setChecked(false)",ble)

if __name__ == "__main__":
    unittest.main()
