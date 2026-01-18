<template>
  <ion-app>
  <div class="app-container" :data-theme="currentTheme" :class="{ 'native-map-active': activeTab === 'nav' }">
    <div class="background-gradient" />

    <div class="glow-top" />
    <div class="glow-bottom" />

    <div class="content-wrapper">
      <StatusBar :isGpsActive="isTracking" :theme="currentTheme" />

      <div class="main-content">
        <!-- Riding Tab -->
        <div v-show="activeTab === 'riding'" class="tab-content dashboard-view">
          <div class="dashboard-container">
            <!-- LEFT INFO - Overlaid on gauge in landscape, above speedometer in portrait -->
            <div v-if="showDetailsOnNavigation || isNavigating" class="info-overlay info-left">
              <!-- Mini Map - Hidden when not navigating or disabled in settings -->
              <div class="map-widget-small" :class="{ 'map-hidden': isNavigating }">
                <MiniMap
                  v-if="isNavigating && showMinimap"
                  :distance="remainingDistance"
                  :unit="unit"
                  :current-location="currentLocation"
                  :route-path="routePath"
                  :bearing="bearing"
                  :next-turn="nextTurnInstruction"
                  :map-style="mapStyle"
                  :is-visible="activeTab === 'riding'"
                  @expand="activeTab = 'nav'"
                  @arrived="handleArrival"
                />
              </div>

              <div class="info-items-row">
                <!-- Destination Name -->
                <div class="info-item" @click="showTooltip('Destination')">
                  <MapPin class="info-icon" />
                  <span class="info-value" :class="{ 'marquee': destination && destination.length > 20 }">
                    <span class="marquee-content">{{ destination || 'No destination' }}</span>
                  </span>
                  <div v-if="activeTooltip === 'Destination'" class="info-tooltip">Destination</div>
                </div>

                <!-- ETA - Show estimated time of arrival -->
                <div class="info-item" @click="showTooltip('Estimated Arrival')">
                  <Clock class="info-icon" />
                  <span class="info-value">{{ estimatedTimeOfArrival }}</span>
                  <div v-if="activeTooltip === 'Estimated Arrival'" class="info-tooltip">Estimated Arrival</div>
                </div>

                <!-- Duration - Navigation trip time -->
                <div class="info-item" @click="showTooltip('Duration')">
                  <Timer class="info-icon" />
                  <span class="info-value">{{ formattedTripTime }}</span>
                  <div v-if="activeTooltip === 'Duration'" class="info-tooltip">Duration</div>
                </div>

                <!-- Total Distance -->
                <div class="info-item" @click="showTooltip('Total Distance')">
                  <Target class="info-icon" />
                  <span class="info-value">{{ navTotalDistance.toFixed(1) }} km</span>
                  <div v-if="activeTooltip === 'Total Distance'" class="info-tooltip">Total Distance</div>
                </div>

                <!-- Remaining Distance - Only show during navigation -->
                <div class="info-item" @click="showTooltip('Remaining Distance')">
                  <Navigation class="info-icon" />
                  <span class="info-value">{{ remainingDistance.toFixed(1) ?? 0 }} km</span>
                  <div v-if="activeTooltip === 'Remaining Distance'" class="info-tooltip">Remaining Distance</div>
                </div>
              </div>
            </div>

            <!-- CENTER - MASSIVE SPEEDOMETER with overlaid info -->
            <div class="gauge-wrapper">
              <SpeedometerGauge :speed="speed" :unit="unit" :class="{ 'with-music-container': showMiniPlayer }" />

              <!-- RIGHT INFO - Overlaid on gauge -->
              <div class="info-overlay info-right">
                <!-- Average Speed (with reset on long-press) -->
                <div
                  class="info-item"
                  :class="{ 'resetting': isResettingAvg }"
                  @touchstart="startReset('avg')"
                  @touchend="cancelReset('avg')"
                  @touchcancel="cancelReset('avg')"
                  @mousedown="startReset('avg')"
                  @mouseup="cancelReset('avg')"
                  @mouseleave="cancelReset('avg')"
                  @click="showTooltip('Average Speed')"
                >
                  <TrendingUp class="info-icon" />
                  <span class="info-value">{{ avgSpeed.toFixed(1) }} {{ unit === 'mph' ? 'mph' : 'km/h' }}</span>
                  <div v-if="activeTooltip === 'Average Speed' && !isResettingAvg" class="info-tooltip">Average Speed</div>
                  <div v-if="isResettingAvg" class="info-tooltip reset-tooltip">Hold to reset average speed</div>
                  <div v-if="isResettingAvg" class="reset-progress" :style="{ width: resetProgressAvg + '%' }"></div>
                </div>

                <!-- Maximum Speed (with reset on long-press) -->
                <div
                  class="info-item"
                  :class="{ 'resetting': isResettingMax }"
                  @touchstart="startReset('max')"
                  @touchend="cancelReset('max')"
                  @touchcancel="cancelReset('max')"
                  @mousedown="startReset('max')"
                  @mouseup="cancelReset('max')"
                  @mouseleave="cancelReset('max')"
                  @click="showTooltip('Maximum Speed')"
                >
                  <Zap class="info-icon" />
                  <span class="info-value">{{ tripData.maxSpeed.toFixed(1) }} {{ unit === 'mph' ? 'mph' : 'km/h' }}</span>
                  <div v-if="activeTooltip === 'Maximum Speed' && !isResettingMax" class="info-tooltip">Maximum Speed</div>
                  <div v-if="isResettingMax" class="info-tooltip reset-tooltip">Hold to reset max speed</div>
                  <div v-if="isResettingMax" class="reset-progress" :style="{ width: resetProgressMax + '%' }"></div>
                </div>

                <!-- Trip (with reset on long-press) -->
                <div
                  class="info-item"
                  :class="{ 'resetting': isResettingTrip }"
                  @touchstart="startReset('trip')"
                  @touchend="cancelReset('trip')"
                  @touchcancel="cancelReset('trip')"
                  @mousedown="startReset('trip')"
                  @mouseup="cancelReset('trip')"
                  @mouseleave="cancelReset('trip')"
                  @click="showTooltip('Trip')"
                >
                  <Route class="info-icon" />
                  <span class="info-value">{{ tripData.distance.toFixed(1) }} {{ unit === 'mph' ? 'mi' : 'km' }}</span>
                  <div v-if="activeTooltip === 'Trip' && !isResettingTrip" class="info-tooltip">Trip</div>
                  <div v-if="isResettingTrip" class="info-tooltip reset-tooltip">Hold to reset trip</div>
                  <div v-if="isResettingTrip" class="reset-progress" :style="{ width: resetProgressTrip + '%' }"></div>
                </div>

                <!-- Altitude -->
                <div class="info-item" @click="showTooltip('Altitude')">
                  <Mountain class="info-icon" />
                  <span class="info-value">{{ Math.round(altitude) }} masl</span>
                  <div v-if="activeTooltip === 'Altitude'" class="info-tooltip">Altitude</div>
                </div>

                <!-- Slope -->
                <div class="info-item" @click="showTooltip('Slope')">
                  <component :is="slopeIcon" :class="['info-icon', slopeColorClass]" />
                  <span class="info-value">{{ formattedSlope }}</span>
                  <div v-if="activeTooltip === 'Slope'" class="info-tooltip">Slope</div>
                </div>

                <!-- Weather -->
                <div class="info-item" @click="showTooltip('Weather')">
                  <component :is="weatherIcon" class="info-icon weather" />
                  <span class="info-value">{{ temperature }}°C</span>
                  <div v-if="activeTooltip === 'Weather'" class="info-tooltip">Weather</div>
                </div>
              </div>

              <!-- Mini Music Player - Show when user wants to see it -->
              <div v-if="showMiniPlayer && musicCurrentTrack && activeTab === 'riding'" class="mini-music-player">
                <div class="mini-music-info">
                  <div class="mini-album-art">
                    <img v-if="musicCurrentTrack.albumArt" :src="musicCurrentTrack?.albumArt" alt="Album art" />
                    <Music v-else class="mini-music-icon" />
                  </div>
                  <div class="mini-track-details">
                    <div class="mini-track-title">{{ musicCurrentTrack.title }}</div>
                    <div class="mini-track-artist">{{ musicCurrentTrack?.artist }}</div>
                  </div>
                </div>
                <div class="mini-music-controls">
                  <button @click="musicPreviousTrack()" class="mini-control-btn">
                    <SkipBack class="mini-control-icon" />
                  </button>
                  <button @click="musicTogglePlay()" class="mini-control-btn mini-play-btn">
                    <Play v-if="!musicIsPlaying" class="mini-control-icon" />
                    <Pause v-else class="mini-control-icon" />
                  </button>
                  <button @click="musicNextTrack()" class="mini-control-btn">
                    <SkipForward class="mini-control-icon" />
                  </button>
                </div>
                <button @click="closeMiniPlayer()" class="mini-close-btn">
                  <X class="mini-close-icon" />
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Navigation Tab - Use v-show to preserve map state when switching tabs -->
        <div v-show="activeTab === 'nav'" class="tab-content map-tab">
          <NavigationMap ref="navigationMapRef" :theme="currentTheme" @update:isSearching="isSearchingLocation = $event" />
        </div>

        <!-- Music Tab - Use v-show to preserve music player state -->
        <div v-show="activeTab === 'music'" class="tab-content music-tab">
          <MusicPlayer :theme="currentTheme" />
        </div>

        <!-- Settings Tab - Use v-if since it doesn't need to preserve state -->
        <div v-show="activeTab === 'settings'" class="tab-content scrollable">
          <Settings :theme="currentTheme" />
        </div>
      </div>

      <BottomNavigation
        v-show="!isSearchingLocation"
        :activeTab="activeTab"
        :theme="currentTheme"
        @update:activeTab="activeTab = $event"
      />
    </div>
  </div>
  </ion-app>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { IonApp } from '@ionic/vue'
