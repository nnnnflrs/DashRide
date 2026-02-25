import { computed } from 'vue'
import { useSettings } from './useSettings'

const GAUGE_COLORS: Record<string, { zone1: string; zone2: string; zone3: string; zone4: string }> = {
  'hiraya':          { zone1: '#00ff88', zone2: '#ffb800', zone3: '#ff6b35', zone4: '#ff1744' },
  sinta:             { zone1: '#ff1744', zone2: '#ff5722', zone3: '#ff8f00', zone4: '#ffb800' },
  dalisay:           { zone1: '#00ffd5', zone2: '#00bcd4', zone3: '#ff6b35', zone4: '#ff1744' },
  tanglaw:           { zone1: '#ffd700', zone2: '#ffb300', zone3: '#ff8f00', zone4: '#ff1744' },
  tadhana:           { zone1: '#a78bfa', zone2: '#818cf8', zone3: '#c084fc', zone4: '#f43f5e' },
  bighani:           { zone1: '#bf00ff', zone2: '#e040fb', zone3: '#ff6b35', zone4: '#ff1744' },
  marilag:           { zone1: '#e0f0ff', zone2: '#90caf9', zone3: '#ff8a65', zone4: '#ff1744' },
  gabay:             { zone1: '#4db6ac', zone2: '#ffb300', zone3: '#ff6b35', zone4: '#ff1744' },
  hiwaga:            { zone1: '#448aff', zone2: '#82b1ff', zone3: '#ff8a65', zone4: '#ff1744' },
  malaya:            { zone1: '#ff6d00', zone2: '#ffab40', zone3: '#ff6b35', zone4: '#ff1744' },
  panalangin:        { zone1: '#fbbf24', zone2: '#f59e0b', zone3: '#ff6b35', zone4: '#ff1744' },
  'pag-asa':         { zone1: '#f472b6', zone2: '#f9a8d4', zone3: '#ff8a65', zone4: '#ff1744' },
}

export function useGaugeColors() {
  const { accentTheme } = useSettings()

  const colors = computed(() => {
    return GAUGE_COLORS[accentTheme.value] || GAUGE_COLORS['hiraya']
  })

  return { colors }
}
