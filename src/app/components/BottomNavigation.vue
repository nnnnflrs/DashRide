<template>
  <div
    class="bottom-nav"
    :data-theme="theme"
    :style="{
      paddingBottom: isNavigationBarVisible && navBarPosition === 'bottom' ? `${bottomInset}px` : '0px',
      paddingLeft: isNavigationBarVisible && navBarPosition === 'left' ? `${leftInset}px` : '0px',
      paddingRight: isNavigationBarVisible && navBarPosition === 'right' ? `${rightInset}px` : '0px'
    }"
  >
    <div class="nav-grid">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        @click="$emit('update:activeTab', tab.id)"
        :class="['nav-button', { active: activeTab === tab.id }]"
      >
        <div v-if="activeTab === tab.id" class="active-indicator" />
        <component :is="tab.icon" class="nav-icon" />
        <span class="nav-label">{{ tab.label }}</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Navigation, Music, Gauge, Settings } from 'lucide-vue-next'
import { useSafeArea } from '../../composables/useSafeArea'

interface Props {
  activeTab: 'nav' | 'music' | 'riding' | 'settings'
  theme?: 'light' | 'dark'
}

defineProps<Props>()

defineEmits<{
  'update:activeTab': [tab: 'nav' | 'music' | 'riding' | 'settings']
}>()

const { bottomInset, leftInset, rightInset, navBarPosition, isNavigationBarVisible } = useSafeArea()

const tabs = [
  { id: 'nav' as const, icon: Navigation, label: 'NAV' },
  { id: 'music' as const, icon: Music, label: 'MUSIC' },
  { id: 'riding' as const, icon: Gauge, label: 'RIDING' },
  { id: 'settings' as const, icon: Settings, label: 'SETTINGS' },
]
</script>

<style scoped>
/* Dark Theme (Default) */
.bottom-nav {
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(12px);
  border-top: 1px solid rgba(128, 128, 128, 0.5);
  transition: background 0.3s ease, border-color 0.3s ease;
}

/* Light Theme */
.bottom-nav[data-theme="light"] {
  background: rgba(255, 255, 255, 0.85);
  border-top: 1px solid rgba(148, 163, 184, 0.4);
}

.nav-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0.25rem;
  padding: 0.5rem;
}

.nav-button {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.25rem;
  padding: 0.5rem 0.75rem;
  border-radius: 0.5rem;
  transition: all 0.2s;
  background: transparent;
  border: none;
  cursor: pointer;
  color: rgba(156, 163, 175, 1);
}

.nav-button:hover {
  color: rgba(209, 213, 219, 1);
}

.nav-button:active {
  background: rgba(31, 41, 55, 0.5);
}

.nav-button.active {
  background: rgba(37, 99, 235, 0.2);
  color: rgb(96, 165, 250);
}

/* Light Theme Navigation Buttons */
.bottom-nav[data-theme="light"] .nav-button {
  color: rgb(71, 85, 105);
}

.bottom-nav[data-theme="light"] .nav-button:hover {
  color: rgb(51, 65, 85);
}

.bottom-nav[data-theme="light"] .nav-button:active {
  background: rgba(203, 213, 225, 0.5);
}

.bottom-nav[data-theme="light"] .nav-button.active {
  background: rgba(37, 99, 235, 0.15);
  color: rgb(37, 99, 235);
}

.bottom-nav[data-theme="light"] .active-indicator {
  background: rgba(37, 99, 235, 0.15);
  border: 1px solid rgba(37, 99, 235, 0.3);
}

.active-indicator {
  position: absolute;
  inset: 0;
  background: rgba(37, 99, 235, 0.2);
  border-radius: 0.5rem;
  border: 1px solid rgba(59, 130, 246, 0.3);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.nav-icon {
  width: 1.25rem;
  height: 1.25rem;
  position: relative;
  z-index: 10;
}

.nav-label {
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  position: relative;
  z-index: 10;
}
</style>
