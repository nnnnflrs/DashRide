<template>
  <div class="settings-container" :data-theme="theme">
    <h2 class="settings-title">Settings</h2>
    
    <!-- Theme Setting -->
    <div class="setting-card">
      <label class="setting-label">Theme</label>
      <div class="theme-buttons">
        <button
          @click="settingsTheme = 'auto'"
          :class="['theme-button', { active: settingsTheme === 'auto' }]"
        >
          Automatic
        </button>
        <button
          @click="settingsTheme = 'light'"
          :class="['theme-button', { active: settingsTheme === 'light' }]"
        >
          Light
        </button>
        <button
          @click="settingsTheme = 'dark'"
          :class="['theme-button', { active: settingsTheme === 'dark' }]"
        >
          Dark
        </button>
      </div>
    </div>

    <!-- Speed Unit Setting -->
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

    <!-- Map Settings Section -->
    <div class="setting-card">
      <label class="setting-label">Map Settings</label>

      <!-- Map Style Selection -->
      <div style="margin-bottom: 1rem;">
        <label class="toggle-title" style="padding-bottom: 1rem;">Map Style</label>
        <p class="toggle-description" style="padding-bottom: 1rem;">
          Choose the color scheme for navigation map
        </p>
        <div class="unit-buttons">
          <button
            @click="mapStyle = 'dark'"
            :class="['unit-button', { active: mapStyle === 'dark' }]"
          >
            Dark
          </button>
          <button
            @click="mapStyle = 'light'"
            :class="['unit-button', { active: mapStyle === 'light' }]"
          >
            Light
          </button>
        </div>
      </div>

      <!-- Avoid Tolls Toggle -->
      <div class="toggle-row">
        <div>
          <div class="toggle-title">Avoid Tolls</div>
          <div class="toggle-description">
            Avoid toll roads when calculating routes
          </div>
        </div>
        <button
          @click="avoidTolls = !avoidTolls"
          :class="['toggle-button', { active: avoidTolls }]"
        >
          <div :class="['toggle-knob', { active: avoidTolls }]" />
        </button>
      </div>

      <!-- Show Minimap Toggle -->
      <div class="toggle-row">
        <div>
          <div class="toggle-title">Show Mini-map</div>
          <div class="toggle-description">
            Display minimap in riding view during navigation
          </div>
        </div>
        <button
          @click="showMinimap = !showMinimap"
          :class="['toggle-button', { active: showMinimap }]"
        >
          <div :class="['toggle-knob', { active: showMinimap }]" />
        </button>
      </div>

      <!-- Show Minimap Toggle -->
      <div class="toggle-row">
        <div>
          <div class="toggle-title">Show Navigation Details</div>
          <div class="toggle-description">
            Always show navigation details
          </div>
        </div>
        <button
          @click="showDetailsOnNavigation = !showDetailsOnNavigation"
          :class="['toggle-button', { active: showDetailsOnNavigation }]"
        >
          <div :class="['toggle-knob', { active: showDetailsOnNavigation }]" />
        </button>
      </div>

      <!-- Voice Instruction -->

      <div class="toggle-row">
        <div>
          <div class="toggle-title">Voice Instructions</div>
          <div class="toggle-description">
            Enable turn-by-turn voice navigation
          </div>
        </div>
        <button
          @click="voiceInstructions = !voiceInstructions"
          :class="['toggle-button', { active: voiceInstructions }]"
        >
          <div :class="['toggle-knob', { active: voiceInstructions }]" />
        </button>
      </div>
      
    </div>

    <!-- Keep Screen On Toggle -->
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

    <!-- Voice Instructions Toggle -->
    <!-- <div class="setting-card">
      <div class="toggle-row">
        <div>
          <div class="toggle-title">Voice Instructions</div>
          <div class="toggle-description">
            Enable turn-by-turn voice guidance during navigation
          </div>
        </div>
        <button
          @click="voiceInstructions = !voiceInstructions"
          :class="['toggle-button', { active: voiceInstructions }]"
        >
          <div :class="['toggle-knob', { active: voiceInstructions }]" />
        </button>
      </div>
    </div> -->

    <!-- Skins Section (Coming Soon) -->
    <div class="setting-card coming-soon-card">
      <label class="setting-label">Skins</label>
      <div class="coming-soon-content">
        <div class="coming-soon-text">
          <span class="coming-soon-badge">Coming Soon</span>
          <p class="coming-soon-description">Customize your dashboard with different visual themes and styles</p>
        </div>
      </div>
    </div>

    <!-- Feedback Section -->
    <div class="setting-card">
      <label class="setting-label">Feedback</label>
      <button @click="openFeedbackForm" class="feedback-button">
        <svg class="feedback-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/>
        </svg>
        <span>Report Bug / Request Feature</span>
      </button>
      <p class="feedback-description">Spot a bug or have an idea? Let me know!</p>
    </div>

    <!-- About Section -->
    <div class="setting-card">
      <h3 class="about-title">About</h3>
      <div class="about-content">
        <p>Modern TFT-style motorcycle dashboard application that transforms your phone into a comprehensive riding companion with real-time data visualization and navigation.</p>

        <p class="free-badge">
          <span class="bold">🎉 Free and always will be</span>
        </p>

        <p class="data-sources-title">
          <span class="bold">Key Features:</span>
        </p>
        <ul class="data-sources-list">
          <li>Real-time speedometer with color-coded zones</li>
          <li>GPS navigation with turn-by-turn directions</li>
          <li>Interactive minimap during navigation</li>
          <li>Music player with native audio integration</li>
          <li>Trip tracking (distance, duration, avg/max speed)</li>
          <li>Slope calculation and altitude display</li>
          <li>Real-time weather information</li>
          <li>Dark and light themes</li>
          <li>Landscape fullscreen mode optimized for mounting</li>
        </ul>

        <p class="data-sources-title">
          <span class="bold">Data Sources:</span>
        </p>
        <ul class="data-sources-list">
          <li>GPS (Geolocation API) - Speed, location, and navigation</li>
          <li>Device sensors - Motion data for slope calculation</li>
          <li>Google Maps API - Navigation and route planning</li>
          <li>Google Places API - Location search and autocomplete</li>
          <li>Open-Meteo API - Real-time weather information</li>
          <li>MediaStore API - Local music library access</li>
        </ul>

        <!-- Share Section -->
        <div class="share-section">
          <p class="data-sources-title">
            <span class="bold">Share DashRide:</span>
          </p>
          <div class="share-buttons">
            <button @click="shareToFacebook" class="share-button facebook">
              <svg class="share-icon" viewBox="0 0 24 24" fill="currentColor">
                <path d="M24 12.073c0-6.627-5.373-12-12-12s-12 5.373-12 12c0 5.99 4.388 10.954 10.125 11.854v-8.385H7.078v-3.47h3.047V9.43c0-3.007 1.792-4.669 4.533-4.669 1.312 0 2.686.235 2.686.235v2.953H15.83c-1.491 0-1.956.925-1.956 1.874v2.25h3.328l-.532 3.47h-2.796v8.385C19.612 23.027 24 18.062 24 12.073z"/>
              </svg>
            </button>
            <button @click="shareToTwitter" class="share-button twitter">
              <svg class="share-icon" viewBox="0 0 24 24" fill="currentColor">
                <path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z"/>
              </svg>
              
            </button>
            <button @click="shareToInstagram" class="share-button instagram">
              <svg class="share-icon" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 0C8.74 0 8.333.015 7.053.072 5.775.132 4.905.333 4.14.63c-.789.306-1.459.717-2.126 1.384S.935 3.35.63 4.14C.333 4.905.131 5.775.072 7.053.012 8.333 0 8.74 0 12s.015 3.667.072 4.947c.06 1.277.261 2.148.558 2.913.306.788.717 1.459 1.384 2.126.667.666 1.336 1.079 2.126 1.384.766.296 1.636.499 2.913.558C8.333 23.988 8.74 24 12 24s3.667-.015 4.947-.072c1.277-.06 2.148-.262 2.913-.558.788-.306 1.459-.718 2.126-1.384.666-.667 1.079-1.335 1.384-2.126.296-.765.499-1.636.558-2.913.06-1.28.072-1.687.072-4.947s-.015-3.667-.072-4.947c-.06-1.277-.262-2.149-.558-2.913-.306-.789-.718-1.459-1.384-2.126C21.319 1.347 20.651.935 19.86.63c-.765-.297-1.636-.499-2.913-.558C15.667.012 15.26 0 12 0zm0 2.16c3.203 0 3.585.016 4.85.071 1.17.055 1.805.249 2.227.415.562.217.96.477 1.382.896.419.42.679.819.896 1.381.164.422.36 1.057.413 2.227.057 1.266.07 1.646.07 4.85s-.015 3.585-.074 4.85c-.061 1.17-.256 1.805-.421 2.227-.224.562-.479.96-.899 1.382-.419.419-.824.679-1.38.896-.42.164-1.065.36-2.235.413-1.274.057-1.649.07-4.859.07-3.211 0-3.586-.015-4.859-.074-1.171-.061-1.816-.256-2.236-.421-.569-.224-.96-.479-1.379-.899-.421-.419-.69-.824-.9-1.38-.165-.42-.359-1.065-.42-2.235-.045-1.26-.061-1.649-.061-4.844 0-3.196.016-3.586.061-4.861.061-1.17.255-1.814.42-2.234.21-.57.479-.96.9-1.381.419-.419.81-.689 1.379-.898.42-.166 1.051-.361 2.221-.421 1.275-.045 1.65-.06 4.859-.06l.045.03zm0 3.678c-3.405 0-6.162 2.76-6.162 6.162 0 3.405 2.76 6.162 6.162 6.162 3.405 0 6.162-2.76 6.162-6.162 0-3.405-2.76-6.162-6.162-6.162zM12 16c-2.21 0-4-1.79-4-4s1.79-4 4-4 4 1.79 4 4-1.79 4-4 4zm7.846-10.405c0 .795-.646 1.44-1.44 1.44-.795 0-1.44-.646-1.44-1.44 0-.794.646-1.439 1.44-1.439.793-.001 1.44.645 1.44 1.439z"/>
              </svg>
            </button>
          </div>
        </div>

        <!-- Buy Me a Coffee - Compact -->
        <div class="donation-section">
          <p class="donation-title">☕ Bili mo ako ng kape</p>
          <div class="donation-row">
            <svg class="solana-icon-small" viewBox="0 0 397.7 311.7" xmlns="http://www.w3.org/2000/svg">
              <defs>
                <linearGradient id="solanaGradient" x1="360.88" y1="351.46" x2="141.21" y2="-69.29" gradientUnits="userSpaceOnUse">
                  <stop offset="0" stop-color="#00ffa3"/>
                  <stop offset="1" stop-color="#dc1fff"/>
                </linearGradient>
              </defs>
              <path d="M64.6 237.9c2.4-2.4 5.7-3.8 9.2-3.8h317.4c5.8 0 8.7 7 4.6 11.1l-62.7 62.7c-2.4 2.4-5.7 3.8-9.2 3.8H6.5c-5.8 0-8.7-7-4.6-11.1l62.7-62.7z" fill="url(#solanaGradient)"/>
              <path d="M64.6 3.8C67.1 1.4 70.4 0 73.8 0h317.4c5.8 0 8.7 7 4.6 11.1l-62.7 62.7c-2.4 2.4-5.7 3.8-9.2 3.8H6.5c-5.8 0-8.7-7-4.6-11.1L64.6 3.8z" fill="url(#solanaGradient)"/>
              <path d="M333.1 120.1c-2.4-2.4-5.7-3.8-9.2-3.8H6.5c-5.8 0-8.7 7-4.6 11.1l62.7 62.7c2.4 2.4 5.7 3.8 9.2 3.8h317.4c5.8 0 8.7-7 4.6-11.1l-62.7-62.7z" fill="url(#solanaGradient)"/>
            </svg>
            <span class="donation-text">{{ walletAddress }}</span>
            <button @click="copyWalletAddress" class="copy-icon-button" :title="copied ? 'Copied!' : 'Copy wallet address'">
              <Copy v-if="!copied" class="icon-small" />
              <Check v-else class="icon-small success" />
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Copy, Check } from 'lucide-vue-next'
import { useSettings } from '../../composables/useSettings'

