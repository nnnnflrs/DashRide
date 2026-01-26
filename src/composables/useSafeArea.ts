import { ref, computed, onMounted, onUnmounted } from 'vue'
import { Capacitor } from '@capacitor/core'
import { SafeArea } from 'capacitor-plugin-safe-area'

const NAVBAR_THRESHOLD = 30

const bottomInset = ref(0)
const topInset = ref(0)
const leftInset = ref(0)
const rightInset = ref(0)
const isNavigationBarVisible = ref(false)
let isInitialized = false

export function useSafeArea() {
  const getSafeAreaInsets = async () => {
    if (!Capacitor.isNativePlatform()) return

    try {
      const safeAreaData = await SafeArea.getSafeAreaInsets()

      topInset.value = safeAreaData.insets.top
      bottomInset.value = safeAreaData.insets.bottom
      leftInset.value = safeAreaData.insets.left
      rightInset.value = safeAreaData.insets.right

      const isGestureNavigation = safeAreaData.insets.bottom > 15 && safeAreaData.insets.bottom < 30

      const hasSignificantInset = isGestureNavigation
        ? false
        : (safeAreaData.insets.bottom > NAVBAR_THRESHOLD ||
           safeAreaData.insets.left > NAVBAR_THRESHOLD ||
           safeAreaData.insets.right > NAVBAR_THRESHOLD)

      isNavigationBarVisible.value = hasSignificantInset
    } catch (error) {
      console.error('[SafeArea] Failed to get safe area insets:', error)

      const testDiv = document.createElement('div')
      testDiv.style.position = 'fixed'
      testDiv.style.bottom = '0'
      testDiv.style.paddingBottom = 'env(safe-area-inset-bottom, 0px)'
      document.body.appendChild(testDiv)

      const computedPadding = parseInt(window.getComputedStyle(testDiv).paddingBottom)
      document.body.removeChild(testDiv)

      if (computedPadding > 0) {
        bottomInset.value = computedPadding
        isNavigationBarVisible.value = true
      }
    }
  }

  const updateSafeArea = () => {
    setTimeout(getSafeAreaInsets, 100)
  }

  onMounted(() => {
    if (isInitialized) return

    isInitialized = true
    getSafeAreaInsets()

    window.addEventListener('orientationchange', updateSafeArea)
    window.addEventListener('resize', updateSafeArea)

    if (Capacitor.isNativePlatform()) {
      SafeArea.addListener('safeAreaChanged', (data) => {
        topInset.value = data.insets.top
        bottomInset.value = data.insets.bottom
        leftInset.value = data.insets.left
        rightInset.value = data.insets.right

        const isGestureNavigation = data.insets.bottom > 15 && data.insets.bottom < 30

        const hasSignificantInset = isGestureNavigation
          ? false
          : (data.insets.bottom > NAVBAR_THRESHOLD ||
             data.insets.left > NAVBAR_THRESHOLD ||
             data.insets.right > NAVBAR_THRESHOLD)

        isNavigationBarVisible.value = hasSignificantInset
      })
    }
  })

  onUnmounted(() => {
    window.removeEventListener('orientationchange', updateSafeArea)
    window.removeEventListener('resize', updateSafeArea)

    if (Capacitor.isNativePlatform()) {
      SafeArea.removeAllListeners()
    }
  })

  const navBarPosition = computed(() => {
    if (!isNavigationBarVisible.value) return 'none'

    const maxInset = Math.max(bottomInset.value, leftInset.value, rightInset.value, topInset.value)

    if (maxInset < NAVBAR_THRESHOLD) return 'none'
    if (bottomInset.value === maxInset) return 'bottom'
    if (leftInset.value === maxInset) return 'left'
    if (rightInset.value === maxInset) return 'right'
    if (topInset.value === maxInset) return 'top'

    return 'none'
  })

  return {
    bottomInset,
    topInset,
    leftInset,
    rightInset,
    isNavigationBarVisible,
    navBarPosition
  }
}
