import UIKit
import Capacitor
import GoogleMaps
import GooglePlaces

@objc(MainViewController)
class MainViewController: CAPBridgeViewController {

    override func viewDidLoad() {
        super.viewDidLoad()

        // Initialize Google Maps SDK before registering plugins
        if let apiKey = Bundle.main.object(forInfoDictionaryKey: "GMSApiKey") as? String {
            GMSServices.provideAPIKey(apiKey)
            GMSPlacesClient.provideAPIKey(apiKey)
            NSLog("Google Maps SDK initialized with API key")
        } else {
            NSLog("WARNING: GMSApiKey not found in Info.plist")
        }
    }

    override func capacitorDidLoad() {
        super.capacitorDidLoad()

        NSLog("capacitorDidLoad called, bridge: \(String(describing: bridge))")

        // Register custom local plugins using instances
        let googleMapsPlugin = GoogleMapsNativePlugin()
        let googlePlacesPlugin = GooglePlacesPlugin()
        let textToSpeechPlugin = TextToSpeechPlugin()

        bridge?.registerPluginInstance(googleMapsPlugin)
        bridge?.registerPluginInstance(googlePlacesPlugin)
        bridge?.registerPluginInstance(textToSpeechPlugin)

        NSLog("Custom plugins registered: GoogleMapsNative, GooglePlaces, TextToSpeech")
    }
}
