import { ref, onUnmounted } from 'vue'
import { Capacitor } from '@capacitor/core'
import { GoogleMapsNative, type LatLng, type CameraPosition, type MarkerOptions } from '../plugins/googlemaps'

export interface MapConfig {
  lat: number
  lng: number
  zoom?: number
  theme?: 'light' | 'dark'
}

export function useGoogleMaps() {
  const isNative = Capacitor.getPlatform() === 'android'
  const isInitialized = ref(false)
  const loading = ref(true)
  const error = ref('')

  // Web API references
  let webMap: any = null
  let webMarkers: Map<string, any> = new Map()
  let webPolyline: any = null


  async function initializeMap(config: MapConfig): Promise<void> {
    loading.value = true
    error.value = ''

    try {
      if (isNative) {
        await GoogleMapsNative.create({
          lat: config.lat,
          lng: config.lng,
          zoom: config.zoom || 17
        })

        await GoogleMapsNative.show()

        isInitialized.value = true
        loading.value = false
      } else {

        throw new Error('Web implementation should be handled by component')
      }
    } catch (err: any) {
      console.error('Error initializing map:', err)
      error.value = err.message || 'Failed to initialize map'
      loading.value = false
    }
  }


  async function setCenter(position: CameraPosition): Promise<void> {
    if (!isInitialized.value) return

    try {
      if (isNative) {
        await GoogleMapsNative.setCenter(position)
      }
    } catch (err) {
      console.error('Error setting center:', err)
    }
  }

  async function addMarker(options: MarkerOptions): Promise<string> {
    if (!isInitialized.value) throw new Error('Map not initialized')

    try {
      if (isNative) {
        const result = await GoogleMapsNative.addMarker(options)
        return result.id
      }
      return ''
    } catch (err) {
      console.error('Error adding marker:', err)
      throw err
    }
  }


  async function removeMarker(id: string): Promise<void> {
    if (!isInitialized.value) return

    try {
      if (isNative) {
        await GoogleMapsNative.removeMarker({ id })
      }
    } catch (err) {
      console.error('Error removing marker:', err)
    }
  }


  async function drawRoute(points: LatLng[], color = '#4285F4', width = 5): Promise<void> {
    if (!isInitialized.value) return

    try {
      if (isNative) {
        await GoogleMapsNative.drawRoute({ points, color, width })
      }
    } catch (err) {
      console.error('Error drawing route:', err)
    }
  }


  async function clearRoute(): Promise<void> {
    if (!isInitialized.value) return

    try {
      if (isNative) {
        await GoogleMapsNative.clearRoute()
      }
    } catch (err) {
      console.error('Error clearing route:', err)
    }
  }


  async function setMapType(type: 'normal' | 'satellite' | 'hybrid' | 'terrain'): Promise<void> {
    if (!isInitialized.value) return

    try {
      if (isNative) {
        await GoogleMapsNative.setMapType({ type })
      }
    } catch (err) {
      console.error('Error setting map type:', err)
    }
  }


  async function showMap(): Promise<void> {
    if (!isInitialized.value) return

    try {
      if (isNative) {
        await GoogleMapsNative.show()
      }
    } catch (err) {
      console.error('Error showing map:', err)
    }
  }

  async function hideMap(): Promise<void> {
    if (!isInitialized.value) return

    try {
      if (isNative) {
        await GoogleMapsNative.hide()
      }
    } catch (err) {
      console.error('Error hiding map:', err)
    }
  }


  function onCameraMove(callback: (data: { lat: number; lng: number; zoom: number; tilt: number; bearing: number }) => void) {
    if (isNative) {
      GoogleMapsNative.addListener('cameraMove', callback)
    }
  }

  /**
   * Listen for map click events
   */
  function onMapClick(callback: (data: { lat: number; lng: number }) => void) {
    if (isNative) {
      GoogleMapsNative.addListener('mapClick', callback)
    }
  }

  async function destroyMap(): Promise<void> {
    try {
      if (isNative && isInitialized.value) {
        await GoogleMapsNative.destroy()
      }
      isInitialized.value = false
    } catch (err) {
      console.error('Error destroying map:', err)
    }
  }

  onUnmounted(() => {
    destroyMap()
  })

  return {
    isNative,
    isInitialized,
    loading,
    error,
    initializeMap,
    setCenter,
    addMarker,
    removeMarker,
    drawRoute,
    clearRoute,
    setMapType,
    showMap,
    hideMap,
    onCameraMove,
    onMapClick,
    destroyMap
  }
}
