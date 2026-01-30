<template>
  <div class="music-player" :data-theme="theme">
    <div class="blur-background" :style="{ backgroundImage: currentTrack?.albumArt ? `url(${currentTrack.albumArt})` : 'none' }"></div>
    
    <div class="player-content">
      <div class="album-art-container">
        <img
          v-if="currentTrack?.albumArt"
          :src="currentTrack.albumArt"
          alt="Album Art"
          class="album-art"
        />
        <div v-else class="album-art-placeholder">
          <Music class="placeholder-icon" />
        </div>
      </div>

      <!-- Right Side Content -->
      <div class="player-right">
        <!-- Track Info -->
        <div class="track-info">
          <button @click="toggleFavorite" class="favorite-btn">
            <Star :class="['star-icon', { filled: isFavorite }]" />
          </button>
          
          <div class="track-details">
            <h2 class="track-title">{{ currentTrack?.title || 'No Track' }}</h2>
            <p class="track-artist clickable" @click="showArtistTracks">{{ currentTrack?.artist || 'Unknown Artist' }}</p>
            <p class="track-album clickable" @click="showAlbumTracks">{{ currentTrack?.album || 'Unknown Album' }}</p>
          </div>

          <button class="add-btn">
            <Plus class="add-icon" />
          </button>
        </div>

        <!-- Progress Bar -->
        <div class="progress-container">
          <input
            type="range"
            :value="currentTime"
            :max="duration"
            @input="handleSeek"
            class="progress-bar"
          />
          <div class="time-labels">
            <span class="time-label">{{ formatTime(currentTime) }}</span>
            <span class="time-label">{{ formatTime(duration) }}</span>
          </div>
        </div>

        <!-- Controls -->
        <div class="controls">
          <button @click="toggleRepeat" class="control-btn">
            <Repeat :class="['control-icon', { active: repeat }]" />
          </button>

          <button @click="previousTrack" class="control-btn">
            <SkipBack class="control-icon large" />
          </button>

          <button @click="togglePlay" class="control-btn play-btn">
            <Pause v-if="isPlaying" class="control-icon xlarge" />
            <Play v-else class="control-icon xlarge" />
          </button>

          <button @click="nextTrack" class="control-btn">
            <SkipForward class="control-icon large" />
          </button>

          <button @click="toggleShuffle" class="control-btn">
            <Shuffle :class="['control-icon', { active: shuffle }]" />
          </button>
        </div>

        <!-- Action Buttons -->
        <div class="action-buttons">
          <button @click="showTrackList = !showTrackList" class="action-btn">
            <List class="action-icon" />
            <span>{{ tracks.length }} Tracks</span>
          </button>
          <button @click="showQueue = !showQueue" class="action-btn">
            <List class="action-icon" />
            <span>Queue ({{ queueTracks.length }})</span>
          </button>
          <button v-if="tracks.length === 0" @click="scanForMusic" class="action-btn scan-music-btn" :disabled="isScanning">
            <Music class="action-icon" />
            <span>{{ isScanning ? 'Scanning...' : 'Scan Music' }}</span>
          </button>
        </div>
      </div>
    </div>

    <!-- Track List Modal -->
    <div v-if="showTrackList" class="track-list-modal" @click="showTrackList = false">
      <div class="track-list-content" @click.stop>
        <div class="track-list-header">
          <h3>Track List</h3>
          <button @click="showTrackList = false" class="close-btn">
            <X class="close-icon" />
          </button>
        </div>
        <div class="track-list-items" ref="trackListContainer">
          <div
            v-for="(track, index) in tracks"
            :key="index"
            :ref="el => { if (index === currentTrackIndex) currentTrackElement = el as HTMLElement }"
            @click="handlePlayTrack(index)"
            :class="['track-item', { active: currentTrackIndex === index }]"
          >
            <Music class="track-item-icon" />
            <div class="track-item-info">
              <div class="track-item-title">{{ track.title }}</div>
              <div class="track-item-artist">{{ track.artist }}</div>
            </div>
            <div v-if="currentTrackIndex === index" class="playing-indicator">
              <div class="wave"></div>
              <div class="wave"></div>
              <div class="wave"></div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Queue Modal -->
    <div v-if="showQueue" class="track-list-modal" @click="showQueue = false">
      <div class="track-list-content" @click.stop>
        <div class="track-list-header">
          <h3>Queue ({{ queueTracks.length }} tracks)</h3>
          <button @click="showQueue = false" class="close-btn">
            <X class="close-icon" />
          </button>
        </div>
        <div class="track-list-items">
          <div
            v-for="(track, index) in queueTracks"
            :key="index"
            @click="handlePlayFromQueue(index)"
            :class="['track-item', { active: index === 0, removing: removingTrackIndex === index }]"
          >
            <Music class="track-item-icon" />
            <div class="track-item-info">
              <div class="track-item-title">{{ track.title }}</div>
              <div class="track-item-artist">{{ track.artist }}</div>
            </div>
            <div v-if="index === 0" class="playing-indicator">
              <div class="wave"></div>
              <div class="wave"></div>
              <div class="wave"></div>
            </div>
            <button
              v-else
              @click.stop="handleRemoveFromQueue(index)"
              class="queue-remove-btn"
              title="Remove from queue"
            >
              <X class="queue-remove-icon" />
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Filtered Tracks Modal (Artist/Album) -->
    <div v-if="showFilteredTracks" class="track-list-modal" @click="showFilteredTracks = false">
      <div class="track-list-content" @click.stop>
        <div class="track-list-header">
          <h3>{{ filteredTracksTitle }} ({{ filteredTracks.length }} tracks)</h3>
          <button @click="showFilteredTracks = false" class="close-btn">
            <X class="close-icon" />
          </button>
        </div>
        <div class="track-list-items">
          <div
            v-for="(track, index) in filteredTracks"
            :key="index"
            @click="handlePlayFilteredTrack(track)"
            :class="['track-item', { active: currentTrack?.title === track.title && currentTrack?.artist === track.artist }]"
          >
            <Music class="track-item-icon" />
            <div class="track-item-info">
              <div class="track-item-title">{{ track.title }}</div>
              <div class="track-item-artist">{{ track.artist }}</div>
            </div>
            <div v-if="currentTrack?.title === track.title && currentTrack?.artist === track.artist" class="playing-indicator">
              <div class="wave"></div>
              <div class="wave"></div>
              <div class="wave"></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { Music, Star, Plus, Shuffle, SkipBack, Play, Pause, SkipForward, Repeat, List, X } from 'lucide-vue-next'
