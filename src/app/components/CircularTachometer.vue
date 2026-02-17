<template>
  <div class="gauge-container" :data-shader="isMetalShader ? 'metal' : 'original'" @dblclick="toggleTestMode" @touchend="handleTap" >
    <svg class="gauge-svg" viewBox="0 0 300 180" :style="{ filter: gaugeGlow }">
      <defs>
        <!-- Chrome ring gradient - polished metal -->
        <linearGradient id="chromeRing" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stop-color="#3d4f5f" />
          <stop offset="20%" stop-color="#90a4ae" />
          <stop offset="40%" stop-color="#607080" />
          <stop offset="60%" stop-color="#cfd8dc" />
          <stop offset="80%" stop-color="#607080" />
          <stop offset="100%" stop-color="#3d4f5f" />
        </linearGradient>
        <!-- Radial highlight for chrome ring -->
        <radialGradient id="chromeHighlight" cx="30%" cy="30%" r="70%">
          <stop offset="0%" stop-color="rgba(255,255,255,0.12)" />
          <stop offset="100%" stop-color="rgba(255,255,255,0)" />
        </radialGradient>
        <!-- Glow filters for LED effect -->
        <filter id="cyanGlow" x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur stdDeviation="2" result="blur" />
          <feMerge>
            <feMergeNode in="blur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
        <!-- Ambient bloom filter -->
        <filter id="ambientBloom" x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur stdDeviation="4" result="bloom" />
          <feMerge>
            <feMergeNode in="bloom" />
            <feMergeNode in="bloom" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>

      <!-- Chrome ring background - polished bezel (metal shader only) -->
      <template v-if="isMetalShader">
        <path
          d="M 30 150 A 120 120 0 0 1 270 150"
          fill="none"
          stroke="url(#chromeRing)"
          stroke-width="22"
          stroke-linecap="butt"
          opacity="0.6"
        />
        <!-- Chrome ring highlight -->
        <path
          d="M 30 150 A 120 120 0 0 1 270 150"
          fill="none"
          stroke="url(#chromeHighlight)"
          stroke-width="22"
          stroke-linecap="butt"
        />
      </template>
      <!-- Background Arc (recessed track) -->
      <path
        d="M 30 150 A 120 120 0 0 1 270 150"
        fill="none"
        stroke="rgba(255, 255, 255, 0.04)"
        stroke-width="18"
        stroke-linecap="butt"
      />
      
      <!-- Value Arc with dynamic color -->
      <path
        :d="valueArcPath"
        fill="none"
        :stroke="gaugeColor"
        stroke-width="18"
        stroke-linecap="butt"
        class="value-path"
        :style="{ filter: `drop-shadow(0 0 10px ${gaugeColor})` }"
      />
      
      <!-- Tick Marks (outside the arc) -->
      <g v-for="(mark, index) in marks" :key="index">
        <line
          :x1="mark.x1"
          :y1="mark.y1"
          :x2="mark.x2"
          :y2="mark.y2"
          :stroke="mark.value === 220 ? '#ff0a4a' : (mark.isActive ? gaugeColor : 'rgba(255, 255, 255, 0.4)')"
          :stroke-width="mark.isMajor ? 2.5 : 1"
          stroke-linecap="round"
          class="tick-mark"
        />
        <text
          v-if="mark.isMajor"
          :x="mark.textX"
          :y="mark.textY"
          :fill="mark.value === 220 ? '#ff0a4a' : (mark.isActive ? gaugeColor : 'rgba(255, 255, 255, 0.6)')"
          font-size="13"
          text-anchor="middle"
          dominant-baseline="middle"
          class="mark-text"
          :class="{ 'active-mark': mark.isActive, 'red-mark': mark.value === 220 }"
        >
          {{ mark.value }}
        </text>
      </g>
      
      <!-- Speed Text -->
      <text
        x="150"
        y="120"
        text-anchor="middle"
        class="speed-text"
        :fill="gaugeColor"
        :style="{ filter: `drop-shadow(0 0 15px ${gaugeColor})` }"
      >
        {{ Math.round(displaySpeed) }}
      </text>
      <text x="150" y="145" text-anchor="middle" class="unit-text">
        {{ unit === 'mph' ? 'MPH' : 'km/h' }}
      </text>
    </svg>
    
    <!-- Test mode indicator -->
    <div v-if="testMode" class="test-indicator">
      TEST MODE - Double-click to exit
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useSettings } from '../../composables/useSettings'
import { useGaugeColors } from '../../composables/useGaugeColors'

interface Props {
  speed: number
  unit: 'mph' | 'kmh'
}

const props = defineProps<Props>()
const { isMetalShader } = useSettings()
const { colors: gaugeColors } = useGaugeColors()

const testMode = ref(false)
const testSpeed = ref(0)

const displaySpeed = computed(() => testMode.value ? testSpeed.value : props.speed)

const maxSpeed = computed(() => props.unit === 'mph' ? 140 : 220)
const speedPercentage = computed(() => Math.min((displaySpeed.value / maxSpeed.value), 1))

let lastTapTime = 0
const handleTap = () => {
  const now = Date.now()
  if (now - lastTapTime < 300) {
    toggleTestMode()
  }
  lastTapTime = now
}

