import { registerPlugin } from '@capacitor/core'

export interface MediaSessionPlugin {
  /**
   * Update the media session metadata (track info, album art)
   */
  updateMetadata(options: {
    title: string
    artist: string
    album: string
    albumArt?: string
    duration: number
  }): Promise<void>

  /**
   * Update the playback state (playing, paused)
   */
  updatePlaybackState(options: {
    state: 'playing' | 'paused'
    position: number
  }): Promise<void>

  /**
   * Hide the media notification
   */
  hideNotification(): Promise<void>

  /**
   * Listen for media session actions (play, pause, next, previous, seek)
   */
  addListener(
    eventName: 'mediaSessionAction',
    listenerFunc: (event: { action: string; data?: { position?: number } }) => void
  ): Promise<{ remove: () => void }>
}

const MediaSession = registerPlugin<MediaSessionPlugin>('MediaSession', {
  web: () => ({
    updateMetadata: async () => {},
    updatePlaybackState: async () => {},
    hideNotification: async () => {},
    addListener: async () => ({ remove: () => {} })
  })
})

export default MediaSession
