package com.flipperai.satellite;

import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

/** Phone-speaker-only, user-initiated online radio stream playback.
 * No transmission, RF capture, feed discovery, audio recording or persistence.
 * The caller supplies a stream URL and must explicitly tap Play.
 */
public final class OnlineRadioPlayer implements AutoCloseable {
    public interface Events { void state(String message); }
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Events events;
    private MediaPlayer player;
    private int generation;
    private boolean closed;

    public OnlineRadioPlayer(Events events) {
        if(events==null)throw new IllegalArgumentException("events");
        this.events=events;
    }

    /** Must be invoked on the Activity UI thread, not by an automatic background task. */
    public void play(String userInput) {
        if(closed)throw new IllegalStateException("Radio player closed");
        final String url=RadioStreamPolicy.validate(userInput);
        stop();
        final int epoch=++generation;
        final MediaPlayer candidate=new MediaPlayer();
        player=candidate;
        try {
            candidate.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            candidate.setOnPreparedListener(mp->{
                if(closed || epoch!=generation || player!=mp)return;
                mp.start();
                events.state("Radio stream playing on phone");
            });
            candidate.setOnErrorListener((mp,what,extra)->{
                if(epoch==generation) {
                    stop();
                    events.state("Radio stream unavailable (media error "+what+")");
                }
                return true;
            });
            candidate.setOnCompletionListener(mp->{
                if(epoch==generation) {
                    stop();
                    events.state("Radio stream ended");
                }
            });
            candidate.setDataSource(url);
            candidate.prepareAsync();
            events.state("Connecting to user-selected HTTPS radio stream...");
        } catch(Exception e) {
            stop();
            throw new IllegalArgumentException("Could not start radio stream: "+e.getMessage(),e);
        }
    }
    public void stop() {
        generation++;
        MediaPlayer old=player;
        player=null;
        if(old!=null) {
            old.setOnPreparedListener(null);
            old.setOnErrorListener(null);
            old.setOnCompletionListener(null);
            try{old.reset();}catch(RuntimeException ignored){}
            old.release();
        }
    }
    @Override public void close() {
        if(closed)return;
        closed=true;
        stop();
        main.removeCallbacksAndMessages(null);
    }
}
