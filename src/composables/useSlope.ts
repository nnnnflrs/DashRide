import { ref, computed } from 'vue'

const currentSlope = ref(0)
const slopeHistory = ref<number[]>([])
const lastPosition = ref<{ lat: number; lng: number; altitude: number; timestamp: number } | null>(null)

const MIN_DISTANCE = 7 // meters - minimum distance between points
const MIN_SPEED_KMH = 7 // km/h - minimum speed to update slope
const MAX_SLOPE = 25 // % - clamp slope to realistic range
const MIN_SLOPE = -25 // % - clamp slope to realistic range
const HISTORY_SIZE = 5 // number of samples for moving average
const MAX_GPS_ACCURACY = 10 // meters - only update if GPS accuracy is better than this

export function useSlope() {
  const calculateDistance = (lat1: number, lng1: number, lat2: number, lng2: number): number => {
    const R = 6371e3 // Earth's radius in meters
    const φ1 = lat1 * Math.PI / 180
    const φ2 = lat2 * Math.PI / 180
    const Δφ = (lat2 - lat1) * Math.PI / 180
    const Δλ = (lng2 - lng1) * Math.PI / 180

    const a = Math.sin(Δφ / 2) * Math.sin(Δφ / 2) +
              Math.cos(φ1) * Math.cos(φ2) *
              Math.sin(Δλ / 2) * Math.sin(Δλ / 2)
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

    return R * c
  }

  const clamp = (value: number, min: number, max: number): number => {
    return Math.max(min, Math.min(max, value))
  }

  const calculateMovingAverage = (history: number[]): number => {
    if (history.length === 0) return 0
    const sum = history.reduce((acc, val) => acc + val, 0)
    return sum / history.length
  }


  const updateSlope = (
    lat: number,
    lng: number,
    altitude: number,
    speed: number,
    accuracy?: number
  ) => {
    if (speed < MIN_SPEED_KMH) {
      return
    }

    if (accuracy !== undefined && accuracy > MAX_GPS_ACCURACY) {
      return
    }

    if (!lastPosition.value) {
      lastPosition.value = {
        lat,
        lng,
        altitude,
        timestamp: Date.now()
      }
      return
    }

    const distance = calculateDistance(
      lastPosition.value.lat,
      lastPosition.value.lng,
      lat,
      lng
    )

    if (distance < MIN_DISTANCE) {
      return
    }

    const altitudeDelta = altitude - lastPosition.value.altitude
    const slopePercent = (altitudeDelta / distance) * 100
    const clampedSlope = clamp(slopePercent, MIN_SLOPE, MAX_SLOPE)

    slopeHistory.value.push(clampedSlope)
    if (slopeHistory.value.length > HISTORY_SIZE) {
      slopeHistory.value.shift()
    }

    currentSlope.value = calculateMovingAverage(slopeHistory.value)

    lastPosition.value = {
      lat,
      lng,
      altitude,
      timestamp: Date.now()
    }
  }

  const resetSlope = () => {
    currentSlope.value = 0
    slopeHistory.value = []
    lastPosition.value = null
  }

 
  const formattedSlope = computed(() => {
    const slope = currentSlope.value
    if (Math.abs(slope) < 0.1) return '0.0%'
    const sign = slope > 0 ? '+' : ''
    return `${sign}${slope.toFixed(1)}%`
  })

  
  const slopeDirection = computed(() => {
    if (currentSlope.value > 0.5) return 'uphill'
    if (currentSlope.value < -0.5) return 'downhill'
    return 'flat'
  })

  return {
    currentSlope,
    formattedSlope,
    slopeDirection,
    updateSlope,
    resetSlope
  }
}
