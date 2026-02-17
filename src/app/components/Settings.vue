<template>
  <div class="settings-container" :data-theme="theme" :data-shader="isMetalShader ? 'metal' : 'original'">
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
            Display minimap when navigating
          </div>
        </div>
        <button
          @click="showMinimap = !showMinimap"
          :class="['toggle-button', { active: showMinimap }]"
        >
          <div :class="['toggle-knob', { active: showMinimap }]" />
        </button>
      </div>

      <!-- Show Navigation Details Toggle -->
      <div class="toggle-row">
        <div>
          <div class="toggle-title">Show Navigation Details</div>
          <div class="toggle-description">
            Only show navigation details when navigating
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

 
    <div class="setting-card">
      <label class="setting-label">Miscellaneous</label>

      <!-- Accent Theme Selector -->
      <div style="margin-bottom: 1rem;">
        <div class="toggle-title">Accent Theme</div>
        <div class="toggle-description" style="margin-bottom: 0.75rem;">
          Choose your dashboard accent style
        </div>
        <div class="accent-grid">
          <button
            v-for="accent in accentThemes"
            :key="accent.id"
            @click="metalShader = accent.id"
            :class="['accent-option', { active: metalShader === accent.id }]"
            :style="metalShader === accent.id ? { borderColor: accent.color, boxShadow: `0 0 12px ${accent.color}25` } : {}"
          >
            <div class="accent-color-dot" :style="{ backgroundColor: accent.color, boxShadow: `0 0 8px ${accent.color}60` }" />
            <span class="accent-name">{{ accent.name }}</span>
            <span class="accent-subtitle">{{ accent.subtitle }}</span>
          </button>
        </div>
      </div>

      <!-- Status bar -->
      <div class="toggle-row">
          <div>
            <div class="toggle-title">Show Status bar</div>
            <div class="toggle-description">
              Show status bar in the app
            </div>
          </div>
          <button
            @click="showStatusBar = !showStatusBar"
            :class="['toggle-button', { active: showStatusBar }]"
          >
            <div :class="['toggle-knob', { active: showStatusBar }]" />
          </button>
        </div>

      <!-- Keep screen on -->
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

      <!-- GPS Accuracy Filter -->
      <div class="toggle-row">
        <div>
          <div class="toggle-title">GPS Accuracy Filter</div>
          <div class="toggle-description">
            Ignore speed readings when GPS accuracy is poor (>20m)
          </div>
        </div>
        <button
          @click="gpsAccuracyFilter = !gpsAccuracyFilter"
          :class="['toggle-button', { active: gpsAccuracyFilter }]"
        >
          <div :class="['toggle-knob', { active: gpsAccuracyFilter }]" />
        </button>
      </div>
    </div>

    <!-- Gauge Skin Selection -->
    <div class="setting-card">
      <label class="setting-label">Gauge Style</label>
      <p class="skin-description">Choose your preferred speedometer style. More styles coming soon!</p>
      <div class="skin-options">
        <button
          @click="gaugeSkin = 'circular'"
          :class="['skin-option', { active: gaugeSkin === 'circular' }]"
        >
          <div class="skin-preview circular-preview">
            <svg viewBox="0 0 60 40" class="skin-preview-svg">
              <path d="M 8 20 A 25 25 0 0 1 52 20" fill="none" stroke="currentColor" stroke-width="4" stroke-linecap="round"/>
              <text x="30" y="30" text-anchor="middle" fill="currentColor" font-size="10" font-weight="bold">66</text>
            </svg>
          </div>
          <span class="skin-name">Circular</span>
          <span class="skin-badge default">Classic</span>
        </button>
        <button
          @click="gaugeSkin = 'horizontal'"
          :class="['skin-option', { active: gaugeSkin === 'horizontal' }]"
        >
          <div class="skin-preview horizontal-preview">
            <svg viewBox="0 0 60 40" class="skin-preview-svg">
              <g transform="skewX(-5)">
                <rect x="8" y="8" width="6" height="12" fill="currentColor" rx="1"/>
                <rect x="16" y="8" width="6" height="12" fill="currentColor" rx="1"/>
                <rect x="24" y="8" width="6" height="12" fill="currentColor" rx="1"/>
                <rect x="32" y="8" width="6" height="12" fill="currentColor" opacity="0.3" rx="1"/>
                <rect x="40" y="8" width="6" height="12" fill="currentColor" opacity="0.3" rx="1"/>
              </g>
              <text x="30" y="32" text-anchor="middle" fill="currentColor" font-size="10" font-weight="bold">66</text>
            </svg>
          </div>
          <span class="skin-name">Horizontal</span>
          <span class="skin-badge new">New</span>
        </button>
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
          <span class="bold">The app is free. No catch. Just ride. 🏍️💨</span>
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