import { useLocalStorage } from '@vueuse/core'
import { toast } from 'vue-sonner'
import { Clock, MapPin,Target, Navigation , Timer, Sun, Cloud, CloudRain, Music, TrendingUp, TrendingDown, Minus, Zap, Mountain, Play, Pause, SkipBack, SkipForward, X, Route } from 'lucide-vue-next'
import { StatusBar as CapStatusBar } from '@capacitor/status-bar'
import { Haptics, ImpactStyle } from '@capacitor/haptics'
import { Capacitor } from '@capacitor/core'
import SpeedometerGauge from './components/SpeedometerGauge.vue'
import StatusBar from './components/StatusBar.vue'
import MiniMap from './components/MiniMap.vue'
import BottomNavigation from './components/BottomNavigation.vue'
import NavigationMap from './components/NavigationMap.vue'
import MusicPlayer from './components/MusicPlayer.vue'
import Settings from './components/Settings.vue'
import { useMusicPlayer } from '../composables/useMusicPlayer'
import { useWeather } from '../composables/useWeather'
import { useSettings } from '../composables/useSettings'
import { useNavigation } from '../composables/useNavigation'
import { useSlope } from '../composables/useSlope'
import { Geolocation } from '@capacitor/geolocation'
import { GoogleMapsNative } from '../plugins/googlemaps'
import { Dialog } from '@capacitor/dialog';

// Get music player state and controls
const {
  isPlaying: musicIsPlaying,
  currentTrack: musicCurrentTrack,
  togglePlay: musicTogglePlay,
  nextTrack: musicNextTrack,
  previousTrack: musicPreviousTrack
} = useMusicPlayer()

// Mini music player visibility control
const showMiniPlayer = ref(false)

