<template>
  <ion-app>
  <div
    class="app-container"
    :data-theme="currentTheme"
    :class="{ 'native-map-active': activeTab === 'nav' }"
    :style="{
      paddingLeft: isNavigationBarVisible && navBarPosition === 'left' ? `${leftInset}px` : '0px',
      paddingRight: isNavigationBarVisible && navBarPosition === 'right' ? `${rightInset}px` : '0px'
    }"
  >
    <div class="background-gradient" />

    <div class="glow-top" />
    <div class="glow-bottom" />

    <div class="content-wrapper">
      <StatusBar :isGpsActive="isTracking" :theme="currentTheme" />

      <div class="main-content">
        <!-- Riding Tab -->
        <div v-show="activeTab === 'riding'" class="tab-content dashboard-view">
          <div class="dashboard-container" :class="{ 'horizontal-gauge-mode': gaugeSkin === 'horizontal' }">
            <!-- LEFT INFO - Only show for circular gauge -->
            <div v-if="(showDetailsOnNavigation || isNavigating) && gaugeSkin === 'circular'" class="info-overlay info-left">
              <!-- Mini Map - Hidden when not navigating or disabled in settings -->
              <div class="map-widget-small" :class="{ 'map-hidden': !isNavigating}">
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
              <SpeedometerGauge v-if="gaugeSkin === 'circular'" :speed="speed" :unit="unit" :class="{ 'with-music-container': showMiniPlayer }" />
              <HorizontalTachometer v-else :speed="speed" :unit="unit" />

              <!-- RIGHT INFO - Only show for circular gauge (inside gauge-wrapper) -->
              <div v-if="gaugeSkin === 'circular'" class="info-overlay info-right">
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

              <!-- Mini Music Player - Show when user wants to see it (only for circular gauge) -->
              <div v-if="showMiniPlayer && musicCurrentTrack && activeTab === 'riding' && gaugeSkin === 'circular'" class="mini-music-player" :style="{ marginBottom: isNavigationBarVisible && navBarPosition === 'bottom' ? `${bottomInset}px` : '0px' }">
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

            <!-- BOTTOM INFO BAR - For horizontal gauge mode -->
            <div v-if="gaugeSkin === 'horizontal'" class="horizontal-bottom-bar">
              <!-- Left side - Mini Map + Navigation info -->
              <div v-if="showDetailsOnNavigation || isNavigating" class="bottom-info-section left-section">
                <!-- Mini Map for horizontal mode -->
                <div class="horizontal-map-widget" :class="{ 'map-hidden': !isNavigating || !showMinimap }">
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
                <div class="bottom-info-grid nav-grid" :class="{ 'single-column': !isNavigating || !showMinimap }">
                  <div class="info-item destination-item" @click="showTooltip('Destination')">
                    <MapPin class="info-icon" />
                    <span class="info-value marquee-container">
                      <span class="marquee-text">{{ destination || 'No destination' }}</span>
                    </span>
                    <div v-if="activeTooltip === 'Destination'" class="info-tooltip">Destination</div>
                  </div>
                  <div class="info-item" @click="showTooltip('ETA')">
                    <Clock class="info-icon" />
                    <span class="info-value">{{ estimatedTimeOfArrival }}</span>
                    <div v-if="activeTooltip === 'ETA'" class="info-tooltip">Estimated Arrival</div>
                  </div>
                  <div class="info-item" @click="showTooltip('Duration')">
                    <Timer class="info-icon" />
                    <span class="info-value">{{ formattedTripTime }}</span>
                    <div v-if="activeTooltip === 'Duration'" class="info-tooltip">Duration</div>
                  </div>
                  <div class="info-item" @click="showTooltip('Total Distance')">
                    <Target class="info-icon" />
                    <span class="info-value">{{ navTotalDistance.toFixed(1) }} km</span>
                    <div v-if="activeTooltip === 'Total Distance'" class="info-tooltip">Total Distance</div>
                  </div>
                  <div class="info-item" @click="showTooltip('Remaining')">
                    <Navigation class="info-icon" />
                    <span class="info-value">{{ remainingDistance.toFixed(1) }} km</span>
                    <div v-if="activeTooltip === 'Remaining'" class="info-tooltip">Remaining Distance</div>
                  </div>
                </div>
              </div>

              <!-- Center - Mini Music Player -->
              <div v-if="showMiniPlayer && musicCurrentTrack" class="bottom-music-player">
                <div class="bottom-music-info">
                  <div class="bottom-album-art">
                    <img v-if="musicCurrentTrack.albumArt" :src="musicCurrentTrack?.albumArt" alt="Album art" />
                    <Music v-else class="bottom-music-icon" />
                  </div>
                  <div class="bottom-track-details">
                    <div class="bottom-track-title">{{ musicCurrentTrack.title }}</div>
                    <div class="bottom-track-artist">{{ musicCurrentTrack?.artist }}</div>
                  </div>
                </div>
                <div class="bottom-music-controls">
                  <button @click="musicPreviousTrack()" class="bottom-control-btn">
                    <SkipBack class="bottom-control-icon" />
                  </button>
                  <button @click="musicTogglePlay()" class="bottom-control-btn bottom-play-btn">
                    <Play v-if="!musicIsPlaying" class="bottom-control-icon" />
                    <Pause v-else class="bottom-control-icon" />
                  </button>
                  <button @click="musicNextTrack()" class="bottom-control-btn">
                    <SkipForward class="bottom-control-icon" />
                  </button>
                </div>
                <button @click="closeMiniPlayer()" class="bottom-close-btn">
                  <X class="bottom-close-icon" />
                </button>
              </div>

              <!-- Right side - Stats info (3x2 grid) -->
              <div class="bottom-info-section right-section" :style="isNavigationBarVisible && navBarPosition === 'right' ? { right: `calc(1rem + ${rightInset}px)` } : undefined">
                <div class="bottom-info-grid stats-grid">
                  <!-- Average Speed -->
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

                  <!-- Maximum Speed -->
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

                  <!-- Trip -->
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
                    <div v-if="activeTooltip === 'Trip' && !isResettingTrip" class="info-tooltip">Trip Distance</div>
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
import { App as CapApp } from '@capacitor/app'
import SpeedometerGauge from './components/SpeedometerGauge.vue'
import HorizontalTachometer from './components/HorizontalTachometer.vue'
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
import { useSafeArea } from '../composables/useSafeArea'
import { useGPSTracker } from '../composables/useGPSTracker'
import { useTripManager } from '../composables/useTripManager'
import { Geolocation } from '@capacitor/geolocation'
import { GoogleMapsNative } from '../plugins/googlemaps'
import { Dialog } from '@capacitor/dialog'
import type { Location, NativeGPSPosition, WakeLock, NavigatorWithWakeLock } from '../types'

