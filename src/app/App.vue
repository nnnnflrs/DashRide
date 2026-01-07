<template>
  <ion-app>
  <div class="app-container">
    <div class="background-gradient" />
    
    <div class="glow-top" />
    <div class="glow-bottom" />

    <div class="content-wrapper">
      <StatusBar :isGpsActive="isTracking" />

      <div class="main-content">
        <Transition name="fade" mode="out-in">
          <div v-if="activeTab === 'riding'" key="riding" class="tab-content dashboard-view">
            <div class="dashboard-container">
              <!-- CENTER - MASSIVE SPEEDOMETER with overlaid info -->
              <div class="gauge-wrapper">
                <SpeedometerGauge :speed="speed" :unit="unit" />
                
                <!-- LEFT INFO - Overlaid on gauge -->
                <div class="info-overlay info-left">
                  <!-- Mini Map -->
                  <div class="map-widget-small">
                    <MiniMap :distance="0.6" :unit="unit" nextTurn="Main Street" />
                  </div>

                  <!-- Navigation Info -->
                  <div class="info-item">
                    <Navigation class="info-icon" />
                    <div class="info-text">
                      <div class="info-label">MAIN STREET</div>
                      <div class="info-sublabel">{{ riderName }}</div>
                    </div>
                  </div>

                  <!-- Music -->
                  <div class="info-item">
                    <Music class="info-icon" />
                    <span class="info-value">Ride On</span>
                  </div>

                  <!-- Fuel -->
                  <div class="info-item">
                    <Fuel class="info-icon" />
                    <span class="info-value">{{ fuelConsumption.toFixed(1) }}/100km</span>
                  </div>

                  <!-- Total Distance -->
                  <div class="info-item">
                    <MapPin class="info-icon" />
                    <span class="info-value">{{ Math.round(tripData.totalDistance) }} km</span>
                  </div>
                </div>

                <!-- RIGHT INFO - Overlaid on gauge -->
                <div class="info-overlay info-right">
                  <!-- Range -->
                  <div class="info-item">
                    <span class="info-label">RANGE</span>
                    <span class="info-value-large">{{ Math.round(estimatedRange) }} km</span>
                  </div>

                  <!-- Trip -->
                  <div class="info-item">
                    <span class="info-label">TRIP</span>
                    <span class="info-value-large">{{ tripData.distance.toFixed(1) }} km</span>
                  </div>

                  <!-- Duration -->
                  <div class="info-item">
                    <Timer class="info-icon" />
                    <span class="info-value">{{ formattedTripTime }}</span>
                  </div>

                  <!-- Weather -->
                  <div class="info-item">
                    <component :is="weatherIcon" class="info-icon weather" />
                    <span class="info-value">{{ temperature }}°C</span>
                  </div>
                </div>

                <!-- Bottom Center Info -->
                <div class="center-bottom-info">
                  <Clock class="bottom-icon" />
                  <span class="bottom-time">{{ currentTime }}</span>
                  <Bell v-if="isTracking" class="bottom-icon active" />
                </div>
              </div>
            </div>
          </div>

          <div v-else-if="activeTab === 'nav'" key="nav" class="tab-content map-tab">
            <NavigationMap />
          </div>

          <div v-else-if="activeTab === 'music'" key="music" class="tab-content centered">
            <div class="placeholder-content">
              <div class="placeholder-icon">🎵</div>
              <h2 class="placeholder-title">Music Control</h2>
              <p class="placeholder-text">Control your music while riding</p>
              <p class="placeholder-soon">Feature coming soon</p>
            </div>
          </div>

          <div v-else-if="activeTab === 'settings'" key="settings" class="tab-content scrollable">
            <div class="settings-container">
              <h2 class="settings-title">Settings</h2>
              
              <div class="setting-card">
                <label class="setting-label">Speed Unit</label>
                <div class="unit-buttons">
                  <button
                    @click="unit = 'mph'"
                    :class="['unit-button', { active: unit === 'mph' }]"
                  >
                    MPH
                  </button>
                  <button
                    @click="unit = 'kmh'"
                    :class="['unit-button', { active: unit === 'kmh' }]"
                  >
                    KM/H
                  </button>
                </div>
              </div>

              <div class="setting-card">
                <label class="setting-label">
                  Fuel Consumption ({{ unit === 'mph' ? 'MPG' : 'L/100km' }})
                </label>
                <input
                  type="number"
                  step="0.1"
                  v-model.number="fuelConsumption"
                  class="fuel-input"
                />
              </div>

              <div class="setting-card">
                <div class="toggle-row">
                  <div>
                    <div class="toggle-title">Keep Screen On</div>
                    <div class="toggle-description">
                      Prevents screen from sleeping
                    </div>
                  </div>
                  <button
                    @click="keepScreenOn = !keepScreenOn"
                    :class="['toggle-button', { active: keepScreenOn }]"
                  >
                    <div :class="['toggle-knob', { active: keepScreenOn }]" />
                  </button>
                </div>
              </div>

              <div class="setting-card">
                <h3 class="about-title">About</h3>
                <div class="about-content">
                  <p>Modern TFT-style motorcycle dashboard using phone sensors.</p>
                  <p class="data-sources-title">
                    <span class="bold">Data Sources:</span>
                  </p>
                  <ul class="data-sources-list">
                    <li>GPS for speed and location</li>
                    <li>Device sensors for motion data</li>
                    <li>User input for fuel consumption</li>
                  </ul>
                </div>
              </div>
            </div>
          </div>
        </Transition>
      </div>

      <!-- <div class="bottom-info">
        <div class="time-display">
          <Clock class="time-icon" />
          <span class="time-text">{{ currentTime }}</span>
        </div>
        <div v-if="isTracking" class="tracking-indicator">
          <Bell class="bell-icon" />
        </div>
      </div> -->

      <BottomNavigation :activeTab="activeTab" @update:activeTab="activeTab = $event" />
    </div>
  </div>
  </ion-app>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { IonApp } from '@ionic/vue'
