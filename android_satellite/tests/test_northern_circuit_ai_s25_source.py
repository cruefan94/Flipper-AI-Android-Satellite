#!/usr/bin/env python3
"""Development-only source guards for Qwen3.5 options (not device inference tests)."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/com/flipperai/satellite"
MODELS = (SRC / "ModelsActivity.java").read_text()
LOCAL = (SRC / "LlamaCppProfile.java").read_text()
BACKEND = (SRC / "LocalModelEngine.java").read_text()
PROFILE = (SRC / "NorthernCircuitAiProfile.java").read_text()

class NorthernCircuitS25ModelsTest(unittest.TestCase):
    def test_both_models_have_explicit_sources_and_fallback(self):
        self.assertIn("Qwen3.5-4B", MODELS)
        self.assertIn("Qwen3.5-2B", MODELS)
        self.assertIn("https://huggingface.co/ggml-org/Qwen3.5-4B-GGUF", MODELS)
        self.assertIn("https://huggingface.co/gguf/Qwen3.5-2B-GGUF", MODELS)

    def test_gguf_not_confused_with_embedded_litert(self):
        self.assertIn("they cannot be imported as LiteRT files", MODELS)
        self.assertIn("Nothing downloads until you choose", MODELS)

    def test_existing_local_only_transport_kept(self):
        self.assertIn("http://127.0.0.1:9931/v1/chat/completions", LOCAL)
        self.assertIn("flipper-local", LOCAL)
        self.assertIn("Proxy.NO_PROXY", BACKEND)
        self.assertIn("LocalModelEndpointPolicy.validate(url)", BACKEND)

    def test_general_assistant_cannot_claim_hardware_action(self):
        self.assertIn("Never claim to control hardware", PROFILE)
        self.assertIn("Ask permission before consequential actions", PROFILE)

if __name__ == "__main__":
    unittest.main()