const {
  isPlaying: musicIsPlaying,
  currentTrack: musicCurrentTrack,
  togglePlay: musicTogglePlay,
  nextTrack: musicNextTrack,
  previousTrack: musicPreviousTrack
} = useMusicPlayer()

const { bottomInset, topInset, leftInset, rightInset, navBarPosition, isNavigationBarVisible } = useSafeArea()

// GPS Tracking
const {
  speed,
  altitude,
  bearing,
  isTracking,
  currentLocation,
  startTracking: gpsStartTracking,
  stopTracking: gpsStopTracking,
  getLastPosition,
} = useGPSTracker()

// Trip Management
const {
  tripData,
  avgSpeed,
  updateTripData,
  startTripTimer,
  stopTripTimer,
  resetAverageSpeed,
  resetMaxSpeed,
  resetTripDistance,
} = useTripManager()

const showMiniPlayer = ref(false)

watch([musicIsPlaying, musicCurrentTrack], ([playing, track]) => {
  if (playing && track) {
    showMiniPlayer.value = true
  }
})

const closeMiniPlayer = () => {
  showMiniPlayer.value = false
}

const { temperature, weatherData, isLoading: weatherLoading, error: weatherError } = useWeather()
const { isNavigating, remainingDistance, totalDistance: navTotalDistance, destination, formattedETA, routePath, nextTurnInstruction, stopNavigation } = useNavigation()
const { formattedSlope, slopeDirection, updateSlope } = useSlope()

