package com.motorcycle.dashride.ph;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;
import android.util.LruCache;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;

@CapacitorPlugin(name = "MediaStore", permissions = {
        @Permission(strings = { Manifest.permission.READ_EXTERNAL_STORAGE }, alias = "storage"),
        @Permission(strings = { Manifest.permission.READ_MEDIA_AUDIO }, alias = "mediaAudio")
})
public class MediaStorePlugin extends Plugin {
    private static final String TAG = "MediaStorePlugin";

    // Thumbnail sizes
    public static final int SIZE_SMALL = 256; // For list views
    public static final int SIZE_LARGE = 512; // For player view

    // Cache directory name
    private static final String CACHE_DIR = "album_thumbnails";

    // Background executor for off-main-thread work
    private ExecutorService executor = Executors.newFixedThreadPool(4);

    // In-memory LRU cache (50 albums * ~100KB = ~5MB max)
    private static final int LRU_CACHE_SIZE = 50;
    private LruCache<String, String> memoryCache = new LruCache<>(LRU_CACHE_SIZE);

    // Track which albums are currently being loaded to avoid duplicate work
    private ConcurrentHashMap<Long, Boolean> loadingAlbums = new ConcurrentHashMap<>();

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

    /**
     * Get a cached/resized album thumbnail
     * Returns file URI if cached, or triggers background load and returns null
     */
    @PluginMethod
    public void getAlbumThumbnail(PluginCall call) {
        Long albumId = getLongParam(call, "albumId");
        Integer size = call.getInt("size", SIZE_SMALL);

        if (albumId == null) {
            call.reject("albumId is required");
            return;
        }

        String cacheKey = albumId + "_" + size;

        // 1. Check memory cache first (instant)
        String cachedUri = memoryCache.get(cacheKey);
        if (cachedUri != null) {
            JSObject result = new JSObject();
            result.put("uri", cachedUri);
            result.put("source", "memory");
            call.resolve(result);
            return;
        }

        // 2. Check disk cache
        File cacheFile = getCacheFile(albumId, size);
        if (cacheFile.exists()) {
            String fileUri = "file://" + cacheFile.getAbsolutePath();
            memoryCache.put(cacheKey, fileUri);

            JSObject result = new JSObject();
            result.put("uri", fileUri);
            result.put("source", "disk");
            call.resolve(result);
            return;
        }

        // 3. Not cached - need to load
        // Return null immediately, client can show placeholder
        JSObject result = new JSObject();
        result.put("uri", (String) null);
        result.put("source", "none");
        result.put("loading", true);
        call.resolve(result);
    }

