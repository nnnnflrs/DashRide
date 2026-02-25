import Foundation
import Capacitor
import GoogleMaps
import CoreLocation

@objc(GoogleMapsNativePlugin)
public class GoogleMapsNativePlugin: CAPPlugin, CAPBridgedPlugin, GMSMapViewDelegate, CLLocationManagerDelegate {
    public let identifier = "GoogleMapsNativePlugin"
    public let jsName = "GoogleMapsNative"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "create", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setCenter", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "addMarker", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "removeMarker", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "updateMarker", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "drawRoute", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "clearRoute", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "updateRoute", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setMapType", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setMapStyle", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "show", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "hide", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "updateBounds", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "destroy", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getDirections", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "startLocationTracking", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stopLocationTracking", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "openLocationSettings", returnType: CAPPluginReturnPromise)
    ]

    // Multi-map support
    private var mapViews: [String: GMSMapView] = [:]
    private var mapContainers: [String: UIView] = [:]
    private var markersMap: [String: [String: GMSMarker]] = [:]
    private var routePolylinesMap: [String: [GMSPolyline]] = [:]
    private var currentMapIdInitializing: String = ""

    // Location tracking
    private var locationManager: CLLocationManager?
    private var isLocationTrackingStarted = false

    // Arrow image cache
    private var arrowImage: UIImage?

    public override func load() {
        super.load()

        // Initialize Google Maps SDK with API key from Info.plist
        if let apiKey = Bundle.main.object(forInfoDictionaryKey: "GMSApiKey") as? String {
            GMSServices.provideAPIKey(apiKey)
        }

        print("GoogleMapsNativePlugin loaded")
    }

    @objc func create(_ call: CAPPluginCall) {
        let mapId = call.getString("mapId") ?? "default"
        let type = call.getString("type") ?? "fullscreen"
        let lat = call.getDouble("lat") ?? 0.0
        let lng = call.getDouble("lng") ?? 0.0
        let zoom = call.getFloat("zoom") ?? 15.0

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }

            do {
                let camera = GMSCameraPosition.camera(withLatitude: lat, longitude: lng, zoom: zoom)
                let mapView = GMSMapView(frame: .zero, camera: camera)
                mapView.delegate = self

                // Create container for the map
                let mapContainer = UIView()
                mapContainer.clipsToBounds = true

                // Get the WebView
                guard let webView = self.bridge?.webView else {
                    call.reject("WebView not found")
                    return
                }

                if type == "minimap" {
                    // Minimap: use exact bounds from JavaScript if provided
                    let x = call.getFloat("x") ?? 16
                    let y = call.getFloat("y") ?? 60
                    let width = call.getFloat("width") ?? 208
                    let height = call.getFloat("height") ?? 160

                    mapContainer.frame = CGRect(x: CGFloat(x), y: CGFloat(y), width: CGFloat(width), height: CGFloat(height))
                    mapView.frame = mapContainer.bounds

                    // Minimap: disable gestures
                    mapView.settings.scrollGestures = false
                    mapView.settings.zoomGestures = false
                    mapView.settings.tiltGestures = false
                    mapView.settings.rotateGestures = false
                    mapView.settings.compassButton = false
                    mapView.settings.myLocationButton = false
                    mapView.isBuildingsEnabled = true
                    mapView.isIndoorEnabled = false

                    // Add as overlay on top of WebView
                    webView.superview?.addSubview(mapContainer)
                } else {
                    // Fullscreen: match WebView bounds
                    mapContainer.frame = webView.bounds
                    mapView.frame = mapContainer.bounds
                    mapView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
                    mapContainer.autoresizingMask = [.flexibleWidth, .flexibleHeight]

                    // Fullscreen: enable scroll and zoom gestures
                    // Note: tilt/rotate gestures won't work when map is behind WebView on iOS
                    mapView.settings.scrollGestures = true
                    mapView.settings.zoomGestures = true
                    mapView.settings.tiltGestures = false
                    mapView.settings.rotateGestures = false
                    mapView.settings.compassButton = false
                    mapView.settings.myLocationButton = false

                    mapView.setMinZoom(2.0, maxZoom: 21.0)
                    mapView.isBuildingsEnabled = true
                    mapView.isIndoorEnabled = true

                    // Add padding for UI elements
                    mapView.padding = UIEdgeInsets(top: 100, left: 0, bottom: 90, right: 20)

                    // Add map BEHIND WebView - WebView UI will be on top and clickable
                    webView.superview?.insertSubview(mapContainer, belowSubview: webView)

                    // Make WebView transparent so map shows through
                    webView.isOpaque = false
                    webView.backgroundColor = .clear
                    webView.scrollView.backgroundColor = .clear
                }

                mapContainer.addSubview(mapView)

                // Store references
                self.mapViews[mapId] = mapView
                self.mapContainers[mapId] = mapContainer
                self.markersMap[mapId] = [:]
                self.routePolylinesMap[mapId] = []
                self.currentMapIdInitializing = mapId

                print("MapView created successfully for mapId: \(mapId), type: \(type)")

                call.resolve(["success": true])
            } catch {
                call.reject("Failed to create map: \(error.localizedDescription)")
            }
        }
    }

    @objc func setCenter(_ call: CAPPluginCall) {
        let mapId = call.getString("mapId") ?? "default"

        guard let mapView = mapViews[mapId] else {
            call.reject("Map not initialized for mapId: \(mapId)")
            return
        }

        let lat = call.getDouble("lat") ?? 0.0
        let lng = call.getDouble("lng") ?? 0.0
        let zoom = call.getFloat("zoom")
        let tilt = call.getDouble("tilt")
        let bearing = call.getDouble("bearing")
        let animate = call.getBool("animate") ?? true

        DispatchQueue.main.async {
            var cameraPosition = GMSCameraPosition.camera(
                withLatitude: lat,
                longitude: lng,
                zoom: zoom ?? mapView.camera.zoom,
                bearing: bearing ?? mapView.camera.bearing,
                viewingAngle: tilt ?? mapView.camera.viewingAngle
            )

            if animate {
                mapView.animate(to: cameraPosition)
            } else {
                mapView.camera = cameraPosition
            }

            call.resolve()
        }
    }

    @objc func addMarker(_ call: CAPPluginCall) {
        let mapId = call.getString("mapId") ?? "default"

        guard let mapView = mapViews[mapId] else {
            call.reject("Map not initialized for mapId: \(mapId)")
            return
        }

        let id = call.getString("id") ?? "marker_\(Date().timeIntervalSince1970)"
        let lat = call.getDouble("lat") ?? 0.0
        let lng = call.getDouble("lng") ?? 0.0
        let title = call.getString("title")
        let color = call.getString("color") ?? "#FF0000"
        let iconType = call.getString("iconType") ?? "default"
        let rotation = call.getDouble("rotation")
        let flat = call.getBool("flat")
        let scale = call.getFloat("scale")

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }

            // Remove existing marker with same ID
            if var markers = self.markersMap[mapId], let existingMarker = markers[id] {
                existingMarker.map = nil
                markers.removeValue(forKey: id)
                self.markersMap[mapId] = markers
            }

            let marker = GMSMarker()
            marker.position = CLLocationCoordinate2D(latitude: lat, longitude: lng)
            marker.title = title

            if iconType == "dot" {
                // Create a blue dot for current location
                marker.icon = self.createDotImage(color: color)
                marker.groundAnchor = CGPoint(x: 0.5, y: 0.5)
            } else if iconType == "arrow" {
                // Use arrow image for navigation
                if let arrowImage = self.getArrowImage(scale: scale) {
                    marker.icon = arrowImage
                    marker.groundAnchor = CGPoint(x: 0.5, y: 0.5)
                    marker.isFlat = flat ?? true
                    if let rotation = rotation {
                        marker.rotation = rotation
                    }
                }
            } else {
                // Default pin marker with color
                marker.icon = GMSMarker.markerImage(with: self.colorFromHex(color))
                if let rotation = rotation {
                    marker.rotation = rotation
                }
                if let flat = flat {
                    marker.isFlat = flat
                }
            }

            marker.map = mapView

            // Store marker
            if self.markersMap[mapId] == nil {
                self.markersMap[mapId] = [:]
            }
            self.markersMap[mapId]?[id] = marker

            call.resolve(["id": id])
        }
    }

    @objc func removeMarker(_ call: CAPPluginCall) {
        let mapId = call.getString("mapId") ?? "default"
        guard let id = call.getString("id") else {
            call.reject("Marker ID required")
            return
        }

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }

            if var markers = self.markersMap[mapId], let marker = markers[id] {
                marker.map = nil
                markers.removeValue(forKey: id)
                self.markersMap[mapId] = markers
                call.resolve()
            } else {
                call.reject("Marker not found")
            }
        }
    }

    @objc func updateMarker(_ call: CAPPluginCall) {
        let mapId = call.getString("mapId") ?? "default"
        guard let id = call.getString("id") else {
            call.reject("Marker ID required")
            return
        }

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }

            guard let markers = self.markersMap[mapId], let marker = markers[id] else {
                call.reject("Marker not found")
                return
            }

            // Update position if provided
            if let lat = call.getDouble("lat"), let lng = call.getDouble("lng") {
                marker.position = CLLocationCoordinate2D(latitude: lat, longitude: lng)
            }

            // Update rotation if provided
            if let rotation = call.getDouble("rotation") {
                marker.rotation = rotation
            }

            // Update flat property if provided
            if let flat = call.getBool("flat") {
                marker.isFlat = flat
            }

            call.resolve()
        }
    }

    @objc func drawRoute(_ call: CAPPluginCall) {
        let mapId = call.getString("mapId") ?? "default"

        guard let mapView = mapViews[mapId] else {
            call.reject("Map not initialized for mapId: \(mapId)")
            return
        }

        guard let points = call.getArray("points") as? [[String: Any]] else {
            call.reject("Points array required")
            return
        }

        let color = call.getString("color") ?? "#4285F4"
        let width = call.getFloat("width") ?? 12
        let routeIndex = call.getInt("routeIndex")

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }

            let path = GMSMutablePath()
            for point in points {
                if let lat = point["lat"] as? Double, let lng = point["lng"] as? Double {
                    path.add(CLLocationCoordinate2D(latitude: lat, longitude: lng))
                }
            }

            let polyline = GMSPolyline(path: path)
            polyline.strokeWidth = CGFloat(width)
            polyline.strokeColor = self.colorFromHex(color)
            polyline.geodesic = true
            polyline.map = mapView

            // Store route index as user data
            if let routeIndex = routeIndex {
                polyline.userData = routeIndex
            }

            // Store polyline
            if self.routePolylinesMap[mapId] == nil {
                self.routePolylinesMap[mapId] = []
            }
            self.routePolylinesMap[mapId]?.append(polyline)

            call.resolve()
        }
    }

    @objc func clearRoute(_ call: CAPPluginCall) {
        let mapId = call.getString("mapId") ?? "default"

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }

            if let polylines = self.routePolylinesMap[mapId] {
                for polyline in polylines {
                    polyline.map = nil
                }
            }
            self.routePolylinesMap[mapId] = []

            call.resolve()
        }
    }

    @objc func updateRoute(_ call: CAPPluginCall) {
        let mapId = call.getString("mapId") ?? "default"

        guard let mapView = mapViews[mapId] else {
            call.reject("Map not initialized for mapId: \(mapId)")
            return
        }

        guard let points = call.getArray("points") as? [[String: Any]] else {
            call.reject("Points array required")
            return
        }

        let color = call.getString("color") ?? "#4285F4"
        let width = call.getFloat("width") ?? 14

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }

            let path = GMSMutablePath()
            for point in points {
                if let lat = point["lat"] as? Double, let lng = point["lng"] as? Double {
                    path.add(CLLocationCoordinate2D(latitude: lat, longitude: lng))
                }
            }

            // Update existing polyline if it exists
            if let polylines = self.routePolylinesMap[mapId], !polylines.isEmpty {
                polylines[0].path = path
            } else {
                // Create new polyline
                let polyline = GMSPolyline(path: path)
                polyline.strokeWidth = CGFloat(width)
                polyline.strokeColor = self.colorFromHex(color)
                polyline.geodesic = true
                polyline.map = mapView

                if self.routePolylinesMap[mapId] == nil {
                    self.routePolylinesMap[mapId] = []
                }
                self.routePolylinesMap[mapId]?.append(polyline)
            }

            call.resolve()
        }
    }

    @objc func setMapType(_ call: CAPPluginCall) {
        let mapId = call.getString("mapId") ?? "default"

        guard let mapView = mapViews[mapId] else {
            call.reject("Map not initialized for mapId: \(mapId)")
            return
        }

        let type = call.getString("type") ?? "normal"

        DispatchQueue.main.async {
            switch type.lowercased() {
            case "satellite":
                mapView.mapType = .satellite
            case "hybrid":
                mapView.mapType = .hybrid
            case "terrain":
                mapView.mapType = .terrain
            default:
                mapView.mapType = .normal
            }

            call.resolve()
        }
    }

    @objc func setMapStyle(_ call: CAPPluginCall) {
        let mapId = call.getString("mapId") ?? "default"

        guard let mapView = mapViews[mapId] else {
            call.reject("Map not initialized for mapId: \(mapId)")
            return
        }

        let style = call.getString("style")

        DispatchQueue.main.async {
            do {
                if let style = style, !style.isEmpty, style != "null" {
                    mapView.mapStyle = try GMSMapStyle(jsonString: style)
                } else {
                    mapView.mapStyle = nil
                }
                call.resolve()
            } catch {
                print("Error setting map style: \(error)")
                call.reject("Error setting map style: \(error.localizedDescription)")
            }
        }
    }

    @objc func show(_ call: CAPPluginCall) {
        let mapId = call.getString("mapId") ?? "default"

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }

            if let container = self.mapContainers[mapId] {
                container.isHidden = false

                // Ensure WebView remains transparent
                self.bridge?.webView?.isOpaque = false
                self.bridge?.webView?.backgroundColor = .clear

                call.resolve()
            } else {
                call.reject("Map not created for mapId: \(mapId)")
            }
        }
    }

    @objc func hide(_ call: CAPPluginCall) {
        let mapId = call.getString("mapId") ?? "default"

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }

            if let container = self.mapContainers[mapId] {
                container.isHidden = true
                call.resolve()
            } else {
                call.reject("Map not created for mapId: \(mapId)")
            }
        }
    }

    @objc func updateBounds(_ call: CAPPluginCall) {
        let mapId = call.getString("mapId") ?? "default"
        let x = call.getFloat("x") ?? 0
        let y = call.getFloat("y") ?? 0
        let width = call.getFloat("width") ?? 0
        let height = call.getFloat("height") ?? 0

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }

            if let container = self.mapContainers[mapId], let mapView = self.mapViews[mapId] {
                container.frame = CGRect(x: CGFloat(x), y: CGFloat(y), width: CGFloat(width), height: CGFloat(height))
                mapView.frame = container.bounds
                call.resolve()
            } else {
                call.reject("Map not found for mapId: \(mapId)")
            }
        }
    }

    @objc func destroy(_ call: CAPPluginCall) {
        let mapId = call.getString("mapId") ?? "default"

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }

            // Remove polylines
            if let polylines = self.routePolylinesMap[mapId] {
                for polyline in polylines {
                    polyline.map = nil
                }
            }
            self.routePolylinesMap.removeValue(forKey: mapId)

            // Remove markers
            if let markers = self.markersMap[mapId] {
                for (_, marker) in markers {
                    marker.map = nil
                }
            }
            self.markersMap.removeValue(forKey: mapId)

            // Remove map view
            if let mapView = self.mapViews[mapId] {
                mapView.removeFromSuperview()
            }
            self.mapViews.removeValue(forKey: mapId)

            // Remove container
            if let container = self.mapContainers[mapId] {
                container.removeFromSuperview()
            }
            self.mapContainers.removeValue(forKey: mapId)

            call.resolve()
        }
    }

    @objc func getDirections(_ call: CAPPluginCall) {
        guard let origin = call.getString("origin"),
              let destination = call.getString("destination"),
              let apiKey = call.getString("apiKey") else {
            call.reject("Missing required parameters")
            return
        }

        let avoidTolls = call.getBool("avoidTolls") ?? false

        DispatchQueue.global(qos: .userInitiated).async {
            var urlString = "https://maps.googleapis.com/maps/api/directions/json?"
            urlString += "origin=\(origin)"
            urlString += "&destination=\(destination)"
            urlString += "&alternatives=true"

            if avoidTolls {
                urlString += "&avoid=tolls"
            }

            urlString += "&key=\(apiKey)"

            guard let encodedUrl = urlString.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
                  let url = URL(string: encodedUrl) else {
                call.reject("Invalid URL")
                return
            }

            let task = URLSession.shared.dataTask(with: url) { data, response, error in
                if let error = error {
                    call.reject("Failed to fetch directions: \(error.localizedDescription)")
                    return
                }

                guard let data = data else {
                    call.reject("No data received")
                    return
                }

                do {
                    if let json = try JSONSerialization.jsonObject(with: data, options: []) as? [String: Any] {
                        call.resolve(json)
                    } else {
                        call.reject("Invalid JSON response")
                    }
                } catch {
                    call.reject("JSON parsing error: \(error.localizedDescription)")
                }
            }
            task.resume()
        }
    }

    @objc func startLocationTracking(_ call: CAPPluginCall) {
        if isLocationTrackingStarted {
            call.resolve()
            return
        }

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }

            if self.locationManager == nil {
                self.locationManager = CLLocationManager()
                self.locationManager?.delegate = self
                self.locationManager?.desiredAccuracy = kCLLocationAccuracyBestForNavigation
                self.locationManager?.distanceFilter = 1 // Update every meter
                self.locationManager?.allowsBackgroundLocationUpdates = true
                self.locationManager?.pausesLocationUpdatesAutomatically = false
            }

            self.locationManager?.startUpdatingLocation()
            self.locationManager?.startUpdatingHeading()
            self.isLocationTrackingStarted = true

            print("Location tracking started")
            call.resolve()
        }
    }

    @objc func stopLocationTracking(_ call: CAPPluginCall) {
        if !isLocationTrackingStarted {
            call.resolve()
            return
        }

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }

            self.locationManager?.stopUpdatingLocation()
            self.locationManager?.stopUpdatingHeading()
            self.isLocationTrackingStarted = false

            print("Location tracking stopped")
            call.resolve()
        }
    }

    @objc func openLocationSettings(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            if let url = URL(string: UIApplication.openSettingsURLString) {
                UIApplication.shared.open(url, options: [:], completionHandler: nil)
            }
            call.resolve()
        }
    }

    // MARK: - CLLocationManagerDelegate

    public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }

        var data: [String: Any] = [
            "latitude": location.coordinate.latitude,
            "longitude": location.coordinate.longitude,
            "altitude": location.altitude,
            "accuracy": location.horizontalAccuracy,
            "speed": max(0, location.speed), // Speed in m/s, ensure non-negative
            "bearing": location.course >= 0 ? location.course : 0,
            "timestamp": location.timestamp.timeIntervalSince1970 * 1000
        ]

        notifyListeners("locationUpdate", data: data)
    }

    public func locationManager(_ manager: CLLocationManager, didUpdateHeading newHeading: CLHeading) {
        // Heading updates can be used for compass-like features
    }

    public func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        print("Location manager error: \(error.localizedDescription)")
    }

    // MARK: - GMSMapViewDelegate

    public func mapView(_ mapView: GMSMapView, willMove gesture: Bool) {
        if gesture {
            // Find which map this is
            for (mapId, view) in mapViews {
                if view === mapView {
                    notifyListeners("cameraMoveStarted", data: ["gesture": true, "mapId": mapId])
                    break
                }
            }
        }
    }

    public func mapView(_ mapView: GMSMapView, didChange position: GMSCameraPosition) {
        // Find which map this is
        for (mapId, view) in mapViews {
            if view === mapView {
                let data: [String: Any] = [
                    "lat": position.target.latitude,
                    "lng": position.target.longitude,
                    "zoom": position.zoom,
                    "tilt": position.viewingAngle,
                    "bearing": position.bearing,
                    "mapId": mapId
                ]
                notifyListeners("cameraMove", data: data)
                break
            }
        }
    }

    public func mapView(_ mapView: GMSMapView, didTapAt coordinate: CLLocationCoordinate2D) {
        // Find which map this is
        for (mapId, view) in mapViews {
            if view === mapView {
                let data: [String: Any] = [
                    "lat": coordinate.latitude,
                    "lng": coordinate.longitude,
                    "mapId": mapId
                ]
                notifyListeners("mapClick", data: data)
                break
            }
        }
    }

    // MARK: - Helper Methods

    private func createDotImage(color: String) -> UIImage {
        let size: CGFloat = 40
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: size, height: size))

        return renderer.image { context in
            let ctx = context.cgContext
            let center = CGPoint(x: size / 2, y: size / 2)

            // Draw shadow
            ctx.setFillColor(UIColor.black.withAlphaComponent(0.25).cgColor)
            ctx.addArc(center: CGPoint(x: center.x + 1, y: center.y + 1), radius: size / 2 - 2, startAngle: 0, endAngle: .pi * 2, clockwise: true)
            ctx.fillPath()

            // Draw white border
            ctx.setFillColor(UIColor.white.cgColor)
            ctx.addArc(center: center, radius: size / 2 - 2, startAngle: 0, endAngle: .pi * 2, clockwise: true)
            ctx.fillPath()

            // Draw main dot
            ctx.setFillColor(colorFromHex(color).cgColor)
            ctx.addArc(center: center, radius: size / 2 - 6, startAngle: 0, endAngle: .pi * 2, clockwise: true)
            ctx.fillPath()
        }
    }

    private func getArrowImage(scale: Float?) -> UIImage? {
        // Try to load arrow image from assets
        if let image = UIImage(named: "up_arrow") {
            if let scale = scale, scale > 0 {
                let newSize = CGSize(width: image.size.width * CGFloat(scale), height: image.size.height * CGFloat(scale))
                UIGraphicsBeginImageContextWithOptions(newSize, false, image.scale)
                image.draw(in: CGRect(origin: .zero, size: newSize))
                let scaledImage = UIGraphicsGetImageFromCurrentImageContext()
                UIGraphicsEndImageContext()
                return scaledImage
            }
            return image
        }

        // Fallback: create a simple arrow programmatically
        let size: CGFloat = 50
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: size, height: size))

        return renderer.image { context in
            let ctx = context.cgContext

            // Draw blue navigation arrow
            ctx.setFillColor(UIColor(red: 26/255, green: 115/255, blue: 232/255, alpha: 1.0).cgColor)

            // Arrow path pointing up
            ctx.move(to: CGPoint(x: size / 2, y: 5))
            ctx.addLine(to: CGPoint(x: size - 10, y: size - 10))
            ctx.addLine(to: CGPoint(x: size / 2, y: size - 20))
            ctx.addLine(to: CGPoint(x: 10, y: size - 10))
            ctx.closePath()
            ctx.fillPath()
        }
    }

    private func colorFromHex(_ hex: String) -> UIColor {
        var hexSanitized = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        hexSanitized = hexSanitized.replacingOccurrences(of: "#", with: "")

        var rgb: UInt64 = 0
        Scanner(string: hexSanitized).scanHexInt64(&rgb)

        let red = CGFloat((rgb & 0xFF0000) >> 16) / 255.0
        let green = CGFloat((rgb & 0x00FF00) >> 8) / 255.0
        let blue = CGFloat(rgb & 0x0000FF) / 255.0

        return UIColor(red: red, green: green, blue: blue, alpha: 1.0)
    }
}
