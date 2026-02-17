<template>
  <div class="horizontal-tachometer" :data-shader="isMetalShader ? 'metal' : 'original'" @dblclick="!isIos && toggleTestMode()" @touchend="isIos && handleTap()">
    <div class="tft-display">
      <!-- Top Tachometer Bar -->
      <div class="tachometer-bar">
        <svg class="tachometer-svg" viewBox="0 0 800 120" preserveAspectRatio="xMidYMid meet">
          <defs>
            <!-- Enhanced glow filter for digital look -->
            <filter id="tftGlow" x="-50%" y="-50%" width="200%" height="200%">
              <feGaussianBlur stdDeviation="4" result="coloredBlur"/>
              <feMerge>
                <feMergeNode in="coloredBlur"/>
                <feMergeNode in="coloredBlur"/>
                <feMergeNode in="SourceGraphic"/>
              </feMerge>
            </filter>
            <!-- Subtle inner glow for segments -->
            <filter id="segmentGlow" x="-30%" y="-30%" width="160%" height="160%">
              <feGaussianBlur stdDeviation="2" result="blur"/>
              <feMerge>
                <feMergeNode in="blur"/>
                <feMergeNode in="SourceGraphic"/>
              </feMerge>
            </filter>
          </defs>

          <!-- Background segments - LCD off state -->
          <g class="background-segments">
            <rect
              v-for="(seg, i) in segments"
              :key="'bg-' + i"
              :x="seg.x"
              :y="seg.y"
              :width="seg.width"
              :height="seg.height"
              :transform="seg.transform"
              class="segment-bg"
              rx="1"
            />
          </g>

          <!-- Active segments with digital colors -->
          <g class="active-segments" :filter="speedPercentage > 0 ? 'url(#segmentGlow)' : ''">
            <rect
              v-for="(seg, i) in activeSegments"
              :key="'active-' + i"
              :x="seg.x"
              :y="seg.y"
              :width="seg.width"
              :height="seg.height"
              :transform="seg.transform"
              :fill="seg.color"
              rx="1"
              class="segment-active"
            />
          </g>

          <!-- Tick marks and numbers -->
          <g class="tick-marks">
            <g v-for="(tick, i) in ticks" :key="'tick-' + i">
              <line
                :x1="tick.x1"
                :y1="tick.y1"
                :x2="tick.x2"
                :y2="tick.y2"
                :stroke="tick.isActive ? tick.color : 'rgba(160, 180, 200, 0.7)'"
                :stroke-width="tick.isMajor ? 2.5 : 1.5"
                :filter="tick.isActive ? 'url(#tftGlow)' : ''"
              />
              <text
                v-if="tick.isMajor"
                :x="tick.textX"
                :y="tick.textY"
                :fill="tick.isActive ? tick.color : 'rgba(180, 200, 220, 0.85)'"
                font-size="13"
                font-weight="700"
                text-anchor="middle"
                class="tick-text"
                :filter="tick.isActive ? 'url(#tftGlow)' : ''"
              >
                {{ tick.value }}
              </text>
            </g>
          </g>

          <!-- Speed multiplier indicator text -->
          <text x="780" y="12" class="unit-indicator" text-anchor="end">
            x10 {{ unit === 'mph' ? 'mph' : 'km/h' }}
          </text>
        </svg>
      </div>

      <!-- Main Display Area - Speed Only -->
      <div class="main-display">
        <div class="speed-display">
          <span class="speed-value" :style="{ color: gaugeColor, textShadow: `0 0 40px ${gaugeColor}, 0 0 80px ${gaugeColor}40` }">
            {{ Math.round(displaySpeed) }}
          </span>
          <span class="speed-unit">{{ unit === 'mph' ? 'mph' : 'km/h' }}</span>
        </div>
      </div>

    </div>

    <div v-if="testMode" class="test-indicator">
      TEST MODE - Double-click to exit
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { Capacitor } from '@capacitor/core'
import { useSettings } from '../../composables/useSettings'
import { useGaugeColors } from '../../composables/useGaugeColors'

const isIos = Capacitor.getPlatform() === 'ios'

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
const maxSpeed = computed(() => props.unit === 'mph' ? 93 : 150)
const speedPercentage = computed(() => Math.min((displaySpeed.value / maxSpeed.value), 1))

