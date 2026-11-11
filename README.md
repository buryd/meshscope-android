# MeshScope (Android)

Android companion to the iOS **NodeScope** experience: a live map of MeshCore nodes and hop-by-hop packet traffic from a CoreScope / Beacon analyzer API.

## What it shows

- Mapped MeshCore nodes (companions + repeaters) from `GET /api/nodes`
- Recent and live packets from `GET /api/packets` and WebSocket `/ws`
- Selected packet path drawn on the map with a moving hop pulse
- Analyzer presets (Chicagoland CoreScope) or any custom base URL

## Requirements

- Android Studio Ladybug+ (AGP 8.7) or **JDK 17** + Android SDK 35
- Device or emulator with network access to an analyzer

> Android Studio’s bundled JBR may be Java 25. This project pins Gradle to JDK 17 via `org.gradle.java.home` (local `.jdk/`). Open the folder in Android Studio and sync normally.

## Build & run

1. Open `C:\Users\dell\MeshScopeAndroid` in Android Studio
2. Let Gradle sync
3. Run the **app** configuration on a phone or emulator

Command line:

```bat
gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Analyzer URL

Default preset is **Warner Robins (LAN)** → `http://10.0.2.2:3000` (Android emulator → host PC running [CoreScope-WarnerRobins](../CoreScope-WarnerRobins)).

On a physical phone use your PC’s Wi‑Fi IP, e.g. `http://192.168.1.50:3000`.

Chicagoland (`https://corescope.chicagolandmesh.org`) remains available as a demo feed only.

Cleartext HTTP is allowed for LAN analyzers.

## API expectations

Compatible with CoreScope-style JSON:

| Endpoint | Purpose |
|----------|---------|
| `/api/nodes` or `/api/v1/nodes` | Node list with `lat`/`lon` (or `latitude`/`longitude`), `name`, `role` |
| `/api/packets?limit=` | Packet history with `path_json` / `resolved_path` |
| `/ws` (also `/api/ws`, `/api/v1/ws`) | Live packet stream |

Read-only — MeshScope does not transmit on the radio mesh.

## Middle Georgia

Point the custom URL at whatever analyzer covers Warner Robins / Middle Georgia once that feed is online. Until then, Chicagoland is useful for UI/live-path testing.
