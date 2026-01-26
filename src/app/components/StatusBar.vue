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

// Select battery icon based on level
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
  // Initial updates
  await updateBatteryInfo()
  await updateNetworkStatus()
  await checkBluetoothStatus()

  // Update time every second
  timeInterval = window.setInterval(() => {
    currentTime.value = new Date().toLocaleTimeString('en-US', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: true
    })
  }, 1000)

  // Update battery every 30 seconds
  batteryInterval = window.setInterval(updateBatteryInfo, 30000)

  // Update Bluetooth status every 10 seconds
  bluetoothInterval = window.setInterval(checkBluetoothStatus, 10000)

  // Listen for network changes
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
/* Dark Theme (Default) */
.status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.5rem 1rem;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid rgba(128, 128, 128, 0.5);
  transition: background 0.3s ease, border-color 0.3s ease;
}

/* Light Theme */
.status-bar[data-theme="light"] {
  background: rgba(255, 255, 255, 0.85);
  border-bottom: 1px solid rgba(148, 163, 184, 0.4);
}

.left, .right {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.left {
  padding-left: 0.75rem;
}

.right {
  padding-right: 0.75rem;;
  gap: 0.75rem;
}

.icon {
  width: 1rem;
  height: 1rem;
  color: rgba(156, 163, 175, 1);
  transition: color 0.3s;
}

.icon.connected {
  color: rgba(74, 222, 128, 1);
}

.gps-icon {
  color: rgba(74, 222, 128, 1);
}

.battery {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.battery-icon {
  color: rgba(74, 222, 128, 1);
}

.battery-low {
  color: rgba(239, 68, 68, 1);
}

.bluetooth-icon {
  color: rgba(96, 165, 250, 1);
}

.label {
  font-size: 0.75rem;
  color: rgba(156, 163, 175, 1);
  transition: color 0.3s ease;
}

.time-label {
  font-size: 0.875rem;
  font-weight: 600;
  color: white;
  margin-right: 0.5rem;
  padding-left: 12px;
  transition: color 0.3s ease;
}

/* Light Theme Status Bar */
.status-bar[data-theme="light"] .icon {
  color: rgb(100, 116, 139);
}

.status-bar[data-theme="light"] .icon.connected {
  color: rgb(34, 197, 94);
}

.status-bar[data-theme="light"] .gps-icon {
  color: rgb(34, 197, 94);
}

.status-bar[data-theme="light"] .battery-icon {
  color: rgb(34, 197, 94);
}

.status-bar[data-theme="light"] .battery-low {
  color: rgb(239, 68, 68);
}

.status-bar[data-theme="light"] .bluetooth-icon {
  color: rgb(59, 130, 246);
}

.status-bar[data-theme="light"] .label {
  color: rgb(100, 116, 139);
}

.status-bar[data-theme="light"] .time-label {
  color: rgb(15, 23, 42);
}
</style>
