import { ref, onMounted, onUnmounted } from 'vue'
import { Geolocation } from '@capacitor/geolocation'

interface WeatherData {
  temperature: number
  condition: string
  humidity: number
  windSpeed: number
}

const temperature = ref(27) // Default value
const weatherData = ref<WeatherData | null>(null)
const isLoading = ref(false)
const error = ref<string | null>(null)

let updateInterval: number | null = null

export function useWeather() {
  const fetchWeather = async () => {
    try {
      isLoading.value = true
      error.value = null

      // Get current position
      const position = await Geolocation.getCurrentPosition({
        enableHighAccuracy: false,
        timeout: 10000,
        maximumAge: 5000
      })

      const { latitude, longitude } = position.coords

      // Use Open-Meteo API (free, no API key required)
      const response = await fetch(
        `https://api.open-meteo.com/v1/forecast?latitude=${latitude}&longitude=${longitude}&current=temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code&timezone=auto`
      )

      if (!response.ok) {
        throw new Error('Failed to fetch weather data')
      }

      const data = await response.json()
      
      temperature.value = Math.round(data.current.temperature_2m)
      
      weatherData.value = {
        temperature: Math.round(data.current.temperature_2m),
        condition: getWeatherCondition(data.current.weather_code),
        humidity: data.current.relative_humidity_2m,
        windSpeed: Math.round(data.current.wind_speed_10m)
      }
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to fetch weather'
      // Keep the default temperature value on error
    } finally {
      isLoading.value = false
    }
  }

  const getWeatherCondition = (code: number): string => {
    // WMO Weather interpretation codes
    if (code === 0) return 'Clear'
    if (code <= 3) return 'Partly Cloudy'
    if (code <= 48) return 'Foggy'
    if (code <= 67) return 'Rainy'
    if (code <= 77) return 'Snowy'
    if (code <= 82) return 'Rainy'
    if (code <= 86) return 'Snowy'
    if (code <= 99) return 'Stormy'
    return 'Unknown'
  }

  const startWeatherUpdates = () => {
    // Fetch weather immediately
    fetchWeather()

    // Update weather every 30 minutes
    updateInterval = window.setInterval(() => {
      fetchWeather()
    }, 30 * 60 * 1000)
  }

  const stopWeatherUpdates = () => {
    if (updateInterval) {
      clearInterval(updateInterval)
      updateInterval = null
    }
  }

  onMounted(() => {
    startWeatherUpdates()
  })

  onUnmounted(() => {
    stopWeatherUpdates()
  })

  return {
    temperature,
    weatherData,
    isLoading,
    error,
    fetchWeather,
    startWeatherUpdates,
    stopWeatherUpdates
  }
}