import { Capacitor } from '@capacitor/core'
import MediaStore from '../../plugins/mediastore'
import { toast } from 'vue-sonner'
import { useMusicPlayer, type Track } from '../../composables/useMusicPlayer'

interface Props {
  theme?: 'light' | 'dark'
}

defineProps<Props>()

const {
  tracks,
  currentTrackIndex,
  isPlaying,
  currentTime,
  duration,
  shuffle,
  repeat,
  isFavorite,
  isScanning,
  currentTrack,
  queueTracks,
  formatTime,
  selectAndPlayTrack,
  setCustomQueue,
  togglePlay,
  nextTrack,
  previousTrack,
  seekTo,
  toggleShuffle,
  toggleRepeat,
  toggleFavorite,
  restorePlaybackState,
  removeFromQueue,
  playFromQueue,
  updateMediaSession,
} = useMusicPlayer()

const showTrackList = ref(false)
const showQueue = ref(false)
const showFilteredTracks = ref(false)
const filteredTracks = ref<Track[]>([])
const filteredTracksTitle = ref('')
const trackListContainer = ref<HTMLElement | null>(null)
const currentTrackElement = ref<HTMLElement | null>(null)
const removingTrackIndex = ref<number | null>(null)

watch(showTrackList, async (isOpen) => {
  if (isOpen) {
    await nextTick()
    if (currentTrackElement.value && trackListContainer.value) {
      currentTrackElement.value.scrollIntoView({
        behavior: 'smooth',
        block: 'center'
      })
    }
  }
})

