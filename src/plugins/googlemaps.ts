import { registerPlugin } from '@capacitor/core'

export interface LatLng {
  lat: number
  lng: number
}

export interface CameraPosition {
  lat: number
  lng: number
  zoom?: number
  tilt?: number
  bearing?: number
  animate?: boolean
}

export interface MarkerOptions {
  mapId?: string
  id?: string
  lat: number
  lng: number
  title?: string
  color?: string
  iconType?: 'default' | 'arrow' | 'dot'
  rotation?: number
  flat?: boolean
  scale?: number // Scale factor for icon size (e.g., 0.5 for half size, 2 for double size)
}

export interface RouteOptions {
  mapId?: string
  points: LatLng[]
  color?: string
  width?: number
  routeIndex?: number
}

export interface GoogleMapsNativePlugin {
  /**
   * Create and initialize the native map
   */
  create(options: {
    mapId?: string
    lat: number
    lng: number
    zoom?: number
    type?: 'fullscreen' | 'minimap'
    x?: number
    y?: number
    width?: number
    height?: number
  }): Promise<{ success: boolean }>

  /**
   * Set the map center with optional zoom, tilt, and bearing
   */
  setCenter(options: CameraPosition & { mapId?: string }): Promise<void>

  /**
   * Add a marker to the map
   */
  addMarker(options: MarkerOptions): Promise<{ id: string }>

  /**
   * Remove a marker from the map
   */
  removeMarker(options: { mapId?: string; id: string }): Promise<void>

  /**
   * Update marker position and/or rotation
   */
  updateMarker(options: { mapId?: string; id: string; lat?: number; lng?: number; rotation?: number; flat?: boolean }): Promise<void>

  /**
   * Draw a route on the map
   */
  drawRoute(options: RouteOptions): Promise<void>

  /**
   * Clear the current route
   */
  clearRoute(options?: { mapId?: string }): Promise<void>

  /**
   * Update the existing route without clearing (no blink)
   */
  updateRoute(options: RouteOptions): Promise<void>

  /**
   * Set the map type
   */
  setMapType(options: { mapId?: string; type: 'normal' | 'satellite' | 'hybrid' | 'terrain' }): Promise<void>

  /**
   * Show the map
   */
  show(options?: { mapId?: string }): Promise<void>

  /**
   * Hide the map
   */
  hide(options?: { mapId?: string }): Promise<void>

  /**
   * Update map bounds (position and size) - useful for handling device rotation or layout changes
   */
  updateBounds(options: { mapId?: string; x: number; y: number; width: number; height: number }): Promise<void>

  /**
   * Destroy the map and free resources
   */
  destroy(options?: { mapId?: string }): Promise<void>

  /**
   * Set the map style (for theme support)
   * @param options.style - JSON string for map styling or null to reset to default
   */
  setMapStyle(options: { mapId?: string; style: string | null }): Promise<void>

  /**
   * Add a listener for camera move started events (detects user gestures)
   */
  addListener(
    eventName: 'cameraMoveStarted',
    listenerFunc: (data: { gesture: boolean }) => void
  ): Promise<{ remove: () => void }>

  /**
   * Add a listener for camera move events
   */
  addListener(
    eventName: 'cameraMove',
    listenerFunc: (data: {
      lat: number
      lng: number
      zoom: number
      tilt: number
      bearing: number
    }) => void
  ): Promise<{ remove: () => void }>

  /**
   * Add a listener for map click events
   */
  addListener(
    eventName: 'mapClick',
    listenerFunc: (data: { lat: number; lng: number }) => void
  ): Promise<{ remove: () => void }>

  /**
   * Add a listener for polyline click events
   */
  addListener(
    eventName: 'polylineClick',
    listenerFunc: (data: { routeIndex: number }) => void
  ): Promise<{ remove: () => void }>

  /**
   * Get directions from Google Directions API
   */
  getDirections(options: { origin: string; destination: string; apiKey: string; avoidTolls?: boolean }): Promise<any>

  /**
   * Start location tracking using FusedLocationProviderClient (Android only)
   */
  startLocationTracking(): Promise<void>

  /**
   * Stop location tracking
   */
  stopLocationTracking(): Promise<void>

  /**
   * Add listener for location updates with speed data
   */
  addListener(
    eventName: 'locationUpdate',
    listenerFunc: (data: {
      latitude: number
      longitude: number
      altitude: number
      accuracy: number
      speed: number // m/s
      bearing: number
      timestamp: number
    }) => void
  ): Promise<{ remove: () => void }>

  /**
   * Open the device's location settings (Android only)
   * Opens the native location settings screen to allow user to enable location services
   */
  openLocationSettings(): Promise<void>
}

const GoogleMapsNative = registerPlugin<GoogleMapsNativePlugin>('GoogleMapsNative')

export { GoogleMapsNative }
