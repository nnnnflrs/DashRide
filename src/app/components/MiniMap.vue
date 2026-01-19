<template>
  <div ref="containerRef" class="mini-map" :data-style="mapStyle" @dblclick="$emit('expand')">
    <!-- Native minimap renders here via GoogleMapsPlugin with mapId="minimap" -->
    <!-- The native map is positioned to match this container's bounds -->

    <!-- Distance badge overlay -->
    <div class="distance-badge">
      <TrendingUp class="badge-icon" />
      <span class="badge-text">{{ displayDistance }}</span>
    </div>

    <!-- Zoom indicator hint -->
    <div class="zoom-hint">
      <span class="hint-text">Double-click to expand</span>
    </div>

    <!-- Turn info footer -->
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
import { ref, computed, watch, onMounted, onUnmounted, nextTick, onBeforeMount } from 'vue'
import { Navigation, TrendingUp } from 'lucide-vue-next'
import { GoogleMapsNative } from '../../plugins/googlemaps'

interface Props {
  distance: number
  unit: 'mph' | 'kmh'
  nextTurn?: string
  currentLocation?: { lat: number; lng: number } | null
  routePath?: Array<{ lat: number; lng: number }>
  bearing?: number // Device heading for arrow rotation
  mapStyle?: 'light' | 'dark'
  isVisible?: boolean // Whether the component is visible (on riding tab)
}

const props = withDefaults(defineProps<Props>(), {
  nextTurn: '',
  mapStyle: 'light',
  isVisible: true
})

const emit = defineEmits<{
  expand: []
  arrived: [] // Emitted when user arrives at destination
}>()

const mapId = 'minimap'
const isInitialized = ref(false)
const containerRef = ref<HTMLElement | null>(null)

const distanceUnit = computed(() => props.unit === 'mph' ? 'mi' : 'km')

const displayDistance = computed(() => {
  if (props.distance < 1) {
    return `${Math.round(props.distance * 1000)}m`
  }
  return `${props.distance.toFixed(1)}${distanceUnit.value}`
})

// Dark mode map style (same as NavigationMap)
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

// Initialize minimap with retry logic for better device compatibility
const initMinimap = async (retryCount = 0) => {
  if (!props.currentLocation) {
    console.log('MiniMap: No current location, skipping init')
    return
  }

  if (!containerRef.value) {
    console.log('MiniMap: Container ref not available, skipping init')
    return
  }

  try {
    console.log('MiniMap: Initializing native minimap (attempt ' + (retryCount + 1) + ')')

    // Get the container's actual bounds from the DOM
    const rect = containerRef.value.getBoundingClientRect()

    // Scale bounds by device pixel ratio for native rendering
    const dpr = window.devicePixelRatio || 1
    const scaledX = rect.x * dpr
    const scaledY = rect.y * dpr
    const scaledWidth = rect.width * dpr
    const scaledHeight = rect.height * dpr

    console.log(`MiniMap: Container bounds: x=${rect.x}, y=${rect.y}, width=${rect.width}, height=${rect.height}, dpr=${dpr}`)
    console.log(`MiniMap: Scaled bounds: x=${scaledX}, y=${scaledY}, width=${scaledWidth}, height=${scaledHeight}`)

    // If bounds are still 0 after a few retries, wait a bit longer and retry
    if ((rect.width === 0 || rect.height === 0) && retryCount < 3) {
      console.warn('MiniMap: Container has zero bounds, retrying in 200ms...')
      setTimeout(() => initMinimap(retryCount + 1), 200)
      return
    }

    // Log if we're using fallback (native plugin has defaults)
    if (rect.width === 0 || rect.height === 0) {
      console.warn('MiniMap: Container still has zero bounds after retries, native plugin will use fallback dp values')
    }

    // Create native minimap with type="minimap" and exact bounds (scaled for device pixels)
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

    // Set initial camera with bearing to match heading direction
    await GoogleMapsNative.setCenter({
      mapId,
      lat: props.currentLocation.lat,
      lng: props.currentLocation.lng,
      zoom: 17,
      bearing: props.bearing || 0, // Rotate map to match heading direction
      animate: false
    })

    // Apply map style based on prop
    if (props.mapStyle === 'dark') {
      await GoogleMapsNative.setMapStyle({ mapId, style: DARK_MAP_STYLE })
    } else {
      await GoogleMapsNative.setMapStyle({ mapId, style: null })
    }

    // Add arrow marker for current location (for navigation)
    await GoogleMapsNative.addMarker({
      mapId,
      id: 'minimap-location',
      lat: props.currentLocation.lat,
      lng: props.currentLocation.lng,
      iconType: 'arrow',
      color: '#4285F4',
      rotation: props.bearing || 0,
      flat: true,
      scale: 0.5 // Make arrow smaller for minimap (50% of original size)
    })

    // Draw route if it already exists (user is already navigating)
    if (props.routePath && props.routePath.length > 1) {
      console.log('MiniMap: Drawing initial route with', props.routePath.length, 'points')
      await GoogleMapsNative.drawRoute({
        mapId,
        points: props.routePath.map(p => ({ lat: p.lat, lng: p.lng })),
        color: '#4285F4',
        width: 8
      })
    }

    // Only show the minimap if it should be visible (on riding tab)
    if (props.isVisible) {
      await GoogleMapsNative.show({ mapId })
    } else {
      // Hide it immediately if we're not on the riding tab
      await GoogleMapsNative.hide({ mapId })
    }

    isInitialized.value = true
    console.log('MiniMap: Native minimap initialized successfully')
  } catch (err) {
    console.error('MiniMap: Error initializing minimap:', err)
  }
}

