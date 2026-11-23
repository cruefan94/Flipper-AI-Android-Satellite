#!/usr/bin/env python3
"""Static UI regression checks; device rendering still requires S25 testing."""
from pathlib import Path
import unittest

BASE = Path(__file__).resolve().parents[1]
JAVA = BASE / "src/main/java/com/northerncircuitstudios/ai"
CHAT = (JAVA / "ChatMarkdown.java").read_text()
ACTIVITY = (JAVA / "AssistantActivity.java").read_text()


class ChatPresentationSourceTest(unittest.TestCase):
    def test_text_only_markdown_not_webview(self):
        self.assertIn("SpannableStringBuilder", CHAT)
        self.assertIn("StyleSpan", CHAT)
        self.assertIn("TypefaceSpan", CHAT)
        self.assertIn("normalizeLines", CHAT)
        self.assertIn("• ", CHAT)
        self.assertNotIn("Html.fromHtml", CHAT)
        self.assertNotIn("import android.webkit.WebView", CHAT)

    def test_individual_chat_bubbles(self):
        self.assertIn("chatMessages.removeAllViews()", ACTIVITY)
        self.assertIn("messageGroup", ACTIVITY)
        self.assertIn("ChatMarkdown.render(entry.text)", ACTIVITY)
        self.assertIn("message.setTextIsSelectable(true)", ACTIVITY)
        self.assertIn("conversationScroll.fullScroll(View.FOCUS_DOWN)", ACTIVITY)
        self.assertNotIn("transcript.setText(", ACTIVITY)

    def test_mode_aware_privacy_label(self):
        self.assertIn("GEMINI CLOUD AI  •  REQUESTS SENT ONLINE", ACTIVITY)
        self.assertIn("LOCAL FIRST  •  CLOUD FALLBACK ENABLED", ACTIVITY)
        self.assertIn("PRIVATE ON-DEVICE AI  •  GALAXY S25", ACTIVITY)
        self.assertIn("Local server unavailable. Tap LOCAL SETUP", ACTIVITY)


if __name__ == "__main__":
    unittest.main()
