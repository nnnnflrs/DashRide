<template>
  <div ref="containerRef" class="mini-map" :data-style="mapStyle" @dblclick="$emit('expand')">
    <div class="distance-badge">
      <TrendingUp class="badge-icon" />
      <span class="badge-text">{{ displayDistance }}</span>
    </div>

    <div class="zoom-hint">
      <span class="hint-text">Double-click to expand</span>
    </div>

    <div v-if="nextTurn" class="turn-info">
      <Navigation class="turn-icon" />
      <div class="turn-details">
        <div class="turn-label">Next</div>
        <div class="turn-street">{{ nextTurn }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { Navigation, TrendingUp } from 'lucide-vue-next'
import { GoogleMapsNative } from '../../plugins/googlemaps'


interface Props {
  distance: number
  unit: 'mph' | 'kmh'
  nextTurn?: string
  currentLocation?: { lat: number; lng: number } | null
  routePath?: Array<{ lat: number; lng: number }>
  bearing?: number
  mapStyle?: 'light' | 'dark'
  isVisible?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  nextTurn: '',
  mapStyle: 'light',
  isVisible: true
})

const emit = defineEmits<{
  expand: []
  arrived: []
}>()

const mapId = 'minimap'
const isInitialized = ref(false)
const isDestroying = ref(false)
const isInitializing = ref(false)
const containerRef = ref<HTMLElement | null>(null)

const distanceUnit = computed(() => props.unit === 'mph' ? 'mi' : 'km')

const displayDistance = computed(() => {
  if (props.distance < 1) {
    return `${Math.round(props.distance * 1000)}m`
  }
  return `${props.distance.toFixed(1)}${distanceUnit.value}`
})

const DARK_MAP_STYLE = JSON.stringify([
  { elementType: "geometry", stylers: [{ color: "#1d2c4d" }] },
  { elementType: "labels.text.fill", stylers: [{ color: "#8ec3b9" }] },
  { elementType: "labels.text.stroke", stylers: [{ color: "#1a3646" }] },
  { featureType: "administrative.country", elementType: "geometry.stroke", stylers: [{ color: "#4b6878" }] },
  { featureType: "administrative.land_parcel", elementType: "labels.text.fill", stylers: [{ color: "#64779f" }] },
  { featureType: "administrative.province", elementType: "geometry.stroke", stylers: [{ color: "#4b6878" }] },
  { featureType: "landscape.man_made", elementType: "geometry.stroke", stylers: [{ color: "#334e87" }] },
  { featureType: "landscape.natural", elementType: "geometry", stylers: [{ color: "#023e58" }] },
  { featureType: "poi", elementType: "geometry", stylers: [{ color: "#283d6a" }] },
  { featureType: "poi", elementType: "labels.text.fill", stylers: [{ color: "#6f9ba5" }] },
  { featureType: "poi", elementType: "labels.text.stroke", stylers: [{ color: "#1d2c4d" }] },
  { featureType: "poi.park", elementType: "geometry.fill", stylers: [{ color: "#023e58" }] },
  { featureType: "poi.park", elementType: "labels.text.fill", stylers: [{ color: "#3C7680" }] },
  { featureType: "road", elementType: "geometry", stylers: [{ color: "#304a7d" }] },
  { featureType: "road", elementType: "labels.text.fill", stylers: [{ color: "#98a5be" }] },
  { featureType: "road", elementType: "labels.text.stroke", stylers: [{ color: "#1d2c4d" }] },
  { featureType: "road.highway", elementType: "geometry", stylers: [{ color: "#2c6675" }] },
  { featureType: "road.highway", elementType: "geometry.stroke", stylers: [{ color: "#255763" }] },
  { featureType: "road.highway", elementType: "labels.text.fill", stylers: [{ color: "#b0d5df" }] },
  { featureType: "road.highway", elementType: "labels.text.stroke", stylers: [{ color: "#023e58" }] },
  { featureType: "transit", elementType: "labels.text.fill", stylers: [{ color: "#98a5be" }] },
  { featureType: "transit", elementType: "labels.text.stroke", stylers: [{ color: "#1d2c4d" }] },
  { featureType: "transit.line", elementType: "geometry.fill", stylers: [{ color: "#283d6a" }] },
  { featureType: "transit.station", elementType: "geometry", stylers: [{ color: "#3a4762" }] },
  { featureType: "water", elementType: "geometry", stylers: [{ color: "#0e1626" }] },
  { featureType: "water", elementType: "labels.text.fill", stylers: [{ color: "#4e6d70" }] }
])

