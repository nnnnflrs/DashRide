<template>
  <div v-if="showStatusBar" class="status-bar" :data-theme="theme" :style="{ paddingTop: isNavigationBarVisible && navBarPosition === 'top' ? `${topInset}px` : '0px' }">
    <div class="left">
      <span class="time-label">{{ currentTime }}</span>
      <template v-if="isGpsActive">
        <Navigation2 class="icon gps-icon" />
        <span class="label">GPS</span>
      </template>
    </div>
    

    <div class="right">
      <Signal :class="['icon', networkConnected ? 'connected' : '']" />
      <Wifi :class="['icon', wifiConnected ? 'connected' : '']" />
      <div class="battery">
        <component :is="batteryIcon" :class="['icon', batteryLevel > 20 ? 'battery-icon' : 'battery-low']" />
        <span class="label">{{ batteryLevel }}%</span>
      </div>
      <Bluetooth v-if="bluetoothEnabled" :class="['icon', 'bluetooth-icon']" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { Wifi, Bluetooth, Navigation2, BatteryFull, BatteryMedium, BatteryLow, Signal } from 'lucide-vue-next'
import { Device } from '@capacitor/device'
import { Network } from '@capacitor/network'
import { BleClient } from '@capacitor-community/bluetooth-le'
import { useSettings } from '@/composables/useSettings'
import { useSafeArea } from '@/composables/useSafeArea'

const { showStatusBar } = useSettings()
const { topInset, navBarPosition, isNavigationBarVisible } = useSafeArea()

interface Props {
  isGpsActive: boolean
  theme?: 'light' | 'dark'
}

defineProps<Props>()

const batteryLevel = ref(80)
const networkConnected = ref(false)
const wifiConnected = ref(false)
const bluetoothEnabled = ref(false)

let networkListener: any = null

const batteryIcon = computed(() => {
  if (batteryLevel.value > 60) return BatteryFull
  if (batteryLevel.value > 20) return BatteryMedium
  return BatteryLow
})

const checkBluetoothStatus = async () => {
  try {
    await BleClient.initialize()
    const isEnabled = await BleClient.isEnabled()
    bluetoothEnabled.value = isEnabled
  } catch {
    bluetoothEnabled.value = false
  }
}

const updateBatteryInfo = async () => {
  try {
    const info = await Device.getBatteryInfo()
    batteryLevel.value = Math.round((info.batteryLevel || 0) * 100)
  } catch {
    // Battery info not available on web
  }
}

const updateNetworkStatus = async () => {
  try {
    const status = await Network.getStatus()
    networkConnected.value = status.connected
    wifiConnected.value = status.connectionType === 'wifi'
  } catch {
    // Network status not available on web
  }
}

const currentTime = ref(new Date().toLocaleTimeString('en-US', {
  hour: '2-digit',
  minute: '2-digit',
  hour12: true
}))

let timeInterval: number | null = null
let batteryInterval: number | null = null
let bluetoothInterval: number | null = null

onMounted(async () => {
  await updateBatteryInfo()
  await updateNetworkStatus()
  await checkBluetoothStatus()

  timeInterval = window.setInterval(() => {
    currentTime.value = new Date().toLocaleTimeString('en-US', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: true
    })
  }, 1000)

  batteryInterval = window.setInterval(updateBatteryInfo, 30000)
  bluetoothInterval = window.setInterval(checkBluetoothStatus, 10000)

  try {
    networkListener = await Network.addListener('networkStatusChange', (status) => {
      networkConnected.value = status.connected
      wifiConnected.value = status.connectionType === 'wifi'
    })
  } catch {
    // Network listener not available on web
  }
})

onUnmounted(() => {
  if (timeInterval) clearInterval(timeInterval)
  if (batteryInterval) clearInterval(batteryInterval)
  if (bluetoothInterval) clearInterval(bluetoothInterval)
  if (networkListener) {
    networkListener.remove()
  }
})
</script>

<style scoped>
/* Dark Theme (Default) - Brushed aluminum status bar */
.status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 1rem;
  background: linear-gradient(180deg,
    rgba(42, 48, 56, 0.95) 0%,
    rgba(26, 31, 37, 0.95) 100%);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid rgba(90, 101, 119, 0.3);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.4), inset 0 1px 0 rgba(255, 255, 255, 0.04);
  transition: background 0.3s ease, border-color 0.3s ease;
}