const gaugeColor = computed(() => {
  const c = gaugeColors.value
  const percentage = speedPercentage.value
  if (percentage < 4/15) return c.zone1
  if (percentage < 7/15) return c.zone2
  if (percentage < 10/15) return c.zone3
  return c.zone4
})

const totalSegments = 60
const segments = computed(() => {
  const segs = []
  const startX = 40
  const endX = 760
  const segmentWidth = (endX - startX) / totalSegments
  const height = 30

  const bottomY = 60
  const topY = 18
  const rampEndSegment = 20

  for (let i = 0; i < totalSegments; i++) {
    const x = startX + i * segmentWidth

    let y: number
    let skewAngle: number

    if (i <= rampEndSegment) {
      const rampProgress = i / rampEndSegment
      const easedProgress = 1 - Math.pow(1 - rampProgress, 2)
      y = bottomY - (bottomY - topY) * easedProgress
      skewAngle = -12 * (1 - easedProgress)
    } else {
      y = topY
      skewAngle = 0
    }

    segs.push({
      x: x,
      y: y,
      width: segmentWidth - 2,
      height: height,
      transform: skewAngle !== 0 ? `skewX(${skewAngle})` : ''
    })
  }
  return segs
})

const activeSegments = computed(() => {
  const c = gaugeColors.value
  const activeCount = Math.floor(speedPercentage.value * totalSegments)
  return segments.value.slice(0, activeCount).map((seg, i) => {
    const percentage = i / totalSegments
    let color
    if (percentage < 4/15) color = c.zone1
    else if (percentage < 7/15) color = c.zone2
    else if (percentage < 10/15) color = c.zone3
    else color = c.zone4

    return { ...seg, color }
  })
})

const ticks = computed(() => {
  const c = gaugeColors.value
  const tickList = []
  const startX = 40
  const endX = 760
  const totalTicks = 16


  const bottomY = 60 + 30 + 2
  const topY = 18 + 30 + 2
  const rampEndTick = 5

  for (let i = 0; i < totalTicks; i++) {
    const x = startX + (i / (totalTicks - 1)) * (endX - startX)
    const isMajor = true
    const tickValue = i
    const isActive = (i / (totalTicks - 1)) <= speedPercentage.value

    let baseY: number

    if (i <= rampEndTick) {
      const rampProgress = i / rampEndTick
      const easedProgress = 1 - Math.pow(1 - rampProgress, 2)
      baseY = bottomY - (bottomY - topY) * easedProgress
    } else {
      baseY = topY
    }

    let color
    const tickMark = i
    if (tickMark < 4) color = c.zone1
    else if (tickMark < 7) color = c.zone2
    else if (tickMark < 10) color = c.zone3
    else color = c.zone4

    tickList.push({
      x1: x,
      y1: baseY,
      x2: x,
      y2: baseY + 8,
      textX: x,
      textY: baseY + 22,
      value: tickValue,
      isMajor,
      isActive,
      color
    })
  }
  return tickList
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

    testSpeed.value += 3
    if (testSpeed.value >= maxSpeed.value) {
      testSpeed.value = 0
    }
  }, 100)
}
let lastTapTime = 0
const handleTap = () => {
  const now = Date.now()
  if (now - lastTapTime < 300) {
    toggleTestMode()
  }
  lastTapTime = now
}
</script>

<style scoped>
.horizontal-tachometer {
  position: relative;
  width: 100%;
  max-width: min(100%, 700px);
  margin: 0 auto;
  cursor: pointer;
  z-index: 10;
}

.tft-display {
  padding: 0 var(--space-lg, 1.5rem);
  position: relative;
}

.tachometer-bar {
  width: 100%;
  /* Fluid height - larger in landscape, smaller in portrait */
  height: clamp(100px, 25vh, 180px);
  margin-bottom: 0;
}

/* Landscape mode - ensure taller tachometer */
@media (orientation: landscape) {
  .tachometer-bar {
    height: clamp(130px, 30vh, 180px);
  }
}

.tachometer-svg {
  width: 100%;
  height: 100%;
}