const initMinimap = async (retryCount = 0) => {
  if (!containerRef.value) return
  if (isDestroying.value || isInitialized.value || isInitializing.value) return
  if (!props.currentLocation) return

  try {
    isInitializing.value = true

    const rect = containerRef.value.getBoundingClientRect()
    const dpr = window.devicePixelRatio || 1
    const scaledX = rect.x * dpr
    const scaledY = rect.y * dpr
    const scaledWidth = rect.width * dpr
    const scaledHeight = rect.height * dpr

    if ((rect.width === 0 || rect.height === 0) && retryCount < 3) {
      isInitializing.value = false
      setTimeout(() => initMinimap(retryCount + 1), 200)
      return
    }

    await GoogleMapsNative.create({
      mapId,
      type: 'minimap',
      lat: props.currentLocation.lat,
      lng: props.currentLocation.lng,
      zoom: 17,
      x: scaledX,
      y: scaledY,
      width: scaledWidth,
      height: scaledHeight
    })

    await GoogleMapsNative.setCenter({
      mapId,
      lat: props.currentLocation.lat,
      lng: props.currentLocation.lng,
      zoom: 17,
      bearing: props.bearing || 0,
      animate: false
    })

    if (props.mapStyle === 'dark') {
      await GoogleMapsNative.setMapStyle({ mapId, style: DARK_MAP_STYLE })
    } else {
      await GoogleMapsNative.setMapStyle({ mapId, style: null })
    }

    await GoogleMapsNative.addMarker({
      mapId,
      id: 'minimap-location',
      lat: props.currentLocation.lat,
      lng: props.currentLocation.lng,
      iconType: 'arrow',
      color: '#4285F4',
      rotation: props.bearing || 0,
      flat: true,
      scale: 0.5
    })

    if (props.routePath && props.routePath.length > 1) {
      await GoogleMapsNative.drawRoute({
        mapId,
        points: props.routePath.map(p => ({ lat: p.lat, lng: p.lng })),
        color: '#4285F4',
        width: 8
      })
    }

    if (props.isVisible) {
      await GoogleMapsNative.show({ mapId })
    } else {
      await GoogleMapsNative.hide({ mapId })
    }

    isInitialized.value = true
    isInitializing.value = false
  } catch (err) {
    isInitializing.value = false
    console.error('MiniMap: Error initializing:', err)
  }
}

watch(() => props.currentLocation, async (newLocation) => {
  if (!newLocation) return

  if (isDestroying.value) return

  if (!isInitialized.value && !isInitializing.value) {
    await initMinimap()
    return
  }

  if (isInitialized.value) {
    try {
      await GoogleMapsNative.updateMarker({
        mapId,
        id: 'minimap-location',
        lat: newLocation.lat,
        lng: newLocation.lng,
        rotation: props.bearing || 0
      })

      await GoogleMapsNative.setCenter({
        mapId,
        lat: newLocation.lat,
        lng: newLocation.lng,
        zoom: 17,
        bearing: props.bearing || 0,
        animate: true
      })
    } catch (err) {
      console.error('MiniMap: Error updating location:', err)
    }
  }
}, { deep: true, immediate: true, flush: 'post' })

watch(() => props.routePath, async (newPath) => {
  if (!newPath || !isInitialized.value) return

  try {
    await GoogleMapsNative.clearRoute({ mapId })

    if (newPath.length > 1) {
      await GoogleMapsNative.drawRoute({
        mapId,
        points: newPath.map(p => ({ lat: p.lat, lng: p.lng })),
        color: '#4285F4',
        width: 8
      })
    }
  } catch (err) {
    console.error('MiniMap: Error updating route:', err)
  }
}, { deep: true })