const scanAudioFiles = async () => {
  if (isScanning.value) {
    return
  }
  
  isScanning.value = true
  toast.info('Scanning for music files...')

  try {
    const result = await MediaStore.scanAudioFiles()

    if (result.files && result.files.length > 0) {
      const newTracks: Track[] = result.files.map((file, index) => {
        const audioId = `track_${Date.now()}_${index}`
        
        return {
          id: file.id,
          title: file.title || 'Unknown Title',
          artist: file.artist || 'Unknown Artist',
          album: file.album || 'Unknown Album',
          albumId: file.albumId,
          uri: file.uri,
          albumArt: file.albumArt,
          duration: file.duration / 1000,
          audioId
        }
      })

      tracks.value = newTracks
      toast.success(`Found ${newTracks.length} track${newTracks.length > 1 ? 's' : ''}!`)
    } else {
      toast.info('No music files found')
      tracks.value = []
    }
  } catch (error) {
    toast.error('Failed to scan music files')
  } finally {
    isScanning.value = false
  }
}

const scanForMusic = async () => {
  await scanAudioFiles()
}

const handleSeek = (event: Event) => {
  const target = event.target as HTMLInputElement
  const time = parseFloat(target.value)
  seekTo(time)
}

const handlePlayTrack = (index: number) => {
  selectAndPlayTrack(index)
  showTrackList.value = false
}

const handleRemoveFromQueue = (queueIndex: number) => {
  removingTrackIndex.value = queueIndex
  setTimeout(() => {
    removeFromQueue(queueIndex)
    removingTrackIndex.value = null
  }, 300)
}

const handlePlayFromQueue = async (queueIndex: number) => {
  await playFromQueue(queueIndex)
  showQueue.value = false;
}

const showArtistTracks = () => {
  if (!currentTrack.value) return

  filteredTracks.value = tracks.value.filter(track =>
    track.artist === currentTrack.value!.artist
  )
  filteredTracksTitle.value = `Songs by ${currentTrack.value.artist}`
  showFilteredTracks.value = true
}

const showAlbumTracks = () => {
  if (!currentTrack.value) return

  filteredTracks.value = tracks.value.filter(track =>
    track.album === currentTrack.value!.album &&
    track.artist === currentTrack.value!.artist
  )
  filteredTracksTitle.value = `${currentTrack.value.album} by ${currentTrack.value.artist}`
  showFilteredTracks.value = true
}

const handlePlayFilteredTrack = async (track: Track) => {
  const trackIndex = tracks.value.findIndex(t =>
    t.title === track.title && t.artist === track.artist && t.album === track.album
  )

  if (trackIndex === -1) return

  const filteredIndices = filteredTracks.value.map(filteredTrack =>
    tracks.value.findIndex(t =>
      t.title === filteredTrack.title &&
      t.artist === filteredTrack.artist &&
      t.album === filteredTrack.album
    )
  ).filter(index => index !== -1)

  const positionInFiltered = filteredIndices.indexOf(trackIndex)

  const newQueue = [
    trackIndex,
    ...filteredIndices.slice(0, positionInFiltered),
    ...filteredIndices.slice(positionInFiltered + 1)
  ]

  await setCustomQueue(newQueue, 0)
  showFilteredTracks.value = false
}

let permissionCheckInterval: number | null = null

const checkPermissionsAndScan = async () => {
  if (!Capacitor.isNativePlatform()) {
    console.log('MusicPlayer: Running on web, skipping permission check')
    await scanForMusic()
    return
  }

  try {
    console.log('MusicPlayer: Checking media permissions...')
    const result = await MediaStore.checkPermissions()
    console.log('MusicPlayer: Permission granted =', result.granted)

    if (result.granted) {
      console.log('MusicPlayer: Permissions granted, scanning for music...')

      if (permissionCheckInterval) {
        clearInterval(permissionCheckInterval)
        permissionCheckInterval = null
      }

      if (tracks.value.length === 0) {
        await scanForMusic()
      }
    } else {
      console.log('MusicPlayer: Permissions not granted yet')
    }
  } catch (err) {
    console.error('MusicPlayer: Error checking permissions:', err)
  }
}

onMounted(async () => {
  console.log('MusicPlayer: Component mounted')

  await restorePlaybackState()
  updateMediaSession()

  await checkPermissionsAndScan()

  if (tracks.value.length === 0 && Capacitor.isNativePlatform()) {
    console.log('MusicPlayer: Setting up permission check interval...')
    permissionCheckInterval = window.setInterval(async () => {
      await checkPermissionsAndScan()
    }, 500)
  }
})

