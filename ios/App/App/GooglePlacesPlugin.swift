import Foundation
import Capacitor
import GooglePlaces

@objc(GooglePlacesPlugin)
public class GooglePlacesPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "GooglePlacesPlugin"
    public let jsName = "GooglePlaces"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "searchPlaces", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getPlaceDetails", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "clearSession", returnType: CAPPluginReturnPromise)
    ]

    private var placesClient: GMSPlacesClient?
    private var sessionToken: GMSAutocompleteSessionToken?

    public override func load() {
        super.load()

        // Initialize Places SDK with API key from Info.plist
        if let apiKey = Bundle.main.object(forInfoDictionaryKey: "GMSApiKey") as? String {
            GMSPlacesClient.provideAPIKey(apiKey)
        }

        placesClient = GMSPlacesClient.shared()
        sessionToken = GMSAutocompleteSessionToken.init()

        print("GooglePlacesPlugin loaded successfully")
    }

    @objc func searchPlaces(_ call: CAPPluginCall) {
        guard let query = call.getString("query"), !query.trimmingCharacters(in: .whitespaces).isEmpty else {
            call.reject("Query is required")
            return
        }

        let lat = call.getDouble("lat")
        let lng = call.getDouble("lng")

        DispatchQueue.main.async { [weak self] in
            guard let self = self, let client = self.placesClient else {
                call.reject("Places client not initialized")
                return
            }

            // Create filter for autocomplete predictions
            let filter = GMSAutocompleteFilter()

            // Create location bias if coordinates provided
            if let lat = lat, let lng = lng {
                let center = CLLocationCoordinate2D(latitude: lat, longitude: lng)
                // Create a rectangular bounds ~50km around the center
                let offset = 0.5 // Approximately 50km
                let northEast = CLLocationCoordinate2D(latitude: lat + offset, longitude: lng + offset)
                let southWest = CLLocationCoordinate2D(latitude: lat - offset, longitude: lng - offset)

                filter.locationBias = GMSPlaceRectangularLocationOption(northEast, southWest)
            }

            client.findAutocompletePredictions(
                fromQuery: query,
                filter: filter,
                sessionToken: self.sessionToken
            ) { [weak self] (predictions, error) in
                if let error = error {
                    print("Error getting autocomplete predictions: \(error.localizedDescription)")
                    call.reject("Failed to get predictions: \(error.localizedDescription)")
                    return
                }

                var predictionsArray: [[String: Any]] = []

                if let predictions = predictions {
                    for prediction in predictions {
                        let predictionObj: [String: Any] = [
                            "placeId": prediction.placeID,
                            "primaryText": prediction.attributedPrimaryText.string,
                            "secondaryText": prediction.attributedSecondaryText?.string ?? "",
                            "fullText": prediction.attributedFullText.string
                        ]
                        predictionsArray.append(predictionObj)
                    }
                }

                print("Returning \(predictionsArray.count) predictions")
                call.resolve(["predictions": predictionsArray])
            }
        }
    }

    @objc func getPlaceDetails(_ call: CAPPluginCall) {
        guard let placeId = call.getString("placeId"), !placeId.trimmingCharacters(in: .whitespaces).isEmpty else {
            call.reject("Place ID is required")
            return
        }

        DispatchQueue.main.async { [weak self] in
            guard let self = self, let client = self.placesClient else {
                call.reject("Places client not initialized")
                return
            }

            // Specify which place data types to return
            let placeFields: GMSPlaceField = [.placeID, .name, .formattedAddress, .coordinate, .types]

            client.fetchPlace(
                fromPlaceID: placeId,
                placeFields: placeFields,
                sessionToken: self.sessionToken
            ) { [weak self] (place, error) in
                if let error = error {
                    print("Error getting place details: \(error.localizedDescription)")
                    call.reject("Failed to get place details: \(error.localizedDescription)")
                    return
                }

                guard let place = place else {
                    call.reject("Place not found")
                    return
                }

                var placeObj: [String: Any] = [
                    "placeId": place.placeID ?? "",
                    "name": place.name ?? "",
                    "address": place.formattedAddress ?? ""
                ]

                // Add location
                let location: [String: Any] = [
                    "lat": place.coordinate.latitude,
                    "lng": place.coordinate.longitude
                ]
                placeObj["location"] = location

                // Add types if available
                if let types = place.types, !types.isEmpty {
                    placeObj["types"] = types
                }

                // Create new session token after fetching place details
                self?.sessionToken = GMSAutocompleteSessionToken.init()

                call.resolve(placeObj)
            }
        }
    }

    @objc func clearSession(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            self?.sessionToken = GMSAutocompleteSessionToken.init()
            call.resolve()
        }
    }
}
