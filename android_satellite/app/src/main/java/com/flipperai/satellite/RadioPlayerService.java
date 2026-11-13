package com.flipperai.satellite;

import android.app.*;
import android.content.*;
import android.media.*;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.*;
import java.io.IOException;

/** Receive-only HTTPS internet audio. No microphone, recording, or radio transmitter. */
public final class RadioPlayerService extends Service {
  public static final String ACTION="com.flipperai.satellite.RADIO_CONTROL";
  public static final String UPDATE="com.flipperai.satellite.RADIO_STATUS";
  public static final String CMD="cmd", URL="url", NAME="name", STATUS="status";
  private static final String CHANNEL="radio_playback";
  private static final int NOTIFICATION=609;
  private MediaPlayer player;
  private MediaSession session;
  private String station="Radio",state="Stopped",stream="";
  private boolean foreground=false;
  @Override public void onCreate(){
    super.onCreate();
    NotificationChannel channel=new NotificationChannel(CHANNEL,"Radio playback",NotificationManager.IMPORTANCE_LOW);
    ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    session=new MediaSession(this,"Flipper AI Radio");
    session.setCallback(new MediaSession.Callback(){
      @Override public void onPlay(){if(player!=null)try{player.start();state="Playing";publish();}catch(IllegalStateException ignored){}}
      @Override public void onPause(){pause();}
      @Override public void onStop(){stopPlayback();}
    });
    session.setActive(true);
  }
  @Override public int onStartCommand(Intent intent,int flags,int startId){
    if(intent==null)return START_NOT_STICKY;
    String cmd=intent.getStringExtra(CMD);
    if("play".equals(cmd))play(intent.getStringExtra(URL),intent.getStringExtra(NAME));
    else if("pause".equals(cmd))pause();
    else if("resume".equals(cmd)){if(player!=null)try{player.start();state="Playing";publish();}catch(IllegalStateException e){state="Cannot resume stream";publish();}}
    else if("stop".equals(cmd))stopPlayback();
    else if("status".equals(cmd))publish();
    return START_NOT_STICKY;
  }
  private void play(String url,String name){
    if(url==null || !url.startsWith("https://")){state="Only HTTPS radio streams are allowed";publish();return;}
    stopPlayer();
    stream=url;station=name==null || name.trim().isEmpty()?"Radio stream":name.trim();
    state="Connecting";publish();
    try{
      player=new MediaPlayer();
      player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
      player.setDataSource(url);
      player.setOnPreparedListener(mp->{mp.start();state="Playing";publish();});
      player.setOnBufferingUpdateListener((mp,percent)->{if(percent<100 && "Connecting".equals(state)){state="Buffering ("+percent+"%)";publish();}});
      player.setOnInfoListener((mp,what,extra)->{
        if(what==MediaPlayer.MEDIA_INFO_BUFFERING_START){state="Buffering";publish();}
        else if(what==MediaPlayer.MEDIA_INFO_BUFFERING_END){state="Playing";publish();}
        return false;
      });
      player.setOnErrorListener((mp,what,extra)->{state=describeStreamError(what,extra);stopPlayer();publish();return true;});
      player.setOnCompletionListener(mp->{state="Stream ended";stopPlayer();publish();});
      player.prepareAsync();
      startForeground(NOTIFICATION,notification());
      foreground=true;
    }catch(IOException | IllegalArgumentException | IllegalStateException | SecurityException e){
      state="Cannot open stream: "+e.getClass().getSimpleName();stopPlayer();publish();
    }
  }
  /** Preserve Android error codes so failed stations can be diagnosed without guesswork. */
  private static String describeStreamError(int what,int extra){
    String reason;
    if(extra==MediaPlayer.MEDIA_ERROR_IO)reason="Network or stream I/O failure";
    else if(extra==MediaPlayer.MEDIA_ERROR_MALFORMED)reason="Malformed or unsupported stream";
    else if(extra==MediaPlayer.MEDIA_ERROR_UNSUPPORTED)reason="Audio format unsupported by this phone";
    else if(extra==MediaPlayer.MEDIA_ERROR_TIMED_OUT)reason="Station connection timed out";
    else if(what==MediaPlayer.MEDIA_ERROR_SERVER_DIED)reason="Android media service stopped";
    else reason="Playback failure";
    return "Stream error ("+what+"/"+extra+"): "+reason+". Try a different HTTPS MP3/AAC station.";
  }
  private void pause(){if(player!=null)try{if(player.isPlaying())player.pause();state="Paused";publish();}catch(IllegalStateException ignored){}}
  private void stopPlayer(){if(player!=null){try{player.reset();}catch(Exception ignored){}player.release();player=null;}}
  private void stopPlayback(){stopPlayer();state="Stopped";publish();if(foreground){stopForeground(STOP_FOREGROUND_REMOVE);foreground=false;}stopSelf();}
  private Notification notification(){
    Intent open=new Intent(this,RadioScannerActivity.class);
    PendingIntent content=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    Intent toggle=new Intent(this,RadioPlayerService.class).setAction(ACTION).putExtra(CMD,"Playing".equals(state)?"pause":"resume");
    Intent stop=new Intent(this,RadioPlayerService.class).setAction(ACTION).putExtra(CMD,"stop");
    PendingIntent togglePi=PendingIntent.getService(this,1,toggle,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    PendingIntent stopPi=PendingIntent.getService(this,2,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_media_play)
      .setContentTitle(station).setContentText(state).setContentIntent(content)
      .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()))
      .addAction(android.R.drawable.ic_media_pause,"Play / Pause",togglePi)
      .addAction(android.R.drawable.ic_delete,"Stop",stopPi).setOngoing("Playing".equals(state)).build();
  }
  private void publish(){
    Intent update=new Intent(UPDATE).setPackage(getPackageName()).putExtra(STATUS,state).putExtra(NAME,station);
    sendBroadcast(update);
    if(foreground)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION,notification());
    if(session!=null){
      int playback="Playing".equals(state)?PlaybackState.STATE_PLAYING:"Paused".equals(state)?PlaybackState.STATE_PAUSED:PlaybackState.STATE_STOPPED;
      session.setPlaybackState(new PlaybackState.Builder().setState(playback,PlaybackState.PLAYBACK_POSITION_UNKNOWN,1f)
        .setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_STOP).build());
      session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,station).build());
    }
  }
  @Override public IBinder onBind(Intent intent){return null;}
  @Override public void onDestroy(){stopPlayer();if(session!=null){session.setActive(false);session.release();}super.onDestroy();}
}