// Watch for location changes and update map
watch(() => props.currentLocation, async (newLocation) => {
  if (!newLocation || !isInitialized.value) return

  try {
    // Update marker position
    await GoogleMapsNative.updateMarker({
      mapId,
      id: 'minimap-location',
      lat: newLocation.lat,
      lng: newLocation.lng,
      rotation: props.bearing || 0
    })

    // Center map on new location with bearing (so it points in the direction of travel)
    await GoogleMapsNative.setCenter({
      mapId,
      lat: newLocation.lat,
      lng: newLocation.lng,
      zoom: 17,
      bearing: props.bearing || 0, // Rotate map to match heading direction
      animate: true
    })
  } catch (err) {
    console.error('MiniMap: Error updating location:', err)
  }
}, { deep: true })

// Watch for route changes and draw route
watch(() => props.routePath, async (newPath) => {
  if (!newPath || !isInitialized.value) return

  try {
    console.log('MiniMap: Route path changed, redrawing route with', newPath.length, 'points')

    // Clear existing route
    await GoogleMapsNative.clearRoute({ mapId })

    // Draw new route
    if (newPath.length > 1) {
      await GoogleMapsNative.drawRoute({
        mapId,
        points: newPath.map(p => ({ lat: p.lat, lng: p.lng })),
        color: '#4285F4',
        width: 8
      })
      console.log('MiniMap: Route drawn successfully')
    }
  } catch (err) {
    console.error('MiniMap: Error updating route:', err)
  }
}, { deep: true })

// Watch for map style changes
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

// Watch for arrival (distance becomes 0 or very close to 0)
let hasArrived = false
watch(() => props.distance, (newDistance, oldDistance) => {
  // Reset hasArrived flag when starting a new navigation (distance jumps from 0 to a large value)
  if (oldDistance < 0.1 && newDistance > 1) {
    hasArrived = false
    console.log('MiniMap: New navigation started, reset arrival flag')
  }

  // Detect arrival: distance is less than 50 meters (0.05 km) or 0
  if (!hasArrived && newDistance < 0.05 && newDistance >= 0) {
    hasArrived = true
    console.log('MiniMap: User has arrived at destination!')
    emit('arrived')
  }
})

// Watch for visibility changes (tab switching)
watch(() => props.isVisible, async (visible) => {
  if (!isInitialized.value) return

  try {
    if (visible) {
      await GoogleMapsNative.show({ mapId })

      // When becoming visible, check if we need to update bounds
      // (in case they were 0 on initial mount or layout changed)
      if (containerRef.value) {
        const rect = containerRef.value.getBoundingClientRect()
        if (rect.width > 0 && rect.height > 0) {
          // Scale bounds by device pixel ratio
          const dpr = window.devicePixelRatio || 1
          const scaledX = rect.x * dpr
          const scaledY = rect.y * dpr
          const scaledWidth = rect.width * dpr
          const scaledHeight = rect.height * dpr

          console.log(`MiniMap: Updating bounds on visibility change: x=${rect.x}, y=${rect.y}, width=${rect.width}, height=${rect.height}`)
          console.log(`MiniMap: Scaled update bounds: x=${scaledX}, y=${scaledY}, width=${scaledWidth}, height=${scaledHeight}`)
          try {
            await GoogleMapsNative.updateBounds({
              mapId,
              x: scaledX,
              y: scaledY,
              width: scaledWidth,
              height: scaledHeight
            })
            console.log('MiniMap: Bounds updated successfully')
          } catch (boundsErr) {
            console.error('MiniMap: Error updating bounds:', boundsErr)
          }
        }
      }
    } else {
      await GoogleMapsNative.hide({ mapId })
    }
  } catch (err) {
    console.error('MiniMap: Error toggling visibility:', err)
  }
})

// Handle orientation changes to update map bounds
const handleOrientationChange = async () => {
  if (!isInitialized.value || !containerRef.value) return

  console.log('MiniMap: Orientation changed, updating bounds')

  // Wait a bit for the layout to settle after orientation change
  await new Promise(resolve => setTimeout(resolve, 300))

  const rect = containerRef.value.getBoundingClientRect()
  if (rect.width > 0 && rect.height > 0) {
    const dpr = window.devicePixelRatio || 1
    const scaledX = rect.x * dpr
    const scaledY = rect.y * dpr
    const scaledWidth = rect.width * dpr
    const scaledHeight = rect.height * dpr

    console.log(`MiniMap: Updating bounds after orientation: x=${rect.x}, y=${rect.y}, width=${rect.width}, height=${rect.height}`)

    try {
      await GoogleMapsNative.updateBounds({
        mapId,
        x: scaledX,
        y: scaledY,
        width: scaledWidth,
        height: scaledHeight
      })
      console.log('MiniMap: Bounds updated successfully after orientation change')
    } catch (err) {
      console.error('MiniMap: Error updating bounds after orientation:', err)
    }
  }
}

