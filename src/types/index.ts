/**
 * Location coordinate interface
 */
export interface Location {
  lat: number
  lng: number
}

/**
 * GPS position interface for native location tracking
 */
export interface NativeGPSPosition {
  latitude: number
  longitude: number
  altitude: number | null
  accuracy: number
  speed: number | null
  bearing: number | null
  timestamp?: number
}

/**
 * Web Geolocation API Position (from Capacitor/browser)
 */
export interface WebGPSPosition {
  coords: {
    latitude: number
    longitude: number
    altitude: number | null
    accuracy: number
    speed: number | null
    heading: number | null
  }
  timestamp: number
}

/**
 * Trip data tracking interface
 */
export interface TripData {
  distance: number
  duration: number
  maxSpeed: number
  totalSpeed: number
  speedSamples: number
  totalDistance: number
}

/**
 * Navigation route step interface
 */
export interface RouteStep {
  html_instructions: string
  distance: {
    text: string
    value: number
  }
  duration: {
    text: string
    value: number
  }
  start_location: Location
  end_location: Location
  maneuver?: string
}

/**
 * Place suggestion interface for search
 */
export interface PlaceSuggestion {
  placeId: string
  primaryText: string
  secondaryText: string
  description: string
}

/**
 * Place details interface
 */
export interface PlaceDetails {
  name: string
  address: string
  location: Location
  placeId: string
}

/**
 * Available route interface
 */
export interface AvailableRoute {
  distance: string
  duration: string
  summary: string
  polyline: string
  steps: RouteStep[]
  bounds: {
    northeast: Location
    southwest: Location
  }
}

/**
 * WakeLock API interface
 */
export interface WakeLock {
  release: () => Promise<void>
  released: boolean
  type: 'screen'
}

/**
 * Navigator with WakeLock support
 */
export interface NavigatorWithWakeLock extends Navigator {
  wakeLock?: {
    request: (type: 'screen') => Promise<WakeLock>
  }
}
