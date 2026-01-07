# DashRide - Motorcycle Dashboard App

A cross-platform motorcycle dashboard application built with Vue 3, Ionic, and Capacitor.

## Features

- **Real-time Speedometer** with dynamic color zones (green → yellow → orange → red)
- **Google Maps Navigation** with current location tracking
- **Device Sensors Integration**
  - GPS location
  - Battery status
  - Network connectivity
  - WiFi and Bluetooth status
- **Landscape Fullscreen Mode** optimized for motorcycle mounting
- **Cross-platform Support** for Android and iOS

## Tech Stack

- **Vue 3** with Composition API
- **Ionic Vue** for mobile UI components
- **Capacitor** for native device functionality
- **Google Maps JavaScript API** for navigation
- **TypeScript** for type safety
- **Vite** for fast development and building

## Prerequisites

- Node.js (v18 or higher)
- Bun package manager
- Android Studio (for Android development)
- Xcode (for iOS development, macOS only)

## Installation

1. Clone the repository:
```bash
git clone https://github.com/yourusername/DashRide.git
cd DashRide
```

2. Install dependencies:
```bash
bun install
```

3. Create a `.env` file in the root directory:
```env
VITE_GOOGLE_MAPS_API_KEY=your_google_maps_api_key_here
```

## Development

### Web Development
```bash
bun run dev
```

### Android Development
```bash
# Sync changes to Android
bun run sync

# Run on Android device/emulator
bun run android
```

### iOS Development
```bash
# Sync changes to iOS
bun run sync

# Run on iOS device/simulator
bun run ios
```

## Building for Production

```bash
# Build web assets
bun run build

# Sync to native platforms
bun run sync
```

## Project Structure

```
project/
├── src/
│   ├── app/
│   │   ├── components/      # Vue components
│   │   │   ├── SemiCircularGauge.vue    # Speedometer
│   │   │   ├── NavigationMap.vue        # Google Maps
│   │   │   ├── StatusBar.vue            # Device status
│   │   │   └── ...
│   │   └── App.vue          # Main app component
│   ├── styles/              # Global styles
│   └── main.ts              # App entry point
├── android/                 # Android native project
├── ios/                     # iOS native project
└── capacitor.config.ts      # Capacitor configuration
```

## Features in Detail

### Speedometer
- Semi-circular gauge with dynamic arc
- Color changes based on speed:
  - 0-40%: Green (safe)
  - 40-60%: Yellow (caution)
  - 60-80%: Orange (warning)
  - 80-100%: Red (danger)
- **Test Mode**: Double-click the speedometer to activate automatic speed cycling

### Navigation
- Real-time GPS tracking
- Google Maps integration
- Rotate and tilt controls enabled
- POI (Points of Interest) labels visible

### Device Status
- Real-time battery level and charging status
- Network type (WiFi, Cellular, None)
- Connection status indicators
- Current time display

## Configuration

### Google Maps API Key
1. Get an API key from [Google Cloud Console](https://console.cloud.google.com/)
2. Enable the following APIs:
   - Maps JavaScript API
   - Geolocation API
3. Add the key to your `.env` file

### Permissions
The app requires the following permissions:
- **Location**: For GPS tracking and navigation
- **Network State**: For connectivity status

## Pushing to GitHub

1. Create a new repository on GitHub

2. Add the remote repository:
```bash
git remote add origin https://github.com/yourusername/dashride.git
```

3. Push to GitHub:
```bash
git branch -M main
git push -u origin main
```

## Security Notes

- Never commit the `.env` file (already in `.gitignore`)
- Keep your Google Maps API key secure
- Use environment-specific API keys for development and production
- The `.gitignore` file protects:
  - Environment variables (`.env`)
  - API keys and certificates
  - Build outputs
  - Native platform build files
  - Node modules

## License

MIT

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
