package com.motorcycle.dashride.ph;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

@CapacitorPlugin(
    name = "MediaStore",
    permissions = {
        @Permission(strings = { Manifest.permission.READ_EXTERNAL_STORAGE }, alias = "storage"),
        @Permission(strings = { Manifest.permission.READ_MEDIA_AUDIO }, alias = "mediaAudio")
    }
)
public class MediaStorePlugin extends Plugin {
    private static final String TAG = "MediaStorePlugin";

    @PluginMethod
    public void scanAudioFiles(PluginCall call) {
        Log.d(TAG, "scanAudioFiles called");
        
        if (!hasRequiredPermissions()) {
            Log.d(TAG, "Permissions not granted, requesting...");
            requestAllPermissions(call, "permissionCallback");
            return;
        }

        Log.d(TAG, "Permissions granted, scanning files...");
        try {
            JSArray audioFiles = getAudioFiles();
            Log.d(TAG, "Found " + audioFiles.length() + " audio files");
            JSObject result = new JSObject();
            result.put("files", audioFiles);
            call.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "Error scanning audio files", e);
            call.reject("Failed to scan audio files: " + e.getMessage());
        }
    }

    @PluginMethod
    public void checkPermissions(PluginCall call) {
        Log.d(TAG, "checkPermissions called");
        JSObject result = new JSObject();
        result.put("granted", hasRequiredPermissions());
        call.resolve(result);
    }

    @PluginMethod
    public void getAlbumArt(PluginCall call) {
        Log.d(TAG, "getAlbumArt called");

        // Try to get audioId as Integer first, then as Long
        Integer audioIdInt = call.getInt("audioId");
        Long audioId = audioIdInt != null ? audioIdInt.longValue() : call.getLong("audioId");

        Log.d(TAG, "audioId parameter: " + audioId);

        if (audioId == null) {
            Log.e(TAG, "audioId is null!");
            call.reject("audioId is required");
            return;
        }

        try {
            Log.d(TAG, "Extracting album art from audio file ID: " + audioId);
            String albumArtBase64 = getAlbumArtFromAudioFile(audioId);
            Log.d(TAG, "getAlbumArtFromAudioFile returned: " + (albumArtBase64 != null ? "data (length: " + albumArtBase64.length() + ")" : "null"));

            JSObject result = new JSObject();
            if (albumArtBase64 != null) {
                result.put("albumArt", albumArtBase64);
                Log.d(TAG, "Resolving with album art data");
                call.resolve(result);
            } else {
                result.put("albumArt", null);
                Log.d(TAG, "Resolving with null album art");
                call.resolve(result);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting album art", e);
            call.reject("Failed to get album art: " + e.getMessage());
        }
    }

    @PermissionCallback
    private void permissionCallback(PluginCall call) {
        if (hasRequiredPermissions()) {
            scanAudioFiles(call);
        } else {
            call.reject("Permission denied");
        }
    }

    public boolean hasRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return hasPermission(Manifest.permission.READ_MEDIA_AUDIO);
        } else {
            return hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }

    private JSArray getAudioFiles() {
        JSArray audioFiles = new JSArray();
        ContentResolver contentResolver = getContext().getContentResolver();

        String[] projection = {
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION
        };

        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";

        Log.d(TAG, "Querying MediaStore for audio files (no album art)...");
        try (Cursor cursor = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )) {
            Log.d(TAG, "Query completed, cursor: " + (cursor != null ? "not null" : "null"));
            if (cursor != null) {
                Log.d(TAG, "Cursor count: " + cursor.getCount());
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
                int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                int albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
                int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);

                int count = 0;
                while (cursor.moveToNext()) {
                    count++;
                    long id = cursor.getLong(idColumn);
                    String name = cursor.getString(nameColumn);
                    String title = cursor.getString(titleColumn);
                    String artist = cursor.getString(artistColumn);
                    String album = cursor.getString(albumColumn);
                    long albumId = cursor.getLong(albumIdColumn);
                    long duration = cursor.getLong(durationColumn);

                    // Filter out corrupted or zero-duration files
                    if (duration <= 0) {
                        Log.d(TAG, "Skipping file with invalid duration: " + name);
                        continue;
                    }

                    Uri contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    );

                    // DON'T get album art during scan - load it on-demand when playing
                    
                    // Defensive null handling
                    if (title == null || title.isEmpty()) {
                        title = name;
                    }
                    if (title == null || title.isEmpty()) {
                        title = "Unknown Title";
                    }

                    JSObject audioFile = new JSObject();
                    audioFile.put("id", id);
                    audioFile.put("name", name != null ? name : "Unknown");
                    audioFile.put("title", title);
                    audioFile.put("artist", artist != null && !artist.isEmpty() ? artist : "Unknown Artist");
                    audioFile.put("album", album != null && !album.isEmpty() ? album : "Unknown Album");
                    audioFile.put("albumId", albumId); // Store albumId for lazy loading
                    audioFile.put("duration", duration); // Keep in milliseconds
                    audioFile.put("uri", contentUri.toString());
                    // albumArt will be loaded on-demand

                    audioFiles.put(audioFile);
                }
                Log.d(TAG, "Processed " + count + " audio files, added " + audioFiles.length() + " to result");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying audio files", e);
        }

        Log.d(TAG, "Returning " + audioFiles.length() + " audio files");
        return audioFiles;
    }

    private String getAlbumArtFromAudioFile(long audioId) {
        MediaMetadataRetriever retriever = null;
        try {
            Uri audioUri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                audioId
            );
            
            Log.d(TAG, "Audio URI: " + audioUri.toString());
            
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(getContext(), audioUri);
            
            byte[] art = retriever.getEmbeddedPicture();
            if (art == null) {
                Log.d(TAG, "No embedded album art found in audio file");
                return null;
            }
            
            Log.d(TAG, "Found embedded album art, size: " + art.length + " bytes");
            
            // Decode the image
            Bitmap bitmap = BitmapFactory.decodeByteArray(art, 0, art.length);
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode album art bitmap");
                return null;
            }
            
            // Scale down to max 300x300 to reduce memory usage
            int maxSize = 300;
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            
            Log.d(TAG, "Original bitmap size: " + width + "x" + height);
            
            if (width > maxSize || height > maxSize) {
                float scale = Math.min((float) maxSize / width, (float) maxSize / height);
                int newWidth = Math.round(width * scale);
                int newHeight = Math.round(height * scale);
                bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                Log.d(TAG, "Scaled bitmap to: " + newWidth + "x" + newHeight);
            }
            
            // Convert to base64
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream);
            byte[] imageBytes = outputStream.toByteArray();
            String base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
            
            bitmap.recycle();
            outputStream.close();
            
            Log.d(TAG, "Successfully converted album art to base64, length: " + base64.length());
            return "data:image/jpeg;base64," + base64;
        } catch (Exception e) {
            Log.e(TAG, "Error extracting album art from audio file " + audioId, e);
            return null;
        } finally {
            if (retriever != null) {
                try {
                    retriever.release();
                } catch (Exception e) {
                    Log.e(TAG, "Error releasing MediaMetadataRetriever", e);
                }
            }
        }
    }
}