watch(() => props.mapStyle, async (newStyle) => {
  if (!isInitialized.value) return

  try {
    if (newStyle === 'dark') {
      await GoogleMapsNative.setMapStyle({ mapId, style: DARK_MAP_STYLE })
    } else {
      await GoogleMapsNative.setMapStyle({ mapId, style: null })
    }
  } catch (err) {
    console.error('MiniMap: Error updating map style:', err)
  }
})

let hasArrived = false
watch(() => props.distance, (newDistance, oldDistance) => {
  if (oldDistance < 0.1 && newDistance > 1) {
    hasArrived = false
  }

  if (!hasArrived && newDistance < 0.05 && newDistance >= 0) {
    hasArrived = true
    emit('arrived')
  }
})

watch(() => props.isVisible, async (visible) => {
  if (!isInitialized.value) return

  try {
    if (visible) {
      await GoogleMapsNative.show({ mapId })

      if (containerRef.value) {
        const rect = containerRef.value.getBoundingClientRect()
        if (rect.width > 0 && rect.height > 0) {
          const dpr = window.devicePixelRatio || 1
          await GoogleMapsNative.updateBounds({
            mapId,
            x: rect.x * dpr,
            y: rect.y * dpr,
            width: rect.width * dpr,
            height: rect.height * dpr
          })
        }
      }
    } else {
      await GoogleMapsNative.hide({ mapId })
    }
  } catch (err) {
    console.error('MiniMap: Error toggling visibility:', err)
  }
})

const handleOrientationChange = async () => {
  if (!isInitialized.value || !containerRef.value) return

  await new Promise(resolve => setTimeout(resolve, 300))

  const rect = containerRef.value.getBoundingClientRect()
  if (rect.width > 0 && rect.height > 0) {
    try {
      const dpr = window.devicePixelRatio || 1
      await GoogleMapsNative.updateBounds({
        mapId,
        x: rect.x * dpr,
        y: rect.y * dpr,
        width: rect.width * dpr,
        height: rect.height * dpr
      })
    } catch (err) {
      console.error('MiniMap: Error updating bounds:', err)
    }
  }
}

onMounted(() => {
  window.addEventListener('orientationchange', handleOrientationChange)
  window.addEventListener('resize', handleOrientationChange)
})

onUnmounted(() => {
  window.removeEventListener('orientationchange', handleOrientationChange)
  window.removeEventListener('resize', handleOrientationChange)

  if (isInitialized.value) {
    isInitialized.value = false
    isDestroying.value = true

    GoogleMapsNative.hide({ mapId })
      .then(() => GoogleMapsNative.destroy({ mapId }))
      .then(() => {
        setTimeout(() => {
          isDestroying.value = false
        }, 200)
      })
      .catch(err => {
        console.error('MiniMap: Error destroying:', err)
        setTimeout(() => {
          isDestroying.value = false
        }, 200)
      })
  }
})
</script>

<style scoped>
/* Metal-framed map widget */
.mini-map {
  position: relative;
  width: 100%;
  height: 100%;
  border-radius: 0.5rem;
  border: 1px solid rgba(90, 101, 119, 0.35);
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  box-sizing: border-box;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04), 0 2px 8px rgba(0, 0, 0, 0.4);
}

