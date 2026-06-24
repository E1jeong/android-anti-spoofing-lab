# Project: ubio-anti-spoofing

## Purpose

This repository is a minimal Android test application for evaluating newly developed RGB/IR anti-spoofing TensorFlow Lite models on UBio-N Face Pro hardware. It is an isolated model-development and verification tool, not the production terminal application.

Only the device, camera, calibration, face-detection, and lifecycle behavior required for model evaluation was extracted from the UBio-N Face Pro project. Keep this repository minimal. When copied device behavior is unclear, consult the source project before changing it; do not import unrelated production features or modules.

## Runtime Pipeline

The application performs the following pipeline:

1. Capture RGB and IR frames from the device cameras.
2. Detect the largest face from the latest RGB frame with FaceMe and update the overlay independently of model inference.
3. Match the latest IR frame within the timestamp tolerance for anti-spoofing inference.
4. Map the RGB face region to IR coordinates using the device calibration file, then expand both crops according to the model specification.
5. Run the matched RGB and IR crops through the TensorFlow Lite anti-spoofing model on a separate worker.
6. Display the four-class probabilities, top result, conversion time, detection time, inference time, and processing FPS over the RGB or IR preview.

## Model Contract

- The model asset is `app/src/main/assets/anti_spoofing.tflite`.
- Preprocessing settings are defined by `app/src/main/assets/model_spec.json`. They must match the training contract whenever the model changes.
- The model must have exactly two NHWC inputs: RGB and IR. Supported input types are `FLOAT32` and `UINT8`; RGB must have three channels, while IR may have one or three channels.
- The model must have one `FLOAT32` output with shape `[1,4]`.
- Output indices are fixed in this order: `LIVE`, `SPOOF_MASK`, `DISPLAY`, `PHOTO`.
- `model_spec.json` controls the RGB/IR input indices, channel order, normalization values, whether the output contains logits, and the crop margin ratio.
- Do not change preprocessing, output ordering, or tensor assumptions without updating the model contract and verifying them against the exported model.

## Device and Build Requirements

- This is an Android application with `minSdk 30`, `targetSdk 34`, and Java 17 source compatibility.
- The proprietary FaceMe dependency repository must be supplied through the `UBIO_MAVEN_URL` Gradle property.
- Face detection requires the configured FaceMe dependency and a valid `FACEME_LICENSE_KEY` Gradle property.
- Platform signing uses `UBIO_KEYSTORE_PATH`, `UBIO_KEY_ALIAS`, `UBIO_KEY_PASSWORD`, and `UBIO_STORE_PASSWORD` when provided.
- RGB-to-IR mapping requires `/sdcard/devlocal/CalibConfig.dat` on the device.
- The hidden camera-calibration flow is opened by five taps on the upper-left hotspot. It measures one RGB face and one synchronized IR face, then writes the npro-compatible 64-byte calibration file.
- The app controls the UBio IR LED and LCD through device sysfs paths and communicates with the UBio daemon for watchdog behavior. These paths and protocols are hardware-specific.
- The application ID and namespace are `com.virditech.ac7000`. Because this is also used by the production application, this test app cannot coexist with UBio-N Face Pro on the same device.

Default compile validation:

```powershell
./gradlew.bat :app:compileDebugJavaWithJavac
```

## Change Rules

- Make the smallest change that satisfies the requested model test or diagnostic goal.
- Do not perform broad refactors, add production features, or copy additional UBio-N Face Pro modules unless explicitly requested.
- Preserve camera selection, resolution, timestamp synchronization, calibration mapping, IR LED control, watchdog behavior, lifecycle cleanup, package identity, and signing behavior unless the task specifically targets one of them.
- Keep model-specific behavior in the model layer and keep device-specific behavior isolated in the existing camera, calibration, face, and device packages.
- Match the existing Java style and remove only imports or code made unused by the current change.
- Inspect the existing worktree before editing and do not overwrite unrelated user changes.
- Never commit FaceMe licenses, keystores, passwords, credentials, customer data, or other secrets.
- Do not add machine-local configuration or paths to tracked files. The source-project path above is documentation for this repository's origin, not a location that application code should depend on.

## Validation

- Run the default compile validation after code or build changes. Use a narrower check only when it fully covers the changed behavior.
- If the model changes, verify that the model loads and that its input/output tensors match the documented contract.
- Hardware-dependent changes require manual verification on the target device. At minimum, check RGB and IR preview startup, frame pairing, calibration alignment, IR LED state, face detection, all four output probabilities, inference timing, and cleanup/restart across pause and resume.
- Calibration changes must also verify hidden-mode entry, single-face validation for both cameras, cancel-without-save, persisted alignment after restart, and compatibility with the production RGB-to-IR mapping formula.
- If hardware validation cannot be performed, state which checks remain unverified and the resulting risk.
