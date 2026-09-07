# WebRTC Terminal Verification

Version-bound execution checklist for the checked-out Android evaluator. Resolve the project wiki through root `AGENTS.md`; `features/webrtc-test` owns the contracts, rationale, and dated results. Record the tested commit, APK/model hashes, device, scenario, and outcome. A documentation review does not rerun these checks.

## Validation

- After code or build changes, run `./gradlew.bat :app:compileDebugJavaWithJavac`.
- On the target device, verify signaling connection, `registered`, operator `call.invite` and audio/video Offer, device `call.accept` and Answer, bidirectional ICE, local/remote video and audio, microphone permission and mute, speaker routing, Activity close `call.hangup`, disconnect/reconnect, and app termination without pending reconnect work.
- Repeat entry to and exit from `WebRtcCallActivity`; treat crashes, process restarts, abandoned BufferQueues, unreleased camera warnings, stale preview, missing IR LED recovery, or stalled inference as failures.
- Verify front-camera selection/orientation/mirroring, microphone permission denial and grant, mute/unmute, audio focus loss/gain, echo/howling, speaker volume/routing, SDP/ICE flow, hangup, network loss, peer loss, and cleanup before claiming audio/video support.
