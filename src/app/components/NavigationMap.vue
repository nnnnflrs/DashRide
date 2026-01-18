<template>
  <div class="navigation-map-container">
    <!-- Native map renders behind this container -->

    <!-- Info Panel - Show only when navigating -->
    <InfoPanel
      v-if="navIsNavigating"
      :destination="navDestination"
      :totalDistance="navTotalDistance"
      :remainingDistance="navRemainingDistance"
      :unit="unit"
    />

    <!-- Search Input -->
    <div v-if="!isNavigating && !loading && !error" class="search-container" :data-theme="theme">
      <input
        v-model="searchQuery"
        type="text"
        placeholder="Search location..."
        class="search-input"
        @input="onSearchInput"
        @focus="onSearchFocus"
        @blur="onSearchBlur"
      />
      <button
        v-if="searchQuery"
        @click="clearSearch"
        class="clear-button"
        type="button"
      >
        <X class="clear-icon" />
      </button>
    </div>

    <!-- Autocomplete Suggestions -->
    <div
      v-if="searchSuggestions.length > 0"
      class="suggestions-container"
      :data-theme="theme"
    >
      <div
        v-for="suggestion in searchSuggestions"
        :key="suggestion.placeId"
        class="suggestion-item"
        @click="selectPlace(suggestion)"
      >
        <div class="suggestion-primary">{{ suggestion.primaryText }}</div>
        <div class="suggestion-secondary">{{ suggestion.secondaryText }}</div>
      </div>
    </div>

    <!-- Directions Button (Step 1: Show after place selection) -->
    <button
      v-if="selectedPlace && !showRouteCalculated && !isNavigating && !isLoadingRoutes"
      @click="showDirections"
      class="directions-button"
    >
      <Navigation class="nav-icon" />
      Directions
    </button>

    <!-- Loading Routes Indicator -->
    <div v-if="isLoadingRoutes" class="loading-routes">
      <div class="spinner-small"></div>
      <span>Finding routes...</span>
    </div>

    <!-- Route Info & Start Button (Step 2: Show after routes are calculated) -->
    <div v-if="showRouteCalculated && !isNavigating" class="route-info-bar">
      <div class="selected-route-info">
        <div class="route-time">{{ availableRoutes[selectedRouteIndex]?.duration }}</div>
        <div class="route-dist">{{ availableRoutes[selectedRouteIndex]?.distance }}</div>
        <div class="route-name">{{ availableRoutes[selectedRouteIndex]?.summary }}</div>
      </div>
      <button @click="startNavigation" class="start-nav-btn">
        <Navigation class="nav-icon" />
        Start
      </button>
      <button @click="cancelRouteSelection" class="cancel-btn">
        <X class="nav-icon" />
      </button>
    </div>

    <!-- Stop Navigation Button (Step 3: Show during navigation) -->
    <button
      v-if="isNavigating"
      @click="stopNavigation"
      class="stop-nav-button"
    >
      <X class="nav-icon" />
      Stop
    </button>

    <div v-if="loading" class="loading-overlay" :data-theme="theme">
      <div class="spinner"></div>
      <p class="loading-text">Loading map...</p>
    </div>

    <div v-if="error" class="error-overlay">
      <p class="error-text">{{ error }}</p>
      <button @click="initMap" class="retry-button">Retry</button>
    </div>

    <button
      v-if="!loading && !error"
      @click="centerOnCurrentLocation"
      class="location-button"
      :class="{ active: isFollowingLocation }"
    >
      <LocateFixed v-if="isFollowingLocation" class="location-icon" />
      <Locate v-else class="location-icon" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { Geolocation } from '@capacitor/geolocation'
import { Navigation, X, Locate, LocateFixed } from 'lucide-vue-next'
import { useSettings } from '../../composables/useSettings'
import { useNavigation } from '../../composables/useNavigation'
import { useSlope } from '../../composables/useSlope'
import { GoogleMapsNative } from '../../plugins/googlemaps'
import { GooglePlaces, type PlacePrediction, type PlaceDetails } from '../../plugins/googleplaces'
import InfoPanel from './InfoPanel.vue'

interface Props {
  theme?: 'light' | 'dark'
}

const props = withDefaults(defineProps<Props>(), {
  theme: 'dark'
})

const emit = defineEmits<{
  'update:isSearching': [value: boolean]
}>()

