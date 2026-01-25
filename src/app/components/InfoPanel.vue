<template>
  <div class="info-panel">
    <div class="info-item">
      <MapPin class="info-icon" />
      <div class="info-content">
        <div class="info-label">DESTINATION</div>
        <div class="info-value">{{ destination }}</div>
      </div>
    </div>

    <div class="info-item">
      <Navigation class="info-icon" />
      <div class="info-content">
        <div class="info-label">TOTAL DISTANCE</div>
        <div class="info-value">{{ totalDistance.toFixed(1) }} {{ distanceUnit }}</div>
      </div>
    </div>

    <div class="info-item">
      <TrendingDown class="info-icon remaining" />
      <div class="info-content">
        <div class="info-label">REMAINING</div>
        <div class="info-value">{{ remainingDistance.toFixed(1) }} {{ distanceUnit }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { MapPin, Navigation, TrendingDown, TrendingUp, Minus } from 'lucide-vue-next'

interface Props {
  destination: string
  totalDistance: number
  remainingDistance: number
  unit: 'mph' | 'kmh'
  slopeDirection: 'uphill' | 'downhill' | 'flat'
}

const props = defineProps<Props>()

const distanceUnit = computed(() => props.unit === 'mph' ? 'mi' : 'km')
</script>

<style scoped>
.info-panel {
  position: absolute;
  bottom: 7rem;
  right: 1rem;
  background: rgba(17, 24, 39, 0.95);
  backdrop-filter: blur(12px);
  border-radius: clamp(0.375rem, 1.5vw, 0.5rem);
  border: 1px solid rgba(75, 85, 99, 0.5);
  padding: clamp(0.5rem, 2vw, 0.75rem);
  display: flex;
  flex-direction: column;
  gap: clamp(0.375rem, 1.5vw, 0.5rem);
  min-width: clamp(180px, 25vw, 220px);
  max-width: clamp(220px, 30vw, 260px);
  z-index: 20;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.3);
}

.info-item {
  display: flex;
  align-items: center;
  gap: clamp(0.375rem, 1.5vw, 0.5rem);
}

.info-icon {
  width: clamp(1rem, 2.8vw, 1.25rem);
  height: clamp(1rem, 2.8vw, 1.25rem);
  color: rgb(59, 130, 246);
  flex-shrink: 0;
}

.info-icon.remaining {
  color: rgb(34, 197, 94);
}

.info-icon.slope-uphill {
  color: rgb(239, 68, 68);
}

.info-icon.slope-downhill {
  color: rgb(34, 197, 94);
}

.info-icon.slope-flat {
  color: rgb(156, 163, 175);
}

.info-content {
  flex: 1;
  min-width: 0;
}

.info-label {
  font-size: clamp(0.55rem, 1.5vw, 0.65rem);
  font-weight: 600;
  color: rgba(156, 163, 175, 0.9);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: clamp(0.1rem, 0.5vw, 0.2rem);
}

.info-value {
  font-size: clamp(0.7rem, 2vw, 0.875rem);
  font-weight: 600;
  color: white;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