// Watch for music starting to show mini player
watch([musicIsPlaying, musicCurrentTrack], ([playing, track]) => {
  if (playing && track) {
    showMiniPlayer.value = true
  }
})

const closeMiniPlayer = () => {
  showMiniPlayer.value = false
}

// Get weather data
const { temperature, weatherData, isLoading: weatherLoading, error: weatherError } = useWeather()

// Get navigation state
const { isNavigating, remainingDistance, totalDistance: navTotalDistance, destination, formattedETA, routePath, nextTurnInstruction, stopNavigation } = useNavigation()

// Get slope calculation state
const { formattedSlope, slopeDirection, updateSlope } = useSlope()

// Navigation-specific duration tracking
const navigationDuration = ref(0)
let navigationInterval: number | null = null

// Reference to NavigationMap component
const navigationMapRef = ref<InstanceType<typeof NavigationMap> | null>(null)

// Use ETA from navigation composable (gets updated from Google Directions API)
const estimatedTimeOfArrival = computed(() => {
  return formattedETA.value || '--:--'
})

// Handle user arrival at destination
const handleArrival = async () => {

  await Dialog.alert({
    title: 'Success',
    message: `You have arrived at ${destination.value}!`,
  });

  // Clean up NavigationMap component state (routes, markers, etc.)
  if (navigationMapRef.value) {
    await navigationMapRef.value.cleanupNavigation()
  }

  // Stop navigation in shared state
  stopNavigation()

  // Clear the navigation interval
  if (navigationInterval !== null) {
    clearInterval(navigationInterval)
    navigationInterval = null
  }

  navigationDuration.value = 0
}

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
const bearing = ref(0) // GPS heading/direction
const isTracking = ref(false)
const activeTab = ref<'nav' | 'music' | 'riding' | 'settings'>('riding')
const isSearchingLocation = ref(false)
const currentLocation = ref<{ lat: number; lng: number } | null>(null)

// Tooltip state
const activeTooltip = ref<string | null>(null)
let tooltipTimeout: number | null = null


// Use shared settings state
const { theme, unit, keepScreenOn, showMinimap, showDetailsOnNavigation, mapStyle } = useSettings()

// Compute current theme based on selection
const currentTheme = computed(() => {
  if (theme.value === 'auto') {
    // Check system preference
    if (window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches) {
      return 'light'
    }
    return 'dark'
  }
  return theme.value
})

const tripData = useLocalStorage<TripData>('tripData', {
  distance: 0,
  duration: 0,
  maxSpeed: 0,
  totalSpeed: 0,
  speedSamples: 0,
  totalDistance: 230,
})

let watchId: string | null = null
let lastPosition: any = null
let tripInterval: number | null = null
let wakeLock: any = null

const currentTime = ref(new Date().toLocaleTimeString('en-US', { 
  hour: '2-digit', 
  minute: '2-digit',
  hour12: true 
}))

let timeInterval: number | null = null
onMounted(async () => {

    try {
      await CapStatusBar.hide()
    } catch (error) {
      console.log("Status bar hide failed'")
    }  

  startTracking();

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
  if (navigationInterval) clearInterval(navigationInterval)
})

const avgSpeed = computed(() =>
  tripData.value.speedSamples > 0 ? tripData.value.totalSpeed / tripData.value.speedSamples : 0
)

// Reset functionality for speed stats - separate for avg and max
const isResettingAvg = ref(false)
const resetProgressAvg = ref(0)
let resetTimerAvg: number | null = null
let resetProgressIntervalAvg: number | null = null

const isResettingMax = ref(false)
const resetProgressMax = ref(0)
let resetTimerMax: number | null = null
let resetProgressIntervalMax: number | null = null

const isResettingTrip = ref(false)
const resetProgressTrip = ref(0)
let resetTimerTrip: number | null = null
let resetProgressIntervalTrip: number | null = null

const startReset = (type: 'avg' | 'max' | 'trip') => {
  const duration = 2000 // 2 seconds
  const delayBeforeShow = 300 // 300ms delay before showing reset UI

  if (type === 'avg') {
    resetProgressAvg.value = 0

    // Delay before showing reset UI to allow click events to fire
    setTimeout(() => {
      if (resetTimerAvg !== null) { // Only show if still holding
        isResettingAvg.value = true
      }
    }, delayBeforeShow)

    const startTime = Date.now()

    // Update progress bar
    resetProgressIntervalAvg = window.setInterval(() => {
      const elapsed = Date.now() - startTime
      resetProgressAvg.value = Math.min((elapsed / duration) * 100, 100)
    }, 50)

    // Trigger haptic feedback
    try {
      Haptics.impact({ style: ImpactStyle.Light })
    } catch (error) {
      console.log('Haptics not available')
    }

    // Set timer to reset after 2 seconds
    resetTimerAvg = window.setTimeout(() => {
      // Reset average speed statistics only
      tripData.value = {
        ...tripData.value,
        totalSpeed: 0,
        speedSamples: 0
      }

      // Strong haptic feedback on reset
      try {
        Haptics.impact({ style: ImpactStyle.Heavy })
      } catch (error) {
        console.log('Haptics not available')
      }

      isResettingAvg.value = false
      resetProgressAvg.value = 0
    }, duration)
  } else if (type === 'max') {
    resetProgressMax.value = 0

    // Delay before showing reset UI to allow click events to fire
    setTimeout(() => {
      if (resetTimerMax !== null) { // Only show if still holding
        isResettingMax.value = true
      }
    }, delayBeforeShow)

    const startTime = Date.now()

    // Update progress bar
    resetProgressIntervalMax = window.setInterval(() => {
      const elapsed = Date.now() - startTime
      resetProgressMax.value = Math.min((elapsed / duration) * 100, 100)
    }, 50)

    // Trigger haptic feedback
    try {
      Haptics.impact({ style: ImpactStyle.Light })
    } catch (error) {
      console.log('Haptics not available')
    }

    // Set timer to reset after 2 seconds
    resetTimerMax = window.setTimeout(() => {
      // Reset max speed only
      tripData.value = {
        ...tripData.value,
        maxSpeed: 0
      }

      // Strong haptic feedback on reset
      try {
        Haptics.impact({ style: ImpactStyle.Heavy })
      } catch (error) {
        console.log('Haptics not available')
      }

      isResettingMax.value = false
      resetProgressMax.value = 0
    }, duration)
  } else if (type === 'trip') {
    resetProgressTrip.value = 0

    // Delay before showing reset UI to allow click events to fire
    setTimeout(() => {
      if (resetTimerTrip !== null) { // Only show if still holding
        isResettingTrip.value = true
      }
    }, delayBeforeShow)

    const startTime = Date.now()

    resetProgressIntervalTrip = window.setInterval(() => {
      const elapsed = Date.now() - startTime
      resetProgressTrip.value = Math.min((elapsed / duration) * 100, 100)
    }, 50)

    try {
      Haptics.impact({ style: ImpactStyle.Light })
    } catch (error) {
      console.log('Haptics not available')
    }

    resetTimerTrip = window.setTimeout(() => {
      tripData.value = {
        ...tripData.value,
        distance: 0
      }

      try {
        Haptics.impact({ style: ImpactStyle.Heavy })
      } catch (error) {
        console.log('Haptics not available')
      }

      isResettingTrip.value = false
      resetProgressTrip.value = 0
    }, duration)
  }
}

