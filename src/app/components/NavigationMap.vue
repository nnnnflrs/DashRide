<template>
  <div class="navigation-map-container">
    <div ref="mapContainer" class="map-container"></div>
    
    <!-- Info Panel - Show only when navigating -->
    <InfoPanel
      v-if="navIsNavigating"
      :destination="navDestination"
      :totalDistance="navTotalDistance"
      :remainingDistance="navRemainingDistance"
      :unit="unit"
    />
    
    <!-- Search Input -->
    <div v-if="!loading && !error" class="search-container">
      <input
        ref="searchInput"
        v-model="searchQuery"
        type="text"
        placeholder="Search location..."
        class="search-input"
        @focus="onSearchFocus"
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

    <!-- Directions Button (Step 1: Show after place selection) -->
    <button
      v-if="selectedPlace && !showRouteCalculated && !isNavigating"
      @click="showDirections"
      class="directions-button"
    >
      <Navigation class="nav-icon" />
      Directions
    </button>

    <!-- Start Navigation Button (Step 2: Show after route is calculated) -->
    <button
      v-if="showRouteCalculated && !isNavigating"
      @click="startNavigation"
      class="start-nav-button"
    >
      <Navigation class="nav-icon" />
      Start
    </button>

    <!-- Stop Navigation Button (Step 3: Show during navigation) -->
    <button
      v-if="isNavigating"
      @click="stopNavigation"
      class="stop-nav-button"
    >
      <X class="nav-icon" />
      Stop
    </button>

    <div v-if="loading" class="loading-overlay">
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
      <Navigation class="location-icon" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { Geolocation } from '@capacitor/geolocation'
import { Navigation, X } from 'lucide-vue-next'
import { useSettings } from '../../composables/useSettings'
import { useNavigation } from '../../composables/useNavigation'
import InfoPanel from './InfoPanel.vue'

const mapContainer = ref<HTMLDivElement | null>(null)
const searchInput = ref<HTMLInputElement | null>(null)
const searchQuery = ref('')
const loading = ref(true)
const error = ref('')
const map = ref<any>(null)
const currentLocationMarker = ref<any>(null)
const isFollowingLocation = ref(true)
const searchMarker = ref<any>(null)
const selectedPlace = ref<any>(null)
const isNavigating = ref(false)
const showRouteCalculated = ref(false)
const directionsRenderer = ref<any>(null)
const directionsService = ref<any>(null)
const fallbackLine = ref<any>(null)
const routePath = ref<any[]>([])
const remainingRoutePolyline = ref<any>(null)
let watchId: string | null = null
let autocomplete: any = null

const API_KEY = import.meta.env.VITE_GOOGLE_MAPS_API_KEY

// Get settings
const { avoidTolls, unit } = useSettings()

// Get navigation state - destructure and rename to avoid conflicts
const {
  isNavigating: navIsNavigating,
  totalDistance: navTotalDistance,
  remainingDistance: navRemainingDistance,
  destination: navDestination,
  startNavigation: navStartNavigation,
  updateRemainingDistance: navUpdateRemainingDistance,
  stopNavigation: navStopNavigation
} = useNavigation()

const getCurrentLocation = async () => {
  const position = await Geolocation.getCurrentPosition({
    enableHighAccuracy: true,
    timeout: 10000,
    maximumAge: 0
  })
  
  return {
    lat: position.coords.latitude,
    lng: position.coords.longitude,
    accuracy: position.coords.accuracy
  }
}

