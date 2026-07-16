# Android app setup

No code yet — this covers what the environment will need once implementation starts.

## Prerequisites

- Android Studio (latest stable)
- Android SDK Platform 26+ (project minSdk), compileSdk set to the latest stable release
- A physical device for testing — the emulator's virtual camera isn't representative of real hardware encoder behavior

## Permissions the manifest will need

- `CAMERA`
- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `ACCESS_WIFI_STATE`
- `FOREGROUND_SERVICE`

## USB mode setup (developer, not end-user, for now)

1. Enable Developer Options on the phone
2. Enable USB debugging
3. Confirm the device shows up with `adb devices`

## Build

To build and install the app on a connected device from the command line:

```powershell
# Set JAVA_HOME to JDK 17
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'

cd android
.\gradlew.bat installDebug
```
