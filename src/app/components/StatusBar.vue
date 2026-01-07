<template>
  <div class="status-bar">
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
        <Battery :class="['icon', batteryLevel > 20 ? 'battery-icon' : 'battery-low']" />
        <span class="label">{{ batteryLevel }}%</span>
      </div>
      <Bluetooth :class="['icon', 'bluetooth-icon']" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { Wifi, Bluetooth, Navigation2, Battery, Signal } from 'lucide-vue-next'
import { Device } from '@capacitor/device'
import { Network } from '@capacitor/network'

interface Props {
  isGpsActive: boolean
}

defineProps<Props>()

const batteryLevel = ref(80)
const networkConnected = ref(false)
const wifiConnected = ref(false)

let networkListener: any = null

const updateBatteryInfo = async () => {
  try {
    const info = await Device.getBatteryInfo()
    batteryLevel.value = Math.round((info.batteryLevel || 0) * 100)
  } catch (error) {
    console.log('Battery info not available:', error)
  }
}

const updateNetworkStatus = async () => {
  try {
    const status = await Network.getStatus()
    networkConnected.value = status.connected
    wifiConnected.value = status.connectionType === 'wifi'
  } catch (error) {
    console.log('Network status not available:', error)
  }
}

const currentTime = ref(new Date().toLocaleTimeString('en-US', { 
  hour: '2-digit', 
  minute: '2-digit',
  hour12: true 
}))

onMounted(async () => {
  // Initial updates
  await updateBatteryInfo()
  await updateNetworkStatus()

  // Update time every second
  const timeInterval = setInterval(() => {
    currentTime.value = new Date().toLocaleTimeString('en-US', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: true
    })
  }, 1000)

  // Update battery every 30 seconds
  const batteryInterval = setInterval(updateBatteryInfo, 30000)

  // Listen for network changes
  try {
    networkListener = await Network.addListener('networkStatusChange', (status) => {
      networkConnected.value = status.connected
      wifiConnected.value = status.connectionType === 'wifi'
    })
  } catch (error) {
    console.log('Network listener not available:', error)
  }

  onUnmounted(() => {
    clearInterval(timeInterval)
    clearInterval(batteryInterval)
    if (networkListener) {
      networkListener.remove()
    }
  })
})
</script>

<style scoped>
.status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.5rem 1rem;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid rgba(128, 128, 128, 0.5);
}

.left, .right {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.right {
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
}

.time-label {
  font-size: 0.875rem;
  font-weight: 600;
  color: white;
  margin-right: 0.5rem;
  padding-left: 12px;
}
</style>
