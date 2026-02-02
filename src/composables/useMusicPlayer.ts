import { ref, computed, watch } from 'vue'
import { Capacitor } from '@capacitor/core'
import { useLocalStorage } from '@vueuse/core'
import BackgroundMusicPlayer from '../plugins/backgroundmusicplayer'
import { toast } from 'vue-sonner'
import { useMediaSession } from './useMediaSession'
import { useAlbumArtCache, type TrackWithAlbumArt } from './useAlbumArtCache'

const { getAlbumArt, loadAlbumArtsInBackground, isLoadingAlbumArt, loadingProgress, totalAlbumsToLoad } = useAlbumArtCache()

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

const tracks = useLocalStorage<Track[]>('dashride_music_tracks', [])
const currentTrackIndex = useLocalStorage('dashride_music_currentTrackIndex', 0)
const isPlaying = ref(false)
const currentTime = useLocalStorage('dashride_music_currentTime', 0)
const duration = ref(0)
const shuffle = useLocalStorage('dashride_music_shuffle', false)
const repeat = useLocalStorage('dashride_music_repeat', false)
const isFavorite = ref(false)
const isScanning = ref(false)
const queue = useLocalStorage<number[]>('dashride_music_queue', [])
const queuePosition = useLocalStorage('dashride_music_queuePosition', 0)

const useNativeAudio = Capacitor.isNativePlatform()

let progressInterval: number | null = null
let isChangingTrack = false