interface Props {
  theme?: 'light' | 'dark'
}

defineProps<Props>()

// Use shared settings state
const { theme: settingsTheme, unit, keepScreenOn, avoidTolls, showMinimap, mapStyle, showDetailsOnNavigation, voiceInstructions } = useSettings()

// Wallet address for donations
const walletAddress = '37qjhJQno7bybHembmiCYcaTXGAauQompfjwS8ki2uz8'
const copied = ref(false)

const copyWalletAddress = async () => {
  try {
    await navigator.clipboard.writeText(walletAddress)
    copied.value = true
    setTimeout(() => {
      copied.value = false
    }, 2000)
  } catch {
    // Clipboard not available
  }
}

// Share functions
const appUrl = 'https://play.google.com/store/apps/details?id=com.motorcycle.dashride.ph'
const shareText = 'Check out DashRide - A modern motorcycle dashboard app with GPS navigation, speedometer, and music player! 🏍️'

const shareToFacebook = () => {
  const url = `https://www.facebook.com/sharer/sharer.php?u=${encodeURIComponent(appUrl)}`
  window.open(url, '_blank', 'width=600,height=400')
}

const shareToTwitter = () => {
  const url = `https://twitter.com/intent/tweet?text=${encodeURIComponent(shareText)}&url=${encodeURIComponent(appUrl)}`
  window.open(url, '_blank', 'width=600,height=400')
}

