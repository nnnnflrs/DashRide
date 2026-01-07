<template>
  <div class="navigation-map-container">
    <div ref="mapContainer" class="map-container"></div>
    
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
import { Navigation } from 'lucide-vue-next'

const mapContainer = ref<HTMLDivElement | null>(null)
const loading = ref(true)
const error = ref('')
const map = ref<any>(null)
const currentLocationMarker = ref<any>(null)
const isFollowingLocation = ref(true)
let watchId: string | null = null

const API_KEY = import.meta.env.VITE_GOOGLE_MAPS_API_KEY

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
    const position = await Geolocation.getCurrentPosition({
      enableHighAccuracy: true,
      timeout: 10000,
      maximumAge: 0
    })

    const currentLocation = {
      lat: position.coords.latitude,
      lng: position.coords.longitude
    }

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
      radius: position.coords.accuracy,
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
          
          if (isFollowingLocation.value) {
            map.value.panTo(newLocation)
          }
        }
      }
    )

    // Detect when user manually moves the map
    map.value.addListener('dragstart', () => {
      isFollowingLocation.value = false
    })

    loading.value = false
  } catch (err: any) {
    console.error('Map initialization error:', err)
    error.value = err.message || 'Failed to load map'
    loading.value = false
  }
}

const centerOnCurrentLocation = async () => {
  try {
    const position = await Geolocation.getCurrentPosition({
      enableHighAccuracy: true,
      timeout: 5000,
      maximumAge: 0
    })

    const currentLocation = {
      lat: position.coords.latitude,
      lng: position.coords.longitude
    }

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
</style>
