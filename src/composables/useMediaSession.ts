import { watch } from 'vue'
import { Capacitor } from '@capacitor/core'
import MediaSession from '../plugins/mediasession'
import type { Track } from './useMusicPlayer'

export interface MediaSessionHandlers {
  onPlay: () => void
  onPause: () => void
  onNext: () => void
  onPrevious: () => void
  onSeek: (time: number) => void
}

export function useMediaSession(handlers: MediaSessionHandlers) {
  const isNative = Capacitor.isNativePlatform()

  if (isNative) {
    MediaSession.addListener('mediaSessionAction', (event) => {
      switch (event.action) {
        case 'play':
          handlers.onPlay()
          break
        case 'pause':
          handlers.onPause()
          break
        case 'next':
          handlers.onNext()
          break
        case 'previous':
          handlers.onPrevious()
          break
        case 'seek':
          if (event.data?.position !== undefined) {
            handlers.onSeek(event.data.position)
          }
          break
        case 'stop':
          handlers.onPause()
          break
      }
    })
  }

  const hasWebMediaSession = !isNative && 'mediaSession' in navigator
  const mediaSession = hasWebMediaSession ? navigator.mediaSession : null

  const updateMetadata = (track: Track | null) => {
    if (isNative) {
      if (track) {
        MediaSession.updateMetadata({
          title: track.title || 'Unknown Title',
          artist: track.artist || 'Unknown Artist',
          album: track.album || 'Unknown Album',
          albumArt: track.albumArt,
          duration: track.duration || 0
        }).catch(() => {})
      }
    } else if (mediaSession) {
      if (!track) {
        mediaSession.metadata = null
        return
      }

      try {
        const artwork = track.albumArt
          ? [{ src: track.albumArt, sizes: '512x512', type: 'image/jpeg' }]
          : []

        mediaSession.metadata = new MediaMetadata({
          title: track.title || 'Unknown Title',
          artist: track.artist || 'Unknown Artist',
          album: track.album || 'Unknown Album',
          artwork
        })

      } catch (error) {
        // Ignore errors
      }
    }
  }

  const updatePlaybackState = (state: 'playing' | 'paused' | 'none', position = 0) => {
    if (isNative) {
      MediaSession.updatePlaybackState({
        state: state === 'none' ? 'paused' : state,
        position
      }).catch(() => {})
    } else if (mediaSession) {
      try {
        mediaSession.playbackState = state
      } catch (error) {
        // Ignore errors
      }
    }
  }

  const updatePosition = (currentTime: number, duration: number, playbackRate = 1.0) => {
    if (isNative) {
    } else if (mediaSession) {
      try {
        if ('setPositionState' in mediaSession) {
          mediaSession.setPositionState({
            duration: duration || 0,
            playbackRate,
            position: Math.min(currentTime, duration || 0)
          })
        }
      } catch (error) {
        // Ignore errors
      }
    }
  }

  const setActionHandlers = () => {
    if (!mediaSession) return

    try {
      mediaSession.setActionHandler('play', () => {
        handlers.onPlay()
      })

      mediaSession.setActionHandler('pause', () => {
        handlers.onPause()
      })

      mediaSession.setActionHandler('nexttrack', () => {
        handlers.onNext()
      })

      mediaSession.setActionHandler('previoustrack', () => {
        handlers.onPrevious()
      })

      mediaSession.setActionHandler('seekforward', (details) => {
        const skipTime = details.seekOffset || 10
        handlers.onSeek(skipTime)
      })

      mediaSession.setActionHandler('seekbackward', (details) => {
        const skipTime = -(details.seekOffset || 10)
        handlers.onSeek(skipTime)
      })

      mediaSession.setActionHandler('seekto', (details) => {
        if (details.seekTime !== null && details.seekTime !== undefined) {
          handlers.onSeek(details.seekTime)
        }
      })

      mediaSession.setActionHandler('stop', () => {
        handlers.onPause()
      })
    } catch (error) {
      // Ignore errors
    }
  }

  if (!isNative) {
    setActionHandlers()
  }

  return {
    updateMetadata,
    updatePlaybackState,
    updatePosition,
    setActionHandlers
  }
}

export function setupMediaSessionSync(
  currentTrack: () => Track | undefined,
  isPlaying: () => boolean,
  currentTime: () => number,
  duration: () => number,
  handlers: MediaSessionHandlers
) {
  const { updateMetadata, updatePlaybackState, updatePosition } = useMediaSession(handlers)

  watch(() => currentTrack(), (track) => {
    updateMetadata(track || null)
  }, { immediate: true })

  watch(() => isPlaying(), (playing) => {
    updatePlaybackState(playing ? 'playing' : 'paused')
  }, { immediate: true })

  let positionInterval: number | null = null

  watch(() => isPlaying(), (playing) => {
    if (playing) {
      positionInterval = window.setInterval(() => {
        updatePosition(currentTime(), duration())
      }, 1000)
    } else {
      if (positionInterval) {
        clearInterval(positionInterval)
        positionInterval = null
      }
      updatePosition(currentTime(), duration())
    }
  }, { immediate: true })

  return {
    updateMetadata,
    updatePlaybackState,
    updatePosition
  }
}
