package com.motorcycle.dashride.ph;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.provider.Settings;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CapacitorPlugin(
    name = "GoogleMapsNative",
    permissions = {
        @Permission(strings = { Manifest.permission.ACCESS_FINE_LOCATION }, alias = "location")
    }
)
public class GoogleMapsPlugin extends Plugin implements OnMapReadyCallback {
    private static final String TAG = "GoogleMapsPlugin";

    // Multi-map instance support
    private Map<String, MapView> mapViews = new HashMap<>();
    private Map<String, GoogleMap> googleMaps = new HashMap<>();
    private Map<String, FrameLayout> mapContainers = new HashMap<>();
    private Map<String, Map<String, Marker>> markersMap = new HashMap<>();
    private Map<String, List<Polyline>> routePolylinesMap = new HashMap<>();
    private String currentMapIdInitializing; // Track which map is being initialized

    private Handler mainHandler;

    // Location tracking
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private boolean isLocationTrackingStarted = false;

    @Override
    public void load() {
        super.load();
        mainHandler = new Handler(Looper.getMainLooper());
        Log.d(TAG, "GoogleMapsPlugin loaded");
    }

    @PluginMethod
    public void create(PluginCall call) {
        Log.d(TAG, "create() called");

        String mapId = call.getString("mapId", "default");
        String type = call.getString("type", "fullscreen");
        double lat = call.getDouble("lat", 0.0);
        double lng = call.getDouble("lng", 0.0);
        int zoom = call.getInt("zoom", 15);

        mainHandler.post(() -> {
            try {
                // Create MapView
                MapView mapView = new MapView(getContext());
                mapView.onCreate(null);
                mapView.onResume();

                // Create container for the map
                FrameLayout mapContainer = new FrameLayout(getContext());

                // Get the WebView's position and size to match its bounds
                android.view.View webView = getBridge().getWebView();
                FrameLayout.LayoutParams containerParams;
                FrameLayout.LayoutParams mapViewParams;

                if ("minimap".equals(type)) {
                    // Minimap: use exact bounds from JavaScript if provided
                    Float x = call.getFloat("x");
                    Float y = call.getFloat("y");
                    Float width = call.getFloat("width");
                    Float height = call.getFloat("height");

                    int widthPx;
                    int heightPx;
                    int leftMarginPx;
                    int topMarginPx;

                    // Get screen dimensions
                    android.util.DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
                    int screenWidth = displayMetrics.widthPixels;
                    int screenHeight = displayMetrics.heightPixels;

                    // Only use JS bounds if they are valid (greater than 0)
                    // getBoundingClientRect returns CSS pixels - use them directly as physical pixels
                    if (width != null && height != null && width > 0 && height > 0) {
                        // Use exact pixel values from JavaScript getBoundingClientRect
                        widthPx = Math.round(width);
                        heightPx = Math.round(height);
                        leftMarginPx = (x != null && x > 0) ? Math.round(x) : dpToPx(16);
                        topMarginPx = (y != null && y > 0) ? Math.round(y) : dpToPx(16);
                        Log.d(TAG, "Using JS bounds: x=" + leftMarginPx + ", y=" + topMarginPx +
                              ", width=" + widthPx + ", height=" + heightPx);
                    } else {
                        // Fallback: Use reasonable dp values
                        widthPx = dpToPx(208);
                        heightPx = dpToPx(160);
                        leftMarginPx = dpToPx(16);
                        topMarginPx = dpToPx(60);
                        Log.d(TAG, "Using fallback dp bounds: x=" + leftMarginPx + ", y=" + topMarginPx +
                              ", width=" + widthPx + ", height=" + heightPx);
                    }

                    // Container params with positioning
                    containerParams = new FrameLayout.LayoutParams(widthPx, heightPx);
                    containerParams.leftMargin = leftMarginPx;
                    containerParams.topMargin = topMarginPx;
                    containerParams.gravity = android.view.Gravity.TOP | android.view.Gravity.LEFT;

                    // MapView params to fill container
                    mapViewParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    );
                } else {
                    // Fullscreen: match parent
                    containerParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    );

                    mapViewParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    );
                }

                // Add mapView to container with specific layout params
                mapContainer.addView(mapView, mapViewParams);

                // Enable clipping for minimap to prevent overflow
                if ("minimap".equals(type)) {
                    mapContainer.setClipToOutline(true);
                    mapContainer.setClipChildren(true);
                }

                // Add container to the bridge's web view parent
                ViewGroup bridgeView = (ViewGroup) getBridge().getWebView().getParent();

                if ("minimap".equals(type)) {
                    // Minimap: add as overlay with specific layout params
                    bridgeView.addView(mapContainer, containerParams);
                } else {
                    // Fullscreen: add at index 0 (behind WebView) with params
                    bridgeView.addView(mapContainer, 0, containerParams);
                }

                // Make WebView background transparent so map shows through
                webView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);

