import { ref, computed, watch } from 'vue'
import { Capacitor } from '@capacitor/core'
import { NativeAudio } from '@capacitor-community/native-audio'
import { useLocalStorage } from '@vueuse/core'
import BackgroundAudio from '../plugins/backgroundaudio'
import MediaStore from '../plugins/mediastore'
import { toast } from 'vue-sonner'
import { useMediaSession } from './useMediaSession'

export interface Track {
  title: string
  artist: string
  album: string
  id?: number
  albumId?: number
  uri: string
  albumArt?: string
  duration: number
  audioId?: string
}

// Global state that persists across component mounts/unmounts
const tracks = ref<Track[]>([])
const currentTrackIndex = useLocalStorage('music_currentTrackIndex', 0)
const isPlaying = ref(false)
const currentTime = useLocalStorage('music_currentTime', 0)
const duration = ref(0)
const shuffle = useLocalStorage('music_shuffle', false)
const repeat = useLocalStorage('music_repeat', false)
const isFavorite = ref(false)
const isScanning = ref(false)
const queue = useLocalStorage<number[]>('music_queue', []) // Queue of track indices
const queuePosition = useLocalStorage('music_queuePosition', 0) // Current position in queue

const useNativeAudio = Capacitor.isNativePlatform()
const AUDIO_ID = 'music_player'

// Progress tracking interval (needs to be managed globally)
let progressInterval: number | null = null

// Track change lock to prevent rapid successive changes
let isChangingTrack = false

