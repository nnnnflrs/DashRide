<template>
  <div class="mini-map">
    <div class="map-view">
      <svg class="map-pattern" viewBox="0 0 100 100">
        <path d="M20,80 L30,60 L50,50 L70,30" stroke="#22c55e" stroke-width="3" fill="none" />
        <path d="M10,70 L40,40 L60,20" stroke="#3b82f6" stroke-width="2" fill="none" opacity="0.5" />
      </svg>
      
      <div class="location-marker">
        <div class="marker-dot" />
      </div>

      <div class="distance-badge">
        <TrendingUp class="badge-icon" />
        <span class="badge-text">{{ displayDistance }}</span>
      </div>
    </div>

    <div class="turn-info">
      <Navigation class="turn-icon" />
      <div class="turn-details">
        <div class="turn-label">Next</div>
        <div class="turn-street">{{ nextTurn }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Navigation, TrendingUp } from 'lucide-vue-next'

interface Props {
  distance: number
  unit: 'mph' | 'kmh'
  nextTurn?: string
}

const props = withDefaults(defineProps<Props>(), {
  nextTurn: 'Main Street'
})

const distanceUnit = computed(() => props.unit === 'mph' ? 'mi' : 'km')

const displayDistance = computed(() => {
  if (props.distance < 1) {
    return `${Math.round(props.distance * 1000)}m`
  }
  return `${props.distance.toFixed(1)}${distanceUnit.value}`
})
</script>

<style scoped>
.mini-map {
  background: linear-gradient(to bottom right, rgb(17, 24, 39), rgb(31, 41, 55));
  border-radius: 0.5rem;
  border: 1px solid rgba(55, 65, 81, 0.5);
  overflow: hidden;
}

.map-view {
  position: relative;
  height: 9rem;
  background: linear-gradient(to bottom right, rgba(20, 83, 45, 0.2), rgba(30, 58, 138, 0.2));
  padding: 0.75rem;
}

.map-pattern {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  opacity: 0.2;
}

.location-marker {
  position: absolute;
  bottom: 0.75rem;
  left: 50%;
  transform: translateX(-50%);
}

.marker-dot {
  width: 0.75rem;
  height: 0.75rem;
  background: rgb(59, 130, 246);
  border-radius: 50%;
  border: 2px solid white;
  box-shadow: 0 0 10px rgba(59, 130, 246, 0.5);
}

.distance-badge {
  position: absolute;
  top: 0.5rem;
  left: 0.5rem;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
  padding: 0.25rem 0.5rem;
  border-radius: 0.25rem;
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.badge-icon {
  width: 0.75rem;
  height: 0.75rem;
  color: rgb(74, 222, 128);
}

.badge-text {
  font-size: 0.75rem;
  font-weight: bold;
  color: white;
}

.turn-info {
  background: rgba(0, 0, 0, 0.4);
  padding: 0.5rem 0.75rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  border-top: 1px solid rgba(55, 65, 81, 0.5);
}

.turn-icon {
  width: 1rem;
  height: 1rem;
  color: rgb(74, 222, 128);
  flex-shrink: 0;
}

.turn-details {
  flex: 1;
  min-width: 0;
}

.turn-label {
  font-size: 0.75rem;
  color: rgba(156, 163, 175, 1);
}

.turn-street {
  font-size: 0.875rem;
  font-weight: 600;
  color: white;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