.mini-map[data-style="dark"] {
  background: linear-gradient(145deg, var(--metal-base, #1a1f25), var(--metal-dark, #12161a));
}

.mini-map[data-style="light"] {
  background: linear-gradient(145deg, var(--metal-mid, #252b33), var(--metal-base, #1a1f25));
  border-color: rgba(90, 101, 119, 0.4);
}

.mini-map:hover {
  transform: scale(1.02);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.5), inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

.distance-badge {
  position: absolute;
  top: 0.5rem;
  left: 0.5rem;
  background: linear-gradient(145deg, var(--metal-mid, #252b33), var(--metal-dark, #12161a));
  backdrop-filter: blur(8px);
  padding: 0.375rem 0.625rem;
  border-radius: 0.375rem;
  display: flex;
  align-items: center;
  gap: 0.375rem;
  z-index: 10;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.4);
  pointer-events: none;
  border: 1px solid rgba(90, 101, 119, 0.2);
}

.mini-map[data-style="light"] .distance-badge {
  background: linear-gradient(145deg, var(--metal-mid, #252b33), var(--metal-dark, #12161a));
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

.zoom-hint {
  position: absolute;
  bottom: 3.5rem;
  right: 0.5rem;
  background: linear-gradient(145deg, var(--metal-mid, #252b33), var(--metal-dark, #12161a));
  backdrop-filter: blur(8px);
  padding: 0.25rem 0.5rem;
  border-radius: 0.25rem;
  z-index: 10;
  opacity: 0;
  transition: opacity 0.3s ease;
  pointer-events: none;
  border: 1px solid rgba(90, 101, 119, 0.2);
}

.mini-map:hover .zoom-hint {
  opacity: 1;
}

.hint-text {
  font-size: 0.625rem;
  color: var(--chrome-highlight, #cfd8dc);
  font-weight: 500;
}

.mini-map[data-style="light"] .zoom-hint {
  background: linear-gradient(145deg, var(--metal-mid, #252b33), var(--metal-dark, #12161a));
}

.mini-map[data-style="light"] .hint-text {
  color: var(--aluminum-light, #718096);
}

.badge-icon {
  width: 0.75rem;
  height: 0.75rem;
  color: var(--accent-green, #00ff88);
  filter: drop-shadow(0 0 2px var(--glow-green, rgba(0, 255, 136, 0.4)));
}

.badge-text {
  font-size: 0.8125rem;
  font-weight: 700;
  color: var(--chrome-highlight, #cfd8dc);
  font-family: 'SF Mono', 'Menlo', 'Monaco', 'Consolas', monospace;
}

.mini-map[data-style="light"] .badge-text {
  color: var(--chrome-highlight, #cfd8dc);
}

.mini-map[data-style="light"] .badge-icon {
  color: var(--accent-green, #00ff88);
}

.turn-info {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: linear-gradient(0deg, rgba(18, 22, 26, 0.9), rgba(18, 22, 26, 0.7));
  padding: 0.625rem 0.875rem;
  display: flex;
  align-items: center;
  gap: 0.625rem;
  border-top: 1px solid rgba(90, 101, 119, 0.3);
  pointer-events: none;
}

.mini-map[data-style="light"] .turn-info {
  background: linear-gradient(0deg, rgba(18, 22, 26, 0.85), rgba(18, 22, 26, 0.65));
  border-top-color: rgba(90, 101, 119, 0.35);
}

.turn-icon {
  width: 1.125rem;
  height: 1.125rem;
  color: var(--accent-green, #00ff88);
  flex-shrink: 0;
  filter: drop-shadow(0 0 3px var(--glow-green, rgba(0, 255, 136, 0.4)));
}

.mini-map[data-style="light"] .turn-icon {
  color: var(--accent-green, #00ff88);
}

.turn-details {
  flex: 1;
  min-width: 0;
}

.turn-label {
  font-size: 0.75rem;
  color: var(--metal-shine, rgba(136, 153, 170, 1));
  font-weight: 500;
  letter-spacing: 0.04em;
  text-shadow: 0 1px 0 rgba(0, 0, 0, 0.5);
}

.mini-map[data-style="light"] .turn-label {
  color: var(--aluminum-light, #718096);
}

.turn-street {
  font-size: 0.9375rem;
  font-weight: 600;
  color: var(--chrome-highlight, #cfd8dc);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-shadow: 0 1px 0 rgba(0, 0, 0, 0.4);
}

.mini-map[data-style="light"] .turn-street {
  color: var(--chrome-highlight, #cfd8dc);
}
</style>
