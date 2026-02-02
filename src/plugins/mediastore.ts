import { registerPlugin } from '@capacitor/core'

export interface MediaStorePlugin {
  checkPermissions(): Promise<{ granted: boolean }>
  scanAudioFiles(): Promise<{ files: AudioFile[] }>

  // Legacy methods (still work but less efficient)
  getAlbumArt(options: { audioId: number }): Promise<{ albumArt: string | null }>
  getAlbumArtBatch(options: { audioIds: number[] }): Promise<{ results: { id: number, albumArt: string | null }[] }>

  // New optimized thumbnail methods
  getAlbumThumbnail(options: { albumId: number, size?: number }): Promise<{
    uri: string | null
    source: 'memory' | 'disk' | 'none'
    loading?: boolean
  }>

  loadAlbumThumbnail(options: { albumId: number, audioId?: number, size?: number }): Promise<{
    uri: string | null
    status: 'loaded' | 'not_found' | 'already_loading'
  }>

  preloadAlbumThumbnails(options: {
    albums: Array<{ albumId: number, audioId?: number }>
    size?: number
  }): Promise<{
    results: Array<{ albumId: number, uri: string | null, source: string }>
  }>

  clearThumbnailCache(): Promise<{ success: boolean }>
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
  albumArt?: string // file:// URI or base64 data URL for album art
}

// Thumbnail sizes matching native constants
export const THUMBNAIL_SIZE_SMALL = 256  // For list views
export const THUMBNAIL_SIZE_LARGE = 512  // For player view

const MediaStore = registerPlugin<MediaStorePlugin>('MediaStore')

export default MediaStore
