import { ref } from 'vue'
import { Capacitor } from '@capacitor/core'
import MediaStore, { THUMBNAIL_SIZE_SMALL, THUMBNAIL_SIZE_LARGE } from '../plugins/mediastore'

// LRU Cache implementation for in-memory caching
class LRUCache<K, V> {
  private cache = new Map<K, V>()
  private maxSize: number

  constructor(maxSize: number) {
    this.maxSize = maxSize
  }

  get(key: K): V | undefined {
    if (!this.cache.has(key)) return undefined

    // Move to end (most recently used)
    const value = this.cache.get(key)!
    this.cache.delete(key)
    this.cache.set(key, value)
    return value
  }

  set(key: K, value: V): void {
    // Delete existing to refresh position
    if (this.cache.has(key)) {
      this.cache.delete(key)
    }

    // Evict oldest if at capacity
    if (this.cache.size >= this.maxSize) {
      const oldestKey = this.cache.keys().next().value
      if (oldestKey !== undefined) {
        this.cache.delete(oldestKey)
      }
    }

    this.cache.set(key, value)
  }

  has(key: K): boolean {
    return this.cache.has(key)
  }

  clear(): void {
    this.cache.clear()
  }

  keys(): IterableIterator<K> {
    return this.cache.keys()
  }
}

// Module-level state
const isLoadingAlbumArt = ref(false)
const loadingProgress = ref(0)
const totalAlbumsToLoad = ref(0)
let abortLoading = false

// LRU memory cache (50 albums max, keyed by "albumId_size")
const memoryCache = new LRUCache<string, string>(50)

// Track which albums are currently being loaded
const loadingAlbums = new Set<string>()

export interface TrackWithAlbumArt {
  id?: number
  albumId?: number
  albumArt?: string
}

