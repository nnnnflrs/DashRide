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
/* Dark Theme (Default) - Machined metal navigation bar */
.bottom-nav {
  background: linear-gradient(0deg,
    rgba(18, 22, 26, 0.98) 0%,
    rgba(26, 31, 37, 0.98) 50%,
    rgba(37, 43, 51, 0.98) 100%);
  backdrop-filter: blur(12px);
  border-top: 1px solid rgba(90, 101, 119, 0.3);
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.4), inset 0 1px 0 rgba(255, 255, 255, 0.04);
  transition: background 0.3s ease, border-color 0.3s ease;
  padding-left: var(--safe-area-inset-left, 0px);
  padding-right: var(--safe-area-inset-right, 0px);
}

/* Light Theme */
.bottom-nav[data-theme="light"] {
  background: linear-gradient(0deg,
    rgba(26, 31, 37, 0.98) 0%,
    rgba(37, 43, 51, 0.98) 50%,
    rgba(42, 48, 56, 0.98) 100%);
  border-top: 1px solid rgba(90, 101, 119, 0.4);
}

.nav-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-xs, 0.25rem);
  padding: var(--space-xs, 0.5rem);
}

/* Machined metal button - recessed with bevel */
.nav-button {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-xs, 0.25rem);
  padding: var(--space-sm, 0.5rem) var(--space-sm, 0.75rem);
  border-radius: var(--radius-md, 0.5rem);
  transition: all 0.2s;
  background: transparent;
  border: 1px solid transparent;
  cursor: pointer;
  color: var(--metal-shine, rgba(136, 153, 170, 1));
}

.nav-button:hover {
  color: var(--aluminum-shine, #a0aec0);
  background: rgba(58, 66, 77, 0.3);
}

.nav-button:active {
  background: rgba(18, 22, 26, 0.6);
  box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.4);
}

/* Active state - LED glow accent */
.nav-button.active {
  background: linear-gradient(145deg, rgba(37, 43, 51, 0.8), rgba(18, 22, 26, 0.8));
  color: var(--accent-green, #00ff88);
  border: 1px solid color-mix(in srgb, var(--accent-green, #00ff88) 20%, transparent);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.03), 0 1px 4px rgba(0, 0, 0, 0.3);
}

/* Light Theme Navigation Buttons */
.bottom-nav[data-theme="light"] .nav-button {
  color: var(--aluminum-light, #718096);
}

.bottom-nav[data-theme="light"] .nav-button:hover {
  color: var(--aluminum-shine, #a0aec0);
}

.bottom-nav[data-theme="light"] .nav-button:active {
  background: rgba(18, 22, 26, 0.6);
}

.bottom-nav[data-theme="light"] .nav-button.active {
  color: var(--accent-green, #00ff88);
  background: linear-gradient(145deg, rgba(37, 43, 51, 0.8), rgba(18, 22, 26, 0.8));
  border: 1px solid color-mix(in srgb, var(--accent-green, #00ff88) 20%, transparent);
}

.bottom-nav[data-theme="light"] .active-indicator {
  background: color-mix(in srgb, var(--accent-green, #00ff88) 6%, transparent);
  border: 1px solid color-mix(in srgb, var(--accent-green, #00ff88) 15%, transparent);
}

/* Active indicator - LED underglow */
.active-indicator {
  position: absolute;
  inset: 0;
  background: color-mix(in srgb, var(--accent-green, #00ff88) 6%, transparent);
  border-radius: var(--radius-md, 0.5rem);
  border: 1px solid color-mix(in srgb, var(--accent-green, #00ff88) 15%, transparent);
  box-shadow: 0 0 8px color-mix(in srgb, var(--accent-green, #00ff88) 8%, transparent);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.nav-icon {
  width: var(--icon-sm, 1.25rem);
  height: var(--icon-sm, 1.25rem);
  position: relative;
  z-index: 10;
  transition: filter 0.2s;
}

.nav-button.active .nav-icon {
  filter: drop-shadow(0 0 4px var(--glow-green, rgba(0, 255, 136, 0.4)));
}

/* Laser-etched label */
.nav-label {
  font-size: var(--text-xs, 0.75rem);
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  position: relative;
  z-index: 10;
  text-shadow: 0 1px 0 rgba(0, 0, 0, 0.5);
  font-family: 'SF Mono', 'Menlo', 'Monaco', 'Consolas', monospace;
}
</style>
