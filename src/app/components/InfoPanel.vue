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
import { MapPin, Navigation, TrendingDown } from 'lucide-vue-next'

interface Props {
  destination: string
  totalDistance: number
  remainingDistance: number
  unit: 'mph' | 'kmh'
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
  border-radius: 0.5rem;
  border: 1px solid rgba(75, 85, 99, 0.5);
  padding: 0.65rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  min-width: 200px;
  max-width: 240px;
  z-index: 20;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.3);
}

.info-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.info-icon {
  width: 1.1rem;
  height: 1.1rem;
  color: rgb(59, 130, 246);
  flex-shrink: 0;
}

.info-icon.remaining {
  color: rgb(34, 197, 94);
}

.info-content {
  flex: 1;
}

.info-label {
  font-size: 0.6rem;
  font-weight: 600;
  color: rgba(156, 163, 175, 0.9);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 0.15rem;
}

.info-value {
  font-size: 0.8rem;
  font-weight: 600;
  color: white;
}
</style>
