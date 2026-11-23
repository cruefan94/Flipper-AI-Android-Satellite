#!/usr/bin/env python3
"""Memory Bank source/privacy gates. Phone test still mandatory."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
J = ROOT / "src/main/java/com/northerncircuitstudios/ai"
DB = (J/"ConversationMemoryStore.java").read_text()
ACT = (J/"AssistantActivity.java").read_text()
LOCAL = (J/"LocalAiClient.java").read_text()
CLOUD = (J/"CloudAiClient.java").read_text()
CLOUD_STORE = (J/"CloudSettingsStore.java").read_text()
GRADLE = (ROOT/"build.gradle").read_text()
MANIFEST = (ROOT/"src/main/AndroidManifest.xml").read_text()

class ConversationMemoryRegressionTest(unittest.TestCase):
    def test_unique_side_by_side_and_keystore(self):
        self.assertIn("applicationIdSuffix '.m7memory'", GRADLE)
        self.assertIn("'Northern AI Memory Test'", GRADLE)
        for marker in ['SQLiteOpenHelper', 'AndroidKeyStore', 'AES/GCM/NoPadding',
                       'GCMParameterSpec(128, iv)', 'new SecureRandom()',
                       'CREATE TABLE turns', 'CREATE TABLE facts', 'CREATE TABLE meta']:
            self.assertIn(marker, DB)
        self.assertIn('android:allowBackup="false"', MANIFEST)
        for permission in ('READ_EXTERNAL_STORAGE', 'MANAGE_EXTERNAL_STORAGE'):
            self.assertNotIn(permission, MANIFEST)

    def test_history_not_silently_truncated_in_storage(self):
        self.assertIn('memoryStore.migrateLegacy(this)', ACT)
        self.assertIn('List<AssistantPolicy.Entry> previous = memoryStore.recent(30)', ACT)
        self.assertIn('memoryStore.append(new AssistantPolicy.Entry("user", question))', ACT)
        self.assertIn('memoryStore.append(new AssistantPolicy.Entry("assistant", reply))', ACT)
        self.assertIn('public synchronized List<SearchResult> recall(', DB)
        self.assertIn('SELECT _id,ts,role,nonce,body FROM turns ORDER BY _id DESC', DB)
        self.assertNotIn('MAX_STORED_ENTRIES', DB)
        self.assertIn('migrateLegacy', DB)
        self.assertIn('db.beginTransaction()', DB)
        self.assertIn("db.setTransactionSuccessful()", DB)

    def test_actual_recall_used_for_offline_and_cloud_opt_in(self):
        self.assertIn('memoryStore.relevantContext(question, 820)', ACT)
        self.assertIn('client.reply(snapshot, chosenLocalModel, remembered)', ACT)
        self.assertIn('allowedCloudMemory = shareMemory ? remembered : ""', ACT)
        self.assertIn('cloudClient.reply(question, token, allowedCloudMemory)', ACT)
        self.assertIn('public String reply(List<AssistantPolicy.Entry> conversation, String chosenModel,', LOCAL)
        self.assertIn('memoryContext.substring(0, Math.min(950, memoryContext.length()))', LOCAL)
        self.assertIn('composeCloudPrompt(prompt, consentedMemory)', CLOUD)

    def test_cloud_sharing_is_separate_and_encrypted_default_off(self):
        self.assertIn('public final boolean shareMemoryWithCloud;', CLOUD_STORE)
        self.assertIn('this(mode, token, false);', CLOUD_STORE)
        self.assertIn('data.optBoolean("shareMemoryWithCloud", false)', CLOUD_STORE)
        self.assertIn('"shareMemoryWithCloud", settings.shareMemoryWithCloud', CLOUD_STORE)
        self.assertIn('"I AGREE TO SHARE"', ACT)
        self.assertIn('"KEEP PRIVATE"', ACT)
        self.assertIn('CloudSettingsStore.save(this, updated)', ACT)
        self.assertIn('if (!enabled)', ACT)
        self.assertIn('cloudSettings = updated;', ACT)
        self.assertIn('if (mode == CloudModePolicy.Mode.CLOUD)', ACT)
        self.assertNotIn('GEMINI_API_KEY', ACT)
        self.assertNotIn('HttpsURLConnection', DB)
        self.assertNotIn('https://', DB)

    def test_explicit_memory_edit_forget_search_and_delete(self):
        for marker in ('"Remember that "', 'memoryStore.remember(fact)',
                       '"MEMORY BANK"', '"SEARCH ALL CONVERSATIONS"',
                       '"VIEW / EDIT / FORGET SAVED FACTS"', 'memoryStore.editFact',
                       'memoryStore.forget(', 'memoryStore.clearFacts()',
                       'memoryStore.clearArchive()', 'EncryptedChatStore.clear(this)',
                       'memoryStore.recall(query, 5)', '"Delete ALL encrypted conversations?"'):
            self.assertIn(marker, ACT)
        self.assertIn('public synchronized void editFact', DB)
        self.assertIn('public synchronized boolean forget', DB)

if __name__ == "__main__":
    unittest.main()
