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

/**
 * Composable for integrating with the Media Session API
 * This enables hardware controls (earphones, intercom, car Bluetooth) to control playback
 */
export function useMediaSession(handlers: MediaSessionHandlers) {
  const isNative = Capacitor.isNativePlatform()

  // Set up native media session listeners on Android/iOS
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

  // Check if Web Media Session API is available (for web)
  const hasWebMediaSession = !isNative && 'mediaSession' in navigator
  const mediaSession = hasWebMediaSession ? navigator.mediaSession : null

  /**
   * Update the currently playing track metadata
   * This shows up in lock screen, notification shade, car displays, etc.
   */
  const updateMetadata = (track: Track | null) => {
    if (isNative) {
      // Use native Android MediaSession
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
      // Use web Media Session API
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

  /**
   * Update the playback state (playing, paused, none)
   */
  const updatePlaybackState = (state: 'playing' | 'paused' | 'none', position = 0) => {
    if (isNative) {
      // Use native Android MediaSession
      MediaSession.updatePlaybackState({
        state: state === 'none' ? 'paused' : state,
        position
      }).catch(() => {})
    } else if (mediaSession) {
      // Use web Media Session API
      try {
        mediaSession.playbackState = state
      } catch (error) {
        // Ignore errors
      }
    }
  }

  /**
   * Update the current playback position
   * Required for accurate seek bar display on lock screen
   */
  const updatePosition = (currentTime: number, duration: number, playbackRate = 1.0) => {
    if (isNative) {
      // Position is updated via updatePlaybackState on native
      MediaSession.updatePlaybackState({
        state: 'playing',
        position: currentTime
      }).catch(() => {})
    } else if (mediaSession) {
      // Use web Media Session API
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

  /**
   * Set up action handlers for hardware controls (Web only)
   */
  const setActionHandlers = () => {
    if (!mediaSession) return

    try {
      // Play action
      mediaSession.setActionHandler('play', () => {
        handlers.onPlay()
      })

      // Pause action
      mediaSession.setActionHandler('pause', () => {
        handlers.onPause()
      })

      // Next track
      mediaSession.setActionHandler('nexttrack', () => {
        handlers.onNext()
      })

      // Previous track
      mediaSession.setActionHandler('previoustrack', () => {
        handlers.onPrevious()
      })

      // Seek forward
      mediaSession.setActionHandler('seekforward', (details) => {
        const skipTime = details.seekOffset || 10
        handlers.onSeek(skipTime)
      })

      // Seek backward
      mediaSession.setActionHandler('seekbackward', (details) => {
        const skipTime = -(details.seekOffset || 10)
        handlers.onSeek(skipTime)
      })

      // Seek to specific position
      mediaSession.setActionHandler('seekto', (details) => {
        if (details.seekTime !== null && details.seekTime !== undefined) {
          handlers.onSeek(details.seekTime)
        }
      })

      // Stop playback
      mediaSession.setActionHandler('stop', () => {
        handlers.onPause()
      })
    } catch (error) {
      // Ignore errors
    }
  }

  // Set up web handlers on initialization
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

/**
 * Helper function to set up automatic media session synchronization
 */
export function setupMediaSessionSync(
  currentTrack: () => Track | undefined,
  isPlaying: () => boolean,
  currentTime: () => number,
  duration: () => number,
  handlers: MediaSessionHandlers
) {
  const { updateMetadata, updatePlaybackState, updatePosition } = useMediaSession(handlers)

  // Watch for track changes
  watch(() => currentTrack(), (track) => {
    updateMetadata(track || null)
  }, { immediate: true })

  // Watch for playback state changes
  watch(() => isPlaying(), (playing) => {
    updatePlaybackState(playing ? 'playing' : 'paused')
  }, { immediate: true })

  // Update position periodically (every 1 second)
  let positionInterval: number | null = null

  watch(() => isPlaying(), (playing) => {
    if (playing) {
      // Update position every second while playing
      positionInterval = window.setInterval(() => {
        updatePosition(currentTime(), duration())
      }, 1000)
    } else {
      // Clear interval when paused
      if (positionInterval) {
        clearInterval(positionInterval)
        positionInterval = null
      }
      // Update position one last time when paused
      updatePosition(currentTime(), duration())
    }
  }, { immediate: true })

  return {
    updateMetadata,
    updatePlaybackState,
    updatePosition
  }
}
