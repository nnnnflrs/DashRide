<template>
  <div class="info-card">
    <div class="card-header">
      <component :is="icon" :class="['card-icon', iconColor]" />
      <span class="card-label">{{ label }}</span>
    </div>
    <div class="card-value-row">
      <span class="card-value">{{ value }}</span>
      <span v-if="unit" class="card-unit">{{ unit }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'

interface Props {
  icon: Component
  iconColor: string
  label: string
  value: string
  unit: string
}

defineProps<Props>()
</script>

<style scoped>
.info-card {
  background: linear-gradient(to bottom right, rgba(17, 24, 39, 0.8), rgba(31, 41, 55, 0.8));
  backdrop-filter: blur(8px);
  border-radius: clamp(0.375rem, 1.5vw, 0.5rem);
  padding: clamp(0.5rem, 1.8vw, 0.75rem);
  border: 1px solid rgba(55, 65, 81, 0.5);
  transition: transform 0.2s;
}

.info-card:hover {
  transform: scale(1.05);
}

.card-header {
  display: flex;
  align-items: center;
  gap: clamp(0.375rem, 1.2vw, 0.5rem);
  margin-bottom: clamp(0.2rem, 0.8vw, 0.3rem);
}

.card-icon {
  width: clamp(0.875rem, 2.5vw, 1.125rem);
  height: clamp(0.875rem, 2.5vw, 1.125rem);
  flex-shrink: 0;
}

.card-icon.text-amber-400 {
  color: rgb(251, 191, 36);
}

.card-icon.text-yellow-400 {
  color: rgb(250, 204, 21);
}

.card-icon.text-green-400 {
  color: rgb(74, 222, 128);
}

.card-icon.text-blue-400 {
  color: rgb(96, 165, 250);
}

.card-icon.text-orange-400 {
  color: rgb(251, 146, 60);
}

.card-icon.text-yellow-300 {
  color: rgb(253, 224, 71);
}

.card-label {
  font-size: clamp(0.625rem, 1.8vw, 0.75rem);
  color: rgba(156, 163, 175, 1);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.card-value-row {
  display: flex;
  align-items: baseline;
  gap: clamp(0.2rem, 0.8vw, 0.3rem);
}

.card-value {
  font-size: clamp(0.9rem, 2.8vw, 1.25rem);
  font-weight: bold;
  color: white;
  font-variant-numeric: tabular-nums;
  transition: opacity 0.2s;
}

.card-unit {
  font-size: clamp(0.625rem, 1.8vw, 0.8rem);
  color: rgba(156, 163, 175, 1);
  font-weight: 600;
}
</style>
