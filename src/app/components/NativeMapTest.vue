<template>
  <div class="native-map-test">
    <div v-if="loading" class="loading">
      <p>Loading native map...</p>
    </div>
    <div v-if="error" class="error">
      <p>{{ error }}</p>
      <button @click="initMap">Retry</button>
    </div>
    <div v-if="!loading && !error && isNative" class="info">
      <p>Native Map Active</p>
      <p>Try two-finger gestures:</p>
      <ul>
        <li>Two fingers + rotate = Rotate map (compass)</li>
        <li>Two fingers + drag up/down = Tilt (3D view)</li>
        <li>Pinch = Zoom</li>
      </ul>
      <button @click="testRotation">Test Rotation</button>
      <button @click="testTilt">Test Tilt</button>
    </div>
    <div v-if="!isNative" class="warning">
      <p>⚠️ Web version - Native maps only work on Android</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { Capacitor } from '@capacitor/core'
import { GoogleMapsNative } from '../../plugins/googlemaps'
import { Geolocation } from '@capacitor/geolocation'

const isNative = Capacitor.getPlatform() === 'android'
const loading = ref(true)
const error = ref('')

async function initMap() {
  loading.value = true
  error.value = ''

  try {
    if (!isNative) {
      loading.value = false
      return
    }

    // Get current location
    const position = await Geolocation.getCurrentPosition({
      enableHighAccuracy: true,
      timeout: 10000
    })

    // Initialize native map
    await GoogleMapsNative.create({
      lat: position.coords.latitude,
      lng: position.coords.longitude,
      zoom: 18 // High zoom to see 3D buildings
    })

    // Show the map
    await GoogleMapsNative.show()

    // Add a marker at current location
    await GoogleMapsNative.addMarker({
      id: 'current',
      lat: position.coords.latitude,
      lng: position.coords.longitude,
      title: 'You are here',
      color: '#4285F4'
    })

    loading.value = false
  } catch (err: any) {
    console.error('Error initializing map:', err)
    error.value = err.message || 'Failed to initialize map'
    loading.value = false
  }
}

async function testRotation() {
  try {
    const position = await Geolocation.getCurrentPosition()

    // Rotate 90 degrees
    await GoogleMapsNative.setCenter({
      lat: position.coords.latitude,
      lng: position.coords.longitude,
      zoom: 18,
      bearing: 90,
      tilt: 45,
      animate: true
    })

  } catch (err) {
    console.error('Test rotation error:', err)
  }
}

async function testTilt() {
  try {
    const position = await Geolocation.getCurrentPosition()

    // Apply heavy tilt for 3D view
    await GoogleMapsNative.setCenter({
      lat: position.coords.latitude,
      lng: position.coords.longitude,
      zoom: 19,
      tilt: 60,
      bearing: 0,
      animate: true
    })

  } catch (err) {
    console.error('Test tilt error:', err)
  }
}

onMounted(() => {
  initMap()
})

onUnmounted(async () => {
  if (isNative) {
    try {
      await GoogleMapsNative.destroy()
    } catch (err) {
      console.error('Error destroying map:', err)
    }
  }
})
</script>

<style scoped>
.native-map-test {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1000;
}

.loading,
.error,
.warning {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  background: rgba(0, 0, 0, 0.9);
  color: white;
  padding: 2rem;
  border-radius: 0.5rem;
  text-align: center;
  z-index: 1001;
}

.info {
  position: absolute;
  bottom: 2rem;
  left: 1rem;
  right: 1rem;
  background: rgba(0, 0, 0, 0.8);
  color: white;
  padding: 1rem;
  border-radius: 0.5rem;
  z-index: 1001;
}

.info ul {
  text-align: left;
  margin: 0.5rem 0;
  padding-left: 1.5rem;
}

.info button {
  margin: 0.5rem;
  padding: 0.5rem 1rem;
  background: #4285F4;
  color: white;
  border: none;
  border-radius: 0.25rem;
  cursor: pointer;
}

.error button {
  margin-top: 1rem;
  padding: 0.5rem 1rem;
  background: #4285F4;
  color: white;
  border: none;
  border-radius: 0.25rem;
  cursor: pointer;
}
</style>
