# Wire protocol (draft v1)

Draft spec — will firm up once the Android and PC implementations are underway.

## Transport

- **Control channel:** TCP, one connection per session.
- **Video channel:** UDP, one stream per session.
- Both channels run over whichever path is active (Wi-Fi IP or the localhost port `adb forward` maps to) — the protocol itself doesn't know or care which.

## Control channel (TCP)

Simple length-prefixed JSON messages: 4-byte big-endian length, then a UTF-8 JSON payload.

| Message | Direction | Purpose |
|---|---|---|
| `hello` | Phone → PC | Announces device name, supported resolutions/frame rates, encoder capabilities |
| `hello_ack` | PC → Phone | Confirms chosen resolution/frame rate, UDP port to send video to |
| `start` | PC → Phone | Begin streaming |
| `stop` | PC → Phone | Stop streaming |
| `heartbeat` | Either | Keep-alive, sent every ~2s; connection is considered dead after 2 missed beats |

## Video channel (UDP)

Each packet:

| Field | Size | Notes |
|---|---|---|
| Sequence number | 4 bytes | Detects loss/reordering |
| Timestamp | 8 bytes | Capture time, for PC-side pacing |
| Flags | 1 byte | Bit 0 = keyframe |
| Payload | variable | Raw H.264 NAL unit(s), fragmented across packets if needed |

Frame loss is tolerated — the PC receiver drops to the next keyframe rather than trying to repair a stream. The phone can be asked to force a keyframe via a control message if visual glitches build up (not yet in the message table above — add when implemented).

## Pairing (Wi-Fi mode)

Not yet decided: manual IP entry vs. QR code vs. local network discovery (NSD/mDNS). Manual entry is the simplest to ship first; discovery can follow.
