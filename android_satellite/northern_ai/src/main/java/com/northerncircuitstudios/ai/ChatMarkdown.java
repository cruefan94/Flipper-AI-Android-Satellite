package com.northerncircuitstudios.ai;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small, dependency-free, text-only markdown formatter.
 * Content remains literal text with spans; no HTML, WebView or script rendering.
 * Supports bold, italic, inline code, basic headings and unordered list bullets.
 */
public final class ChatMarkdown {
    private static final Pattern BOLD = Pattern.compile("\\*\\*([^*\\n]+)\\*\\*");
    private static final Pattern CODE = Pattern.compile("\x60([^\x60\\n]+)\x60");
    private static final Pattern ITALIC =
        Pattern.compile("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)");

    private ChatMarkdown() {}

    private static String normalizeLines(String input) {
        String[] lines = input.split("\\n", -1);
        StringBuilder output = new StringBuilder();
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            if (line.matches("^\\s{0,3}[-*]\\s+.*")) {
                line = line.replaceFirst("^\\s{0,3}[-*]\\s+", "• ");
            } else if (line.matches("^\\s{0,3}#{1,6}\\s+.*")) {
                line = "**" + line.replaceFirst("^\\s{0,3}#{1,6}\\s+", "") + "**";
            }
            if (index > 0) output.append('\n');
            output.append(line);
        }
        return output.toString();
    }

    private static void apply(SpannableStringBuilder output, Pattern pattern,
            int format, boolean monospace) {
        Matcher matcher = pattern.matcher(output.toString());
        // End-to-start replacements preserve match indices.
        while (matcher.find()) {
            String inner = matcher.group(1);
            int start = matcher.start();
            int end = matcher.end();
            output.replace(start, end, inner);
            if (monospace) {
                output.setSpan(new TypefaceSpan("monospace"), start, start + inner.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                output.setSpan(new StyleSpan(format), start, start + inner.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            matcher = pattern.matcher(output.toString());
        }
    }

    public static CharSequence render(String input) {
        if (input == null || input.isEmpty()) return "";
        SpannableStringBuilder text = new SpannableStringBuilder(normalizeLines(input));
        apply(text, BOLD, Typeface.BOLD, false);
        apply(text, CODE, Typeface.NORMAL, true);
        apply(text, ITALIC, Typeface.ITALIC, false);
        return text;
    }
}