// Initialize on mount and listen for orientation changes
onMounted(async () => {
  window.addEventListener('orientationchange', handleOrientationChange)
  window.addEventListener('resize', handleOrientationChange)

  // Wait for next tick to ensure DOM is fully rendered
  await nextTick()

  // Use requestAnimationFrame to wait for layout to complete
  requestAnimationFrame(() => {
    // Add a small delay to ensure layout is fully computed
    setTimeout(() => {
      initMinimap()
    }, 100)
  })
})

// Cleanup on unmount
onUnmounted(async () => {
  // Remove orientation change listeners
  window.removeEventListener('orientationchange', handleOrientationChange)
  window.removeEventListener('resize', handleOrientationChange)

  if (isInitialized.value) {
    try {
      // Hide the map first before destroying
      await GoogleMapsNative.hide({ mapId })
      await GoogleMapsNative.destroy({ mapId })
      console.log('MiniMap: Native minimap destroyed')
    } catch (err) {
      console.error('MiniMap: Error destroying minimap:', err)
    }
  }
})
</script>

<style scoped>
.mini-map {
  position: relative;
  width: 100%;
  height: 100%; /* Match parent container height */
  border-radius: 0.5rem;
  border: 1px solid rgba(55, 65, 81, 0.5);
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  /* Native map renders inside this container via plugin positioning */
  box-sizing: border-box; /* Include borders in size calculations */
}

/* Dark map style */
.mini-map[data-style="dark"] {
  background: linear-gradient(to bottom right, rgb(17, 24, 39), rgb(31, 41, 55));
}

/* Light map style */
.mini-map[data-style="light"] {
  background: linear-gradient(to bottom right, rgb(241, 245, 249), rgb(226, 232, 240));
  border-color: rgba(148, 163, 184, 0.5);
}

.mini-map:hover {
  transform: scale(1.02);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

.distance-badge {
  position: absolute;
  top: 0.5rem;
  left: 0.5rem;
  background: rgba(0, 0, 0, 0.7);
  backdrop-filter: blur(8px);
  padding: 0.375rem 0.625rem;
  border-radius: 0.375rem;
  display: flex;
  align-items: center;
  gap: 0.375rem;
  z-index: 10;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  pointer-events: none;
}

.mini-map[data-style="light"] .distance-badge {
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.zoom-hint {
  position: absolute;
  bottom: 3.5rem;
  right: 0.5rem;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(8px);
  padding: 0.25rem 0.5rem;
  border-radius: 0.25rem;
  z-index: 10;
  opacity: 0;
  transition: opacity 0.3s ease;
  pointer-events: none;
}

.mini-map:hover .zoom-hint {
  opacity: 1;
}

.hint-text {
  font-size: 0.625rem;
  color: rgba(255, 255, 255, 0.9);
  font-weight: 500;
}

.mini-map[data-style="light"] .zoom-hint {
  background: rgba(255, 255, 255, 0.9);
}

.mini-map[data-style="light"] .hint-text {
  color: rgba(71, 85, 105, 0.9);
}

.badge-icon {
  width: 0.75rem;
  height: 0.75rem;
  color: rgb(74, 222, 128);
}

.badge-text {
  font-size: 0.8125rem;
  font-weight: 700;
  color: white;
}

.mini-map[data-style="light"] .badge-text {
  color: rgb(30, 41, 59);
}

.mini-map[data-style="light"] .badge-icon {
  color: rgb(22, 163, 74);
}

.turn-info {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: rgba(0, 0, 0, 0.5);
  padding: 0.625rem 0.875rem;
  display: flex;
  align-items: center;
  gap: 0.625rem;
  border-top: 1px solid rgba(55, 65, 81, 0.5);
  pointer-events: none;
}

.mini-map[data-style="light"] .turn-info {
  background: rgba(255, 255, 255, 0.95);
  border-top-color: rgba(203, 213, 225, 0.8);
}

.turn-icon {
  width: 1.125rem;
  height: 1.125rem;
  color: rgb(74, 222, 128);
  flex-shrink: 0;
}

.mini-map[data-style="light"] .turn-icon {
  color: rgb(22, 163, 74);
}

.turn-details {
  flex: 1;
  min-width: 0;
}

.turn-label {
  font-size: 0.75rem;
  color: rgba(156, 163, 175, 1);
  font-weight: 500;
}

.mini-map[data-style="light"] .turn-label {
  color: rgb(100, 116, 139);
}

.turn-street {
  font-size: 0.9375rem;
  font-weight: 600;
  color: white;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mini-map[data-style="light"] .turn-street {
  color: rgb(15, 23, 42);
}
</style>