onUnmounted(() => {
  if (permissionCheckInterval) {
    clearInterval(permissionCheckInterval)
    permissionCheckInterval = null
  }
})
</script>

<style scoped>
.music-player {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  overflow: hidden;
}

.blur-background {
  position: absolute;
  inset: 0;
  background-size: cover;
  background-position: center;
  filter: blur(60px);
  opacity: 0.3;
  z-index: 0;
}

.blur-background::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, rgba(20, 20, 20, 0.85), rgba(40, 40, 40, 0.85));
  transition: background 0.3s ease;
}

/* Light Theme Blur Background */
.music-player[data-theme="light"] .blur-background::after {
  background: linear-gradient(135deg, rgba(241, 245, 249, 0.92), rgba(226, 232, 240, 0.90));
}

.player-content {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 2.5rem;
  padding: 1rem 1.5rem;
  width: 100%;
  max-width: 100%;
  height: 100%;
}

.album-art-container {
  flex-shrink: 0;
  width: 320px;
  height: 320px;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.7);
}

.album-art {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.album-art-placeholder {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, rgba(60, 60, 60, 0.9), rgba(80, 80, 80, 0.7));
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.3s ease;
}

.placeholder-icon {
  width: 60px;
  height: 60px;
  color: rgba(255, 255, 255, 0.3);
  transition: color 0.3s ease;
}

/* Light Theme Album Art Placeholder */
.music-player[data-theme="light"] .album-art-placeholder {
  background: linear-gradient(135deg, rgba(203, 213, 225, 0.9), rgba(226, 232, 240, 0.8));
}

.music-player[data-theme="light"] .placeholder-icon {
  color: rgba(71, 85, 105, 0.4);
}

.player-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  min-width: 0;
}

.track-info {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
}

.favorite-btn,
.add-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 0.5rem;
  transition: transform 0.2s;
}

.favorite-btn:active,
.add-btn:active {
  transform: scale(0.9);
}

.star-icon,
.add-icon {
  width: 1.25rem;
  height: 1.25rem;
  color: rgba(255, 255, 255, 0.7);
  transition: color 0.3s ease;
}

.favorite-btn:hover .star-icon,
.add-btn:hover .add-icon {
  color: white;
}

/* Light Theme Icons */
.music-player[data-theme="light"] .star-icon,
.music-player[data-theme="light"] .add-icon {
  color: rgb(125, 211, 252);
}

.music-player[data-theme="light"] .favorite-btn:hover .star-icon,
.music-player[data-theme="light"] .add-btn:hover .add-icon {
  color: rgb(186, 230, 253);
}

.star-icon.filled {
  color: rgb(250, 204, 21);
  fill: rgb(250, 204, 21);
}

.track-details {
  flex: 1;
  text-align: center;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
  overflow: hidden;
}

.track-title {
  font-size: 1.25rem;
  font-weight: 700;
  color: white;
  margin-bottom: 0.25rem;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.3s ease;
}

.track-artist {
  font-size: 0.95rem;
  color: rgba(255, 255, 255, 0.9);
  margin-bottom: 0.125rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.3s ease;
}

.track-artist.clickable,
.track-album.clickable {
  cursor: pointer;
  text-decoration: underline;
  text-decoration-style: dotted;
  text-underline-offset: 2px;
}

.track-artist.clickable:hover,
.track-album.clickable:hover {
  color: rgba(255, 255, 255, 1);
  text-decoration-style: solid;
}

.track-album {
  font-size: 0.8rem;
  color: rgba(255, 255, 255, 0.6);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.3s ease;
}

/* Light Theme Track Info */
.music-player[data-theme="light"] .track-title {
  color: rgb(224, 242, 254);
  text-shadow: 0 0 8px rgba(56, 189, 248, 0.5);
}

.music-player[data-theme="light"] .track-artist {
  color: rgb(186, 230, 253);
}

.music-player[data-theme="light"] .track-album {
  color: rgb(125, 211, 252);
}

.progress-container {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  width: 100%;
}

.time-labels {
  display: flex;
  justify-content: space-between;
}

