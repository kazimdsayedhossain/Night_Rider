# Night Rider — Deliver It 3D (Android Edition)

A high-performance 3D courier delivery arcade game built for Android in Java using **LibGDX** and **gdx-gltf** (PBR glTF 2.0 rendering).

## Overview

Deliver parcels through a bustling, living open-world city. Navigate through dynamic AI traffic, cross stone-arch river bridges, weave past park lakes and boulder gardens, and deliver parcels on time.

---

## Key Features

- **PBR glTF 2.0 Rendering Pipeline**:
  - Physically Based Rendering via `gdx-gltf` with metallic-roughness workflow.
  - Procedural night IBL (Image-Based Lighting) providing rich 360° diffuse ambient fill and specular env reflections.
  - Multi-source directional moonlight with soft wrap-around fill.

- **Rainy Night Atmosphere & Lighting**:
  - Wet-look asphalt with patchy roughness and specular sheen.
  - Organic road puddles and light-smear reflection streaks.
  - Warm golden street lamps with soft luminaires and ground light pools.
  - Vehicle headlights with smooth 4-way feathered edge fade and dynamic spot illumination.

- **Dynamic Aquatic Water**:
  - Vibrant aquatic turquoise teal water shader.
  - Multi-frequency trochoidal wave ripple texture with crest specular sparkle.
  - Real-time GPU UV flow animation for the river under the bridge and park ponds.
  - Stone bridge support piers and natural riverbed boulder clusters.

- **Living World & Park Environments**:
  - Infinite procedural city streaming with seamless cell loading and recycling.
  - Grand parks featuring 140+ boulders across multiple size tiers (small river pebbles up to grand glacial monoliths) with full 3D pitch/roll tilts and natural orientations.
  - Diverse shade tree groves (`nature-library.glb`) with randomized scales and 360° rotations.

- **Controls & Gameplay**:
  - Accelerometer tilt steering and on-screen touch steering controls.
  - Smart multi-lane vehicle traffic with smooth intersection yielding and collision detection.
  - Package depot collection and drop-off scoring mechanics.

---

## Project Structure

```
deliver-it-3d/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   ├── buildings/       # 3D building models (GLB)
│       │   ├── generated/       # Props & accessories (GLB)
│       │   ├── textures/        # Surface & decal textures
│       │   ├── vehicles/        # Cars, buses, motorbikes (GLB)
│       │   ├── world/           # Architecture, nature & rock models (GLB)
│       │   └── city_map.json    # City layout blueprint
│       ├── java/com/kazisayed/deliverit3d/
│       │   ├── AssetLibrary.java       # Asynchronous glTF asset streaming
│       │   ├── CityLayout.java         # Procedural city, roads, parks & bridges
│       │   ├── Collision.java          # 2D/3D collision bounds
│       │   ├── DispatchGame.java       # Game loop and lifecycle
│       │   ├── GameAudio.java          # Sound effects and audio management
│       │   ├── LevelData.java          # Level progression & checkpoints
│       │   ├── MainActivity.java       # Android entry activity
│       │   ├── MeshKit.java            # Procedural materials & light decals
│       │   ├── NativeUi.java           # Canvas-based HUD & overlay UI
│       │   ├── NightEnvironment.java   # Night cubemap & IBL lighting
│       │   ├── PlayerInput.java        # Touch and steer input processor
│       │   ├── RiderRig.java           # Player courier bicycle rig & physics
│       │   ├── Rules.java              # Math helpers & easing utilities
│       │   ├── SaveStore.java          # High score & progress persistence
│       │   ├── StaticCityBlueprint.java# Fixed landmark city layout
│       │   ├── TiltController.java     # Accelerometer steering
│       │   ├── TrafficSim.java         # Traffic simulation logic
│       │   ├── TrafficSystem.java      # Visual traffic vehicle management
│       │   ├── WeatherSystem.java      # Rain, snow, daylight & water flow
│       │   ├── WorldGrid.java          # World coordinates & elevation grid
│       │   └── WorldScene.java         # 3D scene streaming & prop management
│       └── res/                        # App icons, themes, and XML resources
├── gradle/
│   └── wrapper/
├── build.gradle
├── gradle.properties
├── gradlew
├── gradlew.bat
└── settings.gradle
```

---

## Build & Run

### Prerequisites
- **JDK 17** or newer
- **Android SDK** with `compileSdk 34` or newer
- **Android Studio** (Ladybug / Meerkat or newer recommended)

### Build APK via Command Line
```bash
# Debug build
./gradlew assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk
```

### Install onto Connected Device
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Technologies Used

- [LibGDX](https://libgdx.com/) — Cross-platform 2D/3D game development framework
- [gdx-gltf](https://github.com/mgsx-dev/gdx-gltf) — glTF 2.0 loader and PBR shader pipeline for LibGDX
- Android SDK 34 (Android 14)
