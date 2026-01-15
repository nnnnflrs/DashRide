import { ref, computed } from 'vue'

// Shared navigation state
const isNavigating = ref(false)
const totalDistance = ref(0)
const remainingDistance = ref(0)
const destination = ref<string>('')
const estimatedTimeSeconds = ref(0) // ETA in seconds
const startTime = ref<number>(0) // Start timestamp

export function useNavigation() {
  const startNavigation = (dest: string, totalDist: number, etaSeconds: number) => {
    isNavigating.value = true
    destination.value = dest
    totalDistance.value = totalDist
    remainingDistance.value = totalDist
    estimatedTimeSeconds.value = etaSeconds
    startTime.value = Date.now()
  }

  const updateRemainingDistance = (distance: number) => {
    remainingDistance.value = distance
  }

  const updateEstimatedTime = (etaSeconds: number) => {
    estimatedTimeSeconds.value = etaSeconds
  }

  const stopNavigation = () => {
    isNavigating.value = false
    totalDistance.value = 0
    remainingDistance.value = 0
    destination.value = ''
    estimatedTimeSeconds.value = 0
    startTime.value = 0
  }

  // Computed ETA as formatted time (12-hour format with AM/PM)
  const formattedETA = computed(() => {
    if (!estimatedTimeSeconds.value || estimatedTimeSeconds.value <= 0) return '--:-- --'

    const now = Date.now()
    const arrivalTime = now + (estimatedTimeSeconds.value * 1000)
    const date = new Date(arrivalTime)

    // Format as 12-hour time with AM/PM
    return date.toLocaleTimeString('en-US', {
      hour: 'numeric',
      minute: '2-digit',
      hour12: true
    })
  })

  // Elapsed time since navigation started
  const elapsedTime = computed(() => {
    if (!startTime.value) return 0
    return Math.floor((Date.now() - startTime.value) / 1000)
  })

  return {
    isNavigating,
    totalDistance,
    remainingDistance,
    destination,
    estimatedTimeSeconds,
    formattedETA,
    elapsedTime,
    startNavigation,
    updateRemainingDistance,
    updateEstimatedTime,
    stopNavigation
  }
}
