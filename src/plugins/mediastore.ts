import { registerPlugin } from '@capacitor/core'

export interface MediaStorePlugin {
  checkPermissions(): Promise<{ granted: boolean }>
  scanAudioFiles(): Promise<{ files: AudioFile[] }>
  getAlbumArt(options: { audioId: number }): Promise<{ albumArt: string | null }>
}

export interface AudioFile {
  id: number
  name: string
  title: string
  artist: string
  album: string
  albumId: number // for lazy loading album art
  duration: number // in milliseconds
  uri: string // content:// URI
  albumArt?: string // base64 data URL for album art (loaded on-demand)
}

const MediaStore = registerPlugin<MediaStorePlugin>('MediaStore')

export default MediaStore
