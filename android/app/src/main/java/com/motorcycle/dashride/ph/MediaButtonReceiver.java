package com.motorcycle.dashride.ph;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.getcapacitor.Bridge;
import com.getcapacitor.PluginHandle;

/**
 * Static BroadcastReceiver for media button events from notifications
 * This is declared in AndroidManifest.xml to receive broadcasts even when app is in background
 */
public class MediaButtonReceiver extends BroadcastReceiver {
    private static final String TAG = "MediaButtonReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action != null) {
            try {
                Intent forwardIntent = new Intent(context, MainActivity.class);
                forwardIntent.setAction(action);
                forwardIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                context.startActivity(forwardIntent);
            } catch (Exception e) {
                Log.e(TAG, "Error forwarding action", e);
            }
        }
    }
}
