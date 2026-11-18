package com.flipperai.satellite;

import java.net.URI;
import java.util.Locale;

/**
 * Receive-only online radio URL guard. Never accepts cleartext HTTP, local
 * schemes, user info or directly-addressed private/loopback network targets.
 * The app never uploads audio and never transmits radio signals.
 */
public final class RadioStreamPolicy {
  private RadioStreamPolicy() {}

  public static boolean isAllowed(String value) {
    if(value==null || value.length()>2048)return false;
    try {
      URI uri=new URI(value.trim());
      String host=uri.getHost();
      if(!"https".equalsIgnoreCase(uri.getScheme()) || host==null
          || uri.getRawUserInfo()!=null || uri.getRawFragment()!=null)return false;
      if(uri.getPort()==0 || uri.getPort()>65535)return false;
      String h=host.toLowerCase(Locale.ROOT);
      if(h.equals("localhost") || h.endsWith(".localhost") || h.endsWith(".local")
          || h.endsWith(".internal") || h.endsWith(".invalid") || h.endsWith(".test")
          || h.endsWith(".example"))return false;
      // Literal IPv4 and IPv6 hosts are blocked: saved station URLs should
      // resolve through a publicly named streaming provider instead.
      if(h.matches("[0-9.]+") || h.startsWith("[") || h.indexOf(':')>=0)return false;
      if(h.indexOf('.')<0 || h.startsWith(".") || h.endsWith("."))return false;
      return true;
    }catch(Exception ignored){return false;}
  }

  public static String safeLabel(String label) {
    if(label==null)return "Radio stream";
    String text=label.replace('\n',' ').replace('\r',' ').trim();
    if(text.isEmpty())return "Radio stream";
    return text.length()>100?text.substring(0,100):text;
  }
}
