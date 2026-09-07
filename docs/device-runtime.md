# Device Runtime Verification

Version-bound execution checklist for the checked-out Android evaluator. Resolve the project wiki through root `AGENTS.md`; `features/camera-and-calibration` and `technical/code-structure-performance-diagnosis` owns the contracts, rationale, and dated results. Record the tested commit, APK/model hashes, device, scenario, and outcome. A documentation review does not rerun these checks.

## Validation

- Hardware-dependent changes must verify RGB and IR preview startup, frame pairing, calibration alignment, IR LED state, face detection, inference output, timing, and cleanup/restart across pause and resume.
- For motion-gate changes, verify stationary LIVE/MASK inference, normal repositioning, rapid lateral MASK movement, image-edge entry/return, yellow-box clearing, and the `Motion gate` log's block/re-entry state.
- Calibration changes must also verify hidden-mode entry, single-face validation for both cameras, cancel-without-save, persisted alignment after restart, and production mapping-formula compatibility.
- Test camera-open termination, repeated pause/resume, and termination during model/FaceMe warmup when lifecycle behavior changes.
- When changing or testing IR AE controls, verify Full/Center selection, IR-only label visibility, RGB label hiding, RGB/IR preview recovery, frame pairing, and no `IrCameraExposure` error across pause/resume.
- Treat any `SIGSEGV`, abandoned BufferQueue during normal pause/resume, or unreleased camera resource warning as a failed lifecycle regression even if the app process later restarts successfully.
- A `Tracking failed` message means an asynchronous exception escaped `processTracking`. Inspect the stack trace with:

```bash
adb logcat -s MainActivity:E *:S
```
