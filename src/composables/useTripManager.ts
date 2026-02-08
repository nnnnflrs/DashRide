import { computed } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import type { TripData, SingleTripData } from '../types'
import { useSettings } from './useSettings'

let tripInterval: number | null = null

const MOVING_THRESHOLD_KMH = 3
const MAX_REASONABLE_SPEED_KMH = 200
const MAX_DISTANCE_PER_UPDATE_KM = 0.5

const defaultTripData: SingleTripData = {
  distance: 0,
  duration: 0,
  maxSpeed: 0,
  movingTime: 0,
}

export function useTripManager() {
  const { unit } = useSettings()

  const tripData = useLocalStorage<TripData>('dashride_tripData', {
    trip1: { ...defaultTripData },
    trip2: { ...defaultTripData },
    activeTrip: 1,
    totalDistance: 230,
  })

  // Get the active trip data
  const activeTrip = computed(() => tripData.value.activeTrip)
  const activeTripData = computed(() =>
    tripData.value.activeTrip === 1 ? tripData.value.trip1 : tripData.value.trip2
  )

  // Average speed = distance / moving time (proper calculation, not averaging GPS values)
  const avgSpeed = computed(() => {
    const trip = activeTripData.value
    if (trip.movingTime <= 0 || trip.distance <= 0) return 0
    const movingTimeHours = trip.movingTime / 3600
    return trip.distance / movingTimeHours
  })

  const formattedTripDuration = computed(() => {
    const trip = activeTripData.value
    const hours = Math.floor(trip.duration / 3600)
    const minutes = Math.floor((trip.duration % 3600) / 60)
    const seconds = trip.duration % 60
    return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`
  })

  const calculateDistance = (lat1: number, lon1: number, lat2: number, lon2: number): number => {
    const R = unit.value === 'mph' ? 3958.8 : 6371
    const dLat = toRad(lat2 - lat1)
    const dLon = toRad(lon2 - lon1)
    const a =
      Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return R * c
  }

  const toRad = (degrees: number): number => {
    return degrees * (Math.PI / 180)
  }

  // Updates BOTH trips in the background
  const updateTripData = (
    currentSpeed: number,
    currentPosition: { latitude: number; longitude: number } | null,
    lastPosition: { latitude: number; longitude: number } | null,
    accuracy?: number
  ) => {
    const movingThreshold = unit.value === 'mph' ? MOVING_THRESHOLD_KMH * 0.621371 : MOVING_THRESHOLD_KMH
    const maxReasonableSpeed = unit.value === 'mph' ? MAX_REASONABLE_SPEED_KMH * 0.621371 : MAX_REASONABLE_SPEED_KMH

    const isValidSpeed = currentSpeed > 0 && currentSpeed < maxReasonableSpeed
    const isMoving = currentSpeed >= movingThreshold

    // Update max speed for both trips
    if (isValidSpeed) {
      if (currentSpeed > tripData.value.trip1.maxSpeed) {
        tripData.value.trip1 = { ...tripData.value.trip1, maxSpeed: currentSpeed }
      }
      if (currentSpeed > tripData.value.trip2.maxSpeed) {
        tripData.value.trip2 = { ...tripData.value.trip2, maxSpeed: currentSpeed }
      }
    }

    // Filter out low-accuracy GPS readings
    if (accuracy !== undefined && accuracy > 30) {
      return
    }

    // Accumulate distance and moving time for BOTH trips
    if (lastPosition && currentPosition && isMoving && isValidSpeed) {
      const distance = calculateDistance(
        lastPosition.latitude,
        lastPosition.longitude,
        currentPosition.latitude,
        currentPosition.longitude
      )

      const maxDistancePerUpdate = unit.value === 'mph'
        ? MAX_DISTANCE_PER_UPDATE_KM * 0.621371
        : MAX_DISTANCE_PER_UPDATE_KM

      if (distance > 0 && distance < maxDistancePerUpdate) {
        // Update both trips
        tripData.value.trip1 = {
          ...tripData.value.trip1,
          distance: tripData.value.trip1.distance + distance,
          movingTime: tripData.value.trip1.movingTime + 1,
        }
        tripData.value.trip2 = {
          ...tripData.value.trip2,
          distance: tripData.value.trip2.distance + distance,
          movingTime: tripData.value.trip2.movingTime + 1,
        }
        // Update total distance
        tripData.value.totalDistance = tripData.value.totalDistance + distance
      }
    }
  }

  const startTripTimer = () => {
    if (tripInterval !== null) return

    tripInterval = window.setInterval(() => {
      // Increment duration for BOTH trips
      tripData.value.trip1 = {
        ...tripData.value.trip1,
        duration: tripData.value.trip1.duration + 1,
      }
      tripData.value.trip2 = {
        ...tripData.value.trip2,
        duration: tripData.value.trip2.duration + 1,
      }
    }, 1000)
  }

  const stopTripTimer = () => {
    if (tripInterval !== null) {
      clearInterval(tripInterval)
      tripInterval = null
    }
  }

  // Switch between Trip 1 and Trip 2
  const switchTrip = () => {
    tripData.value.activeTrip = tripData.value.activeTrip === 1 ? 2 : 1
  }

  // Reset functions only affect the ACTIVE trip
  const resetAverageSpeed = () => {
    const tripKey = tripData.value.activeTrip === 1 ? 'trip1' : 'trip2'
    tripData.value[tripKey] = {
      ...tripData.value[tripKey],
      distance: 0,
      movingTime: 0,
    }
  }

  const resetMaxSpeed = () => {
    const tripKey = tripData.value.activeTrip === 1 ? 'trip1' : 'trip2'
    tripData.value[tripKey] = {
      ...tripData.value[tripKey],
      maxSpeed: 0,
    }
  }

  const resetTripDistance = () => {
    const tripKey = tripData.value.activeTrip === 1 ? 'trip1' : 'trip2'
    tripData.value[tripKey] = {
      ...tripData.value[tripKey],
      distance: 0,
    }
  }

  // Reset active trip completely
  const resetActiveTrip = () => {
    const tripKey = tripData.value.activeTrip === 1 ? 'trip1' : 'trip2'
    tripData.value[tripKey] = { ...defaultTripData }
  }

  const resetAllTripData = () => {
    stopTripTimer()
    tripData.value = {
      trip1: { ...defaultTripData },
      trip2: { ...defaultTripData },
      activeTrip: tripData.value.activeTrip,
      totalDistance: tripData.value.totalDistance,
    }
  }

  return {
    tripData,
    activeTrip,
    activeTripData,
    avgSpeed,
    formattedTripDuration,

    updateTripData,
    startTripTimer,
    stopTripTimer,
    switchTrip,
    resetAverageSpeed,
    resetMaxSpeed,
    resetTripDistance,
    resetActiveTrip,
    resetAllTripData,
    calculateDistance,
  }
}