const loadGoogleMapsScript = (): Promise<void> => {
  return new Promise((resolve, reject) => {
    if (typeof google !== 'undefined' && google.maps) {
      resolve()
      return
    }

    const script = document.createElement('script')
    script.src = `https://maps.googleapis.com/maps/api/js?key=${API_KEY}&libraries=places,geometry`
    script.async = true
    script.defer = true
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('Failed to load Google Maps'))
    document.head.appendChild(script)
  })
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
    const currentLocation = await getCurrentLocation()

    // Load Google Maps
    await loadGoogleMapsScript()

    if (!mapContainer.value) {
      throw new Error('Map container not found')
    }

    // Initialize map
    map.value = new google.maps.Map(mapContainer.value, {
      center: currentLocation,
      zoom: 17,
      mapTypeControl: false,
      streetViewControl: false,
      fullscreenControl: false,
      zoomControl: false,
      scaleControl: false,
      panControl: false,
      rotateControl: true, // Enable rotation with two-finger gesture
      tilt: 45, // Enable 3D tilt view (required for rotation)
      gestureHandling: 'greedy', // Allows pinch-to-zoom, pan, and rotate gestures
      disableDefaultUI: true // Disable all default UI controls
    })

    // Add current location marker
    currentLocationMarker.value = new google.maps.Marker({
      position: currentLocation,
      map: map.value,
      icon: {
        path: google.maps.SymbolPath.CIRCLE,
        scale: 10,
        fillColor: '#4285F4',
        fillOpacity: 1,
        strokeColor: '#ffffff',
        strokeWeight: 3
      },
      title: 'Your Location'
    })

    // Add accuracy circle
    new google.maps.Circle({
      map: map.value,
      center: currentLocation,
      radius: currentLocation.accuracy,
      fillColor: '#4285F4',
      fillOpacity: 0.1,
      strokeColor: '#4285F4',
      strokeOpacity: 0.3,
      strokeWeight: 1
    })

    // Watch position changes
    watchId = await Geolocation.watchPosition(
      {
        enableHighAccuracy: true,
        timeout: 5000,
        maximumAge: 0
      },
      (position) => {
        if (position && map.value && currentLocationMarker.value) {
          const newLocation = {
            lat: position.coords.latitude,
            lng: position.coords.longitude
          }
          
          currentLocationMarker.value.setPosition(newLocation)
          
          // Update arrow rotation if heading is available and navigating
          if (isNavigating.value && position.coords.heading !== null && position.coords.heading !== undefined) {
            const currentIcon = currentLocationMarker.value.getIcon()
            if (currentIcon && currentIcon.path === google.maps.SymbolPath.FORWARD_CLOSED_ARROW) {
              currentLocationMarker.value.setIcon({
                ...currentIcon,
                rotation: position.coords.heading
              })
            }
            
            // Update route progress during navigation
            updateRouteProgress(newLocation)
          }
          
          if (isFollowingLocation.value) {
            map.value.panTo(newLocation)
            
            // Keep zoomed in during navigation
            if (isNavigating.value && map.value.getZoom() < 18) {
              map.value.setZoom(19)
            }
          }
        }
      }
    )

    // Detect when user manually moves the map
    map.value.addListener('dragstart', () => {
      isFollowingLocation.value = false
    })

    // Initialize Directions Service and Renderer
    directionsService.value = new google.maps.DirectionsService()
    directionsRenderer.value = new google.maps.DirectionsRenderer({
      map: map.value,
      suppressMarkers: false,
      polylineOptions: {
        strokeColor: '#4285F4',
        strokeWeight: 5
      }
    })

    loading.value = false
  } catch (err: any) {
    console.error('Map initialization error:', err)
    error.value = err.message || 'Failed to load map'
    loading.value = false
  }
}

const onSearchFocus = () => {
  // Initialize autocomplete only once when user focuses on input
  if (autocomplete || !searchInput.value || !map.value) return
  
  initAutocomplete()
}

const initAutocomplete = () => {
  // Create autocomplete
  autocomplete = new google.maps.places.Autocomplete(searchInput.value!, {
    fields: ['place_id', 'geometry', 'name', 'formatted_address', 'types']
  })

  // Bind to map bounds
  autocomplete.bindTo('bounds', map.value)

  // Listen for place selection
  autocomplete.addListener('place_changed', () => {
    const place = autocomplete.getPlace()

    if (!place.geometry || !place.geometry.location) {
      return
    }

    // Update search query with selected place name
    searchQuery.value = place.name || place.formatted_address || ''

    // Check if place is specific enough (not a large region/province)
    const validTypes = [
      'establishment',
      'point_of_interest',
      'street_address',
      'premise',
      'subpremise',
      'route',
      'locality',
      'sublocality',
      'neighborhood',
      'colloquial_area'
    ]
    const isSpecificPlace = place.types?.some((type: string) => validTypes.includes(type))

    // Store the place if it's specific enough
    if (isSpecificPlace) {
      selectedPlace.value = place
    } else {
      selectedPlace.value = null
    }

    // Remove previous search marker
    if (searchMarker.value) {
      searchMarker.value.setMap(null)
    }

    // Add marker for searched place
    searchMarker.value = new google.maps.Marker({
      position: place.geometry.location,
      map: map.value,
      icon: {
        path: google.maps.SymbolPath.CIRCLE,
        scale: 8,
        fillColor: '#ef4444',
        fillOpacity: 1,
        strokeColor: '#ffffff',
        strokeWeight: 2
      },
      title: place.name
    })

    // Zoom to the place
    if (place.geometry.viewport) {
      map.value.fitBounds(place.geometry.viewport)
    } else {
      map.value.setCenter(place.geometry.location)
      map.value.setZoom(17)
    }

    // Disable location following
    isFollowingLocation.value = false
  })
}

