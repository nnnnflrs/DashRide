import { ref, watch } from 'vue'
import { Capacitor } from '@capacitor/core'
import { Geolocation } from '@capacitor/geolocation'
import { toast } from 'vue-sonner'
import { GoogleMapsNative } from '../plugins/googlemaps'
import { useSettings } from './useSettings'
import type { Location, NativeGPSPosition } from '../types'

// Module-level state for GPS tracking
const speed = ref(0)
const altitude = ref(0)
const bearing = ref(0)
const isTracking = ref(false)
const currentLocation = ref<Location | null>(null)
const speedReadings = ref<number[]>([])

let watchId: string | null = null
let lastPosition: { latitude: number; longitude: number } | null = null

const maxSpeedReadings = 3

/**
 * GPS tracking composable
 * Handles GPS location tracking, speed smoothing, and position updates
 */
export function useGPSTracker() {
  const { unit } = useSettings()

  /**
   * Smooth speed readings using moving average to reduce GPS noise
   */
  const smoothSpeed = (rawSpeed: number): number => {
    speedReadings.value.push(rawSpeed)

    if (speedReadings.value.length > maxSpeedReadings) {
      speedReadings.value.shift()
    }

    const average = speedReadings.value.reduce((sum, val) => sum + val, 0) / speedReadings.value.length
    const speedThreshold = unit.value === 'mph' ? 1.86 : 3

    return average > speedThreshold ? average : 0
  }

  /**
   * Handle location update from native platform
   */
  const handleNativeLocationUpdate = (location: NativeGPSPosition, onUpdate?: (location: NativeGPSPosition) => void) => {
    const speedMps = location.speed || 0
    const speedKmh = speedMps * 3.6
    const currentSpeed = unit.value === 'mph' ? speedKmh * 0.621371 : speedKmh

    // Apply smoothing and threshold filtering to prevent GPS noise spikes
    // Also filter out readings with poor accuracy (>20m)
    if (location.accuracy && location.accuracy > 20) {
      // Poor GPS signal - use last known speed or 0
      speed.value = smoothSpeed(0)
    } else {
      speed.value = smoothSpeed(currentSpeed)
    }

    altitude.value = location.altitude || 0
    bearing.value = location.bearing || 0

    currentLocation.value = {
      lat: location.latitude,
      lng: location.longitude
    }

    lastPosition = { latitude: location.latitude, longitude: location.longitude }

    // Call external update handler if provided
    if (onUpdate) {
      onUpdate(location)
    }
  }

  /**
   * Handle location update from web geolocation API
   */
  const handleWebLocationUpdate = (position: GeolocationPosition, onUpdate?: (position: GeolocationPosition) => void) => {
    const speedMps = position.coords.speed || 0
    const speedKmh = speedMps * 3.6
    const currentSpeed = unit.value === 'mph' ? speedKmh * 0.621371 : speedKmh

    if (position.coords.accuracy && position.coords.accuracy > 20) {
      speed.value = smoothSpeed(0)
    } else {
      speed.value = smoothSpeed(currentSpeed)
    }

    altitude.value = position.coords.altitude || 0
    bearing.value = position.coords.heading || 0

    // Update current location for MiniMap
    currentLocation.value = {
      lat: position.coords.latitude,
      lng: position.coords.longitude
    }

    lastPosition = { latitude: position.coords.latitude, longitude: position.coords.longitude }

    // Call external update handler if provided
    if (onUpdate) {
      onUpdate(position)
    }
  }

  /**
   * Start GPS tracking
   * @param onUpdate - Optional callback for custom position update handling
   */
  const startTracking = async (
    onNativeUpdate?: (location: NativeGPSPosition) => void,
    onWebUpdate?: (position: GeolocationPosition) => void
  ) => {
    try {
      if (Capacitor.getPlatform() === 'android') {
        await GoogleMapsNative.startLocationTracking()

        await GoogleMapsNative.addListener('locationUpdate', (location: NativeGPSPosition) => {
          handleNativeLocationUpdate(location, onNativeUpdate)
        })

        watchId = 'native'
      } else {
        const permission = await Geolocation.checkPermissions()
        if (permission.location !== 'granted') {
          const requested = await Geolocation.requestPermissions()
          if (requested.location !== 'granted') {
            toast.error('Location permission denied')
            return
          }
        }

        watchId = await Geolocation.watchPosition(
          {
            enableHighAccuracy: true,
            timeout: 10000,
            maximumAge: 5000
          },
          (position, err) => {
            if (err) {
              console.error('GPS error:', err)
              toast.error('GPS error: ' + err.message)
              return
            }

            if (!position) return

            handleWebLocationUpdate(position, onWebUpdate)
          }
        )
      }

      isTracking.value = true
      toast.success('GPS tracking started')
    } catch (error) {
      console.error('Error starting tracking:', error)
      toast.error('Failed to start GPS tracking')
    }
  }

  /**
   * Stop GPS tracking and clean up resources
   */
  const stopTracking = async () => {
    if (watchId !== null) {
      if (Capacitor.getPlatform() === 'android' && watchId === 'native') {
        try {
          await GoogleMapsNative.stopLocationTracking()
        } catch (error) {
          console.error('Error stopping native tracking:', error)
        }
      } else if (typeof watchId === 'string') {
        try {
          await Geolocation.clearWatch({ id: watchId })
        } catch (error) {
          console.error('Error clearing watch:', error)
        }
      }
      watchId = null
    }

    isTracking.value = false
    speed.value = 0
    speedReadings.value = []
    toast.info('GPS tracking stopped')
  }

  /**
   * Get last known position
   */
  const getLastPosition = () => {
    return lastPosition
  }

  /**
   * Reset speed readings (useful when speed unit changes)
   */
  const resetSpeedReadings = () => {
    speedReadings.value = []
  }

  // Watch for unit changes and reset speed readings
  watch(unit, () => {
    resetSpeedReadings()
  })

  return {
    // State
    speed,
    altitude,
    bearing,
    isTracking,
    currentLocation,

    // Methods
    startTracking,
    stopTracking,
    getLastPosition,
    resetSpeedReadings,
  }
}
