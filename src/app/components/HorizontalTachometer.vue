<template>
  <div class="horizontal-tachometer" @dblclick="toggleTestMode">
    <!-- TFT Display Container -->
    <div class="tft-display">
      <!-- Top Tachometer Bar -->
      <div class="tachometer-bar">
        <svg class="tachometer-svg" viewBox="0 0 800 95" preserveAspectRatio="xMidYMid meet">
          <defs>
            <!-- Gradient for the active bar -->
            <linearGradient id="tachGradient" x1="0%" y1="0%" x2="100%" y2="0%">
              <stop offset="0%" stop-color="#22c55e" />
              <stop offset="26.7%" stop-color="#22c55e" />
              <stop offset="26.7%" stop-color="#eab308" />
              <stop offset="46.7%" stop-color="#eab308" />
              <stop offset="46.7%" stop-color="#f97316" />
              <stop offset="60%" stop-color="#f97316" />
              <stop offset="60%" stop-color="#ef4444" />
              <stop offset="100%" stop-color="#ef4444" />
            </linearGradient>
            <!-- Glow filter -->
            <filter id="glow" x="-20%" y="-20%" width="140%" height="140%">
              <feGaussianBlur stdDeviation="3" result="coloredBlur"/>
              <feMerge>
                <feMergeNode in="coloredBlur"/>
                <feMergeNode in="SourceGraphic"/>
              </feMerge>
            </filter>
          </defs>

          <!-- Background segments -->
          <g class="background-segments">
            <rect
              v-for="(seg, i) in segments"
              :key="'bg-' + i"
              :x="seg.x"
              :y="seg.y"
              :width="seg.width"
              :height="seg.height"
              :transform="seg.transform"
              fill="rgba(255, 255, 255, 0.08)"
              rx="2"
            />
          </g>

          <!-- Active segments with color -->
          <g class="active-segments" :filter="speedPercentage > 0 ? 'url(#glow)' : ''">
            <rect
              v-for="(seg, i) in activeSegments"
              :key="'active-' + i"
              :x="seg.x"
              :y="seg.y"
              :width="seg.width"
              :height="seg.height"
              :transform="seg.transform"
              :fill="seg.color"
              rx="2"
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
                :stroke="tick.isActive ? tick.color : 'rgba(255, 255, 255, 0.4)'"
                :stroke-width="tick.isMajor ? 2 : 1"
              />
              <text
                v-if="tick.isMajor"
                :x="tick.textX"
                :y="tick.textY"
                :fill="tick.isActive ? tick.color : 'rgba(255, 255, 255, 0.5)'"
                font-size="12"
                font-weight="600"
                text-anchor="middle"
                class="tick-text"
              >
                {{ tick.value }}
              </text>
            </g>
          </g>

          <!-- Speed multiplier indicator text -->
          <text x="780" y="12" fill="rgba(255, 255, 255, 0.6)" font-size="10" text-anchor="end">
            x10 {{ unit === 'mph' ? 'mph' : 'km/h' }}
          </text>
        </svg>
      </div>

      <!-- Main Display Area - Speed Only -->
      <div class="main-display">
        <div class="speed-display">
          <span class="speed-value" :style="{ color: gaugeColor, textShadow: `0 0 30px ${gaugeColor}` }">
            {{ Math.round(displaySpeed) }}
          </span>
          <span class="speed-unit">{{ unit === 'mph' ? 'mph' : 'km/h' }}</span>
        </div>
      </div>

    </div>

    <!-- Test mode indicator -->
    <div v-if="testMode" class="test-indicator">
      TEST MODE - Double-click to exit
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

interface Props {
  speed: number
  unit: 'mph' | 'kmh'
}

const props = defineProps<Props>()

const testMode = ref(false)
const testSpeed = ref(0)

const displaySpeed = computed(() => testMode.value ? testSpeed.value : props.speed)

// Max speed based on unit (150 km/h or ~93 mph for the 0-15 scale)
const maxSpeed = computed(() => props.unit === 'mph' ? 93 : 150)
const speedPercentage = computed(() => Math.min((displaySpeed.value / maxSpeed.value), 1))

// Dynamic color based on speed (0-4 green, 4-7 yellow, 7-9 orange, 9-15 red)
const gaugeColor = computed(() => {
  const percentage = speedPercentage.value
  if (percentage < 4/15) return '#22c55e'  // Green (0-4)
  if (percentage < 7/15) return '#eab308'  // Yellow (4-7)
  if (percentage < 9/15) return '#f97316'  // Orange (7-9)
  return '#ef4444' // Red (9-15)
})

