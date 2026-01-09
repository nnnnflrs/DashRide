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

    <!-- Fuel Consumption Setting -->
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

    <!-- Map Settings Section -->
    <div class="setting-card">
      <label class="setting-label">Map Settings</label>
      
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

    <!-- About Section -->
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
          <li>Real-time weather from Open-Meteo API</li>
          <li>User input for fuel consumption</li>
        </ul>
        
        <!-- Buy Me a Coffee - Compact -->
        <div class="donation-section">
          <p class="donation-title">☕ Bili mo ako ng kape huhu</p>
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
const { theme: settingsTheme, unit, fuelConsumption, keepScreenOn, avoidTolls } = useSettings()

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
  } catch (err) {
    console.error('Failed to copy:', err)
  }
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
</style>
