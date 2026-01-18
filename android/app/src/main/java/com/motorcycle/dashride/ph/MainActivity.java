package com.motorcycle.dashride.ph;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.view.KeyEvent;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private static final String TAG = "MainActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(MediaStorePlugin.class);
        registerPlugin(BackgroundAudioPlugin.class);
        registerPlugin(GoogleMapsPlugin.class);
        registerPlugin(GooglePlacesPlugin.class);
        registerPlugin(MediaSessionPlugin.class);
        registerPlugin(TextToSpeechPlugin.class);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();

        // Send pause event to JavaScript when app comes to foreground
        try {
            MediaSessionPlugin plugin = (MediaSessionPlugin) getBridge()
                .getPlugin("MediaSession").getInstance();
            if (plugin != null) {
                plugin.sendPauseEvent();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending pause event", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Handle media button events from headphones, intercom, etc.
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_HEADSETHOOK:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                // Forward to MediaSession
                Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
                intent.putExtra(Intent.EXTRA_KEY_EVENT, event);
                androidx.media.session.MediaButtonReceiver.handleIntent(
                    getMediaSessionCompat(), intent);
                return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    private MediaSessionCompat getMediaSessionCompat() {
        // Get the MediaSession from the MediaSessionPlugin
        try {
            MediaSessionPlugin plugin = (MediaSessionPlugin) getBridge()
                .getPlugin("MediaSession").getInstance();
            return plugin.getMediaSession();
        } catch (Exception e) {
            Log.e(TAG, "Error getting MediaSession", e);
            return null;
        }
    }
}