export function useMusicPlayer() {

  // Synchronize album art across all tracks of the same album
  const applyAlbumArt = (albumId: number, artUri: string) => {
    let changed = false
    tracks.value.forEach(t => {
      if (t.albumId === albumId && t.albumArt !== artUri) {
        t.albumArt = artUri
        changed = true
      }
    })
    return changed
  }

  watch(tracks, (newTracks) => {
    if (newTracks.length === 0) {
      queue.value = []
      currentTrackIndex.value = 0
      return
    }

    const validQueue = queue.value.filter(index => index >= 0 && index < newTracks.length)

    if (validQueue.length === 0 && newTracks.length > 0) {
      queue.value = Array.from({ length: newTracks.length }, (_, i) => i)
      queuePosition.value = 0
    } else if (validQueue.length !== queue.value.length) {
      queue.value = validQueue
    }

    if (queuePosition.value >= queue.value.length) {
      queuePosition.value = 0
    }

    if (currentTrackIndex.value >= newTracks.length) {
      currentTrackIndex.value = 0
    }
  }, { deep: true })

  const currentTrack = computed(() => {
    if (queue.value.length === 0) {
      return tracks.value[currentTrackIndex.value]
    }
    const trackIndex = queue.value[queuePosition.value]
    return tracks.value[trackIndex]
  })

  const queueTracks = computed(() => {
    return queue.value.slice(queuePosition.value).map(index => tracks.value[index])
  })

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60)
    const secs = Math.floor(seconds % 60)
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
  }

  const refreshQueue = () => {
    const totalTracks = tracks.value.length
    if (totalTracks === 0) {
      queue.value = []
      queuePosition.value = 0
      return
    }

    // If queue is empty or invalid, build a fresh queue
    if (queue.value.length === 0 || queue.value.length !== totalTracks) {
      if (shuffle.value) {
        // Create shuffled queue with current track first
        const allIndices = Array.from({ length: totalTracks }, (_, i) => i)
          .filter(i => i !== currentTrackIndex.value)

        // Fisher-Yates shuffle
        for (let i = allIndices.length - 1; i > 0; i--) {
          const j = Math.floor(Math.random() * (i + 1));
          [allIndices[i], allIndices[j]] = [allIndices[j], allIndices[i]]
        }

        queue.value = [currentTrackIndex.value, ...allIndices]
        queuePosition.value = 0
      } else {
        // Linear queue
        queue.value = Array.from({ length: totalTracks }, (_, i) => i)
        queuePosition.value = currentTrackIndex.value
      }
      return
    }

    // Queue exists - just reshuffle/unshuffle the REMAINING tracks (after current position)
    const currentTrack = queue.value[queuePosition.value]
    const playedTracks = queue.value.slice(0, queuePosition.value) // Already played
    const remainingTracks = queue.value.slice(queuePosition.value + 1) // Not yet played

    if (shuffle.value) {
      // Shuffle remaining tracks
      for (let i = remainingTracks.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [remainingTracks[i], remainingTracks[j]] = [remainingTracks[j], remainingTracks[i]]
      }
      queue.value = [...playedTracks, currentTrack, ...remainingTracks]
    } else {
      // Unshuffle: sort remaining tracks by their original index
      remainingTracks.sort((a, b) => a - b)
      queue.value = [...playedTracks, currentTrack, ...remainingTracks]
    }
    // queuePosition stays the same since we kept the structure
  }

  const removeFromQueue = (queueIndex: number) => {

    const actualIndex = queuePosition.value + queueIndex
    if (actualIndex >= queue.value.length) return

    queue.value.splice(actualIndex, 1)

    if (actualIndex < queuePosition.value) {
      queuePosition.value--
    }
  }

  const playFromQueue = async (queueIndex: number) => {

    const actualIndex = queuePosition.value + queueIndex
    if (actualIndex >= queue.value.length) return

    queuePosition.value = actualIndex

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
        const result = await BackgroundMusicPlayer.getCurrentTime()
        currentTime.value = result.currentTime

        updateCounter++
        if (updateCounter >= 4) {
          updatePosition(currentTime.value, duration.value)
          updateCounter = 0
        }

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
    }, 250)
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

      if (!track.albumArt) {
        const albumArt = await getAlbumArt(track)
        if (albumArt) {
          track.albumArt = albumArt
        }
      }

      if (useNativeAudio) {
        try {
          stopProgressTracking()
          await BackgroundMusicPlayer.stop()
        } catch {
          // No previous audio to stop
        }

        const result = await BackgroundMusicPlayer.preload({
          audioPath: track.uri
        })
        duration.value = result.duration

        await BackgroundMusicPlayer.play()
        isPlaying.value = true

        updateMediaSession()
        startProgressTracking()
      }
    } catch (error) {
      toast.error('Failed to play track')
      isPlaying.value = false
      stopProgressTracking()
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
          await BackgroundMusicPlayer.pause()
          isPlaying.value = false
          stopProgressTracking()
          updateMediaSession()
        } else {
          let audioLoaded = false
          try {
            await BackgroundMusicPlayer.getDuration()
            audioLoaded = true
          } catch {
            // Audio not loaded yet
          }

          if (audioLoaded) {
            await BackgroundMusicPlayer.play({ time: currentTime.value })
            isPlaying.value = true
            startProgressTracking()
            updateMediaSession()
          } else {
            await playTrack(currentTrackIndex.value)
          }
        }
      }
    } catch (error) {
      toast.error('Failed to toggle playback')
    }
  }

  const nextTrack = async () => {
    if (isChangingTrack) {
      return
    }

    isChangingTrack = true
    try {

      if (queue.value.length === 0) {
        refreshQueue()
      }

      queuePosition.value++

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
    if (isChangingTrack) {
      return
    }

    if (currentTime.value > 3) {
      if (useNativeAudio) {
        try {
          await BackgroundMusicPlayer.stop()
          await BackgroundMusicPlayer.play()
          currentTime.value = 0
        } catch {
          // Failed to restart track
        }
      }
    } else {
      isChangingTrack = true
      try {
        if (queue.value.length === 0) {
          refreshQueue()
        }

        if (queuePosition.value > 0) {
          queuePosition.value--
        } else {
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
        if (wasPlaying) {
          await BackgroundMusicPlayer.pause()
        }

        await BackgroundMusicPlayer.play({ time })

        if (wasPlaying) {
          isPlaying.value = true
          startProgressTracking()
          updateMediaSession()
        } else {
          await BackgroundMusicPlayer.pause()
          isPlaying.value = false
          updateMediaSession()
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

  const restorePlaybackState = async () => {
    if (tracks.value.length > 0 && currentTrackIndex.value >= 0 && currentTrackIndex.value < tracks.value.length) {
      if (queue.value.length === 0) {
        refreshQueue()
      }

      if (queuePosition.value >= queue.value.length) {
        queuePosition.value = 0
      }

      // Always load current track's album art immediately
      const track = tracks.value[currentTrackIndex.value]
      if (track && track.uri) {
        // Load album art first (prioritize current track)
        if (!track.albumArt) {
          try {
            const albumArt = await getAlbumArt(track)
            if (albumArt) {
              applyAlbumArt(track.albumId || 0, albumArt)
            }
          } catch (e) {
            console.error('Failed to load current track album art:', e)
          }
        }

        // Restore playback position if we had significant progress
        const savedTime = currentTime.value
        if (savedTime > 3) {
          try {
            if (useNativeAudio) {
              const result = await BackgroundMusicPlayer.preload({
                audioPath: track.uri
              })
              duration.value = result.duration

              updateMetadata(currentTrack.value || null)
              updatePlaybackState('paused')
            }
          } catch {
            // Failed to restore, reset to beginning
            currentTime.value = 0
          }
        }
      }
    }
  }

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
      const newTime = timeOrOffset < 0 || timeOrOffset > duration.value
        ? Math.max(0, Math.min(duration.value, currentTime.value + timeOrOffset))
        : timeOrOffset
      await seekTo(newTime)
    }
  })

  const updateMediaSession = () => {
    updateMetadata(currentTrack.value || null)
    updatePlaybackState(isPlaying.value ? 'playing' : 'paused')
    updatePosition(currentTime.value, duration.value)
  }

  const selectAndPlayTrack = async (trackIndex: number) => {
    if (trackIndex < 0 || trackIndex >= tracks.value.length) return

    currentTrackIndex.value = trackIndex

    // Force a complete queue rebuild by clearing it first
    // This ensures the queue is regenerated with all tracks
    queue.value = []
    queuePosition.value = 0

    refreshQueue()

    await playTrack(currentTrackIndex.value)
  }

  const setCustomQueue = async (trackIndices: number[], startIndex: number = 0) => {
    if (trackIndices.length === 0) return

    queue.value = trackIndices
    queuePosition.value = startIndex

    currentTrackIndex.value = trackIndices[startIndex]

    await playTrack(currentTrackIndex.value)
  }

  return {
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

    // Album art loading state
    isLoadingAlbumArt,
    loadingProgress,
    totalAlbumsToLoad,

    formatTime,
    playTrack,
    selectAndPlayTrack,
    setCustomQueue,
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
    loadAlbumArtsInBackground,
    applyAlbumArt,
  }
}
