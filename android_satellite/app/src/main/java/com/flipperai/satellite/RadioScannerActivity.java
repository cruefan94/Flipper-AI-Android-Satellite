package com.flipperai.satellite;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Dedicated receive-only radio UI, in the existing Satellite orange/dark style.
 * Streams are played only when the user explicitly selects PLAY. There is no
 * microphone capture, radio transmission, SDR receiver or Flipper radio control.
 */
public final class RadioScannerActivity extends Activity {
  private static final int MAX_SAVED=100;
  private static final String PREFS="radio_favorites_v1";
  private static final String[] CATEGORIES={
    "Broadcast (AM/FM)", "Talk / News", "Weather", "Amateur radio", "CB public feed",
    "Public safety feed", "Other"
  };
  private static final class Station {
    final String name,url,category;
    Station(String name,String url,String category){
      this.name=name;this.url=url;this.category=category;
    }
    @Override public String toString(){return name+" • "+category;}
  }
  private TextView status,description;
  private EditText searchName,searchCountry,favoriteName,streamUrl;
  private Spinner searchResults,favoriteResults,favoriteCategory;
  private final ArrayList<RadioStationDirectory.Entry> discovered=new ArrayList<>();
  private final ArrayList<Station> favorites=new ArrayList<>();
  private BroadcastReceiver receiver;
  private boolean receiverRegistered=false;
  private int searchGeneration=0;

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    setVolumeControlStream(AudioManager.STREAM_MUSIC);
    LinearLayout page=SatelliteUi.scrollPage(this,"RADIO LISTENING",
      "Phone-first internet radio. Streams must be publicly accessible and lawful. "+
      "No radio transmitter, microphone recording, or Flipper hardware receiver is used.");