const cancelReset = (type: 'avg' | 'max' | 'trip') => {
  if (type === 'avg') {
    if (resetTimerAvg) {
      clearTimeout(resetTimerAvg)
      resetTimerAvg = null
    }
    if (resetProgressIntervalAvg) {
      clearInterval(resetProgressIntervalAvg)
      resetProgressIntervalAvg = null
    }
    isResettingAvg.value = false
    resetProgressAvg.value = 0
  } else if (type === 'max') {
    if (resetTimerMax) {
      clearTimeout(resetTimerMax)
      resetTimerMax = null
    }
    if (resetProgressIntervalMax) {
      clearInterval(resetProgressIntervalMax)
      resetProgressIntervalMax = null
    }
    isResettingMax.value = false
    resetProgressMax.value = 0
  } else if (type === 'trip') {
    if (resetTimerTrip) {
      clearTimeout(resetTimerTrip)
      resetTimerTrip = null
    }
    if (resetProgressIntervalTrip) {
      clearInterval(resetProgressIntervalTrip)
      resetProgressIntervalTrip = null
    }
    isResettingTrip.value = false
    resetProgressTrip.value = 0
  }
}

const formattedTripTime = computed(() => {
  const hours = Math.floor(navigationDuration.value / 3600)
  const minutes = Math.floor((navigationDuration.value % 3600) / 60)
  const seconds = navigationDuration.value % 60
  return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`
})

const weatherIcon = computed(() => {
  if (temperature.value > 25) return Sun
  if (temperature.value > 15) return Cloud
  return CloudRain
})

const slopeIcon = computed(() => {
  if (slopeDirection.value === 'uphill') return TrendingUp
  if (slopeDirection.value === 'downhill') return TrendingDown
  return Minus
})

const slopeColorClass = computed(() => {
  if (slopeDirection.value === 'uphill') return 'slope-uphill'
  if (slopeDirection.value === 'downhill') return 'slope-downhill'
  return 'slope-flat'
})

const isAndroid = computed(() => {
  return Capacitor.getPlatform() === 'android'
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

// Tooltip function
const showTooltip = (label: string) => {
  // Clear any existing timeout
  if (tooltipTimeout) {
    clearTimeout(tooltipTimeout)
    tooltipTimeout = null
  }

  // If clicking the same tooltip, dismiss it
  if (activeTooltip.value === label) {
    activeTooltip.value = null
    return
  }

  // Set active tooltip
  activeTooltip.value = label

  // Auto-hide after 3 seconds
  tooltipTimeout = window.setTimeout(() => {
    activeTooltip.value = null
  }, 3000)
}

watch([keepScreenOn, isTracking], () => {
  if (keepScreenOn.value && isTracking.value) {
    requestWakeLock()
  } else {
    releaseWakeLock()
  }
})

// Watch navigation state to control duration tracking
watch(isNavigating, (navigating) => {
  if (navigating) {
    // Start navigation duration tracking
    navigationDuration.value = 0
    navigationInterval = window.setInterval(() => {
      navigationDuration.value++
    }, 1000)
  } else {
    // Stop and reset navigation duration tracking
    if (navigationInterval) {
      clearInterval(navigationInterval)
      navigationInterval = null
    }
    navigationDuration.value = 0
  }
})

const startTracking = async () => {
  try {
    // On Android, use native Google Maps location tracking for better speed accuracy
    if (Capacitor.getPlatform() === 'android') {
      // Start native location tracking
      await GoogleMapsNative.startLocationTracking()

      // Listen for location updates
      await GoogleMapsNative.addListener('locationUpdate', (location) => {
        const speedMps = location.speed || 0
        const speedKmh = speedMps * 3.6
        const currentSpeed = unit.value === 'mph' ? speedKmh * 0.621371 : speedKmh

        // Apply speed threshold to filter out GPS noise when stationary
        // Speeds below 2 km/h (1.24 mph) are considered stationary
        const speedThreshold = unit.value === 'mph' ? 1.24 : 2
        speed.value = currentSpeed > speedThreshold ? currentSpeed : 0
        altitude.value = location.altitude || 0
        bearing.value = location.bearing || 0

        // Update current location for MiniMap
        currentLocation.value = {
          lat: location.latitude,
          lng: location.longitude
        }

        // Update slope calculation with GPS data
        updateSlope(
          location.latitude,
          location.longitude,
          location.altitude || 0,
          speedKmh,
          location.accuracy
        )

        tripData.value = {
          ...tripData.value,
          maxSpeed: Math.max(tripData.value.maxSpeed, currentSpeed),
          totalSpeed: tripData.value.totalSpeed + currentSpeed,
          speedSamples: tripData.value.speedSamples + 1,
        }

        if (lastPosition && currentSpeed > 0.5) {
          const distance = calculateDistance(
            lastPosition.latitude,
            lastPosition.longitude,
            location.latitude,
            location.longitude
          )

          tripData.value = {
            ...tripData.value,
            distance: tripData.value.distance + distance,
          }
        }

        lastPosition = location
      })

      watchId = 'native' // Mark as using native tracking
    } else {
      // iOS and web: use Capacitor Geolocation
      const permission = await Geolocation.checkPermissions()
      if (permission.location !== 'granted') {
        const requested = await Geolocation.requestPermissions()
        if (requested.location !== 'granted') {
          toast.error('Location permission denied')
          return
        }
      }

      watchId = await Geolocation.watchPosition(
        {
          enableHighAccuracy: true,
          timeout: 10000,
          maximumAge: 5000
        },
        (position, err) => {
          if (err) {
            console.error('GPS error:', err)
            toast.error('GPS error: ' + err.message)
            return
          }

          if (!position) return

          const speedMps = position.coords.speed || 0
          const speedKmh = speedMps * 3.6
          const currentSpeed = unit.value === 'mph' ? speedKmh * 0.621371 : speedKmh

          // Apply speed threshold to filter out GPS noise when stationary
          // Speeds below 2 km/h (1.24 mph) are considered stationary
          const speedThreshold = unit.value === 'mph' ? 1.24 : 2
          speed.value = currentSpeed > speedThreshold ? currentSpeed : 0
          altitude.value = position.coords.altitude || 0
          bearing.value = position.coords.heading || 0

          // Update current location for MiniMap
          currentLocation.value = {
            lat: position.coords.latitude,
            lng: position.coords.longitude
          }

          updateSlope(
            position.coords.latitude,
            position.coords.longitude,
            position.coords.altitude || 0,
            speedKmh,
            position.coords.accuracy
          )

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
        }
      )
    }

    tripInterval = window.setInterval(() => {
      tripData.value = {
        ...tripData.value,
        duration: tripData.value.duration + 1,
      }
    }, 1000)

    isTracking.value = true
    toast.success('GPS tracking started')
  } catch (error) {
    console.error('Error starting tracking:', error)
    toast.error('Failed to start GPS tracking')
  }
}

const stopTracking = async () => {
  if (watchId !== null) {
    if (Capacitor.getPlatform() === 'android' && watchId === 'native') {
      await GoogleMapsNative.stopLocationTracking()
    } else if (typeof watchId === 'string') {
      await Geolocation.clearWatch({ id: watchId })
    }
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
  min-height: 100dvh;
  width: 100vw;
  overflow: hidden;
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  transition: background-color 0.3s ease, color 0.3s ease;
}

/* Dark Theme (Default) */
.app-container[data-theme="dark"] {
  background: black;
  color: white;
}

.app-container[data-theme="dark"] .background-gradient {
  background: linear-gradient(to bottom right, rgb(3, 7, 18), black, rgb(17, 24, 39));
}

.app-container[data-theme="dark"] .glow-top {
  background: rgba(59, 130, 246, 0.1);
}

.app-container[data-theme="dark"] .glow-bottom {
  background: rgba(34, 197, 94, 0.1);
}

/* Light Theme - TFT Display Style */
.app-container[data-theme="light"] {
  background: linear-gradient(to bottom right, rgb(30, 41, 59), rgb(51, 65, 85), rgb(71, 85, 105));
  color: rgb(226, 232, 240);
}

.app-container[data-theme="light"] .background-gradient {
  background: linear-gradient(to bottom right, rgb(30, 41, 59), rgb(51, 65, 85), rgb(71, 85, 105));
}

.app-container[data-theme="light"] .glow-top {
  background: rgba(56, 189, 248, 0.15);
}

.app-container[data-theme="light"] .glow-bottom {
  background: rgba(34, 211, 238, 0.15);
}

.app-container[data-theme="light"] .info-value,
.app-container[data-theme="light"] .info-value-large,
.app-container[data-theme="light"] .bottom-time,
.app-container[data-theme="light"] .settings-title,
.app-container[data-theme="light"] .toggle-title,
.app-container[data-theme="light"] .about-title,
.app-container[data-theme="light"] .bold {
  color: rgb(224, 242, 254);
  text-shadow: 0 0 8px rgba(56, 189, 248, 0.5);
}

.app-container[data-theme="light"] .info-label,
.app-container[data-theme="light"] .info-sublabel,
.app-container[data-theme="light"] .setting-label,
.app-container[data-theme="light"] .toggle-description,
.app-container[data-theme="light"] .about-content,
.app-container[data-theme="light"] .music-text,
.app-container[data-theme="light"] .info-text{
  color: rgb(186, 230, 253);
}

.app-container[data-theme="light"] .setting-card {
  background: linear-gradient(to bottom right, rgba(51, 65, 85, 0.9), rgba(71, 85, 105, 0.9));
  border: 1px solid rgba(56, 189, 248, 0.3);
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.3);
}

.app-container[data-theme="light"] .center-bottom-info {
  background: rgba(51, 65, 85, 0.85);
  border: 1px solid rgba(56, 189, 248, 0.3);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

.app-container[data-theme="light"] .unit-button,
.app-container[data-theme="light"] .theme-button {
  background: rgba(71, 85, 105, 0.8);
  color: rgb(186, 230, 253);
  border: 1px solid rgba(56, 189, 248, 0.3);
}

.app-container[data-theme="light"] .unit-button:hover,
.app-container[data-theme="light"] .theme-button:hover {
  background: rgba(71, 85, 105, 0.95);
  border-color: rgba(56, 189, 248, 0.5);
}

.app-container[data-theme="light"] .unit-button.active,
.app-container[data-theme="light"] .theme-button.active {
  background: rgb(14, 165, 233);
  color: white;
  border: 1px solid rgb(2, 132, 199);
  box-shadow: 0 0 12px rgba(56, 189, 248, 0.5);
}

.app-container[data-theme="light"] .info-icon {
  color: rgb(56, 189, 248);
  filter: drop-shadow(0 0 4px rgba(56, 189, 248, 0.6));
}

.app-container[data-theme="light"] .info-icon.weather {
  color: rgb(34, 211, 238);
  filter: drop-shadow(0 0 4px rgba(34, 211, 238, 0.6));
}

.app-container[data-theme="light"] .bottom-icon {
  color: rgb(56, 189, 248);
  filter: drop-shadow(0 0 4px rgba(56, 189, 248, 0.6));
}

.app-container[data-theme="light"] .bottom-icon.active {
  color: rgb(34, 211, 238);
  filter: drop-shadow(0 0 4px rgba(34, 211, 238, 0.6));
}

.app-container[data-theme="light"] .mini-music-player {
  background: rgba(51, 65, 85, 0.95);
  border: 1px solid rgba(56, 189, 248, 0.4);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
}

.app-container[data-theme="light"] .mini-album-art {
  background: linear-gradient(135deg, rgba(56, 189, 248, 0.3), rgba(34, 211, 238, 0.3));
}

.app-container[data-theme="light"] .mini-music-icon {
  color: rgb(125, 211, 252);
}

.app-container[data-theme="light"] .mini-track-title {
  color: rgb(224, 242, 254);
}

.app-container[data-theme="light"] .mini-track-artist {
  color: rgb(186, 230, 253);
}

.app-container[data-theme="light"] .mini-control-btn {
  background: rgba(71, 85, 105, 0.7);
}

.app-container[data-theme="light"] .mini-control-btn:hover {
  background: rgba(71, 85, 105, 0.95);
}

.app-container[data-theme="light"] .mini-play-btn {
  background: linear-gradient(135deg, rgb(14, 165, 233), rgb(6, 182, 212));
}

.app-container[data-theme="light"] .mini-play-btn:hover {
  background: linear-gradient(135deg, rgb(2, 132, 199), rgb(8, 145, 178));
  box-shadow: 0 0 12px rgba(56, 189, 248, 0.6);
}

.app-container[data-theme="light"] .mini-close-btn {
  background: rgba(71, 85, 105, 0.7);
}

.app-container[data-theme="light"] .mini-close-btn:hover {
  background: rgba(71, 85, 105, 0.95);
}

.app-container[data-theme="light"] .fuel-input {
  background: rgba(241, 245, 249, 0.9);
  color: rgb(15, 23, 42);
  border: 1px solid rgba(148, 163, 184, 0.4);
}

.app-container[data-theme="light"] .fuel-input:focus {
  border-color: rgb(59, 130, 246);
  background: rgba(255, 255, 255, 0.95);
}

.app-container[data-theme="light"] .toggle-button {
  background: rgba(148, 163, 184, 0.5);
}

.app-container[data-theme="light"] .toggle-button.active {
  background: rgb(37, 99, 235);
}

.app-container[data-theme="light"] .bottom-icon {
  color: rgb(100, 116, 139);
}

.app-container[data-theme="light"] .bottom-icon.active {
  color: rgb(34, 197, 94);
  filter: drop-shadow(0 0 4px rgba(34, 197, 94, 0.6));
}

.background-gradient {
  position: absolute;
  inset: 0;
}

.glow-top {
  position: absolute;
  top: 0;
  left: 25%;
  width: 24rem;
  height: 24rem;
  border-radius: 50%;
  filter: blur(80px);
  transition: background 0.3s ease;
}

.glow-bottom {
  position: absolute;
  bottom: 0;
  right: 25%;
  width: 24rem;
  height: 24rem;
  border-radius: 50%;
  filter: blur(80px);
  transition: background 0.3s ease;
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

/* Hide background elements when native map is active */
.app-container.native-map-active .background-gradient,
.app-container.native-map-active .glow-top,
.app-container.native-map-active .glow-bottom {
  opacity: 0;
  pointer-events: none;
}

.app-container.native-map-active {
  background: transparent !important;
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
  flex-direction: column;
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

/* Left info overlay - position depends on orientation */
.dashboard-container {
  position: relative;
}

.info-overlay.info-left {
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  align-items: flex-start;
  padding-left: 0.5rem;
  max-width: 240px;
  z-index: 10;
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
  margin-bottom: 0.5rem;
  border-radius: 6px;
  overflow: hidden;
  width: 100%; /* Full width of parent container */
}

.map-widget-small.map-hidden {
  visibility: hidden;
}

/* Info Items - Simple Text, No Tiles */
.info-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.15rem 0;
  position: relative;
  cursor: pointer;
  transition: transform 0.2s;
}

.info-overlay.info-left .info-item {
  gap: 0.4rem;
}

.info-overlay.info-left .info-icon {
  width: 1.25rem;
  height: 1.25rem;
}

.info-item:active {
  transform: scale(0.95);
}

.info-item.right-align {
  justify-content: flex-end;
  flex-direction: row-reverse;
}

/* Reset progress indicator */
.info-item.resetting {
  border-radius: 0.375rem;
  padding: 0.5rem;
  overflow: hidden;
}

.reset-progress {
  position: absolute;
  bottom: 0;
  left: 0;
  height: 3px;
  background: linear-gradient(90deg, #ef4444, #dc2626);
  transition: width 0.05s linear;
  border-radius: 0 0 0.375rem 0.375rem;
  box-shadow: 0 0 8px rgba(239, 68, 68, 0.6);
}

/* Tooltip */
.info-tooltip {
  position: absolute;
  background: rgba(17, 24, 39, 0.95);
  color: white;
  padding: 0.5rem 0.75rem;
  border-radius: 0.375rem;
  font-size: 0.875rem;
  font-weight: 500;
  white-space: nowrap;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(75, 85, 99, 0.5);
  z-index: 1000;
}

/* Landscape mode - left side shows tooltip on the right, right side shows on the left (towards center) */
@media (orientation: landscape) {
  .info-item {
    width: fit-content;
  }

  .info-overlay.info-left .info-tooltip {
    left: 100%;
    margin-left: 0.5rem;
    top: 50%;
    transform: translateY(-50%);
    animation: tooltipSlideInRight 0.2s ease-out;
  }

  .info-overlay.info-right .info-tooltip {
    right: 100%;
    margin-right: 0.5rem;
    top: 50%;
    transform: translateY(-50%);
    animation: tooltipSlideInLeft 0.2s ease-out;
  }
}

/* Portrait mode - show tooltip centered for both sides */
@media (orientation: portrait) {
  .info-tooltip {
    left: 50%;
    top: 50%;
    transform: translate(-50%, -50%);
    animation: tooltipFadeIn 0.2s ease-out;
  }
}

.info-tooltip.reset-tooltip {
  font-weight: 600;
}


@keyframes tooltipFadeIn {
  from {
    opacity: 0;
    transform: translate(-50%, -50%) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translate(-50%, -50%) scale(1);
  }
}

/* Info Icons */
.info-icon {
  width: 1.75rem;
  height: 1.75rem;
  color: rgb(74, 222, 128);
  flex-shrink: 0;
  filter: drop-shadow(0 0 3px rgba(74, 222, 128, 0.4));
}

.info-icon.weather {
  color: rgb(250, 204, 21);
  filter: drop-shadow(0 0 3px rgba(250, 204, 21, 0.4));
}

.info-icon.slope-uphill {
  color: rgb(239, 68, 68);
  filter: drop-shadow(0 0 3px rgba(239, 68, 68, 0.4));
}

.info-icon.slope-downhill {
  color: rgb(34, 197, 94);
  filter: drop-shadow(0 0 3px rgba(34, 197, 94, 0.4));
}

.info-icon.slope-flat {
  color: rgb(156, 163, 175);
  filter: drop-shadow(0 0 3px rgba(156, 163, 175, 0.3));
}

/* Info Text */
.info-text {
  display: flex;
  flex-direction: column;
}

.info-label {
  font-size: 0.75rem;
  font-weight: 600;
  color: rgba(156, 163, 175, 0.9);
  letter-spacing: 0.05em;
  line-height: 1.2;
}

.info-sublabel {
  font-size: 1.2rem;
  font-weight: 600;
  color: white;
  line-height: 1.2;
}

.info-value {
  font-size: 1.2rem;
  font-weight: 600;
  color: white;
}

.info-value-large {
  font-size: 1.6rem;
  font-weight: 700;
  color: white;
  text-shadow: 0 0 6px rgba(255, 255, 255, 0.2);
}

/* Marquee effect for long destination names */
.info-value.marquee {
  overflow: hidden;
  max-width: 200px;
  position: relative;
  display: inline-block;
}

.info-value.marquee .marquee-content {
  display: inline-block;
  white-space: nowrap;
  animation: marquee 10s linear infinite;
  padding-right: 2rem;
}

@keyframes marquee {
  0% {
    transform: translateX(0);
  }
  100% {
    transform: translateX(-100%);
  }
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

.with-music-container{
  bottom: 1.5rem;
}

/* Mini Music Player */
.mini-music-player {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.75rem 1.25rem;
  background: rgba(17, 24, 39, 0.95);
  backdrop-filter: blur(12px);
  border-radius: 12px;
  border: 1px solid rgba(75, 85, 99, 0.4);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  min-width: 320px;
  max-width: 400px;
}

.mini-music-info {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex: 1;
  min-width: 0;
}

.mini-album-art {
  width: 48px;
  height: 48px;
  border-radius: 6px;
  overflow: hidden;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.2), rgba(147, 51, 234, 0.2));
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.mini-album-art img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.mini-music-icon {
  width: 24px;
  height: 24px;
  color: rgb(147, 197, 253);
}

.mini-track-details {
  flex: 1;
  min-width: 0;
}

.mini-track-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: white;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 0.15rem;
}

.mini-track-artist {
  font-size: 0.75rem;
  color: rgba(156, 163, 175, 0.8);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mini-music-controls {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.mini-control-btn {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: none;
  background: rgba(55, 65, 81, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
}

.mini-control-btn:hover {
  background: rgba(55, 65, 81, 0.9);
  transform: scale(1.05);
}

.mini-control-btn:active {
  transform: scale(0.95);
}

.mini-play-btn {
  width: 42px;
  height: 42px;
  background: linear-gradient(135deg, rgb(59, 130, 246), rgb(37, 99, 235));
}

.mini-play-btn:hover {
  background: linear-gradient(135deg, rgb(37, 99, 235), rgb(29, 78, 216));
  box-shadow: 0 0 12px rgba(59, 130, 246, 0.5);
}

.mini-control-icon {
  width: 18px;
  height: 18px;
  color: white;
}

.mini-play-btn .mini-control-icon {
  width: 20px;
  height: 20px;
}

.mini-close-btn {
  position: absolute;
  top: 0.35rem;
  right: 0.35rem;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: none;
  background: rgba(55, 65, 81, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
}

.mini-close-btn:hover {
  background: rgba(55, 65, 81, 0.9);
  transform: scale(1.05);
}

.mini-close-btn:active {
  transform: scale(0.95);
}

.mini-close-icon {
  width: 12px;
  height: 12px;
  color: white;
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
  
  .center-bottom-info {
    padding: 0.4rem 0.75rem;
    gap: 0.75rem;
  }
  
  .bottom-time {
    font-size: 0.85rem;
  }

  .mini-music-player {
    min-width: 280px;
    max-width: 320px;
    padding: 0.6rem 1rem;
    bottom: 3.5rem;
  }

  .mini-album-art {
    width: 40px;
    height: 40px;
  }

  .mini-track-title {
    font-size: 0.8rem;
  }

  .mini-track-artist {
    font-size: 0.7rem;
  }

  .mini-control-btn {
    width: 32px;
    height: 32px;
  }

  .mini-play-btn {
    width: 38px;
    height: 38px;
  }

  .mini-control-icon {
    width: 16px;
    height: 16px;
  }

  .mini-play-btn .mini-control-icon {
    width: 18px;
    height: 18px;
  }

  .mini-close-btn {
    width: 18px;
    height: 18px;
    top: 0.3rem;
    right: 0.3rem;
  }

  .mini-close-icon {
    width: 10px;
    height: 10px;
  }
}

@media (orientation: landscape) {
/* Landscape mode - info overlay sizing and positioning */
  .info-overlay.info-left {
    width: 25%;
    max-width: 240px;
    bottom: 1rem; /* Push down from top instead of centering */
    transform: translateY(0); 
  }
}


/* Landscape mode - wider mini music player */
@media (orientation: landscape) {
  .mini-music-player {
    min-width: 500px;
    max-width: 600px;
    padding: 0.6rem 1.5rem;
    gap: 1.5rem;
    padding-top: 1rem;
  }

  .mini-music-controls {
    gap: 1.75rem;
    flex: 0 0 auto;
  }

  .mini-control-btn {
    width: 44px;
    height: 44px;
  }

  .mini-control-icon {
    width: 18px;
    height: 18px;
  }

  .mini-play-btn .mini-control-icon {
    width: 22px;
    height: 22px;
  }

  .mini-album-art {
    width: 44px;
    height: 44px;
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

/* Music Info with Marquee */
.info-item.music-info {
  max-width: 150px;
  overflow: hidden;
}

.music-marquee {
  overflow: hidden;
  position: relative;
  width: 100%;
}

.music-text {
  display: inline-block;
  white-space: nowrap;
  font-size: 0.85rem;
  font-weight: 600;
  color: white;
  animation: marquee 15s linear infinite;
  padding-right: 50px;
}

@keyframes marquee {
  0% {
    transform: translateX(0%);
  }
  100% {
    transform: translateX(-100%);
  }
}

/* Pause animation on hover */
.music-info:hover .music-text {
  animation-play-state: paused;
}

@media (max-width: 768px) {
  .info-item.music-info {
    max-width: 120px;
  }

  .music-text {
    font-size: 0.75rem;
  }
}

/* Portrait Orientation Styles */
@media (orientation: portrait) {
  .tab-content {
    overflow-y: auto;
    overflow-x: hidden;
  }

  .gauge-wrapper {
    flex-direction: column;
    padding: 0.5rem 0;
    min-height: auto;
  }

  /* Portrait layout - center everything */
  .info-overlay.info-left {
    position: relative;
    left: 50%;
    top: auto;
    transform: translateX(-50%);
    width:100%;
    max-width: 500px;
    padding: 0.25rem;
    margin-bottom: 0.25rem;
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
    align-items: stretch;
  }

  /* Minimap full width - stretch within container */
  .info-overlay.info-left .map-widget-small {
    width: 100%;
    margin-bottom: 0;
  }

  /* Info items row container - use grid for precise 2-column layout */
  .info-items-row {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 0.25rem;
    width: 100%;
  }

  /* Info items in portrait - grid handles sizing automatically */
  .info-items-row .info-item {
    padding: 0.75rem 0.5rem !important;
    display: flex !important;
    flex-direction: row !important;
    align-items: center !important;
    background: rgba(17, 24, 39, 0.6) !important;
    border-radius: 8px !important;
    backdrop-filter: blur(8px) !important;
  }

  /* Last item stretches full width if odd number (3 items) */
  .info-items-row .info-item:last-child:nth-child(odd) {
    grid-column: 1 / -1;
  }

  /* Readable text in portrait */
  .info-items-row .info-label {
    font-size: 0.8rem;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .info-items-row .info-sublabel {
    font-size: 1.15rem;
    font-weight: 700;
    white-space: nowrap;
  }

  .info-items-row .info-icon {
    width: 1.35rem;
    height: 1.35rem;
    flex-shrink: 0;
  }

  .info-items-row .info-text {
    min-width: 0;
    flex: 1;
  }

  .info-overlay.info-right {
    position: relative;
    right: auto;
    top: auto;
    transform: none;
    width: 100%;
    padding: 0.5rem 1rem;
    margin-top: 0.5rem;
    align-items: flex-start;
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 0.75rem;
  }


  .info-item {
    justify-content: flex-start;
    padding: 0.5rem;
    background: rgba(17, 24, 39, 0.6);
    border-radius: 8px;
    backdrop-filter: blur(8px);
  }

  .info-item.right-align {
    justify-content: flex-start;
    flex-direction: row;
  }

  .info-item.music-info {
    grid-column: 1 / -1;
    max-width: 100%;
  }

  .mini-music-player {
    position: fixed;
    bottom: 5.5rem;
    left: 50%;
    transform: translateX(-50%);
    min-width: calc(100% - 2rem);
    max-width: calc(100% - 2rem);
    padding: 0.75rem 1rem;
  }

  .center-bottom-info {
    bottom: 0.75rem;
  }

  /* Light theme portrait adjustments */
  .app-container[data-theme="light"] .info-item {
    background: rgba(51, 65, 85, 0.7);
    border: 1px solid rgba(56, 189, 248, 0.3);
  }
}

/* Portrait mode - smaller devices */
@media (orientation: portrait) and (max-width: 768px) {
  .info-label {
    font-size: 0.875rem;
  }

  .info-value {
    font-size: 1rem;
  }

  .info-value-large {
    font-size: 1.3rem;
  }


}
</style>
