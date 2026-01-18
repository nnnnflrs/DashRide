package com.motorcycle.dashride.ph;

import android.Manifest;
import android.annotation.SuppressLint;
import android.graphics.Bitmap;
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
    private MapView mapView;
    private GoogleMap googleMap;
    private FrameLayout mapContainer;
    private Map<String, Marker> markers = new HashMap<>();
    private Polyline routePolyline;
    private List<Polyline> routePolylines = new ArrayList<>();
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

        double lat = call.getDouble("lat", 0.0);
        double lng = call.getDouble("lng", 0.0);
        int zoom = call.getInt("zoom", 15);

        mainHandler.post(() -> {
            try {
                // Create MapView
                mapView = new MapView(getContext());
                mapView.onCreate(null);
                mapView.onResume();

                // Create container for the map
                mapContainer = new FrameLayout(getContext());

                // Get the WebView's position and size to match its bounds
                android.view.View webView = getBridge().getWebView();
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                );

                mapContainer.setLayoutParams(params);

                // Add mapView to container
                mapContainer.addView(mapView);

                // Add container to the bridge's web view parent at index 0 (behind WebView)
                ViewGroup bridgeView = (ViewGroup) getBridge().getWebView().getParent();
                bridgeView.addView(mapContainer, 0);

                // Make WebView background transparent so map shows through
                webView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);

                // Intercept touch events and pass them to the native map
                webView.setOnTouchListener((v, event) -> {
                    // Pass all touch events to the native map container
                    if (mapContainer != null && mapContainer.getVisibility() == android.view.View.VISIBLE) {
                        mapContainer.dispatchTouchEvent(event);
                    }
                    // Return false to let WebView also handle the touch for UI elements
                    return false;
                });

                // Get the map asynchronously
                mapView.getMapAsync(this);

                // Store initial camera position for when map is ready
                call.getData().put("initialLat", lat);
                call.getData().put("initialLng", lng);
                call.getData().put("initialZoom", zoom);

                // Store call for later resolution
                saveCall(call);

                Log.d(TAG, "MapView created successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error creating map", e);
                call.reject("Failed to create map: " + e.getMessage());
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap map) {
        Log.d(TAG, "onMapReady() called");
        this.googleMap = map;

        try {
            // Enable ALL gestures including rotation and tilt
            googleMap.getUiSettings().setRotateGesturesEnabled(true);
            googleMap.getUiSettings().setTiltGesturesEnabled(true);
            googleMap.getUiSettings().setZoomGesturesEnabled(true);
            googleMap.getUiSettings().setScrollGesturesEnabled(true);
            googleMap.getUiSettings().setCompassEnabled(true);
            googleMap.getUiSettings().setMyLocationButtonEnabled(false);
            googleMap.getUiSettings().setMapToolbarEnabled(false);
            googleMap.getUiSettings().setAllGesturesEnabled(true);

            // Enable 3D buildings for better rotation visualization
            googleMap.setBuildingsEnabled(true);
            googleMap.setIndoorEnabled(true);

            // Set map type to normal (supports 3D buildings)
            googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);

            // Add padding to move compass and zoom controls away from info panel
            // Padding: left, top, right, bottom (in pixels)
            // Top padding: 400px to avoid info panel
            // Right padding: 20px for margin
            // Bottom padding: 180px to keep controls above locate button
            googleMap.setPadding(0, 400, 20, 180);

            // Get saved call
            PluginCall call = getSavedCall();
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

                    googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));

                    JSObject result = new JSObject();
                    result.put("success", true);
                    call.resolve(result);
                    Log.d(TAG, "Map initialized successfully");
                } catch (JSONException e) {
                    Log.e(TAG, "Error getting initial position", e);
                    call.reject("Error initializing map");
                }
            }

            // Set up listeners for map events
            setupMapListeners();

        } catch (Exception e) {
            Log.e(TAG, "Error in onMapReady", e);
        }
    }

    private void setupMapListeners() {
        if (googleMap == null) return;

        // Listen for camera move start to detect user gestures
        googleMap.setOnCameraMoveStartedListener(reason -> {
            // REASON_GESTURE = 1 means user initiated the move (drag, pinch, rotate, tilt)
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                JSObject data = new JSObject();
                data.put("gesture", true);
                notifyListeners("cameraMoveStarted", data);
            }
        });

        // Listen for camera changes (rotation, tilt, zoom, pan)
        googleMap.setOnCameraMoveListener(() -> {
            CameraPosition position = googleMap.getCameraPosition();
            JSObject data = new JSObject();
            data.put("lat", position.target.latitude);
            data.put("lng", position.target.longitude);
            data.put("zoom", position.zoom);
            data.put("tilt", position.tilt);
            data.put("bearing", position.bearing);
            notifyListeners("cameraMove", data);
        });

        // Listen for map clicks
        googleMap.setOnMapClickListener(latLng -> {
            JSObject data = new JSObject();
            data.put("lat", latLng.latitude);
            data.put("lng", latLng.longitude);
            notifyListeners("mapClick", data);
        });

        // Listen for polyline clicks
        googleMap.setOnPolylineClickListener(polyline -> {
            Object tag = polyline.getTag();
            if (tag != null) {
                JSObject data = new JSObject();
                data.put("routeIndex", (Integer) tag);
                notifyListeners("polylineClick", data);
            }
        });
    }

    @PluginMethod
    public void setCenter(PluginCall call) {
        if (googleMap == null) {
            call.reject("Map not initialized");
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
                    googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                } else {
                    googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
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
        if (googleMap == null) {
            call.reject("Map not initialized");
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

        mainHandler.post(() -> {
            try {
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
                    options.icon(BitmapDescriptorFactory.fromResource(
                            getContext().getResources().getIdentifier("up_arrow", "drawable", getContext().getPackageName())
                          ))
                          .anchor(0.5f, 0.5f) // Center the arrow
                          .flat(flat != null ? flat : true); // Flat by default for navigation arrows

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

                Marker marker = googleMap.addMarker(options);
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
        String id = call.getString("id");
        if (id == null) {
            call.reject("Marker ID required");
            return;
        }

        mainHandler.post(() -> {
            Marker marker = markers.get(id);
            if (marker != null) {
                marker.remove();
                markers.remove(id);
                call.resolve();
            } else {
                call.reject("Marker not found");
            }
        });
    }

    @PluginMethod
    public void updateMarker(PluginCall call) {
        String id = call.getString("id");
        if (id == null) {
            call.reject("Marker ID required");
            return;
        }

        Log.d(TAG, "updateMarker called for id: " + id);

        mainHandler.post(() -> {
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
        if (googleMap == null) {
            call.reject("Map not initialized");
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

                Polyline polyline = googleMap.addPolyline(polylineOptions);

                // Set tag for route identification
                if (routeIndex != null) {
                    polyline.setTag(routeIndex);
                }

                routePolylines.add(polyline);

                call.resolve();
            } catch (Exception e) {
                Log.e(TAG, "Error drawing route", e);
                call.reject("Failed to draw route: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void clearRoute(PluginCall call) {
        mainHandler.post(() -> {
            // Clear all polylines from the list
            for (Polyline polyline : routePolylines) {
                if (polyline != null) {
                    polyline.remove();
                }
            }
            routePolylines.clear();

            // Also clear old single polyline if exists (for backwards compatibility)
            if (routePolyline != null) {
                routePolyline.remove();
                routePolyline = null;
            }
            call.resolve();
        });
    }

    @PluginMethod
    public void updateRoute(PluginCall call) {
        if (googleMap == null) {
            call.reject("Map not initialized");
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

                // Update existing polyline if it exists, otherwise create new one
                if (routePolyline != null) {
                    routePolyline.setPoints(routePoints);
                    Log.d(TAG, "Updated route polyline with " + routePoints.size() + " points");
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

                    routePolyline = googleMap.addPolyline(polylineOptions);
                    Log.d(TAG, "Created new route polyline with " + routePoints.size() + " points");
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
        if (googleMap == null) {
            call.reject("Map not initialized");
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

            googleMap.setMapType(mapType);
            call.resolve();
        });
    }

    @PluginMethod
    public void setMapStyle(PluginCall call) {
        if (googleMap == null) {
            call.reject("Map not initialized");
            return;
        }

        String style = call.getString("style");

        mainHandler.post(() -> {
            try {
                if (style == null || style.isEmpty() || style.equals("null")) {
                    // Reset to default style
                    googleMap.setMapStyle(null);
                    Log.d(TAG, "Map style reset to default");
                } else {
                    // Apply custom style
                    boolean success = googleMap.setMapStyle(new MapStyleOptions(style));
                    if (success) {
                        Log.d(TAG, "Map style applied successfully");
                    } else {
                        Log.w(TAG, "Map style parsing failed");
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
        mainHandler.post(() -> {
            if (mapContainer != null) {
                mapContainer.setVisibility(View.VISIBLE);

                // Ensure WebView remains transparent
                getBridge().getWebView().setBackgroundColor(android.graphics.Color.TRANSPARENT);

                call.resolve();
            } else {
                call.reject("Map not created");
            }
        });
    }

    @PluginMethod
    public void hide(PluginCall call) {
        mainHandler.post(() -> {
            if (mapContainer != null) {
                mapContainer.setVisibility(View.GONE);
                call.resolve();
            } else {
                call.reject("Map not created");
            }
        });
    }

    @PluginMethod
    public void destroy(PluginCall call) {
        mainHandler.post(() -> {
            try {
                if (mapView != null) {
                    mapView.onPause();
                    mapView.onDestroy();
                }

                if (mapContainer != null && mapContainer.getParent() != null) {
                    ((ViewGroup) mapContainer.getParent()).removeView(mapContainer);
                }

                markers.clear();
                googleMap = null;
                mapView = null;
                mapContainer = null;
                routePolyline = null;

                call.resolve();
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
        if (mapView != null) {
            mapView.onPause();
        }
    }

    @Override
    protected void handleOnResume() {
        super.handleOnResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    protected void handleOnDestroy() {
        super.handleOnDestroy();
        if (mapView != null) {
            mapView.onDestroy();
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
}
