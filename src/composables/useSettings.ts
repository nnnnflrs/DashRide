import { computed } from 'vue'
import { useLocalStorage } from '@vueuse/core'

export type AccentTheme = 'pag-asa' | 'hiraya' | 'sinta' | 'dalisay' | 'tanglaw' | 'tadhana' | 'bighani' | 'marilag' | 'gabay' | 'hiwaga' | 'malaya' | 'panalangin'

// Migration: boolean -> string (v1), old accent names -> new names (v2)
const rawValue = localStorage.getItem('metalShader')
if (rawValue === 'true') {
  localStorage.setItem('metalShader', '"hiraya"')
} else if (rawValue === 'false') {
  localStorage.setItem('metalShader', '"pag-asa"')
} else if (rawValue) {
  const nameMigrations: Record<string, string> = {
    '"hiraya"': '"hiraya"',
    '"silakbo"': '"sinta"',
    '"diwa"': '"dalisay"',
    '"mutya"': '"tanglaw"',
    '"panimdim"': '"tadhana"',
    '"pusali"': '"gabay"',
    '"original"': '"pag-asa"',
  }
  if (nameMigrations[rawValue]) {
    localStorage.setItem('metalShader', nameMigrations[rawValue])
  }
}

export function useSettings() {
  const theme = useLocalStorage<'auto' | 'light' | 'dark'>('theme', 'dark')
  const unit = useLocalStorage<'mph' | 'kmh'>('speedUnit', 'kmh')
  const keepScreenOn = useLocalStorage('keepScreenOn', true)
  const avoidTolls = useLocalStorage('avoidTolls', true)
  const showMinimap = useLocalStorage('showMinimap', true)
  const mapStyle = useLocalStorage<'dark' | 'light'>('mapStyle', 'light')
  const showDetailsOnNavigation = useLocalStorage('showDetailsOnNavigation', true)
  const voiceInstructions = useLocalStorage('voiceInstructions', true)
  const showStatusBar = useLocalStorage('showStatusBar', false)
  const gaugeSkin = useLocalStorage<'circular' | 'horizontal'>('gaugeSkin', 'horizontal')
  const gpsAccuracyFilter = useLocalStorage('gpsAccuracyFilter', true)
  const metalShader = useLocalStorage<AccentTheme>('metalShader', 'hiraya')
  const isMetalShader = computed(() => metalShader.value !== 'pag-asa')

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
    gaugeSkin,
    gpsAccuracyFilter,
    metalShader,
    isMetalShader
  }
}
