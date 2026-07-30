# WebRTC Test Guidance

Read this document before changing signaling, WebRTC dependencies or messages, network permissions, the hidden test menu, `WebRtcCallActivity`, camera/audio handoff, or call recovery.

## Current Scope

- This repository owns the isolated Android terminal proof of concept. Shared signaling-server, operator-web, and common message-contract work belongs to the separate UBio WebRTC project.
- UBio-N Face Pro is a deferred production integration target. Do not copy the test implementation back into it unless the user explicitly returns product integration to scope.
- `SignalingClient` currently registers a test `device` Peer, parses registration/error messages, reconnects with capped exponential backoff, and disconnects from `MainActivity.onDestroy()`.
- `WebRtcCallActivity` is only a lifecycle handoff screen. There is no WebRTC Android SDK, `PeerConnection`, SDP/ICE handling, media track, capturer, or audio implementation yet.

## Test-Network Boundary

- Preserve the source-copied signaling URL and Peer ID unless the user explicitly requests a change.
- Treat the current fixed identity, `ws://`, and application-wide cleartext allowance as trusted-LAN PoC settings, not production security or deployment policy.
- Never copy real signaling URLs, Peer IDs, credentials, TURN secrets, or customer/device identifiers into tracked documentation or user-facing logs.
- Do not add authentication, WSS, TURN, persistence, or production configuration speculatively.

## Camera and Activity Handoff

- WebRTC does not require a separate Activity, but two Camera2 owners cannot use the same color camera concurrently.
- The current boundary is `MainActivity` → `WebRtcCallActivity`: `MainActivity.onPause()` must stop RGB/IR cameras and turn off the IR LED before a future WebRTC capturer opens the color camera.
- Preserve the `CameraDevice.onClosed()` shutdown boundary documented in `device-runtime.md`. Do not start a WebRTC capturer while the existing camera teardown is still in progress.
- A future call Activity must release capturer, tracks, `PeerConnection`, audio state, and related callbacks before finishing. Returning to `MainActivity` must restore RGB/IR preview, IR LED, face detection, inference, and timing output.
- The current signaling client remains owned by `MainActivity` and stays connected while that Activity is paused. Do not introduce a coordinator or service until the call-state contract requires it.

## Validation

- After code or build changes, run `./gradlew.bat :app:compileDebugJavaWithJavac`.
- On the target device, verify signaling connection, `registered` response, disconnect/reconnect, and app termination without pending reconnect work.
- Repeat entry to and exit from `WebRtcCallActivity`; treat crashes, process restarts, abandoned BufferQueues, unreleased camera warnings, stale preview, missing IR LED recovery, or stalled inference as failures.
- When media is added, verify audio permissions and focus, camera selection/orientation/mirroring, SDP/ICE flow, hangup, network loss, peer loss, and cleanup before claiming WebRTC support.