const searchQuery = ref('')
const searchSuggestions = ref<PlacePrediction[]>([])
const loading = ref(true)
const error = ref('')
const isFollowingLocation = ref(true)
const selectedPlace = ref<PlaceDetails | null>(null)
const isNavigating = ref(false)
const showRouteCalculated = ref(false)
const routePath = ref<any[]>([])
const isSearching = ref(false)
const availableRoutes = ref<any[]>([])
const selectedRouteIndex = ref(0)
const isLoadingRoutes = ref(false)
let watchId: string | null = null
let searchTimeout: NodeJS.Timeout | null = null
let etaUpdateInterval: NodeJS.Timeout | null = null
let routeUpdateInterval: NodeJS.Timeout | null = null
let currentLocation = { lat: 0, lng: 0 }

// Native map state
const nativeMarkers = new Map<string, string>()
const isMapInitialized = ref(false)

// Dark mode map style
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

// Get settings
const { unit, avoidTolls, theme, mapStyle } = useSettings()

// Get navigation state
const {
  isNavigating: navIsNavigating,
  totalDistance: navTotalDistance,
  remainingDistance: navRemainingDistance,
  destination: navDestination,
  startNavigation: navStartNavigation,
  stopNavigation: navStopNavigation,
  updateEstimatedTime: navUpdateEstimatedTime,
  updateRoutePath: navUpdateRoutePath,
  updateRemainingDistance: navUpdateRemainingDistance
} = useNavigation()


// Compute current theme (resolve 'auto' to 'light' or 'dark') - for UI only
const currentTheme = computed(() => {
  if (theme.value === 'auto') {
    if (window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches) {
      return 'light'
    }
    return 'dark'
  }
  return theme.value
})

// Apply map style based on mapStyle setting (independent from main theme)
const applyMapStyle = async () => {
  if (!isMapInitialized.value) return

  try {
    if (mapStyle.value === 'dark') {
      await GoogleMapsNative.setMapStyle({ style: DARK_MAP_STYLE })
    } else {
      // Reset to default light style
      await GoogleMapsNative.setMapStyle({ style: null })
    }
  } catch (err) {
    console.error('Error setting map style:', err)
  }
}

// Watch for map style changes
watch(mapStyle, () => {
  applyMapStyle()
})

const getCurrentLocation = async () => {
  const position = await Geolocation.getCurrentPosition({
    enableHighAccuracy: true,
    timeout: 10000,
    maximumAge: 5000
  })
  return {
    lat: position.coords.latitude,
    lng: position.coords.longitude,
    accuracy: position.coords.accuracy
  }
}

// Search functions
const onSearchFocus = () => {
  isSearching.value = true
  emit('update:isSearching', true)

  // Clear any existing suggestions when focusing
  if (searchQuery.value.trim().length > 0) {
    performSearch()
  }
}

const onSearchBlur = () => {
  // Delay to allow click on suggestions
  setTimeout(() => {
    if (searchSuggestions.value.length === 0) {
      isSearching.value = false
      emit('update:isSearching', false)
    }
  }, 200)
}

const onSearchInput = () => {
  // Clear existing timeout
  if (searchTimeout) {
    clearTimeout(searchTimeout)
  }

  // Clear suggestions if query is empty
  if (searchQuery.value.trim().length === 0) {
    searchSuggestions.value = []
    return
  }

  // Debounce search by 300ms
  searchTimeout = setTimeout(() => {
    performSearch()
  }, 300)
}

const performSearch = async () => {
  const query = searchQuery.value.trim()
  if (query.length === 0) {
    searchSuggestions.value = []
    return
  }

  try {
    const result = await GooglePlaces.searchPlaces({
      query,
      lat: currentLocation.lat,
      lng: currentLocation.lng
    })

    searchSuggestions.value = result.predictions
  } catch (err) {
    console.error('Error searching places:', err)
    searchSuggestions.value = []
  }
}

const selectPlace = async (prediction: PlacePrediction) => {
  try {
    // Get place details
    const placeDetails = await GooglePlaces.getPlaceDetails({
      placeId: prediction.placeId
    })

    selectedPlace.value = placeDetails

    // Clear search UI
    searchQuery.value = placeDetails.name
    searchSuggestions.value = []
    isSearching.value = false
    emit('update:isSearching', false)

    // Add marker for selected place
    await GoogleMapsNative.addMarker({
      id: 'destination',
      lat: placeDetails.location.lat,
      lng: placeDetails.location.lng,
      title: placeDetails.name,
      color: '#EA4335' // Red color for destination
    })

    // Show the place on map
    await GoogleMapsNative.setCenter({
      lat: placeDetails.location.lat,
      lng: placeDetails.location.lng,
      zoom: 15,
      animate: true
    })

    // Clear autocomplete session
    await GooglePlaces.clearSession()

  } catch (err) {
    alert('Failed to get place details')
  }
}