const { theme: settingsTheme, unit, keepScreenOn, avoidTolls, showMinimap, mapStyle, showDetailsOnNavigation, voiceInstructions, showStatusBar, gaugeSkin, gpsAccuracyFilter, metalShader, isMetalShader } = useSettings()

const accentThemes = [
  { id: 'hiraya' as const, name: 'Hiraya', subtitle: 'Aspirations', color: '#00ff88' },
  { id: 'sinta' as const, name: 'Sinta', subtitle: 'Beloved', color: '#ff1744' },
  { id: 'dalisay' as const, name: 'Dalisay', subtitle: 'Pure', color: '#00ffd5' },
  { id: 'tanglaw' as const, name: 'Tanglaw', subtitle: 'Illuminate', color: '#ffd700' },
  { id: 'tadhana' as const, name: 'Tadhana', subtitle: 'Destiny', color: '#7c3aed' },
  { id: 'bighani' as const, name: 'Bighani', subtitle: 'Enchantment', color: '#bf00ff' },
  { id: 'marilag' as const, name: 'Marilag', subtitle: 'Magnificent', color: '#e0f0ff' },
  { id: 'gabay' as const, name: 'Gabay', subtitle: 'Guidance', color: '#4db6ac' },
  { id: 'hiwaga' as const, name: 'Hiwaga', subtitle: 'Mystery', color: '#448aff' },
  { id: 'malaya' as const, name: 'Malaya', subtitle: 'Freedom', color: '#ff6d00' },
  { id: 'panalangin' as const, name: 'Panalangin', subtitle: 'Prayer', color: '#fbbf24' },
  { id: 'pag-asa' as const, name: 'Pag-asa', subtitle: 'Hope', color: '#f472b6' },
]

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

  }
}
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
  navigator.clipboard.writeText(`${shareText}\n${appUrl}`).then(() => {
    alert('Share text copied! Paste it in your Instagram post.')
    window.open('https://www.instagram.com/', '_blank')
  }).catch(() => {
    alert('Please share manually on Instagram')
  })
}

const openFeedbackForm = () => {
  window.open('https://docs.google.com/forms/d/e/1FAIpQLSfNBAYi-nCqQOsg2e574fyrr8EH7Tdkp8n60ckY4XfanKjB6Q/viewform', '_blank')
}
</script>

<style scoped>

@media (orientation: portrait) {
  .toggle-button {
    width: clamp(5rem, 8vw, 8rem);
  }
  .toggle-knob.active {
    left: 30%;
  }
}