const navigationDuration = ref(0)
let navigationInterval: number | null = null

const navigationMapRef = ref<InstanceType<typeof NavigationMap> | null>(null)
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

  stopNavigation()

  if (navigationInterval !== null) {
    clearInterval(navigationInterval)
    navigationInterval = null
  }

  navigationDuration.value = 0
}

import type { TripData } from '../types'

const activeTab = ref<'nav' | 'music' | 'riding' | 'settings'>('riding')
const isSearchingLocation = ref(false)

const activeTooltip = ref<string | null>(null)
let tooltipTimeout: number | null = null

const { theme, unit, keepScreenOn, showMinimap, showDetailsOnNavigation, mapStyle, gaugeSkin } = useSettings()

const currentTheme = computed(() => {
  if (theme.value === 'auto') {
    if (window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches) {
      return 'light'
    }
    return 'dark'
  }
  return theme.value
})

let wakeLock: WakeLock | null = null

const currentTime = ref(new Date().toLocaleTimeString('en-US', { 
  hour: '2-digit', 
  minute: '2-digit',
  hour12: true 
}))

let timeInterval: number | null = null

onMounted(async () => {
  console.log('App.vue: Component mounted')

  // Permissions are now requested natively in MainActivity on app launch
  // No need to request them here in JavaScript

  try {
    await CapStatusBar.hide()
  } catch (error) {
    console.log("Status bar hide failed")
  }

  // Listen to app state changes to debug background audio
  CapApp.addListener('appStateChange', ({ isActive }) => {
    console.log('[App] App state changed, isActive:', isActive)
    if (isActive) {
      console.log('[App] App came to foreground')
    } else {
      console.log('[App] App went to background')
    }
  })

  // Start GPS tracking - permissions should already be granted by MainActivity
  console.log('App.vue: Starting GPS tracking...')
  await gpsStartTracking(
    // Native location update callback
    (location) => {
      const speedKmh = (location.speed || 0) * 3.6
      const currentSpeed = unit.value === 'mph' ? speedKmh * 0.621371 : speedKmh

      // Update slope
      updateSlope(
        location.latitude,
        location.longitude,
        location.altitude || 0,
        speedKmh,
        location.accuracy
      )

      // Update trip data
      const lastPos = getLastPosition()
      updateTripData(currentSpeed, { latitude: location.latitude, longitude: location.longitude }, lastPos)
    },
    // Web location update callback
    (position) => {
      const speedKmh = (position.coords.speed || 0) * 3.6
      const currentSpeed = unit.value === 'mph' ? speedKmh * 0.621371 : speedKmh

      // Update slope
      updateSlope(
        position.coords.latitude,
        position.coords.longitude,
        position.coords.altitude || 0,
        speedKmh,
        position.coords.accuracy
      )

      // Update trip data
      const lastPos = getLastPosition()
      updateTripData(currentSpeed, { latitude: position.coords.latitude, longitude: position.coords.longitude }, lastPos)
    }
  )

  // Start trip timer
  startTripTimer()

  timeInterval = window.setInterval(() => {
    currentTime.value = new Date().toLocaleTimeString('en-US', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: true
    })
  }, 1000)
})