.time-label {
  font-size: 0.75rem;
  color: rgba(255, 255, 255, 0.7);
  transition: color 0.3s ease;
}

/* Light Theme Time Labels */
.music-player[data-theme="light"] .time-label {
  color: rgb(186, 230, 253);
}

.progress-bar {
  width: 100%;
  height: 4px;
  -webkit-appearance: none;
  appearance: none;
  background: rgba(255, 255, 255, 0.25);
  border-radius: 2px;
  outline: none;
  cursor: pointer;
}

.progress-bar::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 12px;
  height: 12px;
  background: white;
  border-radius: 50%;
  cursor: pointer;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.3);
}

.progress-bar::-moz-range-thumb {
  width: 12px;
  height: 12px;
  background: white;
  border-radius: 50%;
  cursor: pointer;
  border: none;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.3);
}

.controls {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1rem;
}

.control-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 0.5rem;
  transition: transform 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.control-btn:active {
  transform: scale(0.9);
}

.control-icon {
  width: 1.5rem;
  height: 1.5rem;
  color: rgba(255, 255, 255, 0.9);
  transition: color 0.3s ease;
}

/* Light Theme Control Icons */
.music-player[data-theme="light"] .control-icon {
  color: rgb(51, 65, 85);
}

.control-icon.large {
  width: 2.25rem;
  height: 2.25rem;
}

.control-icon.xlarge {
  width: 2rem;
  height: 2rem;
}

.control-icon.active {
  color: rgb(96, 165, 250);
}

/* Light Theme Active Control Icons */
.music-player[data-theme="light"] .control-icon.active {
  color: rgb(37, 99, 235);
}

.play-btn {
  width: 3.5rem;
  height: 3.5rem;
  background: white;
  border-radius: 50%;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
  transition: transform 0.2s, box-shadow 0.2s;
}

.play-btn:hover {
  transform: scale(1.05);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.5);
}

.play-btn .control-icon {
  color: rgb(30, 30, 30);
}

/* Light Theme Play Button - keep same for contrast */

.action-buttons {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  margin-top: 0.5rem;
}

.action-btn {
  background: rgba(255, 255, 255, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 6px;
  padding: 0.5rem 1rem;
  color: white;
  font-size: 0.8rem;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  transition: all 0.3s ease;
}

.action-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.2);
  border-color: rgba(255, 255, 255, 0.35);
}

/* Light Theme Action Buttons */
.music-player[data-theme="light"] .action-btn {
  background: rgba(203, 213, 225, 0.5);
  border: 1px solid rgba(148, 163, 184, 0.4);
  color: rgb(51, 65, 85);
}

.music-player[data-theme="light"] .action-btn:hover:not(:disabled) {
  background: rgba(203, 213, 225, 0.7);
  border-color: rgba(148, 163, 184, 0.6);
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.action-icon {
  width: 0.9rem;
  height: 0.9rem;
}

.track-list-modal {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.95);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.track-list-content {
  background: linear-gradient(135deg, rgba(30, 30, 30, 0.98), rgba(50, 50, 50, 0.95));
  width: 100%;
  height: 100%;
  border-radius: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  transition: background 0.3s ease;
}

/* Light Theme Track List */
.music-player[data-theme="light"] .track-list-content {
  background: linear-gradient(135deg, rgba(241, 245, 249, 0.98), rgba(226, 232, 240, 0.95));
}

.track-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 1.5rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.track-list-header h3 {
  color: white;
  font-size: 1.25rem;
  font-weight: 600;
  transition: color 0.3s ease;
}

.music-player[data-theme="light"] .track-list-header h3 {
  color: rgb(15, 23, 42);
}

.close-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 0.5rem;
}

.close-icon {
  width: 1.5rem;
  height: 1.5rem;
  color: rgba(255, 255, 255, 0.7);
  transition: color 0.3s ease;
}

.music-player[data-theme="light"] .close-icon {
  color: rgb(71, 85, 105);
}

.track-list-items {
  flex: 1;
  overflow-y: auto;
  padding: 0.5rem 0;
}

.track-item {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.75rem 1.5rem;
  cursor: pointer;
  transition: all 0.3s ease;
  transform-origin: center;
}

.track-item:hover {
  background: rgba(255, 255, 255, 0.05);
}

