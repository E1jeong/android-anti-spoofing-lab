# Device Runtime Guidance

Read this document before changing cameras, RGB/IR pairing, calibration, FaceMe tracking, lifecycle, device controls, preview mirroring, or device storage access.

## Camera and Tracking Pipeline

- RGB and IR YUV-to-Bitmap conversion runs on dedicated single-threaded executors. Camera callbacks drop incoming frames while conversion is busy.
- Detect the largest face from the latest RGB frame and update the overlay independently of model inference.
- Match the latest IR frame within `MAX_PAIR_DELTA_NS` (150ms), then map the RGB face region into IR coordinates using device calibration.
- The diagnostic crop preview updates independently of pairing success through a copied frame buffer and is throttled to a minimum 66ms interval (~15 FPS).
- The UI displays probabilities, top result, conversion time, detection time, inference time, and processing FPS over the RGB or IR preview.

## Device Contract

- RGB-to-IR mapping requires `/sdcard/devlocal/CalibConfig.dat`.
- Because the app runs as `android.uid.system`, Android API 30+ FUSE/MediaProvider can block direct `/sdcard` access. At startup `UbimDaemonClient` grants `MANAGE_EXTERNAL_STORAGE` using `appops set com.virditech.ac7000 MANAGE_EXTERNAL_STORAGE allow`.
- If `/sdcard/devlocal/CalibConfig.dat` still cannot be read or written, calibration transparently falls back to `getFilesDir()/CalibConfig.dat`.
- The hidden calibration flow opens after five taps on the upper-left hotspot, measures one RGB face and one synchronized IR face, and writes the npro-compatible 64-byte calibration file.
- IR LED and LCD controls use device sysfs paths, and watchdog behavior uses the UBio daemon. These paths and protocols are hardware-specific.
- The application ID and namespace are `com.virditech.ac7000`. The test app cannot coexist with the production UBio-N Face Pro app on one device.
- Camera previews are mirrored on screen. RGB is mirrored, and IR uses `irPreviewView.setScaleX(-1f)`. `OverlayView.onDraw()` must always pass `true` as the `mirror` argument to `map()` so the green face box aligns with the preview.

## Source-Project Boundary

- Preserve camera selection, resolution, timestamp synchronization, calibration mapping, IR LED/LCD control, watchdog behavior, lifecycle cleanup, package identity, and signing behavior unless the task explicitly targets them.
- When copied device behavior is unclear, consult UBio-N Face Pro before changing it. Do not import unrelated production features or modules.

## Validation

- Hardware-dependent changes must verify RGB and IR preview startup, frame pairing, calibration alignment, IR LED state, face detection, inference output, timing, and cleanup/restart across pause and resume.
- Calibration changes must also verify hidden-mode entry, single-face validation for both cameras, cancel-without-save, persisted alignment after restart, and production mapping-formula compatibility.
- Test camera-open termination, repeated pause/resume, and termination during model/FaceMe warmup when lifecycle behavior changes.
- A `Tracking failed` message means an asynchronous exception escaped `processTracking`. Inspect the stack trace with:

```bash
adb logcat -s MainActivity:E *:S
```