const clearSearch = () => {
  searchQuery.value = ''
  searchSuggestions.value = []
  selectedPlace.value = null
  showRouteCalculated.value = false
  isSearching.value = false
  emit('update:isSearching', false)

  // Clear routes from map
  GoogleMapsNative.clearRoute()
    .catch(err => console.error('Error clearing routes:', err))

  // Remove destination marker
  GoogleMapsNative.removeMarker({ id: 'destination' })
    .catch(err => console.error('Error removing marker:', err))

  // Reset route state
  availableRoutes.value = []
  selectedRouteIndex.value = 0
}

const initMap = async () => {
  loading.value = true
  error.value = ''

  try {
    // Request location permission
    const permission = await Geolocation.requestPermissions()
    if (permission.location !== 'granted') {
      throw new Error('Location permission denied')
    }

    // Get current position
    const location = await getCurrentLocation()
    currentLocation.lat = location.lat
    currentLocation.lng = location.lng

    // Initialize native Google Maps
    await GoogleMapsNative.create({
      lat: currentLocation.lat,
      lng: currentLocation.lng,
      zoom: 17
    })

    await GoogleMapsNative.show()

    // Mark map as initialized
    isMapInitialized.value = true

    // Apply initial theme style
    await applyMapStyle()

    // Add current location marker
    const result = await GoogleMapsNative.addMarker({
      id: 'current-location',
      lat: currentLocation.lat,
      lng: currentLocation.lng,
      title: 'Your Location',
      color: '#4285F4',
      iconType: 'dot'
    })

    nativeMarkers.set('current-location', result.id)

    // Stop auto-centering after initial load
    isFollowingLocation.value = false

    // Listen for user gestures on native map to stop auto-centering
    GoogleMapsNative.addListener('cameraMoveStarted', (data) => {
      if (data.gesture) {
        isFollowingLocation.value = false
      }
    })

    // Listen for polyline clicks to select routes
    GoogleMapsNative.addListener('polylineClick', (data) => {
      selectRoute(data.routeIndex)
    })

    // Watch position changes with error handling
    console.log('Starting GPS position watch...')
    watchId = await Geolocation.watchPosition(
      {
        enableHighAccuracy: true,
        timeout: 10000, // Increased timeout to 10 seconds
        maximumAge: 0
      },
      (position, err) => {
        // Handle errors
        if (err) {
          console.error('GPS Error:', err)
          // Don't stop watching, just log the error and continue
          return
        }

        if (!position) {
          console.log('GPS: No position received')
          return
        }

        const newLocation = {
          lat: position.coords.latitude,
          lng: position.coords.longitude
        }

        console.log(`GPS Update: lat=${newLocation.lat.toFixed(6)}, lng=${newLocation.lng.toFixed(6)}, isNavigating=${isNavigating.value}`)

        // Update current location for search bias
        currentLocation.lat = newLocation.lat
        currentLocation.lng = newLocation.lng

        // Get bearing/heading for rotation
        const bearing = position.coords.heading
        console.log(`GPS Bearing: ${bearing}`)

        // Update marker based on navigation state
        if (isNavigating.value) {
          // During navigation: use arrow marker with rotation
          console.log('Updating arrow marker during navigation')
          GoogleMapsNative.updateMarker({
            id: 'current-location',
            lat: newLocation.lat,
            lng: newLocation.lng,
            rotation: bearing !== null && bearing !== undefined ? bearing : 0,
            flat: true
          }).then(() => {
            console.log('Arrow marker updated successfully')
          }).catch(err => console.error('Error updating arrow marker:', err))
        } else {
          // When not navigating: use blue dot marker
          console.log('Adding/updating dot marker (not navigating)')
          GoogleMapsNative.addMarker({
            id: 'current-location',
            lat: newLocation.lat,
            lng: newLocation.lng,
            title: 'Your Location',
            color: '#4285F4',
            iconType: 'dot'
          }).catch(err => console.error('Error updating native marker:', err))
        }

        // Center map on location if following (always follow during navigation)
        if (isFollowingLocation.value || isNavigating.value) {
          GoogleMapsNative.setCenter({
            lat: newLocation.lat,
            lng: newLocation.lng,
            zoom: isNavigating.value ? 19 : 17,
            tilt: isNavigating.value ? 45 : 0,
            bearing: isNavigating.value && bearing !== null && bearing !== undefined ? bearing : undefined,
            animate: true
          }).catch(err => console.error('Error centering native map:', err))
        }

        // Update route progress during navigation
        if (isNavigating.value && routePath.value.length > 0) {
          updateRouteProgress(newLocation)
        }
      }
    )

    console.log('GPS watch started with ID:', watchId)

    loading.value = false
  } catch (err: any) {
    console.error('Map initialization error:', err)
    error.value = err.message || 'Failed to load map'
    loading.value = false
  }
}

