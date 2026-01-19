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
      <button @click="initMap" class="retry-button">Request permission</button>
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
import { Navigation, X, Locate, LocateFixed } from 'lucide-vue-next'
import { Capacitor } from '@capacitor/core'
import { Geolocation } from '@capacitor/geolocation'
import { useSettings } from '../../composables/useSettings'
import { useNavigation } from '../../composables/useNavigation'
import { GoogleMapsNative } from '../../plugins/googlemaps'
import { GooglePlaces, type PlacePrediction, type PlaceDetails } from '../../plugins/googleplaces'
import TextToSpeech from '../../plugins/texttospeech'
import InfoPanel from './InfoPanel.vue'
import { Dialog } from '@capacitor/dialog';

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
let searchTimeout: NodeJS.Timeout | null = null
let etaUpdateInterval: NodeJS.Timeout | null = null
let routeUpdateInterval: NodeJS.Timeout | null = null
let currentLocation = { lat: 0, lng: 0 }

// Native map state
const nativeMarkers = new Map<string, string>()
const isMapInitialized = ref(false)

// TTS state - speed-adaptive announcement system
const ttsEnabled = Capacitor.isNativePlatform() // Only enable TTS on native platforms
const currentStepIndex = ref(0) // Track which navigation step we're on
const lastAnnouncedStepIndex = ref(-1) // Track last announced step to avoid repetition
let routeStepsData: any[] = [] // Store route steps for turn-by-turn

// Speed-adaptive TTS state
type AnnouncementState = {
  early: boolean     // 12-15s and 300-400m before turn
  final: boolean     // 4-6s and 60-80m before turn
  immediate: boolean // ≤20m before turn
}
const announcedStepsAt = new Map<number, AnnouncementState>() // Track announcement states per step
let currentSpeedMps = 0 // Current speed in meters per second
const MIN_SPEED_MPS = 3 // Minimum speed for time calculations (3 m/s ≈ 10.8 km/h)

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
const { unit, avoidTolls, theme, mapStyle, voiceInstructions } = useSettings()

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
      await GoogleMapsNative.setMapStyle({ mapId: 'navigation', style: DARK_MAP_STYLE })
    } else {
      // Reset to default light style
      await GoogleMapsNative.setMapStyle({ mapId: 'navigation', style: null })
    }
  } catch (err) {
    console.error('Error setting map style:', err)
  }
}

// Watch for map style changes
watch(mapStyle, () => {
  applyMapStyle()
})

// Watch for arrival (distance becomes 0 or very close to 0)
let hasArrived = false
watch(navRemainingDistance, (newDistance) => {
  // Detect arrival: distance is less than 50 meters (0.05 km) or 0
  if (!hasArrived && navIsNavigating.value && newDistance < 0.05 && newDistance >= 0) {
    hasArrived = true
    handleArrival()
  }
})

// Handle user arrival at destination
const handleArrival = async () => {

  await Dialog.alert({
    title: 'Success',
    message: `You have arrived at ${navDestination.value}!`,
  });

  await GoogleMapsNative.clearRoute({ mapId: 'navigation' })
  .catch(err => console.error('Error clearing route:', err))

   await GoogleMapsNative.removeMarker({ mapId: 'navigation', id: 'destination' }).catch(err => console.error('Error removing destination marker:', err))

    // Stop navigation - this will call stopNavigation() which handles cleanup
    await stopNavigation()

    // Reset arrival flag for next navigation
    hasArrived = false
}