const clearSearch = () => {
  // Clear the search input
  searchQuery.value = ''
  
  // Remove search marker if exists
  if (searchMarker.value) {
    searchMarker.value.setMap(null)
    searchMarker.value = null
  }
  
  // Clear selected place
  selectedPlace.value = null
  
  // Clear route if calculated
  if (directionsRenderer.value) {
    directionsRenderer.value.setDirections({ routes: [] })
  }
  
  // Clear fallback line if exists
  if (fallbackLine.value) {
    fallbackLine.value.setMap(null)
    fallbackLine.value = null
  }
  
  // Reset states
  showRouteCalculated.value = false
  isNavigating.value = false
  
  // Restore normal marker if it was changed
  updateMarkerToCircle()
  
  // Clear the autocomplete input
  if (searchInput.value) {
    searchInput.value.value = ''
  }
}

const showDirections = async () => {
  if (!selectedPlace.value || !map.value) return

  try {
    const origin = await getCurrentLocation()
    const destination = selectedPlace.value.geometry.location

    // Calculate distance
    const distance = google.maps.geometry.spherical.computeDistanceBetween(origin, destination)

    // If destination is very close, just show it
    if (distance < 50) {
      alert('You are already at the destination!')
      map.value.panTo(destination)
      map.value.setZoom(19)
      return
    }

    // Request directions with DRIVING mode
    directionsService.value.route(
      {
        origin,
        destination,
        travelMode: google.maps.TravelMode.DRIVING,
        avoidHighways: false,
        avoidTolls: avoidTolls.value
      },
      (result: any, status: any) => {
        if (status === 'OK') {
          // Store the route path for progress tracking
          const route = result.routes[0]
          routePath.value = []
          route.legs.forEach((leg: any) => {
            leg.steps.forEach((step: any) => {
              step.path.forEach((point: any) => {
                routePath.value.push({
                  lat: point.lat(),
                  lng: point.lng()
                })
              })
            })
          })
          
          // Display the route
          directionsRenderer.value.setDirections(result)
          showRouteCalculated.value = true
        } else if (status === 'ZERO_RESULTS') {
          // Draw a straight line as visual fallback
          fallbackLine.value = new google.maps.Polyline({
            path: [origin, destination],
            geodesic: true,
            strokeColor: '#FF6B6B',
            strokeOpacity: 0.8,
            strokeWeight: 4,
            map: map.value
          })
          
          // Fit bounds to show both points
          const bounds = new google.maps.LatLngBounds()
          bounds.extend(origin)
          bounds.extend(destination)
          map.value.fitBounds(bounds)
          
          showRouteCalculated.value = true
        } else {
          let errorMsg = 'Could not calculate route. '
          if (status === 'REQUEST_DENIED') {
            errorMsg += 'API key issue.'
          } else if (status === 'OVER_QUERY_LIMIT') {
            errorMsg += 'API quota exceeded.'
          } else if (status === 'INVALID_REQUEST') {
            errorMsg += 'Invalid request.'
          } else {
            errorMsg += `Error: ${status}`
          }
          alert(errorMsg)
        }
      }
    )
  } catch (err) {
    console.error('Error calculating directions:', err)
    alert('Could not get your current location.')
  }
}

