<template>
  <div class="rider-info">
    <div class="rider-name-row">
      <User class="user-icon" />
      <input
        type="text"
        :value="riderName"
        @input="$emit('update:riderName', ($event.target as HTMLInputElement).value)"
        class="name-input"
        placeholder="Rider Name"
        :disabled="isRiding"
      />
    </div>
    <div class="status-row">
      <MusicIcon class="music-icon" />
      <span :class="['status-text', { active: isRiding }]">
        {{ isRiding ? 'Ride On' : 'Ready to Ride' }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { User, Music as MusicIcon } from 'lucide-vue-next'

interface Props {
  riderName: string
  isRiding: boolean
}

defineProps<Props>()

defineEmits<{
  'update:riderName': [name: string]
}>()
</script>

<style scoped>
.rider-info {
  background: linear-gradient(to bottom right, rgba(17, 24, 39, 0.8), rgba(31, 41, 55, 0.8));
  backdrop-filter: blur(8px);
  border-radius: 0.5rem;
  border: 1px solid rgba(55, 65, 81, 0.5);
  padding: 0.75rem;
}

.rider-name-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.user-icon {
  width: 1rem;
  height: 1rem;
  color: rgb(96, 165, 250);
  flex-shrink: 0;
}

.name-input {
  flex: 1;
  background: transparent;
  font-size: 0.875rem;
  color: white;
  font-weight: 600;
  outline: none;
  border: none;
}

.name-input:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.status-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.75rem;
  color: rgba(156, 163, 175, 1);
}

.music-icon {
  width: 0.75rem;
  height: 0.75rem;
  color: rgb(74, 222, 128);
}

.status-text {
  color: rgba(156, 163, 175, 1);
}

.status-text.active {
  color: rgb(74, 222, 128);
}
</style>
