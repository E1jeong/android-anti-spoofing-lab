# Capture Contract Guidance

Read this document before changing capture collection, FaceMe quality gating, sample storage, metadata, BMP writing, progress/countdown behavior, pause/resume, or cancel.

## Collection Contract

- `START CAPTURE` writes exactly 100 valid samples per selected class.
- For `live`, each save candidate must meet the selected FaceMe HIGH or MEDIUM quality threshold. Rejected frames do not increment the saved count or sector count.
- Non-live classes (`display`, `picture`, `print`, `mask`, `pmask`) bypass FaceMe quality checks.
- Capture can be paused and resumed. Cancel invalidates queued work and deletes the current subject directory, including samples already saved during that session.
- A sample count advances only after `RGB.bmp`, `cropRGB.bmp`, `IR.bmp`, `cropIR.bmp`, and `meta.json` all save successfully.

## Storage Contract

- Capture uses direct filesystem writes under `/sdcard/Pictures/raw`; there is no internal-storage fallback.
- `live` paths are `live/high/live_<subject>` or `live/medium/live_<subject>`. Other classes use `<class>/<class>_<subject>`.
- A write probe must succeed before capture starts.
- Internal class identifiers and paths use lowercase `pmask`.
- `meta.json` must remain consistent with the saved BMP coordinate space and the active crop margin.

## Current Ownership and Writer Behavior

- Live save candidates reuse one FaceMe extraction for tracking box and quality landmark/pose data. Preserve non-live bypass and the selected thresholds.
- Capture I/O owns a detached RGB/IR `FramePair` until save completion or discard.
- Full and crop BMPs are written directly from source bitmaps through a reusable 16-row stripe buffer and `BufferedOutputStream`; do not restore the two full-frame copies or two crop bitmap creations without a verified reason.
- BMP output is 24-bit, bottom-up, with four-byte row padding.
- Frames must be returned on success, validation failure, cancel/session invalidation, queued-task discard, and executor rejection.

## Troubleshooting and Validation

- `live` capture can appear paused while quality is below the threshold; this is expected.
- If non-live capture stalls, inspect frame pairing, IR availability, storage writes, and asynchronous tracking errors rather than FaceMe quality.
- Cancel must not allow late work to recreate a deleted subject directory.
- Verify all four BMP files for dimensions, color, orientation, crop alignment, and metadata consistency.
- Run a 100-sample session with pause/resume/cancel and inspect camera-pool pressure, frame drops, capture-save P50/P95, heap, and GC.
- Storage logs:

```bash
adb logcat -s MainActivity:E MainActivity:I
```