import { useLocalStorage } from '@vueuse/core'
import { toast } from 'vue-sonner'
import { Clock, Bell, Fuel, Gauge, MapPin, Timer, Sun, Cloud, CloudRain } from 'lucide-vue-next'
import { ScreenOrientation } from '@capacitor/screen-orientation'
import { StatusBar as CapStatusBar } from '@capacitor/status-bar'
import SpeedometerGauge from './components/SpeedometerGauge.vue'
import StatusBar from './components/StatusBar.vue'
import MiniMap from './components/MiniMap.vue'
import InfoCard from './components/InfoCard.vue'
import BottomNavigation from './components/BottomNavigation.vue'
import RiderInfo from './components/RiderInfo.vue'
import NavigationMap from './components/NavigationMap.vue'

interface TripData {
  distance: number
  duration: number
  maxSpeed: number
  totalSpeed: number
  speedSamples: number
  totalDistance: number
}

const speed = ref(0)
const altitude = ref(0)
const isTracking = ref(false)
const activeTab = ref<'nav' | 'music' | 'riding' | 'settings'>('riding')
const unit = useLocalStorage<'mph' | 'kmh'>('speedUnit', 'kmh')
const keepScreenOn = useLocalStorage('keepScreenOn', true)
const riderName = useLocalStorage('riderName', 'Lennon Flores')
const fuelConsumption = useLocalStorage('fuelConsumption', 5.1)
const temperature = ref(27)

const tripData = useLocalStorage<TripData>('tripData', {
  distance: 0,
  duration: 0,
  maxSpeed: 0,
  totalSpeed: 0,
  speedSamples: 0,
  totalDistance: 230,
})

let watchId: number | null = null
let lastPosition: GeolocationPosition | null = null
let tripInterval: number | null = null
let wakeLock: any = null

const currentTime = ref(new Date().toLocaleTimeString('en-US', { 
  hour: '2-digit', 
  minute: '2-digit',
  hour12: true 
}))

let timeInterval: number | null = null
onMounted(async () => {
  // Lock screen orientation to landscape
  try {
    await ScreenOrientation.lock({ orientation: 'landscape' })
  } catch (error) {
    console.log('Screen orientation lock not supported:', error)
  }

  // Hide status bar for fullscreen experience
  try {
    await CapStatusBar.hide()
  } catch (error) {
    console.log('Status bar hide not supported:', error)
  }

  // Start GPS tracking automatically
  startTracking()

  timeInterval = window.setInterval(() => {
    currentTime.value = new Date().toLocaleTimeString('en-US', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: true
    })
  }, 1000)
})

