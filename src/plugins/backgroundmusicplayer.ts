import { registerPlugin } from '@capacitor/core'

export interface BackgroundMusicPlayerPlugin {
  preload(options: { audioPath: string }): Promise<{ duration: number }>
  play(options?: { time?: number }): Promise<void>
  pause(): Promise<void>
  stop(): Promise<void>
  getCurrentTime(): Promise<{ currentTime: number }>
  getDuration(): Promise<{ duration: number }>
  setVolume(options: { volume: number }): Promise<void>
  isPlaying(): Promise<{ isPlaying: boolean }>
  addListener(
    eventName: 'playbackEvent',
    listenerFunc: (event: { event: string }) => void
  ): Promise<{ remove: () => void }>
}

const BackgroundMusicPlayer = registerPlugin<BackgroundMusicPlayerPlugin>('BackgroundMusicPlayer')

export default BackgroundMusicPlayer