onUnmounted(async () => {
  // Clear all intervals
  if (timeInterval) {
    clearInterval(timeInterval)
    timeInterval = null
  }
  if (navigationInterval) {
    clearInterval(navigationInterval)
    navigationInterval = null
  }
  if (tooltipTimeout) {
    clearTimeout(tooltipTimeout)
    tooltipTimeout = null
  }

  // Clear reset timers and intervals
  if (resetTimerAvg) {
    clearTimeout(resetTimerAvg)
    resetTimerAvg = null
  }
  if (resetProgressIntervalAvg) {
    clearInterval(resetProgressIntervalAvg)
    resetProgressIntervalAvg = null
  }
  if (resetTimerMax) {
    clearTimeout(resetTimerMax)
    resetTimerMax = null
  }
  if (resetProgressIntervalMax) {
    clearInterval(resetProgressIntervalMax)
    resetProgressIntervalMax = null
  }
  if (resetTimerTrip) {
    clearTimeout(resetTimerTrip)
    resetTimerTrip = null
  }
  if (resetProgressIntervalTrip) {
    clearInterval(resetProgressIntervalTrip)
    resetProgressIntervalTrip = null
  }

  // Stop GPS tracking
  await gpsStopTracking()

  // Stop trip timer
  stopTripTimer()

  // Release wake lock
  await releaseWakeLock()
})

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
  const duration = 2000
  const delayBeforeShow = 300

  if (type === 'avg') {
    resetProgressAvg.value = 0

    setTimeout(() => {
      if (resetTimerAvg !== null) {
        isResettingAvg.value = true
      }
    }, delayBeforeShow)

    const startTime = Date.now()

    resetProgressIntervalAvg = window.setInterval(() => {
      const elapsed = Date.now() - startTime
      resetProgressAvg.value = Math.min((elapsed / duration) * 100, 100)
    }, 50)

    try {
      Haptics.impact({ style: ImpactStyle.Light })
    } catch (error) {
      console.log('Haptics not available')
    }

    resetTimerAvg = window.setTimeout(() => {
      resetAverageSpeed()

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

    setTimeout(() => {
      if (resetTimerMax !== null) {
        isResettingMax.value = true
      }
    }, delayBeforeShow)

    const startTime = Date.now()

    resetProgressIntervalMax = window.setInterval(() => {
      const elapsed = Date.now() - startTime
      resetProgressMax.value = Math.min((elapsed / duration) * 100, 100)
    }, 50)

    try {
      Haptics.impact({ style: ImpactStyle.Light })
    } catch (error) {
      console.log('Haptics not available')
    }

    resetTimerMax = window.setTimeout(() => {
      resetMaxSpeed()

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

    setTimeout(() => {
      if (resetTimerTrip !== null) {
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
      resetTripDistance()

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
      wakeLock = await (navigator as NavigatorWithWakeLock).wakeLock?.request('screen') || null
    } catch (err) {
      console.error('Wake Lock error:', err)
    }
  }
}

const releaseWakeLock = async () => {
  if (wakeLock) {
    await wakeLock.release()
    wakeLock = null
  }
}

// Tooltip function
const showTooltip = (label: string) => {
  if (tooltipTimeout) {
    clearTimeout(tooltipTimeout)
    tooltipTimeout = null
  }

  if (activeTooltip.value === label) {
    activeTooltip.value = null
    return
  }

  activeTooltip.value = label

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

watch(isNavigating, (navigating) => {
  if (navigating) {
    navigationDuration.value = 0
    navigationInterval = window.setInterval(() => {
      navigationDuration.value++
    }, 1000)
  } else {
    if (navigationInterval) {
      clearInterval(navigationInterval)
      navigationInterval = null
    }
    navigationDuration.value = 0
  }
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

.app-container[data-theme="light"] .mini-music-player,
.app-container[data-theme="light"] .bottom-music-player {
  background: rgba(51, 65, 85, 0.95);
  border: 1px solid rgba(56, 189, 248, 0.4);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
}

.app-container[data-theme="light"] .mini-album-art,
.app-container[data-theme="light"] .bottom-album-art {
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

.app-container[data-theme="light"] .mini-control-btn,
.app-container[data-theme="light"] .bottom-control-btn {
  background: rgba(71, 85, 105, 0.7);
}

.app-container[data-theme="light"] .mini-control-btn:hover,
.app-container[data-theme="light"] .bottom-control-btn:hover {
  background: rgba(71, 85, 105, 0.95);
}

.app-container[data-theme="light"] .mini-play-btn,
.app-container[data-theme="light"] .bottom-play-btn {
  background: linear-gradient(135deg, rgb(14, 165, 233), rgb(6, 182, 212));
}

.app-container[data-theme="light"] .mini-play-btn:hover,
.app-container[data-theme="light"] .bottom-play-btn:hover {
  background: linear-gradient(135deg, rgb(2, 132, 199), rgb(8, 145, 178));
  box-shadow: 0 0 12px rgba(56, 189, 248, 0.6);
}

.app-container[data-theme="light"] .mini-close-btn,
.app-container[data-theme="light"] .bottom-close-btn {
  background: rgba(71, 85, 105, 0.7);
}

.app-container[data-theme="light"] .mini-close-btn:hover,
.app-container[data-theme="light"] .bottom-close-btn:hover {
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

/* Horizontal Gauge Mode Styles */
.dashboard-container.horizontal-gauge-mode {
  flex-direction: column;
  justify-content: flex-start;
  align-items: center;
  padding-top: 0;
  padding-bottom: 0;
}

.dashboard-container.horizontal-gauge-mode .gauge-wrapper {
  flex: 0 0 auto;
  max-width: 100%;
  width: 100%;
  margin: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* Bottom info bar for horizontal gauge */
.horizontal-bottom-bar {
  position: absolute;
  bottom: 0.75rem;
  left: 0;
  right: 0;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  padding: 0 1rem;
  z-index: 5;
  pointer-events: none;
}

.horizontal-bottom-bar > * {
  pointer-events: auto;
}

.horizontal-bottom-bar > .bottom-music-player {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  bottom: 0;
}

.bottom-info-section {
  display: flex;
  flex-direction: column;
}

.bottom-info-section.left-section {
  align-items: flex-start;
  max-width: 280px;
}

.bottom-info-section.right-section {
  position: fixed;
  right: 1rem;
  top: 60%;
  transform: translateY(-50%);
  align-items: flex-end;
  max-width: 320px;
  z-index: 10;
  padding-bottom: 24px;
}

.bottom-info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.15rem 1rem;
}

.bottom-info-grid.nav-grid {
  grid-template-columns: 1fr 1fr;
}

.bottom-info-grid.nav-grid.single-column {
  grid-template-columns: 1fr;
  gap: 0.5rem;
}

.bottom-info-grid.nav-grid.single-column .info-icon {
  width: 1.25rem;
  height: 1.25rem;
}

.bottom-info-grid.nav-grid.single-column .info-value {
  font-size: 1.1rem;
  font-weight: 600;
}

.bottom-info-grid.stats-grid {
  grid-template-columns: 1fr;
  gap: 0.5rem;
}

.bottom-info-grid.stats-grid .info-item {
  gap: 0.6rem;
}

.bottom-info-grid.stats-grid .info-icon {
  width: 1.25rem;
  height: 1.25rem;
}

.bottom-info-grid.stats-grid .info-value {
  font-size: 1.1rem;
  font-weight: 600;
}

.bottom-info-grid .info-item {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  white-space: nowrap;
  position: relative;
  padding: 0.2rem 0;
}

.bottom-info-grid .info-icon {
  width: 1rem;
  height: 1rem;
}

.bottom-info-grid .info-value {
  font-size: 0.85rem;
}

/* Marquee for destination */
.bottom-info-grid .destination-item {
  overflow: hidden;
}

.marquee-container {
  display: inline-block;
  max-width: 140px;
  overflow: hidden;
  white-space: nowrap;
}

.marquee-text {
  display: inline-block;
  animation: bottom-marquee 12s linear infinite;
  padding-right: 2rem;
}

@keyframes bottom-marquee {
  0% { transform: translateX(0); }
  100% { transform: translateX(-100%); }
}

/* Bottom Music Player */
.bottom-music-player {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.75rem 1rem;
  background: rgba(17, 24, 39, 0.95);
  backdrop-filter: blur(12px);
  border-radius: 12px;
  border: 1px solid rgba(75, 85, 99, 0.4);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  max-width: 500px;
  min-width: 360px;
}

.bottom-music-info {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex: 1;
  min-width: 0;
}

.bottom-album-art {
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

.bottom-album-art img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.bottom-music-icon {
  width: 24px;
  height: 24px;
  color: rgb(147, 197, 253);
}

.bottom-track-details {
  flex: 1;
  min-width: 0;
}

.bottom-track-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: white;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 0.15rem;
}

.bottom-track-artist {
  font-size: 0.75rem;
  color: rgba(156, 163, 175, 0.8);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bottom-music-controls {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.bottom-control-btn {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: none;
  background: rgba(55, 65, 81, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
}

.bottom-control-btn:hover {
  background: rgba(55, 65, 81, 0.9);
  transform: scale(1.05);
}

.bottom-control-btn:active {
  transform: scale(0.95);
}

.bottom-play-btn {
  width: 44px;
  height: 44px;
  background: linear-gradient(135deg, rgb(59, 130, 246), rgb(37, 99, 235));
}

.bottom-play-btn:hover {
  background: linear-gradient(135deg, rgb(37, 99, 235), rgb(29, 78, 216));
  box-shadow: 0 0 12px rgba(59, 130, 246, 0.5);
}

.bottom-control-icon {
  width: 18px;
  height: 18px;
  color: white;
}

.bottom-play-btn .bottom-control-icon {
  width: 20px;
  height: 20px;
}

.bottom-close-btn {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: none;
  background: rgba(55, 65, 81, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}

.bottom-close-btn:hover {
  background: rgba(55, 65, 81, 0.9);
  transform: scale(1.05);
}

.bottom-close-icon {
  width: 14px;
  height: 14px;
  color: white;
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
  max-width: 350px; /* Larger max width */
  z-index: 10;
}

.info-overlay.info-right {
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  align-items: flex-end;
  padding-right: 0.5rem;
}

/* Info items row - no background, just vertical layout */
.info-items-row {
    padding-left: 0.5rem;
    gap: 0.4rem;
}

.map-widget-small {
  height: 45vh;
  width: 22vw;
}

.map-widget-small.map-hidden {
  visibility: hidden;
  display: none; /* Also hide from layout when not visible */
}

/* Horizontal mode map widget */
.horizontal-map-widget {
  height: 35vh;
  width: 20vw;
  min-width: 180px;
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 0.5rem;
}

.horizontal-map-widget.map-hidden {
  display: none;
}


.info-items-row .info-value {
  color: white;
  font-weight: 600;
  font-size: 1rem;
  flex: 1;
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

  /* Horizontal gauge mode - left section tooltip on right, right section tooltip on left */
  .dashboard-container.horizontal-gauge-mode .bottom-info-section.left-section .info-tooltip {
    left: 100%;
    right: auto;
    margin-left: 0.5rem;
    top: 50%;
    transform: translateY(-50%);
    animation: tooltipSlideInRight 0.2s ease-out;
  }

  .dashboard-container.horizontal-gauge-mode .bottom-info-section.right-section .info-tooltip {
    right: 100%;
    left: auto;
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
  /* Note: This element is not currently in use in the template */
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
  padding: 0.75rem 1rem;
  background: rgba(17, 24, 39, 0.95);
  backdrop-filter: blur(12px);
  border-radius: 12px;
  border: 1px solid rgba(75, 85, 99, 0.4);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  min-width: 320px;
  max-width: 400px;
  /* Dynamic margin-bottom applied via inline style from composable */
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
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: none;
  background: rgba(55, 65, 81, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}

.mini-close-btn:hover {
  background: rgba(55, 65, 81, 0.9);
  transform: scale(1.05);
}

.mini-close-btn:active {
  transform: scale(0.95);
}

.mini-close-icon {
  width: 14px;
  height: 14px;
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
    width: 300px; /* Fixed width to match destination container */
    max-width: 300px;
    top: 0;
    bottom: 0;
    transform: none;
    display: flex;
    flex-direction: column;
    justify-content: center;
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
  /* Note: This element is not currently in use in the template */
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
    height: 20vh;
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
    bottom: auto;
    left: 50%;
    transform: translateX(-50%);
    min-width: calc(100% - 2rem);
    max-width: calc(100% - 2rem);
    padding: 0.75rem 1rem;
    margin-top: 2.75rem;
    /* Dynamic margin-bottom applied via inline style */
  }

  .center-bottom-info {
    bottom: 0.75rem;
    /* Note: This element is not currently in use in the template */
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

/* Portrait mode - Horizontal Gauge specific styles */
@media (orientation: portrait) {
  .dashboard-container.horizontal-gauge-mode {
    justify-content: center;
    align-items: center;
    padding: 1rem;
    height: 100%;
  }

  .dashboard-container.horizontal-gauge-mode .gauge-wrapper {
    width: 100%;
    max-width: 100%;
    display: flex;
    flex-direction: column;
    align-items: center;
  }

  /* Horizontal bottom bar in portrait - stack vertically */
  .dashboard-container.horizontal-gauge-mode .horizontal-bottom-bar {
    position: relative;
    bottom: auto;
    left: auto;
    right: auto;
    flex-direction: column;
    align-items: center;
    padding: 0.5rem 1rem;
    gap: 0.75rem;
    width: 100%;
    margin-top: 0.5rem;
  }

  /* Left section in portrait */
  .dashboard-container.horizontal-gauge-mode .bottom-info-section.left-section {
    position: relative;
    width: 100%;
    max-width: 100%;
    align-items: center;
  }

  .dashboard-container.horizontal-gauge-mode .horizontal-map-widget {
    width: 100%;
    height: 25vh;
    min-width: unset;
    margin-bottom: 0.75rem;
  }

  .dashboard-container.horizontal-gauge-mode .bottom-info-grid.nav-grid {
    width: 100%;
    grid-template-columns: 1fr 1fr;
    gap: 0.5rem;
  }

  .dashboard-container.horizontal-gauge-mode .bottom-info-grid.nav-grid.single-column {
    grid-template-columns: 1fr 1fr;
  }

  .dashboard-container.horizontal-gauge-mode .bottom-info-grid.nav-grid .info-item {
    background: rgba(17, 24, 39, 0.6);
    border-radius: 8px;
    padding: 0.5rem 0.75rem;
    backdrop-filter: blur(8px);
  }

  /* Right section in portrait - make it relative and full width */
  .dashboard-container.horizontal-gauge-mode .bottom-info-section.right-section {
    position: relative;
    right: auto;
    top: auto;
    transform: none;
    width: 100%;
    max-width: 100%;
    padding-bottom: 0;
    align-items: center;
  }

  .dashboard-container.horizontal-gauge-mode .bottom-info-grid.stats-grid {
    width: 100%;
    grid-template-columns: 1fr 1fr;
    gap: 0.5rem;
  }

  .dashboard-container.horizontal-gauge-mode .bottom-info-grid.stats-grid .info-item {
    background: rgba(17, 24, 39, 0.6);
    border-radius: 8px;
    padding: 0.5rem 0.75rem;
    backdrop-filter: blur(8px);
  }

  /* Stretch last item full width when odd number of items */
  .dashboard-container.horizontal-gauge-mode .bottom-info-grid.nav-grid .info-item:last-child:nth-child(odd) {
    grid-column: 1 / -1;
  }

  .dashboard-container.horizontal-gauge-mode .bottom-info-grid.stats-grid .info-item:last-child:nth-child(odd) {
    grid-column: 1 / -1;
  }

  /* Music player in portrait for horizontal mode */
  .dashboard-container.horizontal-gauge-mode .bottom-music-player {
    position: relative;
    left: auto;
    transform: none;
    width: 100%;
    max-width: 100%;
    min-width: unset;
  }
}
</style>
