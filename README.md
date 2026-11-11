# Gallery App

A modern, production-quality Android photo and video gallery application built with Kotlin and Jetpack Compose.

## Features

- **Browse photos and videos** from device MediaStore
- **Fullscreen media viewer** with pinch-zoom, swipe navigation, and video playback (Media3/ExoPlayer)
- **Albums** — device albums, user-created albums, and smart albums (Videos, Favorites, Screenshots, GIFs, Large Files, Recently Added)
- **Favorites** and **Hidden** media (app-level metadata)
- **Recently Deleted** with restore support
- **Search** by filename, album, date, media type
- **Sort & filter** (newest/oldest, name, size, duration; photos/videos/GIFs/favorites/screenshots)
- **Multi-select** with share, favorite, hide, delete actions
- **Share** via Android Sharesheet
- **Settings** — theme (light/dark/system), dynamic color, grid density, playback options
- **Permissions** handled correctly for Android 10–15+
- **Offline-first** — no network required
- **RTL support** with English and Persian (Farsi) localization
- **Material 3** design with edge-to-edge UI

## Tech Stack

- Kotlin
- Jetpack Compose + Material 3
- Navigation Compose
- Room (favorites, hidden, albums, deleted)
- MediaStore
- Coil (image loading)
- Media3 / ExoPlayer (video)
- DataStore Preferences
- Coroutines + Flow
- Accompanist Permissions

## Requirements

- Android Studio Ladybug or newer
- JDK 17
- minSdk 29 (Android 10)
- targetSdk 35

## Build

```bash
./gradlew assembleDebug
```

Or open the project in Android Studio and run on a device/emulator.

## Architecture

```
UI (Compose) → ViewModel → GalleryRepository → MediaStore / Room / Preferences
```

## Privacy

All media stays on device. No uploads, no analytics, no internet permission.

## License

This project is provided as a complete reference implementation.
