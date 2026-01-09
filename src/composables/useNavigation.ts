import { ref } from 'vue'

// Shared navigation state
const isNavigating = ref(false)
const totalDistance = ref(0)
const remainingDistance = ref(0)
const destination = ref<string>('')

export function useNavigation() {
  const startNavigation = (dest: string, totalDist: number) => {
    isNavigating.value = true
    destination.value = dest
    totalDistance.value = totalDist
    remainingDistance.value = totalDist
  }

  const updateRemainingDistance = (distance: number) => {
    remainingDistance.value = distance
  }

  const stopNavigation = () => {
    isNavigating.value = false
    totalDistance.value = 0
    remainingDistance.value = 0
    destination.value = ''
  }

  return {
    isNavigating,
    totalDistance,
    remainingDistance,
    destination,
    startNavigation,
    updateRemainingDistance,
    stopNavigation
  }
}