const shareToInstagram = () => {
  // Instagram doesn't have a direct web share API like Facebook/Twitter
  // Open Instagram app or web profile
  // For web, we'll copy the text and open Instagram
  navigator.clipboard.writeText(`${shareText}\n${appUrl}`).then(() => {
    alert('Share text copied! Paste it in your Instagram post.')
    window.open('https://www.instagram.com/', '_blank')
  }).catch(() => {
    alert('Please share manually on Instagram')
  })
}

// Feedback form
const openFeedbackForm = () => {
  window.open('https://docs.google.com/forms/d/e/1FAIpQLSfNBAYi-nCqQOsg2e574fyrr8EH7Tdkp8n60ckY4XfanKjB6Q/viewform', '_blank')
}
</script>

<style scoped>
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
  transition: color 0.3s ease;
}

.setting-card {
  background: linear-gradient(to bottom right, rgba(17, 24, 39, 0.8), rgba(31, 41, 55, 0.8));
  backdrop-filter: blur(8px);
  border-radius: 0.5rem;
  border: 1px solid rgba(55, 65, 81, 0.5);
  padding: 1rem;
  transition: background 0.3s ease, border-color 0.3s ease;
}

.setting-label {
  font-size: 0.875rem;
  color: rgba(156, 163, 175, 1);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 0.75rem;
  display: block;
  transition: color 0.3s ease;
}