/* Brushed aluminum anisotropic streak */
.status-bar::before {
  content: '';
  position: absolute;
  inset: 0;
  background: repeating-linear-gradient(
    90deg,
    transparent,
    rgba(255, 255, 255, 0.008) 1px,
    transparent 2px
  );
  pointer-events: none;
}

/* Light Theme */
.status-bar[data-theme="light"] {
  background: linear-gradient(180deg,
    rgba(58, 66, 77, 0.95) 0%,
    rgba(37, 43, 51, 0.95) 100%);
  border-bottom: 1px solid rgba(90, 101, 119, 0.4);
}

.left, .right {
  display: flex;
  align-items: center;
  gap: var(--space-sm, 0.5rem);
}

.left {
  padding-left: var(--space-sm, 0.75rem);
}

.right {
  padding-right: var(--space-sm, 0.75rem);
  gap: var(--space-sm, 0.75rem);
}

.icon {
  width: var(--icon-sm, 1rem);
  height: var(--icon-sm, 1rem);
  color: var(--metal-shine, rgba(136, 153, 170, 1));
  transition: color 0.3s;
}

.icon.connected {
  color: var(--accent-green, #00ff88);
  filter: drop-shadow(0 0 3px var(--glow-green, rgba(0, 255, 136, 0.4)));
}

.gps-icon {
  color: var(--accent-green, #00ff88);
  filter: drop-shadow(0 0 3px var(--glow-green, rgba(0, 255, 136, 0.4)));
}

.battery {
  display: flex;
  align-items: center;
  gap: var(--space-xs, 0.25rem);
}

.battery-icon {
  color: var(--accent-green, #00ff88);
  filter: drop-shadow(0 0 3px var(--glow-green, rgba(0, 255, 136, 0.4)));
}

.battery-low {
  color: var(--accent-red, #ff1744);
  filter: drop-shadow(0 0 3px var(--glow-red, rgba(255, 23, 68, 0.4)));
}

.bluetooth-icon {
  color: var(--accent-blue, #448aff);
  filter: drop-shadow(0 0 3px var(--glow-blue, rgba(68, 138, 255, 0.3)));
}

/* Laser-etched text effect */
.label {
  font-size: var(--text-xs, 0.75rem);
  color: var(--metal-shine, rgba(136, 153, 170, 1));
  transition: color 0.3s ease;
  letter-spacing: 0.05em;
  text-shadow: 0 1px 0 rgba(0, 0, 0, 0.5);
}

.time-label {
  font-size: var(--text-sm, 0.875rem);
  font-weight: 700;
  color: var(--chrome-highlight, #cfd8dc);
  margin-right: var(--space-sm, 0.5rem);
  padding-left: var(--space-sm, 0.75rem);
  transition: color 0.3s ease;
  letter-spacing: 0.08em;
  text-shadow: 0 1px 0 rgba(0, 0, 0, 0.5), 0 0 6px color-mix(in srgb, var(--accent-green) 10%, transparent);
  font-family: 'SF Mono', 'Menlo', 'Monaco', 'Consolas', monospace;
}

/* Light Theme Status Bar */
.status-bar[data-theme="light"] .icon {
  color: var(--aluminum-light, #718096);
}

.status-bar[data-theme="light"] .icon.connected {
  color: var(--accent-green, #00ff88);
}

.status-bar[data-theme="light"] .gps-icon {
  color: var(--accent-green, #00ff88);
}

.status-bar[data-theme="light"] .battery-icon {
  color: var(--accent-green, #00ff88);
}

.status-bar[data-theme="light"] .battery-low {
  color: var(--accent-red, #ff1744);
}

.status-bar[data-theme="light"] .bluetooth-icon {
  color: var(--accent-blue, #448aff);
}

.status-bar[data-theme="light"] .label {
  color: var(--aluminum-light, #718096);
}

.status-bar[data-theme="light"] .time-label {
  color: var(--chrome-highlight, #cfd8dc);
}
</style>
