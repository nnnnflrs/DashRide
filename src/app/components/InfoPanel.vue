<template>
  <div class="info-panel">
    <InfoCard
      :icon="Fuel"
      iconColor="text-amber-400"
      :value="fuelConsumption.toFixed(1)"
      :unit="consumptionUnit"
      label="Fuel"
    />

    <InfoCard
      :icon="MapPin"
      iconColor="text-yellow-400"
      :value="Math.round(totalDistance).toString()"
      :unit="distanceUnit"
      label="Total"
    />

    <InfoCard
      :icon="Gauge"
      iconColor="text-green-400"
      label="Range"
      :value="Math.round(range).toString()"
      :unit="distanceUnit"
    />

    <InfoCard
      :icon="MapPin"
      iconColor="text-blue-400"
      label="Trip"
      :value="tripDistance.toFixed(1)"
      :unit="distanceUnit"
    />

    <InfoCard
      :icon="Timer"
      iconColor="text-orange-400"
      label="Time"
      :value="formattedTime"
      unit=""
    />

    <InfoCard
      :icon="weatherIcon"
      iconColor="text-yellow-300"
      label="Weather"
      :value="temperature.toString()"
      :unit="tempUnit"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Fuel, Gauge, MapPin, Timer, Sun, Cloud, CloudRain } from 'lucide-vue-next'
import InfoCard from './InfoCard.vue'

interface Props {
  fuelConsumption: number
  totalDistance: number
  range: number
  tripDistance: number
  tripTime: number
  temperature: number
  unit: 'mph' | 'kmh'
}

const props = defineProps<Props>()

const distanceUnit = computed(() => props.unit === 'mph' ? 'mi' : 'km')
const consumptionUnit = computed(() => props.unit === 'mph' ? 'mpg' : 'L/100km')
const tempUnit = '°C'

const hours = computed(() => Math.floor(props.tripTime / 3600))
const minutes = computed(() => Math.floor((props.tripTime % 3600) / 60))
const seconds = computed(() => props.tripTime % 60)

const formattedTime = computed(() => 
  `${hours.value.toString().padStart(2, '0')}:${minutes.value.toString().padStart(2, '0')}:${seconds.value.toString().padStart(2, '0')}`
)

const weatherIcon = computed(() => {
  if (props.temperature > 25) return Sun
  if (props.temperature > 15) return Cloud
  return CloudRain
})
</script>

<style scoped>
.info-panel {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0.5rem;
}
</style>
