package com.northerncircuitstudios.ai;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.StatFs;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * User-authorized GGUF file import. Files are stored privately and are NOT
 * considered validated/loaded for inference merely because copying succeeded.
 * Never needs MANAGE_EXTERNAL_STORAGE or access to Termux private files.
 */
public final class ImportedGgufStore {
    private static final long MIN_SIZE = 1024L * 1024L;
    private static final long MAX_SIZE = 12L * 1024L * 1024L * 1024L;
    private static final long STORAGE_RESERVE = 128L * 1024L * 1024L;
    public static final int MAX_BATCH = 12;

    public interface Progress {
        void onProgress(String message);
    }

    public static final class Item {
        public final String name;
        public final long bytes;
        Item(String name, long bytes) { this.name = name; this.bytes = bytes; }
    }

    private ImportedGgufStore() {}

    private static File folder(Context context) throws IOException {
        File dir = new File(context.getFilesDir(), "models");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Cannot create private model folder");
        }
        return dir;
    }

    private static String cleanName(String name) throws IOException {
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".gguf")) {
            throw new IOException("Choose a .gguf model file.");
        }
        String safe = name.replaceAll("[^A-Za-z0-9._-]", "_");
        safe = safe.replaceAll("^\\.+", "");
        if (safe.length() < 6 || safe.length() > 140 ||
                !safe.toLowerCase(Locale.ROOT).endsWith(".gguf")) {
            throw new IOException("Invalid GGUF filename");
        }
        return safe;
    }

    private static String sourceName(Context context, Uri uri) {
        try (Cursor c = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return c.getString(index);
            }
        } catch (RuntimeException ignored) { }
        return uri.getLastPathSegment();
    }

    private static long declaredSize(Context context, Uri uri) {
        try (Cursor c = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int index = c.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0 && !c.isNull(index)) return c.getLong(index);
            }
        } catch (RuntimeException ignored) { }
        return -1;
    }

    public static List<Item> list(Context context) throws IOException {
        File[] files = folder(context).listFiles((dir, name) ->
            name.toLowerCase(Locale.ROOT).endsWith(".gguf"));
        if (files == null) throw new IOException("Cannot list private models");
        Arrays.sort(files, Comparator.comparing(File::getName));
        List<Item> result = new ArrayList<>();
        for (File file : files) {
            if (file.isFile()) result.add(new Item(file.getName(), file.length()));
        }
        return result;
    }

    public static Item importOne(Context context, Uri uri, Progress progress)
            throws IOException {
        if (uri == null || !"content".equalsIgnoreCase(uri.getScheme())) {
            throw new IOException("Use Android's document picker for model files.");
        }
        String name = cleanName(sourceName(context, uri));
        File dir = folder(context);
        long expected = declaredSize(context, uri);
        if (expected > MAX_SIZE || (expected >= 0 && expected < MIN_SIZE)) {
            throw new IOException("Unsupported GGUF file size");
        }
        long free = new StatFs(dir.getAbsolutePath()).getAvailableBytes();
        if (free < STORAGE_RESERVE + MIN_SIZE ||
                (expected > 0 && expected > free - STORAGE_RESERVE)) {
            throw new IOException("Not enough free storage to import this model.");
        }
        String stem = name.substring(0, name.length() - 5);
        File destination = new File(dir, name);
        for (int suffix = 2; destination.exists() && suffix <= 100; suffix++) {
            destination = new File(dir, stem + "-" + suffix + ".gguf");
        }
        if (destination.exists()) throw new IOException("Too many copies of this model.");
        File staging = File.createTempFile(".gguf-import-", ".part", dir);
        boolean committed = false;
        long copied = 0;
        try {
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(staging)) {
                if (input == null) throw new IOException("Cannot open the selected model.");
                byte[] chunk = new byte[65536];
                int n;
                long lastReport = 0;
                while ((n = input.read(chunk)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new IOException("Model import was cancelled.");
                    }
                    if (n == 0) continue;
                    if (copied + n > MAX_SIZE || copied + n > free - STORAGE_RESERVE) {
                        throw new IOException("Model exceeds size or available storage.");
                    }
                    output.write(chunk, 0, n);
                    copied += n;
                    if (progress != null && copied - lastReport >= 64L * 1024 * 1024) {
                        lastReport = copied;
                        progress.onProgress("Importing " + name + " • " +
                                copied / 1000000L + " MB copied");
                    }
                }
                output.getFD().sync();
            }
            if (copied < MIN_SIZE || copied != staging.length()) {
                throw new IOException("Model is incomplete or too small.");
            }
            try (FileInputStream header = new FileInputStream(staging)) {
                if (header.read() != 'G' || header.read() != 'G' ||
                    header.read() != 'U' || header.read() != 'F') {
                    throw new IOException("File has no GGUF header; import rejected.");
                }
                int version = header.read() | (header.read() << 8) |
                              (header.read() << 16) | (header.read() << 24);
                if (version < 2 || version > 4) {
                    throw new IOException("Unsupported GGUF header version.");
                }
            }
            if (!staging.renameTo(destination)) {
                throw new IOException("Cannot finalize the imported model.");
            }
            committed = true;
            return new Item(destination.getName(), copied);
        } finally {
            if (!committed && staging.exists()) staging.delete();
        }
    }

    public static boolean delete(Context context, String requestedName) throws IOException {
        String name = cleanName(requestedName);
        File base = folder(context);
        File target = new File(base, name);
        if (!target.getCanonicalPath().startsWith(base.getCanonicalPath() + File.separator)) {
            throw new IOException("Invalid model path");
        }
        return target.isFile() && target.delete();
    }
}
