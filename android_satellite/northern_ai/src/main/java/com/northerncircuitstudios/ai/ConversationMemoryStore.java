package com.northerncircuitstudios.ai;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Encrypted, persistent conversation memory on the S25. Unlimited records,
 * bounded per-message size and bounded retrieval/context to protect inference.
 * Each message/fact is AES-256-GCM encrypted in a private SQLite database;
 * encryption key lives in Android Keystore. No cloud I/O in this class.
 */
public final class ConversationMemoryStore extends SQLiteOpenHelper {
    private static final String DB = "northern_ai_conversation_memory_v1.db";
    private static final String KEY_ALIAS = "northern_ai_memory_archive_aes_v1";
    private static final int V = 1;
    private static final int MAX_FACT = 1200;
    private static final int MAX_RECALL_TEXT = 1200;
    private SecretKey cachedKey;

    public static final class MemoryFact {
        public final long id;
        public final String text;
        MemoryFact(long id, String text) { this.id=id; this.text=text; }
    }
    public static final class SearchResult {
        public final long id;
        public final String role;
        public final String text;
        public final long timestamp;
        public final int rank;
        SearchResult(long id, String role, String text, long timestamp, int rank) {
            this.id=id; this.role=role; this.text=text; this.timestamp=timestamp; this.rank=rank;
        }
    }

    public ConversationMemoryStore(Context context) {
        super(context.getApplicationContext(), DB, null, V);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE turns (_id INTEGER PRIMARY KEY AUTOINCREMENT," +
            " ts INTEGER NOT NULL, role TEXT NOT NULL, nonce BLOB NOT NULL, body BLOB NOT NULL)");
        db.execSQL("CREATE TABLE facts (_id INTEGER PRIMARY KEY AUTOINCREMENT," +
            " ts INTEGER NOT NULL, nonce BLOB NOT NULL, body BLOB NOT NULL)");
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int previous, int next) {
        throw new IllegalStateException("Unsupported conversation archive upgrade");
    }

    private synchronized SecretKey key() throws Exception {
        if (cachedKey != null) return cachedKey;
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        KeyStore.Entry entry = store.getEntry(KEY_ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry) {
            cachedKey = ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            return cachedKey;
        }
        KeyGenerator generator = KeyGenerator.getInstance("AES", "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true).build());
        cachedKey = generator.generateKey();
        return cachedKey;
    }

    private ContentValues sealed(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(), new SecureRandom());
        ContentValues result = new ContentValues();
        result.put("nonce", cipher.getIV());
        result.put("body", cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        return result;
    }

    private String unseal(Cursor cursor, int ivColumn, int bodyColumn) throws Exception {
        byte[] iv = cursor.getBlob(ivColumn);
        byte[] body = cursor.getBlob(bodyColumn);
        if (iv == null || iv.length != 12 || body == null || body.length > 50000)
            throw new IllegalStateException("Corrupted encrypted memory record");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(body), StandardCharsets.UTF_8);
    }

    private static void requireThread() {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
            throw new IllegalStateException("Memory database must run on a background thread");
    }

    private void insertTurn(SQLiteDatabase db, AssistantPolicy.Entry entry) throws Exception {
        if (!"user".equals(entry.role) && !"assistant".equals(entry.role))
            throw new IllegalArgumentException("Unsupported conversation role");
        ContentValues values = sealed(entry.text);
        values.put("ts", System.currentTimeMillis());
        values.put("role", entry.role);
        if (db.insertOrThrow("turns", null, values) < 1)
            throw new IllegalStateException("Failed to save conversation");
    }

