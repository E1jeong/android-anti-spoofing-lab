# Device Runtime Guidance

Read this document before changing cameras, RGB/IR pairing, calibration, FaceMe tracking, lifecycle, device controls, preview mirroring, or device storage access.

## Camera and Tracking Pipeline

- RGB and IR YUV-to-Bitmap conversion runs on dedicated single-threaded executors. Camera callbacks drop incoming frames while conversion is busy.
- Detect the largest face from the latest RGB frame and update the overlay independently of model inference.
- Match the latest IR frame within `MAX_PAIR_DELTA_NS` (150ms), then map the RGB face region into IR coordinates using device calibration.
- Normal inference rejects an RGB face box touching an image edge or moving faster than 0.7 face widths per second; it resumes after two stable tracking frames. On rejection, clear the prior classification while retaining the yellow face box, and discard any already-running result from the earlier motion generation. Do not apply this gate to collection.
- The diagnostic crop preview updates independently of pairing success through a copied frame buffer and is throttled to a minimum 66ms interval (~15 FPS).
- The UI displays probabilities, top result, conversion time, detection time, inference time, and processing FPS over the RGB or IR preview.
- On `master`, FaceMe and MediaPipe Face Detector are initialized together and the UI can select either for tracking and calibration. MediaPipe runs on CPU in the existing tracking executor; do not invoke it from camera callbacks or enable a second accelerator path before target-device measurement. Its detected box expands 25% above and 5% below the raw height, with image-bound clamping; the model spec's crop margin remains separate. `live` collection keeps the FaceMe HIGH/MEDIUM quality contract, so it must reject live collection while MediaPipe is selected.
- The MediaPipe short-range model (`blaze_face_short_range.tflite`) is a local ignored asset, not a manifest slot or checked-in model artifact. Provision it locally for branch builds and verify both RGB and IR detection before considering FaceMe removable.

## Camera Teardown Status

- Target-device testing on 2026-07-20 reproduced a native crash after repeated transitions to Android Settings and back. Android DropBox recorded `SIGSEGV` on `rgb-camera` in `__memcpy -> JNI SetByteArrayRegion`, copying `0x300` (768) bytes from an unmapped address.
- The copy length and thread identify the Y-plane row bulk copy in `YuvConverter.copyToNv21()`. An `onImageAvailable` callback can pass its generation check and enter `ByteBuffer.get()` while `CameraStream.stop()` concurrently closes the `ImageReader` on the main thread, invalidating the native image-plane buffer.
- Commit `6a9d6ce` disables delivery and advances generation, queues stop/abort/device close on the camera handler, and waits for `CameraDevice.StateCallback.onClosed()` before releasing `ImageReader` and the preview `Surface`. It then closes the converter after conversion work terminates and safely quits and joins both camera handler threads.
- Before `6a9d6ce`, five Settings round trips consistently produced RGB/IR `BufferQueue has been abandoned` messages and close contention. The same five-cycle test with `6a9d6ce` produced no teardown warning, crash, process restart, stale preview, or camera/inference recovery failure. This closes the reproduced P0 at the verified five-cycle scope.
- Preserve the `CameraDevice.onClosed()` boundary when changing shutdown order. Generation checks alone do not make an already-running image copy safe.

## Device Contract

- RGB-to-IR mapping requires `/sdcard/devlocal/CalibConfig.dat`.
- Because the app runs as `android.uid.system`, Android API 30+ FUSE/MediaProvider can block direct `/sdcard` access. At startup `UbimDaemonClient` grants `MANAGE_EXTERNAL_STORAGE` using `appops set com.virditech.ac7000 MANAGE_EXTERNAL_STORAGE allow`.
- If `/sdcard/devlocal/CalibConfig.dat` still cannot be read or written, calibration transparently falls back to `getFilesDir()/CalibConfig.dat`.
- The hidden calibration flow opens after five taps on the upper-left hotspot, measures one RGB face and one synchronized IR face, and writes the npro-compatible 64-byte calibration file.
- IR LED and LCD controls use device sysfs paths, and watchdog behavior uses the UBio daemon. These paths and protocols are hardware-specific.
- The lower-left five-tap test menu toggles the PI6008K IR AE Full/Center profile without restarting the camera. Keep the selected mode for the process lifetime, show it at the bottom of the upper-right crop preview only while IR is the main preview, and do not treat the label or a successful write as proof that AE converged.
- The application ID and namespace are `com.virditech.ac7000`. The test app cannot coexist with the production UBio-N Face Pro app on one device.
- Camera previews are mirrored on screen. RGB is mirrored, and IR uses `irPreviewView.setScaleX(-1f)`. `OverlayView.onDraw()` must always pass `true` as the `mirror` argument to `map()` so the green face box aligns with the preview.

## Source-Project Boundary

- Preserve camera selection, resolution, timestamp synchronization, calibration mapping, IR LED/LCD control, watchdog behavior, lifecycle cleanup, package identity, and signing behavior unless the task explicitly targets them.
- When copied device behavior is unclear, consult UBio-N Face Pro before changing it. Do not import unrelated production features or modules.

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