export function useMusicPlayer() {
  // Current track should always come from the queue position
  const currentTrack = computed(() => {
    if (queue.value.length === 0) {
      return tracks.value[currentTrackIndex.value]
    }
    const trackIndex = queue.value[queuePosition.value]
    return tracks.value[trackIndex]
  })

  // Computed queue of tracks for display
  const queueTracks = computed(() => {
    return queue.value.slice(queuePosition.value).map(index => tracks.value[index])
  })

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60)
    const secs = Math.floor(seconds % 60)
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
  }

  // Initialize or refresh queue based on shuffle state
  const refreshQueue = () => {

    if (shuffle.value) {
      // If queue is empty or only has current track, initialize with all tracks
      if (queue.value.length <= 1) {
        const allIndices = Array.from({ length: tracks.value.length }, (_, i) => i)
          .filter(i => i !== currentTrackIndex.value)

        // Shuffle all tracks using Fisher-Yates
        for (let i = allIndices.length - 1; i > 0; i--) {
          const j = Math.floor(Math.random() * (i + 1));
          [allIndices[i], allIndices[j]] = [allIndices[j], allIndices[i]]
        }

        queue.value = [currentTrackIndex.value, ...allIndices]
        queuePosition.value = 0
      } else {
        // Only reshuffle tracks that are still in the queue (from current position onwards)
        const remainingQueue = queue.value.slice(queuePosition.value + 1)

        // Shuffle only the remaining tracks using Fisher-Yates
        for (let i = remainingQueue.length - 1; i > 0; i--) {
          const j = Math.floor(Math.random() * (i + 1));
          [remainingQueue[i], remainingQueue[j]] = [remainingQueue[j], remainingQueue[i]]
        }

        // Keep everything up to and including current position, then add shuffled remaining
        queue.value = queue.value.slice(0, queuePosition.value + 1).concat(remainingQueue)
      }
    } else {
      // Sequential queue - start from current track and go through all tracks in order
      queue.value = Array.from({ length: tracks.value.length }, (_, i) => i)
      queuePosition.value = currentTrackIndex.value
    }
  }

  // Remove track from queue
  const removeFromQueue = (queueIndex: number) => {

    const actualIndex = queuePosition.value + queueIndex
    if (actualIndex >= queue.value.length) return

    queue.value.splice(actualIndex, 1)

    // Adjust queue position if needed
    if (actualIndex < queuePosition.value) {
      queuePosition.value--
    }
  }

  // Play track from queue by jumping to its position
  const playFromQueue = async (queueIndex: number) => {

    const actualIndex = queuePosition.value + queueIndex
    if (actualIndex >= queue.value.length) return

    // Simply update the queue position to skip to the clicked track
    queuePosition.value = actualIndex

    // Play the track at the new queue position
    currentTrackIndex.value = queue.value[queuePosition.value]
    await playTrack(currentTrackIndex.value)
  }

  const startProgressTracking = () => {
    if (progressInterval) {
      clearInterval(progressInterval)
    }

    let updateCounter = 0
    progressInterval = window.setInterval(async () => {
      try {
        const result = await NativeAudio.getCurrentTime({ assetId: AUDIO_ID })
        currentTime.value = result.currentTime

        // Update media session position every 10 intervals (1 second)
        updateCounter++
        if (updateCounter >= 10) {
          updatePosition(currentTime.value, duration.value)
          updateCounter = 0
        }

        // Check if track ended
        if (currentTime.value >= duration.value - 0.5 && duration.value > 0) {
          isPlaying.value = false
          if (progressInterval) {
            clearInterval(progressInterval)
            progressInterval = null
          }

          if (repeat.value) {
            await playTrack(currentTrackIndex.value)
          } else {
            await nextTrack()
          }
        }
      } catch {
        // Progress tracking error - likely audio stopped
      }
    }, 100)
  }

  const stopProgressTracking = () => {
    if (progressInterval) {
      clearInterval(progressInterval)
      progressInterval = null
    }
  }

  const playTrack = async (index: number) => {
    const track = tracks.value[index]
    if (!track) {
      return
    }

    try {
      currentTrackIndex.value = index
      currentTime.value = 0

      if (!track.uri) {
        toast.error('No audio source available')
        return
      }

      // Load album art on-demand for the current track using audio file ID
      if (track.id && !track.albumArt) {
        try {
          const result = await MediaStore.getAlbumArt({ audioId: track.id })
          if (result.albumArt) {
            track.albumArt = result.albumArt
          }
        } catch {
          // Album art not available
        }
      }

      if (useNativeAudio) {
        // Request audio focus
        try {
          if (BackgroundAudio && typeof BackgroundAudio.requestAudioFocus === 'function') {
            await BackgroundAudio.requestAudioFocus()
          }
        } catch {
          // Audio focus not available
        }

        // Stop and unload previous track
        try {
          stopProgressTracking()
          await NativeAudio.stop({ assetId: AUDIO_ID })
          await NativeAudio.unload({ assetId: AUDIO_ID })
        } catch {
          // No previous audio to stop
        }

        try {
          // Preload the new track
          await NativeAudio.preload({
            assetId: AUDIO_ID,
            assetPath: track.uri,
            audioChannelNum: 1, // STREAM_MUSIC
            isUrl: false,
            volume: 1.0
          })

          // Get duration
          const durationResult = await NativeAudio.getDuration({ assetId: AUDIO_ID })
          duration.value = durationResult.duration

          // Play the track
          await NativeAudio.play({ assetId: AUDIO_ID, time: 0 })
          isPlaying.value = true
          currentTrack.value?.title

          updateMediaSession()

          // Start tracking progress
          startProgressTracking()
        } catch (audioError) {
          toast.error('Failed to play audio')
          isPlaying.value = false
          return
        }
      }
    } catch (error) {
      toast.error('Failed to play track')
      isPlaying.value = false
    }
  }

  const togglePlay = async () => {
    const track = currentTrack.value
    if (!track) {
      toast.info('No track selected')
      return
    }

    try {
      if (useNativeAudio) {
        if (isPlaying.value) {
          // Pause playback
          await NativeAudio.pause({ assetId: AUDIO_ID })
          isPlaying.value = false
          stopProgressTracking()
          updateMediaSession()
        } else {
          // Check if audio is already loaded by trying to get duration
          let audioLoaded = false
          try {
            await NativeAudio.getDuration({ assetId: AUDIO_ID })
            audioLoaded = true
          } catch {
            // Audio not loaded yet
          }

          if (audioLoaded) {
            // Audio is loaded, resume from current position
            await NativeAudio.play({ assetId: AUDIO_ID, time: currentTime.value })
            isPlaying.value = true
            startProgressTracking()
            updateMediaSession()
          } else {
            // Audio not loaded, load and play from beginning
            await playTrack(currentTrackIndex.value)
          }
        }
      }
    } catch (error) {
      toast.error('Failed to toggle playback')
    }
  }

  const nextTrack = async () => {
    // Prevent rapid successive track changes (debounce)
    if (isChangingTrack) {
      return
    }

    isChangingTrack = true
    try {

      // Initialize queue if empty
      if (queue.value.length === 0) {
        refreshQueue()
      }

      // Move to next in queue
      queuePosition.value++

      // If reached end of queue, create new queue
      if (queuePosition.value >= queue.value.length) {
        refreshQueue()
        queuePosition.value = 0
      }

      const nextTrackIndex = queue.value[queuePosition.value]
      currentTrackIndex.value = nextTrackIndex
      await playTrack(currentTrackIndex.value)
    } finally {
      isChangingTrack = false
    }
  }

  const previousTrack = async () => {
    // Prevent rapid successive track changes (debounce)
    if (isChangingTrack) {
      return
    }

    if (currentTime.value > 3) {
      // Restart current track if more than 3 seconds in
      if (useNativeAudio) {
        try {
          await NativeAudio.stop({ assetId: AUDIO_ID })
          await NativeAudio.play({ assetId: AUDIO_ID })
          currentTime.value = 0
        } catch {
          // Failed to restart track
        }
      }
    } else {
      isChangingTrack = true
      try {
        // Go to previous track in queue
        if (queue.value.length === 0) {
          refreshQueue()
        }

        if (queuePosition.value > 0) {
          queuePosition.value--
        } else {
          // Wrap to end of queue
          queuePosition.value = queue.value.length - 1
        }

        currentTrackIndex.value = queue.value[queuePosition.value]
        await playTrack(currentTrackIndex.value)
      } finally {
        isChangingTrack = false
      }
    }
  }

  const seekTo = async (time: number) => {
    const wasPlaying = isPlaying.value

    currentTime.value = time

    if (useNativeAudio) {
      try {
        stopProgressTracking()
        await NativeAudio.stop({ assetId: AUDIO_ID })
        await NativeAudio.play({ assetId: AUDIO_ID, time })

        if (wasPlaying) {
          isPlaying.value = true
          startProgressTracking()
        } else {
          await NativeAudio.pause({ assetId: AUDIO_ID })
          isPlaying.value = false
        }
      } catch (error) {
        toast.error('Failed to seek')
      }
    }
  }

  const toggleShuffle = () => {
    shuffle.value = !shuffle.value
    refreshQueue()
  }

  const toggleRepeat = () => {
    repeat.value = !repeat.value
  }

  const toggleFavorite = () => {
    isFavorite.value = !isFavorite.value
  }

  // Restore playback state when component remounts
  const restorePlaybackState = async () => {
    // If we have tracks and a saved track index, restore the track and queue
    if (tracks.value.length > 0 && currentTrackIndex.value >= 0 && currentTrackIndex.value < tracks.value.length) {
      // Restore queue if it exists, otherwise refresh it
      if (queue.value.length === 0) {
        refreshQueue()
      }

      // Validate queue position
      if (queuePosition.value >= queue.value.length) {
        queuePosition.value = 0
      }

      // If we have a saved position > 3 seconds, seek to it after loading the track
      const savedTime = currentTime.value
      if (savedTime > 3) {
        try {
          // Load the track without playing
          const track = tracks.value[currentTrackIndex.value]
          if (track && track.uri) {
            // Load album art
            if (track.id && !track.albumArt) {
              try {
                const result = await MediaStore.getAlbumArt({ audioId: track.id })
                if (result.albumArt) {
                  track.albumArt = result.albumArt
                }
              } catch {
                // Album art not available
              }
            }

            if (useNativeAudio) {
              // Preload without playing
              await NativeAudio.preload({
                assetId: AUDIO_ID,
                assetPath: track.uri,
                audioChannelNum: 1,
                isUrl: false,
                volume: 1.0
              })

              // Get duration
              const durationResult = await NativeAudio.getDuration({ assetId: AUDIO_ID })
              duration.value = durationResult.duration

              // Update media session with track info
              updateMetadata(currentTrack.value || null)
              updatePlaybackState('paused')
            }
          }
        } catch {
          // Failed to restore, reset to beginning
          currentTime.value = 0
        }
      }
    }
  }

  // Initialize Media Session API for hardware controls (earphones, intercom, car Bluetooth)
  const { updateMetadata, updatePlaybackState, updatePosition } = useMediaSession({
    onPlay: async () => {
      if (!isPlaying.value) {
        await togglePlay()
      }
    },
    onPause: async () => {
      if (isPlaying.value) {
        await togglePlay()
      }
    },
    onNext: async () => {
      await nextTrack()
     },
    onPrevious: async () => {
      await previousTrack()
    },
    onSeek: async (timeOrOffset: number) => {
      // If time is negative, it's an offset (seek backward/forward)
      // Otherwise it's an absolute position (seekto)
      const newTime = timeOrOffset < 0 || timeOrOffset > duration.value
        ? Math.max(0, Math.min(duration.value, currentTime.value + timeOrOffset))
        : timeOrOffset
      await seekTo(newTime)
    }
  })

  // Helper function to update media session state
  const updateMediaSession = () => {
    updateMetadata(currentTrack.value || null)
    updatePlaybackState(isPlaying.value ? 'playing' : 'paused')
    updatePosition(currentTime.value, duration.value)
  }

  // Select and play a track by its index in the tracks array
  // This ensures queue position is synchronized with the selected track
  const selectAndPlayTrack = async (trackIndex: number) => {
    if (trackIndex < 0 || trackIndex >= tracks.value.length) return

    // Set current track FIRST before refreshing queue
    currentTrackIndex.value = trackIndex

    // Initialize or refresh queue to start from selected track
    refreshQueue()

    // Play the track at current position
    await playTrack(currentTrackIndex.value)
  }

  return {
    // State
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
    queue,
    queuePosition,
    queueTracks,

    // Methods
    formatTime,
    playTrack,
    selectAndPlayTrack,
    togglePlay,
    nextTrack,
    previousTrack,
    seekTo,
    toggleShuffle,
    toggleRepeat,
    toggleFavorite,
    startProgressTracking,
    stopProgressTracking,
    restorePlaybackState,
    refreshQueue,
    removeFromQueue,
    playFromQueue,
    updateMediaSession,
  }
}