/* Light Theme Settings */
.settings-container[data-theme="light"] .settings-title {
  color: rgb(15, 23, 42);
}

.settings-container[data-theme="light"] .setting-card {
  background: linear-gradient(to bottom right, rgba(255, 255, 255, 0.95), rgba(241, 245, 249, 0.95));
  border: 1px solid rgba(148, 163, 184, 0.4);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.settings-container[data-theme="light"] .setting-label {
  color: rgb(71, 85, 105);
}

.settings-container[data-theme="light"] .unit-button,
.settings-container[data-theme="light"] .theme-button {
  background: rgba(226, 232, 240, 0.8);
  color: rgb(51, 65, 85);
  border: 1px solid rgba(148, 163, 184, 0.3);
}

.settings-container[data-theme="light"] .unit-button:hover,
.settings-container[data-theme="light"] .theme-button:hover {
  background: rgba(203, 213, 225, 0.9);
}

.settings-container[data-theme="light"] .unit-button.active,
.settings-container[data-theme="light"] .theme-button.active {
  background: rgb(37, 99, 235);
  color: white;
  border: 1px solid rgb(29, 78, 216);
}

.settings-container[data-theme="light"] .fuel-input {
  background: rgba(241, 245, 249, 0.9);
  color: rgb(15, 23, 42);
  border: 1px solid rgba(148, 163, 184, 0.4);
}

.settings-container[data-theme="light"] .fuel-input:focus {
  border-color: rgb(59, 130, 246);
  background: rgba(255, 255, 255, 0.95);
}

.settings-container[data-theme="light"] .toggle-title {
  color: rgb(15, 23, 42);
}

.settings-container[data-theme="light"] .toggle-description {
  color: rgb(71, 85, 105);
}

.settings-container[data-theme="light"] .toggle-button {
  background: rgba(148, 163, 184, 0.5);
}

.settings-container[data-theme="light"] .toggle-button.active {
  background: rgb(37, 99, 235);
}

.settings-container[data-theme="light"] .about-title {
  color: rgb(15, 23, 42);
}

.settings-container[data-theme="light"] .about-content {
  color: rgb(71, 85, 105);
}

.settings-container[data-theme="light"] .bold {
  color: rgb(15, 23, 42);
}

.unit-buttons {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0.75rem;
}

.theme-buttons {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.75rem;
}

.unit-button,
.theme-button {
  padding: 0.75rem;
  border-radius: 0.5rem;
  font-weight: 600;
  transition: all 0.2s;
  background: rgb(31, 41, 55);
  color: rgba(156, 163, 175, 1);
  border: none;
  cursor: pointer;
}

.unit-button:hover,
.theme-button:hover {
  background: rgb(55, 65, 81);
}

.unit-button.active,
.theme-button.active {
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
  margin-top: 1rem;
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

.free-badge {
  padding: 0.75rem 1rem;
  background: linear-gradient(135deg, rgba(34, 197, 94, 0.15), rgba(16, 185, 129, 0.15));
  border: 1px solid rgba(34, 197, 94, 0.3);
  border-radius: 0.5rem;
  text-align: center;
  margin: 1rem 0;
}

.settings-container[data-theme="light"] .free-badge {
  background: linear-gradient(135deg, rgba(34, 197, 94, 0.1), rgba(16, 185, 129, 0.1));
  border-color: rgba(34, 197, 94, 0.25);
}

.donation-section {
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid rgba(55, 65, 81, 0.5);
}

.settings-container[data-theme="light"] .donation-section {
  border-top-color: rgba(148, 163, 184, 0.3);
}

.donation-title {
  font-size: 0.875rem;
  color: rgba(156, 163, 175, 1);
  margin-bottom: 0.5rem;
  font-weight: 500;
}

.settings-container[data-theme="light"] .donation-title {
  color: rgb(71, 85, 105);
}

.donation-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background: rgba(0, 0, 0, 0.2);
  padding: 0.5rem;
  border-radius: 0.375rem;
  border: 1px solid rgba(55, 65, 81, 0.5);
}

.settings-container[data-theme="light"] .donation-row {
  background: rgba(241, 245, 249, 0.6);
  border-color: rgba(148, 163, 184, 0.3);
}

.solana-icon-small {
  width: 1rem;
  height: 1rem;
  flex-shrink: 0;
}

.donation-text {
  flex: 1;
  font-family: 'SF Mono', 'Monaco', 'Courier New', monospace;
  font-size: 0.7rem;
  color: rgb(156, 163, 175);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.settings-container[data-theme="light"] .donation-text {
  color: rgb(71, 85, 105);
}

.copy-icon-button {
  background: none;
  border: none;
  padding: 0.25rem;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 0.25rem;
  transition: background 0.2s;
  flex-shrink: 0;
}

.copy-icon-button:hover {
  background: rgba(59, 130, 246, 0.2);
}

.icon-small {
  width: 1rem;
  height: 1rem;
  color: rgb(156, 163, 175);
}

.settings-container[data-theme="light"] .icon-small {
  color: rgb(71, 85, 105);
}

.icon-small.success {
  color: rgb(74, 222, 128);
}

/* Coming Soon Card */
.coming-soon-card {
  opacity: 0.7;
  position: relative;
}

.coming-soon-content {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.coming-soon-text {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.coming-soon-badge {
  display: inline-block;
  padding: 0.25rem 0.75rem;
  background: linear-gradient(135deg, rgb(59, 130, 246), rgb(147, 51, 234));
  color: white;
  font-size: 0.75rem;
  font-weight: 600;
  border-radius: 9999px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  width: fit-content;
}

.coming-soon-description {
  font-size: 0.875rem;
  color: rgba(156, 163, 175, 1);
  margin: 0;
}

.settings-container[data-theme="light"] .coming-soon-description {
  color: rgb(71, 85, 105);
}

/* Share Section */
.share-section {
  margin-top: 1.5rem;
  padding-top: 1.5rem;
  border-top: 1px solid rgba(55, 65, 81, 0.3);
}

.share-buttons {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
  margin-top: 0.75rem;
}

.share-button {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1.25rem;
  border: none;
  border-radius: 0.5rem;
  font-size: 0.875rem;
  font-weight: 600;
  color: white;
  cursor: pointer;
  transition: all 0.2s ease;
  flex: 1;
  min-width: 120px;
  justify-content: center;
}

.share-icon {
  width: 1.25rem;
  height: 1.25rem;
}

.share-button.facebook {
  background: #1877F2;
}

.share-button.facebook:hover {
  background: #0d65d9;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(24, 119, 242, 0.4);
}

.share-button.twitter {
  background: #000000;
}

.share-button.twitter:hover {
  background: #000000;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(29, 161, 242, 0.4);
}

.share-button.instagram {
  background: linear-gradient(45deg, #f09433 0%, #e6683c 25%, #dc2743 50%, #cc2366 75%, #bc1888 100%);
}

.share-button.instagram:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(188, 24, 136, 0.4);
}

.share-button:active {
  transform: translateY(0);
}

/* Light theme adjustments for share section */
.settings-container[data-theme="light"] .share-section {
  border-top-color: rgba(148, 163, 184, 0.3);
}

/* Feedback Section */
.feedback-button {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  padding: 1rem 1.5rem;
  background: linear-gradient(135deg, rgb(59, 130, 246), rgb(37, 99, 235));
  color: white;
  border: none;
  border-radius: 0.5rem;
  font-size: 0.9375rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  margin-bottom: 0.5rem;
}

.feedback-button:hover {
  background: linear-gradient(135deg, rgb(37, 99, 235), rgb(29, 78, 216));
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.4);
}

.feedback-button:active {
  transform: translateY(0);
}

.feedback-icon {
  width: 1.25rem;
  height: 1.25rem;
  flex-shrink: 0;
}

.feedback-description {
  font-size: 0.8125rem;
  color: rgba(156, 163, 175, 1);
  text-align: center;
  margin: 0;
}

.settings-container[data-theme="light"] .feedback-description {
  color: rgb(71, 85, 105);
}
</style>
