import { registerPlugin } from '@capacitor/core'

export interface BackgroundAudioPlugin {
  requestAudioFocus(): Promise<void>
  abandonAudioFocus(): Promise<void>
}

const BackgroundAudio = registerPlugin<BackgroundAudioPlugin>('BackgroundAudio')

export default BackgroundAudio
