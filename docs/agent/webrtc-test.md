# WebRTC Test Guidance

Read this document before changing signaling, WebRTC dependencies or messages, network permissions, the hidden test menu, `WebRtcCallActivity`, camera/audio handoff, or call recovery.

## Current Scope

- This repository owns the isolated Android terminal proof of concept. Shared signaling-server, operator-web, and common message-contract work belongs to the separate UBio WebRTC project.
- UBio-N Face Pro is a deferred production integration target. Do not copy the test implementation back into it unless the user explicitly returns product integration to scope.
- `SignalingClient` registers a test `device` Peer, reconnects with capped exponential backoff, and relays `call.*` plus SDP/ICE messages to the active Activity listener.
- `WebRtcCallActivity` accepts the incoming call, answers the operator-created SDP Offer, exchanges ICE, sends the front RGB camera at the device-validated 768×432/15fps size, renders local and remote video, and sends `call.hangup` when it closes. Audio, STUN, TURN, authentication, and production configuration are not implemented.
- Preserve SDP strings exactly as received, including their final CRLF. Trimming or normalizing SDP can make the native WebRTC parser reject an otherwise valid remote description.
- The target device requires 270-degree frame-rotation metadata for its WebRTC front-camera capture. Mirror only the local self preview so it matches the main-screen preview; keep the transmitted frame unchanged.

## Test-Network Boundary

- Preserve the source-copied signaling URL and Peer ID unless the user explicitly requests a change.
- Treat the current fixed identity, `ws://`, and application-wide cleartext allowance as trusted-LAN PoC settings, not production security or deployment policy.
- Never copy real signaling URLs, Peer IDs, credentials, TURN secrets, or customer/device identifiers into tracked documentation or user-facing logs.
- Do not add authentication, WSS, TURN, persistence, or production configuration speculatively.

## Camera and Activity Handoff

- WebRTC does not require a separate Activity, but two Camera2 owners cannot use the same color camera concurrently.
- The current boundary is `MainActivity` → `WebRtcCallActivity`: `MainActivity.onPause()` stops RGB/IR cameras and turns off the IR LED before the WebRTC Camera2 capturer opens the front color camera.
- Preserve the `CameraDevice.onClosed()` shutdown boundary documented in `device-runtime.md`. Do not start the WebRTC capturer while the existing camera teardown is still in progress.
- The call Activity must release its capturer, video track/source, `PeerConnection`, renderers, and callbacks before finishing. Returning to `MainActivity` must restore RGB/IR preview, IR LED, face detection, inference, and timing output.
- The process-local signaling client stays connected while `MainActivity` is paused and transfers its active listener to the call Activity. Keep listener replacement lifecycle-safe; do not introduce a service until background call ownership is required.

## Validation

- After code or build changes, run `./gradlew.bat :app:compileDebugJavaWithJavac`.
- On the target device, verify signaling connection, `registered`, operator `call.invite` and Offer, device `call.accept` and Answer, bidirectional ICE, local/remote video, Activity close `call.hangup`, disconnect/reconnect, and app termination without pending reconnect work.
- Repeat entry to and exit from `WebRtcCallActivity`; treat crashes, process restarts, abandoned BufferQueues, unreleased camera warnings, stale preview, missing IR LED recovery, or stalled inference as failures.
- Verify front-camera selection/orientation/mirroring, SDP/ICE flow, hangup, network loss, peer loss, and cleanup before claiming video support. When audio is added, separately verify permissions, audio focus, echo, routing, and cleanup.
