# Capture Verification

Version-bound execution checklist for the checked-out Android evaluator. Resolve the project wiki through root `AGENTS.md`; `features/camera-and-calibration` owns the contracts, rationale, and dated results. Record the tested commit, APK/model hashes, device, scenario, and outcome. A documentation review does not rerun these checks.

## Troubleshooting and Validation

- `live` capture can appear paused while quality is below the threshold; this is expected.
- If non-live capture stalls, inspect frame pairing, IR availability, storage writes, and asynchronous tracking errors rather than FaceMe quality.
- For ATTACK capture, verify the 80% threshold, X-time in-flight write completion, and long-run frame-pool pressure.
- Cancel must not allow late work to recreate a deleted subject directory.
- Verify all four BMP files for dimensions, color, orientation, crop alignment, and metadata consistency.
- Run a 100-sample session with pause/resume/cancel and inspect camera-pool pressure, frame drops, capture-save P50/P95, heap, and GC.
- Storage logs:

```bash
adb logcat -s MainActivity:E MainActivity:I
```
