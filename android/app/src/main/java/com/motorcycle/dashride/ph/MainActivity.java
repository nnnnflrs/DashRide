package com.motorcycle.dashride.ph;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.view.KeyEvent;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(MediaStorePlugin.class);
        registerPlugin(BackgroundAudioPlugin.class);
        registerPlugin(BackgroundMusicPlayerPlugin.class);
        registerPlugin(GoogleMapsPlugin.class);
        registerPlugin(GooglePlacesPlugin.class);
        registerPlugin(MediaSessionPlugin.class);
        registerPlugin(TextToSpeechPlugin.class);
        super.onCreate(savedInstanceState);

        // Request all necessary permissions on app launch
        requestRequiredPermissions();
    }

    private void requestRequiredPermissions() {
        Log.d(TAG, "Requesting required permissions on app launch");

        // List of permissions to request
        java.util.List<String> permissionsToRequest = new java.util.ArrayList<>();

        // Location permissions (required for GPS and navigation)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        // Media/Storage permissions (required for music player)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+) - Use READ_MEDIA_AUDIO
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO);
            }
        } else {
            // Android 12 and below - Use READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }

        // Bluetooth permissions (required for BLE status check in StatusBar)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        }

        // Request all missing permissions at once
        if (!permissionsToRequest.isEmpty()) {
            Log.d(TAG, "Requesting " + permissionsToRequest.size() + " permissions");
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toArray(new String[0]),
                PERMISSION_REQUEST_CODE
            );
        } else {
            Log.d(TAG, "All permissions already granted");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Permission granted: " + permissions[i]);
                } else {
                    Log.w(TAG, "Permission denied: " + permissions[i]);
                }
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().onResume();
            getBridge().getWebView().resumeTimers();
        }
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