export function useAlbumArtCache() {

  /**
   * Get cached album thumbnail URI (instant if in memory)
   * Returns null if not cached, caller should use loadAlbumThumbnail
   */
  const getCachedThumbnail = (albumId: number, size: number = THUMBNAIL_SIZE_SMALL): string | null => {
    const cacheKey = `${albumId}_${size}`
    return memoryCache.get(cacheKey) || null
  }

  /**
   * Get album thumbnail with caching
   * Flow: Memory → Disk → Native load
   */
  const getAlbumThumbnail = async (
    albumId: number,
    audioId?: number,
    size: number = THUMBNAIL_SIZE_SMALL
  ): Promise<string | null> => {
    if (!Capacitor.isNativePlatform()) return null

    const cacheKey = `${albumId}_${size}`

    // 1. Check memory cache (instant)
    const memCached = memoryCache.get(cacheKey)
    if (memCached) return memCached

    // 2. Check native cache (disk)
    try {
      const { uri, source } = await MediaStore.getAlbumThumbnail({ albumId, size })

      if (uri) {
        // Convert to web-usable URI
        const webUri = Capacitor.convertFileSrc(uri)
        memoryCache.set(cacheKey, webUri)
        return webUri
      }

      // 3. Not cached, need to load
      if (source === 'none') {
        // Skip if already loading
        if (loadingAlbums.has(cacheKey)) return null

        loadingAlbums.add(cacheKey)

        try {
          const result = await MediaStore.loadAlbumThumbnail({ albumId, audioId, size })

          if (result.uri) {
            const webUri = Capacitor.convertFileSrc(result.uri)
            memoryCache.set(cacheKey, webUri)
            return webUri
          }
        } finally {
          loadingAlbums.delete(cacheKey)
        }
      }
    } catch (error) {
      console.error('Error getting album thumbnail:', error)
    }

    return null
  }

  /**
   * Get album art for a track (backward compatible)
   */
  const getAlbumArt = async (track: TrackWithAlbumArt): Promise<string | null> => {
    if (track.albumArt) return track.albumArt
    if (!track.albumId) return null

    return getAlbumThumbnail(track.albumId, track.id, THUMBNAIL_SIZE_SMALL)
  }

  /**
   * Load album art for a track and update it in place
   */
  const loadAlbumArtForTrack = async (track: TrackWithAlbumArt): Promise<void> => {
    if (track.albumArt) return

    const albumArt = await getAlbumArt(track)
    if (albumArt) {
      track.albumArt = albumArt
    }
  }

  /**
   * Preload thumbnails for visible tracks (optimized batch operation)
   */
  const preloadVisibleThumbnails = async (
    tracks: TrackWithAlbumArt[],
    size: number = THUMBNAIL_SIZE_SMALL,
    onTrackUpdated?: (track: TrackWithAlbumArt) => void
  ): Promise<void> => {
    if (!Capacitor.isNativePlatform()) return

    // Build list of albums to preload (unique, not already cached)
    const albumsToLoad: Array<{ albumId: number, audioId: number | undefined }> = []
    const seenAlbums = new Set<number>()

    for (const track of tracks) {
      if (!track.albumId) continue
      if (seenAlbums.has(track.albumId)) continue
      if (memoryCache.has(`${track.albumId}_${size}`)) continue

      seenAlbums.add(track.albumId)
      albumsToLoad.push({ albumId: track.albumId, audioId: track.id })
    }

    if (albumsToLoad.length === 0) return

    try {
      const response = await MediaStore.preloadAlbumThumbnails({
        albums: albumsToLoad,
        size
      })

      // Update memory cache and tracks
      for (const result of response.results) {
        if (result.uri) {
          const webUri = Capacitor.convertFileSrc(result.uri)
          memoryCache.set(`${result.albumId}_${size}`, webUri)

          // Update all tracks with this album
          for (const track of tracks) {
            if (track.albumId === result.albumId && !track.albumArt) {
              track.albumArt = webUri
              onTrackUpdated?.(track)
            }
          }
        }
      }
    } catch (error) {
      console.error('Error preloading thumbnails:', error)
    }
  }

  /**
   * Batch load album arts in background (backward compatible)
   */
  const loadAlbumArtsInBackground = async (
    tracks: TrackWithAlbumArt[],
    options: {
      initialBatchSize?: number
      batchSize?: number
      delayBetweenBatches?: number
      onProgress?: (loaded: number, total: number) => void
      onTrackUpdated?: (track: TrackWithAlbumArt) => void
    } = {}
  ): Promise<void> => {
    const {
      initialBatchSize = 50,
      batchSize = 20,
      delayBetweenBatches = 50,
      onProgress,
      onTrackUpdated
    } = options

    if (!Capacitor.isNativePlatform()) return
    if (isLoadingAlbumArt.value) {
      console.log('Album art loading already in progress')
      return
    }

    abortLoading = false
    isLoadingAlbumArt.value = true
    loadingProgress.value = 0

    try {
      // Build unique album map
      const albumMap = new Map<number, TrackWithAlbumArt>()
      for (const track of tracks) {
        if (!track.albumId || !track.id) continue
        if (track.albumArt) continue
        if (memoryCache.has(`${track.albumId}_${THUMBNAIL_SIZE_SMALL}`)) continue
        if (albumMap.has(track.albumId)) continue
        albumMap.set(track.albumId, track)
      }

      const uniqueAlbums = Array.from(albumMap.values())
      totalAlbumsToLoad.value = uniqueAlbums.length
      console.log(`Need to load ${uniqueAlbums.length} unique album arts`)

      if (uniqueAlbums.length === 0) {
        isLoadingAlbumArt.value = false
        return
      }

      // Process in batches
      let processed = 0

      const processBatch = async (batch: TrackWithAlbumArt[]) => {
        if (abortLoading) return

        const albumsData = batch.map(t => ({
          albumId: t.albumId!,
          audioId: t.id
        }))

        try {
          const response = await MediaStore.preloadAlbumThumbnails({
            albums: albumsData,
            size: THUMBNAIL_SIZE_SMALL
          })

          for (const result of response.results) {
            if (result.uri) {
              const webUri = Capacitor.convertFileSrc(result.uri)
              memoryCache.set(`${result.albumId}_${THUMBNAIL_SIZE_SMALL}`, webUri)

              // Update all tracks with this album
              for (const t of tracks) {
                if (t.albumId === result.albumId && !t.albumArt) {
                  t.albumArt = webUri
                  onTrackUpdated?.(t)
                }
              }
            }
          }
        } catch (error) {
          console.error('Batch loading error:', error)
        }

        processed += batch.length
        loadingProgress.value = processed
        onProgress?.(processed, totalAlbumsToLoad.value)
      }

      // Load initial batch immediately
      const initialBatch = uniqueAlbums.slice(0, initialBatchSize)
      await processBatch(initialBatch)

      // Load remaining in smaller batches with delays
      const remaining = uniqueAlbums.slice(initialBatchSize)
      for (let i = 0; i < remaining.length; i += batchSize) {
        if (abortLoading) break
        await new Promise(resolve => setTimeout(resolve, delayBetweenBatches))
        const batch = remaining.slice(i, i + batchSize)
        await processBatch(batch)
      }

      console.log(`Finished loading album arts. Processed: ${processed}`)
    } catch (error) {
      console.error('Error in background album art loading:', error)
    } finally {
      isLoadingAlbumArt.value = false
    }
  }

  /**
   * Stop background loading
   */
  const stopBackgroundLoading = () => {
    abortLoading = true
  }

  /**
   * Clear all caches
   */
  const clearCache = async (): Promise<void> => {
    memoryCache.clear()
    loadingAlbums.clear()

    if (Capacitor.isNativePlatform()) {
      try {
        await MediaStore.clearThumbnailCache()
      } catch (error) {
        console.error('Error clearing native cache:', error)
      }
    }
  }

  return {
    // State
    isLoadingAlbumArt,
    loadingProgress,
    totalAlbumsToLoad,

    // Size constants
    THUMBNAIL_SIZE_SMALL,
    THUMBNAIL_SIZE_LARGE,

    // Methods
    getCachedThumbnail,
    getAlbumThumbnail,
    getAlbumArt,
    loadAlbumArtForTrack,
    preloadVisibleThumbnails,
    loadAlbumArtsInBackground,
    stopBackgroundLoading,
    clearCache,
  }
}
