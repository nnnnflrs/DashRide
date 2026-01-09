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
          
          console.log('Track ended, playing next...')
          
          if (repeat.value) {
            await playTrack(currentTrackIndex.value)
          } else {
            await nextTrack()
          }
        }
      } catch (error) {
        console.error('Error getting current time:', error)
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
      console.error('No track at index:', index)
      return
    }

    try {
      currentTrackIndex.value = index
      currentTime.value = 0
      
      if (!track.uri) {
        toast.error('No audio source available')
        console.error('Track has no URI:', track)
        return
      }
      
      console.log(`Playing track: ${track.title}, URI: ${track.uri}`)
      console.log(`Track id: ${track.id}, existing albumArt: ${track.albumArt ? 'yes' : 'no'}`)
      
      // Load album art on-demand for the current track using audio file ID
      if (track.id && !track.albumArt) {
        try {
          console.log(`Loading album art for audio ID: ${track.id}`)
          const result = await MediaStore.getAlbumArt({ audioId: track.id })
          console.log('getAlbumArt result:', result)
          if (result.albumArt) {
            track.albumArt = result.albumArt
            console.log('Album art loaded successfully, length:', result.albumArt.length)
          } else {
            console.log('No album art returned from getAlbumArt')
          }
        } catch (error) {
          console.error('Failed to load album art:', error)
        }
      } else {
        console.log(`Skipping album art load - id: ${track.id}, albumArt exists: ${!!track.albumArt}`)
      }
      
      if (useNativeAudio) {
        // Request audio focus
        try {
          if (BackgroundAudio && typeof BackgroundAudio.requestAudioFocus === 'function') {
            await BackgroundAudio.requestAudioFocus()
            console.log('Audio focus granted')
          }
        } catch (error) {
          console.warn('Audio focus not available or failed:', error)
        }
        
        // Stop and unload previous track
        try {
          stopProgressTracking()
          await NativeAudio.stop({ assetId: AUDIO_ID })
          await NativeAudio.unload({ assetId: AUDIO_ID })
        } catch (e) {
          console.log('No previous audio to stop')
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
          
          console.log(`Native audio playing, duration: ${duration.value}s`)
        } catch (audioError) {
          console.error('NativeAudio error:', audioError)
          toast.error('Failed to play audio')
          isPlaying.value = false
          return
        }
      }
    } catch (error) {
      console.error('Error playing track:', error)
      toast.error(`Failed to play track`)
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
          console.log(`Paused at ${currentTime.value}s`)
        } else {
          // Check if audio is already loaded by trying to get duration
          let audioLoaded = false
          try {
            await NativeAudio.getDuration({ assetId: AUDIO_ID })
            audioLoaded = true
          } catch (error) {
            console.log('Audio not loaded yet')
          }

          if (audioLoaded) {
            // Audio is loaded, resume from current position
            console.log(`Resuming from ${currentTime.value}s`)
            await NativeAudio.play({ assetId: AUDIO_ID, time: currentTime.value })
            isPlaying.value = true
            startProgressTracking()
          } else {
            // Audio not loaded, load and play from beginning
            console.log('Loading and playing track...')
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
        } catch (error) {
          console.error('Error restarting track:', error)
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
        
        console.log(`Seeked to ${time}s`)
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
      console.log('Restoring playback state...')
      try {
        // Get current time from native audio
        const result = await NativeAudio.getCurrentTime({ assetId: AUDIO_ID })
        currentTime.value = result.currentTime
        
        // Restart progress tracking
        startProgressTracking()
        
        console.log(`Playback state restored: ${currentTime.value}s`)
      } catch (error) {
        console.error('Error restoring playback state:', error)
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
