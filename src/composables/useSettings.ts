import { useLocalStorage } from '@vueuse/core'

// Shared settings state
export function useSettings() {
  const theme = useLocalStorage<'auto' | 'light' | 'dark'>('dashride_theme', 'dark')
  const unit = useLocalStorage<'mph' | 'kmh'>('dashride_speedUnit', 'kmh')
  const keepScreenOn = useLocalStorage('dashride_keepScreenOn', true)
  const avoidTolls = useLocalStorage('dashride_avoidTolls', true)
  const showMinimap = useLocalStorage('dashride_showMinimap', true)
  const mapStyle = useLocalStorage<'dark' | 'light'>('dashride_mapStyle', 'light')
  const showDetailsOnNavigation = useLocalStorage('dashride_showDetailsOnNavigation', true)
  const voiceInstructions = useLocalStorage('dashride_voiceInstructions', true)
  const showStatusBar = useLocalStorage('dashride_showStatusBar', false)
  const gaugeSkin = useLocalStorage<'circular' | 'horizontal'>('dashride_gaugeSkin', 'circular')

  return {
    theme,
    unit,
    keepScreenOn,
    avoidTolls,
    showMinimap,
    mapStyle,
    showDetailsOnNavigation,
    voiceInstructions,
    showStatusBar,
    gaugeSkin
  }
}
