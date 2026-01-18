import { useLocalStorage } from '@vueuse/core'

// Shared settings state
export function useSettings() {
  const theme = useLocalStorage<'auto' | 'light' | 'dark'>('theme', 'dark')
  const unit = useLocalStorage<'mph' | 'kmh'>('speedUnit', 'kmh')
  const keepScreenOn = useLocalStorage('keepScreenOn', true)
  const avoidTolls = useLocalStorage('avoidTolls', true)
  const showMinimap = useLocalStorage('showMinimap', true)
  const mapStyle = useLocalStorage<'dark' | 'light'>('mapStyle', 'light')
  const showDetailsOnNavigation = useLocalStorage('showDetailsOnNavigation', true)
  const voiceInstructions = useLocalStorage('voiceInstructions', true)

  return {
    theme,
    unit,
    keepScreenOn,
    avoidTolls,
    showMinimap,
    mapStyle,
    showDetailsOnNavigation,
    voiceInstructions
  }
}