const showDirections = async () => {
  if (!selectedPlace.value) return

  try {
    isLoadingRoutes.value = true

    // Get current location
    const currentPos = await getCurrentLocation()

    // Fetch routes from Google Directions API using native method
    const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY
    const origin = `${currentPos.lat},${currentPos.lng}`
    const destination = `${selectedPlace.value.location.lat},${selectedPlace.value.location.lng}`

    const data = await GoogleMapsNative.getDirections({
      origin,
      destination,
      apiKey,
      avoidTolls: avoidTolls.value
    })

    if (data.status === 'OK' && data.routes && data.routes.length > 0) {
      // Process routes
      availableRoutes.value = data.routes.map((route: any, index: number) => {
        const leg = route.legs[0]
        const routeInfo = {
          index,
          summary: route.summary || `Route ${index + 1}`,
          distance: leg.distance.text,
          duration: leg.duration.text,
          distanceValue: leg.distance.value,
          durationValue: leg.duration.value,
          polyline: route.overview_polyline.points,
          steps: leg.steps
        }
        return routeInfo
      })

      // Draw all routes on map
      await drawAllRoutes()

      // Zoom out to show all routes
      await zoomToShowRoutes()

      showRouteCalculated.value = true
    } else {
      alert(`Could not find routes to destination. Status: ${data.status}`)
    }
  } catch (err) {
    alert('Failed to calculate routes')
  } finally {
    isLoadingRoutes.value = false
  }
}

const zoomToShowRoutes = async () => {
  if (!selectedPlace.value || availableRoutes.value.length === 0) return

  try {
    // Get current location
    const currentPos = await getCurrentLocation()

    // Calculate the center point between current location and destination
    const destLat = selectedPlace.value.location.lat
    const destLng = selectedPlace.value.location.lng
    const centerLat = (currentPos.lat + destLat) / 2
    const centerLng = (currentPos.lng + destLng) / 2

    // Calculate distance to determine zoom level
    const latDiff = Math.abs(currentPos.lat - destLat)
    const lngDiff = Math.abs(currentPos.lng - destLng)
    const maxDiff = Math.max(latDiff, lngDiff)

    // Determine zoom level based on distance
    // Larger difference = zoom out more (lower zoom number)
    let zoom = 15
    if (maxDiff > 1) zoom = 10
    else if (maxDiff > 0.5) zoom = 11
    else if (maxDiff > 0.2) zoom = 12
    else if (maxDiff > 0.1) zoom = 13
    else if (maxDiff > 0.05) zoom = 14

    // Set camera to center with calculated zoom
    await GoogleMapsNative.setCenter({
      lat: centerLat,
      lng: centerLng,
      zoom,
      animate: true
    })
  } catch (err) {
    console.error('Error zooming to show routes:', err)
  }
}

const drawAllRoutes = async () => {
  // Draw all routes with different colors - draw non-selected routes first, then selected on top
  const sortedRoutes = availableRoutes.value.map((route, index) => ({ route, index }))
    .sort((a, b) => {
      // Selected route should be drawn last (on top)
      if (a.index === selectedRouteIndex.value) return 1
      if (b.index === selectedRouteIndex.value) return -1
      return 0
    })

  for (const { route, index: i } of sortedRoutes) {
    const isSelected = i === selectedRouteIndex.value

    // Decode polyline
    const points = decodePolyline(route.polyline)

    // Draw route with color based on selection and routeIndex tag
    // Selected route: Bold blue (#1A73E8)
    // Alternative routes: Medium gray (#808080) - more visible than light gray
    await GoogleMapsNative.drawRoute({
      points: points.map((p: any) => ({ lat: p.lat, lng: p.lng })),
      color: isSelected ? '#1A73E8' : '#808080',
      width: isSelected ? 14 : 10,
      routeIndex: i
    })
  }
}

const decodePolyline = (encoded: string) => {
  const points = []
  let index = 0
  const len = encoded.length
  let lat = 0
  let lng = 0

  while (index < len) {
    let b
    let shift = 0
    let result = 0
    do {
      b = encoded.charCodeAt(index++) - 63
      result |= (b & 0x1f) << shift
      shift += 5
    } while (b >= 0x20)
    const dlat = ((result & 1) ? ~(result >> 1) : (result >> 1))
    lat += dlat

    shift = 0
    result = 0
    do {
      b = encoded.charCodeAt(index++) - 63
      result |= (b & 0x1f) << shift
      shift += 5
    } while (b >= 0x20)
    const dlng = ((result & 1) ? ~(result >> 1) : (result >> 1))
    lng += dlng

    points.push({ lat: lat / 1e5, lng: lng / 1e5 })
  }

  return points
}