    /**
     * Load and cache album thumbnail in background
     * This does the heavy work off the main thread
     */
    @PluginMethod
    public void loadAlbumThumbnail(PluginCall call) {
        Long albumId = getLongParam(call, "albumId");
        Long audioId = getLongParam(call, "audioId"); // Fallback for embedded extraction
        Integer size = call.getInt("size", SIZE_SMALL);

        if (albumId == null) {
            call.reject("albumId is required");
            return;
        }

        // Check if already loading
        if (loadingAlbums.putIfAbsent(albumId, true) != null) {
            // Already being loaded by another call
            JSObject result = new JSObject();
            result.put("status", "already_loading");
            call.resolve(result);
            return;
        }

        // Execute in background thread
        executor.execute(() -> {
            try {
                String resultUri = loadAndCacheThumbnail(albumId, audioId, size);

                getActivity().runOnUiThread(() -> {
                    JSObject result = new JSObject();
                    if (resultUri != null) {
                        result.put("uri", resultUri);
                        result.put("status", "loaded");
                    } else {
                        result.put("uri", (String) null);
                        result.put("status", "not_found");
                    }
                    call.resolve(result);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading thumbnail for album " + albumId, e);
                getActivity().runOnUiThread(() -> {
                    call.reject("Failed to load thumbnail: " + e.getMessage());
                });
            } finally {
                loadingAlbums.remove(albumId);
            }
        });
    }

    /**
     * Batch preload thumbnails for visible albums
     */
    @PluginMethod
    public void preloadAlbumThumbnails(PluginCall call) {
        JSArray albumData = call.getArray("albums");
        Integer size = call.getInt("size", SIZE_SMALL);

        if (albumData == null) {
            call.reject("albums array is required");
            return;
        }

        // Execute batch loading in background
        executor.execute(() -> {
            JSArray results = new JSArray();

            try {
                for (int i = 0; i < albumData.length(); i++) {
                    Object item = albumData.get(i);
                    if (!(item instanceof org.json.JSONObject))
                        continue;

                    JSObject album = JSObject.fromJSONObject((org.json.JSONObject) item);
                    if (album == null)
                        continue;

                    Long albumId = album.getLong("albumId");
                    Long audioId = album.getLong("audioId");

                    if (albumId == null)
                        continue;

                    String cacheKey = albumId + "_" + size;

                    // Skip if already cached
                    if (memoryCache.get(cacheKey) != null) {
                        JSObject resultItem = new JSObject();
                        resultItem.put("albumId", albumId);
                        resultItem.put("uri", memoryCache.get(cacheKey));
                        resultItem.put("source", "memory");
                        results.put(resultItem);
                        continue;
                    }

                    File cacheFile = getCacheFile(albumId, size);
                    if (cacheFile.exists()) {
                        String fileUri = "file://" + cacheFile.getAbsolutePath();
                        memoryCache.put(cacheKey, fileUri);

                        JSObject resultItem = new JSObject();
                        resultItem.put("albumId", albumId);
                        resultItem.put("uri", fileUri);
                        resultItem.put("source", "disk");
                        results.put(resultItem);
                        continue;
                    }

                    // Load and cache
                    String uri = loadAndCacheThumbnail(albumId, audioId, size);

                    JSObject resultItem = new JSObject();
                    resultItem.put("albumId", albumId);
                    resultItem.put("uri", uri);
                    resultItem.put("source", uri != null ? "loaded" : "not_found");
                    results.put(resultItem);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in batch preload", e);
            }

            getActivity().runOnUiThread(() -> {
                JSObject response = new JSObject();
                response.put("results", results);
                call.resolve(response);
            });
        });
    }

    /**
     * Clear the thumbnail cache
     */
    @PluginMethod
    public void clearThumbnailCache(PluginCall call) {
        executor.execute(() -> {
            try {
                File cacheDir = new File(getContext().getCacheDir(), CACHE_DIR);
                if (cacheDir.exists()) {
                    deleteRecursive(cacheDir);
                }
                memoryCache.evictAll();

                getActivity().runOnUiThread(() -> {
                    JSObject result = new JSObject();
                    result.put("success", true);
                    call.resolve(result);
                });
            } catch (Exception e) {
                getActivity().runOnUiThread(() -> {
                    call.reject("Failed to clear cache: " + e.getMessage());
                });
            }
        });
    }

    // Keep legacy methods for backward compatibility
    @PluginMethod
    public void getAlbumArt(PluginCall call) {
        Long audioId = getLongParam(call, "audioId");

        if (audioId == null) {
            call.reject("audioId is required");
            return;
        }

        executor.execute(() -> {
            try {
                String albumArt = getAlbumArtFromAudioFile(audioId, SIZE_SMALL);

                getActivity().runOnUiThread(() -> {
                    JSObject result = new JSObject();
                    result.put("albumArt", albumArt);
                    call.resolve(result);
                });
            } catch (Exception e) {
                getActivity().runOnUiThread(() -> {
                    call.reject("Failed to get album art: " + e.getMessage());
                });
            }
        });
    }

    @PluginMethod
    public void getAlbumArtBatch(PluginCall call) {
        JSArray audioIds = call.getArray("audioIds");
        if (audioIds == null) {
            call.reject("audioIds array is required");
            return;
        }

        executor.execute(() -> {
            try {
                JSArray results = new JSArray();

                for (int i = 0; i < audioIds.length(); i++) {
                    Object itemId = audioIds.get(i);
                    Long audioId = parseLong(itemId);

                    if (audioId != null) {
                        JSObject itemResult = new JSObject();
                        itemResult.put("id", audioId);

                        try {
                            String albumArt = getAlbumArtFromAudioFile(audioId, SIZE_SMALL);
                            itemResult.put("albumArt", albumArt);
                        } catch (Exception e) {
                            itemResult.put("albumArt", (String) null);
                        }

                        results.put(itemResult);
                    }
                }

                getActivity().runOnUiThread(() -> {
                    JSObject response = new JSObject();
                    response.put("results", results);
                    call.resolve(response);
                });

            } catch (Exception e) {
                getActivity().runOnUiThread(() -> {
                    call.reject("Batch processing failed: " + e.getMessage());
                });
            }
        });
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

    // ==================== Private Helper Methods ====================

    private File getCacheFile(long albumId, int size) {
        File cacheDir = new File(getContext().getCacheDir(), CACHE_DIR);
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        return new File(cacheDir, albumId + "_" + size + ".webp");
    }

    /**
     * Load album art and save to disk cache
     * Flow: MediaStore album art → Embedded extraction
     */
    private String loadAndCacheThumbnail(Long albumId, Long audioId, int size) {
        String cacheKey = albumId + "_" + size;
        File cacheFile = getCacheFile(albumId, size);

        Bitmap bitmap = null;

        try {
            // 1. Try MediaStore album art URI first (most efficient)
            bitmap = loadFromMediaStoreAlbumArt(albumId, size);

            // 2. Fallback: Extract from audio file with sampling
            if (bitmap == null && audioId != null) {
                bitmap = extractEmbeddedArtWithSampling(audioId, size);
            }

            if (bitmap == null) {
                return null;
            }

            // Save to disk cache as WEBP
            FileOutputStream fos = new FileOutputStream(cacheFile);
            bitmap.compress(Bitmap.CompressFormat.WEBP, 80, fos);
            fos.close();

            bitmap.recycle();

            String fileUri = "file://" + cacheFile.getAbsolutePath();
            memoryCache.put(cacheKey, fileUri);

            Log.d(TAG, "Cached thumbnail for album " + albumId + " at " + size + "px");
            return fileUri;

        } catch (Exception e) {
            Log.e(TAG, "Error caching thumbnail for album " + albumId, e);
            if (bitmap != null)
                bitmap.recycle();
            return null;
        }
    }

    /**
     * Load from MediaStore album art content URI
     */
    private Bitmap loadFromMediaStoreAlbumArt(long albumId, int targetSize) {
        try {
            Uri albumArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId);

            ContentResolver resolver = getContext().getContentResolver();
            InputStream is = resolver.openInputStream(albumArtUri);

            if (is == null)
                return null;

            // First pass: get dimensions
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, options);
            is.close();

            // Calculate sample size for efficient decoding
            options.inSampleSize = calculateInSampleSize(options, targetSize, targetSize);
            options.inJustDecodeBounds = false;

            // Second pass: decode with sampling
            is = resolver.openInputStream(albumArtUri);
            if (is == null)
                return null;

            Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
            is.close();

            if (bitmap == null)
                return null;

            // Scale to exact target size
            return Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true);

        } catch (Exception e) {
            // Album art not available via MediaStore
            return null;
        }
    }

    /**
     * Extract embedded album art with efficient sampling
     */
    private Bitmap extractEmbeddedArtWithSampling(long audioId, int targetSize) {
        MediaMetadataRetriever retriever = null;
        try {
            Uri audioUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    audioId);

            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(getContext(), audioUri);

            byte[] art = retriever.getEmbeddedPicture();
            if (art == null)
                return null;

            // First pass: get dimensions
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(art, 0, art.length, options);

            // Calculate sample size
            options.inSampleSize = calculateInSampleSize(options, targetSize, targetSize);
            options.inJustDecodeBounds = false;

            // Second pass: decode with sampling
            Bitmap bitmap = BitmapFactory.decodeByteArray(art, 0, art.length, options);
            if (bitmap == null)
                return null;

            // Scale to exact target size
            return Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true);

        } catch (Exception e) {
            Log.e(TAG, "Error extracting embedded art from audio " + audioId, e);
            return null;
        } finally {
            if (retriever != null) {
                try {
                    retriever.release();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Calculate optimal inSampleSize for BitmapFactory
     */
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    /**
     * Legacy method for backward compatibility
     */
    private String getAlbumArtFromAudioFile(long audioId, int maxSize) {
        Bitmap bitmap = extractEmbeddedArtWithSampling(audioId, maxSize);
        if (bitmap == null)
            return null;

        try {
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream);
            byte[] imageBytes = outputStream.toByteArray();
            String base64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP);
            bitmap.recycle();
            outputStream.close();
            return "data:image/jpeg;base64," + base64;
        } catch (Exception e) {
            if (bitmap != null)
                bitmap.recycle();
            return null;
        }
    }

    private JSArray getAudioFiles() {
        long startTime = System.currentTimeMillis();
        JSArray audioFiles = new JSArray();
        ContentResolver contentResolver = getContext().getContentResolver();

        // 1. Batch query all albums to get IDs and Art paths (if available)
        // This avoids N queries later.
        java.util.Map<Long, String> albumArtMap = new java.util.HashMap<>();
        String[] albumProjection = {
                MediaStore.Audio.Albums._ID,
                MediaStore.Audio.Albums.ALBUM_ART
        };

        try (Cursor albumCursor = contentResolver.query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                albumProjection, null, null, null)) {
            if (albumCursor != null) {
                int idCol = albumCursor.getColumnIndex(MediaStore.Audio.Albums._ID);
                int artCol = albumCursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART);
                while (albumCursor.moveToNext()) {
                    long aid = albumCursor.getLong(idCol);
                    String artPath = albumCursor.getString(artCol);
                    if (artPath != null && !artPath.isEmpty()) {
                        albumArtMap.put(aid, artPath);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying album metadata", e);
        }

        // 2. Query tracks
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

        try (Cursor cursor = contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder)) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
                int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                int albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
                int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String name = cursor.getString(nameColumn);
                    String title = cursor.getString(titleColumn);
                    String artist = cursor.getString(artistColumn);
                    String album = cursor.getString(albumColumn);
                    long albumId = cursor.getLong(albumIdColumn);
                    long duration = cursor.getLong(durationColumn);

                    if (duration <= 0)
                        continue;

                    Uri contentUri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);

                    if (title == null || title.isEmpty())
                        title = name;
                    if (title == null || title.isEmpty())
                        title = "Unknown Title";

                    JSObject audioFile = new JSObject();
                    audioFile.put("id", id);
                    audioFile.put("name", name != null ? name : "Unknown");
                    audioFile.put("title", title);
                    audioFile.put("artist", artist != null && !artist.isEmpty() ? artist : "Unknown Artist");
                    audioFile.put("album", album != null && !album.isEmpty() ? album : "Unknown Album");
                    audioFile.put("albumId", albumId);
                    audioFile.put("duration", duration);
                    audioFile.put("uri", contentUri.toString());

                    // Include album art if found in MediaStore
                    if (albumArtMap.containsKey(albumId)) {
                        String artPath = albumArtMap.get(albumId);
                        // Convert to file URI for consistency
                        audioFile.put("albumArt", "file://" + artPath);
                    }

                    audioFiles.put(audioFile);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying audio files", e);
        }

        Log.i(TAG, "Scan completed in " + (System.currentTimeMillis() - startTime) + "ms for " + audioFiles.length()
                + " files");
        return audioFiles;
    }

    private Long getLongParam(PluginCall call, String key) {
        Integer intVal = call.getInt(key);
        if (intVal != null)
            return intVal.longValue();
        return call.getLong(key);
    }

    private Long parseLong(Object value) {
        if (value instanceof Integer)
            return ((Integer) value).longValue();
        if (value instanceof Long)
            return (Long) value;
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }
}
