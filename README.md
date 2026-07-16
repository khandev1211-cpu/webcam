# Phone Webcam 📱🎥

Turn your Android device into a high-quality, low-latency webcam for your Windows PC via Wi-Fi.

[![Build and Release](https://github.com/khandev1211-cpu/webcam/actions/workflows/build-and-release.yml/badge.svg)](https://github.com/khandev1211-cpu/webcam/actions/workflows/build-and-release.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## 🌟 Overview

**Phone Webcam** is a high-performance streaming solution that bridges your Android phone's camera to Windows as a native Virtual Camera device. It uses hardware-accelerated H.264 encoding and low-latency UDP transport.

### Key Features
- **Zero Latency (Target):** Optimized UDP transport for near-instant video feed.
- **High Quality:** Leverages Android's `CameraX` API and hardware `MediaCodec`.
- **Native Integration:** Appears as a standard webcam in Zoom, Microsoft Teams, OBS, and Discord.
- **Wi-Fi Transport:** Works over your local network. Simply enter the IP shown on your phone.

## 🚀 Quick Start

### 1. Requirements
- **PC:** Windows 11 (Build 22000+)
- **Mobile:** Android 8.0 (Oreo) or higher
- **Network:** Both devices must be on the same Wi-Fi network.

### 2. Installation
1. Download the latest **APK** and **Receiver.exe** from the [Releases](https://github.com/khandev1211-cpu/webcam/releases) page.
2. Install the APK on your Android device.
3. Launch the app on your phone.

### 3. Connecting
1. Open the app on your phone. It will display an **IP Address** (e.g., `192.168.1.5`).
2. Run `PhoneWebcamReceiver.exe` on your PC.
3. Pass the phone's IP as an argument:
   ```powershell
   .\PhoneWebcamReceiver.exe 192.168.1.5
   ```
4. Press **'S'** on your keyboard to switch between Front/Back cameras.

## 🛠 Developer Setup

### Android App
- Located in `/android`.
- Requires Camera, Internet, and Wi-Fi State permissions.

### Windows Receiver
- Located in `/windows`.
- Built with C++ 20 and CMake.
- Uses Windows Media Foundation for decoding.

## 📄 License
This project is licensed under the MIT License.