// Generate segments for the tachometer bar with curved slant (0-5) then horizontal (5-15)
const totalSegments = 60
const segments = computed(() => {
  const segs = []
  const startX = 40
  const endX = 760
  const segmentWidth = (endX - startX) / totalSegments
  const height = 30

  // Y positions for the curved ramp
  const bottomY = 60    // Starting Y at mark 0 (bottom of ramp)
  const topY = 18       // Y at mark 5 and onwards (top, horizontal section)
  const rampEndSegment = 20  // Segment where ramp ends (~mark 5 out of 15, so 60 * 5/15 = 20)

  for (let i = 0; i < totalSegments; i++) {
    const x = startX + i * segmentWidth

    let y: number
    let skewAngle: number

    if (i <= rampEndSegment) {
      // Curved ramp section (0 to 5) - use easeOutQuad for smooth curve
      const rampProgress = i / rampEndSegment
      // easeOutQuad: smoother curve that starts steep and flattens out
      const easedProgress = 1 - Math.pow(1 - rampProgress, 2)
      y = bottomY - (bottomY - topY) * easedProgress
      // Skew angle decreases as we approach horizontal
      skewAngle = -12 * (1 - easedProgress)
    } else {
      // Horizontal section (5 to 15)
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

// Active segments based on speed (0-4 green, 4-7 yellow, 7-9 orange, 9-15 red)
const activeSegments = computed(() => {
  const activeCount = Math.floor(speedPercentage.value * totalSegments)
  return segments.value.slice(0, activeCount).map((seg, i) => {
    const percentage = i / totalSegments
    let color
    if (percentage < 4/15) color = '#22c55e'      // Green (0-4)
    else if (percentage < 7/15) color = '#eab308' // Yellow (4-7)
    else if (percentage < 9/15) color = '#f97316' // Orange (7-9)
    else color = '#ef4444'                         // Red (9-15)

    return { ...seg, color }
  })
})

// Generate tick marks (0-15 scale) with matching curved slant
const ticks = computed(() => {
  const tickList = []
  const startX = 40
  const endX = 760
  const totalTicks = 16 // 0 to 15

  // Match the segment Y positions
  const bottomY = 60 + 30 + 2  // Bottom of segment + height + gap
  const topY = 18 + 30 + 2     // Top section Y + height + gap
  const rampEndTick = 5        // Tick where ramp ends (mark 5)

  for (let i = 0; i < totalTicks; i++) {
    const x = startX + (i / (totalTicks - 1)) * (endX - startX)
    const isMajor = true // All are major in 0-15
    const tickValue = i
    const isActive = (i / (totalTicks - 1)) <= speedPercentage.value

    let baseY: number

    if (i <= rampEndTick) {
      // Curved ramp section (0 to 5) - use easeOutQuad
      const rampProgress = i / rampEndTick
      const easedProgress = 1 - Math.pow(1 - rampProgress, 2)
      baseY = bottomY - (bottomY - topY) * easedProgress
    } else {
      // Horizontal section (5 to 15)
      baseY = topY
    }

    // Color based on position (0-4 green, 4-7 yellow, 7-9 orange, 9-15 red)
    let color
    const tickMark = i // tick mark number (0-15)
    if (tickMark < 4) color = '#22c55e'       // Green (0-3)
    else if (tickMark < 7) color = '#eab308'  // Yellow (4-6)
    else if (tickMark < 9) color = '#f97316'  // Orange (7-8)
    else color = '#ef4444'                     // Red (9-15)

    tickList.push({
      x1: x,
      y1: baseY,
      x2: x,
      y2: baseY + 6,
      textX: x,
      textY: baseY + 14,
      value: tickValue,
      isMajor,
      isActive,
      color
    })
  }
  return tickList
})

// Test mode functionality
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
</script>

<style scoped>
.horizontal-tachometer {
  position: relative;
  width: 100%;
  max-width: 700px;
  margin: 0 auto;
  cursor: pointer;
}

.tft-display {
  padding: 0px 24px;
}

/* Tachometer Bar */
.tachometer-bar {
  width: 100%;
  height: 115px;
  margin-bottom: 0;
}

.tachometer-svg {
  width: 100%;
  height: 100%;
}

.segment-active {
  transition: fill 0.1s ease;
}

.tick-text {
  font-family: 'SF Mono', 'Monaco', 'Consolas', monospace;
}

/* Main Display Area */
.main-display {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-top: 4px;
  position: absolute;
  left: 50%;
  top: 85%;
  transform: translateX(-50%);
}

.speed-display {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.speed-value {
  font-size: 72px;
  font-weight: 700;
  line-height: 1;
  font-family: 'SF Mono', 'Monaco', 'Consolas', monospace;
  letter-spacing: -2px;
  transition: color 0.3s ease, text-shadow 0.3s ease;
}

.speed-unit {
  font-size: 16px;
  color: rgba(156, 163, 175, 0.8);
  font-weight: 600;
  letter-spacing: 1px;
  margin-top: 4px;
}

/* Test Mode Indicator */
.test-indicator {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(239, 68, 68, 0.9);
  color: white;
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
  animation: pulse 2s infinite;
  z-index: 2;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

/* Responsive adjustments */
@media (max-width: 600px) {
  .speed-value {
    font-size: 56px;
  }

  .tft-display {
    padding: 8px 12px;
  }
}

@media (orientation: landscape) {
  .horizontal-tachometer {
    max-width: 100%;
  }

  .tachometer-bar {
    height: 150px;
  }

  .speed-value {
    font-size: 100px;
  }

  .speed-unit {
    font-size: 20px;
  }
}

/* Portrait mode - center speed display below tachometer */
@media (orientation: portrait) {
  .horizontal-tachometer {
    max-width: 100%;
  }

  .tft-display {
    padding: 0 12px;
  }

  .tachometer-bar {
    height: 100px;
  }

  .main-display {
    position: relative;
    left: auto;
    top: auto;
    transform: none;
    margin-top: 0.5rem;
  }

  .speed-value {
    font-size: 80px;
  }

  .speed-unit {
    font-size: 18px;
  }

  .test-indicator {
    bottom: -40px;
  }
}
</style>