const selectRoute = async (routeIndex: number) => {
  selectedRouteIndex.value = routeIndex

  // Redraw all routes to update colors
  await GoogleMapsNative.clearRoute()
  await drawAllRoutes()
}

const cancelRouteSelection = async () => {
  // Clear routes from map
  await GoogleMapsNative.clearRoute()

  // Reset state
  showRouteCalculated.value = false
  availableRoutes.value = []
  selectedRouteIndex.value = 0
}

const updateRouteProgress = async (currentLocation: { lat: number; lng: number }) => {
  if (routePath.value.length === 0) {
    console.log('updateRouteProgress: routePath is empty')
    return
  }

  console.log(`updateRouteProgress: Called with ${routePath.value.length} route points`)

  // Find the closest point on the route
  let minDistance = Infinity
  let closestIndex = 0

  for (let i = 0; i < routePath.value.length; i++) {
    const point = routePath.value[i]
    const distance = calculateDistance(currentLocation.lat, currentLocation.lng, point.lat, point.lng)

    if (distance < minDistance) {
      minDistance = distance
      closestIndex = i
    }
  }

  console.log(`Closest point index: ${closestIndex}, distance: ${minDistance.toFixed(2)}m`)

  // Calculate remaining distance BEFORE trimming
  let remainingDist = 0

  // Add distance from current location to closest point on route
  if (closestIndex < routePath.value.length) {
    remainingDist += calculateDistance(
      currentLocation.lat,
      currentLocation.lng,
      routePath.value[closestIndex].lat,
      routePath.value[closestIndex].lng
    )
  }

  // Add distance between remaining route points (from closest point to end)
  for (let i = closestIndex; i < routePath.value.length - 1; i++) {
    const point1 = routePath.value[i]
    const point2 = routePath.value[i + 1]
    remainingDist += calculateDistance(point1.lat, point1.lng, point2.lat, point2.lng)
  }

  // Convert meters to kilometers
  const remainingDistKm = remainingDist / 1000

  // Ensure remaining distance never exceeds total distance
  const finalRemainingDist = Math.min(remainingDistKm, navTotalDistance.value)

  console.log(`Remaining distance: ${remainingDistKm.toFixed(2)} km (capped at ${finalRemainingDist.toFixed(2)} km, total: ${navTotalDistance.value.toFixed(2)} km)`)

  // Update shared state
  navUpdateRemainingDistance(finalRemainingDist)

  // Trim the route if we've moved past the first point
  if (closestIndex > 0) {
    console.log(`Trimming route: removing first ${closestIndex} points (up to closest), ${routePath.value.length} -> ${routePath.value.length - closestIndex}`)

    // Remove all points before the closest point
    routePath.value = routePath.value.slice(closestIndex)

    // Update shared navigation state with trimmed route
    navUpdateRoutePath(routePath.value)

    // Redraw the route when trimming happens
    try {
      await GoogleMapsNative.clearRoute()
      console.log('Route cleared for redraw after trimming')

      if (routePath.value.length > 0) {
        // Draw route from current location to the remaining route points
        const routePoints = [
          { lat: currentLocation.lat, lng: currentLocation.lng }, // Start from current location
          ...routePath.value.map((p: any) => ({ lat: p.lat, lng: p.lng }))
        ]

        await GoogleMapsNative.drawRoute({
          points: routePoints,
          color: '#4285F4',
          width: 14
        })
        console.log(`Route redrawn with ${routePoints.length} points (from current location to destination)`)
      }
    } catch (err) {
      console.error('Error redrawing route:', err)
    }
  } else {
    console.log(`No trimming needed, already at start of route (closestIndex: ${closestIndex})`)
  }
}

// Helper function to calculate distance between two points in meters
const calculateDistance = (lat1: number, lng1: number, lat2: number, lng2: number): number => {
  const R = 6371e3 // Earth's radius in meters
  const φ1 = lat1 * Math.PI / 180
  const φ2 = lat2 * Math.PI / 180
  const Δφ = (lat2 - lat1) * Math.PI / 180
  const Δλ = (lng2 - lng1) * Math.PI / 180

  const a = Math.sin(Δφ / 2) * Math.sin(Δφ / 2) +
            Math.cos(φ1) * Math.cos(φ2) *
            Math.sin(Δλ / 2) * Math.sin(Δλ / 2)
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

  return R * c
}

