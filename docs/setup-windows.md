# PC app setup (Windows)

No code yet — this covers what the environment will need once implementation starts.

## Prerequisites

- Windows 11, build 22000 or later (required for the Media Foundation virtual camera API)
- Visual Studio 2022 with the "Desktop development with C++" workload
- CMake 3.20 or later
- Windows SDK version matching build 22000+ (needed for `mfvirtualcamera.h`)

## Virtual camera registration

The virtual camera is a COM component that must be registered once, with administrator rights, before Windows will list it as a camera device. Exact registration steps (regsvr32-style, or handled through `MFCreateVirtualCamera` at first run) will be documented here once the receiver app exists.

## Testing

Once registered, the camera should appear in:
- Windows Settings → Bluetooth & devices → Cameras
- Any app's camera picker (Camera app, Zoom, Teams, OBS)

## Build

The PC receiver is a C++ project managed by CMake.

### Using Command Line (PowerShell)

```powershell
# Define path to CMake (usually bundled with Visual Studio)
$cmake = 'C:\Program Files\Microsoft Visual Studio\18\Community\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe'

cd windows
& $cmake -B build
& $cmake --build build --config Release
```

The executable will be located at `windows/build/Release/PhoneWebcamReceiver.exe`.