    status=SatelliteUi.text(this,"Radio player: stopped",16,SatelliteUi.ORANGE,true);
    status.setTextIsSelectable(true);
    page.addView(status);
    description=SatelliteUi.text(this,"Play a stream to see its status.",14,SatelliteUi.WHITE,false);
    page.addView(description);
    heading(page,"PLAYBACK");
    LinearLayout playback=new LinearLayout(this);
    playback.setOrientation(LinearLayout.HORIZONTAL);
    page.addView(playback);
    mediaButton(playback,"PAUSE RADIO",()->command("pause",null,null));
    mediaButton(playback,"RESUME RADIO",()->command("resume",null,null));
    mediaButton(playback,"STOP RADIO",()->command("stop",null,null));
    SatelliteUi.action(this,page,"PHONE MEDIA VOLUME",()->{
      AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE);
      if(audio!=null)audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
        AudioManager.ADJUST_SAME,AudioManager.FLAG_SHOW_UI);
    });

    SatelliteUi.gap(this,page,12);
    heading(page,"FIND ONLINE RADIO STATIONS");
    body(page,"Search public Radio Browser station listings. Results may be outdated, inaccessible, or unsupported by Android playback.");
    searchName=field(page,"Station or category name (optional)");
    searchCountry=field(page,"Country (optional; e.g. Canada)");
    SatelliteUi.action(this,page,"SEARCH DIRECTORY",this::searchStations);
    SatelliteUi.action(this,page,"BROWSE CANADIAN BROADCASTS",()->{
      searchCountry.setText("Canada");
      searchName.setText("");
      searchStations();
    });
    searchResults=spinner(page,new String[]{"Search to find online stations"});
    SatelliteUi.action(this,page,"PLAY SELECTED ONLINE STATION",this::playDiscovered);
    SatelliteUi.action(this,page,"SAVE SELECTED STATION",this::saveDiscovered);

    SatelliteUi.gap(this,page,10);
    heading(page,"YOUR SAVED STATIONS");
    body(page,"Favorites are stored only on this phone. You can add an HTTPS direct MP3/AAC stream provided by a lawful broadcaster or feed operator.");
    favoriteResults=spinner(page,new String[]{"No favorites yet"});
    SatelliteUi.action(this,page,"PLAY SELECTED FAVORITE",this::playFavorite);
    SatelliteUi.action(this,page,"REMOVE SELECTED FAVORITE",this::removeFavorite);
    favoriteName=field(page,"Optional station/favorite name");
    streamUrl=field(page,"HTTPS direct radio stream URL");
    favoriteCategory=spinner(page,CATEGORIES);
    SatelliteUi.action(this,page,"SAVE ENTERED STREAM",this::saveEntered);

    SatelliteUi.gap(this,page,8);
    body(page,"AM/FM here means internet streams, not direct AM/FM radio reception. "+
      "CB, fire and police are only playable if an authorized public internet feed is available. "+
      "Encrypted or restricted channels cannot be accessed. SDR and Flipper radio attachments are not implemented.");
    SatelliteUi.backHome(this,page);

    loadFavorites();
    refreshFavorites();
    receiver=new BroadcastReceiver(){
      @Override public void onReceive(Context context,Intent intent) {
        if(!RadioPlayerService.UPDATE.equals(intent.getAction()))return;
        String next=intent.getStringExtra(RadioPlayerService.STATUS);
        String label=intent.getStringExtra(RadioPlayerService.NAME);
        if(next!=null)status.setText("Radio player: "+next);
        if(label!=null)description.setText("Station: "+label);
      }
    };
  }

  private void heading(LinearLayout page,String title){
    SatelliteUi.sectionTitle(this,page,title);
  }
  private void mediaButton(LinearLayout row,String label,Runnable action){
    TextView control=SatelliteUi.text(this,label.replace(" RADIO",""),13,SatelliteUi.WHITE,true);
    control.setGravity(android.view.Gravity.CENTER);
    control.setBackground(SatelliteUi.panel(this,false));
    control.setMinHeight(SatelliteUi.dp(this,52));
    control.setFocusable(true);
    control.setClickable(true);
    control.setContentDescription(label);
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
    p.setMargins(SatelliteUi.dp(this,2),0,SatelliteUi.dp(this,2),SatelliteUi.dp(this,6));
    row.addView(control,p);
    control.setOnClickListener(v->action.run());
  }
  private void body(LinearLayout page,String text) {
    TextView v=SatelliteUi.text(this,text,13,SatelliteUi.MUTED,false);
    v.setPadding(0,SatelliteUi.dp(this,5),0,SatelliteUi.dp(this,12));
    page.addView(v);
  }
  private EditText field(LinearLayout page,String hint) {
    EditText e=new EditText(this);
    e.setSingleLine(true);
    e.setTextColor(SatelliteUi.WHITE);
    e.setHintTextColor(SatelliteUi.MUTED);
    e.setHint(hint);
    e.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
    e.setFilters(new android.text.InputFilter[]{
      new android.text.InputFilter.LengthFilter(2048)});
    page.addView(e);
    return e;
  }
  private Spinner spinner(LinearLayout page,String[] options){
    Spinner v=new Spinner(this);
    v.setAdapter(new ArrayAdapter<String>(this,
      android.R.layout.simple_spinner_dropdown_item,options));
    page.addView(v);
    return v;
  }
  private void searchStations() {
    String name=searchName.getText().toString().trim();
    String country=searchCountry.getText().toString().trim();
    if(name.isEmpty()&&country.isEmpty()){
      status.setText("Enter a station name or country before searching.");
      return;
    }
    final int requested=++searchGeneration;
    status.setText("Searching public station directory...");
    RadioStationDirectory.search(name,country,"",(items,error)->runOnUiThread(()->{
      if(isFinishing()||isDestroyed()||requested!=searchGeneration)return;
      discovered.clear();
      discovered.addAll(items);
      ArrayList<String> names=new ArrayList<>();
      for(RadioStationDirectory.Entry item:items)
        names.add(item.name+" — "+item.country+" ("+item.codec+")");
      if(names.isEmpty())names.add("No compatible HTTPS stations found");
      searchResults.setAdapter(new ArrayAdapter<String>(this,
        android.R.layout.simple_spinner_dropdown_item,names));
      status.setText(error==null
        ?items.size()+" online listings found; playback not yet verified":error);
    }));
  }
  private RadioStationDirectory.Entry selectedDirectoryEntry(){
    int index=searchResults.getSelectedItemPosition();
    return index>=0 && index<discovered.size()?discovered.get(index):null;
  }
  private void playDiscovered() {
    RadioStationDirectory.Entry entry=selectedDirectoryEntry();
    if(entry==null){status.setText("Search and select an online station first.");return;}
    command("play",entry.url,entry.name);
  }
  private void saveDiscovered(){
    RadioStationDirectory.Entry entry=selectedDirectoryEntry();
    if(entry==null){status.setText("Search and select an online station first.");return;}
    addFavorite(new Station(entry.name,entry.url,"Broadcast (AM/FM)"));
  }
  private void playFavorite() {
    int index=favoriteResults.getSelectedItemPosition();
    if(index<0||index>=favorites.size()){
      status.setText("Choose a saved station first.");return;
    }
    Station favorite=favorites.get(index);
    command("play",favorite.url,favorite.name);
  }
  private void removeFavorite() {
    int index=favoriteResults.getSelectedItemPosition();
    if(index<0||index>=favorites.size()){
      status.setText("No saved station selected.");return;
    }
    favorites.remove(index);
    persistFavorites();
    refreshFavorites();
    status.setText("Station removed from phone favorites.");
  }
  private void saveEntered(){
    String url=streamUrl.getText().toString().trim();
    if(!RadioStreamPolicy.isAllowed(url)){
      status.setText("Enter a publicly named HTTPS stream URL; private/local URLs aren't accepted.");
      return;
    }
    String name=RadioStreamPolicy.safeLabel(favoriteName.getText().toString());
    addFavorite(new Station(name,url,CATEGORIES[favoriteCategory.getSelectedItemPosition()]));
  }
  private void addFavorite(Station next) {
    if(!RadioStreamPolicy.isAllowed(next.url)){
      status.setText("Invalid HTTPS station URL.");return;
    }
    for(int i=favorites.size()-1;i>=0;i--)
      if(favorites.get(i).url.equals(next.url))favorites.remove(i);
    if(favorites.size()>=MAX_SAVED)favorites.remove(0);
    favorites.add(next);
    persistFavorites();
    refreshFavorites();
    status.setText("Saved locally: "+next.name+". Stream playback is not yet verified.");
  }
  private void loadFavorites() {
    try{
      JSONArray items=new JSONArray(getSharedPreferences(PREFS,MODE_PRIVATE)
        .getString("stations","[]"));
      for(int i=0;i<items.length() && favorites.size()<MAX_SAVED;i++) {
        JSONObject v=items.optJSONObject(i);
        if(v==null)continue;
        String url=v.optString("url","");
        if(RadioStreamPolicy.isAllowed(url))
          favorites.add(new Station(RadioStreamPolicy.safeLabel(v.optString("name")),
            url,v.optString("category","Other")));
      }
    }catch(Exception ignored){
      status.setText("Saved stations unavailable; you can add new ones.");
    }
  }
  private void persistFavorites(){
    JSONArray items=new JSONArray();
    for(Station station:favorites){
      JSONObject value=new JSONObject();
      try{
        value.put("name",station.name);
        value.put("url",station.url);
        value.put("category",station.category);
        items.put(value);
      }catch(Exception ignored){}
    }
    getSharedPreferences(PREFS,MODE_PRIVATE).edit()
      .putString("stations",items.toString()).apply();
  }
  private void refreshFavorites(){
    ArrayList<String> names=new ArrayList<>();
    for(Station v:favorites)names.add(v.toString());
    if(names.isEmpty())names.add("No saved stations");
    favoriteResults.setAdapter(new ArrayAdapter<String>(this,
      android.R.layout.simple_spinner_dropdown_item,names));
  }
  private void command(String action,String url,String name) {
    if("play".equals(action) && !RadioStreamPolicy.isAllowed(url)){
      status.setText("Invalid or unsafe HTTPS stream URL.");return;
    }
    Intent intent=new Intent(this,RadioPlayerService.class)
      .setAction(RadioPlayerService.ACTION)
      .putExtra(RadioPlayerService.CMD,action);
    if(url!=null)intent.putExtra(RadioPlayerService.URL,url);
    if(name!=null)intent.putExtra(RadioPlayerService.NAME,name);
    try{
      if("play".equals(action)){
        startForegroundService(intent);
        status.setText("Connecting to "+RadioStreamPolicy.safeLabel(name)+"...");
      }else startService(intent);
    }catch(Exception error){
      status.setText("Radio playback unavailable: "+error.getClass().getSimpleName());
    }
  }
  @Override protected void onStart(){
    super.onStart();
    IntentFilter filter=new IntentFilter(RadioPlayerService.UPDATE);
    if(Build.VERSION.SDK_INT>=33)
      registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);
    else registerReceiver(receiver,filter);
    receiverRegistered=true;
    command("status",null,null);
  }
  @Override protected void onStop(){
    searchGeneration++;
    if(receiverRegistered){unregisterReceiver(receiver);receiverRegistered=false;}
    super.onStop();
  }
}
