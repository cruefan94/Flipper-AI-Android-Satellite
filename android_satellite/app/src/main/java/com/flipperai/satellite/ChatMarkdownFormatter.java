package com.flipperai.satellite;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Displays a small, safe subset of Markdown returned by local or online AI.
 * Does NOT parse HTML, load links/images, or execute text from a model.
 * The separate raw conversation remains available through COPY.
 */
public final class ChatMarkdownFormatter {
  private static final Pattern HEADING=Pattern.compile("^\\s{0,3}(#{1,6})\\s+(.+)$");
  private static final Pattern BULLET=Pattern.compile("^\\s*[-*+]\\s+(.+)$");
  private static final Pattern NUMBER=Pattern.compile("^\\s*(\\d{1,3})[.)]\\s+(.+)$");

  private ChatMarkdownFormatter(){}

  public static CharSequence render(String source) {
    if(source==null || source.isEmpty())return "";
    SpannableStringBuilder out=new SpannableStringBuilder();
    boolean code=false;
    boolean started=false;
    for(String raw:source.replace("\r\n","\n").replace('\r','\n').split("\n",-1)) {
      String line=raw;
      if(line.trim().startsWith("\u0060\u0060\u0060")) {
        code=!code;
        continue;
      }
      if(started)out.append('\n');
      started=true;
      int start=out.length();
      if(code) {
        out.append(line);
        if(out.length()>start)
          out.setSpan(new TypefaceSpan("monospace"),start,out.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        continue;
      }
      Matcher heading=HEADING.matcher(line);
      int level=0;
      if(heading.matches()) {
        level=heading.group(1).length();
        line=heading.group(2);
      } else {
        Matcher bullet=BULLET.matcher(line);
        if(bullet.matches())line="•  "+bullet.group(1);
        else {
          Matcher number=NUMBER.matcher(line);
          if(number.matches())line=number.group(1)+".  "+number.group(2);
        }
        if(line.trim().matches("(?:---+|___+|\\*\\*\\*+)"))line="────────";
      }
      appendInline(out,line);
      if(level>0 && out.length()>start) {
        out.setSpan(new StyleSpan(Typeface.BOLD),start,out.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new RelativeSizeSpan(level<=2?1.22f:1.10f),
          start,out.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }
    return out;
  }

  // Apply only local typography: never evaluate or import model-supplied HTML.
  private static void appendInline(SpannableStringBuilder out,String line) {
    int i=0;
    while(i<line.length()) {
      String marker=null;
      int style=Typeface.NORMAL;
      if(line.startsWith("**",i) || line.startsWith("__",i)) {
        marker=line.substring(i,i+2);style=Typeface.BOLD;
      } else if(line.charAt(i)=='*' || line.charAt(i)=='_') {
        marker=line.substring(i,i+1);style=Typeface.ITALIC;
      } else if(line.charAt(i)=='\u0060') {
        marker="\u0060";
      }
      if(marker!=null) {
        int end=line.indexOf(marker,i+marker.length());
        if(end>i+marker.length()) {
          int from=out.length();
          out.append(line.substring(i+marker.length(),end));
          if(style!=Typeface.NORMAL)
            out.setSpan(new StyleSpan(style),from,out.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          else
            out.setSpan(new TypefaceSpan("monospace"),from,out.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          i=end+marker.length();
          continue;
        }
      }
      out.append(line.charAt(i));
      i++;
    }
  }

  public static String plainSpeech(String markdown) {
    // Spoken text uses the same markup-stripping renderer as on-screen text.
    // This stays local and does not alter the original copyable conversation.
    return render(markdown).toString().replace("•  ","");
  }
}
