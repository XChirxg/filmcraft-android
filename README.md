# FilmCraft for Android 🎬📱

An Android port of **[FilmCraft](https://github.com/storytold/filmcraft)** (the open-source clean-room reimplementation of Adobe Premiere Pro in pure Rust).

---

## 🌟 Features

- **Full Premiere Pro Workflow**: Source and Program monitors, multi-track timeline (V1/V2, A1/A2), audio meters (LUFS/dB), Essential Graphics, Effects, and Bins.
- **Hardware Keyboard & Mouse Support**: Connect a Bluetooth or USB OTG keyboard/folio to use all native Premiere Pro shortcuts (`Space`, `J-K-L`, `Ctrl+K`, `I/O`, nudging, etc.) and mouse wheel scrubbing.
- **100% Offline & Standalone**: Bundles the complete WebAssembly Rust engine (`filmcraft_web_bg.wasm`) with an in-process local asset handler and embedded HTTP server. Zero internet connection required.
- **Fullscreen Immersive Editing**: Automatically hides system navigation bars and prevents screen sleep while editing.
- **Media Import Bridge**: Connects Android's native file picker (`ACTION_GET_CONTENT`) for importing video, audio, and photo clips directly into your project.

---

## 📱 Device Recommendations

- **Recommended:** **Android Tablets (10"+)** paired with a physical keyboard (Bluetooth, USB OTG, or keyboard case) and mouse. The multi-dock timeline, source/program monitors, and audio meters feel natural and productive on tablet displays.
- **Phones:** Functions on Android smartphones as well, though the multi-panel layout is much more compact on smaller screens.

---

## 📥 Direct APK Download

Pre-built APKs are available under the **[Releases](https://github.com/storytold/filmcraft/releases)** tab. Download the `.apk` file directly to your Android device, tap to install, and launch!

---

## 🏗️ Architecture

FilmCraft's desktop UI (`ui-egui`) and engine are compiled to WebAssembly (`wasm32`) with `wgpu` (WebGL2 / WebGPU canvas).

This Android package wraps the engine in an optimized Android container:
1. **Assets**: The compiled WebAssembly bundle (`filmcraft_web_bg.wasm`, `filmcraft_web.js`, `index.html`, fonts, shaders) is stored in `app/src/main/assets/web/`.
2. **LocalAssetServer**: A lightweight local embedded server (`LocalAssetServer.kt`) running on `127.0.0.1` ensures proper `application/wasm` MIME streaming.
3. **In-Process Fallback (`shouldInterceptRequest`)**: Intercepts requests directly from APK assets to deliver instant zero-latency loading.
4. **Hardware Acceleration**: Hardware-accelerated WebView renders the `egui` WebGL2 canvas at full display refresh rate.
5. **Key Routing**: `dispatchKeyEvent` passes hardware key events directly into the wasm event loop.

---

## 🚀 Building from Source

### Requirements
- Android SDK (API 34, Build Tools 34.0.0, Platform API 24+)
- JDK 17
- Gradle 8.7

### Build Commands
```bash
./android-build.sh     # Compiles and outputs dist/app-debug.apk
./android-install.sh   # Installs directly to any ADB-connected tablet/phone
./android-run.sh       # Launches the app on device
./android-logs.sh      # Streams logcat output
```