// Update ETA by fetching fresh directions from current location to destination
const updateETA = async () => {
  if (!selectedPlace.value || !isNavigating.value) return

  try {
    const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY
    const origin = `${currentLocation.lat},${currentLocation.lng}`
    const destination = `${selectedPlace.value.location.lat},${selectedPlace.value.location.lng}`

    const data = await GoogleMapsNative.getDirections({
      origin,
      destination,
      apiKey,
      avoidTolls: avoidTolls.value
    })

    if (data.status === 'OK' && data.routes && data.routes.length > 0) {
      const route = data.routes[0]
      const leg = route.legs[0]
      const durationSeconds = leg.duration.value

      // Update ETA in shared navigation state
      navUpdateEstimatedTime(durationSeconds)
    }
  } catch (err) {
    console.error('Error updating ETA:', err)
  }
}

const startNavigation = async () => {
  if (!showRouteCalculated.value || !selectedPlace.value || availableRoutes.value.length === 0) return

  try {
    // Use already-tracked current location instead of requesting fresh position
    const currentLocationPos = {
      lat: currentLocation.lat,
      lng: currentLocation.lng
    }

    // Get the selected route
    const selectedRoute = availableRoutes.value[selectedRouteIndex.value]

    // Calculate total distance in km
    const totalDist = selectedRoute.distanceValue / 1000

    // Get duration in seconds from the route
    const durationSeconds = selectedRoute.durationValue

    // Start navigation with shared state (including ETA)
    const destName = selectedPlace.value.name || 'Destination'
    navStartNavigation(destName, totalDist, durationSeconds)

    // Start navigation mode
    isNavigating.value = true

    // Enable location following for navigation (always on during navigation)
    isFollowingLocation.value = true

    // Store the selected route path for progress tracking
    routePath.value = decodePolyline(selectedRoute.polyline)

    // Update shared navigation state with initial route
    navUpdateRoutePath(routePath.value)

    // Replace current location marker with arrow marker
    await GoogleMapsNative.removeMarker({ id: 'current-location' })

    await GoogleMapsNative.addMarker({
      id: 'current-location',
      lat: currentLocationPos.lat,
      lng: currentLocationPos.lng,
      iconType: 'arrow',
      color: '#1A73E8', // Blue arrow for navigation
      rotation: 0,
      flat: true
    })

    // Clear all other routes, keep only the selected one
    await GoogleMapsNative.clearRoute()

    const points = decodePolyline(selectedRoute.polyline)
    await GoogleMapsNative.drawRoute({
      points: points.map((p: any) => ({ lat: p.lat, lng: p.lng })),
      color: '#4285F4',
      width: 14
    })

    // Start periodic ETA updates (every 30 seconds)
    etaUpdateInterval = setInterval(() => {
      updateETA()
    }, 30000)

    // Do initial ETA update after a short delay
    setTimeout(() => {
      updateETA()
    }, 3000)

    // Start aggressive route update interval (every 1 second)
    // This ensures the route line is always updated even if GPS updates are slow
    routeUpdateInterval = setInterval(() => {
      if (routePath.value.length > 0 && currentLocation.lat !== 0 && currentLocation.lng !== 0) {
        console.log('Route update interval triggered')
        updateRouteProgress({
          lat: currentLocation.lat,
          lng: currentLocation.lng
        })
      }
    }, 1000)

    // Focus camera on current location with navigation view (tilt, zoom, bearing)
    await GoogleMapsNative.setCenter({
      lat: currentLocationPos.lat,
      lng: currentLocationPos.lng,
      zoom: 19,
      tilt: 45,
      bearing: 0,
      animate: true
    })

    // Initialize route progress
    await updateRouteProgress(currentLocationPos)

  } catch (err: any) {
    alert(`Failed to start navigation: ${err.message || 'Unknown error'}`)
    clearSearch();
  }
}

const stopNavigation = async () => {
  // Clear ETA update interval
  if (etaUpdateInterval) {
    clearInterval(etaUpdateInterval)
    etaUpdateInterval = null
  }

  // Clear route update interval
  if (routeUpdateInterval) {
    clearInterval(routeUpdateInterval)
    routeUpdateInterval = null
  }

  // Clear the route
  await GoogleMapsNative.clearRoute().catch(err => console.error('Error clearing route:', err))

  // Reset navigation state
  isNavigating.value = false
  showRouteCalculated.value = false
  selectedPlace.value = null
  routePath.value = []

  // Stop following location
  isFollowingLocation.value = false

  // Replace arrow marker with blue dot marker
  await GoogleMapsNative.removeMarker({ id: 'current-location' })
  await GoogleMapsNative.addMarker({
    id: 'current-location',
    lat: currentLocation.lat,
    lng: currentLocation.lng,
    title: 'Your Location',
    color: '#4285F4',
    iconType: 'dot'
  }).catch(err => console.error('Error restoring regular marker:', err))

  // Stop shared navigation state
  navStopNavigation()
}

