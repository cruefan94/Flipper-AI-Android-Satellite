package com.flipperai.satellite;

import android.app.Activity;
import android.content.*;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Dedicated phone-first, receive-only radio screen. No feed is represented as verified. */
public final class RadioScannerActivity extends Activity {
  private static final String[] CATEGORIES={"AM broadcast","FM broadcast","CB","Fire","Police","Ham / amateur","Aircraft","Weather"};
  private final ArrayList<Station> stations=new ArrayList<>();
  private final ArrayList<String> recent=new ArrayList<>();
  private Spinner category,stationList,source;
  private EditText name,url,search;
  private TextView status,info,ai;
  private BroadcastReceiver receiver;
  private int selected=-1;
  private static final class Station{
    String name,url,category;
    Station(String n,String u,String c){name=n;url=u;category=c;}
  }
  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    setVolumeControlStream(AudioManager.STREAM_MUSIC);
    ScrollView scroll=new ScrollView(this);
    LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(22,22,22,22);scroll.addView(root);setContentView(scroll);
    title(root,"RADIO SCANNER — PHONE FIRST");
    label(root,"Receive-only internet streams. No preloaded feeds are claimed available. Add an HTTPS direct audio URL from a provider that permits listening.");
    source=spinner(root,new String[]{"ONLINE STREAMS","USB SDR (future)","FLIPPER ATTACHMENT (future)"});
    source.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
      public void onNothingSelected(android.widget.AdapterView<?> p){}
      public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
        if(pos!=0){status.setText("Receiver unavailable: "+source.getSelectedItem()+". Online playback remains available.");}
      }
    });
    status=label(root,"Radio: stopped");
    info=label(root,"Station: none");
    category=spinner(root,CATEGORIES);
    search=edit(root,"Search saved stations by name, category or location keyword");
    button(root,"SEARCH SAVED STATIONS",()->refresh());
    stationList=spinner(root,new String[]{"No saved stations"});
    stationList.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
      public void onNothingSelected(android.widget.AdapterView<?> p){}
      public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
        if(pos>=0 && pos<visible.size()){
          selected=stations.indexOf(visible.get(pos));Station s=stations.get(selected);
          name.setText(s.name);url.setText(s.url);info.setText("Selected: "+s.name+" — "+s.category);
        }
      }
    });
    name=edit(root,"Station name (and optional location)");
    url=edit(root,"HTTPS direct MP3/AAC stream URL");
    button(root,"SAVE / FAVORITE STATION",()->saveStation());
    button(root,"REMOVE SELECTED FAVORITE",()->removeStation());
    button(root,"PLAY SELECTED OR ENTERED STREAM",()->play());
    button(root,"PAUSE",()->send("pause",null,null));
    button(root,"RESUME",()->send("resume",null,null));
    button(root,"STOP",()->send("stop",null,null));
    button(root,"ANDROID MEDIA VOLUME",()->{
      AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE);
      if(audio!=null)audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_SAME,AudioManager.FLAG_SHOW_UI);
    });
    button(root,"RECENTLY PLAYED",()->status.setText(recent.isEmpty()?"No recent stations":android.text.TextUtils.join(" | ",recent)));
    title(root,"AI RADIO INTELLIGENCE");
    ai=label(root,"Transcription and AI summaries are not enabled. No audio is recorded or uploaded. Actual audio capture and user consent must be implemented first.");
    button(root,"CHECK AI RADIO FEATURES",()->ai.setText("Unavailable: no authorized audio-capture/transcription pipeline or configured radio AI model. Playback is unaffected."));
    title(root,"FLIPPER BLE CONTROL");
    label(root,"Not connected to an authenticated radio-control protocol. Radio playback works independently of Flipper. No signal-strength reading without an SDR receiver.");
    button(root,"BACK TO SATELLITE",()->finish());
    load();refresh();
    receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent intent){
      String state=intent.getStringExtra(RadioPlayerService.STATUS);
      String station=intent.getStringExtra(RadioPlayerService.NAME);
      if(state!=null)status.setText("Radio: "+state);
      if(station!=null)info.setText("Now playing: "+station);
    }};
    registerReceiver(receiver,new IntentFilter(RadioPlayerService.UPDATE),Context.RECEIVER_NOT_EXPORTED);
    send("status",null,null);
  }
  private final ArrayList<Station> visible=new ArrayList<>();
  private void refresh(){
    visible.clear();String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);
    ArrayList<String> labels=new ArrayList<>();
    for(Station s:stations){
      if(q.isEmpty() || (s.name+" "+s.category).toLowerCase(Locale.ROOT).contains(q)){
        visible.add(s);labels.add(s.name+" ["+s.category+"]");
      }
    }
    if(labels.isEmpty())labels.add("No matching saved stations");
    stationList.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels));
  }
  private void saveStation(){
    String n=name.getText().toString().trim(),u=url.getText().toString().trim();
    if(n.isEmpty() || !valid(u)){status.setText("Enter a station name and a valid HTTPS direct audio URL.");return;}
    for(int i=stations.size()-1;i>=0;i--)if(stations.get(i).url.equals(u))stations.remove(i);
    stations.add(new Station(n,u,CATEGORIES[category.getSelectedItemPosition()]));
    persist();refresh();status.setText("Favorite saved. Stream availability not yet verified.");
  }
  private void removeStation(){
    if(selected>=0 && selected<stations.size()){stations.remove(selected);selected=-1;persist();refresh();status.setText("Favorite removed.");}
    else status.setText("Select a saved station first.");
  }
  private boolean valid(String s){
    try{java.net.URI u=new java.net.URI(s);return "https".equalsIgnoreCase(u.getScheme()) && u.getHost()!=null && u.getUserInfo()==null;}
    catch(Exception e){return false;}
  }
  private void play(){
    if(source.getSelectedItemPosition()!=0){status.setText("Select ONLINE STREAMS; hardware receiver not implemented.");return;}
    String u=url.getText().toString().trim(),n=name.getText().toString().trim();
    if(!valid(u)){status.setText("Invalid HTTPS stream URL. MP3/AAC direct streams are supported where Android can decode them.");return;}
    if(n.isEmpty())n="Radio stream";
    recent.remove(n);recent.add(0,n);if(recent.size()>15)recent.remove(recent.size()-1);
    persist();send("play",u,n);status.setText("Connecting to "+n+"...");
  }
  private void send(String cmd,String u,String n){
    Intent intent=new Intent(this,RadioPlayerService.class).setAction(RadioPlayerService.ACTION).putExtra(RadioPlayerService.CMD,cmd);
    if(u!=null)intent.putExtra(RadioPlayerService.URL,u);
    if(n!=null)intent.putExtra(RadioPlayerService.NAME,n);
    try{if("play".equals(cmd))startForegroundService(intent);else startService(intent);}
    catch(Exception e){status.setText("Radio service unavailable: "+e.getClass().getSimpleName());}
  }
  private void load(){
    android.content.SharedPreferences p=getSharedPreferences("radio_favorites_v1",MODE_PRIVATE);
    try{
      JSONArray a=new JSONArray(p.getString("stations","[]"));
      for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);String u=o.optString("url");if(valid(u))stations.add(new Station(o.optString("name"),u,o.optString("category")));}
      JSONArray r=new JSONArray(p.getString("recent","[]"));for(int i=0;i<r.length();i++)recent.add(r.optString(i));
    }catch(JSONException ignored){status.setText("Saved radio stations could not be read.");}
  }
  private void persist(){
    JSONArray a=new JSONArray(),r=new JSONArray();
    for(Station s:stations){JSONObject o=new JSONObject();try{o.put("name",s.name);o.put("url",s.url);o.put("category",s.category);}catch(JSONException ignored){}a.put(o);}
    for(String s:recent)r.put(s);
    getSharedPreferences("radio_favorites_v1",MODE_PRIVATE).edit().putString("stations",a.toString()).putString("recent",r.toString()).apply();
  }
  private TextView label(LinearLayout root,String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(15);t.setPadding(0,8,0,8);root.addView(t);return t;}
  private void title(LinearLayout root,String s){TextView t=label(root,s);t.setTextSize(20);}
  private EditText edit(LinearLayout root,String hint){EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);root.addView(e);return e;}
  private Spinner spinner(LinearLayout root,String[] options){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,options));root.addView(s);return s;}
  private void button(LinearLayout root,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setOnClickListener(v->action.run());root.addView(b);}
  @Override protected void onDestroy(){if(receiver!=null)unregisterReceiver(receiver);super.onDestroy();}
}
