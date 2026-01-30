import { ref, computed } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import type { TripData } from '../types'
import { useSettings } from './useSettings'

let tripInterval: number | null = null

export function useTripManager() {
  const { unit } = useSettings()

  const tripData = useLocalStorage<TripData>('dashride_tripData', {
    distance: 0,
    duration: 0,
    maxSpeed: 0,
    totalSpeed: 0,
    speedSamples: 0,
    totalDistance: 230,
  })


  const avgSpeed = computed(() =>
    tripData.value.speedSamples > 0 ? tripData.value.totalSpeed / tripData.value.speedSamples : 0
  )


  const formattedTripDuration = computed(() => {
    const hours = Math.floor(tripData.value.duration / 3600)
    const minutes = Math.floor((tripData.value.duration % 3600) / 60)
    const seconds = tripData.value.duration % 60
    return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`
  })

  const calculateDistance = (lat1: number, lon1: number, lat2: number, lon2: number): number => {
    const R = unit.value === 'mph' ? 3958.8 : 6371 // Earth radius in miles or km
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

  const updateTripData = (
    currentSpeed: number,
    currentPosition: { latitude: number; longitude: number } | null,
    lastPosition: { latitude: number; longitude: number } | null
  ) => {
    tripData.value = {
      ...tripData.value,
      maxSpeed: Math.max(tripData.value.maxSpeed, currentSpeed),
      totalSpeed: tripData.value.totalSpeed + currentSpeed,
      speedSamples: tripData.value.speedSamples + 1,
    }

    if (lastPosition && currentPosition && currentSpeed > 0.5) {
      const distance = calculateDistance(
        lastPosition.latitude,
        lastPosition.longitude,
        currentPosition.latitude,
        currentPosition.longitude
      )

      tripData.value = {
        ...tripData.value,
        distance: tripData.value.distance + distance,
      }
    }
  }

  const startTripTimer = () => {
    if (tripInterval !== null) return

    tripInterval = window.setInterval(() => {
      tripData.value = {
        ...tripData.value,
        duration: tripData.value.duration + 1,
      }
    }, 1000)
  }

  const stopTripTimer = () => {
    if (tripInterval !== null) {
      clearInterval(tripInterval)
      tripInterval = null
    }
  }


  const resetAverageSpeed = () => {
    tripData.value = {
      ...tripData.value,
      totalSpeed: 0,
      speedSamples: 0
    }
  }

  const resetMaxSpeed = () => {
    tripData.value = {
      ...tripData.value,
      maxSpeed: 0
    }
  }


  const resetTripDistance = () => {
    tripData.value = {
      ...tripData.value,
      distance: 0
    }
  }


  const resetAllTripData = () => {
    stopTripTimer()
    tripData.value = {
      distance: 0,
      duration: 0,
      maxSpeed: 0,
      totalSpeed: 0,
      speedSamples: 0,
      totalDistance: tripData.value.totalDistance,
    }
  }

  return {
    tripData,
    avgSpeed,
    formattedTripDuration,

    updateTripData,
    startTripTimer,
    stopTripTimer,
    resetAverageSpeed,
    resetMaxSpeed,
    resetTripDistance,
    resetAllTripData,
    calculateDistance,
  }
}