const centerOnCurrentLocation = async () => {
  try {
    // Get fresh location
    const currentLocation = await Geolocation.getCurrentPosition({
      enableHighAccuracy: true,
      timeout: 5000,
      maximumAge: 5000
    })

    const preciseLocation = {
      lat: currentLocation.coords.latitude,
      lng: currentLocation.coords.longitude
    }

    // Enable location following
    isFollowingLocation.value = true

    // Center native map
    await GoogleMapsNative.setCenter({
      lat: preciseLocation.lat,
      lng: preciseLocation.lng,
      zoom: 17,
      animate: true
    })
  } catch (err) {
    console.error('Error getting current location:', err)
  }
}

onMounted(() => {
  initMap()
})

onUnmounted(async () => {
  if (watchId) {
    Geolocation.clearWatch({ id: watchId })
  }

  // Cleanup native map
  try {
    await GoogleMapsNative.destroy()
  } catch (err) {
    console.error('Error destroying native map:', err)
  }
})
</script>

<style scoped>
.navigation-map-container {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  /* Allow touch events to pass through to native map */
  pointer-events: none;
}

/* Re-enable pointer events for interactive UI elements */
.navigation-map-container > * {
  pointer-events: auto;
}

.loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.9);
  z-index: 1000;
}

.loading-overlay[data-theme="light"] {
  background: rgba(255, 255, 255, 0.95);
}

.spinner {
  width: 3rem;
  height: 3rem;
  border: 4px solid rgba(66, 133, 244, 0.2);
  border-top-color: rgb(66, 133, 244);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.loading-text {
  margin-top: 1rem;
  color: white;
  font-size: 1rem;
}

.loading-overlay[data-theme="light"] .loading-text {
  color: rgb(30, 41, 59);
}

.error-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.95);
  z-index: 1000;
  padding: 2rem;
}

.error-text {
  color: rgb(248, 113, 113);
  font-size: 1rem;
  text-align: center;
  margin-bottom: 1.5rem;
  max-width: 90%;
}

