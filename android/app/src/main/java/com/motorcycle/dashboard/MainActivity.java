package com.motorcycle.dashboard;

import android.os.Bundle;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;
import java.util.ArrayList;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(MediaStorePlugin.class);
        registerPlugin(BackgroundAudioPlugin.class);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onPause() {
        super.onPause();
        // Don't pause the WebView to keep audio playing
        // This allows background audio playback
    }

    @Override
    public void onResume() {
        super.onResume();
        // Resume normally
    }
}