.segment-bg {
  fill: rgba(37, 43, 51, 0.6);
  stroke: rgba(90, 101, 119, 0.2);
  stroke-width: 1;
}

.segment-active {
  transition: fill 0.08s ease;
}

.tick-text {
  font-family: 'SF Mono', 'Menlo', 'Monaco', 'Consolas', monospace;
  letter-spacing: 0.5px;
}

/* SVG text uses viewBox-relative units */
.unit-indicator {
  fill: var(--metal-shine, rgba(136, 153, 170, 0.9));
  font-size: 12px;
  font-family: 'SF Mono', 'Menlo', 'Monaco', 'Consolas', monospace;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.main-display {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-top: var(--space-xs, 0.25rem);
  position: absolute;
  left: 50%;
  top: 20vh;
  transform: translateX(-50%);
}

.speed-display {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-xs, 0.25rem);
}

.speed-value {
  /* Fluid font-size: scales between portrait and landscape */
  font-size: clamp(3.5rem, 12vw, 6.25rem);
  font-weight: 700;
  line-height: 1;
  font-family: 'SF Mono', 'Menlo', 'Monaco', 'Consolas', monospace;
  letter-spacing: -0.02em;
  transition: color 0.2s ease, text-shadow 0.2s ease;
}

.speed-unit {
  font-size: clamp(0.875rem, 2.5vw, 1.25rem);
  color: var(--metal-shine, rgba(136, 153, 170, 0.9));
  font-weight: 700;
  letter-spacing: 0.125em;
  text-transform: uppercase;
  margin-top: var(--space-xs, 0.25rem);
  font-family: 'SF Mono', 'Menlo', 'Monaco', 'Consolas', monospace;
  text-shadow: 0 1px 0 rgba(0, 0, 0, 0.5);
}

.test-indicator {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  background: linear-gradient(135deg, var(--accent-red-dim, #cc1236), var(--accent-red, #ff1744));
  color: white;
  padding: var(--space-xs, 0.375rem) var(--space-md, 1rem);
  border-radius: var(--radius-sm, 0.25rem);
  font-size: var(--text-xs, 0.6875rem);
  font-weight: 700;
  font-family: 'SF Mono', 'Menlo', 'Monaco', 'Consolas', monospace;
  letter-spacing: 0.0625em;
  text-transform: uppercase;
  animation: pulse 1.5s infinite;
  box-shadow: 0 0 1.25rem var(--glow-red, rgba(255, 23, 68, 0.4));
  border: 1px solid var(--accent-red, #ff1744);
  z-index: 2;
  white-space: nowrap;
}

@keyframes pulse {
  0%, 100% { opacity: 1; box-shadow: 0 0 1.25rem var(--glow-red, rgba(255, 23, 68, 0.4)); }
  50% { opacity: 0.7; box-shadow: 0 0 1.875rem rgba(255, 23, 68, 0.6); }
}

/* Portrait mode adjustments */
@media (orientation: portrait) {
  .horizontal-tachometer {
    max-width: 100%;
  }

  .tft-display {
    padding: 0 var(--space-sm, 0.75rem);
  }

  .main-display {
    position: relative;
    left: auto;
    top: auto;
    transform: none;
    margin-top: var(--space-sm, 0.5rem);
  }

  .test-indicator {
    bottom: clamp(-2.5rem, -5vh, -1.5rem);
  }
}

/* Landscape mode adjustments */
@media (orientation: landscape) {
  .horizontal-tachometer {
    max-width: 100%;
  }
}

/* ============ ORIGINAL STYLE (Metal Shader OFF) ============ */
.horizontal-tachometer[data-shader="original"] .segment-bg {
  fill: rgba(255, 255, 255, 0.06);
  stroke: rgba(255, 255, 255, 0.08);
}

.horizontal-tachometer[data-shader="original"] .unit-indicator {
  fill: rgba(156, 163, 175, 0.9);
}

.horizontal-tachometer[data-shader="original"] .speed-unit {
  color: rgba(156, 163, 175, 0.9);
  text-shadow: none;
}

.horizontal-tachometer[data-shader="original"] .tick-text {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', sans-serif;
}

.horizontal-tachometer[data-shader="original"] .speed-value {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', sans-serif;
}
</style>
