import { ref, computed } from 'vue'

// Shared navigation state
const isNavigating = ref(false)
const totalDistance = ref(0)
const remainingDistance = ref(0)
const destination = ref<string>('')
const estimatedTimeSeconds = ref(0) // ETA in seconds
const startTime = ref<number>(0) // Start timestamp
const routePath = ref<Array<{ lat: number; lng: number }>>([]) // Current route path
const routeSteps = ref<Array<any>>([]) // Turn-by-turn steps from Google Directions API
const nextTurnInstruction = ref<string>('') // Next turn instruction

export function useNavigation() {
  const startNavigation = (dest: string, totalDist: number, etaSeconds: number, steps?: Array<any>) => {
    isNavigating.value = true
    destination.value = dest
    totalDistance.value = totalDist
    remainingDistance.value = totalDist
    estimatedTimeSeconds.value = etaSeconds
    startTime.value = Date.now()

    // Store route steps for turn-by-turn navigation
    if (steps) {
      routeSteps.value = steps
      // Set first instruction
      updateNextTurnInstruction()
    }
  }

  const updateRemainingDistance = (distance: number) => {
    remainingDistance.value = distance
  }

  const updateEstimatedTime = (etaSeconds: number) => {
    estimatedTimeSeconds.value = etaSeconds
  }

  const updateRoutePath = (path: Array<{ lat: number; lng: number }>) => {
    routePath.value = path
  }

  const updateNextTurnInstruction = (currentLocation?: { lat: number; lng: number }) => {
    if (!routeSteps.value || routeSteps.value.length === 0) {
      nextTurnInstruction.value = ''
      return
    }

    // Find the next step based on current location
    // For now, use the first step with remaining distance
    // In a full implementation, you'd calculate which step is closest ahead
    const nextStep = routeSteps.value.find((step: any) => {
      // Simple heuristic: find steps that haven't been passed yet
      // This is simplified - production would need proper distance calculations
      return true // Return first step for now
    })

    if (nextStep && nextStep.html_instructions) {
      // Strip HTML tags from instruction
      const instruction = nextStep.html_instructions.replace(/<[^>]*>/g, ' ').trim()
      nextTurnInstruction.value = instruction
    }
  }

  const stopNavigation = () => {
    isNavigating.value = false
    totalDistance.value = 0
    remainingDistance.value = 0
    destination.value = ''
    estimatedTimeSeconds.value = 0
    startTime.value = 0
    routePath.value = []
    routeSteps.value = []
    nextTurnInstruction.value = ''
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
    routePath,
    routeSteps,
    nextTurnInstruction,
    formattedETA,
    elapsedTime,
    startNavigation,
    updateRemainingDistance,
    updateEstimatedTime,
    updateRoutePath,
    updateNextTurnInstruction,
    stopNavigation
  }
}