// Color based on actual speed value (km/h)
const gaugeColor = computed(() => {
  const c = gaugeColors.value
  const percentage = speedPercentage.value
  if (percentage < 4/15) return c.zone1
  if (percentage < 7/15) return c.zone2
  if (percentage < 10/15) return c.zone3
  return c.zone4
})

const gaugeGlow = computed(() => {
  return `drop-shadow(0 0 15px ${gaugeColor.value}40)`
})

const valueArcPath = computed(() => {
  const r = 120
  const cx = 150
  const cy = 150
  
  const startRad = Math.PI
  const endRad = Math.PI + (speedPercentage.value * Math.PI)
  
  const endX = cx + r * Math.cos(endRad)
  const endY = cy + r * Math.sin(endRad)
  
  const largeArcFlag = 0
  
  return `M 30 150 A ${r} ${r} 0 ${largeArcFlag} 1 ${endX} ${endY}`
})

const majorMarks = computed(() =>
  props.unit === 'mph'
    ? [0, 20, 40, 60, 80, 100, 120, 140]
    : [0, 20, 40, 60, 80, 100, 120, 140, 160, 180, 200, 220]
)

const marks = computed(() => {
  const result = []
  const markCount = majorMarks.value.length
  const cx = 150
  const cy = 150
  const radius = 120
  
  for (let i = 0; i < markCount; i++) {
    // Start at PI (180 deg, left) and go to 0 (right) over 180 degrees
    const angle = Math.PI + (i / (markCount - 1)) * Math.PI
    const x1 = cx + radius * Math.cos(angle)
    const y1 = cy + radius * Math.sin(angle)
    const x2 = cx + (radius - 15) * Math.cos(angle)
    const y2 = cy + (radius - 15) * Math.sin(angle)
    const textX = cx + (radius - 30) * Math.cos(angle)
    const textY = cy + (radius - 30) * Math.sin(angle)
    
    const markValue = majorMarks.value[i]
    const isActive = displaySpeed.value >= markValue
    
    result.push({
      value: markValue,
      isMajor: true,
      isActive,
      x1,
      y1,
      x2,
      y2,
      textX,
      textY,
    })
  }
  
  return result
})

const toggleTestMode = () => {
  testMode.value = !testMode.value
  if (testMode.value) {
    testSpeed.value = 0
    animateTestSpeed()
  }
}

const animateTestSpeed = () => {
  if (!testMode.value) return
  
  const interval = setInterval(() => {
    if (!testMode.value) {
      clearInterval(interval)
      return
    }
    
    testSpeed.value += 5
    if (testSpeed.value >= maxSpeed.value) {
      testSpeed.value = 0
    }
  }, 100)
}
</script>

<style scoped>
.gauge-container {
  position: relative;
  width: 100%;
  max-width: var(--gauge-max-width, min(100%, 600px));
  aspect-ratio: 5/3;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.gauge-svg {
  width: 100%;
  height: 100%;
  transition: filter 0.3s ease;
}

.mark-text {
  font-weight: 600;
  font-family: 'SF Mono', 'Menlo', 'Monaco', 'Consolas', monospace;
  transition: fill 0.3s ease;
}

.active-mark {
  font-weight: 700;
}

.tick-mark {
  transition: stroke 0.3s ease;
}

/* Speed text - laser-etched into chrome */
.speed-text {
  font-size: 55px;
  font-weight: 700;
  font-family: 'SF Mono', 'Menlo', 'Monaco', 'Consolas', monospace;
  transition: fill 0.3s ease, filter 0.3s ease;
}

.unit-text {
  font-size: 16px;
  fill: var(--metal-shine, rgba(136, 153, 170, 1));
  font-weight: 600;
  font-family: 'SF Mono', 'Menlo', 'Monaco', 'Consolas', monospace;
  letter-spacing: 0.1em;
}

.value-path {
  transition: d 0.3s ease-out, stroke 0.3s ease, filter 0.3s ease;
}

.test-indicator {
  position: absolute;
  bottom: var(--space-sm, 0.5rem);
  left: 50%;
  transform: translateX(-50%);
  background: linear-gradient(135deg, var(--accent-red-dim, #cc1236), var(--accent-red, #ff1744));
  color: white;
  padding: var(--space-xs, 0.25rem) var(--space-sm, 0.75rem);
  border-radius: var(--radius-lg, 0.75rem);
  font-size: var(--text-xs, 0.75rem);
  font-weight: 700;
  font-family: 'SF Mono', 'Menlo', 'Monaco', 'Consolas', monospace;
  letter-spacing: 0.05em;
  animation: pulse 2s infinite;
  z-index: 2;
  white-space: nowrap;
  box-shadow: 0 0 12px var(--glow-red, rgba(255, 23, 68, 0.4));
  border: 1px solid var(--accent-red, #ff1744);
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
    box-shadow: 0 0 12px var(--glow-red, rgba(255, 23, 68, 0.4));
  }
  50% {
    opacity: 0.7;
    box-shadow: 0 0 20px var(--glow-red, rgba(255, 23, 68, 0.6));
  }
}

/* ============ ORIGINAL STYLE (Metal Shader OFF) ============ */
.gauge-container[data-shader="original"] .mark-text {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', sans-serif;
}

.gauge-container[data-shader="original"] .speed-text {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', sans-serif;
}

.gauge-container[data-shader="original"] .unit-text {
  fill: rgba(156, 163, 175, 0.9);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', sans-serif;
}
</style>