@media (orientation: landscape) {
  .toggle-button {
    width: clamp(4rem, 8vw, 8rem);
  }

  .toggle-knob.active {
    left: 20%;
  }
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
  font-weight: 700;
  transition: color 0.3s ease;
  color: var(--chrome-highlight, #cfd8dc);
  text-shadow: 0 1px 0 rgba(0, 0, 0, 0.5);
  letter-spacing: 0.04em;
}

/* Beveled metal card panel */
.setting-card {
  background: linear-gradient(145deg, var(--metal-mid, #252b33), var(--metal-dark, #12161a));
  backdrop-filter: blur(8px);
  border-radius: 0.5rem;
  border: 1px solid rgba(90, 101, 119, 0.3);
  padding: 1rem;
  transition: background 0.3s ease, border-color 0.3s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.5), 0 1px 2px rgba(0, 0, 0, 0.3), inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

/* Laser-etched section label */
.setting-label {
  font-size: 0.875rem;
  color: var(--metal-shine, rgba(136, 153, 170, 1));
  text-transform: uppercase;
  letter-spacing: 0.1em;
  margin-bottom: 0.75rem;
  display: block;
  transition: color 0.3s ease;
  text-shadow: 0 1px 0 rgba(0, 0, 0, 0.5);
  font-family: 'SF Mono', 'Menlo', 'Monaco', 'Consolas', monospace;
  font-weight: 600;
}

/* Light Theme Settings - same metal look */
.settings-container[data-theme="light"] .settings-title {
  color: var(--chrome-highlight, #cfd8dc);
}

.settings-container[data-theme="light"] .setting-card {
  background: linear-gradient(145deg, var(--metal-mid, #252b33), var(--metal-base, #1a1f25));
  border: 1px solid rgba(90, 101, 119, 0.4);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.5), inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

.settings-container[data-theme="light"] .setting-label {
  color: var(--aluminum-light, #718096);
}

.settings-container[data-theme="light"] .unit-button,
.settings-container[data-theme="light"] .theme-button {
  background: var(--metal-mid, #252b33);
  color: var(--aluminum-shine, #a0aec0);
  border: 1px solid rgba(90, 101, 119, 0.4);
}

.settings-container[data-theme="light"] .unit-button:hover,
.settings-container[data-theme="light"] .theme-button:hover {
  background: var(--metal-light, #3a424d);
}

.settings-container[data-theme="light"] .unit-button.active,
.settings-container[data-theme="light"] .theme-button.active {
  background: linear-gradient(135deg, var(--accent-green-dim, #00cc6a), var(--accent-green, #00ff88));
  color: var(--metal-darkest, #080a0c);
  border: 1px solid var(--accent-green, #00ff88);
  box-shadow: 0 0 12px var(--glow-green);
  font-weight: 700;
}

.settings-container[data-theme="light"] .fuel-input {
  background: var(--metal-dark, #12161a);
  color: var(--chrome-highlight, #cfd8dc);
  border: 1px solid rgba(90, 101, 119, 0.4);
}

.settings-container[data-theme="light"] .fuel-input:focus {
  border-color: var(--accent-green, #00ff88);
  box-shadow: 0 0 6px color-mix(in srgb, var(--accent-green) 30%, transparent);
}

.settings-container[data-theme="light"] .toggle-title {
  color: var(--chrome-highlight, #cfd8dc);
}

.settings-container[data-theme="light"] .toggle-description {
  color: var(--aluminum-light, #718096);
}

.settings-container[data-theme="light"] .toggle-button {
  background: var(--metal-light, #3a424d);
}

.settings-container[data-theme="light"] .toggle-button.active {
  background: var(--accent-green, #00ff88);
}

.settings-container[data-theme="light"] .about-title {
  color: var(--chrome-highlight, #cfd8dc);
}

.settings-container[data-theme="light"] .about-content {
  color: var(--aluminum-light, #718096);
}

.settings-container[data-theme="light"] .bold {
  color: var(--chrome-highlight, #cfd8dc);
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

/* Machined metal selector buttons */
.unit-button,
.theme-button {
  padding: var(--space-sm, 0.75rem);
  border-radius: var(--radius-md, 0.5rem);
  font-weight: 600;
  transition: all 0.2s;
  background: linear-gradient(145deg, var(--metal-light, #3a424d), var(--metal-mid, #252b33));
  color: var(--metal-shine, rgba(136, 153, 170, 1));
  border: 1px solid rgba(90, 101, 119, 0.25);
  cursor: pointer;
  min-height: var(--touch-target-min, 44px);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04), 0 2px 4px rgba(0, 0, 0, 0.3);
  text-shadow: 0 1px 0 rgba(0, 0, 0, 0.4);
  letter-spacing: 0.03em;
}

.unit-button:hover,
.theme-button:hover {
  background: linear-gradient(145deg, var(--metal-highlight, #5a6577), var(--metal-light, #3a424d));
}

/* Active = LED glow through machined slot */
.unit-button.active,
.theme-button.active {
  background: linear-gradient(135deg, var(--accent-green-dim, #00cc6a), var(--accent-green, #00ff88));
  color: var(--metal-darkest, #080a0c);
  border: 1px solid var(--accent-green, #00ff88);
  box-shadow: 0 0 10px color-mix(in srgb, var(--accent-green) 35%, transparent), inset 0 1px 0 rgba(255, 255, 255, 0.15);
  text-shadow: none;
  font-weight: 700;
}

.fuel-input {
  width: 100%;
  background: var(--metal-dark, #12161a);
  color: var(--chrome-highlight, #cfd8dc);
  padding: 0.75rem 1rem;
  border-radius: 0.5rem;
  border: 1px solid rgba(90, 101, 119, 0.3);
  outline: none;
  box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.3);
}

.fuel-input:focus {
  border-color: var(--accent-green, #00ff88);
  box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.3), 0 0 6px color-mix(in srgb, var(--accent-green) 20%, transparent);
}

.toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-top: 1rem;
}

.toggle-row > div:first-child {
  flex: 1;
  min-width: 0;
  max-width: calc(100% - 4.5rem);
}

.toggle-title {
  color: var(--chrome-highlight, #cfd8dc);
  font-weight: 600;
  margin-bottom: 0.25rem;
  font-size: clamp(0.875rem, 2.5vw, 1rem);
  text-shadow: 0 1px 0 rgba(0, 0, 0, 0.4);
}

.toggle-description {
  font-size: clamp(0.75rem, 2vw, 0.875rem);
  color: var(--metal-shine, rgba(136, 153, 170, 1));
  line-height: 1.4;
  word-wrap: break-word;
  overflow-wrap: break-word;
  hyphens: auto;
}

/* Recessed toggle track */
.toggle-button {
  position: relative;
  height: clamp(1.75rem, 5vw, 2rem);
  border-radius: var(--radius-full, 9999px);
  transition: background-color 0.2s, box-shadow 0.2s;
  background: var(--metal-dark, #12161a);
  border: 1px solid rgba(90, 101, 119, 0.25);
  cursor: pointer;
  flex-shrink: 0;
  min-width: var(--touch-target-min, 44px);
  min-height: var(--touch-target-min, 44px);
  display: flex;
  align-items: center;
  box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.4);
}

.toggle-button.active {
  background: var(--accent-green, #00ff88);
  border-color: var(--accent-green, #00ff88);
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.2), 0 0 8px color-mix(in srgb, var(--accent-green) 30%, transparent);
}

/* Chrome knob */
.toggle-knob {
  position: absolute;
  left: 0.25rem;
  width: 1.5rem;
  height: 1.5rem;
  background: linear-gradient(145deg, var(--chrome-highlight, #cfd8dc), var(--chrome-light, #90a4ae));
  border-radius: 50%;
  transition: transform 0.2s;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.4), inset 0 1px 0 rgba(255, 255, 255, 0.3);
}

.toggle-knob.active {
  transform: translateX(1.5rem);
  background: linear-gradient(145deg, #ffffff, var(--chrome-highlight, #cfd8dc));
}


.about-title {
  color: var(--chrome-highlight, #cfd8dc);
  font-weight: 600;
  margin-bottom: 0.75rem;
  text-shadow: 0 1px 0 rgba(0, 0, 0, 0.4);
}

.about-content {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  font-size: 0.875rem;
  color: var(--metal-shine, rgba(136, 153, 170, 1));
}

.data-sources-title {
  padding-top: 0.5rem;
}

.bold {
  color: var(--chrome-highlight, #cfd8dc);
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
  background: linear-gradient(135deg, color-mix(in srgb, var(--accent-green) 8%, transparent), color-mix(in srgb, var(--accent-green-dim) 8%, transparent));
  border: 1px solid color-mix(in srgb, var(--accent-green) 25%, transparent);
  border-radius: 0.5rem;
  text-align: center;
  margin: 1rem 0;
  box-shadow: inset 0 0 12px color-mix(in srgb, var(--accent-green) 5%, transparent);
}

.settings-container[data-theme="light"] .free-badge {
  background: linear-gradient(135deg, color-mix(in srgb, var(--accent-green) 6%, transparent), color-mix(in srgb, var(--accent-green-dim) 6%, transparent));
  border-color: color-mix(in srgb, var(--accent-green) 20%, transparent);
}

.donation-section {
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid rgba(90, 101, 119, 0.25);
}

.settings-container[data-theme="light"] .donation-section {
  border-top-color: rgba(90, 101, 119, 0.3);
}

.donation-title {
  font-size: 0.875rem;
  color: var(--metal-shine, rgba(136, 153, 170, 1));
  margin-bottom: 0.5rem;
  font-weight: 500;
}

.settings-container[data-theme="light"] .donation-title {
  color: var(--aluminum-light, #718096);
}

.donation-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background: var(--metal-dark, #12161a);
  padding: 0.5rem;
  border-radius: 0.375rem;
  border: 1px solid rgba(90, 101, 119, 0.25);
  box-shadow: inset 0 1px 3px rgba(0, 0, 0, 0.3);
}

.settings-container[data-theme="light"] .donation-row {
  background: var(--metal-dark, #12161a);
  border-color: rgba(90, 101, 119, 0.3);
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
  color: var(--metal-shine, #8899aa);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.settings-container[data-theme="light"] .donation-text {
  color: var(--aluminum-light, #718096);
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
  background: color-mix(in srgb, var(--accent-green) 20%, transparent);
}

.icon-small {
  width: 1rem;
  height: 1rem;
  color: var(--metal-shine, #8899aa);
}

.settings-container[data-theme="light"] .icon-small {
  color: var(--aluminum-light, #718096);
}

.icon-small.success {
  color: var(--accent-green, #00ff88);
  filter: drop-shadow(0 0 3px var(--glow-green));
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
  background: linear-gradient(135deg, var(--metal-light, #3a424d), var(--metal-mid, #252b33));
  color: var(--accent-amber, #ffb800);
  font-size: 0.75rem;
  font-weight: 700;
  border-radius: 9999px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  width: fit-content;
  border: 1px solid rgba(255, 184, 0, 0.3);
  box-shadow: 0 0 6px rgba(255, 184, 0, 0.15);
  font-family: 'SF Mono', 'Menlo', 'Monaco', 'Consolas', monospace;
}

.coming-soon-description {
  font-size: 0.875rem;
  color: var(--metal-shine, rgba(136, 153, 170, 1));
  margin: 0;
}

.settings-container[data-theme="light"] .coming-soon-description {
  color: var(--aluminum-light, #718096);
}

/* Share Section */
.share-section {
  margin-top: 1.5rem;
  padding-top: 1.5rem;
  border-top: 1px solid rgba(90, 101, 119, 0.25);
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
  gap: var(--space-sm, 0.5rem);
  padding: var(--space-sm, 0.75rem) var(--space-md, 1.25rem);
  border: none;
  border-radius: var(--radius-md, 0.5rem);
  font-size: var(--text-sm, 0.875rem);
  font-weight: 600;
  color: white;
  cursor: pointer;
  transition: all 0.2s ease;
  flex: 1;
  min-width: 100px;
  justify-content: center;
  /* Ensure touch target minimum size */
  min-height: var(--touch-target-min, 44px);
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
  border-top-color: rgba(90, 101, 119, 0.25);
}

/* Feedback Section - Machined CTA button */
.feedback-button {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  padding: 1rem 1.5rem;
  background: linear-gradient(145deg, var(--metal-light, #3a424d), var(--metal-mid, #252b33));
  color: var(--chrome-highlight, #cfd8dc);
  border: 1px solid rgba(90, 101, 119, 0.3);
  border-radius: 0.5rem;
  font-size: 0.9375rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  margin-bottom: 0.5rem;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.05), 0 2px 6px rgba(0, 0, 0, 0.3);
  text-shadow: 0 1px 0 rgba(0, 0, 0, 0.4);
}

.feedback-button:hover {
  background: linear-gradient(145deg, var(--metal-highlight, #5a6577), var(--metal-light, #3a424d));
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4), inset 0 1px 0 rgba(255, 255, 255, 0.06);
}

.feedback-button:active {
  transform: translateY(0);
  box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.4);
}

.feedback-icon {
  width: 1.25rem;
  height: 1.25rem;
  flex-shrink: 0;
}

.feedback-description {
  font-size: 0.8125rem;
  color: var(--metal-shine, rgba(136, 153, 170, 1));
  text-align: center;
  margin: 0;
}

.settings-container[data-theme="light"] .feedback-description {
  color: var(--aluminum-light, #718096);
}

/* Accent Theme Grid */
.accent-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.625rem;
}

.accent-option {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.375rem;
  padding: 0.75rem 0.5rem;
  background: linear-gradient(145deg, var(--metal-light, #3a424d), var(--metal-mid, #252b33));
  border: 2px solid rgba(90, 101, 119, 0.2);
  border-radius: 0.625rem;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04), 0 2px 6px rgba(0, 0, 0, 0.3);
  min-height: auto;
}

.accent-option:hover {
  background: linear-gradient(145deg, var(--metal-highlight, #5a6577), var(--metal-light, #3a424d));
  transform: translateY(-1px);
}

.accent-option.active {
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.06), var(--metal-mid, #252b33));
}

.accent-color-dot {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  flex-shrink: 0;
}

.accent-name {
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--foreground, #e0e0e0);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  line-height: 1;
}

.accent-subtitle {
  font-size: 0.625rem;
  color: var(--metal-shine, rgba(136, 153, 170, 0.9));
  line-height: 1;
  white-space: nowrap;
}

.settings-container[data-theme="light"] .accent-option {
  background: linear-gradient(145deg, #e2e8f0, #f1f5f9);
  border-color: rgba(100, 116, 139, 0.2);
}

.settings-container[data-theme="light"] .accent-option:hover {
  background: linear-gradient(145deg, #cbd5e1, #e2e8f0);
}

.settings-container[data-theme="light"] .accent-option.active {
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.9), #e2e8f0);
}

.settings-container[data-theme="light"] .accent-name {
  color: #1e293b;
}

.settings-container[data-theme="light"] .accent-subtitle {
  color: #64748b;
}

.settings-container[data-shader="original"] .accent-option {
  background: rgba(255, 255, 255, 0.06);
  border-color: rgba(255, 255, 255, 0.1);
  box-shadow: none;
}

.settings-container[data-shader="original"] .accent-option:hover {
  background: rgba(255, 255, 255, 0.1);
}

.settings-container[data-shader="original"] .accent-option.active {
  background: rgba(255, 255, 255, 0.08);
}

.settings-container[data-shader="original"][data-theme="light"] .accent-option {
  background: rgba(0, 0, 0, 0.04);
  border-color: rgba(0, 0, 0, 0.1);
}

.settings-container[data-shader="original"][data-theme="light"] .accent-option:hover {
  background: rgba(0, 0, 0, 0.06);
}

.settings-container[data-shader="original"][data-theme="light"] .accent-option.active {
  background: rgba(0, 0, 0, 0.08);
}

/* Gauge Skin Selection */
.skin-description {
  font-size: 0.875rem;
  color: var(--metal-shine, rgba(136, 153, 170, 1));
  margin-bottom: 1rem;
}

.settings-container[data-theme="light"] .skin-description {
  color: var(--aluminum-light, #718096);
}

.skin-options {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
}

.skin-option {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem;
  background: linear-gradient(145deg, var(--metal-light, #3a424d), var(--metal-mid, #252b33));
  border: 2px solid rgba(90, 101, 119, 0.2);
  border-radius: 0.75rem;
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04), 0 2px 6px rgba(0, 0, 0, 0.3);
}

.skin-option:hover {
  background: linear-gradient(145deg, var(--metal-highlight, #5a6577), var(--metal-light, #3a424d));
  transform: translateY(-2px);
}

.skin-option.active {
  border-color: var(--accent-green, #00ff88);
  background: linear-gradient(145deg, color-mix(in srgb, var(--accent-green) 8%, transparent), var(--metal-mid, #252b33));
  box-shadow: 0 0 10px color-mix(in srgb, var(--accent-green) 15%, transparent), inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

.settings-container[data-theme="light"] .skin-option {
  background: linear-gradient(145deg, var(--metal-light, #3a424d), var(--metal-mid, #252b33));
  border-color: rgba(90, 101, 119, 0.2);
}

.settings-container[data-theme="light"] .skin-option:hover {
  background: linear-gradient(145deg, var(--metal-highlight, #5a6577), var(--metal-light, #3a424d));
}

.settings-container[data-theme="light"] .skin-option.active {
  border-color: var(--accent-green, #00ff88);
  background: linear-gradient(145deg, color-mix(in srgb, var(--accent-green) 8%, transparent), var(--metal-mid, #252b33));
}

.skin-preview {
  width: 80px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--metal-dark, #12161a);
  border-radius: 0.5rem;
  overflow: hidden;
  border: 1px solid rgba(90, 101, 119, 0.2);
  box-shadow: inset 0 1px 3px rgba(0, 0, 0, 0.3);
}

.settings-container[data-theme="light"] .skin-preview {
  background: var(--metal-dark, #12161a);
}

.skin-preview-svg {
  width: 100%;
  height: 100%;
  color: var(--metal-shine, rgba(136, 153, 170, 0.8));
}

.skin-option.active .skin-preview-svg {
  color: var(--accent-green, #00ff88);
  filter: drop-shadow(0 0 4px var(--glow-green));
}

.settings-container[data-theme="light"] .skin-option.active .skin-preview-svg {
  color: var(--accent-green, #00ff88);
}

.circular-preview .skin-preview-svg {
  padding: 4px;
}

.horizontal-preview .skin-preview-svg {
  padding: 2px;
}

.skin-name {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--metal-shine, rgba(136, 153, 170, 1));
  transition: color 0.2s;
  text-shadow: 0 1px 0 rgba(0, 0, 0, 0.4);
}

.skin-option.active .skin-name {
  color: var(--accent-green, #00ff88);
}

.settings-container[data-theme="light"] .skin-name {
  color: var(--aluminum-light, #718096);
}

.settings-container[data-theme="light"] .skin-option.active .skin-name {
  color: var(--accent-green-dim);
}

.skin-badge {
  position: absolute;
  top: 0.5rem;
  right: 0.5rem;
  padding: 0.125rem 0.5rem;
  font-size: 0.625rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  border-radius: 9999px;
  font-family: 'SF Mono', 'Menlo', 'Monaco', 'Consolas', monospace;
}

.skin-badge.default {
  background: var(--metal-dark, #12161a);
  color: var(--metal-shine, #8899aa);
  border: 1px solid rgba(90, 101, 119, 0.25);
}

.skin-badge.new {
  background: linear-gradient(135deg, var(--accent-amber-dim, #cc9300), var(--accent-amber, #ffb800));
  color: var(--metal-darkest, #080a0c);
  border: 1px solid var(--accent-amber, #ffb800);
  box-shadow: 0 0 6px rgba(255, 184, 0, 0.3);
}

@media (max-width: 400px) {
  .skin-options {
    grid-template-columns: 1fr;
  }
}

/* ============ ORIGINAL STYLE (Metal Shader OFF) ============ */
.settings-container[data-shader="original"] .settings-title {
  color: white;
  text-shadow: none;
}

.settings-container[data-shader="original"] .setting-card {
  background: rgba(17, 24, 39, 0.6);
  border: 1px solid rgba(128, 128, 128, 0.2);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

.settings-container[data-shader="original"] .setting-label {
  color: rgba(156, 163, 175, 0.9);
  text-shadow: none;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', sans-serif;
}

.settings-container[data-shader="original"] .unit-button,
.settings-container[data-shader="original"] .theme-button {
  background: rgba(55, 65, 81, 0.6);
  color: rgba(156, 163, 175, 1);
  border: 1px solid rgba(128, 128, 128, 0.2);
  box-shadow: none;
  text-shadow: none;
}

.settings-container[data-shader="original"] .unit-button:hover,
.settings-container[data-shader="original"] .theme-button:hover {
  background: rgba(75, 85, 99, 0.8);
}

.settings-container[data-shader="original"] .unit-button.active,
.settings-container[data-shader="original"] .theme-button.active {
  background: linear-gradient(135deg, var(--accent-green-dim), var(--accent-green));
  color: white;
  border: 1px solid var(--accent-green);
  box-shadow: 0 0 8px var(--glow-green);
  text-shadow: none;
}

.settings-container[data-shader="original"] .toggle-title {
  color: white;
  text-shadow: none;
}

.settings-container[data-shader="original"] .toggle-description {
  color: rgba(156, 163, 175, 0.9);
}

.settings-container[data-shader="original"] .toggle-button {
  background: rgba(55, 65, 81, 0.6);
  border: 1px solid rgba(128, 128, 128, 0.2);
  box-shadow: none;
}

.settings-container[data-shader="original"] .toggle-button.active {
  background: var(--accent-green);
  border-color: var(--accent-green);
  box-shadow: none;
}

.settings-container[data-shader="original"] .toggle-knob {
  background: white;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.3);
}

.settings-container[data-shader="original"] .toggle-knob.active {
  background: white;
}

.settings-container[data-shader="original"] .about-title {
  color: white;
  text-shadow: none;
}

.settings-container[data-shader="original"] .about-content {
  color: rgba(156, 163, 175, 0.9);
}

.settings-container[data-shader="original"] .bold {
  color: white;
}

.settings-container[data-shader="original"] .skin-option {
  background: rgba(55, 65, 81, 0.4);
  border: 2px solid rgba(128, 128, 128, 0.2);
  box-shadow: none;
}

.settings-container[data-shader="original"] .skin-option:hover {
  background: rgba(75, 85, 99, 0.6);
}

.settings-container[data-shader="original"] .skin-option.active {
  border-color: var(--accent-green);
  background: color-mix(in srgb, var(--accent-green) 10%, transparent);
  box-shadow: 0 0 8px color-mix(in srgb, var(--accent-green) 20%, transparent);
}

.settings-container[data-shader="original"] .skin-preview {
  background: rgba(17, 24, 39, 0.8);
  border: 1px solid rgba(128, 128, 128, 0.2);
  box-shadow: none;
}

.settings-container[data-shader="original"] .skin-option.active .skin-preview-svg {
  color: var(--accent-green);
  filter: none;
}

.settings-container[data-shader="original"] .skin-name {
  color: rgba(156, 163, 175, 1);
  text-shadow: none;
}

.settings-container[data-shader="original"] .skin-option.active .skin-name {
  color: var(--accent-green);
}

.settings-container[data-shader="original"] .skin-badge.default {
  background: rgba(55, 65, 81, 0.6);
  color: rgba(156, 163, 175, 0.8);
  border: 1px solid rgba(128, 128, 128, 0.2);
}

.settings-container[data-shader="original"] .skin-badge.new {
  background: linear-gradient(135deg, var(--accent-green-dim), var(--accent-green));
  color: white;
  border: 1px solid var(--accent-green);
  box-shadow: none;
}

.settings-container[data-shader="original"] .skin-description {
  color: rgba(156, 163, 175, 0.9);
}

.settings-container[data-shader="original"] .feedback-button {
  background: rgba(55, 65, 81, 0.6);
  color: white;
  border: 1px solid rgba(128, 128, 128, 0.2);
  box-shadow: none;
  text-shadow: none;
}

.settings-container[data-shader="original"] .feedback-button:hover {
  background: rgba(75, 85, 99, 0.8);
}

.settings-container[data-shader="original"] .feedback-description {
  color: rgba(156, 163, 175, 0.9);
}

.settings-container[data-shader="original"] .donation-row {
  background: rgba(17, 24, 39, 0.6);
  border: 1px solid rgba(128, 128, 128, 0.2);
  box-shadow: none;
}

.settings-container[data-shader="original"] .donation-title {
  color: rgba(156, 163, 175, 0.9);
}

.settings-container[data-shader="original"] .donation-text {
  color: rgba(156, 163, 175, 0.8);
}

.settings-container[data-shader="original"] .free-badge {
  background: color-mix(in srgb, var(--accent-green) 8%, transparent);
  border: 1px solid color-mix(in srgb, var(--accent-green) 25%, transparent);
  box-shadow: none;
}

.settings-container[data-shader="original"] .coming-soon-badge {
  background: rgba(55, 65, 81, 0.6);
  color: rgb(250, 204, 21);
  border: 1px solid rgba(250, 204, 21, 0.3);
  box-shadow: none;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', sans-serif;
}

/* Light theme original overrides */
.settings-container[data-shader="original"][data-theme="light"] .settings-title {
  color: #1e293b;
}

.settings-container[data-shader="original"][data-theme="light"] .setting-card {
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(0, 0, 0, 0.1);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.settings-container[data-shader="original"][data-theme="light"] .setting-label {
  color: #64748b;
}

.settings-container[data-shader="original"][data-theme="light"] .toggle-title {
  color: #1e293b;
}

.settings-container[data-shader="original"][data-theme="light"] .toggle-description {
  color: #64748b;
}

.settings-container[data-shader="original"][data-theme="light"] .unit-button,
.settings-container[data-shader="original"][data-theme="light"] .theme-button {
  background: rgba(241, 245, 249, 0.9);
  color: #64748b;
  border: 1px solid rgba(0, 0, 0, 0.1);
}

.settings-container[data-shader="original"][data-theme="light"] .unit-button.active,
.settings-container[data-shader="original"][data-theme="light"] .theme-button.active {
  background: linear-gradient(135deg, var(--accent-green-dim), var(--accent-green));
  color: white;
  border: 1px solid var(--accent-green);
}

.settings-container[data-shader="original"][data-theme="light"] .about-title {
  color: #1e293b;
}

.settings-container[data-shader="original"][data-theme="light"] .about-content {
  color: #64748b;
}

.settings-container[data-shader="original"][data-theme="light"] .bold {
  color: #1e293b;
}
</style>
