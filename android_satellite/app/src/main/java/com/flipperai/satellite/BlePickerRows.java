package com.flipperai.satellite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure-Java snapshot for Android's Bluetooth device spinner.
 * Device identity is an internal key, never inferred from a visible name;
 * multiple devices sharing a name remain individually selectable.
 * Keys (Bluetooth addresses) are never shown in the spinner labels.
 */
public final class BlePickerRows {
  public static final String PLACEHOLDER="None — no device selected";
  private final List<String> ids;
  private final List<String> labels;

  private BlePickerRows(List<String> ids,List<String> labels){
    this.ids=Collections.unmodifiableList(ids);
    this.labels=Collections.unmodifiableList(labels);
  }

  public static BlePickerRows empty(){
    return from(new LinkedHashMap<String,String>());
  }

  private static String clean(String raw){
    if(raw==null)return "Unknown Bluetooth device";
    String value=raw.replace('\n',' ').replace('\r',' ').replace('\t',' ').trim();
    if(value.isEmpty())return "Unknown Bluetooth device";
    return value.length()>90?value.substring(0,90):value;
  }

  public static BlePickerRows from(LinkedHashMap<String,String> devices){
    if(devices==null)throw new IllegalArgumentException("Device rows required");
    List<String> ids=new ArrayList<>();
    List<String> visible=new ArrayList<>();
    visible.add(PLACEHOLDER);
    Map<String,Integer> totals=new HashMap<>();
    for(Map.Entry<String,String> e:devices.entrySet()){
      if(e.getKey()==null || e.getKey().isEmpty())continue;
      String title=clean(e.getValue());
      totals.put(title,totals.getOrDefault(title,0)+1);
    }
    Map<String,Integer> seen=new HashMap<>();
    for(Map.Entry<String,String> e:devices.entrySet()){
      if(e.getKey()==null || e.getKey().isEmpty())continue;
      String title=clean(e.getValue());
      int slot=seen.getOrDefault(title,0)+1;
      seen.put(title,slot);
      int total=totals.get(title);
      ids.add(e.getKey());
      visible.add(total>1?title+" • "+slot+"/"+total:title);
    }
    return new BlePickerRows(ids,visible);
  }

  /** Position 0 means no selection; every real row is 1-indexed. */
  public String idAt(int selectedIndex){
    return selectedIndex>=1 && selectedIndex<=ids.size()?ids.get(selectedIndex-1):null;
  }

  /** Restores the same device even if scans insert/reorder other devices. */
  public int positionFor(String id){
    if(id==null)return 0;
    int index=ids.indexOf(id);
    return index<0?0:index+1;
  }

  public List<String> labels(){return labels;}
  public int size(){return ids.size();}
}