const smoothZoomTo = (targetZoom: number, targetTilt: number = 0, duration: number = 1000) => {
  if (!map.value) return
  
  const currentZoom = map.value.getZoom()
  const currentTilt = map.value.getTilt() || 0
  
  const zoomDiff = targetZoom - currentZoom
  const tiltDiff = targetTilt - currentTilt
  
  // Calculate number of steps based on duration (60fps = 16.67ms per frame)
  const steps = Math.ceil(duration / 16.67)
  const zoomIncrement = zoomDiff / steps
  const tiltIncrement = tiltDiff / steps
  
  let step = 0
  const animationInterval = setInterval(() => {
    if (step >= steps || !map.value) {
      clearInterval(animationInterval)
      // Ensure final values are set
      if (map.value) {
        map.value.setZoom(targetZoom)
        map.value.setTilt(targetTilt)
      }
      return
    }
    
    step++
    const newZoom = currentZoom + (zoomIncrement * step)
    const newTilt = currentTilt + (tiltIncrement * step)
    
    map.value.setZoom(newZoom)
    map.value.setTilt(newTilt)
  }, 16.67) // ~60fps
}

const updateRouteProgress = (currentLocation: any) => {
  if (!routePath.value.length || !map.value) return
  
  // Find the closest point on the route to current location
  let closestIndex = 0
  let minDistance = Infinity
  
  routePath.value.forEach((point, index) => {
    const distance = google.maps.geometry.spherical.computeDistanceBetween(
      new google.maps.LatLng(currentLocation.lat, currentLocation.lng),
      new google.maps.LatLng(point.lat, point.lng)
    )
    
    if (distance < minDistance) {
      minDistance = distance
      closestIndex = index
    }
  })
  
  // Get remaining route points (from closest point to end)
  const remainingPoints = routePath.value.slice(closestIndex)
  
  // Calculate remaining distance
  let remainingDist = 0
  for (let i = 0; i < remainingPoints.length - 1; i++) {
    remainingDist += google.maps.geometry.spherical.computeDistanceBetween(
      new google.maps.LatLng(remainingPoints[i].lat, remainingPoints[i].lng),
      new google.maps.LatLng(remainingPoints[i + 1].lat, remainingPoints[i + 1].lng)
    )
  }
  
  // Update navigation state with remaining distance in km
  navUpdateRemainingDistance(remainingDist / 1000)
  
  // Remove old polyline if exists
  if (remainingRoutePolyline.value) {
    remainingRoutePolyline.value.setMap(null)
  }
  
  // Draw new polyline for remaining route
  if (remainingPoints.length > 1) {
    remainingRoutePolyline.value = new google.maps.Polyline({
      path: remainingPoints,
      geodesic: true,
      strokeColor: '#4285F4',
      strokeOpacity: 1.0,
      strokeWeight: 5,
      map: map.value
    })
  }
  
  // Hide the original directions renderer polyline
  if (directionsRenderer.value) {
    directionsRenderer.value.setOptions({
      polylineOptions: {
        strokeOpacity: 0
      }
    })
  }
}

const startNavigation = async () => {
  if (!showRouteCalculated.value || !selectedPlace.value) return
  
  try {
    // Get current location
    const currentLocation = await getCurrentLocation()
    
    // Calculate total distance to destination
    const destination = selectedPlace.value.geometry.location
    const totalDist = google.maps.geometry.spherical.computeDistanceBetween(
      new google.maps.LatLng(currentLocation.lat, currentLocation.lng),
      destination
    )
    
    // Start navigation with destination name and total distance (in km)
    const destName = selectedPlace.value.name || selectedPlace.value.formatted_address || 'Destination'
    navStartNavigation(destName, totalDist / 1000)
    
    // Center on current location first
    if (map.value) {
      map.value.panTo(currentLocation)
    }
    
    // Start navigation mode
    isNavigating.value = true
    
    // Change current location marker to arrow
    updateMarkerToArrow()
    
    // Enable location following for navigation
    isFollowingLocation.value = true
    
    // Initialize route progress
    updateRouteProgress(currentLocation)
    
    // Smooth zoom transition to navigation view (1.5 seconds)
    smoothZoomTo(19, 45, 1500)
  } catch (err) {
    console.error('Error starting navigation:', err)
    alert('Could not get your current location.')
  }
}

const stopNavigation = () => {
  // Clear the route
  if (directionsRenderer.value) {
    directionsRenderer.value.setDirections({ routes: [] })
  }
  
  // Clear remaining route polyline
  if (remainingRoutePolyline.value) {
    remainingRoutePolyline.value.setMap(null)
    remainingRoutePolyline.value = null
  }
  
  // Clear fallback line if exists
  if (fallbackLine.value) {
    fallbackLine.value.setMap(null)
    fallbackLine.value = null
  }
  
  // Clear route path
  routePath.value = []
  
  // Stop navigation in shared state
  navStopNavigation()
  
  // Reset navigation states
  isNavigating.value = false
  showRouteCalculated.value = false
  
  // Restore normal marker
  updateMarkerToCircle()
  
  // Reset zoom to normal level
  if (map.value) {
    map.value.setZoom(17)
    map.value.setTilt(0) // Reset tilt
  }
}

