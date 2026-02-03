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
git clone https://github.com/nnnnflrs/DashRide.git
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

### Screenshots

![1](https://github.com/user-attachments/assets/5e638223-5123-4a34-89b1-44cfa54609f0)
![8](https://github.com/user-attachments/assets/46c972b8-c05e-4078-a904-5994ac2b74f1)
![9](https://github.com/user-attachments/assets/0cfc590a-e109-4967-b187-bc9d0f5a6dbd)
![2](https://github.com/user-attachments/assets/8ec8fb86-0335-43cb-8232-ab82920accd4)
![3](https://github.com/user-attachments/assets/dfd1bc19-c5ad-4f91-9636-6cc8d0bc1324)
![4](https://github.com/user-attachments/assets/7a0690cd-3771-4a88-b4d9-4b23effe96a5)
![5](https://github.com/user-attachments/assets/d500a98d-cb62-4e79-b680-82b96e7e5f55)
![6](https://github.com/user-attachments/assets/9a8158a1-343c-4d7b-a327-2285272f7a2b)
![7](https://github.com/user-attachments/assets/2e8e9a10-30dd-4369-8fac-1750bbb80174)


### Live Demo
https://play.google.com/store/apps/details?id=com.motorcycle.dashride.ph

## License

MIT

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
