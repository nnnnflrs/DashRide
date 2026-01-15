import { registerPlugin } from '@capacitor/core'

export interface PlacePrediction {
  placeId: string
  primaryText: string
  secondaryText: string
  fullText: string
}

export interface PlaceLocation {
  lat: number
  lng: number
}

export interface PlaceDetails {
  placeId: string
  name: string
  address: string
  location: PlaceLocation
  types?: string[]
}

export interface GooglePlacesPlugin {
  /**
   * Search for places with autocomplete predictions
   */
  searchPlaces(options: {
    query: string
    lat?: number
    lng?: number
  }): Promise<{ predictions: PlacePrediction[] }>

  /**
   * Get detailed information about a place
   */
  getPlaceDetails(options: { placeId: string }): Promise<PlaceDetails>

  /**
   * Clear the current autocomplete session
   */
  clearSession(): Promise<void>
}

const GooglePlaces = registerPlugin<GooglePlacesPlugin>('GooglePlaces')

export { GooglePlaces }
