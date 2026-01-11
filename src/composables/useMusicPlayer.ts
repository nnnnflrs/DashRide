import { ref, computed } from 'vue'
import { Capacitor } from '@capacitor/core'
import { NativeAudio } from '@capacitor-community/native-audio'
import BackgroundAudio from '../plugins/backgroundaudio'
import MediaStore from '../plugins/mediastore'
import { toast } from 'vue-sonner'

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
const currentTrackIndex = ref(0)
const isPlaying = ref(false)
const currentTime = ref(0)
const duration = ref(0)
const shuffle = ref(false)
const repeat = ref(false)
const isFavorite = ref(false)
const isScanning = ref(false)

const useNativeAudio = Capacitor.isNativePlatform()
const AUDIO_ID = 'music_player'

// Progress tracking interval (needs to be managed globally)
let progressInterval: number | null = null

export function useMusicPlayer() {
  const currentTrack = computed(() => tracks.value[currentTrackIndex.value])

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60)
    const secs = Math.floor(seconds % 60)
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
  }

  const startProgressTracking = () => {
    if (progressInterval) {
      clearInterval(progressInterval)
    }

    progressInterval = window.setInterval(async () => {
      try {
        const result = await NativeAudio.getCurrentTime({ assetId: AUDIO_ID })
        currentTime.value = result.currentTime

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

          // Start tracking progress
          startProgressTracking()
        } catch (audioError) {
          console.error('NativeAudio error:', audioError)
          toast.error('Failed to play audio')
          isPlaying.value = false
          return
        }
      }
    } catch (error) {
      console.error('Error playing track:', error)
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
          } else {
            // Audio not loaded, load and play from beginning
            await playTrack(currentTrackIndex.value)
          }
        }
      }
    } catch (error) {
      console.error('Error toggling playback:', error)
      toast.error('Failed to toggle playback')
    }
  }

  const nextTrack = async () => {
    if (shuffle.value) {
      currentTrackIndex.value = Math.floor(Math.random() * tracks.value.length)
    } else {
      currentTrackIndex.value = (currentTrackIndex.value + 1) % tracks.value.length
    }
    await playTrack(currentTrackIndex.value)
  }

  const previousTrack = async () => {
    if (currentTime.value > 3) {
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
      currentTrackIndex.value = currentTrackIndex.value === 0
        ? tracks.value.length - 1
        : currentTrackIndex.value - 1
      await playTrack(currentTrackIndex.value)
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
        console.error('Error seeking:', error)
        toast.error('Failed to seek')
      }
    }
  }

  const toggleShuffle = () => {
    shuffle.value = !shuffle.value
  }

  const toggleRepeat = () => {
    repeat.value = !repeat.value
  }

  const toggleFavorite = () => {
    isFavorite.value = !isFavorite.value
  }

  // Restore playback state when component remounts
  const restorePlaybackState = async () => {
    if (useNativeAudio && isPlaying.value) {
      try {
        // Get current time from native audio
        const result = await NativeAudio.getCurrentTime({ assetId: AUDIO_ID })
        currentTime.value = result.currentTime

        // Restart progress tracking
        startProgressTracking()
      } catch {
        // Audio might not be loaded, reset state
        isPlaying.value = false
        currentTime.value = 0
      }
    }
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
    
    // Methods
    formatTime,
    playTrack,
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
  }
}
