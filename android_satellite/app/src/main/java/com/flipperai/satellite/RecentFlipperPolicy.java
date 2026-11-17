package com.flipperai.satellite;

import java.util.Locale;

/** Pure-Java validation and display policy for manually confirmed recent devices. */
public final class RecentFlipperPolicy {
  private RecentFlipperPolicy() {}
  public static final int MAX_RECENTS=3;
  public static boolean validAddress(String address) {
    return address!=null && address.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}");
  }
  public static String canonicalAddress(String address) {
    if(!validAddress(address))throw new IllegalArgumentException("Invalid BLE address");
    return address.toUpperCase(Locale.ROOT);
  }
  public static String displayName(String name) {
    if(name==null)return "Paired Flipper";
    StringBuilder clean=new StringBuilder();
    for(int i=0;i<name.length() && clean.length()<32;i++){
      char c=name.charAt(i);
      if(c>=32 && c!=127 && c!='|' && c!='\\' && c!='\n' && c!='\r')
        clean.append(c);
    }
    String label=clean.toString().trim();
    return label.isEmpty()?"Paired Flipper":label;
  }
}
