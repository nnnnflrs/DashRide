package com.motorcycle.dashride.ph;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.IOException;

@CapacitorPlugin(name = "BackgroundMusicPlayer")
public class BackgroundMusicPlayerPlugin extends Plugin {
    private static final String TAG = "BackgroundMusicPlayer";

    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;
    private Handler progressHandler;
    private Runnable progressRunnable;
    private boolean isPrepared = false;

    @Override
    public void load() {
        super.load();
        audioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        progressHandler = new Handler(Looper.getMainLooper());

        // Create audio focus change listener
        audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
            @Override
            public void onAudioFocusChange(int focusChange) {
                Log.d(TAG, "Audio focus changed: " + focusChange);

                switch (focusChange) {
                    case AudioManager.AUDIOFOCUS_LOSS:
                        // Lost audio focus permanently - pause playback
                        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                            pause();
                            notifyAudioFocusLost();
                        }
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        // Lost audio focus temporarily - pause playback
                        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                            pause();
                        }
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        // Lost audio focus briefly - lower volume (duck)
                        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                            mediaPlayer.setVolume(0.3f, 0.3f);
                        }
                        break;
                    case AudioManager.AUDIOFOCUS_GAIN:
                        // Gained audio focus - restore volume
                        if (mediaPlayer != null) {
                            mediaPlayer.setVolume(1.0f, 1.0f);
                        }
                        break;
                }
            }
        };
    }

    @PluginMethod
    public void preload(PluginCall call) {
        String audioPath = call.getString("audioPath");
        if (audioPath == null) {
            call.reject("audioPath is required");
            return;
        }

        Log.d(TAG, "Preloading audio: " + audioPath);

        // Release existing player
        releasePlayer();

        try {
            // Request audio focus
            requestAudioFocus();

            // Create new MediaPlayer
            mediaPlayer = new MediaPlayer();

            // Set audio attributes for background playback
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
            mediaPlayer.setAudioAttributes(audioAttributes);

            // Set data source
            Uri audioUri = Uri.parse(audioPath);
            mediaPlayer.setDataSource(getContext(), audioUri);

            // Prepare async
            mediaPlayer.setOnPreparedListener(mp -> {
                isPrepared = true;
                Log.d(TAG, "Audio prepared, duration: " + mp.getDuration());

                JSObject ret = new JSObject();
                ret.put("duration", mp.getDuration() / 1000.0);
                call.resolve(ret);
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: what=" + what + ", extra=" + extra);
                isPrepared = false;
                call.reject("Failed to load audio");
                return true;
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                Log.d(TAG, "Playback completed");
                notifyCompletion();
            });

            mediaPlayer.prepareAsync();

        } catch (IOException e) {
            Log.e(TAG, "Failed to preload audio", e);
            call.reject("Failed to load audio: " + e.getMessage());
        }
    }

    @PluginMethod
    public void play(PluginCall call) {
        if (mediaPlayer == null || !isPrepared) {
            call.reject("Audio not loaded");
            return;
        }

        Double seekTo = call.getDouble("time");

        try {
            if (seekTo != null && seekTo > 0) {
                mediaPlayer.seekTo((int)(seekTo * 1000));
            }

            mediaPlayer.start();
            startProgressUpdates();

            Log.d(TAG, "Playback started");
            call.resolve();

        } catch (Exception e) {
            Log.e(TAG, "Failed to start playback", e);
            call.reject("Failed to play: " + e.getMessage());
        }
    }

    @PluginMethod
    public void pause(PluginCall call) {
        pause();
        call.resolve();
    }

    private void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            stopProgressUpdates();
            Log.d(TAG, "Playback paused");
        }
    }

    @PluginMethod
    public void stop(PluginCall call) {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            stopProgressUpdates();
            isPrepared = false;
            Log.d(TAG, "Playback stopped");
        }
        call.resolve();
    }

    @PluginMethod
    public void getCurrentTime(PluginCall call) {
        if (mediaPlayer != null && isPrepared) {
            JSObject ret = new JSObject();
            ret.put("currentTime", mediaPlayer.getCurrentPosition() / 1000.0);
            call.resolve(ret);
        } else {
            call.reject("Audio not loaded");
        }
    }

    @PluginMethod
    public void getDuration(PluginCall call) {
        if (mediaPlayer != null && isPrepared) {
            JSObject ret = new JSObject();
            ret.put("duration", mediaPlayer.getDuration() / 1000.0);
            call.resolve(ret);
        } else {
            call.reject("Audio not loaded");
        }
    }

    @PluginMethod
    public void setVolume(PluginCall call) {
        Double volume = call.getDouble("volume", 1.0);
        if (mediaPlayer != null) {
            float vol = volume.floatValue();
            mediaPlayer.setVolume(vol, vol);
            call.resolve();
        } else {
            call.reject("Audio not loaded");
        }
    }

    @PluginMethod
    public void isPlaying(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("isPlaying", mediaPlayer != null && mediaPlayer.isPlaying());
        call.resolve(ret);
    }

    private void requestAudioFocus() {
        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(false)
                .build();

            result = audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            result = audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            );
        }

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.d(TAG, "Audio focus granted");
        } else {
            Log.w(TAG, "Audio focus request failed");
        }
    }

    private void abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        } else {
            audioManager.abandonAudioFocus(audioFocusChangeListener);
        }
        Log.d(TAG, "Audio focus abandoned");
    }

    private void startProgressUpdates() {
        stopProgressUpdates();
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    // Progress updates handled by JS polling getCurrentTime
                    progressHandler.postDelayed(this, 100);
                }
            }
        };
        progressHandler.post(progressRunnable);
    }

    private void stopProgressUpdates() {
        if (progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
            progressRunnable = null;
        }
    }

    private void releasePlayer() {
        stopProgressUpdates();
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
            isPrepared = false;
        }
        abandonAudioFocus();
    }

    private void notifyCompletion() {
        JSObject ret = new JSObject();
        ret.put("event", "completed");
        notifyListeners("playbackEvent", ret);
    }

    private void notifyAudioFocusLost() {
        JSObject ret = new JSObject();
        ret.put("event", "audioFocusLost");
        notifyListeners("playbackEvent", ret);
    }

    @Override
    protected void handleOnDestroy() {
        releasePlayer();
        super.handleOnDestroy();
    }
}
