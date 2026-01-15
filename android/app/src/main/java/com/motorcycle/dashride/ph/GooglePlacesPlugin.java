package com.motorcycle.dashride.ph;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.RectangularBounds;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;

import java.util.Arrays;
import java.util.List;

@CapacitorPlugin(name = "GooglePlaces")
public class GooglePlacesPlugin extends Plugin {
    private static final String TAG = "GooglePlacesPlugin";
    private PlacesClient placesClient;
    private AutocompleteSessionToken sessionToken;
    private Handler mainHandler;

    @Override
    public void load() {
        super.load();
        mainHandler = new Handler(Looper.getMainLooper());

        try {
            // Initialize Places SDK
            if (!Places.isInitialized()) {
                // Get API key from resources (set in AndroidManifest.xml)
                String apiKey = getContext().getPackageManager()
                    .getApplicationInfo(getContext().getPackageName(),
                    android.content.pm.PackageManager.GET_META_DATA)
                    .metaData.getString("com.google.android.geo.API_KEY");

                Places.initialize(getContext(), apiKey);
            }

            placesClient = Places.createClient(getContext());
            sessionToken = AutocompleteSessionToken.newInstance();

            Log.d(TAG, "GooglePlacesPlugin loaded successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Places SDK", e);
        }
    }

    @PluginMethod
    public void searchPlaces(PluginCall call) {
        String query = call.getString("query");
        Log.d(TAG, "searchPlaces called with query: " + query);

        if (query == null || query.trim().isEmpty()) {
            Log.e(TAG, "Query is empty");
            call.reject("Query is required");
            return;
        }

        Double lat = call.getDouble("lat");
        Double lng = call.getDouble("lng");
        Log.d(TAG, "Search location: lat=" + lat + ", lng=" + lng);

        mainHandler.post(() -> {
            try {
                if (placesClient == null) {
                    Log.e(TAG, "PlacesClient is null!");
                    call.reject("Places client not initialized");
                    return;
                }

                Log.d(TAG, "Building autocomplete request...");
                // Build autocomplete request
                FindAutocompletePredictionsRequest.Builder requestBuilder =
                    FindAutocompletePredictionsRequest.builder()
                        .setSessionToken(sessionToken)
                        .setQuery(query);

                // Add location bias if provided
                if (lat != null && lng != null) {
                    // Bias results towards a location (within ~50km radius)
                    double offset = 0.5; // Approximately 50km
                    RectangularBounds bounds = RectangularBounds.newInstance(
                        new LatLng(lat - offset, lng - offset),
                        new LatLng(lat + offset, lng + offset)
                    );
                    requestBuilder.setLocationBias(bounds);
                }

                FindAutocompletePredictionsRequest request = requestBuilder.build();

                placesClient.findAutocompletePredictions(request).addOnSuccessListener(response -> {
                    Log.d(TAG, "Got response, building predictions array...");
                    JSArray predictions = new JSArray();

                    for (AutocompletePrediction prediction : response.getAutocompletePredictions()) {
                        JSObject predictionObj = new JSObject();
                        predictionObj.put("placeId", prediction.getPlaceId());
                        predictionObj.put("primaryText", prediction.getPrimaryText(null).toString());
                        predictionObj.put("secondaryText", prediction.getSecondaryText(null).toString());
                        predictionObj.put("fullText", prediction.getFullText(null).toString());
                        predictions.put(predictionObj);
                    }

                    Log.d(TAG, "Returning " + predictions.length() + " predictions");
                    JSObject result = new JSObject();
                    result.put("predictions", predictions);
                    call.resolve(result);
                }).addOnFailureListener(exception -> {
                    Log.e(TAG, "Error getting autocomplete predictions", exception);
                    call.reject("Failed to get predictions: " + exception.getMessage());
                });

            } catch (Exception e) {
                Log.e(TAG, "Error in searchPlaces", e);
                call.reject("Failed to search places: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void getPlaceDetails(PluginCall call) {
        String placeId = call.getString("placeId");
        if (placeId == null || placeId.trim().isEmpty()) {
            call.reject("Place ID is required");
            return;
        }

        mainHandler.post(() -> {
            try {
                // Specify which place data types to return
                List<Place.Field> placeFields = Arrays.asList(
                    Place.Field.ID,
                    Place.Field.NAME,
                    Place.Field.ADDRESS,
                    Place.Field.LAT_LNG,
                    Place.Field.TYPES
                );

                FetchPlaceRequest request = FetchPlaceRequest.builder(placeId, placeFields)
                    .setSessionToken(sessionToken)
                    .build();

                placesClient.fetchPlace(request).addOnSuccessListener(response -> {
                    Place place = response.getPlace();

                    JSObject placeObj = new JSObject();
                    placeObj.put("placeId", place.getId());
                    placeObj.put("name", place.getName());
                    placeObj.put("address", place.getAddress());

                    if (place.getLatLng() != null) {
                        JSObject location = new JSObject();
                        location.put("lat", place.getLatLng().latitude);
                        location.put("lng", place.getLatLng().longitude);
                        placeObj.put("location", location);
                    }

                    if (place.getTypes() != null && !place.getTypes().isEmpty()) {
                        JSArray types = new JSArray();
                        for (Place.Type type : place.getTypes()) {
                            types.put(type.toString());
                        }
                        placeObj.put("types", types);
                    }

                    call.resolve(placeObj);

                    // Create new session token after place details fetch
                    sessionToken = AutocompleteSessionToken.newInstance();
                }).addOnFailureListener(exception -> {
                    Log.e(TAG, "Error getting place details", exception);
                    call.reject("Failed to get place details: " + exception.getMessage());
                });

            } catch (Exception e) {
                Log.e(TAG, "Error in getPlaceDetails", e);
                call.reject("Failed to get place details: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void clearSession(PluginCall call) {
        mainHandler.post(() -> {
            sessionToken = AutocompleteSessionToken.newInstance();
            call.resolve();
        });
    }
}