// getCurrentLocation is now handled by the location listener
// Current location is cached in the currentLocation variable

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
      mapId: 'navigation',
      id: 'destination',
      lat: placeDetails.location.lat,
      lng: placeDetails.location.lng,
      title: placeDetails.name,
      color: '#EA4335' // Red color for destination
    })

    // Show the place on map
    await GoogleMapsNative.setCenter({
      mapId: 'navigation',
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
  GoogleMapsNative.clearRoute({ mapId: 'navigation' })
    .catch(err => console.error('Error clearing routes:', err))

  // Remove destination marker
  GoogleMapsNative.removeMarker({ mapId: 'navigation', id: 'destination' })
    .catch(err => console.error('Error removing marker:', err))

  // Reset route state
  availableRoutes.value = []
  selectedRouteIndex.value = 0
}

const requestLocationPermission = async () => {
    const permissionStatus = await Geolocation.checkPermissions()
    if (permissionStatus.location !== 'granted') {
      const result = await Geolocation.requestPermissions()
      if (result.location !== 'granted') {
        throw new Error('Location permission is required for navigation')
      }
    } else {
      console.log('Location permission already granted')
    }
}


const initMap = async () => {
  loading.value = true
  error.value = ''

  try {

    await requestLocationPermission()

    // Get immediate location to initialize map quickly
    console.log('Getting current location...')
    const position = await Geolocation.getCurrentPosition({
      enableHighAccuracy: true,
      timeout: 10000
    })

    // Cache initial location
    currentLocation.lat = position.coords.latitude
    currentLocation.lng = position.coords.longitude
    console.log(`Initial location: lat=${position.coords.latitude.toFixed(6)}, lng=${position.coords.longitude.toFixed(6)}`)

    // Create map immediately with current location
    await GoogleMapsNative.create({
      mapId: 'navigation',
      type: 'fullscreen',
      lat: position.coords.latitude,
      lng: position.coords.longitude,
      zoom: 17
    })

    await GoogleMapsNative.show({ mapId: 'navigation' })
    isMapInitialized.value = true
    console.log('Map initialized successfully')

    // Apply initial theme style
    await applyMapStyle()

    // Add initial location marker
    await GoogleMapsNative.addMarker({
      mapId: 'navigation',
      id: 'current-location',
      lat: position.coords.latitude,
      lng: position.coords.longitude,
      title: 'Your Location',
      color: '#4285F4',
      iconType: 'dot'
    })

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

    loading.value = false

    // Start native location tracking for continuous updates
    await GoogleMapsNative.startLocationTracking()

    // Set up location listener to update map with live location
    await GoogleMapsNative.addListener('locationUpdate', (location) => {
      // Cache location
      currentLocation.lat = location.latitude
      currentLocation.lng = location.longitude
      currentSpeedMps = location.speed || 0

      console.log(`GPS Update: lat=${location.latitude.toFixed(6)}, lng=${location.longitude.toFixed(6)}, isNavigating=${isNavigating.value}`)

      // Get bearing/heading for rotation
      const bearing = location.bearing || 0
      console.log(`GPS Bearing: ${bearing}, Speed: ${currentSpeedMps.toFixed(2)} m/s`)

      // Update marker based on navigation state
      if (isNavigating.value) {
        // During navigation: use arrow marker with rotation
        GoogleMapsNative.updateMarker({
          mapId: 'navigation',
          id: 'current-location',
          lat: location.latitude,
          lng: location.longitude,
          rotation: bearing,
          flat: true
        }).catch(err => console.error('Error updating arrow marker:', err))
      } else {
        // When not navigating: use blue dot marker
        GoogleMapsNative.addMarker({
          mapId: 'navigation',
          id: 'current-location',
          lat: location.latitude,
          lng: location.longitude,
          title: 'Your Location',
          color: '#4285F4',
          iconType: 'dot'
        }).catch(err => console.error('Error updating native marker:', err))
      }

      // Center map on location only when following is enabled
      if (isFollowingLocation.value) {
        GoogleMapsNative.setCenter({
          mapId: 'navigation',
          lat: location.latitude,
          lng: location.longitude,
          zoom: isNavigating.value ? 19 : 17,
          tilt: isNavigating.value ? 45 : 0,
          bearing: isNavigating.value ? bearing : undefined,
          animate: true
        }).catch(err => console.error('Error centering native map:', err))
      }

      // Update route progress during navigation
      if (isNavigating.value && routePath.value.length > 0) {
        updateRouteProgress({ lat: location.latitude, lng: location.longitude })
      }
    })

    console.log('Location tracking started')

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

    // Use cached current location (updated by location listener)
    const origin = `${currentLocation.lat},${currentLocation.lng}`
    const destination = `${selectedPlace.value.location.lat},${selectedPlace.value.location.lng}`

    // Fetch routes from Google Directions API using native method
    const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY

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
    // Use cached current location

    // Calculate the center point between current location and destination
    const destLat = selectedPlace.value.location.lat
    const destLng = selectedPlace.value.location.lng
    const centerLat = (currentLocation.lat + destLat) / 2
    const centerLng = (currentLocation.lng + destLng) / 2

    // Calculate distance to determine zoom level
    const latDiff = Math.abs(currentLocation.lat - destLat)
    const lngDiff = Math.abs(currentLocation.lng - destLng)
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
      mapId: 'navigation',
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
      mapId: 'navigation',
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
  await GoogleMapsNative.clearRoute({ mapId: 'navigation' })
  await drawAllRoutes()
}

const cancelRouteSelection = async () => {
  // Clear routes from map
  await GoogleMapsNative.clearRoute({ mapId: 'navigation' })

  // Reset state
  showRouteCalculated.value = false
  availableRoutes.value = []
  selectedRouteIndex.value = 0
}

// Helper function to parse maneuver from Google Maps step
const parseManeuver = (step: any): string => {
  if (!step) return 'continue'

  const instruction = step.html_instructions?.toLowerCase() || step.maneuver || ''

  // Extract maneuver type from instruction or maneuver field
  if (instruction.includes('turn left') || step.maneuver === 'turn-left') {
    return 'turn left'
  } else if (instruction.includes('turn right') || step.maneuver === 'turn-right') {
    return 'turn right'
  } else if (instruction.includes('slight left') || step.maneuver === 'turn-slight-left') {
    return 'slight left'
  } else if (instruction.includes('slight right') || step.maneuver === 'turn-slight-right') {
    return 'slight right'
  } else if (instruction.includes('sharp left') || step.maneuver === 'turn-sharp-left') {
    return 'sharp left'
  } else if (instruction.includes('sharp right') || step.maneuver === 'turn-sharp-right') {
    return 'sharp right'
  } else if (instruction.includes('u-turn') || instruction.includes('u turn') || step.maneuver === 'uturn-left' || step.maneuver === 'uturn-right') {
    return 'make a u-turn'
  } else if (instruction.includes('merge') || step.maneuver === 'merge') {
    return 'merge'
  } else if (instruction.includes('ramp') || step.maneuver === 'ramp-left' || step.maneuver === 'ramp-right') {
    return 'take the ramp'
  } else if (instruction.includes('roundabout') || step.maneuver?.includes('roundabout')) {
    return 'enter the roundabout'
  } else if (instruction.includes('keep left') || step.maneuver === 'keep-left') {
    return 'keep left'
  } else if (instruction.includes('keep right') || step.maneuver === 'keep-right') {
    return 'keep right'
  } else if (instruction.includes('straight') || step.maneuver === 'straight') {
    return 'continue straight'
  } else if (instruction.includes('ferry')) {
    return 'take the ferry'
  }

  return 'continue'
}

// Helper to get road name from step
const getRoadName = (step: any): string => {
  if (!step || !step.html_instructions) return ''

  // Try to extract road name from instructions
  const match = step.html_instructions.match(/onto\s+<b>([^<]+)<\/b>/i) ||
                step.html_instructions.match(/on\s+<b>([^<]+)<\/b>/i)

  return match ? ` onto ${match[1]}` : ''
}

// Helper to get speed-adaptive distance threshold
const getDistanceThreshold = (speedKmh: number, type: 'early' | 'final'): number => {
  if (type === 'early') {
    // Early warning distances based on speed
    if (speedKmh < 20) return 200       // Slow speed: 200m
    if (speedKmh < 60) return 400       // Medium speed: 400m
    return 600                          // High speed: 600m
  } else {
    // Final instruction distances based on speed
    if (speedKmh < 20) return 70        // Slow speed: 70m
    if (speedKmh < 60) return 80        // Medium speed: 80m
    return 100                          // High speed: 100m
  }
}

const announceManeuver = async (
  step: any,
  distanceInMeters: number,
  announcementType: 'early' | 'final' | 'immediate'
) => {
  if (!ttsEnabled || !voiceInstructions.value || !step) return

  const maneuver = parseManeuver(step)
  const roadName = getRoadName(step)

  let announcement = ''

  // Build announcement based on type
  if (announcementType === 'early') {
    // Early warning: announce with distance
    const speedKmh = currentSpeedMps * 3.6
    const threshold = getDistanceThreshold(speedKmh, 'early')

    // Round to nearest 100m for clarity
    const roundedDistance = Math.round(distanceInMeters / 100) * 100
    announcement = `In ${roundedDistance} meters, ${maneuver}${roadName}`
  } else if (announcementType === 'final') {
    // Final instruction: announce with shorter distance
    const roundedDistance = Math.round(distanceInMeters / 10) * 10 // Round to nearest 10m
    announcement = `In ${roundedDistance} meters, ${maneuver}${roadName}`
  } else {
    // Immediate action
    announcement = `${maneuver}${roadName} now`
  }

  if (announcement) {
    try {
      console.log(`TTS [${announcementType.toUpperCase()}]: "${announcement}" (actual distance: ${distanceInMeters.toFixed(0)}m, speed: ${(currentSpeedMps * 3.6).toFixed(1)} km/h)`)
      await TextToSpeech.speak({ text: announcement, rate: 0.9 })
    } catch (error) {
      console.error('TTS error:', error)
    }
  }
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

  // Turn-by-turn voice announcement logic (speed-adaptive with dual thresholds)
  if (ttsEnabled && voiceInstructions.value && routeStepsData.length > 0) {
    // Check if we're close to destination (last 50 meters)
    if (remainingDist < 50 && lastAnnouncedStepIndex.value !== -2) {
      try {
        await TextToSpeech.speak({ text: 'You have arrived at your destination', rate: 0.9 })
        lastAnnouncedStepIndex.value = -2 // Special value to indicate arrival announced
      } catch (error) {
        console.error('TTS error:', error)
      }
      return
    }

    // Get current speed in km/h for threshold calculations
    const currentSpeedKmh = currentSpeedMps * 3.6

    // Calculate actual distance from current position to each step's start location
    let foundStep = false

    for (let i = currentStepIndex.value; i < routeStepsData.length; i++) {
      const step = routeStepsData[i]

      // Skip if no location data
      if (!step.start_location) continue

      // Calculate distance from current location to this step's start point
      const distanceToStepStart = calculateDistance(
        currentLocation.lat,
        currentLocation.lng,
        step.start_location.lat,
        step.start_location.lng
      )

      // Calculate time to turn using: timeToTurn = distance / max(currentSpeed, minSpeed)
      const effectiveSpeed = Math.max(currentSpeedMps, MIN_SPEED_MPS)
      const timeToTurnSeconds = distanceToStepStart / effectiveSpeed

      // If we're past this step (more than 30m away from its start), move to next step
      if (distanceToStepStart > 30 && i === currentStepIndex.value) {
        // Check if we might have passed this step
        const distanceToStepEnd = step.end_location ? calculateDistance(
          currentLocation.lat,
          currentLocation.lng,
          step.end_location.lat,
          step.end_location.lng
        ) : Infinity

        // If we're closer to the end than the start, we've passed this step
        if (distanceToStepEnd < distanceToStepStart) {
          currentStepIndex.value++
          announcedStepsAt.delete(i) // Clear announcements for passed step
          continue
        }
      }

      // We found the current step - check if we should announce
      if (i === currentStepIndex.value) {
        foundStep = true

        // Get or initialize announcement state for this step
        const state = announcedStepsAt.get(i) || { early: false, final: false, immediate: false }

        // Check for complex maneuvers (next turn within 60m)
        let hasComplexManeuver = false
        if (i + 1 < routeStepsData.length) {
          const nextStep = routeStepsData[i + 1]
          if (nextStep.start_location && step.end_location) {
            const distanceBetweenTurns = calculateDistance(
              step.end_location.lat,
              step.end_location.lng,
              nextStep.start_location.lat,
              nextStep.start_location.lng
            )
            hasComplexManeuver = distanceBetweenTurns < 60
          }
        }

        // IMMEDIATE announcement: ≤20m
        if (!state.immediate && distanceToStepStart <= 20) {
          console.log(`Immediate announcement: ${distanceToStepStart.toFixed(0)}m`)
          await announceManeuver(step, distanceToStepStart, 'immediate')
          state.immediate = true
          announcedStepsAt.set(i, state)
        }
        // FINAL announcement: 4-6 seconds AND appropriate distance
        else if (!state.final && timeToTurnSeconds <= 6 && timeToTurnSeconds >= 4) {
          const finalDistThreshold = getDistanceThreshold(currentSpeedKmh, 'final')

          if (distanceToStepStart <= finalDistThreshold + 20 && distanceToStepStart >= finalDistThreshold - 20) {
            console.log(`Final announcement: ${distanceToStepStart.toFixed(0)}m, ${timeToTurnSeconds.toFixed(1)}s`)

            if (hasComplexManeuver) {
              // Announce complex maneuver
              const nextStep = routeStepsData[i + 1]
              const nextManeuver = parseManeuver(nextStep)
              const maneuver = parseManeuver(step)
              const roadName = getRoadName(step)
              const announcement = `${maneuver}${roadName}, then immediately ${nextManeuver}`

              try {
                await TextToSpeech.speak({ text: announcement, rate: 0.9 })
                console.log(`TTS [FINAL-COMPLEX]: "${announcement}"`)
              } catch (error) {
                console.error('TTS error:', error)
              }
            } else {
              await announceManeuver(step, distanceToStepStart, 'final')
            }

            state.final = true
            announcedStepsAt.set(i, state)
          }
        }
        // EARLY announcement: 12-15 seconds AND appropriate distance
        else if (!state.early && timeToTurnSeconds <= 15 && timeToTurnSeconds >= 12) {
          const earlyDistThreshold = getDistanceThreshold(currentSpeedKmh, 'early')

          if (distanceToStepStart <= earlyDistThreshold + 50 && distanceToStepStart >= earlyDistThreshold - 50) {
            console.log(`Early announcement: ${distanceToStepStart.toFixed(0)}m, ${timeToTurnSeconds.toFixed(1)}s`)
            await announceManeuver(step, distanceToStepStart, 'early')
            state.early = true
            announcedStepsAt.set(i, state)
          }
        }

        break // Found and processed current step
      }
    }

    // If we didn't find a valid step, reset to search from beginning
    if (!foundStep && currentStepIndex.value > 0) {
      currentStepIndex.value = 0
    }
  }

  // Trim the route if we've moved past the first point
  if (closestIndex > 0) {
    console.log(`Trimming route: removing first ${closestIndex} points (up to closest), ${routePath.value.length} -> ${routePath.value.length - closestIndex}`)

    // Remove all points before the closest point
    routePath.value = routePath.value.slice(closestIndex)

    // Update shared navigation state with trimmed route
    navUpdateRoutePath(routePath.value)

    // Redraw the route when trimming happens
    try {
      await GoogleMapsNative.clearRoute({ mapId: 'navigation' })
      console.log('Route cleared for redraw after trimming')

      if (routePath.value.length > 0) {
        // Draw route from current location to the remaining route points
        const routePoints = [
          { lat: currentLocation.lat, lng: currentLocation.lng }, // Start from current location
          ...routePath.value.map((p: any) => ({ lat: p.lat, lng: p.lng }))
        ]

        await GoogleMapsNative.drawRoute({
          mapId: 'navigation',
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
    // Reset TTS state
    currentStepIndex.value = 0
    lastAnnouncedStepIndex.value = -1
    announcedStepsAt.clear()
    currentSpeedMps = 0 // Reset speed

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

    // Store route steps for turn-by-turn TTS
    routeStepsData = selectedRoute.steps || []

    // Start navigation with shared state (including ETA and route steps for turn-by-turn)
    const destName = selectedPlace.value.name || 'Destination'
    navStartNavigation(destName, totalDist, durationSeconds, selectedRoute.steps)

    // Announce navigation start
    if (ttsEnabled && voiceInstructions.value) {
      try {
        await TextToSpeech.speak({
          text: `Navigation started. Your destination is ${destName}. Total distance is ${totalDist.toFixed(1)} kilometers.`,
          rate: 0.9
        })
      } catch (error) {
        console.error('TTS error:', error)
      }
    }

    // Start navigation mode
    isNavigating.value = true

    // Enable location following for navigation (always on during navigation)
    isFollowingLocation.value = true

    // Store the selected route path for progress tracking
    routePath.value = decodePolyline(selectedRoute.polyline)

    // Update shared navigation state with initial route
    navUpdateRoutePath(routePath.value)

    // Replace current location marker with arrow marker
    await GoogleMapsNative.removeMarker({ mapId: 'navigation', id: 'current-location' })

    await GoogleMapsNative.addMarker({
      mapId: 'navigation',
      id: 'current-location',
      lat: currentLocationPos.lat,
      lng: currentLocationPos.lng,
      iconType: 'arrow',
      color: '#1A73E8', // Blue arrow for navigation
      rotation: 0,
      flat: true
    })

    // Clear all other routes, keep only the selected one
    await GoogleMapsNative.clearRoute({ mapId: 'navigation' })

    const points = decodePolyline(selectedRoute.polyline)
    await GoogleMapsNative.drawRoute({
      mapId: 'navigation',
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
      mapId: 'navigation',
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
  // Stop any ongoing TTS
  if (ttsEnabled) {
    try {
      await TextToSpeech.stop()
    } catch (error) {
      console.error('Error stopping TTS:', error)
    }
  }

  // Reset TTS state
  currentStepIndex.value = 0
  lastAnnouncedStepIndex.value = -1
  announcedStepsAt.clear()
  routeStepsData = []
  currentSpeedMps = 0 // Reset speed

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
  await GoogleMapsNative.clearRoute({ mapId: 'navigation' }).catch(err => console.error('Error clearing route:', err))

  // Reset navigation state
  isNavigating.value = false
  showRouteCalculated.value = false
  selectedPlace.value = null
  routePath.value = []

  // Stop following location
  isFollowingLocation.value = false

  // Replace arrow marker with blue dot marker
  await GoogleMapsNative.removeMarker({ mapId: 'navigation', id: 'current-location' })
  await GoogleMapsNative.addMarker({
    mapId: 'navigation',
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
    // Use cached current location (updated by location listener)
    // Enable location following
    isFollowingLocation.value = true

    // Center native map on cached location
    await GoogleMapsNative.setCenter({
      mapId: 'navigation',
      lat: currentLocation.lat,
      lng: currentLocation.lng,
      zoom: 17,
      animate: true
    })
  } catch (err) {
    console.error('Error centering on current location:', err)
  }
}

// Check permissions and initialize map
let permissionCheckInterval: number | null = null

const checkPermissionsAndInitMap = async () => {
  console.log('NavigationMap: Checking if permissions are granted...')

  try {
    const permissionStatus = await Geolocation.checkPermissions()
    console.log('NavigationMap: Permission status =', permissionStatus.location)

    if (permissionStatus.location === 'granted') {
      console.log('NavigationMap: Permissions granted! Initializing map...')

      // Clear interval if running
      if (permissionCheckInterval) {
        clearInterval(permissionCheckInterval)
        permissionCheckInterval = null
      }

      // Initialize map
      await initMap()
    } else {
      console.log('NavigationMap: Permissions not granted yet')
      error.value = 'Location permission is required for navigation'
      loading.value = false
    }
  } catch (err) {
    console.error('NavigationMap: Error checking permissions:', err)
    error.value = 'Failed to check permissions'
    loading.value = false
  }
}

onMounted(async () => {
  console.log('NavigationMap: Component mounted')

  // Check permissions immediately
  await checkPermissionsAndInitMap()

  // If map didn't initialize (no permissions), poll every 500ms
  if (!isMapInitialized.value) {
    console.log('NavigationMap: Setting up permission polling...')
    permissionCheckInterval = window.setInterval(async () => {
      await checkPermissionsAndInitMap()
    }, 500)
  }
})

// Expose cleanup method for parent component
defineExpose({
  async cleanupNavigation() {
    console.log('NavigationMap: Cleaning up navigation from parent call')
    await stopNavigation()
  }
})

onUnmounted(async () => {
  // Clear permission check interval
  if (permissionCheckInterval) {
    clearInterval(permissionCheckInterval)
    permissionCheckInterval = null
  }

  // Stop native location tracking
  try {
    await GoogleMapsNative.stopLocationTracking()
  } catch (err) {
    console.error('Error stopping location tracking:', err)
  }

  // Cleanup native map
  try {
    await GoogleMapsNative.destroy({ mapId: 'navigation' })
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

/* Test Arrival Button */
.test-arrival-button {
  position: absolute;
  bottom: 2rem;
  left: 1rem;
  padding: 0.75rem 1.5rem;
  background: rgb(239, 68, 68);
  color: white;
  border: none;
  border-radius: 0.5rem;
  font-size: 0.9rem;
  font-weight: 700;
  cursor: pointer;
  z-index: 100;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  transition: all 0.2s;
}

.test-arrival-button:hover {
  background: rgb(220, 38, 38);
  transform: scale(1.05);
}
</style>
