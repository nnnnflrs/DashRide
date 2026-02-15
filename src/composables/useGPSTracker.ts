import { ref, watch } from 'vue'
import { Capacitor } from '@capacitor/core'
import { Geolocation } from '@capacitor/geolocation'
import { toast } from 'vue-sonner'
import { GoogleMapsNative } from '../plugins/googlemaps'
import { useSettings } from './useSettings'
import type { Location, NativeGPSPosition } from '../types'

const speed = ref(0)
const altitude = ref(0)
const bearing = ref(0)
const isTracking = ref(false)
const currentLocation = ref<Location | null>(null)

let watchId: string | null = null
let lastPosition: { latitude: number; longitude: number } | null = null

const BASE_ZERO_THRESHOLD_KMH = 2


export function useGPSTracker() {
  const { unit, gpsAccuracyFilter } = useSettings()

  const processSpeed = (rawSpeed: number, accuracy?: number): number => {
    let thresholdKmh = BASE_ZERO_THRESHOLD_KMH

    if (gpsAccuracyFilter.value && accuracy !== undefined) {
      if (accuracy > 30) {
        return 0
      } else if (accuracy > 15) {
        thresholdKmh = BASE_ZERO_THRESHOLD_KMH + (accuracy - 15) * 0.2
      } else if (accuracy > 10) {
        thresholdKmh = BASE_ZERO_THRESHOLD_KMH + 0.5
      }
    }

    const threshold = unit.value === 'mph' ? thresholdKmh * 0.621371 : thresholdKmh
    return rawSpeed < threshold ? 0 : rawSpeed
  }

  const handleNativeLocationUpdate = (location: NativeGPSPosition, onUpdate?: (location: NativeGPSPosition) => void) => {
    const speedMps = location.speed || 0
    const speedKmh = speedMps * 3.6
    const currentSpeed = unit.value === 'mph' ? speedKmh * 0.621371 : speedKmh

    speed.value = processSpeed(currentSpeed, location.accuracy)
    altitude.value = location.altitude || 0
    bearing.value = location.bearing || 0

    currentLocation.value = {
      lat: location.latitude,
      lng: location.longitude
    }

    if (onUpdate) {
      onUpdate(location)
    }

    lastPosition = { latitude: location.latitude, longitude: location.longitude }
  }


  const handleWebLocationUpdate = (position: GeolocationPosition, onUpdate?: (position: GeolocationPosition) => void) => {
    const speedMps = position.coords.speed || 0
    const speedKmh = speedMps * 3.6
    const currentSpeed = unit.value === 'mph' ? speedKmh * 0.621371 : speedKmh

    speed.value = processSpeed(currentSpeed, position.coords.accuracy)

    altitude.value = position.coords.altitude || 0
    bearing.value = position.coords.heading || 0

    currentLocation.value = {
      lat: position.coords.latitude,
      lng: position.coords.longitude
    }

    if (onUpdate) {
      onUpdate(position)
    }

    lastPosition = { latitude: position.coords.latitude, longitude: position.coords.longitude }
  }

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

            handleWebLocationUpdate(position as unknown as GeolocationPosition, onWebUpdate)
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
    toast.info('GPS tracking stopped')
  }

  const getLastPosition = () => {
    return lastPosition
  }

  const resetSpeed = () => {
    speed.value = 0
  }

  watch(unit, () => {
    resetSpeed()
  })

  return {
    speed,
    altitude,
    bearing,
    isTracking,
    currentLocation,

    startTracking,
    stopTracking,
    getLastPosition,
    resetSpeed,
  }
}
