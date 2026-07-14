# Phone Webcam

Turn an Android phone into a Windows webcam — over Wi-Fi or USB — that shows up as a normal camera in Zoom, Teams, OBS, and anything else that takes a webcam.

> Working name — rename freely once you've got something better.

## Status

Early planning / scaffolding. No application code yet — this repo currently holds the architecture and specs that implementation will follow.

## How it works

The phone captures and hardware-encodes video, sends it to the PC over Wi-Fi or USB, and the PC decodes it into a Windows virtual camera device that any app can select like a normal webcam.

See [docs/architecture.md](docs/architecture.md) for the full breakdown.

## v1 scope

- Android phone as the source (Kotlin + Camera2 + MediaCodec)
- Windows 11 as the only target OS (Media Foundation virtual camera API)
- Both connection modes work the same way underneath — USB just tunnels the same protocol over `adb forward`
- Virtual camera is system-wide: any app that lists cameras sees it, not just a bundled viewer

## Requirements

- **Phone:** Android 8.0+ (API 26+), camera + hardware H.264 encoder
- **PC:** Windows 11, build 22000 or later
- **USB mode:** USB debugging enabled on the phone, ADB installed on the PC

## Docs

| File | Contents |
|---|---|
| [docs/architecture.md](docs/architecture.md) | Components, data flow, key design decisions |
| [docs/protocol.md](docs/protocol.md) | Wire format for the control and video channels |
| [docs/setup-android.md](docs/setup-android.md) | Android app prerequisites and build setup |
| [docs/setup-windows.md](docs/setup-windows.md) | PC app prerequisites, build setup, virtual camera registration |

## Roadmap

**Now:** Wi-Fi + USB connection, Windows 11 virtual camera, Android capture.
**Later:** Windows 10 support (DirectShow fallback), audio passthrough, iOS client, quality/latency tuning, background operation (no foreground window required).

## License

TBD