.retry-button {
  padding: 0.75rem 2rem;
  background: rgb(66, 133, 244);
  color: white;
  border: none;
  border-radius: 0.5rem;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.retry-button:hover {
  background: rgb(51, 118, 229);
  transform: translateY(-2px);
}

.search-container {
  position: absolute;
  top: 0.75rem;
  left: 0.75rem;
  right: 0.75rem;
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.search-input {
  flex: 1;
  padding: 0.625rem 0.875rem;
  background: rgba(0, 0, 0, 0.85);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 0.5rem;
  color: white;
  font-size: 0.9rem;
  backdrop-filter: blur(10px);
  transition: all 0.2s;
}

.search-container[data-theme="light"] .search-input {
  background: rgba(255, 255, 255, 0.95);
  border-color: rgba(0, 0, 0, 0.1);
  color: rgb(30, 41, 59);
}

.search-input:focus {
  outline: none;
  border-color: rgb(66, 133, 244);
  box-shadow: 0 0 0 3px rgba(66, 133, 244, 0.1);
}

.search-input::placeholder {
  color: rgba(255, 255, 255, 0.5);
}

.search-container[data-theme="light"] .search-input::placeholder {
  color: rgba(0, 0, 0, 0.4);
}

.clear-button {
  padding: 0.4rem;
  background: rgba(255, 255, 255, 0.1);
  border: none;
  border-radius: 0.4rem;
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.clear-button:hover {
  background: rgba(255, 255, 255, 0.2);
}

.clear-icon {
  width: 1rem;
  height: 1rem;
}

.suggestions-container {
  position: absolute;
  top: 3.5rem;
  left: 0.75rem;
  right: 2.5rem;
  max-height: 12rem;
  overflow-y: auto;
  background: rgba(0, 0, 0, 0.98);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 0.5rem;
  backdrop-filter: blur(10px);
  z-index: 99999;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
}

.suggestions-container[data-theme="light"] {
  background: rgba(255, 255, 255, 0.98);
  border-color: rgba(0, 0, 0, 0.1);
}

.suggestion-item {
  padding: 0.5rem 0.75rem;
  cursor: pointer;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  transition: background 0.2s;
}

.suggestions-container[data-theme="light"] .suggestion-item {
  border-bottom-color: rgba(0, 0, 0, 0.05);
}

.suggestion-item:last-child {
  border-bottom: none;
}

.suggestion-item:hover {
  background: rgba(255, 255, 255, 0.1);
}

.suggestions-container[data-theme="light"] .suggestion-item:hover {
  background: rgba(0, 0, 0, 0.05);
}

.suggestion-primary {
  color: white;
  font-size: 0.9rem;
  font-weight: 500;
  margin-bottom: 0.2rem;
  line-height: 1.3;
}

.suggestions-container[data-theme="light"] .suggestion-primary {
  color: rgb(30, 41, 59);
}

.suggestion-secondary {
  color: rgba(255, 255, 255, 0.6);
  font-size: 0.8rem;
  line-height: 1.2;
}

.suggestions-container[data-theme="light"] .suggestion-secondary {
  color: rgba(0, 0, 0, 0.5);
}

.directions-button,
.start-nav-button,
.stop-nav-button {
  position: absolute;
  bottom: 2rem;
  left: 50%;
  transform: translateX(-50%);
  padding: 1rem 2rem;
  border: none;
  border-radius: 2rem;
  font-size: 1.125rem;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  transition: all 0.3s;
  z-index: 100;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

.directions-button {
  background: rgb(66, 133, 244);
  color: white;
}

.directions-button:hover {
  background: rgb(51, 118, 229);
  transform: translateX(-50%) scale(1.05);
}

.start-nav-button {
  background: rgb(34, 197, 94);
  color: white;
}

.start-nav-button:hover {
  background: rgb(22, 163, 74);
  transform: translateX(-50%) scale(1.05);
}

.stop-nav-button {
  background: rgb(239, 68, 68);
  color: white;
}

.stop-nav-button:hover {
  background: rgb(220, 38, 38);
  transform: translateX(-50%) scale(1.05);
}

.location-button {
  position: absolute;
  bottom: 2rem;
  right: 1rem;
  width: 3.5rem;
  height: 3.5rem;
  background: white;
  border: 2px solid rgba(255, 255, 255, 0.1);
  border-radius: 50%;
  color: #1a73e8;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  backdrop-filter: blur(10px);
  z-index: 100;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

.location-icon {
  width: 1.0rem;
  height: 1.0rem;
}


.location-button:hover {
  transform: scale(1.05);
  box-shadow: 0 6px 8px rgba(0, 0, 0, 0.4);
}

.location-button.active {
  background: rgb(59, 130, 246);
}


.location-button.active .location-icon {
  color: white;
}

.nav-icon {
  width: 1.25rem;
  height: 1.25rem;
}

/* Loading Routes Indicator */
.loading-routes {
  position: absolute;
  bottom: 2rem;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem 1.5rem;
  background: rgba(0, 0, 0, 0.9);
  border-radius: 2rem;
  color: white;
  font-size: 1rem;
  z-index: 100;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

.spinner-small {
  width: 1.25rem;
  height: 1.25rem;
  border: 2px solid rgba(66, 133, 244, 0.2);
  border-top-color: rgb(66, 133, 244);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

/* Route Info Bar (Compact Google Maps Style) */
.route-info-bar {
  position: absolute;
  bottom: 1rem;
  left: 1rem;
  right: 5rem;
  background: rgba(255, 255, 255, 0.98);
  backdrop-filter: blur(10px);
  border-radius: 0.75rem;
  padding: 0.75rem 1rem;
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 0.75rem;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.2);
}

.selected-route-info {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
}

.route-time {
  color: rgb(30, 41, 59);
  font-size: 1.1rem;
  font-weight: 700;
  white-space: nowrap;
}

.route-dist {
  color: rgb(71, 85, 105);
  font-size: 0.875rem;
  font-weight: 500;
  white-space: nowrap;
}

.route-name {
  color: rgb(100, 116, 139);
  font-size: 0.8rem;
  margin-left: auto;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.start-nav-btn {
  padding: 0.625rem 1.25rem;
  background: rgb(37, 99, 235);
  color: white;
  border: none;
  border-radius: 0.5rem;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 0.375rem;
  transition: all 0.2s;
  white-space: nowrap;
}

.start-nav-btn:hover {
  background: rgb(29, 78, 216);
  transform: scale(1.02);
}

.start-nav-btn:active {
  transform: scale(0.98);
}

.start-nav-btn .nav-icon {
  width: 1rem;
  height: 1rem;
}

.cancel-btn {
  padding: 0.625rem;
  background: rgba(0, 0, 0, 0.05);
  color: rgb(71, 85, 105);
  border: none;
  border-radius: 0.5rem;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.cancel-btn:hover {
  background: rgba(0, 0, 0, 0.1);
}

.cancel-btn .nav-icon {
  width: 1.125rem;
  height: 1.125rem;
}
</style>
