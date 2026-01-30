import { ref, computed } from 'vue'

const isNavigating = ref(false)
const totalDistance = ref(0)
const remainingDistance = ref(0)
const destination = ref<string>('')
const estimatedTimeSeconds = ref(0) 
const startTime = ref<number>(0)
const routePath = ref<Array<{ lat: number; lng: number }>>([])
const routeSteps = ref<Array<any>>([])
const nextTurnInstruction = ref<string>('')

export function useNavigation() {
  const startNavigation = (dest: string, totalDist: number, etaSeconds: number, steps?: Array<any>) => {
    isNavigating.value = true
    destination.value = dest
    totalDistance.value = totalDist
    remainingDistance.value = totalDist
    estimatedTimeSeconds.value = etaSeconds
    startTime.value = Date.now()

    if (steps) {
      routeSteps.value = steps
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
  
    const nextStep = routeSteps.value.find((step: any) => {
      return true 
    })

    if (nextStep && nextStep.html_instructions) {
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

  const formattedETA = computed(() => {
    if (!estimatedTimeSeconds.value || estimatedTimeSeconds.value <= 0) return '--:-- --'

    const now = Date.now()
    const arrivalTime = now + (estimatedTimeSeconds.value * 1000)
    const date = new Date(arrivalTime)

    return date.toLocaleTimeString('en-US', {
      hour: 'numeric',
      minute: '2-digit',
      hour12: true
    })
  })

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
