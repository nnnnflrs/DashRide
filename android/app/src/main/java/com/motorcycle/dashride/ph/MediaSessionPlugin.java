package com.motorcycle.dashride.ph;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Base64;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "MediaSession")
public class MediaSessionPlugin extends Plugin {
    private static final String TAG = "MediaSessionPlugin";
    private static final String CHANNEL_ID = "music_playback";
    private static final int NOTIFICATION_ID = 1;

    // Action constants for notification buttons
    private static final String ACTION_PLAY = "com.motorcycle.dashride.ph.ACTION_PLAY";
    private static final String ACTION_PAUSE = "com.motorcycle.dashride.ph.ACTION_PAUSE";
    private static final String ACTION_NEXT = "com.motorcycle.dashride.ph.ACTION_NEXT";
    private static final String ACTION_PREVIOUS = "com.motorcycle.dashride.ph.ACTION_PREVIOUS";

    private MediaSessionCompat mediaSession;
    private NotificationManager notificationManager;
    private boolean isPlaying = false;
    private BroadcastReceiver mediaButtonReceiver;

    @Override
    public void load() {
        super.load();

        // Register broadcast receiver for notification button clicks
        mediaButtonReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action != null) {
                    switch (action) {
                        case ACTION_PLAY:
                            isPlaying = true;
                            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);
                            notifyListener("play", null);
                            break;
                        case ACTION_PAUSE:
                            isPlaying = false;
                            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED);
                            notifyListener("pause", null);
                            break;
                        case ACTION_NEXT:
                            notifyListener("next", null);
                            break;
                        case ACTION_PREVIOUS:
                            notifyListener("previous", null);
                            break;
                    }
                }
            }
        };

        // Register receiver for our custom actions
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PLAY);
        filter.addAction(ACTION_PAUSE);
        filter.addAction(ACTION_NEXT);
        filter.addAction(ACTION_PREVIOUS);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getContext().registerReceiver(mediaButtonReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            getContext().registerReceiver(mediaButtonReceiver, filter);
        }

        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Controls for music playback");
            channel.setShowBadge(false);

            notificationManager = getContext().getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        } else {
            notificationManager = (NotificationManager) getContext().getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        }

        // Initialize MediaSession
        mediaSession = new MediaSessionCompat(getContext(), TAG);
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );

        // Set up media session callback
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                isPlaying = true;
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);
                notifyListener("play", null);
            }

            @Override
            public void onPause() {
                isPlaying = false;
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED);
                notifyListener("pause", null);
            }

            @Override
            public void onSkipToNext() {
                notifyListener("next", null);
            }

            @Override
            public void onSkipToPrevious() {
                notifyListener("previous", null);
            }

            @Override
            public void onStop() {
                isPlaying = false;
                updatePlaybackState(PlaybackStateCompat.STATE_STOPPED);
                notifyListener("stop", null);
            }

            @Override
            public void onSeekTo(long pos) {
                JSObject data = new JSObject();
                data.put("position", pos / 1000.0); // Convert to seconds
                notifyListener("seek", data);
            }
        });

        mediaSession.setActive(true);
    }

    @PluginMethod
    public void updateMetadata(PluginCall call) {
        String title = call.getString("title", "Unknown Title");
        String artist = call.getString("artist", "Unknown Artist");
        String album = call.getString("album", "Unknown Album");
        String albumArtBase64 = call.getString("albumArt");
        Long duration = call.getLong("duration", 0L);

        MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, (long) (duration * 1000)); // Convert to milliseconds

        // Decode album art if provided
        if (albumArtBase64 != null && !albumArtBase64.isEmpty()) {
            try {
                // Remove data:image prefix if present
                String base64Data = albumArtBase64;
                if (albumArtBase64.startsWith("data:image")) {
                    base64Data = albumArtBase64.substring(albumArtBase64.indexOf(",") + 1);
                }

                byte[] decodedBytes = Base64.decode(base64Data, Base64.DEFAULT);
                Bitmap albumArt = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                if (albumArt != null) {
                    metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error decoding album art", e);
            }
        }

        mediaSession.setMetadata(metadataBuilder.build());

        // Update notification
        updateNotification(title, artist, albumArtBase64);

        call.resolve();
    }

    @PluginMethod
    public void updatePlaybackState(PluginCall call) {
        String state = call.getString("state", "paused");
        Long position = call.getLong("position", 0L);

        int playbackState = state.equals("playing")
            ? PlaybackStateCompat.STATE_PLAYING
            : PlaybackStateCompat.STATE_PAUSED;

        isPlaying = state.equals("playing");
        updatePlaybackState(playbackState, position * 1000); // Convert to milliseconds

        call.resolve();
    }

    private void updatePlaybackState(int state) {
        updatePlaybackState(state, 0);
    }

    private void updatePlaybackState(int state, long position) {
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY |
                PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(state, position, 1.0f);

        mediaSession.setPlaybackState(stateBuilder.build());
    }

    private void updateNotification(String title, String artist, String albumArtBase64) {
        Intent intent = new Intent(getContext(), MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
            getContext(),
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Decode album art for notification
        Bitmap albumArt = null;
        if (albumArtBase64 != null && !albumArtBase64.isEmpty()) {
            try {
                String base64Data = albumArtBase64;
                if (albumArtBase64.startsWith("data:image")) {
                    base64Data = albumArtBase64.substring(albumArtBase64.indexOf(",") + 1);
                }
                byte[] decodedBytes = Base64.decode(base64Data, Base64.DEFAULT);
                albumArt = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
            } catch (Exception e) {
                Log.e(TAG, "Error decoding album art for notification", e);
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext(), CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentIntent)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2)
            );

        if (albumArt != null) {
            builder.setLargeIcon(albumArt);
        }

        // Add action buttons with direct broadcast intents (explicit package)
        Intent previousIntent = new Intent(ACTION_PREVIOUS);
        previousIntent.setPackage(getContext().getPackageName());
        PendingIntent previousPending = PendingIntent.getBroadcast(
            getContext(), 0, previousIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        builder.addAction(new NotificationCompat.Action(
            android.R.drawable.ic_media_previous,
            "Previous",
            previousPending
        ));

        if (isPlaying) {
            Intent pauseIntent = new Intent(ACTION_PAUSE);
            pauseIntent.setPackage(getContext().getPackageName());
            PendingIntent pausePending = PendingIntent.getBroadcast(
                getContext(), 1, pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            builder.addAction(new NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pause",
                pausePending
            ));
        } else {
            Intent playIntent = new Intent(ACTION_PLAY);
            playIntent.setPackage(getContext().getPackageName());
            PendingIntent playPending = PendingIntent.getBroadcast(
                getContext(), 1, playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            builder.addAction(new NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Play",
                playPending
            ));
        }

        Intent nextIntent = new Intent(ACTION_NEXT);
        nextIntent.setPackage(getContext().getPackageName());
        PendingIntent nextPending = PendingIntent.getBroadcast(
            getContext(), 2, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        builder.addAction(new NotificationCompat.Action(
            android.R.drawable.ic_media_next,
            "Next",
            nextPending
        ));

        Notification notification = builder.build();

        // Start foreground service to keep playing in background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getActivity().startForegroundService(
                new Intent(getContext(), getActivity().getClass())
            );
        }

        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    @PluginMethod
    public void hideNotification(PluginCall call) {
        notificationManager.cancel(NOTIFICATION_ID);
        call.resolve();
    }

    private void notifyListener(String action, JSObject data) {
        JSObject ret = new JSObject();
        ret.put("action", action);
        if (data != null) {
            ret.put("data", data);
        }
        notifyListeners("mediaSessionAction", ret);
    }

    // Public getter for MainActivity to access the MediaSession
    public MediaSessionCompat getMediaSession() {
        return mediaSession;
    }

    @Override
    protected void handleOnDestroy() {
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
        if (mediaButtonReceiver != null) {
            try {
                getContext().unregisterReceiver(mediaButtonReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering receiver", e);
            }
        }
        super.handleOnDestroy();
    }
}