.track-item.active {
  background: rgba(96, 165, 250, 0.1);
}

.track-item.removing {
  animation: slideOutFade 0.3s ease forwards;
  pointer-events: none;
}

@keyframes slideOutFade {
  0% {
    opacity: 1;
    transform: translateX(0) scale(1);
  }
  50% {
    opacity: 0.5;
    transform: translateX(20px) scale(0.95);
  }
  100% {
    opacity: 0;
    transform: translateX(40px) scale(0.9);
    max-height: 0;
    padding-top: 0;
    padding-bottom: 0;
    margin-top: 0;
    margin-bottom: 0;
  }
}

.track-item-icon {
  width: 1.25rem;
  height: 1.25rem;
  color: rgba(255, 255, 255, 0.5);
  flex-shrink: 0;
  transition: color 0.3s ease;
}

.music-player[data-theme="light"] .track-item-icon {
  color: rgb(100, 116, 139);
}

.track-item-info {
  flex: 1;
  min-width: 0;
}

.track-item-title {
  color: white;
  font-size: 0.9rem;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.3s ease;
}

.track-item-artist {
  color: rgba(255, 255, 255, 0.6);
  font-size: 0.8rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.3s ease;
}

.music-player[data-theme="light"] .track-item-title {
  color: rgb(15, 23, 42);
}

.music-player[data-theme="light"] .track-item-artist {
  color: rgb(100, 116, 139);
}

.playing-indicator {
  display: flex;
  gap: 2px;
  align-items: flex-end;
  height: 16px;
}

.wave {
  width: 3px;
  background: rgb(96, 165, 250);
  animation: wave 1s ease-in-out infinite;
}

.wave:nth-child(1) {
  animation-delay: 0s;
}

.wave:nth-child(2) {
  animation-delay: 0.2s;
}

.wave:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes wave {
  0%, 100% {
    height: 4px;
  }
  50% {
    height: 16px;
  }
}

.queue-remove-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 0.5rem;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0.6;
  transition: opacity 0.2s, transform 0.2s;
}

.queue-remove-btn:hover {
  opacity: 1;
  transform: scale(1.1);
}

.queue-remove-icon {
  width: 1.25rem;
  height: 1.25rem;
  color: rgba(255, 255, 255, 0.7);
  transition: color 0.3s ease;
}

.music-player[data-theme="light"] .queue-remove-icon {
  color: rgb(100, 116, 139);
}


/* Portrait Orientation Styles */
@media (orientation: portrait) {

  .music-player {
    padding: 1rem;
  }

  .player-content {
    padding-top: 6rem;
    flex-direction: column;
    gap: 1.5rem;
    align-items: center;
  }

  .album-art-container {
    width: 100%;
    max-width: 320px;
    height: auto;
  }

  .album-art,
  .album-art-placeholder {
    width: 100%;
    height: auto;
    aspect-ratio: 1;
    max-height: 320px;
  }

  .player-right {
    width: 100%;
    max-width: 100%;
  }

  .track-info {
    margin-bottom: 1rem;
  }

  .track-title {
    font-size: 1.5rem;
  }

  .track-artist {
    font-size: 1.1rem;
  }

  .track-album {
    font-size: 0.95rem;
  }

  .playback-controls {
    gap: 1.5rem;
    margin: 1.5rem 0;
  }

  .control-btn {
    width: 3.5rem;
    height: 3.5rem;
  }

  .play-pause-btn {
    width: 4.5rem;
    height: 4.5rem;
  }

  .tracks-list {
    max-height: 40vh;
  }
}

/* Portrait mode - smaller devices */
@media (orientation: portrait) and (max-height: 700px) {
  .album-art-container {
    max-width: 240px;
  }

  .album-art,
  .album-art-placeholder {
    max-height: 240px;
  }

  .track-title {
    font-size: 1.25rem;
  }

  .track-artist {
    font-size: 1rem;
  }

  .playback-controls {
    gap: 1rem;
    margin: 1rem 0;
  }

  .control-btn {
    width: 3rem;
    height: 3rem;
  }

  .play-pause-btn {
    width: 4rem;
    height: 4rem;
  }

  .tracks-list {
    max-height: 30vh;
  }
}
</style>