const updateMarkerToArrow = () => {
  if (!currentLocationMarker.value) return
  
  currentLocationMarker.value.setIcon({
    path: google.maps.SymbolPath.FORWARD_CLOSED_ARROW,
    scale: 6,
    fillColor: '#4285F4',
    fillOpacity: 1,
    strokeColor: '#ffffff',
    strokeWeight: 3,
    rotation: 0,
    anchor: new google.maps.Point(0, 2.5)
  })
}

const updateMarkerToCircle = () => {
  if (!currentLocationMarker.value) return
  
  currentLocationMarker.value.setIcon({
    path: google.maps.SymbolPath.CIRCLE,
    scale: 10,
    fillColor: '#4285F4',
    fillOpacity: 1,
    strokeColor: '#ffffff',
    strokeWeight: 3
  })
}

const centerOnCurrentLocation = async () => {
  try {
    const currentLocation = await getCurrentLocation()

    if (map.value) {
      map.value.panTo(currentLocation)
      map.value.setZoom(17)
      isFollowingLocation.value = true
    }
  } catch (err) {
    console.error('Error getting current location:', err)
  }
}

onMounted(() => {
  initMap()
})

onUnmounted(() => {
  if (watchId) {
    Geolocation.clearWatch({ id: watchId })
  }
})
</script>

<style scoped>
.navigation-map-container {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.map-container {
  width: 100%;
  height: 100%;
}

.loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.8);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  z-index: 10;
}

.spinner {
  width: 3rem;
  height: 3rem;
  border: 4px solid rgba(59, 130, 246, 0.3);
  border-top-color: rgb(59, 130, 246);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.loading-text {
  color: white;
  font-size: 1rem;
}

.error-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.9);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  padding: 2rem;
  z-index: 10;
}

.error-text {
  color: rgb(239, 68, 68);
  font-size: 1rem;
  text-align: center;
}

.retry-button {
  padding: 0.75rem 1.5rem;
  background: rgb(59, 130, 246);
  color: white;
  border: none;
  border-radius: 0.5rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
}

.retry-button:hover {
  background: rgb(37, 99, 235);
}

.location-button {
  position: absolute;
  bottom: 2rem;
  right: 1rem;
  width: 3rem;
  height: 3rem;
  background: white;
  border: none;
  border-radius: 50%;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
  z-index: 5;
  color: #1a73e8;
}

.location-button:hover {
  transform: scale(1.05);
  box-shadow: 0 6px 8px rgba(0, 0, 0, 0.4);
}

.location-button.active {
  background: rgb(59, 130, 246);
}

.location-icon {
  width: 1.5rem;
  height: 1.5rem;
  color: rgb(55, 65, 81);
}

.location-button.active .location-icon {
  color: white;
}

.search-container {
  position: absolute;
  top: 1rem;
  left: 50%;
  transform: translateX(-50%);
  width: 90%;
  max-width: 400px;
  z-index: 5;
}

.search-input {
  width: 100%;
  padding: 0.75rem 2.5rem 0.75rem 1rem;
  border: none;
  border-radius: 0.5rem;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.3);
  font-size: 1rem;
  outline: none;
}

.search-input::placeholder {
  color: rgb(156, 163, 175);
}

.clear-button {
  position: absolute;
  right: 0.5rem;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  padding: 0.25rem;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: background 0.2s;
  z-index: 10;
}

.clear-button:hover {
  background: rgba(0, 0, 0, 0.05);
}

.clear-icon {
  width: 1.25rem;
  height: 1.25rem;
  color: rgb(107, 114, 128);
}

.directions-button,
.start-nav-button,
.stop-nav-button {
  position: absolute;
  top: 5rem;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1.5rem;
  border: none;
  border-radius: 0.5rem;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.3);
  transition: all 0.2s;
  z-index: 5;
}

.directions-button {
  background: rgb(59, 130, 246);
  color: white;
}

.directions-button:hover {
  background: rgb(37, 99, 235);
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

.nav-icon {
  width: 1.25rem;
  height: 1.25rem;
}
</style>
