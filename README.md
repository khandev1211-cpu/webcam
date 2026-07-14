# Phone Webcam 📱🎥

Turn your Android device into a high-quality, low-latency webcam for your Windows PC.

[![Build and Release](https://github.com/khandev1211-cpu/webcam/actions/workflows/build-and-release.yml/badge.svg)](https://github.com/khandev1211-cpu/webcam/actions/workflows/build-and-release.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## 🌟 Overview

**Phone Webcam** is a high-performance streaming solution that bridges your Android phone's camera to Windows as a native Virtual Camera device. It uses hardware-accelerated H.264 encoding to ensure smooth video with minimal CPU impact on both devices.

### Key Features
- **Zero Latency (Target):** Optimized UDP transport for near-instant video feed.
- **High Quality:** Leverages Android's `CameraX` API and hardware `MediaCodec`.
- **Native Integration:** Appears as a standard webcam in Zoom, Microsoft Teams, OBS, and Discord via the Windows 11 Media Foundation Virtual Camera API.
- **Dual Transport:** Works seamlessly over **Wi-Fi** (local network) or **USB** (via ADB tunneling).

## 🚀 Quick Start

### 1. Requirements
- **PC:** Windows 11 (Build 22000+)
- **Mobile:** Android 8.0 (Oreo) or higher
- **Tools:** [ADB](https://developer.android.com/studio/releases/platform-tools) (for USB mode)

### 2. Installation
1. Download the latest **APK** and **Receiver.exe** from the [Releases](https://github.com/khandev1211-cpu/webcam/releases) page.
2. Install the APK on your Android device.
3. Launch the app on your phone.

### 3. Connecting via USB (Recommended)
1. Enable **USB Debugging** on your phone.
2. Connect your phone to your PC via USB.
3. Run the following command on your PC:
   ```bash
   adb forward tcp:8080 tcp:8080
   ```
4. Run `PhoneWebcamReceiver.exe` on your PC.

## 🛠 Developer Setup

### Android App
- Located in `/android`.
- Built with Kotlin and Gradle.
- Requires Camera and Internet permissions.

### Windows Receiver
- Located in `/windows`.
- Built with C++ 20 and CMake.
- Uses Windows Media Foundation for virtual camera registration.

## 🗺 Roadmap
- [x] Initial Project Scaffolding
- [x] TCP Control Channel Handshake
- [ ] H.264 Hardware Encoding (Android)
- [ ] UDP Stream Reception (Windows)
- [ ] Virtual Camera Driver Registration
- [ ] Audio Streaming Support

## 📄 License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