    /** Old M1-M6 chat is imported once, transactionally, preserving its original storage. */
    public synchronized void migrateLegacy(Context context) throws Exception {
        requireThread();
        SQLiteDatabase db = getWritableDatabase();
        try (Cursor marker = db.rawQuery(
            "SELECT value FROM meta WHERE key='legacy_migrated' LIMIT 1", null)) {
            if (marker.moveToFirst()) return;
        }
        List<AssistantPolicy.Entry> legacy = EncryptedChatStore.load(context);
        db.beginTransaction();
        try {
            for (AssistantPolicy.Entry entry : legacy) insertTurn(db, entry);
            ContentValues flag = new ContentValues();
            flag.put("key", "legacy_migrated");
            flag.put("value", "yes");
            db.insertWithOnConflict("meta", null, flag, SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public synchronized void append(AssistantPolicy.Entry entry) throws Exception {
        requireThread();
        insertTurn(getWritableDatabase(), entry);
    }

    public synchronized List<AssistantPolicy.Entry> recent(int maxCount) throws Exception {
        requireThread();
        int count = Math.max(1, Math.min(30, maxCount));
        ArrayList<AssistantPolicy.Entry> out = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT role,nonce,body FROM turns ORDER BY _id DESC LIMIT " + count, null)) {
            while (cursor.moveToNext())
                out.add(new AssistantPolicy.Entry(cursor.getString(0), unseal(cursor,1,2)));
        }
        Collections.reverse(out);
        return out;
    }

    public synchronized long countTurns() {
        requireThread();
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM turns", null)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0;
        }
    }

    public synchronized MemoryFact remember(String fact) throws Exception {
        requireThread();
        String text = fact == null ? "" : fact.trim();
        if (text.isEmpty() || text.length() > MAX_FACT)
            throw new IllegalArgumentException("Memory must contain 1–1200 characters.");
        ContentValues values = sealed(text);
        values.put("ts", System.currentTimeMillis());
        long id = getWritableDatabase().insertOrThrow("facts", null, values);
        return new MemoryFact(id, text);
    }

    public synchronized List<MemoryFact> facts() throws Exception {
        requireThread();
        ArrayList<MemoryFact> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
            "SELECT _id,nonce,body FROM facts ORDER BY _id DESC", null)) {
            while (c.moveToNext()) out.add(new MemoryFact(c.getLong(0),unseal(c,1,2)));
        }
        return out;
    }

    public synchronized void editFact(long id, String value) throws Exception {
        requireThread();
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || text.length() > MAX_FACT)
            throw new IllegalArgumentException("Memory must contain 1–1200 characters.");
        ContentValues encrypted = sealed(text);
        if (getWritableDatabase().update("facts", encrypted, "_id=?",
            new String[]{Long.toString(id)}) != 1)
            throw new IllegalStateException("Memory entry no longer exists.");
    }

    public synchronized boolean forget(long id) {
        requireThread();
        return getWritableDatabase().delete("facts","_id=?",
            new String[]{Long.toString(id)}) == 1;
    }

    public synchronized void clearArchive() {
        requireThread();
        // Legacy source must also be erased or the database would reimport on reset.
        SQLiteDatabase db = getWritableDatabase();
        db.delete("turns",null,null);
    }

    public synchronized void clearFacts() {
        requireThread();
        getWritableDatabase().delete("facts",null,null);
    }

    private static Set<String> keywords(String text) {
        String[] parts = text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        Set<String> terms = new HashSet<>();
        String stop = " the and for with from this that your what when where which about " +
            " tell said have does are was were who how you our did can i me my it of " +
            " they there want know more some make show remember discussed conversation ";
        for (String part : parts) {
            if (part.length() >= 3 && !stop.contains(" "+part+" ")) terms.add(part);
        }
        return terms;
    }

    /**
     * Search every encrypted archived turn, on a worker thread, while returning a
     * tiny context. If there is no matching text, older memories are not invented.
     */
    public synchronized List<SearchResult> recall(String question, int maxHits) throws Exception {
        requireThread();
        int limit = Math.max(1, Math.min(5, maxHits));
        Set<String> terms = keywords(question);
        ArrayList<SearchResult> candidates = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
            "SELECT _id,ts,role,nonce,body FROM turns ORDER BY _id DESC", null)) {
            while (c.moveToNext()) {
                if (Thread.currentThread().isInterrupted()) return Collections.emptyList();
                String old = unseal(c,3,4);
                if ("user".equals(c.getString(2)) && old.equals(question)) continue;
                String plain = old.toLowerCase(Locale.ROOT);
                int score = 0;
                for (String term : terms) if (plain.contains(term)) score++;
                if (score == 0) continue;
                candidates.add(new SearchResult(c.getLong(0),c.getString(2),
                    old.length() > MAX_RECALL_TEXT
                        ? old.substring(0,MAX_RECALL_TEXT) : old,
                    c.getLong(1),score));
                // Bound candidate memory footprint, but still inspect every record.
                if (candidates.size() > 40) {
                    candidates.sort(Comparator.comparingInt((SearchResult x)->x.rank)
                        .thenComparingLong(x->x.id).reversed());
                    candidates.subList(30,candidates.size()).clear();
                }
            }
        }
        candidates.sort(Comparator.comparingInt((SearchResult x)->x.rank)
            .thenComparingLong(x->x.id).reversed());
        return new ArrayList<>(candidates.subList(0, Math.min(limit,candidates.size())));
    }

    /** Small factual snippets for inference, never a full-history prompt dump. */
    public synchronized String relevantContext(String question, int maxChars) throws Exception {
        requireThread();
        int budget = Math.max(200,Math.min(1000,maxChars));
        StringBuilder context = new StringBuilder();
        List<MemoryFact> known = facts();
        if (!known.isEmpty()) context.append("User-approved saved facts (check before asserting):\n");
        int factsAdded = 0;
        for (MemoryFact fact : known) {
            if (factsAdded >= 5) break;
            String line = "- " + fact.text + "\n";
            if (context.length()+line.length() > budget) break;
            context.append(line);
            factsAdded++;
        }
        List<SearchResult> matches = recall(question,3);
        int added = 0;
        for (SearchResult hit : matches) {
            String shortText = hit.text.replace('\n',' ');
            String line = (added == 0 ? "Relevant earlier conversation excerpts:\n" : "") +
                (hit.role.equals("user") ? "User: " : "Prior AI: ") +
                shortText.substring(0, Math.min(240,shortText.length())) + "\n";
            if (context.length()+line.length() > budget) break;
            context.append(line);
            added++;
        }
        return context.toString().trim();
    }

    @Override public synchronized void close() {
        super.close();
    }
}