                // Intercept touch events and pass them to the native map
                if ("fullscreen".equals(type)) {
                    webView.setOnTouchListener((v, event) -> {
                        // Pass all touch events to the native map container
                        if (mapContainer != null && mapContainer.getVisibility() == android.view.View.VISIBLE) {
                            mapContainer.dispatchTouchEvent(event);
                        }
                        // Return false to let WebView also handle the touch for UI elements
                        return false;
                    });
                }

                // Store references in HashMaps
                mapViews.put(mapId, mapView);
                mapContainers.put(mapId, mapContainer);
                markersMap.put(mapId, new HashMap<>());
                routePolylinesMap.put(mapId, new ArrayList<>());

                // Track which map is being initialized
                currentMapIdInitializing = mapId;

                // Get the map asynchronously
                mapView.getMapAsync(this);

                // Store initial camera position for when map is ready
                call.getData().put("initialLat", lat);
                call.getData().put("initialLng", lng);
                call.getData().put("initialZoom", zoom);
                call.getData().put("mapId", mapId);
                call.getData().put("type", type);

                // Store call for later resolution
                saveCall(call);

                Log.d(TAG, "MapView created successfully for mapId: " + mapId + ", type: " + type);
            } catch (Exception e) {
                Log.e(TAG, "Error creating map", e);
                call.reject("Failed to create map: " + e.getMessage());
            }
        });
    }

    private int dpToPx(int dp) {
        return (int) (dp * getContext().getResources().getDisplayMetrics().density);
    }

    @Override
    public void onMapReady(GoogleMap map) {
        Log.d(TAG, "onMapReady() called for mapId: " + currentMapIdInitializing);

        // Store the map instance with its ID
        String mapId = currentMapIdInitializing;
        googleMaps.put(mapId, map);

        try {
            // Get saved call
            PluginCall call = getSavedCall();
            String type = "fullscreen";

            if (call != null) {
                String callType = call.getString("type");
                if (callType != null) {
                    type = callType;
                }
            }

            if ("minimap".equals(type)) {
                // Minimap configuration: disable gestures, no UI controls
                map.getUiSettings().setAllGesturesEnabled(false);
                map.getUiSettings().setCompassEnabled(false);
                map.getUiSettings().setMyLocationButtonEnabled(false);
                map.getUiSettings().setMapToolbarEnabled(false);
                map.getUiSettings().setZoomControlsEnabled(false);
                map.setBuildingsEnabled(true);
                map.setIndoorEnabled(false);
                map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                // No padding for minimap
                map.setPadding(0, 0, 0, 0);
            } else {
                // Fullscreen configuration: enable all gestures
                map.getUiSettings().setRotateGesturesEnabled(true);
                map.getUiSettings().setTiltGesturesEnabled(true);
                map.getUiSettings().setZoomGesturesEnabled(true);
                map.getUiSettings().setScrollGesturesEnabled(true);
                map.getUiSettings().setCompassEnabled(true);
                map.getUiSettings().setMyLocationButtonEnabled(false);
                map.getUiSettings().setMapToolbarEnabled(false);
                map.getUiSettings().setAllGesturesEnabled(true);
                map.setBuildingsEnabled(true);
                map.setIndoorEnabled(true);
                map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                // Add padding to move compass and zoom controls away from info panel
                map.setPadding(0, 400, 20, 180);
            }

            // Get saved call and set initial camera position
            if (call != null) {
                try {
                    double lat = call.getData().getDouble("initialLat");
                    double lng = call.getData().getDouble("initialLng");
                    int zoom = call.getData().getInt("initialZoom");

                    // Set initial camera position
                    CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(new LatLng(lat, lng))
                        .zoom(zoom)
                        .tilt(0)
                        .bearing(0)
                        .build();

                    map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));

                    JSObject result = new JSObject();
                    result.put("success", true);
                    call.resolve(result);
                    Log.d(TAG, "Map initialized successfully for mapId: " + mapId);
                } catch (JSONException e) {
                    Log.e(TAG, "Error getting initial position", e);
                    call.reject("Error initializing map");
                }
            }

            // Set up listeners for map events (only for fullscreen)
            if ("fullscreen".equals(type)) {
                setupMapListeners(mapId);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in onMapReady", e);
        }
    }

    private void setupMapListeners(String mapId) {
        GoogleMap map = googleMaps.get(mapId);
        if (map == null) return;

        // Listen for camera move start to detect user gestures
        map.setOnCameraMoveStartedListener(reason -> {
            // REASON_GESTURE = 1 means user initiated the move (drag, pinch, rotate, tilt)
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                JSObject data = new JSObject();
                data.put("gesture", true);
                data.put("mapId", mapId);
                notifyListeners("cameraMoveStarted", data);
            }
        });

        // Listen for camera changes (rotation, tilt, zoom, pan)
        map.setOnCameraMoveListener(() -> {
            CameraPosition position = map.getCameraPosition();
            JSObject data = new JSObject();
            data.put("lat", position.target.latitude);
            data.put("lng", position.target.longitude);
            data.put("zoom", position.zoom);
            data.put("tilt", position.tilt);
            data.put("bearing", position.bearing);
            data.put("mapId", mapId);
            notifyListeners("cameraMove", data);
        });

        // Listen for map clicks
        map.setOnMapClickListener(latLng -> {
            JSObject data = new JSObject();
            data.put("lat", latLng.latitude);
            data.put("lng", latLng.longitude);
            data.put("mapId", mapId);
            notifyListeners("mapClick", data);
        });

        // Listen for polyline clicks
        map.setOnPolylineClickListener(polyline -> {
            Object tag = polyline.getTag();
            if (tag != null) {
                JSObject data = new JSObject();
                data.put("routeIndex", (Integer) tag);
                data.put("mapId", mapId);
                notifyListeners("polylineClick", data);
            }
        });
    }

    @PluginMethod
    public void setCenter(PluginCall call) {
        String mapId = call.getString("mapId", "default");
        GoogleMap map = googleMaps.get(mapId);

        if (map == null) {
            call.reject("Map not initialized for mapId: " + mapId);
            return;
        }

        double lat = call.getDouble("lat", 0.0);
        double lng = call.getDouble("lng", 0.0);
        Integer zoom = call.getInt("zoom");
        Float tilt = call.getFloat("tilt");
        Float bearing = call.getFloat("bearing");
        boolean animate = call.getBoolean("animate", true);

        mainHandler.post(() -> {
            try {
                CameraPosition.Builder builder = new CameraPosition.Builder()
                    .target(new LatLng(lat, lng));

                if (zoom != null) builder.zoom(zoom);
                if (tilt != null) builder.tilt(tilt);
                if (bearing != null) builder.bearing(bearing);

                CameraPosition cameraPosition = builder.build();

                if (animate) {
                    map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                } else {
                    map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                }

                call.resolve();
            } catch (Exception e) {
                Log.e(TAG, "Error setting center", e);
                call.reject("Failed to set center: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void addMarker(PluginCall call) {
        String mapId = call.getString("mapId", "default");
        GoogleMap map = googleMaps.get(mapId);

        if (map == null) {
            call.reject("Map not initialized for mapId: " + mapId);
            return;
        }

        String id = call.getString("id", "marker_" + System.currentTimeMillis());
        double lat = call.getDouble("lat", 0.0);
        double lng = call.getDouble("lng", 0.0);
        String title = call.getString("title");
        String color = call.getString("color", "#FF0000");
        String iconType = call.getString("iconType", "default");
        Float rotation = call.getFloat("rotation");
        Boolean flat = call.getBoolean("flat");
        Float scale = call.getFloat("scale"); // Scale factor for icon size

        mainHandler.post(() -> {
            try {
                // Get markers map for this map instance
                Map<String, Marker> markers = markersMap.get(mapId);
                if (markers == null) {
                    markers = new HashMap<>();
                    markersMap.put(mapId, markers);
                }

                // Remove existing marker with same ID to prevent duplicates
                Marker existingMarker = markers.get(id);
                if (existingMarker != null) {
                    existingMarker.remove();
                    markers.remove(id);
                }

                MarkerOptions options = new MarkerOptions()
                    .position(new LatLng(lat, lng));

                if (title != null) {
                    options.title(title);
                }

                // Set marker icon based on type
                if ("dot".equals(iconType)) {
                    // Create a blue dot for current location
                    Bitmap dotBitmap = createDotBitmap(color);
                    options.icon(BitmapDescriptorFactory.fromBitmap(dotBitmap))
                          .anchor(0.5f, 0.5f); // Center the dot
                } else if ("arrow".equals(iconType)) {
                    // Use arrow PNG resource for navigation
                    int arrowResId = getContext().getResources().getIdentifier("up_arrow", "drawable", getContext().getPackageName());

                    // If scale is provided, create a scaled bitmap
                    if (scale != null && scale > 0) {
                        Bitmap originalBitmap = BitmapFactory.decodeResource(getContext().getResources(), arrowResId);
                        int scaledWidth = Math.round(originalBitmap.getWidth() * scale);
                        int scaledHeight = Math.round(originalBitmap.getHeight() * scale);
                        Bitmap scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true);
                        options.icon(BitmapDescriptorFactory.fromBitmap(scaledBitmap))
                              .anchor(0.5f, 0.5f) // Center the arrow
                              .flat(flat != null ? flat : true);
                        originalBitmap.recycle(); // Free memory
                    } else {
                        // Use original size
                        options.icon(BitmapDescriptorFactory.fromResource(arrowResId))
                              .anchor(0.5f, 0.5f) // Center the arrow
                              .flat(flat != null ? flat : true);
                    }

                    // Apply rotation if provided
                    if (rotation != null) {
                        options.rotation(rotation);
                    }
                } else {
                    // Set marker color for default pin markers
                    float hue = getHueFromColor(color);
                    options.icon(BitmapDescriptorFactory.defaultMarker(hue));

                    // Apply rotation and flat if provided
                    if (rotation != null) {
                        options.rotation(rotation);
                    }
                    if (flat != null) {
                        options.flat(flat);
                    }
                }

                Marker marker = map.addMarker(options);
                markers.put(id, marker);

                JSObject result = new JSObject();
                result.put("id", id);
                call.resolve(result);
            } catch (Exception e) {
                Log.e(TAG, "Error adding marker", e);
                call.reject("Failed to add marker: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void removeMarker(PluginCall call) {
        String mapId = call.getString("mapId", "default");
        String id = call.getString("id");

        if (id == null) {
            call.reject("Marker ID required");
            return;
        }

        mainHandler.post(() -> {
            Map<String, Marker> markers = markersMap.get(mapId);
            if (markers != null) {
                Marker marker = markers.get(id);
                if (marker != null) {
                    marker.remove();
                    markers.remove(id);
                    call.resolve();
                } else {
                    call.reject("Marker not found");
                }
            } else {
                call.reject("Map not found");
            }
        });
    }

    @PluginMethod
    public void updateMarker(PluginCall call) {
        String mapId = call.getString("mapId", "default");
        String id = call.getString("id");

        if (id == null) {
            call.reject("Marker ID required");
            return;
        }

        Log.d(TAG, "updateMarker called for mapId: " + mapId + ", id: " + id);

        mainHandler.post(() -> {
            Map<String, Marker> markers = markersMap.get(mapId);
            if (markers == null) {
                Log.e(TAG, "Map not found: " + mapId);
                call.reject("Map not found");
                return;
            }

            Marker marker = markers.get(id);
            if (marker == null) {
                Log.e(TAG, "Marker not found: " + id);
                call.reject("Marker not found");
                return;
            }

            try {
                // Update position if provided
                Double lat = call.getDouble("lat");
                Double lng = call.getDouble("lng");
                if (lat != null && lng != null) {
                    Log.d(TAG, "Updating marker position: lat=" + lat + ", lng=" + lng);
                    marker.setPosition(new LatLng(lat, lng));
                }

                // Update rotation/bearing if provided
                Float rotation = call.getFloat("rotation");
                if (rotation != null) {
                    Log.d(TAG, "Updating marker rotation: " + rotation);
                    marker.setRotation(rotation);
                }

                // Update flat property if provided (for arrows that lay flat on map)
                Boolean flat = call.getBoolean("flat");
                if (flat != null) {
                    marker.setFlat(flat);
                }

                Log.d(TAG, "Marker updated successfully");
                call.resolve();
            } catch (Exception e) {
                Log.e(TAG, "Error updating marker", e);
                call.reject("Failed to update marker: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void drawRoute(PluginCall call) {
        String mapId = call.getString("mapId", "default");
        GoogleMap map = googleMaps.get(mapId);

        if (map == null) {
            call.reject("Map not initialized for mapId: " + mapId);
            return;
        }

        JSArray points = call.getArray("points");
        if (points == null) {
            call.reject("Points array required");
            return;
        }

        String color = call.getString("color", "#4285F4");
        int width = call.getInt("width", 12);
        Integer routeIndex = call.getInt("routeIndex");

        mainHandler.post(() -> {
            try {
                List<LatLng> routePoints = new ArrayList<>();
                for (int i = 0; i < points.length(); i++) {
                    JSONObject point = points.getJSONObject(i);
                    double lat = point.getDouble("lat");
                    double lng = point.getDouble("lng");
                    routePoints.add(new LatLng(lat, lng));
                }

                PolylineOptions polylineOptions = new PolylineOptions()
                    .addAll(routePoints)
                    .width(width)
                    .color(Color.parseColor(color))
                    .geodesic(true)
                    .clickable(true);

                Polyline polyline = map.addPolyline(polylineOptions);

                // Set tag for route identification
                if (routeIndex != null) {
                    polyline.setTag(routeIndex);
                }

                // Get or create polyline list for this map
                List<Polyline> polylines = routePolylinesMap.get(mapId);
                if (polylines == null) {
                    polylines = new ArrayList<>();
                    routePolylinesMap.put(mapId, polylines);
                }
                polylines.add(polyline);

                call.resolve();
            } catch (Exception e) {
                Log.e(TAG, "Error drawing route", e);
                call.reject("Failed to draw route: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void clearRoute(PluginCall call) {
        String mapId = call.getString("mapId", "default");

        mainHandler.post(() -> {
            List<Polyline> polylines = routePolylinesMap.get(mapId);
            if (polylines != null) {
                // Clear all polylines from the list
                for (Polyline polyline : polylines) {
                    if (polyline != null) {
                        polyline.remove();
                    }
                }
                polylines.clear();
            }
            call.resolve();
        });
    }

    @PluginMethod
    public void updateRoute(PluginCall call) {
        String mapId = call.getString("mapId", "default");
        GoogleMap map = googleMaps.get(mapId);

        if (map == null) {
            call.reject("Map not initialized for mapId: " + mapId);
            return;
        }

        JSArray points = call.getArray("points");
        if (points == null) {
            call.reject("Points array required");
            return;
        }

        mainHandler.post(() -> {
            try {
                List<LatLng> routePoints = new ArrayList<>();
                for (int i = 0; i < points.length(); i++) {
                    JSONObject point = points.getJSONObject(i);
                    double lat = point.getDouble("lat");
                    double lng = point.getDouble("lng");
                    routePoints.add(new LatLng(lat, lng));
                }

                // Get polylines list for this map
                List<Polyline> polylines = routePolylinesMap.get(mapId);
                if (polylines == null) {
                    polylines = new ArrayList<>();
                    routePolylinesMap.put(mapId, polylines);
                }

                // Update existing polyline if it exists, otherwise create new one
                Polyline routePolyline = polylines.isEmpty() ? null : polylines.get(0);
                if (routePolyline != null) {
                    routePolyline.setPoints(routePoints);
                    Log.d(TAG, "Updated route polyline with " + routePoints.size() + " points for mapId: " + mapId);
                } else {
                    // Create new polyline if it doesn't exist
                    String color = call.getString("color", "#4285F4");
                    int width = call.getInt("width", 14);

                    PolylineOptions polylineOptions = new PolylineOptions()
                        .addAll(routePoints)
                        .width(width)
                        .color(Color.parseColor(color))
                        .geodesic(true)
                        .clickable(false);

                    routePolyline = map.addPolyline(polylineOptions);
                    polylines.add(routePolyline);
                    Log.d(TAG, "Created new route polyline with " + routePoints.size() + " points for mapId: " + mapId);
                }

                call.resolve();
            } catch (Exception e) {
                Log.e(TAG, "Error updating route", e);
                call.reject("Failed to update route: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void setMapType(PluginCall call) {
        String mapId = call.getString("mapId", "default");
        GoogleMap map = googleMaps.get(mapId);

        if (map == null) {
            call.reject("Map not initialized for mapId: " + mapId);
            return;
        }

        String type = call.getString("type", "normal");

        mainHandler.post(() -> {
            int mapType;
            switch (type.toLowerCase()) {
                case "satellite":
                    mapType = GoogleMap.MAP_TYPE_SATELLITE;
                    break;
                case "hybrid":
                    mapType = GoogleMap.MAP_TYPE_HYBRID;
                    break;
                case "terrain":
                    mapType = GoogleMap.MAP_TYPE_TERRAIN;
                    break;
                default:
                    mapType = GoogleMap.MAP_TYPE_NORMAL;
            }

            map.setMapType(mapType);
            call.resolve();
        });
    }

    @PluginMethod
    public void setMapStyle(PluginCall call) {
        String mapId = call.getString("mapId", "default");
        GoogleMap map = googleMaps.get(mapId);

        if (map == null) {
            call.reject("Map not initialized for mapId: " + mapId);
            return;
        }

        String style = call.getString("style");

        mainHandler.post(() -> {
            try {
                if (style == null || style.isEmpty() || style.equals("null")) {
                    // Reset to default style
                    map.setMapStyle(null);
                    Log.d(TAG, "Map style reset to default for mapId: " + mapId);
                } else {
                    // Apply custom style
                    boolean success = map.setMapStyle(new MapStyleOptions(style));
                    if (success) {
                        Log.d(TAG, "Map style applied successfully for mapId: " + mapId);
                    } else {
                        Log.w(TAG, "Map style parsing failed for mapId: " + mapId);
                    }
                }
                call.resolve();
            } catch (Exception e) {
                Log.e(TAG, "Error setting map style", e);
                call.reject("Error setting map style: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void show(PluginCall call) {
        String mapId = call.getString("mapId", "default");

        mainHandler.post(() -> {
            FrameLayout container = mapContainers.get(mapId);
            if (container != null) {
                container.setVisibility(View.VISIBLE);

                // Only bring to front if it's a minimap (overlay), not fullscreen
                if ("minimap".equals(mapId)) {
                    container.bringToFront();
                }

                // Ensure WebView remains transparent
                getBridge().getWebView().setBackgroundColor(android.graphics.Color.TRANSPARENT);

                call.resolve();
                Log.d(TAG, "Map shown for mapId: " + mapId);
            } else {
                call.reject("Map not created for mapId: " + mapId);
            }
        });
    }

    @PluginMethod
    public void hide(PluginCall call) {
        String mapId = call.getString("mapId", "default");

        mainHandler.post(() -> {
            FrameLayout container = mapContainers.get(mapId);
            if (container != null) {
                container.setVisibility(View.GONE);
                call.resolve();
            } else {
                call.reject("Map not created for mapId: " + mapId);
            }
        });
    }

    @PluginMethod
    public void updateBounds(PluginCall call) {
        String mapId = call.getString("mapId", "default");
        Float x = call.getFloat("x");
        Float y = call.getFloat("y");
        Float width = call.getFloat("width");
        Float height = call.getFloat("height");

        mainHandler.post(() -> {
            FrameLayout container = mapContainers.get(mapId);
            if (container != null && width != null && height != null && width > 0 && height > 0) {
                try {
                    // Update container layout params with new bounds
                    int widthPx = Math.round(width);
                    int heightPx = Math.round(height);
                    int leftMarginPx = (x != null && x > 0) ? Math.round(x) : 0;
                    int topMarginPx = (y != null && y > 0) ? Math.round(y) : 0;

                    // Get existing layout params and modify them to preserve the correct type
                    ViewGroup.LayoutParams existingParams = container.getLayoutParams();
                    if (existingParams instanceof ViewGroup.MarginLayoutParams) {
                        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) existingParams;
                        params.width = widthPx;
                        params.height = heightPx;
                        params.leftMargin = leftMarginPx;
                        params.topMargin = topMarginPx;

                        // Set gravity if it's a FrameLayout.LayoutParams
                        if (params instanceof FrameLayout.LayoutParams) {
                            ((FrameLayout.LayoutParams) params).gravity = android.view.Gravity.TOP | android.view.Gravity.LEFT;
                        }

                        container.setLayoutParams(params);
                        container.requestLayout();
                    } else {
                        // Fallback: create new FrameLayout.LayoutParams
                        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(widthPx, heightPx);
                        params.leftMargin = leftMarginPx;
                        params.topMargin = topMarginPx;
                        params.gravity = android.view.Gravity.TOP | android.view.Gravity.LEFT;
                        container.setLayoutParams(params);
                        container.requestLayout();
                    }

                    Log.d(TAG, "Updated bounds for mapId " + mapId + ": x=" + leftMarginPx +
                          ", y=" + topMarginPx + ", width=" + widthPx + ", height=" + heightPx);
                    call.resolve();
                } catch (Exception e) {
                    Log.e(TAG, "Error updating bounds", e);
                    call.reject("Failed to update bounds: " + e.getMessage());
                }
            } else {
                call.reject("Invalid bounds or map not found for mapId: " + mapId);
            }
        });
    }

    @PluginMethod
    public void destroy(PluginCall call) {
        String mapId = call.getString("mapId", "default");

        mainHandler.post(() -> {
            try {
                MapView mapView = mapViews.get(mapId);
                if (mapView != null) {
                    mapView.onPause();
                    mapView.onDestroy();
                    mapViews.remove(mapId);
                }

                FrameLayout mapContainer = mapContainers.get(mapId);
                if (mapContainer != null && mapContainer.getParent() != null) {
                    ((ViewGroup) mapContainer.getParent()).removeView(mapContainer);
                    mapContainers.remove(mapId);
                }

                // Clear markers for this map
                Map<String, Marker> markers = markersMap.get(mapId);
                if (markers != null) {
                    markers.clear();
                    markersMap.remove(mapId);
                }

                // Clear polylines for this map
                List<Polyline> polylines = routePolylinesMap.get(mapId);
                if (polylines != null) {
                    for (Polyline polyline : polylines) {
                        if (polyline != null) {
                            polyline.remove();
                        }
                    }
                    polylines.clear();
                    routePolylinesMap.remove(mapId);
                }

                // Remove map instance
                googleMaps.remove(mapId);

                call.resolve();
                Log.d(TAG, "Map destroyed for mapId: " + mapId);
            } catch (Exception e) {
                Log.e(TAG, "Error destroying map", e);
                call.reject("Failed to destroy map: " + e.getMessage());
            }
        });
    }

    private Bitmap createDotBitmap(String color) {
        int size = 40; // Dot diameter in pixels
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Parse color
        int dotColor;
        try {
            dotColor = Color.parseColor(color);
        } catch (Exception e) {
            dotColor = Color.parseColor("#4285F4"); // Default blue
        }

        float centerX = size / 2f;
        float centerY = size / 2f;

        // Draw shadow (larger, semi-transparent black circle)
        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.parseColor("#40000000")); // 25% black
        canvas.drawCircle(centerX + 1, centerY + 1, size / 2f - 2, shadowPaint);

        // Draw white border
        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.WHITE);
        canvas.drawCircle(centerX, centerY, size / 2f - 2, borderPaint);

        // Draw main dot
        Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(dotColor);
        canvas.drawCircle(centerX, centerY, size / 2f - 6, dotPaint);

        return bitmap;
    }

    private float getHueFromColor(String color) {
        try {
            int colorInt = Color.parseColor(color);
            float[] hsv = new float[3];
            Color.colorToHSV(colorInt, hsv);
            return hsv[0];
        } catch (Exception e) {
            return BitmapDescriptorFactory.HUE_RED;
        }
    }

    @Override
    protected void handleOnPause() {
        super.handleOnPause();
        for (MapView mapView : mapViews.values()) {
            if (mapView != null) {
                mapView.onPause();
            }
        }
    }

    @Override
    protected void handleOnResume() {
        super.handleOnResume();
        for (MapView mapView : mapViews.values()) {
            if (mapView != null) {
                mapView.onResume();
            }
        }
    }

    @Override
    protected void handleOnDestroy() {
        super.handleOnDestroy();
        for (MapView mapView : mapViews.values()) {
            if (mapView != null) {
                mapView.onDestroy();
            }
        }
    }

    @PluginMethod
    public void getDirections(PluginCall call) {
        String origin = call.getString("origin");
        String destination = call.getString("destination");
        String apiKey = call.getString("apiKey");
        Boolean avoidTolls = call.getBoolean("avoidTolls", false);

        if (origin == null || destination == null || apiKey == null) {
            call.reject("Missing required parameters");
            return;
        }

        new Thread(() -> {
            try {
                StringBuilder urlBuilder = new StringBuilder("https://maps.googleapis.com/maps/api/directions/json?");
                urlBuilder.append("origin=").append(origin);
                urlBuilder.append("&destination=").append(destination);
                urlBuilder.append("&alternatives=true");

                if (avoidTolls) {
                    urlBuilder.append("&avoid=tolls");
                }

                urlBuilder.append("&key=").append(apiKey);

                String urlString = urlBuilder.toString();
                Log.d(TAG, "Fetching directions with URL: " + urlString);

                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    String responseString = response.toString();
                    JSONObject jsonResponse = new JSONObject(responseString);

                    // Log the number of routes
                    if (jsonResponse.has("routes")) {
                        int routeCount = jsonResponse.getJSONArray("routes").length();
                        Log.d(TAG, "Directions API returned " + routeCount + " routes");
                    }

                    JSObject result = JSObject.fromJSONObject(jsonResponse);
                    call.resolve(result);
                } else {
                    Log.e(TAG, "HTTP error response code: " + responseCode);
                    call.reject("HTTP error: " + responseCode);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching directions", e);
                call.reject("Failed to fetch directions: " + e.getMessage());
            }
        }).start();
    }

    @SuppressLint("MissingPermission")
    @PluginMethod
    public void startLocationTracking(PluginCall call) {
        if (isLocationTrackingStarted) {
            call.resolve();
            return;
        }

        mainHandler.post(() -> {
            try {
                if (fusedLocationClient == null) {
                    fusedLocationClient = LocationServices.getFusedLocationProviderClient(getActivity());
                }

                LocationRequest locationRequest = new LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    1000 // Update interval: 1 second
                )
                .setMinUpdateIntervalMillis(500)
                .build();

                locationCallback = new LocationCallback() {
                    @Override
                    public void onLocationResult(LocationResult locationResult) {
                        if (locationResult == null) return;

                        for (Location location : locationResult.getLocations()) {
                            JSObject data = new JSObject();
                            data.put("latitude", location.getLatitude());
                            data.put("longitude", location.getLongitude());
                            data.put("altitude", location.getAltitude());
                            data.put("accuracy", location.getAccuracy());
                            data.put("speed", location.getSpeed()); // m/s
                            data.put("bearing", location.getBearing());
                            data.put("timestamp", location.getTime());

                            // Notify listeners
                            notifyListeners("locationUpdate", data);
                        }
                    }
                };

                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                );

                isLocationTrackingStarted = true;
                call.resolve();
                Log.d(TAG, "Location tracking started");
            } catch (Exception e) {
                Log.e(TAG, "Error starting location tracking", e);
                call.reject("Failed to start location tracking: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void stopLocationTracking(PluginCall call) {
        if (!isLocationTrackingStarted) {
            call.resolve();
            return;
        }

        mainHandler.post(() -> {
            try {
                if (fusedLocationClient != null && locationCallback != null) {
                    fusedLocationClient.removeLocationUpdates(locationCallback);
                    isLocationTrackingStarted = false;
                    Log.d(TAG, "Location tracking stopped");
                }
                call.resolve();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping location tracking", e);
                call.reject("Failed to stop location tracking: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void openLocationSettings(PluginCall call) {
        try {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getActivity().startActivity(intent);
            call.resolve();
        } catch (Exception e) {
            Log.e(TAG, "Error opening location settings", e);
            call.reject("Failed to open location settings: " + e.getMessage());
        }
    }
}