onUnmounted(() => {
  if (timeInterval) clearInterval(timeInterval)
})

const avgSpeed = computed(() => 
  tripData.value.speedSamples > 0 ? tripData.value.totalSpeed / tripData.value.speedSamples : 0
)

const estimatedRange = computed(() =>
  fuelConsumption.value > 0 ? (tripData.value.totalDistance / fuelConsumption.value) * 10 : 230
)

const formattedTripTime = computed(() => {
  const hours = Math.floor(tripData.value.duration / 3600)
  const minutes = Math.floor((tripData.value.duration % 3600) / 60)
  const seconds = tripData.value.duration % 60
  return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`
})

const weatherIcon = computed(() => {
  if (temperature.value > 25) return Sun
  if (temperature.value > 15) return Cloud
  return CloudRain
})

const requestWakeLock = async () => {
  if ('wakeLock' in navigator && keepScreenOn.value) {
    try {
      wakeLock = await (navigator as any).wakeLock.request('screen')
    } catch (err) {
      console.error('Wake Lock error:', err)
    }
  }
}

const releaseWakeLock = () => {
  if (wakeLock) {
    wakeLock.release()
    wakeLock = null
  }
}

watch([keepScreenOn, isTracking], () => {
  if (keepScreenOn.value && isTracking.value) {
    requestWakeLock()
  } else {
    releaseWakeLock()
  }
})

const startTracking = () => {
  if (!navigator.geolocation) {
    toast.error('Geolocation is not supported')
    return
  }

  const options = {
    enableHighAccuracy: true,
    timeout: 5000,
    maximumAge: 0,
  }

  watchId = navigator.geolocation.watchPosition(
    (position) => {
      const speedMps = position.coords.speed || 0
      const speedKmh = speedMps * 3.6
      const currentSpeed = unit.value === 'mph' ? speedKmh * 0.621371 : speedKmh
      
      speed.value = Math.max(0, currentSpeed)
      altitude.value = position.coords.altitude || 0

      tripData.value = {
        ...tripData.value,
        maxSpeed: Math.max(tripData.value.maxSpeed, currentSpeed),
        totalSpeed: tripData.value.totalSpeed + currentSpeed,
        speedSamples: tripData.value.speedSamples + 1,
      }

      if (lastPosition && currentSpeed > 0.5) {
        const distance = calculateDistance(
          lastPosition.coords.latitude,
          lastPosition.coords.longitude,
          position.coords.latitude,
          position.coords.longitude
        )
        
        tripData.value = {
          ...tripData.value,
          distance: tripData.value.distance + distance,
        }
      }

      lastPosition = position
    },
    (error) => {
      console.error('GPS error:', error)
      if (error.code === error.PERMISSION_DENIED) {
        toast.error('Location permission denied')
      }
    },
    options
  )

  tripInterval = window.setInterval(() => {
    tripData.value = {
      ...tripData.value,
      duration: tripData.value.duration + 1,
    }
  }, 1000)

  isTracking.value = true
  toast.success('GPS tracking started')
}

const stopTracking = () => {
  if (watchId !== null) {
    navigator.geolocation.clearWatch(watchId)
    watchId = null
  }

  if (tripInterval !== null) {
    clearInterval(tripInterval)
    tripInterval = null
  }

  isTracking.value = false
  speed.value = 0
  toast.info('GPS tracking stopped')
}

const calculateDistance = (lat1: number, lon1: number, lat2: number, lon2: number): number => {
  const R = unit.value === 'mph' ? 3958.8 : 6371
  const dLat = toRad(lat2 - lat1)
  const dLon = toRad(lon2 - lon1)
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
  return R * c
}

const toRad = (degrees: number): number => {
  return degrees * (Math.PI / 180)
}

onUnmounted(() => {
  stopTracking()
  releaseWakeLock()
})
</script>

<style scoped>
.app-container {
  min-height: 100vh;
  min-height: 100dvh; /* Dynamic viewport height for mobile */
  width: 100vw;
  background: black;
  color: white;
  overflow: hidden;
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
}

.background-gradient {
  position: absolute;
  inset: 0;
  background: linear-gradient(to bottom right, rgb(3, 7, 18), black, rgb(17, 24, 39));
}

.glow-top {
  position: absolute;
  top: 0;
  left: 25%;
  width: 24rem;
  height: 24rem;
  background: rgba(59, 130, 246, 0.1);
  border-radius: 50%;
  filter: blur(80px);
}

.glow-bottom {
  position: absolute;
  bottom: 0;
  right: 25%;
  width: 24rem;
  height: 24rem;
  background: rgba(34, 197, 94, 0.1);
  border-radius: 50%;
  filter: blur(80px);
}

.content-wrapper {
  position: relative;
  z-index: 10;
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.main-content {
  flex: 1;
  overflow: hidden;
}

.tab-content {
  height: 100%;
  padding: 1rem;
  overflow-y: auto;
}

.tab-content.centered {
  display: flex;
  align-items: center;
  justify-content: center;
}

.tab-content.scrollable {
  padding: 1.5rem;
}

.tab-content.map-tab {
  padding: 0;
}

/* Dashboard View - Massive Gauge Layout */
.dashboard-view {
  padding: 0;
  overflow: hidden;
}

.dashboard-container {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 0.5rem;
  position: relative;
}

/* Gauge Wrapper - Contains gauge and overlaid info */
.gauge-wrapper {
  position: relative;
  width: 100%;
  max-width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* Info Overlays - Positioned on top of gauge */
.info-overlay {
  position: absolute;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  z-index: 10;
}

.info-overlay.info-left {
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  align-items: flex-start;
  padding-left: 0.5rem;
}

.info-overlay.info-right {
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  align-items: flex-end;
  padding-right: 0.5rem;
}

/* Map Widget Small */
.map-widget-small {
  width: 100px;
  margin-bottom: 0.5rem;
  border-radius: 6px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.4);
}

/* Info Items - Simple Text, No Tiles */
.info-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.25rem 0;
}

.info-item.right-align {
  justify-content: flex-end;
  flex-direction: row-reverse;
}

/* Info Icons */
.info-icon {
  width: 1.1rem;
  height: 1.1rem;
  color: rgb(74, 222, 128);
  flex-shrink: 0;
  filter: drop-shadow(0 0 3px rgba(74, 222, 128, 0.4));
}

.info-icon.weather {
  color: rgb(250, 204, 21);
  filter: drop-shadow(0 0 3px rgba(250, 204, 21, 0.4));
}

/* Info Text */
.info-text {
  display: flex;
  flex-direction: column;
}

.info-label {
  font-size: 0.7rem;
  font-weight: 600;
  color: rgba(156, 163, 175, 0.9);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.info-sublabel {
  font-size: 0.65rem;
  color: rgba(156, 163, 175, 0.6);
}

.info-value {
  font-size: 0.85rem;
  font-weight: 600;
  color: white;
}

.info-value-large {
  font-size: 1.1rem;
  font-weight: 700;
  color: white;
  text-shadow: 0 0 6px rgba(255, 255, 255, 0.2);
}

/* Bottom Center Info (below gauge) */
.center-bottom-info {
  position: absolute;
  bottom: 1.2rem;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.5rem 1rem;
  background: rgba(17, 24, 39, 0.8);
  backdrop-filter: blur(8px);
  border-radius: 8px;
  border: 1px solid rgba(75, 85, 99, 0.3);
  /* z-index: 10; */
}

.bottom-icon {
  width: 1rem;
  height: 1rem;
  color: rgba(156, 163, 175, 0.7);
}

.bottom-icon.active {
  color: rgb(74, 222, 128);
  filter: drop-shadow(0 0 4px rgba(74, 222, 128, 0.5));
}

.bottom-time {
  font-size: 0.95rem;
  font-weight: 600;
  color: white;
  font-family: 'SF Mono', 'Monaco', 'Courier New', monospace;
  letter-spacing: 0.05em;
}

/* Responsive Design */
@media (max-width: 768px) {
  .info-overlay.info-left {
    left: 0.25rem;
    padding-left: 0.25rem;
  }
  
  .info-overlay.info-right {
    right: 0.25rem;
    padding-right: 0.25rem;
  }
  
  .info-item {
    font-size: 0.8rem;
  }
  
  .info-value {
    font-size: 0.75rem;
  }
  
  .info-value-large {
    font-size: 0.95rem;
  }
  
  .map-widget-small {
    width: 80px;
  }
  
  .center-bottom-info {
    padding: 0.4rem 0.75rem;
    gap: 0.75rem;
  }
  
  .bottom-time {
    font-size: 0.85rem;
  }
}

.placeholder-content {
  text-align: center;
}

.placeholder-icon {
  font-size: 3.75rem;
  margin-bottom: 1rem;
}

.placeholder-title {
  font-size: 1.5rem;
  font-weight: bold;
  margin-bottom: 0.5rem;
}

.placeholder-text {
  color: rgba(156, 163, 175, 1);
}

.placeholder-soon {
  font-size: 0.875rem;
  color: rgba(107, 114, 128, 1);
  margin-top: 1rem;
}

.settings-container {
  max-width: 42rem;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.settings-title {
  font-size: 1.5rem;
  font-weight: bold;
}

.setting-card {
  background: linear-gradient(to bottom right, rgba(17, 24, 39, 0.8), rgba(31, 41, 55, 0.8));
  backdrop-filter: blur(8px);
  border-radius: 0.5rem;
  border: 1px solid rgba(55, 65, 81, 0.5);
  padding: 1rem;
}

.setting-label {
  font-size: 0.875rem;
  color: rgba(156, 163, 175, 1);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 0.75rem;
  display: block;
}

.unit-buttons {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0.75rem;
}

.unit-button {
  padding: 0.75rem;
  border-radius: 0.5rem;
  font-weight: 600;
  transition: all 0.2s;
  background: rgb(31, 41, 55);
  color: rgba(156, 163, 175, 1);
  border: none;
  cursor: pointer;
}

.unit-button:hover {
  background: rgb(55, 65, 81);
}

.unit-button.active {
  background: rgb(37, 99, 235);
  color: white;
}

.fuel-input {
  width: 100%;
  background: rgb(31, 41, 55);
  color: white;
  padding: 0.75rem 1rem;
  border-radius: 0.5rem;
  border: 1px solid rgb(55, 65, 81);
  outline: none;
}

.fuel-input:focus {
  border-color: rgb(59, 130, 246);
}

.toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.toggle-title {
  color: white;
  font-weight: 600;
  margin-bottom: 0.25rem;
}

.toggle-description {
  font-size: 0.875rem;
  color: rgba(156, 163, 175, 1);
}

.toggle-button {
  position: relative;
  width: 3.5rem;
  height: 2rem;
  border-radius: 9999px;
  transition: background-color 0.2s;
  background: rgb(55, 65, 81);
  border: none;
  cursor: pointer;
}

.toggle-button.active {
  background: rgb(37, 99, 235);
}

.toggle-knob {
  position: absolute;
  left: 0.25rem;
  top: 0.25rem;
  width: 1.5rem;
  height: 1.5rem;
  background: white;
  border-radius: 50%;
  transition: transform 0.2s;
}

.toggle-knob.active {
  transform: translateX(1.5rem);
}

.about-title {
  color: white;
  font-weight: 600;
  margin-bottom: 0.75rem;
}

.about-content {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  font-size: 0.875rem;
  color: rgba(156, 163, 175, 1);
}

.data-sources-title {
  padding-top: 0.5rem;
}

.bold {
  color: white;
  font-weight: 600;
}

.data-sources-list {
  list-style-type: disc;
  list-style-position: inside;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  margin-left: 0.5rem;
}

.bottom-info {
  position: absolute;
  bottom: 4rem;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 1rem;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(12px);
  padding: 0.5rem 1rem;
  border-radius: 0.5rem;
  border: 1px solid rgba(31, 41, 55, 0.5);
}

.time-display {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.time-icon {
  width: 1rem;
  height: 1rem;
  color: rgba(156, 163, 175, 1);
}

.time-text {
  font-size: 0.875rem;
  font-weight: 600;
  color: white;
}

.tracking-indicator {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.bell-icon {
  width: 1rem;
  height: 1rem;
  color: rgb(250, 204, 21);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
